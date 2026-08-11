package com.zziirt.ztv.ui

import com.zziirt.ztv.channels.Channel
import com.zziirt.ztv.preferences.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ZtvUiStateTest {
    private val one = Channel(1, "One", "https://example.com/one.m3u8", tvgId = "one")
    private val three = Channel(3, "Three", "https://example.com/three.m3u8", tvgId = "three")

    @Test
    fun `overlay autostart permission is disabled until Android grants it`() {
        assertFalse(ZtvUiState().overlayAutostartPermissionGranted)
    }

    @Test
    fun `channel list uses filtered channels outside reorder mode`() {
        val state = ZtvUiState(
            channels = listOf(one, three),
            visibleChannels = listOf(one),
        )

        assertEquals(listOf("one"), state.channelListChannels().map { it.stableKey })
    }

    @Test
    fun `channel list uses pending favorite order during reorder mode`() {
        val state = ZtvUiState(
            channels = listOf(one, three),
            visibleChannels = listOf(one, three),
            settings = AppSettings(
                favoriteKeys = setOf("one", "three"),
                favoriteOrder = listOf("one", "three"),
            ),
            isMovingFavorite = true,
            pendingFavoriteOrder = listOf("three", "one"),
        )

        assertEquals(listOf("three", "one"), state.channelListChannels().map { it.stableKey })
    }
}
