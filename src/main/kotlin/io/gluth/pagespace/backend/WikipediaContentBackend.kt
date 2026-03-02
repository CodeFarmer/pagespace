package io.gluth.pagespace.backend

import io.gluth.pagespace.domain.Page
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class WikipediaContentBackend : ContentBackend {

    companion object {
        private const val SUMMARY_BASE =
            "https://en.wikipedia.org/api/rest_v1/page/summary/"
        private const val LINKS_BASE =
            "https://en.wikipedia.org/w/api.php" +
            "?action=query&prop=links&pllimit=100&plnamespace=0&format=json&titles="
        private const val LEAD_SECTION_BASE =
            "https://en.wikipedia.org/w/api.php" +
            "?action=parse&prop=text&section=0&format=json&page="
        private const val USER_AGENT =
            "page-space/0.1 (https://github.com/CodeFarmer/page-space; educational)"
        private val DEFAULT_PAGE = Page("Physics", "Physics")
    }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val summaryCache: MutableMap<String, String>     = HashMap()
    private val bodyCache:    MutableMap<String, String>    = HashMap()
    private val linkCache:    MutableMap<String, List<Page>> = HashMap()

    override fun defaultPage(): Page = DEFAULT_PAGE

    override fun fetchBody(id: String): String {
        bodyCache[id]?.let { return it }

        val summaryJson = fetchSummary(id)
        val extractHtml = WikipediaResponseParser.parseSummaryExtractHtml(summaryJson)
        val cleaned = extractHtml.replace(Regex("<a[^>]*>"), "").replace("</a>", "")

        bodyCache[id] = cleaned
        return cleaned
    }

    override fun fetchLinks(id: String): List<Page> {
        linkCache[id]?.let { return it }

        val leadJson = get(LEAD_SECTION_BASE + encode(id))
        val leadTitles = WikipediaResponseParser.parseLeadSectionLinks(leadJson)

        val linksJson = get(LINKS_BASE + encode(id))
        val apiTitles = WikipediaResponseParser.parseLinks(linksJson)

        val leadSet = leadTitles.toSet()
        val ranked = leadTitles + apiTitles.filter { it !in leadSet }
        val pages = ranked.map { Page(it, it) }
        linkCache[id] = pages
        return pages
    }

    private fun fetchSummary(id: String): String {
        summaryCache[id]?.let { return it }
        val json = get(SUMMARY_BASE + encode(id))
        summaryCache[id] = json
        return json
    }

    private fun get(url: String): String {
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 404) throw PageNotFoundException(url)
            if (resp.statusCode() != 200)
                throw PageNotFoundException("HTTP ${resp.statusCode()} for $url")
            return resp.body()
        } catch (e: PageNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw PageNotFoundException("Network error fetching $url: ${e.message}")
        }
    }

    private fun encode(id: String): String = id.replace(' ', '_')
}
