package com.zziirt.ztv.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test
    fun `parses real playlist entries with BOM and quoted commas`() {
        val playlist = "\uFEFF" + """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="Russia, Federal",First Channel
            https://edge.example.com/live/channel.mpd
            #EXTINF:-1 tvg-id="match" group-title="Russia",Match TV
            https://edge.example.com/live/channel.m3u8
        """.trimIndent()

        val channels = M3uParser().parse(playlist.byteInputStream())

        assertEquals(2, channels.size)
        assertEquals("First Channel", channels[0].name)
        assertEquals("Russia, Federal", channels[0].groupTitle)
        assertEquals(1, channels[0].number)
        assertEquals(2, channels[1].number)
    }
}
