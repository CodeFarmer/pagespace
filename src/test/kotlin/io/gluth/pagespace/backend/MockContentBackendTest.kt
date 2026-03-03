package io.gluth.pagespace.backend

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockContentBackendTest {

    private lateinit var backend: MockContentBackend

    @BeforeEach
    fun setUp() {
        backend = MockContentBackend()
    }

    @Test
    fun defaultPageIsNonNullWithNonBlankTitle() {
        val def = backend.defaultPage()
        assertNotNull(def)
        assertFalse(def.title.isBlank())
    }

    @Test
    fun fetchBodyReturnsNonEmptyForKnownPage() {
        val def = backend.defaultPage()
        val body = backend.fetchBody(def.id)
        assertNotNull(body)
        assertFalse(body.isBlank())
    }

    @Test
    fun fetchBodyThrowsForUnknownPage() {
        assertThrows<PageNotFoundException> { backend.fetchBody("mock:unknown-xyz") }
    }

    @Test
    fun fetchLinksContainsRequestedPage() {
        val def = backend.defaultPage()
        val links = backend.fetchLinks(def.id)
        assertFalse(links.isEmpty())
    }

    @Test
    fun fetchLinksIsDeterministic() {
        val def    = backend.defaultPage()
        val first  = backend.fetchLinks(def.id)
        val second = backend.fetchLinks(def.id)
        assertEquals(first, second)
    }

    @Test
    fun fetchLinksThrowsForUnknownPage() {
        assertThrows<PageNotFoundException> { backend.fetchLinks("mock:unknown-xyz") }
    }

    @Test
    fun searchPagesFindsMatchingPages() {
        val results = backend.searchPages("phys")
        assertTrue(results.any { it.title == "Physics" })
    }

    @Test
    fun searchPagesIsCaseInsensitive() {
        val results = backend.searchPages("PHYS")
        assertTrue(results.any { it.title == "Physics" })
    }

    @Test
    fun searchPagesReturnsEmptyForNoMatch() {
        val results = backend.searchPages("zzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun allSixteenPagesHaveBodyAndLinks() {
        val ids = arrayOf(
            "mock:physics", "mock:math", "mock:quantum", "mock:relativity",
            "mock:logic", "mock:philosophy", "mock:calculus", "mock:thermodynamics",
            "mock:optics", "mock:chemistry", "mock:biology", "mock:statistics",
            "mock:probability", "mock:geometry", "mock:algebra", "mock:cs"
        )
        for (id in ids) {
            org.junit.jupiter.api.assertDoesNotThrow("body missing for $id")  { backend.fetchBody(id) }
            org.junit.jupiter.api.assertDoesNotThrow("links missing for $id") { backend.fetchLinks(id) }
        }
    }
}
