# Project Memory

## IPTV playback compatibility

- Do not assume an M3U playlist contains only HLS. The default Russia playlist mixes DASH, HLS, MPEG-TS-compatible streams, and HTML iframe pages.
- Resolve known stream types before creating a Media3 source and keep the matching Media3 modules in dependencies.
- Parse and cache remote playlists as bounded streams; replace the cache only after the downloaded playlist parses successfully.
- Defer playback recovery outside Media3 listener callbacks and bind callbacks to a playback generation so an old error cannot switch a newer channel.
