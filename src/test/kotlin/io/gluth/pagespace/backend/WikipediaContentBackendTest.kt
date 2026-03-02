package io.gluth.pagespace.backend

import io.gluth.pagespace.domain.Page
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikipediaContentBackendTest {

    companion object {
        private val SUMMARY_JSON = """
            {
              "title": "Physics",
              "extract_html": "<p>Physics is the <a href=\"./Natural_science\">natural science</a> of <a href=\"./Matter\">matter</a>.</p>"
            }
        """.trimIndent()

        private val LEAD_SECTION_JSON = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<div><p><b>Physics</b> is the <a href=\"/wiki/Natural_science\">natural science</a> of <a href=\"/wiki/Matter\">matter</a>.</p></div>"
                }
              }
            }
        """.trimIndent()

        private val LINKS_JSON = """
            {
              "batchcomplete": "",
              "query": {
                "pages": {
                  "22939": {
                    "pageid": 22939,
                    "ns": 0,
                    "title": "Physics",
                    "links": [
                      {"ns": 0, "title": "Classical mechanics"},
                      {"ns": 0, "title": "Natural science"},
                      {"ns": 0, "title": "Quantum field theory"}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun stubBackend(responses: Map<String, String>): WikipediaContentBackend {
        return WikipediaContentBackend { url ->
            for ((key, value) in responses) {
                if (url.contains(key)) return@WikipediaContentBackend value
            }
            throw PageNotFoundException("No stub for $url")
        }
    }

    @Test
    fun fetchLinksRanksLeadSectionFirst() {
        val backend = stubBackend(mapOf(
            "action=parse" to LEAD_SECTION_JSON,
            "action=query" to LINKS_JSON
        ))
        val links = backend.fetchLinks("Physics")

        // Lead-section links come first: Natural science, Matter
        // Then API links not in lead section: Classical mechanics, Quantum field theory
        assertEquals("Natural science", links[0].id)
        assertEquals("Matter", links[1].id)
        // Remaining API links (Natural science is deduplicated)
        assertTrue(links.map { it.id }.contains("Classical mechanics"))
        assertTrue(links.map { it.id }.contains("Quantum field theory"))
        assertEquals(4, links.size)
    }

    @Test
    fun fetchLinksDeduplicatesLeadAndApiLinks() {
        // "Natural science" appears in both lead section and API links — should appear only once
        val backend = stubBackend(mapOf(
            "action=parse" to LEAD_SECTION_JSON,
            "action=query" to LINKS_JSON
        ))
        val links = backend.fetchLinks("Physics")
        val ids = links.map { it.id }
        assertEquals(1, ids.count { it == "Natural science" })
    }

    @Test
    fun fetchBodyStripsAnchorTags() {
        val backend = stubBackend(mapOf(
            "rest_v1/page/summary" to SUMMARY_JSON
        ))
        val body = backend.fetchBody("Physics")
        assertFalse(body.contains("<a "))
        assertFalse(body.contains("</a>"))
        assertTrue(body.contains("natural science"))
        assertTrue(body.contains("matter"))
    }

    @Test
    fun fetchBodyCachesResults() {
        var callCount = 0
        val backend = WikipediaContentBackend { url ->
            callCount++
            SUMMARY_JSON
        }
        backend.fetchBody("Physics")
        backend.fetchBody("Physics")
        // Summary fetch should only be called once due to caching
        assertEquals(1, callCount)
    }

    @Test
    fun fetchLinksCachesResults() {
        var callCount = 0
        val backend = WikipediaContentBackend { url ->
            callCount++
            when {
                url.contains("action=parse") -> LEAD_SECTION_JSON
                url.contains("action=query") -> LINKS_JSON
                else -> throw PageNotFoundException("No stub for $url")
            }
        }
        backend.fetchLinks("Physics")
        val countAfterFirst = callCount
        backend.fetchLinks("Physics")
        assertEquals(countAfterFirst, callCount)
    }

    @Test
    fun networkErrorWrappedAsPageNotFoundException() {
        val backend = WikipediaContentBackend { url ->
            throw java.io.IOException("Connection refused")
        }
        assertThrows<PageNotFoundException> { backend.fetchBody("Physics") }
    }

    @Test
    fun defaultPageIsPhysics() {
        val backend = WikipediaContentBackend { throw AssertionError("should not be called") }
        assertEquals(Page("Physics", "Physics"), backend.defaultPage())
    }
}
