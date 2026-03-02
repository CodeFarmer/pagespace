package io.gluth.pagespace.backend

import org.json.JSONArray
import org.json.JSONObject

internal object WikipediaResponseParser {

    fun parseSummaryTitle(json: String): String = JSONObject(json).getString("title")

    fun parseSummaryExtractHtml(json: String): String = JSONObject(json).optString("extract_html", "")

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
