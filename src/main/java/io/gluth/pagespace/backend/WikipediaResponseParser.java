package io.gluth.pagespace.backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stateless helpers for parsing Wikipedia REST / MediaWiki API responses. */
class WikipediaResponseParser {

    private WikipediaResponseParser() {}

    static String parseSummaryTitle(String json) {
        return new JSONObject(json).getString("title");
    }

    static String parseSummaryExtractHtml(String json) {
        JSONObject obj = new JSONObject(json);
        return obj.optString("extract_html", "");
    }

    /** Returns the list of linked page titles from a MediaWiki prop=links response. */
    static List<String> parseLinks(String json) {
        List<String> titles = new ArrayList<>();
        JSONObject pages = new JSONObject(json)
                .getJSONObject("query")
                .getJSONObject("pages");
        for (String key : pages.keySet()) {
            JSONObject page = pages.getJSONObject(key);
            JSONArray links = page.optJSONArray("links");
            if (links == null) continue;
            for (int i = 0; i < links.length(); i++) {
                titles.add(links.getJSONObject(i).getString("title"));
            }
        }
        return titles;
    }
}
