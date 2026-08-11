package com.zziirt.ztv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlResolverTest {
    @Test
    fun `maps legacy Smotrim iframe ids to official HLS streams`() {
        assertEquals(
            "https://live.smotrim.ru/vgtrk/0/russia1-hd/index.m3u8",
            StreamUrlResolver.resolve("https://player.smotrim.ru/iframe/stream/live_id/2961"),
        )
        assertEquals(
            "https://live.smotrim.ru/vgtrk/0/russia24-hd/index.m3u8",
            StreamUrlResolver.resolve("https://player.smotrim.ru/iframe/stream/live_id/21"),
        )
        assertEquals(
            "https://live.smotrim.ru/vgtrk/0/kultura-hd/index.m3u8",
            StreamUrlResolver.resolve("https://player.smotrim.ru/iframe/stream/live_id/19201"),
        )
    }

    @Test
    fun `leaves unrelated and unknown iframe urls unchanged`() {
        val direct = "https://example.com/channel.m3u8"
        val unknown = "https://player.smotrim.ru/iframe/stream/live_id/99999"

        assertEquals(direct, StreamUrlResolver.resolve(direct))
        assertEquals(unknown, StreamUrlResolver.resolve(unknown))
    }
}
