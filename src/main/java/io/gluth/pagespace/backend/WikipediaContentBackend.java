package io.gluth.pagespace.backend;

import io.gluth.pagespace.domain.Page;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ContentBackend that fetches live data from the English Wikipedia REST API
 * and MediaWiki action API.
 *
 * <p>Summary endpoint:
 *   {@code https://en.wikipedia.org/api/rest_v1/page/summary/{title}}
 *
 * <p>Links endpoint:
 *   {@code https://en.wikipedia.org/w/api.php?action=query&prop=links&pllimit=20
 *          &plnamespace=0&format=json&titles={title}}
 */
public class WikipediaContentBackend implements ContentBackend {

    private static final String SUMMARY_BASE =
            "https://en.wikipedia.org/api/rest_v1/page/summary/";
    private static final String LINKS_BASE =
            "https://en.wikipedia.org/w/api.php"
            + "?action=query&prop=links&pllimit=20&plnamespace=0&format=json&titles=";
    private static final String USER_AGENT =
            "page-space/0.1 (https://github.com/CodeFarmer/page-space; educational)";

    private static final Page DEFAULT_PAGE = new Page("Physics", "Physics");

    private final HttpClient http;
    private final Map<String, String> bodyCache  = new HashMap<>();
    private final Map<String, List<Page>> linkCache = new HashMap<>();

    public WikipediaContentBackend() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Page defaultPage() {
        return DEFAULT_PAGE;
    }

    @Override
    public String fetchBody(String id) throws PageNotFoundException {
        if (bodyCache.containsKey(id)) return bodyCache.get(id);

        String summaryJson = get(SUMMARY_BASE + encode(id));
        String extractHtml = WikipediaResponseParser.parseSummaryExtractHtml(summaryJson);
        String title       = WikipediaResponseParser.parseSummaryTitle(summaryJson);

        // Strip anchor tags but keep their text
        String cleaned = extractHtml.replaceAll("<a[^>]*>", "").replace("</a>", "");

        // Append a "See also" section with clickable links
        List<Page> linked = fetchLinks(id);
        StringBuilder sb = new StringBuilder(cleaned);
        if (!linked.isEmpty()) {
            sb.append("<hr><p><b>See also:</b> ");
            for (int i = 0; i < linked.size(); i++) {
                Page p = linked.get(i);
                sb.append("<a href=\"").append(p.id()).append("\">").append(p.title()).append("</a>");
                if (i < linked.size() - 1) sb.append(", ");
            }
            sb.append("</p>");
        }

        String body = sb.toString();
        bodyCache.put(id, body);
        return body;
    }

    @Override
    public List<Page> fetchLinks(String id) throws PageNotFoundException {
        if (linkCache.containsKey(id)) return linkCache.get(id);

        String linksJson = get(LINKS_BASE + encode(id));
        List<String> titles = WikipediaResponseParser.parseLinks(linksJson);

        List<Page> pages = new ArrayList<>();
        for (String title : titles) {
            pages.add(new Page(title, title));
        }
        linkCache.put(id, pages);
        return pages;
    }

    private String get(String url) throws PageNotFoundException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) throw new PageNotFoundException(url);
            if (resp.statusCode() != 200)
                throw new PageNotFoundException("HTTP " + resp.statusCode() + " for " + url);
            return resp.body();
        } catch (PageNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new PageNotFoundException("Network error fetching " + url + ": " + e.getMessage());
        }
    }

    private static String encode(String id) {
        // Replace spaces with underscores (Wikipedia convention)
        return id.replace(' ', '_');
    }
}
