package com.zziirt.ztv.player

import java.net.URI

object StreamUrlResolver {
    fun resolve(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        if (!uri.host.equals(SMOTRIM_PLAYER_HOST, ignoreCase = true)) return trimmed

        val legacyId = LEGACY_PATH.matchEntire(uri.path.orEmpty())?.groupValues?.get(1) ?: return trimmed
        return SMOTRIM_STREAMS[legacyId] ?: trimmed
    }

    private val LEGACY_PATH = Regex("/iframe/stream/live_id/(\\d+)/?")
    private const val SMOTRIM_PLAYER_HOST = "player.smotrim.ru"
    private val SMOTRIM_STREAMS = mapOf(
        "2961" to "https://live.smotrim.ru/vgtrk/0/russia1-hd/index.m3u8",
        "21" to "https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8",
        "19201" to "https://live.smotrim.ru/vgtrk/0/kultura-hd/index.m3u8",
    )
}
