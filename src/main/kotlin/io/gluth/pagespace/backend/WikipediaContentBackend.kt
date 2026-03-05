package io.gluth.pagespace.backend

import io.gluth.pagespace.domain.Page
import java.io.IOException
import java.net.URLEncoder

class WikipediaContentBackend(
    private val httpGet: (String) -> String
) : ContentBackend {

    companion object {
        internal const val SUMMARY_BASE =
            "https://en.wikipedia.org/api/rest_v1/page/summary/"
        internal const val LINKS_BASE =
            "https://en.wikipedia.org/w/api.php" +
            "?action=query&prop=links&pllimit=100&plnamespace=0&format=json&titles="
        internal const val LEAD_SECTION_BASE =
            "https://en.wikipedia.org/w/api.php" +
            "?action=parse&prop=text&section=0&format=json&page="
        internal const val SEARCH_BASE =
            "https://en.wikipedia.org/w/api.php" +
            "?action=opensearch&limit=10&namespace=0&format=json&search="
        private const val USER_AGENT =
            "page-space/0.1 (https://github.com/CodeFarmer/page-space; educational)"
        private val DEFAULT_PAGE = Page("Physics", "Physics")
        private const val MAX_CACHE_SIZE = 200
    }

    private val summaryCache: MutableMap<String, String>     = lruCache(MAX_CACHE_SIZE)
    private val bodyCache:    MutableMap<String, String>      = lruCache(MAX_CACHE_SIZE)
    private val linkCache:    MutableMap<String, List<Page>>  = lruCache(MAX_CACHE_SIZE)

    override fun defaultPage(): Page = DEFAULT_PAGE

    override fun fetchBody(id: String): String {
        bodyCache[id]?.let { return it }

        val summaryJson = fetchSummary(id)
        val extractHtml = WikipediaResponseParser.parseSummaryExtractHtml(summaryJson)
        val cleaned = extractHtml.replace(Regex("<a[^>]*>"), "").replace("</a>", "")

        bodyCache[id] = cleaned
        return cleaned
    }

    override fun searchPages(query: String): List<Page> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = get(SEARCH_BASE + encoded)
        val titles = WikipediaResponseParser.parseOpenSearchTitles(json)
        return titles.map { Page(it, it) }
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
            return httpGet(url)
        } catch (e: PageNotFoundException) {
            throw e
        } catch (e: IOException) {
            throw PageNotFoundException("Network error fetching $url: ${e.message}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    private fun encode(id: String): String = id.replace(' ', '_')

    private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
        return object : LinkedHashMap<K, V>(maxSize * 4 / 3 + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }
    }
}
