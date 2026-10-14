package com.zziirt.ztv.ui

import com.zziirt.ztv.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSelectionTest {
    @Test
    fun `refresh keeps the playing channel even before saved last channel catches up`() {
        val one = Channel(1, "One", "https://example.com/new.m3u8", tvgId = "one", alternativeUrls = listOf("https://example.com/backup.m3u8"))
        val two = Channel(2, "Two", "https://example.com/two.m3u8", tvgId = "two")
        assertEquals(one, selectRefreshedChannel(listOf(one, two), listOf(two), "one", "two"))
    }

    @Test
    fun `first launch chooses saved channel in visible list`() {
        val one = Channel(1, "One", "https://example.com/one.m3u8", tvgId = "one")
        val two = Channel(2, "Two", "https://example.com/two.m3u8", tvgId = "two")
        assertEquals(two, selectRefreshedChannel(listOf(one, two), listOf(one, two), null, "two"))
    }
}
