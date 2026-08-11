package com.zziirt.ztv.channels

import com.zziirt.ztv.preferences.AppSettings

class FavoritesRepository {
    fun visibleChannels(channels: List<Channel>, settings: AppSettings): List<Channel> {
        if (!settings.favoritesOnly) return channels
        val favorites = orderedFavorites(channels, settings)
        return favorites.ifEmpty { channels }
    }

    fun orderedFavorites(channels: List<Channel>, settings: AppSettings): List<Channel> {
        val byKey = channels.associateBy { it.stableKey }
        val ordered = settings.favoriteOrder.mapNotNull { byKey[it] }
        val orderedKeys = ordered.map { it.stableKey }.toSet()
        val remaining = channels.filter { it.stableKey in settings.favoriteKeys && it.stableKey !in orderedKeys }
        return ordered + remaining
    }
}
