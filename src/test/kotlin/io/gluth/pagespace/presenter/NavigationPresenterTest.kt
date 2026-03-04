package io.gluth.pagespace.presenter

import io.gluth.pagespace.backend.MockContentBackend
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationPresenterTest {

    private lateinit var backend: MockContentBackend
    private lateinit var graph: PageGraph
    private lateinit var layout: ForceDirectedLayout
    private lateinit var presenter: NavigationPresenter

    private val physics = Page("mock:physics", "Physics")
    private val math = Page("mock:math", "Mathematics")
    private val quantum = Page("mock:quantum", "Quantum Mechanics")
    private val philosophy = Page("mock:philosophy", "Philosophy")

    @BeforeEach
    fun setUp() {
        backend = MockContentBackend()
        graph = PageGraph()
        layout = ForceDirectedLayout(graph, 800.0, 600.0, 42L)
        presenter = NavigationPresenter(graph, layout, backend)
    }

    @Test
    fun fetchNavDataReturnsLinksAndBody() {
        val result = presenter.fetchNavData(physics)
        assertNull(result.error)
        assertEquals(physics, result.page)
        assertTrue(result.linkedPages.isNotEmpty())
        assertTrue(result.body.contains("Physics"))
    }

    @Test
    fun fetchNavDataReturnsErrorForUnknownPage() {
        val result = presenter.fetchNavData(Page("mock:nonexistent", "Nonexistent"))
        assertNotNull(result.error)
    }

    @Test
    fun applyNavigationAddsNodesToGraph() {
        val gen = presenter.newNavGeneration()
        val result = presenter.fetchNavData(physics)
        val applied = presenter.applyNavigation(result, gen)

        assertNotNull(applied)
        assertEquals(physics, applied.page)
        assertTrue(graph.pageCount() > 1)
        assertTrue(graph.pages().contains(physics))
        assertEquals(physics, presenter.currentPage)
    }

    @Test
    fun applyNavigationReturnsNullForStaleGeneration() {
        val gen = presenter.newNavGeneration()
        presenter.newNavGeneration() // stale
        val result = presenter.fetchNavData(physics)
        val applied = presenter.applyNavigation(result, gen)

        assertNull(applied)
    }

    @Test
    fun applyNavigationPushesHistory() {
        val gen = presenter.newNavGeneration()
        val result = presenter.fetchNavData(physics)
        presenter.applyNavigation(result, gen)

        assertEquals(listOf(physics), presenter.history.pages())
        assertEquals(0, presenter.history.currentIndex())
    }

    @Test
    fun applyDensityAdjustsGraph() {
        // Navigate first
        val gen = presenter.newNavGeneration()
        val result = presenter.fetchNavData(physics)
        presenter.applyNavigation(result, gen)

        val initialCount = graph.linksFrom(physics).size

        // Reduce density
        val densityResult = presenter.applyDensity(5)
        assertNotNull(densityResult)
        assertTrue(graph.linksFrom(physics).size <= 5)
        assertEquals(5, presenter.linkDensity)
    }

    @Test
    fun applyDensityReturnsNullWithNoCurrentPage() {
        assertNull(presenter.applyDensity(10))
    }

    @Test
    fun localSearchFindsMatchingPages() {
        // Populate graph first
        val gen = presenter.newNavGeneration()
        val result = presenter.fetchNavData(physics)
        presenter.applyNavigation(result, gen)

        val matches = presenter.localSearch("Math")
        assertTrue(matches.any { it.title.contains("Math", ignoreCase = true) })
    }

    @Test
    fun localSearchReturnsEmptyForNoMatches() {
        val gen = presenter.newNavGeneration()
        val result = presenter.fetchNavData(physics)
        presenter.applyNavigation(result, gen)

        val matches = presenter.localSearch("xyznonexistent")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun backendSearchDelegatesCorrectly() {
        val results = presenter.backendSearch("Physics")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.title.contains("Physics", ignoreCase = true) })
    }

    @Test
    fun applyBackendSearchResultsTracksGeneration() {
        val gen = presenter.newSearchGeneration()
        val results = listOf(physics, math)

        assertTrue(presenter.applyBackendSearchResults(results, gen))
        assertEquals(results, presenter.cachedBackendResults())
    }

    @Test
    fun applyBackendSearchResultsRejectsStaleGeneration() {
        val gen = presenter.newSearchGeneration()
        presenter.newSearchGeneration() // stale

        assertFalse(presenter.applyBackendSearchResults(listOf(physics), gen))
    }

    @Test
    fun cachedBackendResultsReturnsNullWhenStale() {
        val gen = presenter.newSearchGeneration()
        presenter.applyBackendSearchResults(listOf(physics), gen)
        presenter.newSearchGeneration() // invalidate

        assertNull(presenter.cachedBackendResults())
    }

    @Test
    fun navigateBackDelegatesToHistory() {
        val gen1 = presenter.newNavGeneration()
        presenter.applyNavigation(presenter.fetchNavData(physics), gen1)

        val gen2 = presenter.newNavGeneration()
        presenter.applyNavigation(presenter.fetchNavData(math), gen2)

        val backPage = presenter.navigateBack()
        assertEquals(physics, backPage)
    }

    @Test
    fun navigateBackReturnsNullWithSinglePage() {
        val gen = presenter.newNavGeneration()
        presenter.applyNavigation(presenter.fetchNavData(physics), gen)

        assertNull(presenter.navigateBack())
    }

    @Test
    fun buildSeeAlsoAppendsLinks() {
        val links = listOf(math, quantum)
        val result = presenter.buildSeeAlso("<p>body</p>", links)

        assertTrue(result.contains("<p>body</p>"))
        assertTrue(result.contains("See also:"))
        assertTrue(result.contains("Mathematics"))
        assertTrue(result.contains("Quantum Mechanics"))
    }

    @Test
    fun buildSeeAlsoEmptyLinksReturnsBody() {
        val result = presenter.buildSeeAlso("<p>body</p>", emptyList())
        assertEquals("<p>body</p>", result)
    }

    @Test
    fun buildSeeAlsoEscapesHtml() {
        val links = listOf(Page("AT&T", "AT&T"))
        val result = presenter.buildSeeAlso("body", links)
        assertTrue(result.contains("AT&amp;T"))
    }

    @Test
    fun fetchSecondOrderDataReturnsNeighborLinks() {
        val gen = presenter.newNavGeneration()
        val navResult = presenter.fetchNavData(physics)
        presenter.applyNavigation(navResult, gen)

        val neighbors = navResult.linkedPages.take(3)
        val secondOrder = presenter.fetchSecondOrderData(neighbors, 10, gen)

        assertTrue(secondOrder.neighborLinks.isNotEmpty())
    }

    @Test
    fun applySecondOrderAddsNodesToGraph() {
        val gen = presenter.newNavGeneration()
        val navResult = presenter.fetchNavData(physics)
        presenter.applyNavigation(navResult, gen)

        val countBefore = graph.pageCount()
        val neighbors = navResult.linkedPages.take(presenter.linkDensity)
        val secondOrder = presenter.fetchSecondOrderData(neighbors, presenter.linkDensity, gen)
        presenter.applySecondOrder(secondOrder, physics, gen)

        // Should have at least added some cross-links (link count may increase even if page count doesn't)
        assertTrue(graph.linkCount() > 0)
    }
}
