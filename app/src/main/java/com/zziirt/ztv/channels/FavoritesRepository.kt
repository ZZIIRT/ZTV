package com.zziirt.ztv.channels

import com.zziirt.ztv.preferences.AppSettings

class FavoritesRepository {
    fun visibleChannels(channels: List<Channel>, settings: AppSettings): List<Channel> {
        if (!settings.favoritesOnly) return channels
        return orderedFavorites(channels, settings)
    }

    fun orderedFavorites(
        channels: List<Channel>,
        settings: AppSettings,
        order: List<String> = settings.favoriteOrder,
    ): List<Channel> {
        val byKey = channels.associateBy { it.stableKey }
        val ordered = order.mapNotNull { byKey[it] }.filter { it.stableKey in settings.favoriteKeys }
        val orderedKeys = ordered.map { it.stableKey }.toSet()
        val remaining = channels.filter { it.stableKey in settings.favoriteKeys && it.stableKey !in orderedKeys }
        return ordered + remaining
    }
}
