package io.gluth.pagespace.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PageTest {

    @Test
    fun storesIdAndTitle() {
        val page = Page("id1", "Title One")
        assertEquals("id1", page.id)
        assertEquals("Title One", page.title)
    }

    @Test
    fun equalById() {
        val a = Page("same", "Alpha")
        val b = Page("same", "Beta")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun notEqualForDifferentIds() {
        val a = Page("id1", "Alpha")
        val b = Page("id2", "Alpha")
        assertNotEquals(a, b)
    }
}
