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

class ChannelAnnouncerRetryPolicyTest {
    @Test
    fun `retries cover a speech engine that starts slowly after boot`() {
        val delays = (0..4).map(ChannelAnnouncerRetryPolicy::delayAfterFailure)

        assertEquals(listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L), delays)
        assertEquals(null, ChannelAnnouncerRetryPolicy.delayAfterFailure(5))
    }
}
