package io.gluth.pagespace.layout;

import io.gluth.pagespace.domain.Link;
import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class ForceDirectedLayoutTest {

    private static final int W = 800;
    private static final int H = 600;
    private static final long SEED = 42L;

    private Page physics, math, quantum, logic;

    @BeforeEach
    void setUp() {
        physics = new Page("physics", "Physics");
        math    = new Page("math", "Mathematics");
        quantum = new Page("quantum", "Quantum");
        logic   = new Page("logic", "Logic");
    }

    @Test
    void emptyGraphProducesEmptyPositionMap() {
        PageGraph graph = new PageGraph();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        assertTrue(layout.positions().isEmpty());
    }

    @Test
    void singleNodeProducesOnePosition() {
        PageGraph graph = new PageGraph();
        graph.addPage(physics);
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        assertEquals(1, layout.positions().size());
    }

    @Test
    void positionKeysMatchGraphPagesAfterSteps() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(math, quantum));
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        for (int i = 0; i < 50; i++) layout.step();
        assertEquals(graph.pages(), layout.positions().keySet());
    }

    @Test
    void connectedPairCloserThanDisconnectedPairAfterConvergence() {
        // physics <-> math (connected); quantum and logic are isolated
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(math, physics));
        graph.addPage(quantum);
        graph.addPage(logic);

        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        converge(layout, 500);

        Map<Page, NodePosition> pos = layout.positions();
        double connectedDist    = pos.get(physics).distanceTo(pos.get(math));
        double disconnectedDist = pos.get(quantum).distanceTo(pos.get(logic));
        assertTrue(connectedDist < disconnectedDist,
            "Connected pair (" + connectedDist + ") should be closer than disconnected (" + disconnectedDist + ")");
    }

    @Test
    void highSharedLinkPairCloserThanZeroSharedPairAfterConvergence() {
        // physics and math share quantum + relativity as common neighbours
        Page relativity = new Page("relativity", "Relativity");
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, quantum));
        graph.addLink(new Link(physics, relativity));
        graph.addLink(new Link(math, quantum));
        graph.addLink(new Link(math, relativity));
        // logic has no shared neighbours with philosophy
        Page philosophy = new Page("philosophy", "Philosophy");
        graph.addPage(logic);
        graph.addPage(philosophy);

        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        converge(layout, 500);

        Map<Page, NodePosition> pos = layout.positions();
        double highShared = pos.get(physics).distanceTo(pos.get(math));
        double zeroShared = pos.get(logic).distanceTo(pos.get(philosophy));
        assertTrue(highShared < zeroShared,
            "High-shared pair (" + highShared + ") should be closer than zero-shared (" + zeroShared + ")");
    }

    @Test
    void allPositionsWithinCanvasBoundsAfterConvergence() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(math, quantum));
        graph.addLink(new Link(quantum, logic));
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        converge(layout, 300);

        for (NodePosition pos : layout.positions().values()) {
            assertTrue(pos.x() >= 0 && pos.x() <= W, "x=" + pos.x() + " out of [0," + W + "]");
            assertTrue(pos.y() >= 0 && pos.y() <= H, "y=" + pos.y() + " out of [0," + H + "]");
            assertTrue(pos.z() >= 0 && pos.z() <= layout.depth(), "z=" + pos.z() + " out of [0," + layout.depth() + "]");
        }
    }

    @Test
    void syncWithGraphAddsNewPagesWithoutLosingExisting() {
        PageGraph graph = new PageGraph();
        graph.addPage(physics);
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        NodePosition originalPos = layout.positions().get(physics);

        graph.addPage(math);
        layout.syncWithGraph();

        assertEquals(2, layout.positions().size());
        // original position should still be present (same object or same coords)
        assertNotNull(layout.positions().get(physics));
        assertNotNull(layout.positions().get(math));
        // original position object preserved
        assertSame(originalPos, layout.positions().get(physics));
    }

    @Test
    void nodesDoNotPinToCanvasEdgesAfterConvergence() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addPage(quantum);   // isolated — only repulsion forces act on it
        graph.addPage(logic);
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        converge(layout, 400);

        double xMargin = W * 0.04;
        double yMargin = H * 0.04;
        for (NodePosition pos : layout.positions().values()) {
            assertTrue(pos.x() > xMargin && pos.x() < W - xMargin,
                "x=" + pos.x() + " pinned to x-edge");
            assertTrue(pos.y() > yMargin && pos.y() < H - yMargin,
                "y=" + pos.y() + " pinned to y-edge");
        }
    }

    @Test
    void settledNodesDoNotMoveAfterExtendedSteps() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addPage(quantum);  // isolated — only repulsion, will oscillate then settle
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);

        converge(layout, 300);  // enough for adaptive damping to fully engage

        double qx = layout.positions().get(quantum).x();
        double qy = layout.positions().get(quantum).y();

        converge(layout, 100);  // settled nodes must not move

        assertEquals(qx, layout.positions().get(quantum).x(), 0.5,
            "quantum x should not drift after settling");
        assertEquals(qy, layout.positions().get(quantum).y(), 0.5,
            "quantum y should not drift after settling");
    }

    @Test
    void pinnedPageTransitionsSmoothlyCenterward() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);

        // Let nodes settle somewhere away from centre
        converge(layout, 30);

        NodePosition physPos = layout.positions().get(physics);
        double startX = physPos.x(), startY = physPos.y();

        layout.setPinnedPage(physics);

        // One step in: must have moved toward centre but not yet arrived
        layout.step();
        double dx = Math.abs(physPos.x() - W / 2.0);
        double dyAmt = Math.abs(physPos.y() - H / 2.0);
        assertTrue(dx > 0.5 || dyAmt > 0.5,
            "Should not snap to centre instantly — still transitioning");

        // After full transition: must be at centre
        converge(layout, layout.transitionSteps() + 5);
        assertEquals(W / 2.0, physPos.x(), 1.0, "x should reach centre after transition");
        assertEquals(H / 2.0, physPos.y(), 1.0, "y should reach centre after transition");
    }

    @Test
    void pinnedPageRemainsAtCenterAfterSteps() {
        PageGraph graph = new PageGraph();
        graph.addLink(new Link(physics, math));
        graph.addLink(new Link(physics, quantum));
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, W, H, SEED);
        layout.setPinnedPage(physics);
        converge(layout, 100);

        NodePosition pos = layout.positions().get(physics);
        assertEquals(W / 2.0, pos.x(), 1.0);
        assertEquals(H / 2.0, pos.y(), 1.0);
        assertEquals(0.0,     pos.z(), 1.0);
    }

    // --- helpers ---

    private void converge(ForceDirectedLayout layout, int steps) {
        for (int i = 0; i < steps; i++) layout.step();
    }
}
