package com.zziirt.ztv.preferences

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object PreferencesRecovery {
    fun replacement() = emptyPreferences()
}

private val Context.dataStore by preferencesDataStore(
    name = "ztv_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { PreferencesRecovery.replacement() },
)

class PreferencesRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val textSizeName = prefs[Keys.textSizeMode] ?: TextSizeMode.Large.name
        AppSettings(
            playlistUrl = prefs[Keys.playlistUrl] ?: AppSettings.DEFAULT_PLAYLIST_URL,
            lastChannelKey = prefs[Keys.lastChannelKey],
            favoriteKeys = prefs[Keys.favoriteKeys] ?: emptySet(),
            favoriteOrder = prefs[Keys.favoriteOrder]?.split(LIST_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList(),
            favoritesOnly = prefs[Keys.favoritesOnly] ?: false,
            autoPlayLastChannel = prefs[Keys.autoPlayLastChannel] ?: true,
            bootAutostart = prefs[Keys.bootAutostart] ?: true,
            skipUnavailableChannels = prefs[Keys.skipUnavailableChannels] ?: true,
            connectionTimeoutSeconds = prefs[Keys.connectionTimeoutSeconds] ?: 10,
            showLogos = prefs[Keys.showLogos] ?: true,
            showNumbers = prefs[Keys.showNumbers] ?: true,
            textSizeMode = TextSizeMode.entries.firstOrNull { it.name == textSizeName } ?: TextSizeMode.Large,
            lastPlaylistUpdateEpochMillis = prefs[Keys.lastPlaylistUpdateEpochMillis] ?: 0L,
        )
    }

    suspend fun saveLastChannel(channelKey: String) {
        context.dataStore.edit { it[Keys.lastChannelKey] = channelKey }
    }

    suspend fun setLastPlaylistUpdate(epochMillis: Long) {
        context.dataStore.edit { it[Keys.lastPlaylistUpdateEpochMillis] = epochMillis }
    }

    suspend fun toggleFavorite(channelKey: String) {
        context.dataStore.edit { prefs ->
            val favorites = (prefs[Keys.favoriteKeys] ?: emptySet()).toMutableSet()
            val order = prefs[Keys.favoriteOrder]
                ?.split(LIST_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()

            if (favorites.contains(channelKey)) {
                favorites.remove(channelKey)
                order.remove(channelKey)
            } else {
                favorites.add(channelKey)
                if (!order.contains(channelKey)) order.add(channelKey)
            }

            prefs[Keys.favoriteKeys] = favorites
            prefs[Keys.favoriteOrder] = order.joinToString(LIST_SEPARATOR)
        }
    }

    suspend fun setFavoriteOrder(order: List<String>) {
        context.dataStore.edit { prefs ->
            val favorites = prefs[Keys.favoriteKeys] ?: emptySet()
            prefs[Keys.favoriteOrder] = order.filter { it in favorites }.joinToString(LIST_SEPARATOR)
        }
    }

    suspend fun clearFavorites() {
        context.dataStore.edit {
            it[Keys.favoriteKeys] = emptySet()
            it[Keys.favoriteOrder] = ""
        }
    }

    suspend fun toggleFavoritesOnly() {
        context.dataStore.edit { it[Keys.favoritesOnly] = !(it[Keys.favoritesOnly] ?: false) }
    }

    suspend fun toggleAutoPlay() {
        context.dataStore.edit { it[Keys.autoPlayLastChannel] = !(it[Keys.autoPlayLastChannel] ?: true) }
    }

    suspend fun toggleBootAutostart() {
        context.dataStore.edit { it[Keys.bootAutostart] = !(it[Keys.bootAutostart] ?: true) }
    }

    suspend fun toggleSkipUnavailable() {
        context.dataStore.edit { it[Keys.skipUnavailableChannels] = !(it[Keys.skipUnavailableChannels] ?: true) }
    }

    suspend fun cycleTimeout() {
        context.dataStore.edit {
            val current = it[Keys.connectionTimeoutSeconds] ?: 10
            it[Keys.connectionTimeoutSeconds] = when (current) {
                5 -> 10
                10 -> 15
                else -> 5
            }
        }
    }

    suspend fun cycleTextSize() {
        context.dataStore.edit {
            val current = TextSizeMode.entries.firstOrNull { mode -> mode.name == it[Keys.textSizeMode] }
                ?: TextSizeMode.Large
            it[Keys.textSizeMode] = current.next().name
        }
    }

    suspend fun toggleShowLogos() {
        context.dataStore.edit { it[Keys.showLogos] = !(it[Keys.showLogos] ?: true) }
    }

    suspend fun toggleShowNumbers() {
        context.dataStore.edit { it[Keys.showNumbers] = !(it[Keys.showNumbers] ?: true) }
    }

    private object Keys {
        val playlistUrl = stringPreferencesKey("playlist_url")
        val lastChannelKey = stringPreferencesKey("last_channel_key")
        val favoriteKeys = stringSetPreferencesKey("favorite_keys")
        val favoriteOrder = stringPreferencesKey("favorite_order")
        val favoritesOnly = booleanPreferencesKey("favorites_only")
        val autoPlayLastChannel = booleanPreferencesKey("auto_play_last_channel")
        val bootAutostart = booleanPreferencesKey("boot_autostart")
        val skipUnavailableChannels = booleanPreferencesKey("skip_unavailable_channels")
        val connectionTimeoutSeconds = intPreferencesKey("connection_timeout_seconds")
        val showLogos = booleanPreferencesKey("show_logos")
        val showNumbers = booleanPreferencesKey("show_numbers")
        val textSizeMode = stringPreferencesKey("text_size_mode")
        val lastPlaylistUpdateEpochMillis = longPreferencesKey("last_playlist_update_epoch_millis")
    }

    private companion object {
        const val LIST_SEPARATOR = "\n"
    }
}
