package io.gluth.pagespace.domain

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageGraphTest {

    private lateinit var graph:   PageGraph
    private lateinit var physics: Page
    private lateinit var math:    Page
    private lateinit var quantum: Page
    private lateinit var logic:   Page

    @BeforeEach
    fun setUp() {
        graph   = PageGraph()
        physics = Page("physics", "Physics")
        math    = Page("math", "Mathematics")
        quantum = Page("quantum", "Quantum")
        logic   = Page("logic", "Logic")
    }

    @Test
    fun emptyGraphHasNoPagesOrLinks() {
        assertEquals(0, graph.pageCount())
        assertEquals(0, graph.linkCount())
    }

    @Test
    fun addPageIncreasesCount() {
        graph.addPage(physics)
        assertEquals(1, graph.pageCount())
    }

    @Test
    fun addPageIdempotent() {
        graph.addPage(physics)
        graph.addPage(Page("physics", "Physics (dup)"))
        assertEquals(1, graph.pageCount())
    }

    @Test
    fun addLinkImplicitlyAddsBothPages() {
        graph.addLink(Link(physics, math))
        assertEquals(2, graph.pageCount())
        assertEquals(1, graph.linkCount())
    }

    @Test
    fun linksFromReturnsOutboundTargets() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(physics, quantum))
        val targets = graph.linksFrom(physics)
        assertTrue(targets.contains(math))
        assertTrue(targets.contains(quantum))
        assertEquals(2, targets.size)
    }

    @Test
    fun linksToReturnsInboundSources() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(quantum, math))
        val sources = graph.linksTo(math)
        assertTrue(sources.contains(physics))
        assertTrue(sources.contains(quantum))
        assertEquals(2, sources.size)
    }

    @Test
    fun sharedLinkCountZeroForUnrelated() {
        graph.addPage(physics)
        graph.addPage(math)
        assertEquals(0, graph.sharedLinkCount(physics, math))
    }

    @Test
    fun sharedLinkCountCountsCommonNeighbours() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(physics, quantum))
        graph.addLink(Link(logic, math))
        graph.addLink(Link(logic, quantum))
        assertEquals(2, graph.sharedLinkCount(physics, logic))
    }

    @Test
    fun removePageCascadesToIncidentLinks() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        graph.removePage(math)
        assertEquals(2, graph.pageCount())
        assertEquals(0, graph.linkCount())
    }

    @Test
    fun pagesReturnsAllPages() {
        graph.addPage(physics)
        graph.addPage(math)
        val pages = graph.pages()
        assertTrue(pages.contains(physics))
        assertTrue(pages.contains(math))
        assertEquals(2, pages.size)
    }

    @Test
    fun removeLinkRemovesDirectedEdgeOnly() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(physics, quantum))
        graph.removeLink(Link(physics, math))
        assertEquals(1, graph.linkCount())
        assertFalse(graph.linksFrom(physics).contains(math))
        assertTrue(graph.linksFrom(physics).contains(quantum))
        // Both pages still exist
        assertEquals(3, graph.pageCount())
    }

    @Test
    fun removeLinkUpdatesInEdges() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(quantum, math))
        graph.removeLink(Link(physics, math))
        val sources = graph.linksTo(math)
        assertFalse(sources.contains(physics))
        assertTrue(sources.contains(quantum))
    }

    @Test
    fun pagesWithinDistanceReturnsCorrectSet() {
        val a = Page("a", "A")
        val b = Page("b", "B")
        val c = Page("c", "C")
        val d = Page("d", "D")
        val e = Page("e", "E")
        graph.addLink(Link(a, b))
        graph.addLink(Link(b, c))
        graph.addLink(Link(c, d))
        graph.addLink(Link(d, e))

        val reachable = graph.pagesWithinDistance(c, 1)
        assertEquals(setOf(b, c, d), reachable)

        val reachable2 = graph.pagesWithinDistance(c, 2)
        assertEquals(setOf(a, b, c, d, e), reachable2)
    }

    @Test
    fun pagesWithinDistanceTreatsEdgesAsUndirected() {
        val a = Page("a", "A")
        val b = Page("b", "B")
        graph.addLink(Link(a, b))

        val fromA = graph.pagesWithinDistance(a, 1)
        assertTrue(b in fromA)

        val fromB = graph.pagesWithinDistance(b, 1)
        assertTrue(a in fromB)
    }

    @Test
    fun pruneDistantRemovesFarNodes() {
        val a = Page("a", "A")
        val b = Page("b", "B")
        val c = Page("c", "C")
        val d = Page("d", "D")
        val e = Page("e", "E")
        graph.addLink(Link(a, b))
        graph.addLink(Link(b, c))
        graph.addLink(Link(c, d))
        graph.addLink(Link(d, e))

        graph.pruneDistant(c, 1)

        val remaining = graph.pages()
        assertEquals(setOf(b, c, d), remaining)
        assertFalse(a in remaining)
        assertFalse(e in remaining)
    }

    @Test
    fun pruneDistantKeepsAllWhenWithinDistance() {
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        graph.addLink(Link(quantum, physics))

        graph.pruneDistant(physics, 2)

        assertEquals(setOf(physics, math, quantum), graph.pages())
    }
}
