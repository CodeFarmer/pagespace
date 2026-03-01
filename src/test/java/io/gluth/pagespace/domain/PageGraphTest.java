package io.gluth.pagespace.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

class PageGraphTest {

    private PageGraph graph;
    private Page physics, math, quantum, logic;

    @BeforeEach
    void setUp() {
        graph   = new PageGraph();
        physics = new Page("physics", "Physics");
        math    = new Page("math", "Mathematics");
        quantum = new Page("quantum", "Quantum");
        logic   = new Page("logic", "Logic");
    }

    @Test
    void emptyGraphHasNoPagesOrLinks() {
        assertEquals(0, graph.pageCount());
        assertEquals(0, graph.linkCount());
    }

    @Test
    void addPageIncreasesCount() {
        graph.addPage(physics);
        assertEquals(1, graph.pageCount());
    }

    @Test
    void addPageIdempotent() {
        graph.addPage(physics);
        graph.addPage(new Page("physics", "Physics (dup)"));
        assertEquals(1, graph.pageCount());
    }

    @Test
    void addLinkImplicitlyAddsBothPages() {
        graph.addLink(new Link(physics, math));
        assertEquals(2, graph.pageCount());
        assertEquals(1, graph.linkCount());
    }

    @Test
    void linksFromReturnsOutboundTargets() {
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(physics, quantum));
        Set<Page> targets = graph.linksFrom(physics);
        assertTrue(targets.contains(math));
        assertTrue(targets.contains(quantum));
        assertEquals(2, targets.size());
    }

    @Test
    void linksToReturnsInboundSources() {
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(quantum, math));
        Set<Page> sources = graph.linksTo(math);
        assertTrue(sources.contains(physics));
        assertTrue(sources.contains(quantum));
        assertEquals(2, sources.size());
    }

    @Test
    void sharedLinkCountZeroForUnrelated() {
        graph.addPage(physics);
        graph.addPage(math);
        assertEquals(0, graph.sharedLinkCount(physics, math));
    }

    @Test
    void sharedLinkCountCountsCommonNeighbours() {
        // physics -> math, physics -> quantum
        // logic   -> math, logic   -> quantum
        // shared neighbours of physics & logic: math, quantum => 2
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(physics, quantum));
        graph.addLink(new Link(logic, math));
        graph.addLink(new Link(logic, quantum));
        assertEquals(2, graph.sharedLinkCount(physics, logic));
    }

    @Test
    void removePageCascadesToIncidentLinks() {
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(math, quantum));
        graph.removePage(math);
        assertEquals(2, graph.pageCount()); // physics and quantum remain
        assertEquals(0, graph.linkCount());
    }

    @Test
    void pagesReturnsAllPages() {
        graph.addPage(physics);
        graph.addPage(math);
        Set<Page> pages = graph.pages();
        assertTrue(pages.contains(physics));
        assertTrue(pages.contains(math));
        assertEquals(2, pages.size());
    }
}
