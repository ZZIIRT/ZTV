"""Verify live HLS and repair URLs without changing channel identity or order."""

import argparse
import base64
import hashlib
import http.client
import ipaddress
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, unquote, urljoin, urlsplit

ROOT = Path(__file__).resolve().parent.parent
USER_AGENT = "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 ZTV/0.1.12"
MAX_MANIFEST = 4 * 1024 * 1024
MAX_SEGMENT = 12 * 1024 * 1024
MAX_CANDIDATES = 8
MAX_CHANGES = 3
POLICY_FILES = ("scripts/repair_playlist.py", "playlist-sources.json", ".github/workflows/repair-playlist.yml")
SENSITIVE_QUERY = {"token", "drmreq", "signature", "sig", "auth", "key", "password", "e", "s"}
REGIONAL = re.compile(r"plus\d|\+\d|спб|spb|kazakhstan|belarus|europa|europe|not 24/7", re.I)


class ProbeError(Exception):
    pass


@dataclass(frozen=True)
class Entry:
    channel_id: str
    info: str
    url: str
    line_index: int


@dataclass(frozen=True)
class ProbeResult:
    ok: bool
    reason: str
    playable_url: str = ""
    fingerprint: str = ""
    interval: float = 7


def parse_playlist(text):
    if not text.lstrip("\ufeff").startswith("#EXTM3U"):
        raise ValueError("Missing EXTM3U header")
    entries, info = [], None
    for index, raw in enumerate(text.splitlines()):
        line = raw.strip()
        if line.startswith("#EXTINF:"):
            info = line
        elif line and not line.startswith("#") and info:
            match = re.search(r'\btvg-id="([^"]+)"', info)
            if match:
                entries.append(Entry(match[1], info, line, index))
            info = None
    return entries


def validate_url(url, allow_signed=False):
    parts = urlsplit(url)
    if (
        parts.scheme not in {"http", "https"} or not parts.hostname or
        parts.username or parts.password or parts.fragment or len(url) > 8192 or
        any(ord(char) < 33 for char in url)
    ):
        raise ProbeError("unsafe_url")
    if not allow_signed and set(key.lower() for key in parse_qs(parts.query)) & SENSITIVE_QUERY:
        raise ProbeError("signed_or_authenticated_url")
    decoded_path = parts.path
    for _ in range(3):
        decoded_path = unquote(decoded_path)
    if "\\" in decoded_path or any(item in {".", ".."} for item in decoded_path.split("/")) or any(ord(char) < 32 for char in decoded_path):
        raise ProbeError("unsafe_channel_path")
    try:
        address = ipaddress.ip_address(parts.hostname)
    except ValueError:
        address = None
    if address is not None and not address.is_global:
        raise ProbeError("non_public_address")
    return parts


def fetch(url, limit=MAX_MANIFEST):
    # Pin the validated public IP for the socket, including every redirect.
    for _ in range(5):
        parts = validate_url(url, allow_signed=True)
        port = parts.port or (443 if parts.scheme == "https" else 80)
        addresses = socket.getaddrinfo(parts.hostname, port, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
            raise ProbeError("non_public_address")
        sock = socket.create_connection((addresses[0][4][0], port), timeout=8)
        if parts.scheme == "https":
            try:
                sock = ssl.create_default_context().wrap_socket(sock, server_hostname=parts.hostname)
            except Exception:
                sock.close()
                raise
        connection = http.client.HTTPConnection(parts.hostname, port, timeout=8)
        connection.sock = sock
        try:
            path = parts.path or "/"
            if parts.query:
                path += "?" + parts.query
            connection.request("GET", path, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
            response = connection.getresponse()
            if response.status in {301, 302, 303, 307, 308}:
                location = response.getheader("Location")
                if not location:
                    raise ProbeError("redirect_without_location")
                url = urljoin(url, location)
                continue
            if response.status in {403, 451}:
                raise ProbeError("restricted")
            if response.status != 200:
                raise ProbeError(f"http_{response.status}")
            deadline, chunks, size = time.monotonic() + 15, [], 0
            while True:
                chunk = response.read(min(64 * 1024, limit + 1 - size))
                if not chunk:
                    return b"".join(chunks), url
                size += len(chunk)
                if size > limit:
                    raise ProbeError("response_too_large")
                if time.monotonic() > deadline:
                    raise ProbeError("response_timeout")
                chunks.append(chunk)
        finally:
            connection.close()
    raise ProbeError("too_many_redirects")


def approved_prefixes(config):
    def seed_prefix(url):
        filename = urlsplit(url).path.rsplit("/", 1)[-1]
        generic = re.fullmatch(r"(?:index|variant|playlist(?:_\d+)?|\d+p|\d+_\d+p)\.m3u8", filename)
        return urljoin(url, ".") if generic else url

    return {key: [seed_prefix(url) for url in urls] + config.get("discovery_prefixes", {}).get(key, [])
            for key, urls in config["channels"].items()}


def candidates_from_feed(text, prefixes, aliases=None):
    result = {key: [] for key in prefixes}
    for entry in parse_playlist(text):
        channel_id, _, edition = entry.channel_id.partition("@")
        channel_id = (aliases or {}).get(channel_id, channel_id)
        if channel_id not in result or edition not in {"", "SD", "HD"}:
            continue
        if REGIONAL.search(entry.info + " " + entry.url):
            continue
        if not any(entry.url.startswith(prefix) for prefix in prefixes[channel_id]):
            continue
        try:
            parts = validate_url(entry.url)
            if not parts.path.lower().endswith(".m3u8"):
                continue
        except (ProbeError, ValueError):
            continue
        result[channel_id].append(entry.url)
    return result


def load_candidates(config):
    result = {key: list(value) for key, value in config["channels"].items()}
    errors = []
    for feed_url in config["feeds"]:
        try:
            completed = subprocess.run([sys.executable, __file__, "--feed", feed_url], capture_output=True, timeout=30, check=True)
            body = base64.b64decode(completed.stdout, validate=True)
            found = candidates_from_feed(body.decode("utf-8-sig"), approved_prefixes(config), config.get("aliases"))
            for key, urls in found.items():
                result[key].extend(sorted(urls, key=lambda url: not url.startswith("https://")))
        except (ProbeError, OSError, ValueError, http.client.HTTPException, subprocess.SubprocessError):
            errors.append(urlsplit(feed_url).path)
    return {key: list(dict.fromkeys(urls)) for key, urls in result.items()}, errors


class HlsProbe:
    def __init__(self, ffprobe="ffprobe", ffmpeg="ffmpeg"):
        self.ffprobe = ffprobe
        self.ffmpeg = ffmpeg

    def sample(self, url, depth=0):
        import m3u8

        if depth > 4:
            raise ProbeError("nested_manifest_limit")
        body, actual_url = fetch(url)
        text = body.decode("utf-8-sig")
        if not text.startswith("#EXTM3U"):
            raise ProbeError("not_hls")
        manifest = m3u8.loads(text, uri=actual_url)
        if manifest.is_variant:
            variants = [item for item in manifest.playlists if (
                item.stream_info.resolution and item.stream_info.resolution[1] <= 720 and
                "avc1" in (item.stream_info.codecs or "")
            )]
            if not variants:
                raise ProbeError("no_compatible_variant")
            variant = min(variants, key=lambda item: abs(item.stream_info.resolution[1] - 576))
            video = self.sample(variant.absolute_uri, depth + 1)
            audio = [item for item in manifest.media if (
                item.type == "AUDIO" and item.group_id == variant.stream_info.audio and item.uri
            )]
            compatible_master = all(
                item.stream_info.resolution and item.stream_info.resolution[1] <= 720 and
                all(codec.startswith(("avc1", "mp4a.40.2", "mp4a.40.5", "mp4a.40.29"))
                    for codec in (item.stream_info.codecs or "unknown").split(","))
                for item in manifest.playlists
            )
            if audio:
                video["audio"] = self.sample(audio[0].absolute_uri, depth + 1)
                if not compatible_master or len([item for item in manifest.media if item.type == "AUDIO"]) != 1:
                    raise ProbeError("incompatible_audio_master")
                video["playable_url"] = url
            elif compatible_master:
                video["playable_url"] = url
            return video
        if manifest.is_endlist and urlsplit(actual_url).hostname.endswith(".cdn.ngenix.net"):
            raise ProbeError("restricted")
        if manifest.is_endlist or not manifest.segments:
            raise ProbeError("not_live")
        if not isinstance(manifest.target_duration, (int, float)) or manifest.target_duration <= 0:
            raise ProbeError("invalid_target_duration")
        segment = manifest.segments[-1]
        if segment.key and segment.key.method != "NONE":
            raise ProbeError("encrypted_hls")
        if segment.byterange:
            raise ProbeError("unsupported_byterange")
        if segment.current_program_date_time:
            if segment.current_program_date_time.tzinfo is None:
                raise ProbeError("invalid_program_date_time")
            age = (datetime.now(timezone.utc) - segment.current_program_date_time).total_seconds()
            if age > 180 or age < -180:
                raise ProbeError("stale_segment")
        data, _ = fetch(segment.absolute_uri, MAX_SEGMENT)
        if segment.init_section:
            if segment.init_section.byterange:
                raise ProbeError("unsupported_byterange")
            init, _ = fetch(segment.init_section.absolute_uri, MAX_SEGMENT)
            data = init + data
            media_format = "mov"
        else:
            if len(data) < 188 * 5 or any(data[index * 188] != 0x47 for index in range(5)):
                raise ProbeError("not_mpegts")
            media_format = "mpegts"
        return {"data": data, "format": media_format, "playable_url": url,
                "segment": segment.absolute_uri, "interval": min(12, max(5, manifest.target_duration + 1))}

    def __call__(self, url):
        try:
            sample = self.sample(url)
            with tempfile.TemporaryDirectory(prefix="ztv-probe-") as directory:
                inputs, streams = [], []
                samples = [sample] + ([sample["audio"]] if "audio" in sample else [])
                for index, item in enumerate(samples):
                    path = Path(directory) / f"sample{index}.bin"
                    path.write_bytes(item["data"])
                    options = ["-protocol_whitelist", "file,pipe", "-f", item["format"], "-i", str(path)]
                    inputs.extend(options)
                    completed = subprocess.run(
                        [self.ffprobe, "-v", "error", *options, "-show_streams", "-of", "json"],
                        capture_output=True, timeout=12, check=True,
                    )
                    streams.extend(json.loads(completed.stdout).get("streams", []))
                if not any(item.get("codec_name") == "h264" and 0 < item.get("height", 0) <= 720 for item in streams):
                    raise ProbeError("no_compatible_video")
                if not any(item.get("codec_name") == "aac" for item in streams):
                    raise ProbeError("no_compatible_audio")
                subprocess.run(
                    [self.ffmpeg, "-v", "error", "-xerror", *inputs, "-map", "0:v:0", "-map",
                     ("1:a:0" if len(samples) == 2 else "0:a:0"), "-t", "1", "-f", "null", "-"],
                    capture_output=True, timeout=15, check=True,
                )
            return ProbeResult(True, "ok", sample["playable_url"], digest(sample["data"]), sample["interval"])
        except ProbeError as error:
            return ProbeResult(False, str(error))
        except (OSError, ValueError, http.client.HTTPException, subprocess.SubprocessError):
            return ProbeResult(False, "network_or_decode_error")


def repair_channel(entry, candidates, probe, sleep=time.sleep, prefixes=None):
    result = {"id": entry.channel_id, "status": "healthy", "reason": "ok"}
    first = probe(entry.url)
    if first.ok:
        return entry.url, result
    if first.reason == "restricted":
        return entry.url, dict(result, status="restricted", reason="restricted")
    sleep(5)
    second = probe(entry.url)
    if second.reason == "restricted":
        return entry.url, dict(result, status="restricted", reason="restricted")
    if second.ok:
        return entry.url, dict(result, status="transient", reason=second.reason)
    deadline = time.monotonic() + 120
    for url in [value for value in candidates if value != entry.url][:MAX_CANDIDATES]:
        if time.monotonic() >= deadline:
            break
        check = probe(url)
        if not check.ok:
            continue
        sleep(check.interval)
        confirmed = probe(check.playable_url or url)
        if confirmed.ok and confirmed.fingerprint and confirmed.fingerprint != check.fingerprint:
            replacement = confirmed.playable_url or url
            try:
                validate_url(replacement)
            except (ProbeError, ValueError):
                continue
            if prefixes is not None and not any(replacement.startswith(prefix) for prefix in prefixes):
                continue
            return replacement, dict(result, status="replaced", reason=first.reason, url=replacement)
    return entry.url, dict(result, status="unresolved", reason=first.reason)


def bounded_channel(entry, candidates):
    payload = {"entry": entry.__dict__, "candidates": candidates}
    try:
        completed = subprocess.run([sys.executable, __file__, "--channel"], input=json.dumps(payload).encode(),
                                   capture_output=True, timeout=180, check=True)
        return tuple(json.loads(completed.stdout))
    except (OSError, ValueError, subprocess.SubprocessError):
        return entry.url, {"id": entry.channel_id, "status": "unresolved", "reason": "probe_timeout_or_error"}


def repaired_playlist(text, candidates, probe, sleep=time.sleep, workers=4, channel_worker=None):
    entries = parse_playlist(text)
    if not entries or len({entry.channel_id for entry in entries}) != len(entries):
        raise ValueError("Empty playlist or duplicate channel IDs")
    if [entry.channel_id for entry in entries] != list(candidates):
        raise ValueError("Source catalog must match playlist IDs and order")
    with ThreadPoolExecutor(max_workers=workers) as executor:
        results = list(executor.map(lambda entry: (
            channel_worker(entry, candidates[entry.channel_id]) if channel_worker else
            repair_channel(entry, candidates[entry.channel_id], probe, sleep)
        ), entries))
    failed = sum(report["status"] in {"unresolved", "replaced"} for _, report in results)
    circuit_open = failed >= max(2, len(entries) * 0.6)
    lines, reports, changes = text.splitlines(), [], 0
    for entry, (url, report) in zip(entries, results):
        if report["status"] == "replaced":
            if circuit_open or changes >= MAX_CHANGES:
                report = {"id": entry.channel_id, "status": "unresolved", "reason": "mass_failure_guard" if circuit_open else "change_limit"}
            else:
                lines[entry.line_index] = url
                changes += 1
        reports.append(report)
    return "\n".join(lines) + "\n", reports


def digest(data):
    return hashlib.sha256(data).hexdigest()


def policy_digests():
    return {name: digest((ROOT / name).read_bytes()) for name in POLICY_FILES}


def make_catalog(text, candidates, config):
    prefixes = approved_prefixes(config)
    catalog = {}
    for entry in parse_playlist(text):
        urls = [entry.url]
        for url in candidates.get(entry.channel_id, []):
            try:
                validate_url(url)
                if any(url.startswith(prefix) for prefix in prefixes[entry.channel_id]):
                    urls.append(url)
            except (ProbeError, ValueError):
                continue
        catalog[entry.channel_id] = list(dict.fromkeys(urls))[:9]
    return {"schema": 1, "channels": catalog}


def validate_catalog(catalog, entries, config):
    if catalog.get("schema") != 1 or list(catalog.get("channels", {})) != [entry.channel_id for entry in entries]:
        raise ValueError("Fallback catalog channel identity mismatch")
    prefixes = approved_prefixes(config)
    for entry in entries:
        urls = catalog["channels"][entry.channel_id]
        if not isinstance(urls, list) or not 1 <= len(urls) <= 9:
            raise ValueError("Invalid fallback count")
        for url in urls:
            validate_url(url)
            if url != entry.url and not any(url.startswith(prefix) for prefix in prefixes[entry.channel_id]):
                raise ValueError("Unapproved fallback origin or channel path")


def publish(directory):
    original = (ROOT / "playlist.m3u8").read_bytes()
    report = json.loads((directory / "report.json").read_text(encoding="utf-8"))
    if digest(original) != report["base_sha256"]:
        raise ValueError("Playlist changed since probing; refuse stale publication")
    if policy_digests() != report["policy_sha256"]:
        raise ValueError("Repair policy changed since probing")
    config = json.loads((ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
    prefixes = approved_prefixes(config)
    updated = (directory / "playlist.m3u8").read_text(encoding="utf-8")
    before, after = parse_playlist(original.decode("utf-8-sig")), parse_playlist(updated)
    if [item.info for item in before] != [item.info for item in after]:
        raise ValueError("Channel names, IDs or order changed")
    if len(before) != 20 or len(after) != 20:
        raise ValueError("Expected exactly 20 channels")
    changes = 0
    expected_lines = original.decode("utf-8-sig").splitlines()
    for old, new, status in zip(before, after, report["channels"], strict=True):
        if status["id"] != old.channel_id:
            raise ValueError("Report channel identity mismatch")
        if old.url != new.url:
            validate_url(new.url)
            if not any(new.url.startswith(prefix) for prefix in prefixes[old.channel_id]):
                raise ValueError("Replacement has an unapproved origin or channel path")
            if status["status"] != "replaced" or status.get("url") != new.url:
                raise ValueError("Unverified replacement")
            expected_lines[old.line_index] = new.url
            changes += 1
    if changes > MAX_CHANGES:
        raise ValueError("Too many changes")
    if updated.splitlines() != expected_lines:
        raise ValueError("Artifact contains non-URL changes")
    catalog_bytes = (directory / "playlist-fallbacks.json").read_bytes()
    if digest(catalog_bytes) != report["catalog_sha256"]:
        raise ValueError("Fallback catalog digest mismatch")
    validate_catalog(json.loads(catalog_bytes), after, config)
    if changes:
        temporary = ROOT / "playlist.m3u8.tmp"
        temporary.write_text(updated, encoding="utf-8", newline="\n")
        os.replace(temporary, ROOT / "playlist.m3u8")
    (ROOT / "playlist-fallbacks.json").write_bytes(catalog_bytes)
    health = {"checked_on": report["checked_at"][:10], "channels": report["channels"], "source_errors": report["source_errors"]}
    (ROOT / "playlist-health.json").write_text(json.dumps(health, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / ".Codex" / "playlist-repair")
    parser.add_argument("--publish", type=Path)
    parser.add_argument("--channel", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--feed", help=argparse.SUPPRESS)
    parser.add_argument("--seed-catalog", type=Path, help="Create a bundled catalog from configured sources only")
    parser.add_argument("--seed-feed", type=Path, action="append", default=[], help="Reviewed local feed snapshots for a bundled catalog")
    args = parser.parse_args()
    if args.seed_catalog:
        config = json.loads((ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
        candidates = {key: list(urls) for key, urls in config["channels"].items()}
        for feed in args.seed_feed:
            found = candidates_from_feed(feed.read_text(encoding="utf-8-sig"), approved_prefixes(config), config.get("aliases"))
            for key, urls in found.items():
                candidates[key].extend(urls)
        catalog = make_catalog((ROOT / "playlist.m3u8").read_text(encoding="utf-8"), candidates, config)
        args.seed_catalog.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        return
    if args.feed:
        body, _ = fetch(args.feed)
        sys.stdout.buffer.write(base64.b64encode(body))
        return
    if args.channel:
        payload = json.load(sys.stdin)
        channel = Entry(**payload["entry"])
        config = json.loads((ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
        url, report = repair_channel(channel, payload["candidates"], HlsProbe(),
                                     prefixes=approved_prefixes(config)[channel.channel_id])
        sys.stdout.write(json.dumps([url, report]))
        return
    if args.publish:
        publish(args.publish)
        return
    if not shutil.which("ffprobe") or not shutil.which("ffmpeg"):
        raise SystemExit("ffprobe and ffmpeg are required; no playlist was changed")
    original = (ROOT / "playlist.m3u8").read_bytes()
    config = json.loads((ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
    candidates, source_errors = load_candidates(config)
    updated, channels = repaired_playlist(original.decode("utf-8-sig"), candidates, None, channel_worker=bounded_channel)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "playlist.m3u8").write_text(updated, encoding="utf-8", newline="\n")
    catalog = make_catalog(updated, candidates, config)
    catalog_bytes = (json.dumps(catalog, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    (args.output / "playlist-fallbacks.json").write_bytes(catalog_bytes)
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "base_sha256": digest(original),
              "policy_sha256": policy_digests(), "catalog_sha256": digest(catalog_bytes),
              "source_errors": source_errors, "channels": channels}
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = "# Playlist health\n\n| Channel ID | Status | Reason |\n|---|---|---|\n"
    for channel in channels:
        print(f"{channel['id']}: {channel['status']} ({channel['reason']})", flush=True)
        summary += f"| {channel['id']} | {channel['status']} | {channel['reason']} |\n"
    (args.output / "summary.md").write_text(summary, encoding="utf-8")


if __name__ == "__main__":
    main()
