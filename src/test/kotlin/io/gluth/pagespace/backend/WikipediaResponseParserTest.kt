package io.gluth.pagespace.backend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikipediaResponseParserTest {

    companion object {
        private val SUMMARY_JSON = """
            {
              "title": "Physics",
              "extract_html": "<p>Physics is the <a href=\"./Natural_science\">natural science</a> of matter.</p>"
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
                      {"ns": 0, "title": "Absolute zero"},
                      {"ns": 0, "title": "Classical mechanics"},
                      {"ns": 0, "title": "Quantum field theory"}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
    }

    @Test
    fun parseSummaryTitleExtractsTitle() {
        val title = WikipediaResponseParser.parseSummaryTitle(SUMMARY_JSON)
        assertEquals("Physics", title)
    }

    @Test
    fun parseSummaryExtractHtmlReturnsHtmlString() {
        val html = WikipediaResponseParser.parseSummaryExtractHtml(SUMMARY_JSON)
        assertTrue(html.contains("Physics is the"), "Should contain summary text")
    }

    @Test
    fun parseLinksReturnsAllTitles() {
        val links = WikipediaResponseParser.parseLinks(LINKS_JSON)
        assertEquals(3, links.size)
        assertTrue(links.contains("Absolute zero"))
        assertTrue(links.contains("Classical mechanics"))
        assertTrue(links.contains("Quantum field theory"))
    }

    @Test
    fun parseLinksReturnsEmptyListForMissingLinksField() {
        val json = """
            {
              "query": {
                "pages": {
                  "1": { "pageid": 1, "ns": 0, "title": "Empty" }
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLinks(json)
        assertTrue(links.isEmpty())
    }

    @Test
    fun parseSummaryExtractHtmlMissingKeyReturnsEmpty() {
        val json = """{"title": "X"}"""
        val html = WikipediaResponseParser.parseSummaryExtractHtml(json)
        assertEquals("", html)
    }

    @Test
    fun parseLeadSectionLinksExtractsTitlesInOrder() {
        val json = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<div><p><b>Physics</b> is the <a href=\"/wiki/Natural_science\">natural science</a> of <a href=\"/wiki/Matter\">matter</a> and <a href=\"/wiki/Fundamental_interaction\">fundamental interactions</a>.</p></div>"
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertEquals(listOf("Natural science", "Matter", "Fundamental interaction"), links)
    }

    @Test
    fun parseLeadSectionLinksDeduplicates() {
        val json = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<p><a href=\"/wiki/Matter\">matter</a> and <a href=\"/wiki/Matter\">matter again</a></p>"
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertEquals(listOf("Matter"), links)
    }

    @Test
    fun parseLeadSectionLinksIgnoresNonArticleLinks() {
        val json = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<p><a href=\"/wiki/Matter\">matter</a> and <a href=\"/wiki/Help:Contents\">help</a> and <a href=\"/wiki/Energy#Forms\">energy</a></p>"
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertEquals(listOf("Matter"), links)
    }

    @Test
    fun parseLeadSectionLinksReturnsEmptyForNoLinks() {
        val json = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<p>Physics is a science.</p>"
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertTrue(links.isEmpty())
    }

    @Test
    fun parseLeadSectionLinksReturnsEmptyForMissingParseKey() {
        val json = """{"title": "Physics"}"""
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertTrue(links.isEmpty())
    }

    @Test
    fun parseLeadSectionLinksDecodesUrlEncodedTitles() {
        val json = """
            {
              "parse": {
                "title": "Physics",
                "text": {
                  "*": "<p><a href=\"/wiki/Maxwell%27s_equations\">Maxwell's equations</a></p>"
                }
              }
            }
        """.trimIndent()
        val links = WikipediaResponseParser.parseLeadSectionLinks(json)
        assertEquals(listOf("Maxwell's equations"), links)
    }
}
