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

    private val _contentPane = ContentPane()
    private val _spatialPane = SpatialPane(layout)
    private val history: ArrayDeque<Page> = ArrayDeque()
    private val navGeneration = AtomicInteger(0)

    init {
        _contentPane.setNavigationListener(this)
        _spatialPane.setNavigationListener(this)
        _contentPane.setBackAction(::navigateBack)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _contentPane, _spatialPane)
        splitPane.resizeWeight = 0.4

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

                for (linked in result.linkedPages) {
                    graph.addLink(Link(page, linked))
                }
                layout.syncWithGraph()
                layout.setPinnedPage(page)

                history.push(page)
                _contentPane.setContent(page, result.body)
                _spatialPane.setCurrentPage(page)
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
}
