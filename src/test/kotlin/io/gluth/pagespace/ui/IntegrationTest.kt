package io.gluth.pagespace.ui

import io.gluth.pagespace.backend.ContentBackend
import io.gluth.pagespace.backend.MockContentBackend
import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntegrationTest {

    private lateinit var backend: ContentBackend
    private lateinit var graph:   PageGraph
    private lateinit var layout:  ForceDirectedLayout

    @BeforeEach
    fun setUp() {
        backend = MockContentBackend()
        graph   = PageGraph()
        layout  = ForceDirectedLayout(graph, 800.0, 600.0, 42L)
    }

    @Test
    fun graphGrowsAfterNavigatingFromDefaultPage() {
        val start = backend.defaultPage()
        val links = backend.fetchLinks(start.id)

        for (linked in links) {
            graph.addLink(Link(start, linked))
        }
        layout.syncWithGraph()

        assertTrue(graph.pageCount() > 1, "Graph should have more than 1 page after navigation")
        assertEquals(links.size, graph.linksFrom(start).size)
    }

    @Test
    fun layoutPositionsMatchPageCountAfterNavigation() {
        val start = backend.defaultPage()
        val links = backend.fetchLinks(start.id)

        for (linked in links) {
            graph.addLink(Link(start, linked))
        }
        layout.syncWithGraph()

        assertEquals(graph.pageCount(), layout.positions.size,
            "Layout should have a position for every page in the graph")
    }

    @Test
    fun navigatingMultiplePagesBuildsLargerGraph() {
        val physics = backend.defaultPage()
        for (p in backend.fetchLinks(physics.id)) {
            graph.addLink(Link(physics, p))
        }
        layout.syncWithGraph()
        val afterFirst = graph.pageCount()

        val math = Page("mock:math", "Mathematics")
        for (p in backend.fetchLinks(math.id)) {
            graph.addLink(Link(math, p))
        }
        layout.syncWithGraph()
        val afterSecond = graph.pageCount()

        assertTrue(afterSecond >= afterFirst, "Graph should not shrink after further navigation")
        assertEquals(graph.pageCount(), layout.positions.size)
    }

    @Test
    fun bodyFetchedForDefaultPage() {
        val body = backend.fetchBody(backend.defaultPage().id)
        assertNotNull(body)
        assertFalse(body.isBlank())
    }
}
