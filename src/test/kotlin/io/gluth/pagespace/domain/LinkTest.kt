package io.gluth.pagespace.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LinkTest {

    private val physics = Page("physics", "Physics")
    private val math    = Page("math", "Mathematics")

    @Test
    fun storesSourceAndTarget() {
        val link = Link(physics, math)
        assertEquals(physics, link.source)
        assertEquals(math, link.target)
    }

    @Test
    fun equalByEndpoints() {
        val a = Link(physics, math)
        val b = Link(physics, math)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun notEqualForDifferentEndpoints() {
        val a = Link(physics, math)
        val b = Link(math, physics)
        assertNotEquals(a, b)
    }

    @Test
    fun nullSourceThrows() {
        assertThrows<NullPointerException> { Link(null, math) }
    }

    @Test
    fun nullTargetThrows() {
        assertThrows<NullPointerException> { Link(physics, null) }
    }
}
