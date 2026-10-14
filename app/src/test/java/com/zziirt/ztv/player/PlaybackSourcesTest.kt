package com.zziirt.ztv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourcesTest {
    @Test
    fun `tries alternatives of the same channel once without duplicate primary`() {
        val sources = PlaybackSources("primary", listOf("primary", "backup", "backup", "last"))
        assertEquals("primary", sources.currentUrl)
        assertEquals("backup", sources.next())
        assertEquals("last", sources.next())
        assertNull(sources.next())
        assertFalse(sources.hasNext)
    }

    @Test
    fun `fresh catalog offers new sources without interrupting active playback`() {
        val sources = PlaybackSources("old", listOf("failed"))
        assertEquals("failed", sources.next())
        sources.update("new", listOf("failed", "reserve"))
        assertEquals("failed", sources.currentUrl)
        assertEquals("new", sources.next())
        assertEquals("reserve", sources.next())
        assertNull(sources.next())
    }

    @Test
    fun `caps attempts even if the catalog changes`() {
        val sources = PlaybackSources("0", (1..20).map(Int::toString))
        repeat(8) { sources.next() }
        sources.update("brand-new", emptyList())
        assertNull(sources.next())
    }
}
