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
                "СТС",
                "Домашний",
                "ТВ-3",
                "Пятница",
                "Звезда",
                "МИР",
                "ТНТ",
                "РБК",
            ),
            channels.map { it.name },
        )
        assertEquals((1..20).toList(), channels.map { it.number })
        assertEquals(
            "https://live.smotrim.ru/vgtrk/0/russia24-hd/792000_576p.m3u8",
            channels.single { it.name == "Россия-24" }.url,
        )
    }
}
