import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import repair_playlist as repair


def entry(channel_id="One.ru", url="https://example.com/old.m3u8", index=2):
    return repair.Entry(channel_id, f'#EXTINF:-1 tvg-id="{channel_id}",Channel', url, index)


def copy_policy(root):
    for name in repair.POLICY_FILES:
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((repair.ROOT / name).read_bytes())


class RepairPolicyTests(unittest.TestCase):
    def test_second_probe_geoblock_is_classified_as_restricted(self):
        responses = iter([repair.ProbeResult(False, "http_503"), repair.ProbeResult(False, "restricted")])
        current = entry()
        url, report = repair.repair_channel(current, [], lambda _: next(responses), lambda _: None)
        self.assertEqual(current.url, url)
        self.assertEqual("restricted", report["status"])

    def test_stuck_channel_worker_is_bounded_and_keeps_original(self):
        with patch.object(repair.subprocess, "run", side_effect=repair.subprocess.TimeoutExpired("probe", 180)):
            current = entry()
            url, report = repair.bounded_channel(current, [])
            self.assertEqual(current.url, url)
            self.assertEqual("probe_timeout_or_error", report["reason"])

    def test_healthy_current_stream_is_retained_without_trying_backups(self):
        calls = []

        def probe(url):
            calls.append(url)
            return repair.ProbeResult(True, "ok")

        current = entry()
        url, report = repair.repair_channel(current, ["https://example.com/new.m3u8"], probe)
        self.assertEqual(current.url, url)
        self.assertEqual([current.url], calls)
        self.assertEqual("healthy", report["status"])

    def test_transient_failure_does_not_change_the_url(self):
        responses = iter([repair.ProbeResult(False, "http_503"), repair.ProbeResult(True, "ok")])
        current = entry()
        url, report = repair.repair_channel(current, [], lambda _: next(responses), lambda _: None)
        self.assertEqual(current.url, url)
        self.assertEqual("transient", report["status"])

    def test_geo_restricted_current_stream_is_not_replaced(self):
        current = entry()
        url, report = repair.repair_channel(current, [], lambda _: repair.ProbeResult(False, "restricted"))
        self.assertEqual(current.url, url)
        self.assertEqual("restricted", report["status"])

    def test_replacement_requires_two_checks_with_advancing_live_segments(self):
        current = entry()
        replacement = "https://example.com/new.m3u8"
        calls, delays = [], []
        responses = iter([
            repair.ProbeResult(False, "http_404"), repair.ProbeResult(False, "http_404"),
            repair.ProbeResult(True, "ok", replacement, "segment1.ts"),
            repair.ProbeResult(True, "ok", replacement, "segment2.ts"),
        ])

        def probe(url):
            calls.append(url)
            return next(responses)

        url, report = repair.repair_channel(current, [replacement], probe, delays.append)
        self.assertEqual(replacement, url)
        self.assertEqual("replaced", report["status"])
        self.assertEqual([current.url, current.url, replacement, replacement], calls)
        self.assertEqual([5, 7], delays)

    def test_frozen_backup_and_unavailable_backups_keep_original(self):
        responses = iter([
            repair.ProbeResult(False, "http_404"), repair.ProbeResult(False, "http_404"),
            repair.ProbeResult(True, "ok", "https://example.com/new.m3u8", "same.ts"),
            repair.ProbeResult(True, "ok", "https://example.com/new.m3u8", "same.ts"),
        ])
        current = entry()
        url, report = repair.repair_channel(current, ["https://example.com/new.m3u8"], lambda _: next(responses), lambda _: None)
        self.assertEqual(current.url, url)
        self.assertEqual("unresolved", report["status"])

    def test_mass_outage_keeps_all_channels_and_addresses(self):
        original = '#EXTM3U\n#EXTINF:-1 tvg-id="A.ru",A\nhttps://example.com/a.m3u8\n#EXTINF:-1 tvg-id="B.ru",B\nhttps://example.com/b.m3u8\n'
        backup = "https://example.com/backup.m3u8"
        counter = 0

        def probe(url):
            nonlocal counter
            counter += 1
            return repair.ProbeResult(True, "ok", backup, f"{counter}.ts") if url == backup else repair.ProbeResult(False, "http_503")

        updated, reports = repair.repaired_playlist(original, {"A.ru": [backup], "B.ru": [backup]}, probe, lambda _: None, workers=1)
        self.assertEqual(original, updated)
        self.assertTrue(all(item["reason"] == "mass_failure_guard" for item in reports))

    def test_source_catalog_cannot_change_channel_order(self):
        original = '#EXTM3U\n#EXTINF:-1 tvg-id="A.ru",A\nhttps://example.com/a.m3u8\n'
        with self.assertRaisesRegex(ValueError, "IDs and order"):
            repair.repaired_playlist(original, {"Other.ru": []}, None)


class SourceAndNetworkTests(unittest.TestCase):
    def test_explicit_alias_maps_only_the_same_broadcaster(self):
        text = '#EXTM3U\n#EXTINF:-1 tvg-id="Match.ru@SD",Match TV\nhttps://example.com/match.m3u8\n#EXTINF:-1 tvg-id="MatchPlaneta.ru@SD",Different\nhttps://example.com/match.m3u8\n'
        found = repair.candidates_from_feed(text, {"MatchTV.ru": ["https://example.com/match.m3u8"]}, {"Match.ru": "MatchTV.ru"})
        self.assertEqual({"MatchTV.ru": ["https://example.com/match.m3u8"]}, found)

    def test_catalog_preserves_all_channel_ids_and_rejects_unknown_origins(self):
        text = (repair.ROOT / "playlist.m3u8").read_text(encoding="utf-8")
        entries = repair.parse_playlist(text)
        config = json.loads((repair.ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
        candidates = dict(config["channels"])
        candidates[entries[0].channel_id] = ["https://unknown.example/other.m3u8"]
        catalog = repair.make_catalog(text, candidates, config)
        repair.validate_catalog(catalog, entries, config)
        self.assertEqual([item.channel_id for item in entries], list(catalog["channels"]))
        self.assertEqual([entries[0].url], catalog["channels"][entries[0].channel_id])
        catalog["channels"][entries[0].channel_id].append("https://unknown.example/other.m3u8")
        with self.assertRaisesRegex(ValueError, "Unapproved fallback"):
            repair.validate_catalog(catalog, entries, config)

    def test_internal_cdn_signatures_are_not_persistable_source_urls(self):
        url = "https://example.com/media.ts?s=public-cdn-signature"
        self.assertEqual("example.com", repair.validate_url(url, allow_signed=True).hostname)
        with self.assertRaisesRegex(repair.ProbeError, "signed_or_authenticated"):
            repair.validate_url(url)

    def test_channel_path_traversal_is_rejected(self):
        for path in ["../other/stream.m3u8", "%2e%2e/other/stream.m3u8", "%252e%252e/other/stream.m3u8"]:
            with self.assertRaisesRegex(repair.ProbeError, "unsafe_channel_path"):
                repair.validate_url("https://example.com/channel/" + path)

    def test_discovery_uses_exact_channel_id_and_main_edition_only(self):
        text = '''#EXTM3U
#EXTINF:-1 tvg-id="One.ru@SD",One
https://example.com/main.m3u8
#EXTINF:-1 tvg-id="One.ru@Plus2",One +2
https://example.com/shifted.m3u8
#EXTINF:-1 tvg-id="One.ru@Europa",One Europa
https://example.com/eu.m3u8
#EXTINF:-1 tvg-id="OneExtra.ru@SD",Other
https://example.com/other.m3u8
#EXTINF:-1 tvg-id="One.ru",One
https://example.com/page.html
#EXTINF:-1 tvg-id="One.ru@SD",One
https://example.com/auth.m3u8?token=secret
#EXTINF:-1 tvg-id="One.ru@SD",One SPB
https://example.com/spb.m3u8
'''
        self.assertEqual({"One.ru": ["https://example.com/main.m3u8"]}, repair.candidates_from_feed(text, {"One.ru": ["https://example.com/main.m3u8"]}))

    def test_channel_id_from_an_unapproved_origin_is_not_enough(self):
        text = '#EXTM3U\n#EXTINF:-1 tvg-id="One.ru@SD",Unrelated\nhttps://unknown.example/other.m3u8\n'
        self.assertEqual({"One.ru": []}, repair.candidates_from_feed(text, {"One.ru": ["https://example.com/one/"]}))

    def test_local_addresses_and_credentials_are_rejected(self):
        for url in ["file:///etc/passwd", "http://127.0.0.1/a.m3u8", "http://192.168.1.1/a.m3u8", "http://[::1]/a", "https://user:pass@example.com/a", "https://example.com/a?drmreq=x", "https://example.com/a\nInjected"]:
            with self.subTest(url=url), self.assertRaises(repair.ProbeError):
                repair.validate_url(url)

    def test_private_dns_is_rejected_before_connect(self):
        address = [(2, 1, 6, "", ("10.0.0.1", 443))]
        with patch.object(repair.socket, "getaddrinfo", return_value=address), patch.object(repair.socket, "create_connection") as connect:
            with self.assertRaisesRegex(repair.ProbeError, "non_public_address"):
                repair.fetch("https://example.com/index.m3u8")
            connect.assert_not_called()

    def test_redirect_to_private_address_is_rejected(self):
        from unittest.mock import MagicMock

        connection = MagicMock()
        response = connection.getresponse.return_value
        response.status = 302
        response.getheader.return_value = "http://127.0.0.1/secret"
        with patch.object(repair.socket, "getaddrinfo", return_value=[(2, 1, 6, "", ("8.8.8.8", 80))]), patch.object(repair.socket, "create_connection"), patch.object(repair.http.client, "HTTPConnection", return_value=connection):
            with self.assertRaisesRegex(repair.ProbeError, "non_public_address"):
                repair.fetch("http://example.com/stream")

    def test_repository_catalog_covers_exact_existing_channels(self):
        playlist = repair.parse_playlist((repair.ROOT / "playlist.m3u8").read_text(encoding="utf-8"))
        config = json.loads((repair.ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
        self.assertEqual([item.channel_id for item in playlist], list(config["channels"]))
        self.assertTrue(all(config["channels"].values()))


class HlsTests(unittest.TestCase):
    def test_ngenix_ended_placeholder_is_inconclusive_region_restriction(self):
        text = b'#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nplaceholder.ts\n#EXT-X-ENDLIST\n'
        url = "https://zabava-htlive.cdn.ngenix.net/hls/CH_1TVSD/variant.m3u8"
        with patch.object(repair, "fetch", return_value=(text, url)):
            self.assertEqual("restricted", repair.HlsProbe()(url).reason)
    def test_missing_duration_and_naive_time_fail_only_the_probe(self):
        for text, expected in [
            (b'#EXTM3U\n#EXTINF:6,\nx.ts\n', "invalid_target_duration"),
            (b'#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-PROGRAM-DATE-TIME:2026-10-14T10:00:00\n#EXTINF:6,\nx.ts\n', "invalid_program_date_time"),
        ]:
            with patch.object(repair, "fetch", return_value=(text, "https://example.com/x.m3u8")):
                self.assertEqual(expected, repair.HlsProbe()("https://example.com/x.m3u8").reason)

    def test_mixed_codec_master_publishes_only_verified_h264_rendition(self):
        from unittest.mock import MagicMock

        master = b'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1024x576\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS="hvc1.1.6.L93,ac-3",RESOLUTION=1280x720\nhigh.m3u8\n'
        media = b'#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\n1.ts\n'
        segment = (b'\x47' + bytes(187)) * 6
        codecs = {"streams": [{"codec_name": "h264", "height": 576}, {"codec_name": "aac"}]}
        with patch.object(repair, "fetch", side_effect=[(master, "https://example.com/master.m3u8"), (media, "https://example.com/low.m3u8"), (segment, "https://example.com/1.ts")]), patch.object(repair.subprocess, "run", return_value=MagicMock(stdout=json.dumps(codecs).encode())):
            result = repair.HlsProbe()("https://example.com/master.m3u8")
            self.assertTrue(result.ok)
            self.assertEqual("https://example.com/low.m3u8", result.playable_url)

    def test_inherited_program_time_rejects_a_stale_live_playlist(self):
        text = b'#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00Z\n#EXTINF:6,\na.ts\n#EXTINF:6,\nb.ts\n'
        with patch.object(repair, "fetch", return_value=(text, "https://example.com/x.m3u8")):
            self.assertEqual("stale_segment", repair.HlsProbe()("https://example.com/x.m3u8").reason)

    def test_html_is_not_treated_as_a_working_channel(self):
        with patch.object(repair, "fetch", return_value=(b"<html>error</html>", "https://example.com/x")):
            self.assertEqual("not_hls", repair.HlsProbe()("https://example.com/x").reason)

    def test_vod_and_encrypted_streams_are_rejected(self):
        for tail, expected in [("#EXT-X-ENDLIST", "not_live"), ("", "encrypted_hls")]:
            text = '#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXTINF:6,\nx.ts\n' + tail
            with patch.object(repair, "fetch", return_value=(text.encode(), "https://example.com/x.m3u8")):
                self.assertEqual(expected, repair.HlsProbe()("https://example.com/x.m3u8").reason)

    def test_fresh_media_must_contain_real_mpegts(self):
        text = b"#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nx.ts\n"
        with patch.object(repair, "fetch", side_effect=[(text, "https://example.com/x.m3u8"), (b"<html>failed</html>", "https://example.com/x.ts")]):
            self.assertEqual("not_mpegts", repair.HlsProbe()("https://example.com/x.m3u8").reason)

    def test_decoder_checks_real_video_and_audio_from_local_bytes(self):
        from unittest.mock import MagicMock

        sample = {"data": b"test", "format": "mpegts", "playable_url": "https://example.com/x.m3u8", "segment": "x.ts", "interval": 7}
        codecs = {"streams": [{"codec_name": "h264", "height": 576}, {"codec_name": "aac"}]}
        with patch.object(repair.HlsProbe, "sample", return_value=sample), patch.object(repair.subprocess, "run", return_value=MagicMock(stdout=json.dumps(codecs).encode())) as run:
            result = repair.HlsProbe()("https://example.com/x.m3u8")
            self.assertTrue(result.ok)
            self.assertEqual(2, run.call_count)
            self.assertIn("file,pipe", run.call_args_list[0].args[0])
            self.assertNotIn("https://example.com/x.m3u8", run.call_args_list[0].args[0])


class PublishTests(unittest.TestCase):
    def test_verified_replacement_preserves_channel_metadata_and_other_urls(self):
        original = (repair.ROOT / "playlist.m3u8").read_bytes()
        entries = repair.parse_playlist(original.decode("utf-8"))
        replacement = "https://zabava-htlive.cdn.ngenix.net/hls/CH_1TVSD/recovered.m3u8"
        updated = original.decode("utf-8").replace(entries[0].url, replacement)
        statuses = [{"id": item.channel_id, "status": "healthy", "reason": "ok"} for item in entries]
        statuses[0].update(status="replaced", url=replacement)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_policy(root)
            artifact = root / "artifact"
            artifact.mkdir()
            (root / "playlist.m3u8").write_bytes(original)
            (artifact / "playlist.m3u8").write_text(updated, encoding="utf-8")
            config = json.loads((repair.ROOT / "playlist-sources.json").read_text(encoding="utf-8"))
            catalog = repair.make_catalog(updated, config["channels"], config)
            catalog_bytes = json.dumps(catalog).encode()
            (artifact / "playlist-fallbacks.json").write_bytes(catalog_bytes)
            report = {"base_sha256": repair.digest(original), "policy_sha256": repair.policy_digests(), "catalog_sha256": repair.digest(catalog_bytes), "channels": statuses, "checked_at": "2026-10-14T10:00:00+00:00", "source_errors": []}
            (artifact / "report.json").write_text(json.dumps(report), encoding="utf-8")
            with patch.object(repair, "ROOT", root):
                repair.publish(artifact)
            published = repair.parse_playlist((root / "playlist.m3u8").read_text(encoding="utf-8"))
            self.assertEqual([item.info for item in entries], [item.info for item in published])
            self.assertEqual(replacement, published[0].url)
            self.assertEqual([item.url for item in entries[1:]], [item.url for item in published[1:]])
            self.assertEqual("2026-10-14", json.loads((root / "playlist-health.json").read_text())["checked_on"])

    def test_stale_report_cannot_overwrite_new_manual_playlist(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "playlist.m3u8").write_text("new manual playlist")
            (root / "report.json").write_text(json.dumps({"base_sha256": "old"}))
            with patch.object(repair, "ROOT", root), self.assertRaisesRegex(ValueError, "stale publication"):
                repair.publish(root)

    def test_unverified_url_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_policy(root)
            original = (repair.ROOT / "playlist.m3u8").read_bytes()
            entries = repair.parse_playlist(original.decode())
            artifact = root / "artifact"
            artifact.mkdir()
            (root / "playlist.m3u8").write_bytes(original)
            updated = original.decode().replace(entries[0].url, "https://zabava-htlive.cdn.ngenix.net/hls/CH_1TVSD/unverified.m3u8")
            (artifact / "playlist.m3u8").write_text(updated, encoding="utf-8")
            report = {"base_sha256": repair.digest(original), "policy_sha256": repair.policy_digests(), "channels": [{"id": item.channel_id, "status": "healthy"} for item in entries]}
            (artifact / "report.json").write_text(json.dumps(report))
            with patch.object(repair, "ROOT", root), self.assertRaisesRegex(ValueError, "Unverified replacement"):
                repair.publish(artifact)
            self.assertEqual(original, (root / "playlist.m3u8").read_bytes())

    def test_extra_untagged_channel_cannot_be_published(self):
        original = (repair.ROOT / "playlist.m3u8").read_bytes()
        entries = repair.parse_playlist(original.decode("utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_policy(root)
            artifact = root / "artifact"
            artifact.mkdir()
            (root / "playlist.m3u8").write_bytes(original)
            updated = original.decode("utf-8") + '#EXTINF:-1,Unrequested\nhttps://example.com/extra.m3u8\n'
            (artifact / "playlist.m3u8").write_text(updated, encoding="utf-8")
            report = {"base_sha256": repair.digest(original), "policy_sha256": repair.policy_digests(), "channels": [{"id": item.channel_id, "status": "healthy"} for item in entries]}
            (artifact / "report.json").write_text(json.dumps(report))
            with patch.object(repair, "ROOT", root), self.assertRaisesRegex(ValueError, "non-URL changes"):
                repair.publish(artifact)

    def test_policy_change_after_probing_rejects_publication(self):
        original = (repair.ROOT / "playlist.m3u8").read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_policy(root)
            (root / "playlist.m3u8").write_bytes(original)
            (root / "report.json").write_text(json.dumps({"base_sha256": repair.digest(original), "policy_sha256": repair.policy_digests()}))
            (root / "playlist-sources.json").write_text('{"changed":true}')
            with patch.object(repair, "ROOT", root), self.assertRaisesRegex(ValueError, "policy changed"):
                repair.publish(root)


if __name__ == "__main__":
    unittest.main()
