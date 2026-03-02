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
}
