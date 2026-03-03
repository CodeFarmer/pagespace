package io.gluth.pagespace.backend

import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

internal object WikipediaResponseParser {

    fun parseSummaryTitle(json: String): String = JSONObject(json).getString("title")

    fun parseSummaryExtractHtml(json: String): String = JSONObject(json).optString("extract_html", "")

    private val PARAGRAPH_PATTERN = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val WIKI_LINK_PATTERN = Regex("""href="/wiki/([^"#:]+)"""")

    fun parseLeadSectionLinks(json: String): List<String> {
        val textObj = JSONObject(json).optJSONObject("parse")
            ?.optJSONObject("text") ?: return emptyList()
        val html = textObj.optString("*", "")
        if (html.isEmpty()) return emptyList()

        val seen = LinkedHashSet<String>()
        for (pMatch in PARAGRAPH_PATTERN.findAll(html)) {
            for (linkMatch in WIKI_LINK_PATTERN.findAll(pMatch.groupValues[1])) {
                val raw = linkMatch.groupValues[1]
                val title = URLDecoder.decode(raw, "UTF-8").replace('_', ' ')
                seen.add(title)
            }
        }
        return seen.toList()
    }

    fun parseOpenSearchTitles(json: String): List<String> {
        val arr = JSONArray(json)
        if (arr.length() < 2) return emptyList()
        val titles = arr.getJSONArray(1)
        return (0 until titles.length()).map { titles.getString(it) }
    }

    fun parseLinks(json: String): List<String> {
        val titles = mutableListOf<String>()
        val pages = JSONObject(json).getJSONObject("query").getJSONObject("pages")
        for (key in pages.keySet()) {
            val page = pages.getJSONObject(key)
            val links: JSONArray? = page.optJSONArray("links")
            if (links == null) continue
            for (i in 0 until links.length()) {
                titles.add(links.getJSONObject(i).getString("title"))
            }
        }
        return titles
    }
}
