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
            listOf(
                "ChannelOne.ru", "Russia1.ru", "MatchTV.ru", "NTV.ru", "Channel5.ru",
                "RussiaK.ru", "Russia24.ru", "Carousel.ru", "OTR.ru", "TVCentr.ru",
                "RENTV.ru", "Spas.ru", "STS.ru", "Domashniy.ru", "TV3.ru",
                "Friday.ru", "Zvezda.ru", "Mir.ru", "TNT.ru", "RBKTV.ru",
            ),
            channels.map { it.tvgId },
        )
        assertTrue(channels.all { it.url.startsWith("https://") || it.url.startsWith("http://") })
        assertTrue(channels.all { it.url.substringBefore('?').endsWith(".m3u8") })
    }
}
