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
- Announce channel changes through a lifecycle-owned Android TextToSpeech instance. Keep only the latest name while TTS initializes and use QUEUE_FLUSH so rapid channel switching never builds a stale speech queue.
- Keep boot receivers non-blocking with goAsync, avoid credential-encrypted reads during locked boot, and schedule a delayed immutable getActivity PendingIntent as a fallback when direct background activity launch is blocked.
- Google TV and some Android TV firmware still block boot-receiver activity launches. Offer an opt-in AccessibilityService fallback with canRetrieveWindowContent=false, no event handling, and a short post-boot launch window; the user must enable it once in system settings.
- A direct-boot AccessibilityService must never read credential-protected DataStore in onServiceConnected: an uncaught early-boot storage failure can create a process crash loop. Keep boot retries storage-free, guarded by a CoroutineExceptionHandler, and confirm successful Activity creation in process.
- TCL Google TV firmware based on STT2.230203.001 can still deny AccessibilityService activity starts. Android documents granted SYSTEM_ALERT_WINDOW as a BAL exception, so expose an explicit overlay-permission settings action and status; never create an overlay window when the permission is only needed for boot launch.
- Power loss can corrupt the Preferences DataStore proto and crash every app entry point before playback. Always configure preferencesDataStore with ReplaceFileCorruptionHandler and reset to emptyPreferences so defaults restore automatically.
- Google TTS can hit an ANR when an Android TV app autostarts during boot, then recover after voice resources load. Channel announcements need a bounded initialization timeout and retry schedule instead of treating the first TTS failure as permanent.
