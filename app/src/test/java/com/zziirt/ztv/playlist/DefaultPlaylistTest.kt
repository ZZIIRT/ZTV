package com.zziirt.ztv.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DefaultPlaylistTest {
    @Test
    fun `default playlist contains only requested channels in exact order`() {
        val playlistFile = File(requireNotNull(System.getProperty("ztv.defaultPlaylist")))

        assertTrue("Root playlist.m3u8 must exist", playlistFile.isFile)

        val channels = playlistFile.inputStream().use(M3uParser()::parse)
        assertEquals(
            listOf(
                "Первый канал",
                "Россия 1",
                "Матч ТВ",
                "НТВ",
                "Пятый канал",
                "Россия Культура",
                "Россия-24",
                "Карусель",
                "ОТР",
                "ТВ Центр",
                "РЕН ТВ",
                "СПАС",
            ),
            channels.map { it.name },
        )
        assertEquals((1..12).toList(), channels.map { it.number })
    }
}
