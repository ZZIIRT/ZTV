package com.zziirt.ztv.preferences

enum class TextSizeMode(val label: String, val scale: Float) {
    Normal("Обычный", 1.0f),
    Large("Большой", 1.18f),
    ExtraLarge("Очень большой", 1.35f);

    fun next(): TextSizeMode = entries[(ordinal + 1) % entries.size]
}

data class AppSettings(
    val playlistUrl: String = DEFAULT_PLAYLIST_URL,
    val lastChannelKey: String? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val favoriteOrder: List<String> = emptyList(),
    val favoritesOnly: Boolean = false,
    val autoPlayLastChannel: Boolean = true,
    val bootAutostart: Boolean = false,
    val skipUnavailableChannels: Boolean = true,
    val connectionTimeoutSeconds: Int = 10,
    val showLogos: Boolean = true,
    val showNumbers: Boolean = true,
    val textSizeMode: TextSizeMode = TextSizeMode.Large,
    val lastPlaylistUpdateEpochMillis: Long = 0L,
) {
    companion object {
        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_russia.m3u8"
    }
}
