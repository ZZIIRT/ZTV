package com.zziirt.ztv.ui

import com.zziirt.ztv.channels.Channel
import com.zziirt.ztv.preferences.AppSettings

data class ZtvUiState(
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val currentChannel: Channel? = null,
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isChannelListOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val channelListIndex: Int = 0,
    val settingsIndex: Int = 0,
    val osdVisible: Boolean = false,
    val statusMessage: String? = "Загрузка каналов...",
    val directInput: String = "",
    val isMovingFavorite: Boolean = false,
)
