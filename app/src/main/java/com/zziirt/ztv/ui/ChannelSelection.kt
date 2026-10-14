package com.zziirt.ztv.ui

import com.zziirt.ztv.channels.Channel

internal fun selectRefreshedChannel(
    channels: List<Channel>,
    visibleChannels: List<Channel>,
    currentKey: String?,
    lastKey: String?,
): Channel? {
    channels.firstOrNull { it.stableKey == currentKey }?.let { return it }
    val preferred = visibleChannels.ifEmpty { channels }
    return preferred.firstOrNull { it.stableKey == lastKey } ?: preferred.firstOrNull()
}
