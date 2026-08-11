package com.zziirt.ztv.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelAnnouncementQueueTest {
    @Test
    fun `speaks only latest channel queued before initialization`() {
        val spoken = mutableListOf<String>()
        val queue = ChannelAnnouncementQueue(spoken::add)

        queue.announce("Первый канал")
        queue.announce("Россия 1")
        queue.markReady()

        assertEquals(listOf("Россия 1"), spoken)
    }

    @Test
    fun `speaks channel immediately after initialization`() {
        val spoken = mutableListOf<String>()
        val queue = ChannelAnnouncementQueue(spoken::add)

        queue.markReady()
        queue.announce("  НТВ  ")

        assertEquals(listOf("НТВ"), spoken)
    }
}
