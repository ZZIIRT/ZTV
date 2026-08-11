package com.zziirt.ztv.channels

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteOrderTest {
    @Test
    fun `moves a favorite and stops at list boundaries`() {
        assertEquals(listOf("two", "one", "three"), FavoriteOrder.move(listOf("one", "two", "three"), "two", -1))
        assertEquals(listOf("one", "three", "two"), FavoriteOrder.move(listOf("one", "two", "three"), "two", 1))
        assertEquals(listOf("one", "two", "three"), FavoriteOrder.move(listOf("one", "two", "three"), "one", -1))
        assertEquals(listOf("one", "two", "three"), FavoriteOrder.move(listOf("one", "two", "three"), "three", 1))
    }
}
