package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.presenter.NavigationPresenter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MainWindow(
    private val presenter: NavigationPresenter
) : JFrame("page-space"), NavigationListener {

    companion object {
        private const val MIN_LINK_DENSITY = 5
        private const val MAX_LINK_DENSITY = 50
        private const val DENSITY_STEP = 5
    }

    private val _contentPane = ContentPane()
    private val _spatialPane = SpatialPane(presenter.layout)
    private val densityLabel = JLabel(presenter.linkDensity.toString())
    private val _searchField = JTextField()
    private val searchPopup = JPopupMenu().apply { isFocusable = false }

    init {
        _contentPane.setNavigationListener(this)
        _spatialPane.setNavigationListener(this)
        _contentPane.setBackAction(::navigateBack)

        val minusButton = JButton("-").apply {
            addActionListener {
                if (presenter.linkDensity > MIN_LINK_DENSITY) {
                    val newDensity = presenter.linkDensity - DENSITY_STEP
                    densityLabel.text = newDensity.toString()
                    applyDensity(newDensity)
                }
            }
        }
        val plusButton = JButton("+").apply {
            addActionListener {
                if (presenter.linkDensity < MAX_LINK_DENSITY) {
                    val newDensity = presenter.linkDensity + DENSITY_STEP
                    densityLabel.text = newDensity.toString()
                    applyDensity(newDensity)
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
        val myGen = presenter.newNavGeneration()

        _contentPane.setLoading(page.title)

        val worker = object : SwingWorker<NavigationPresenter.NavResult, Void>() {
            override fun doInBackground(): NavigationPresenter.NavResult {
                return presenter.fetchNavData(page)
            }

            override fun done() {
                val result = try {
                    get()
                } catch (e: Exception) {
                    NavigationPresenter.NavResult(page, emptyList(), "", e.message)
                }

                if (result.error != null) {
                    if (presenter.currentNavGeneration() != myGen) return
                    JOptionPane.showMessageDialog(
                        this@MainWindow,
                        "Page not found: ${page.id}",
                        "Navigation Error",
                        JOptionPane.ERROR_MESSAGE)
                    return
                }

                val applied = presenter.applyNavigation(result, myGen) ?: return

                _contentPane.setContent(applied.page, applied.bodyWithSeeAlso)
                _spatialPane.setCurrentPage(applied.page)

                launchSecondOrderEnrichment(applied.page, applied.truncatedLinks, myGen)
            }
        }

        worker.execute()
    }

    private fun launchSecondOrderEnrichment(
        centerPage: Page,
        firstOrderNeighbors: List<Page>,
        generation: Int
    ) {
        val density = presenter.linkDensity
        val worker = object : SwingWorker<NavigationPresenter.SecondOrderResult, Void>() {
            override fun doInBackground(): NavigationPresenter.SecondOrderResult {
                return presenter.fetchSecondOrderData(firstOrderNeighbors, density, generation)
            }

            override fun done() {
                val result = try {
                    get()
                } catch (_: Exception) {
                    return
                }
                presenter.applySecondOrder(result, centerPage, generation)
            }
        }

        worker.execute()
    }

    private fun navigateBack() {
        val page = presenter.navigateBack() ?: return
        navigateTo(page)
    }

    private fun applyDensity(newDensity: Int) {
        val result = presenter.applyDensity(newDensity) ?: return
        _contentPane.setContent(result.page, result.bodyWithSeeAlso)
        _spatialPane.setCurrentPage(result.page)
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
                val localMatches = presenter.localSearch(query)
                rebuildPopup(localMatches, emptyList(), placeholder)

                // Debounced backend search
                val gen = presenter.newSearchGeneration()
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
                            val localMatch = presenter.localSearch(query).firstOrNull()
                            if (localMatch != null) {
                                clearSearch(placeholder)
                                navigateTo(localMatch)
                            } else {
                                val cached = presenter.cachedBackendResults()
                                if (cached != null && cached.isNotEmpty()) {
                                    val page = cached.first()
                                    clearSearch(placeholder)
                                    navigateTo(page)
                                } else {
                                    // Backend hasn't returned yet — fetch via SwingWorker
                                    val searchQuery = query
                                    clearSearch(placeholder)
                                    val searchWorker = object : SwingWorker<List<Page>, Void>() {
                                        override fun doInBackground(): List<Page> =
                                            presenter.backendSearch(searchQuery)
                                        override fun done() {
                                            val results = try { get() } catch (_: Exception) { emptyList() }
                                            if (results.isNotEmpty()) navigateTo(results.first())
                                        }
                                    }
                                    searchWorker.execute()
                                }
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
            override fun doInBackground(): List<Page> = presenter.backendSearch(query)
            override fun done() {
                val results = try { get() } catch (_: Exception) { emptyList() }
                if (!presenter.applyBackendSearchResults(results, generation)) return

                val currentQuery = _searchField.text
                if (currentQuery.isEmpty() || currentQuery == placeholder) return

                val localMatches = presenter.localSearch(currentQuery)
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

    fun contentPane(): ContentPane  = _contentPane
    fun spatialPane(): SpatialPane  = _spatialPane
    fun searchField(): JTextField   = _searchField
    fun splitPane():   JSplitPane   = getContentPane() as JSplitPane
    internal fun historyPages(): List<Page> = presenter.history.pages()
    internal fun historyIndex(): Int = presenter.history.currentIndex()
    internal fun graph(): PageGraph = presenter.graph
}
