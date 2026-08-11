# Project Memory

## IPTV playback compatibility

- Do not assume an M3U playlist contains only HLS. The default Russia playlist mixes DASH, HLS, MPEG-TS-compatible streams, and HTML iframe pages.
- Resolve known stream types before creating a Media3 source and keep the matching Media3 modules in dependencies.
- Parse and cache remote playlists as bounded streams; replace the cache only after the downloaded playlist parses successfully.
- Defer playback recovery outside Media3 listener callbacks and bind callbacks to a playback generation so an old error cannot switch a newer channel.
- The default playlist still publishes stale Smotrim iframe IDs. Resolve known IDs 2961, 21, and 19201 to the current official Russia 1, Russia 24, and Russia K HLS manifests before selecting a Media3 source.
- Channel-list rendering and navigation must use the same filtered collection; never fall back to all channels when favorites-only is active and empty.
- Favorite reordering is a draft UI operation: keep a pending order, persist it on OK, and discard it on BACK.
- Keep minSdk 23 compatibility visible to lint: avoid Java 8 collection methods introduced at API 24, and handle TV remote keys through Activity.onKeyDown instead of the restricted ComponentActivity.dispatchKeyEvent override.
- Keep the curated default channel list in the repository root `playlist.m3u8`; the TV app loads its raw GitHub URL on startup and every 15 minutes so channel fixes do not require a new APK.
