package io.gluth.pagespace.ui

import io.gluth.pagespace.backend.ContentBackend
import io.gluth.pagespace.backend.PageNotFoundException
import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*

class MainWindow(
    private val backend: ContentBackend,
    private val graph:   PageGraph,
    private val layout:  ForceDirectedLayout
) : JFrame("page-space"), NavigationListener {

    companion object {
        private const val MAX_SECOND_ORDER_NODES = 30
        private const val MIN_LINK_DENSITY = 5
        private const val MAX_LINK_DENSITY = 50
        private const val DENSITY_STEP = 5
    }

    private val _contentPane = ContentPane()
    private val _spatialPane = SpatialPane(layout)
    private val history: ArrayDeque<Page> = ArrayDeque()
    private val navGeneration = AtomicInteger(0)
    private var linkDensity = 20
    private val densityLabel = JLabel("20")
    private var currentPage: Page? = null
    private var currentFullLinks: List<Page> = emptyList()
    private var currentBody: String = ""

    init {
        _contentPane.setNavigationListener(this)
        _spatialPane.setNavigationListener(this)
        _contentPane.setBackAction(::navigateBack)

        val minusButton = JButton("-").apply {
            addActionListener {
                if (linkDensity > MIN_LINK_DENSITY) {
                    linkDensity -= DENSITY_STEP
                    densityLabel.text = linkDensity.toString()
                    applyDensity()
                }
            }
        }
        val plusButton = JButton("+").apply {
            addActionListener {
                if (linkDensity < MAX_LINK_DENSITY) {
                    linkDensity += DENSITY_STEP
                    densityLabel.text = linkDensity.toString()
                    applyDensity()
                }
            }
        }
        val densityPanel = JPanel(FlowLayout(FlowLayout.CENTER, 4, 2)).apply {
            add(JLabel("Link density:"))
            add(minusButton)
            add(densityLabel)
            add(plusButton)
        }

        val spatialWrapper = JPanel(BorderLayout()).apply {
            add(_spatialPane, BorderLayout.CENTER)
            add(densityPanel, BorderLayout.SOUTH)
        }

        // TODO: add a search/jump bar above the spatial pane — a JTextField that filters
        //       visible node labels as you type and navigates to the selected page on Enter
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _contentPane, spatialWrapper)
        splitPane.resizeWeight = 1.0 / 3.0
        splitPane.dividerLocation = 400

        contentPane = splitPane
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(1200, 700)
    }

    override fun navigateTo(page: Page) {
        val myGen = navGeneration.incrementAndGet()

        _contentPane.setLoading(page.title)

        val worker = object : SwingWorker<NavResult, Void>() {
            override fun doInBackground(): NavResult {
                return try {
                    val linkedPages = backend.fetchLinks(page.id)
                    val body = backend.fetchBody(page.id)
                    NavResult(page, linkedPages, body, null)
                } catch (e: PageNotFoundException) {
                    NavResult(page, emptyList(), "", e.message)
                }
            }

            override fun done() {
                if (navGeneration.get() != myGen) return

                val result = try {
                    get()
                } catch (e: Exception) {
                    NavResult(page, emptyList(), "", e.message)
                }

                if (result.error != null) {
                    JOptionPane.showMessageDialog(
                        this@MainWindow,
                        "Page not found: ${page.id}",
                        "Navigation Error",
                        JOptionPane.ERROR_MESSAGE)
                    return
                }

                // TODO: prune nodes that are too far from the current page (e.g. no path
                //       of length ≤ N to the current page) so the graph doesn't grow unboundedly
                currentPage = page
                currentFullLinks = result.linkedPages
                currentBody = result.body

                val truncated = result.linkedPages.take(linkDensity)
                for (linked in truncated) {
                    graph.addLink(Link(page, linked))
                }
                layout.syncWithGraph()
                layout.setPinnedPage(page)
                layout.computeEquilibrium()

                history.push(page)
                _contentPane.setContent(page, buildSeeAlso(result.body, truncated))
                _spatialPane.setCurrentPage(page)

                launchSecondOrderEnrichment(page, truncated, myGen, linkDensity)
            }
        }

        worker.execute()
    }

    private fun launchSecondOrderEnrichment(
        centerPage: Page,
        firstOrderNeighbors: List<Page>,
        generation: Int,
        density: Int
    ) {
        val worker = object : SwingWorker<SecondOrderResult, Void>() {
            override fun doInBackground(): SecondOrderResult {
                val neighborLinks = mutableMapOf<Page, List<Page>>()
                for (neighbor in firstOrderNeighbors) {
                    if (navGeneration.get() != generation) return SecondOrderResult(emptyMap())
                    try {
                        neighborLinks[neighbor] = backend.fetchLinks(neighbor.id).take(density)
                    } catch (_: PageNotFoundException) {
                        // skip unreachable neighbors
                    }
                }
                return SecondOrderResult(neighborLinks)
            }

            override fun done() {
                if (navGeneration.get() != generation) return

                val result = try {
                    get()
                } catch (_: Exception) {
                    return
                }

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
        }

        worker.execute()
    }

    private fun navigateBack() {
        if (history.size > 1) {
            history.pop()
            val previous = history.peek()
            if (previous != null) {
                history.pop()
                navigateTo(previous)
            }
        }
    }

    private fun applyDensity() {
        val page = currentPage ?: return
        if (currentFullLinks.isEmpty()) return

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

        _contentPane.setContent(page, buildSeeAlso(currentBody, desired))
        _spatialPane.setCurrentPage(page)
    }

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

    private fun buildSeeAlso(body: String, links: List<Page>): String {
        if (links.isEmpty()) return body
        return buildString {
            append(body)
            append("<hr><p><b>See also:</b> ")
            links.forEachIndexed { i, p ->
                append("<a href=\"${p.id}\">${p.title}</a>")
                if (i < links.size - 1) append(", ")
            }
            append("</p>")
        }
    }

    fun contentPane(): ContentPane  = _contentPane
    fun spatialPane(): SpatialPane  = _spatialPane
    fun splitPane():   JSplitPane   = getContentPane() as JSplitPane

    private data class NavResult(
        val page:        Page,
        val linkedPages: List<Page>,
        val body:        String,
        val error:       String?
    )

    private data class SecondOrderResult(
        val neighborLinks: Map<Page, List<Page>>
    )
}
