package io.gluth.pagespace.ui

import io.gluth.pagespace.backend.ContentBackend
import io.gluth.pagespace.backend.PageNotFoundException
import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

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
    private val history: MutableList<Page> = mutableListOf()
    private var historyIndex = -1
    private var navigatingBack = false
    private val navGeneration = AtomicInteger(0)
    private var linkDensity = 20
    private val densityLabel = JLabel("20")
    private var currentPage: Page? = null
    private var currentFullLinks: List<Page> = emptyList()
    private var currentBody: String = ""
    private val _searchField = JTextField()
    private val searchPopup = JPopupMenu().apply { isFocusable = false }
    private val searchGeneration = AtomicInteger(0)
    private var lastBackendResults: List<Page> = emptyList()
    private var lastBackendGeneration = 0

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

        setupSearchField()

        val spatialWrapper = JPanel(BorderLayout()).apply {
            add(_searchField, BorderLayout.NORTH)
            add(_spatialPane, BorderLayout.CENTER)
            add(densityPanel, BorderLayout.SOUTH)
        }

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

                currentPage = page
                currentFullLinks = result.linkedPages
                currentBody = result.body

                val truncated = result.linkedPages.take(linkDensity)
                for (linked in truncated) {
                    graph.addLink(Link(page, linked))
                }
                graph.pruneDistant(page, 2)
                layout.syncWithGraph()
                layout.setPinnedPage(page)
                layout.computeEquilibrium()

                if (!navigatingBack) {
                    // Truncate forward history and append
                    while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
                    history.add(page)
                    historyIndex = history.size - 1
                }
                navigatingBack = false
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
        if (historyIndex > 0) {
            historyIndex--
            navigatingBack = true
            navigateTo(history[historyIndex])
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

    private fun setupSearchField() {
        val placeholder = "Search..."
        val defaultFg = _searchField.foreground

        _searchField.foreground = Color.GRAY
        _searchField.text = placeholder

        val debounceTimer = Timer(300) { /* replaced per-restart */ }
        debounceTimer.isRepeats = false

        _searchField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                if (_searchField.text == placeholder && _searchField.foreground == Color.GRAY) {
                    _searchField.text = ""
                    _searchField.foreground = defaultFg
                }
            }
            override fun focusLost(e: FocusEvent) {
                if (_searchField.text.isEmpty()) {
                    _searchField.foreground = Color.GRAY
                    _searchField.text = placeholder
                }
            }
        })

        _searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged()
            override fun removeUpdate(e: DocumentEvent) = onTextChanged()
            override fun changedUpdate(e: DocumentEvent) = onTextChanged()

            private fun onTextChanged() {
                val query = _searchField.text
                if (query.isEmpty() || query == placeholder) {
                    searchPopup.isVisible = false
                    debounceTimer.stop()
                    return
                }

                // Instant local matches
                val localMatches = graph.pages()
                    .filter { it.title.contains(query, ignoreCase = true) }
                    .take(10)
                rebuildPopup(localMatches, emptyList(), placeholder)

                // Debounced backend search
                val gen = searchGeneration.incrementAndGet()
                debounceTimer.stop()
                for (al in debounceTimer.actionListeners) debounceTimer.removeActionListener(al)
                debounceTimer.addActionListener {
                    launchBackendSearch(query, gen, placeholder)
                }
                debounceTimer.restart()
            }
        })

        _searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val query = _searchField.text
                        if (query.isNotEmpty() && query != placeholder) {
                            // Try local match first
                            val localMatch = graph.pages()
                                .firstOrNull { it.title.contains(query, ignoreCase = true) }
                            if (localMatch != null) {
                                clearSearch(placeholder)
                                navigateTo(localMatch)
                            } else if (lastBackendGeneration == searchGeneration.get() && lastBackendResults.isNotEmpty()) {
                                // Use already-returned backend results
                                val page = lastBackendResults.first()
                                clearSearch(placeholder)
                                navigateTo(page)
                            } else {
                                // Backend hasn't returned yet — synchronous fetch via SwingWorker
                                val searchQuery = query
                                clearSearch(placeholder)
                                val worker = object : SwingWorker<List<Page>, Void>() {
                                    override fun doInBackground(): List<Page> = backend.searchPages(searchQuery)
                                    override fun done() {
                                        val results = try { get() } catch (_: Exception) { emptyList() }
                                        if (results.isNotEmpty()) navigateTo(results.first())
                                    }
                                }
                                worker.execute()
                            }
                        }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        clearSearch(placeholder)
                        e.consume()
                    }
                }
            }
        })
    }

    private fun launchBackendSearch(query: String, generation: Int, placeholder: String) {
        val worker = object : SwingWorker<List<Page>, Void>() {
            override fun doInBackground(): List<Page> = backend.searchPages(query)
            override fun done() {
                if (searchGeneration.get() != generation) return
                val results = try { get() } catch (_: Exception) { emptyList() }
                lastBackendResults = results
                lastBackendGeneration = generation

                val currentQuery = _searchField.text
                if (currentQuery.isEmpty() || currentQuery == placeholder) return

                val localMatches = graph.pages()
                    .filter { it.title.contains(currentQuery, ignoreCase = true) }
                    .take(10)
                rebuildPopup(localMatches, results, placeholder)
            }
        }
        worker.execute()
    }

    private fun rebuildPopup(localMatches: List<Page>, backendResults: List<Page>, placeholder: String) {
        searchPopup.removeAll()
        val localIds = localMatches.map { it.id }.toSet()

        for (page in localMatches) {
            val item = JMenuItem(page.title)
            item.addActionListener {
                clearSearch(placeholder)
                navigateTo(page)
            }
            searchPopup.add(item)
        }

        val remoteOnly = backendResults.filter { it.id !in localIds }
        if (remoteOnly.isNotEmpty()) {
            val remaining = 10 - localMatches.size
            if (remaining > 0) {
                val separator = JMenuItem("--- Wikipedia ---")
                separator.isEnabled = false
                searchPopup.add(separator)
                for (page in remoteOnly.take(remaining)) {
                    val item = JMenuItem(page.title)
                    item.addActionListener {
                        clearSearch(placeholder)
                        navigateTo(page)
                    }
                    searchPopup.add(item)
                }
            }
        }

        if (searchPopup.componentCount == 0) {
            searchPopup.isVisible = false
            return
        }
        searchPopup.show(_searchField, 0, _searchField.height)
    }

    private fun clearSearch(placeholder: String) {
        searchPopup.isVisible = false
        _searchField.foreground = Color.GRAY
        _searchField.text = placeholder
        _spatialPane.requestFocusInWindow()
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

    fun contentPane(): ContentPane  = _contentPane
    fun spatialPane(): SpatialPane  = _spatialPane
    fun searchField(): JTextField   = _searchField
    fun splitPane():   JSplitPane   = getContentPane() as JSplitPane
    internal fun historyPages(): List<Page> = history.toList()
    internal fun historyIndex(): Int = historyIndex
    internal fun graph(): PageGraph = graph

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
