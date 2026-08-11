package com.zziirt.ztv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTypeResolverTest {
    @Test
    fun `resolves common live stream formats without relying on Media3 sniffing`() {
        assertEquals(StreamType.Dash, StreamTypeResolver.resolve("https://example.com/live/channel.mpd"))
        assertEquals(StreamType.Hls, StreamTypeResolver.resolve("https://example.com/live/channel.m3u8?token=abc"))
        assertEquals(StreamType.MpegTs, StreamTypeResolver.resolve("http://example.com/live/channel.ts"))
        assertEquals(StreamType.Progressive, StreamTypeResolver.resolve("https://example.com/live/channel"))
        assertEquals(StreamType.Unsupported, StreamTypeResolver.resolve("https://example.com/iframe/stream/live_id/1"))
    }
}
