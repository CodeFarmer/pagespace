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
        repeat(50) { layout.step() }
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
        converge(layout, 500)

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
        converge(layout, 500)

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
        converge(layout, 300)

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
        converge(layout, 400)

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

        converge(layout, 300)

        val qx = layout.positions[quantum]!!.x
        val qy = layout.positions[quantum]!!.y

        converge(layout, 100)

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

        converge(layout, 30)

        layout.setPinnedPage(physics)

        layout.step()
        val physPos = layout.positions[physics]!!
        val dx   = Math.abs(physPos.x - W / 2.0)
        val dyAmt = Math.abs(physPos.y - H / 2.0)
        assertTrue(dx > 0.5 || dyAmt > 0.5,
            "Should not snap to centre instantly — still transitioning")

        converge(layout, layout.transitionSteps + 5)
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
        converge(layout, 100)

        val pos = layout.positions[physics]!!
        assertEquals(W / 2.0, pos.x, 1.0)
        assertEquals(H / 2.0, pos.y, 1.0)
        assertEquals(0.0,     pos.z, 1.0)
    }

    // --- helpers ---

    private fun converge(layout: ForceDirectedLayout, steps: Int) {
        repeat(steps) { layout.step() }
    }
}
