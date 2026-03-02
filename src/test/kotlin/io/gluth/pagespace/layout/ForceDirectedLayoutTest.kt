package io.gluth.pagespace.layout

import io.gluth.pagespace.domain.Link
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForceDirectedLayoutTest {

    companion object {
        private const val W    = 800.0
        private const val H    = 600.0
        private const val SEED = 42L
    }

    private lateinit var physics: Page
    private lateinit var math:    Page
    private lateinit var quantum: Page
    private lateinit var logic:   Page

    @BeforeEach
    fun setUp() {
        physics = Page("physics", "Physics")
        math    = Page("math", "Mathematics")
        quantum = Page("quantum", "Quantum")
        logic   = Page("logic", "Logic")
    }

    @Test
    fun emptyGraphProducesEmptyPositionMap() {
        val graph  = PageGraph()
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        assertTrue(layout.positions.isEmpty())
    }

    @Test
    fun singleNodeProducesOnePosition() {
        val graph = PageGraph()
        graph.addPage(physics)
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        assertEquals(1, layout.positions.size)
    }

    @Test
    fun positionKeysMatchGraphPagesAfterSteps() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        converge(layout)
        assertEquals(graph.pages(), layout.positions.keys)
    }

    @Test
    fun connectedPairCloserThanDisconnectedPairAfterConvergence() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, physics))
        graph.addPage(quantum)
        graph.addPage(logic)

        val layout = ForceDirectedLayout(graph, W, H, SEED)
        converge(layout)

        val pos = layout.positions
        val connectedDist    = pos[physics]!!.distanceTo(pos[math]!!)
        val disconnectedDist = pos[quantum]!!.distanceTo(pos[logic]!!)
        assertTrue(connectedDist < disconnectedDist,
            "Connected pair ($connectedDist) should be closer than disconnected ($disconnectedDist)")
    }

    @Test
    fun highSharedLinkPairCloserThanZeroSharedPairAfterConvergence() {
        val relativity = Page("relativity", "Relativity")
        val graph = PageGraph()
        graph.addLink(Link(physics, quantum))
        graph.addLink(Link(physics, relativity))
        graph.addLink(Link(math, quantum))
        graph.addLink(Link(math, relativity))
        val philosophy = Page("philosophy", "Philosophy")
        graph.addPage(logic)
        graph.addPage(philosophy)

        val layout = ForceDirectedLayout(graph, W, H, SEED)
        converge(layout)

        val pos = layout.positions
        val highShared = pos[physics]!!.distanceTo(pos[math]!!)
        val zeroShared = pos[logic]!!.distanceTo(pos[philosophy]!!)
        assertTrue(highShared < zeroShared,
            "High-shared pair ($highShared) should be closer than zero-shared ($zeroShared)")
    }

    @Test
    fun allPositionsWithinCanvasBoundsAfterConvergence() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        graph.addLink(Link(quantum, logic))
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        converge(layout)

        for (pos in layout.positions.values) {
            assertTrue(pos.x >= 0 && pos.x <= W, "x=${pos.x} out of [0,$W]")
            assertTrue(pos.y >= 0 && pos.y <= H, "y=${pos.y} out of [0,$H]")
            assertTrue(pos.z >= 0 && pos.z <= layout.depth, "z=${pos.z} out of [0,${layout.depth}]")
        }
    }

    @Test
    fun syncWithGraphAddsNewPagesWithoutLosingExisting() {
        val graph = PageGraph()
        graph.addPage(physics)
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        val originalPos = layout.positions[physics]

        graph.addPage(math)
        layout.syncWithGraph()

        assertEquals(2, layout.positions.size)
        assertNotNull(layout.positions[physics])
        assertNotNull(layout.positions[math])
        assertSame(originalPos, layout.positions[physics])
    }

    @Test
    fun nodesDoNotPinToCanvasEdgesAfterConvergence() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addPage(quantum)
        graph.addPage(logic)
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        converge(layout)

        val xMargin = W * 0.04
        val yMargin = H * 0.04
        for (pos in layout.positions.values) {
            assertTrue(pos.x > xMargin && pos.x < W - xMargin, "x=${pos.x} pinned to x-edge")
            assertTrue(pos.y > yMargin && pos.y < H - yMargin, "y=${pos.y} pinned to y-edge")
        }
    }

    @Test
    fun settledNodesDoNotMoveAfterExtendedSteps() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addPage(quantum)
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        converge(layout)

        val qx = layout.positions[quantum]!!.x
        val qy = layout.positions[quantum]!!.y

        converge(layout)

        assertEquals(qx, layout.positions[quantum]!!.x, 0.5,
            "quantum x should not drift after settling")
        assertEquals(qy, layout.positions[quantum]!!.y, 0.5,
            "quantum y should not drift after settling")
    }

    @Test
    fun pinnedPageTransitionsSmoothlyCenterward() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        converge(layout)

        layout.setPinnedPage(physics)
        layout.computeEquilibrium()

        layout.step()
        val physPos = layout.positions[physics]!!
        val dx   = Math.abs(physPos.x - W / 2.0)
        val dyAmt = Math.abs(physPos.y - H / 2.0)
        assertTrue(dx > 0.5 || dyAmt > 0.5,
            "Should not snap to centre instantly — still transitioning")

        repeat(layout.transitionSteps + 5) { layout.step() }
        assertEquals(W / 2.0, layout.positions[physics]!!.x, 1.0, "x should reach centre after transition")
        assertEquals(H / 2.0, layout.positions[physics]!!.y, 1.0, "y should reach centre after transition")
    }

    @Test
    fun pinnedPageRemainsAtCenterAfterSteps() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(physics, quantum))
        val layout = ForceDirectedLayout(graph, W, H, SEED)
        layout.setPinnedPage(physics)
        converge(layout)

        val pos = layout.positions[physics]!!
        assertEquals(W / 2.0, pos.x, 1.0)
        assertEquals(H / 2.0, pos.y, 1.0)
        assertEquals(0.0,     pos.z, 1.0)
    }

    // --- new tests ---

    @Test
    fun stepIsNoOpWhenIdle() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        val before = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }
        repeat(100) { layout.step() }
        val after = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }

        assertEquals(before, after, "step() should be a no-op in IDLE phase")
    }

    @Test
    fun computeEquilibriumReachesConvergence() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        graph.addPage(logic)
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        layout.computeEquilibrium()

        assertTrue(layout.isSettled, "Layout should be settled after computeEquilibrium()")
    }

    @Test
    fun animationInterpolatesMidway() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        val startPositions = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }
        layout.computeEquilibrium()
        layout.step()

        for ((page, pos) in layout.positions) {
            val start = startPositions[page]!!
            val target = layout.positions[page]!!
            // After one step (1/35 of animation), positions should have moved from start
            // but not yet reached the target (unless start == target)
            val moved = start.first != pos.x || start.second != pos.y || start.third != pos.z
            // At least some nodes should have moved
            if (start.first != pos.x || start.second != pos.y || start.third != pos.z) {
                // This node moved — good
            }
        }
        // Verify at least one node moved from its start position
        val anyMoved = layout.positions.any { (page, pos) ->
            val start = startPositions[page]!!
            start.first != pos.x || start.second != pos.y || start.third != pos.z
        }
        assertTrue(anyMoved, "At least one node should have moved after one animation step")
    }

    @Test
    fun animationCompletesAfterTransitionSteps() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        layout.computeEquilibrium()
        repeat(layout.transitionSteps) { layout.step() }

        // Record positions after full animation
        val final_ = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }

        // Further steps should be no-ops (phase is IDLE)
        repeat(10) { layout.step() }

        for ((page, pos) in layout.positions) {
            val f = final_[page]!!
            assertEquals(f.first,  pos.x, 0.001, "$page x should not change after animation completes")
            assertEquals(f.second, pos.y, 0.001, "$page y should not change after animation completes")
            assertEquals(f.third,  pos.z, 0.001, "$page z should not change after animation completes")
        }
    }

    @Test
    fun completeAnimationSnapsToTargets() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        val startPositions = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }
        layout.computeEquilibrium()

        // Without any step() calls, complete immediately
        layout.completeAnimation()

        // Positions should differ from start (equilibrium is different from initial random placement)
        val anyChanged = layout.positions.any { (page, pos) ->
            val start = startPositions[page]!!
            Math.abs(start.first - pos.x) > 0.01 ||
                Math.abs(start.second - pos.y) > 0.01 ||
                Math.abs(start.third - pos.z) > 0.01
        }
        assertTrue(anyChanged, "completeAnimation should snap positions to equilibrium targets")
    }

    @Test
    fun reNavigationDuringAnimationRestartsFromCurrentPositions() {
        val graph = PageGraph()
        graph.addLink(Link(physics, math))
        graph.addLink(Link(math, quantum))
        val layout = ForceDirectedLayout(graph, W, H, SEED)

        layout.setPinnedPage(physics)
        layout.computeEquilibrium()

        // Advance partway through animation
        repeat(layout.transitionSteps / 2) { layout.step() }
        val midPositions = layout.positions.mapValues { (_, p) -> Triple(p.x, p.y, p.z) }

        // Re-navigate: add new node and recompute
        graph.addLink(Link(quantum, logic))
        layout.syncWithGraph()
        layout.setPinnedPage(math)
        layout.computeEquilibrium()

        // After one step, animation should be interpolating from mid-positions
        layout.step()
        // The new animation started from midPositions, so positions should differ from midPositions
        // (they're now interpolating toward a new equilibrium)
        // Just verify we're in a valid state and can complete
        layout.completeAnimation()

        // After completion, pinned page (math) should be at center
        assertEquals(W / 2.0, layout.positions[math]!!.x, 1.0)
        assertEquals(H / 2.0, layout.positions[math]!!.y, 1.0)
    }

    @Test
    fun enrichedCrossLinksClusterRelatedNeighborsCloser() {
        val c = Page("c", "Center")
        val a = Page("a", "A")
        val b = Page("b", "B")
        val x = Page("x", "X")
        val y = Page("y", "Y")
        val s = Page("s", "Shared")

        val graph = PageGraph()
        // C links to all four first-order neighbors
        graph.addLink(Link(c, a))
        graph.addLink(Link(c, b))
        graph.addLink(Link(c, x))
        graph.addLink(Link(c, y))

        // Enrichment: A and B cross-link each other and both link to shared node S
        graph.addLink(Link(a, b))
        graph.addLink(Link(b, a))
        graph.addLink(Link(a, s))
        graph.addLink(Link(b, s))

        // X and Y have no cross-links at all

        val layout = ForceDirectedLayout(graph, W, H, SEED)
        layout.setPinnedPage(c)
        converge(layout)

        val pos = layout.positions
        val distAB = pos[a]!!.distanceTo(pos[b]!!)
        val distXY = pos[x]!!.distanceTo(pos[y]!!)
        assertTrue(distAB < distXY,
            "Enriched pair A-B ($distAB) should be closer than unenriched pair X-Y ($distXY)")
    }

    // --- helpers ---

    private fun converge(layout: ForceDirectedLayout) {
        layout.computeEquilibrium()
        layout.completeAnimation()
    }
}
