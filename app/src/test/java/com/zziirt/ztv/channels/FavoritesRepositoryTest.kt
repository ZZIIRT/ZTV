package com.zziirt.ztv.channels

import com.zziirt.ztv.preferences.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRepositoryTest {
    private val repository = FavoritesRepository()
    private val channels = listOf(
        Channel(number = 1, name = "One", url = "https://example.com/one.m3u8", tvgId = "one"),
        Channel(number = 2, name = "Two", url = "https://example.com/two.m3u8", tvgId = "two"),
        Channel(number = 3, name = "Three", url = "https://example.com/three.m3u8", tvgId = "three"),
    )

    @Test
    fun `favorites only returns an empty list when no favorites are selected`() {
        val visible = repository.visibleChannels(channels, AppSettings(favoritesOnly = true))

        assertTrue(visible.isEmpty())
    }

    @Test
    fun `favorites only follows saved favorite order`() {
        val settings = AppSettings(
            favoriteKeys = setOf("one", "three"),
            favoriteOrder = listOf("three", "one"),
            favoritesOnly = true,
        )

        assertEquals(listOf("three", "one"), repository.visibleChannels(channels, settings).map { it.stableKey })
    }
}
