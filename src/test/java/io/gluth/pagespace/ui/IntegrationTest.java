package io.gluth.pagespace.ui;

import io.gluth.pagespace.backend.ContentBackend;
import io.gluth.pagespace.backend.MockContentBackend;
import io.gluth.pagespace.backend.PageNotFoundException;
import io.gluth.pagespace.domain.Link;
import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;
import io.gluth.pagespace.layout.ForceDirectedLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Full navigation cycle: backend → graph merge → layout sync.
 * No Swing involved.
 */
class IntegrationTest {

    private ContentBackend backend;
    private PageGraph graph;
    private ForceDirectedLayout layout;

    @BeforeEach
    void setUp() {
        backend = new MockContentBackend();
        graph   = new PageGraph();
        layout  = new ForceDirectedLayout(graph, 800, 600, 42L);
    }

    @Test
    void graphGrowsAfterNavigatingFromDefaultPage() throws PageNotFoundException {
        Page start = backend.defaultPage();
        List<Page> links = backend.fetchLinks(start.id());

        for (Page linked : links) {
            graph.addLink(new Link(start, linked));
        }
        layout.syncWithGraph();

        // graph should have start page + all its links
        assertTrue(graph.pageCount() > 1, "Graph should have more than 1 page after navigation");
        assertEquals(links.size(), graph.linksFrom(start).size());
    }

    @Test
    void layoutPositionsMatchPageCountAfterNavigation() throws PageNotFoundException {
        Page start = backend.defaultPage();
        List<Page> links = backend.fetchLinks(start.id());

        for (Page linked : links) {
            graph.addLink(new Link(start, linked));
        }
        layout.syncWithGraph();

        assertEquals(graph.pageCount(), layout.positions().size(),
            "Layout should have a position for every page in the graph");
    }

    @Test
    void navigatingMultiplePagesBuildsLargerGraph() throws PageNotFoundException {
        // Navigate from physics -> then from math
        Page physics = backend.defaultPage();
        for (Page p : backend.fetchLinks(physics.id())) {
            graph.addLink(new Link(physics, p));
        }
        layout.syncWithGraph();
        int afterFirst = graph.pageCount();

        Page math = new Page("mock:math", "Mathematics");
        for (Page p : backend.fetchLinks(math.id())) {
            graph.addLink(new Link(math, p));
        }
        layout.syncWithGraph();
        int afterSecond = graph.pageCount();

        assertTrue(afterSecond >= afterFirst, "Graph should not shrink after further navigation");
        assertEquals(graph.pageCount(), layout.positions().size());
    }

    @Test
    void bodyFetchedForDefaultPage() throws PageNotFoundException {
        String body = backend.fetchBody(backend.defaultPage().id());
        assertNotNull(body);
        assertFalse(body.isBlank());
    }
}
