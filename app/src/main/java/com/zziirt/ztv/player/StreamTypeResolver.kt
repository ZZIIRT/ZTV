package com.zziirt.ztv.player

import java.net.URI

enum class StreamType {
    Dash,
    Hls,
    MpegTs,
    Rtsp,
    Progressive,
    Unsupported,
}

object StreamTypeResolver {
    fun resolve(url: String): StreamType {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return StreamType.Unsupported
        if (uri.scheme.equals("rtsp", ignoreCase = true)) return StreamType.Rtsp

        val normalizedPath = uri.path.orEmpty().lowercase()
        val normalizedUrl = url.substringBefore('#').lowercase()
        return when {
            "/iframe/" in normalizedPath || normalizedPath.endsWith(".html") || normalizedPath.endsWith(".htm") ->
                StreamType.Unsupported
            normalizedPath.endsWith(".mpd") || ".mpd?" in normalizedUrl -> StreamType.Dash
            normalizedPath.endsWith(".m3u8") || ".m3u8?" in normalizedUrl -> StreamType.Hls
            normalizedPath.endsWith(".ts") || ".ts?" in normalizedUrl -> StreamType.MpegTs
            else -> StreamType.Progressive
        }
    }
}
