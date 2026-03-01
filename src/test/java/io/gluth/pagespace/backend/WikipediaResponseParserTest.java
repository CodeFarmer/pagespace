package io.gluth.pagespace.backend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaResponseParserTest {

    private static final String SUMMARY_JSON = """
            {
              "title": "Physics",
              "extract_html": "<p>Physics is the <a href=\\"./Natural_science\\">natural science</a> of matter.</p>"
            }
            """;

    private static final String LINKS_JSON = """
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
            """;

    @Test
    void parseSummaryTitleExtractsTitle() {
        String title = WikipediaResponseParser.parseSummaryTitle(SUMMARY_JSON);
        assertEquals("Physics", title);
    }

    @Test
    void parseSummaryExtractHtmlReturnsHtmlString() {
        String html = WikipediaResponseParser.parseSummaryExtractHtml(SUMMARY_JSON);
        assertTrue(html.contains("Physics is the"), "Should contain summary text");
    }

    @Test
    void parseLinksReturnsAllTitles() {
        List<String> links = WikipediaResponseParser.parseLinks(LINKS_JSON);
        assertEquals(3, links.size());
        assertTrue(links.contains("Absolute zero"));
        assertTrue(links.contains("Classical mechanics"));
        assertTrue(links.contains("Quantum field theory"));
    }

    @Test
    void parseLinksReturnsEmptyListForMissingLinksField() {
        String json = """
                {
                  "query": {
                    "pages": {
                      "1": { "pageid": 1, "ns": 0, "title": "Empty" }
                    }
                  }
                }
                """;
        List<String> links = WikipediaResponseParser.parseLinks(json);
        assertTrue(links.isEmpty());
    }

    @Test
    void parseSummaryExtractHtmlMissingKeyReturnsEmpty() {
        String json = "{\"title\": \"X\"}";
        String html = WikipediaResponseParser.parseSummaryExtractHtml(json);
        assertEquals("", html);
    }
}
