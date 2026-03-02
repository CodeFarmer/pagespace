package io.gluth.pagespace.ui

import io.gluth.pagespace.backend.ContentBackend
import io.gluth.pagespace.backend.PageNotFoundException
import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import java.awt.Dimension
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
    }

    private val _contentPane = ContentPane()
    private val _spatialPane = SpatialPane(layout)
    private val history: ArrayDeque<Page> = ArrayDeque()
    private val navGeneration = AtomicInteger(0)

    init {
        _contentPane.setNavigationListener(this)
        _spatialPane.setNavigationListener(this)
        _contentPane.setBackAction(::navigateBack)

        // TODO: add a search/jump bar above the spatial pane — a JTextField that filters
        //       visible node labels as you type and navigates to the selected page on Enter
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _contentPane, _spatialPane)
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
                for (linked in result.linkedPages) {
                    graph.addLink(Link(page, linked))
                }
                layout.syncWithGraph()
                layout.setPinnedPage(page)
                layout.computeEquilibrium()

                history.push(page)
                _contentPane.setContent(page, result.body)
                _spatialPane.setCurrentPage(page)

                launchSecondOrderEnrichment(page, result.linkedPages, myGen)
            }
        }

        worker.execute()
    }

    private fun launchSecondOrderEnrichment(
        centerPage: Page,
        firstOrderNeighbors: List<Page>,
        generation: Int
    ) {
        val worker = object : SwingWorker<SecondOrderResult, Void>() {
            override fun doInBackground(): SecondOrderResult {
                val neighborLinks = mutableMapOf<Page, List<Page>>()
                for (neighbor in firstOrderNeighbors) {
                    if (navGeneration.get() != generation) return SecondOrderResult(emptyMap())
                    try {
                        neighborLinks[neighbor] = backend.fetchLinks(neighbor.id)
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

                val bridgeNodes = candidateCounts.entries
                    .filter { it.value >= 2 }
                    .sortedByDescending { it.value }
                    .take(MAX_SECOND_ORDER_NODES)
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
