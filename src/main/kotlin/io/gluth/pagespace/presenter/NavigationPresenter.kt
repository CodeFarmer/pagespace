package io.gluth.pagespace.presenter

import io.gluth.pagespace.backend.ContentBackend
import io.gluth.pagespace.backend.PageNotFoundException
import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import java.util.concurrent.atomic.AtomicInteger

class NavigationPresenter(
    val graph: PageGraph,
    val layout: ForceDirectedLayout,
    private val backend: ContentBackend,
    var linkDensity: Int = 20
) {
    companion object {
        const val MAX_SECOND_ORDER_NODES = 30
    }

    val history = NavigationHistory()
    private val navGeneration = AtomicInteger(0)
    var currentPage: Page? = null
        private set
    var currentFullLinks: List<Page> = emptyList()
        private set
    var currentBody: String = ""
        private set

    // --- Search state ---
    private val searchGeneration = AtomicInteger(0)
    private var lastBackendResults: List<Page> = emptyList()
    private var lastBackendGeneration = 0

    // === Navigation ===

    fun newNavGeneration(): Int = navGeneration.incrementAndGet()
    fun currentNavGeneration(): Int = navGeneration.get()

    /** Blocking — call from background thread. */
    fun fetchNavData(pageId: String): NavResult {
        return try {
            val linkedPages = backend.fetchLinks(pageId)
            val body = backend.fetchBody(pageId)
            NavResult(Page(pageId, linkedPages.firstOrNull()?.title?.let {
                // We need the page object; reconstruct from the id
                pageId
            } ?: pageId), linkedPages, body, null)
        } catch (e: PageNotFoundException) {
            NavResult(Page(pageId, pageId), emptyList(), "", e.message)
        }
    }

    /** Blocking — call from background thread. Fetches links and body for a known Page. */
    fun fetchNavData(page: Page): NavResult {
        return try {
            val linkedPages = backend.fetchLinks(page.id)
            val body = backend.fetchBody(page.id)
            NavResult(page, linkedPages, body, null)
        } catch (e: PageNotFoundException) {
            NavResult(page, emptyList(), "", e.message)
        }
    }

    /**
     * Apply fetched data to graph/layout. Call from main thread.
     * Returns null if generation is stale.
     */
    fun applyNavigation(result: NavResult, generation: Int): AppliedNav? {
        if (navGeneration.get() != generation) return null
        if (result.error != null) return null

        currentPage = result.page
        currentFullLinks = result.linkedPages
        currentBody = result.body

        val truncated = result.linkedPages.take(linkDensity)
        for (linked in truncated) {
            graph.addLink(Link(result.page, linked))
        }
        graph.pruneDistant(result.page, 2)
        layout.syncWithGraph()
        layout.setPinnedPage(result.page)
        layout.computeEquilibrium()

        history.push(result.page)

        return AppliedNav(result.page, buildSeeAlso(result.body, truncated), truncated)
    }

    // === Second-order enrichment ===

    /** Blocking — call from background thread. */
    fun fetchSecondOrderData(
        neighbors: List<Page>,
        density: Int,
        generation: Int
    ): SecondOrderResult {
        val neighborLinks = mutableMapOf<Page, List<Page>>()
        for (neighbor in neighbors) {
            if (navGeneration.get() != generation) return SecondOrderResult(emptyMap())
            try {
                neighborLinks[neighbor] = backend.fetchLinks(neighbor.id).take(density)
            } catch (_: PageNotFoundException) {
                // skip unreachable neighbors
            }
        }
        return SecondOrderResult(neighborLinks)
    }

    /** Apply enrichment to graph/layout. Call from main thread. */
    fun applySecondOrder(result: SecondOrderResult, centerPage: Page, generation: Int) {
        if (navGeneration.get() != generation) return
        if (result.neighborLinks.isEmpty()) return

        val existingPages = graph.pages()

        // Phase 1: add cross-links between nodes already in the graph
        for ((neighbor, targets) in result.neighborLinks) {
            for (target in targets) {
                if (target in existingPages && target != centerPage) {
                    graph.addLink(Link(neighbor, target))
                }
            }
        }

        // Phase 2: find second-order nodes referenced by 2+ first-order neighbors
        val candidateCounts = mutableMapOf<Page, Int>()
        for ((_, targets) in result.neighborLinks) {
            for (target in targets) {
                if (target !in existingPages) {
                    candidateCounts[target] = (candidateCounts[target] ?: 0) + 1
                }
            }
        }

        val density = linkDensity
        val maxBridge = minOf(density, MAX_SECOND_ORDER_NODES)
        val bridgeNodes = candidateCounts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(maxBridge)
            .map { it.key }
            .toSet()

        // Add links from first-order neighbors to accepted bridge nodes
        for ((neighbor, targets) in result.neighborLinks) {
            for (target in targets) {
                if (target in bridgeNodes) {
                    graph.addLink(Link(neighbor, target))
                }
            }
        }

        layout.syncWithGraph()
        layout.computeEquilibrium()
    }

    // === Density ===

    fun applyDensity(newDensity: Int): DensityResult? {
        linkDensity = newDensity
        val page = currentPage ?: return null
        if (currentFullLinks.isEmpty()) return null

        val desired = currentFullLinks.take(linkDensity)
        val desiredSet = desired.toSet()
        val fullSet = currentFullLinks.toSet()

        // Remove first-order links beyond the new cutoff
        for (target in graph.linksFrom(page).toList()) {
            if (target in fullSet && target !in desiredSet) {
                graph.removeLink(Link(page, target))
            }
        }

        // Add first-order links within the cutoff
        for (target in desired) {
            graph.addLink(Link(page, target))
        }

        // Remove orphaned pages (no in-edges, not the current page)
        removeOrphans(page)

        layout.syncWithGraph()
        layout.setPinnedPage(page)
        layout.computeEquilibrium()

        return DensityResult(page, buildSeeAlso(currentBody, desired), desired)
    }

    // === Search ===

    fun newSearchGeneration(): Int = searchGeneration.incrementAndGet()
    fun currentSearchGeneration(): Int = searchGeneration.get()

    fun localSearch(query: String): List<Page> =
        graph.pages()
            .filter { it.title.contains(query, ignoreCase = true) }
            .take(10)

    /** Blocking — call from background thread. */
    fun backendSearch(query: String): List<Page> = backend.searchPages(query)

    fun applyBackendSearchResults(results: List<Page>, generation: Int): Boolean {
        if (searchGeneration.get() != generation) return false
        lastBackendResults = results
        lastBackendGeneration = generation
        return true
    }

    fun cachedBackendResults(): List<Page>? {
        return if (lastBackendGeneration == searchGeneration.get()) lastBackendResults else null
    }

    // === History ===

    fun navigateBack(): Page? = history.back()

    // === Utility ===

    fun buildSeeAlso(body: String, links: List<Page>): String {
        if (links.isEmpty()) return body
        return buildString {
            append(body)
            append("<hr><p><b>See also:</b> ")
            links.forEachIndexed { i, p ->
                append("<a href=\"${escapeHtml(p.id)}\">${escapeHtml(p.title)}</a>")
                if (i < links.size - 1) append(", ")
            }
            append("</p>")
        }
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun removeOrphans(keep: Page) {
        var changed = true
        while (changed) {
            changed = false
            for (p in graph.pages()) {
                if (p != keep && graph.linksTo(p).isEmpty()) {
                    graph.removePage(p)
                    changed = true
                    break
                }
            }
        }
    }

    data class NavResult(
        val page: Page,
        val linkedPages: List<Page>,
        val body: String,
        val error: String?
    )

    data class AppliedNav(
        val page: Page,
        val bodyWithSeeAlso: String,
        val truncatedLinks: List<Page>
    )

    data class SecondOrderResult(
        val neighborLinks: Map<Page, List<Page>>
    )

    data class DensityResult(
        val page: Page,
        val bodyWithSeeAlso: String,
        val truncatedLinks: List<Page>
    )
}
