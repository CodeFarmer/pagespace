package io.gluth.pagespace.layout

import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import java.util.Collections
import kotlin.math.*

class ForceDirectedLayout(
    val graph: PageGraph,
    val width: Double,
    val height: Double,
    seed: Long = System.nanoTime()
) {

    companion object {
        private const val BASE_DAMPING     = 0.75
        private const val FORCE_SCALE      = 0.08
        private const val MAX_SPEED        = 60.0
        private const val REVERSAL_DAMP    = 0.45
        private const val REVERSAL_CREDIT  = 8
        private const val SETTLE_THRESHOLD = 4.0
        private const val FAST_DECAY       = 2
        private const val SETTLE_STEPS     = 40
        private const val BOUNDARY_MARGIN  = 0.12
        private const val BOUNDARY_STRENGTH = 50.0
        private const val TRANSITION_STEPS = 35
    }

    val depth: Double = min(width, height)
    val transitionSteps: Int = TRANSITION_STEPS

    private val random = java.util.Random(seed)

    private val _positions:  MutableMap<Page, NodePosition> = LinkedHashMap()
    private val velocities:  MutableMap<Page, DoubleArray>  = LinkedHashMap()
    private val stillCounts: MutableMap<Page, Int>          = LinkedHashMap()

    private var pinnedPage:  Page?   = null
    private var transStartX: Double  = 0.0
    private var transStartY: Double  = 0.0
    private var transStartZ: Double  = 0.0
    private var transStep:   Int     = TRANSITION_STEPS

    val positions: Map<Page, NodePosition>
        get() = Collections.unmodifiableMap(_positions)

    init {
        initPositions()
    }

    private fun initPositions() {
        for (page in graph.pages()) {
            if (!_positions.containsKey(page)) {
                _positions[page]   = spawnPosition(page)
                velocities[page]   = DoubleArray(3)
                stillCounts[page]  = 0
            }
        }
    }

    private fun spawnPosition(page: Page): NodePosition {
        val nb = mutableListOf<NodePosition>()
        for (n in graph.linksFrom(page)) { _positions[n]?.let { nb.add(it) } }
        for (n in graph.linksTo(page))   { _positions[n]?.let { nb.add(it) } }
        if (nb.isNotEmpty()) {
            val sx = nb.sumOf { it.x }
            val sy = nb.sumOf { it.y }
            val sz = nb.sumOf { it.z }
            val cnt = nb.size.toDouble()
            val j = min(width, height) * 0.18
            return NodePosition(
                clamp(sx / cnt + (random.nextDouble() - 0.5) * j,       0.0, width),
                clamp(sy / cnt + (random.nextDouble() - 0.5) * j,       0.0, height),
                clamp(sz / cnt + (random.nextDouble() - 0.5) * j * 0.4, 0.0, depth))
        }
        val xm = width  * BOUNDARY_MARGIN
        val ym = height * BOUNDARY_MARGIN
        val zm = depth  * BOUNDARY_MARGIN
        return NodePosition(
            xm + random.nextDouble() * (width  - 2 * xm),
            ym + random.nextDouble() * (height - 2 * ym),
            zm + random.nextDouble() * (depth  - 2 * zm))
    }

    fun setPinnedPage(page: Page) {
        pinnedPage = page
        val pos = _positions[page]
        if (pos != null) {
            transStartX = pos.x
            transStartY = pos.y
            transStartZ = pos.z
        } else {
            transStartX = width  / 2.0
            transStartY = height / 2.0
            transStartZ = 0.0
        }
        transStep = 0
        wakeAll()
    }

    fun syncWithGraph() {
        var added = false
        for (page in graph.pages()) {
            if (!_positions.containsKey(page)) {
                _positions[page]   = spawnPosition(page)
                velocities[page]   = DoubleArray(3)
                stillCounts[page]  = 0
                added = true
            }
        }
        if (added) wakeAll()
    }

    private fun wakeAll() {
        for (p in stillCounts.keys) stillCounts[p] = 0
        for (v in velocities.values) { v[0] = 0.0; v[1] = 0.0; v[2] = 0.0 }
    }

    fun step() {
        val pageList = _positions.keys.toList()
        val n = pageList.size
        if (n == 0) return

        val k  = cbrt((width * height * depth) / max(n.toDouble(), 1.0))
        val xm = width  * BOUNDARY_MARGIN
        val ym = height * BOUNDARY_MARGIN
        val zm = depth  * BOUNDARY_MARGIN

        val forces = HashMap<Page, DoubleArray>()
        for (p in pageList) forces[p] = DoubleArray(3)

        // Repulsion between all pairs
        for (i in 0 until n) {
            val u = pageList[i]; val pu = _positions[u]!!
            for (j in i + 1 until n) {
                val v = pageList[j]; val pv = _positions[v]!!
                val dx = pu.x - pv.x; val dy = pu.y - pv.y; val dz = pu.z - pv.z
                val dist = max(sqrt(dx * dx + dy * dy + dz * dz), 0.001)
                val rep = (k * k) / dist
                val fu = forces[u]!!; val fv = forces[v]!!
                fu[0] += (dx / dist) * rep; fu[1] += (dy / dist) * rep; fu[2] += (dz / dist) * rep
                fv[0] -= (dx / dist) * rep; fv[1] -= (dy / dist) * rep; fv[2] -= (dz / dist) * rep
            }
        }

        // Attraction along edges weighted by shared-link count
        for (u in pageList) {
            for (v in graph.linksFrom(u)) {
                if (!_positions.containsKey(v)) continue
                val pu = _positions[u]!!; val pv = _positions[v]!!
                val dx = pu.x - pv.x; val dy = pu.y - pv.y; val dz = pu.z - pv.z
                val dist = max(sqrt(dx * dx + dy * dy + dz * dz), 0.001)
                val att = (dist * dist) / k * (1.0 + graph.sharedLinkCount(u, v))
                val fu = forces[u]!!; val fv = forces[v]!!
                fu[0] -= (dx / dist) * att; fu[1] -= (dy / dist) * att; fu[2] -= (dz / dist) * att
                fv[0] += (dx / dist) * att; fv[1] += (dy / dist) * att; fv[2] += (dz / dist) * att
            }
        }

        // Quadratic boundary repulsion
        for (p in pageList) {
            if (p == pinnedPage) continue
            val pos = _positions[p]!!; val f = forces[p]!!
            if (pos.x < xm)         { val t = (xm - pos.x) / xm;          f[0] += k * t * t * BOUNDARY_STRENGTH }
            if (pos.x > width - xm) { val t = (pos.x - (width - xm)) / xm; f[0] -= k * t * t * BOUNDARY_STRENGTH }
            if (pos.y < ym)         { val t = (ym - pos.y) / ym;          f[1] += k * t * t * BOUNDARY_STRENGTH }
            if (pos.y > height - ym){ val t = (pos.y - (height - ym)) / ym; f[1] -= k * t * t * BOUNDARY_STRENGTH }
            if (pos.z < zm)         { val t = (zm - pos.z) / zm;          f[2] += k * t * t * BOUNDARY_STRENGTH }
            if (pos.z > depth - zm) { val t = (pos.z - (depth - zm)) / zm;  f[2] -= k * t * t * BOUNDARY_STRENGTH }
        }

        // Integrate: skip frozen nodes entirely
        for (p in pageList) {
            if (p == pinnedPage) continue

            var count = stillCounts.getOrDefault(p, 0)
            if (count >= SETTLE_STEPS) continue

            val f = forces[p]!!; val vel = velocities[p]!!
            val pos = _positions[p]!!

            val pv0 = vel[0]; val pv1 = vel[1]; val pv2 = vel[2]

            vel[0] = (vel[0] + f[0] * FORCE_SCALE) * BASE_DAMPING
            vel[1] = (vel[1] + f[1] * FORCE_SCALE) * BASE_DAMPING
            vel[2] = (vel[2] + f[2] * FORCE_SCALE) * BASE_DAMPING

            var speed = sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2])
            if (speed > MAX_SPEED) {
                val s = MAX_SPEED / speed
                vel[0] *= s; vel[1] *= s; vel[2] *= s
                speed = MAX_SPEED
            }

            val dot = pv0 * vel[0] + pv1 * vel[1] + pv2 * vel[2]
            if (dot < 0) {
                vel[0] *= REVERSAL_DAMP; vel[1] *= REVERSAL_DAMP; vel[2] *= REVERSAL_DAMP
                speed  *= REVERSAL_DAMP
                count = min(count + REVERSAL_CREDIT, SETTLE_STEPS)
            } else if (speed < SETTLE_THRESHOLD) {
                count = min(count + 1, SETTLE_STEPS)
            } else {
                count = max(0, count - FAST_DECAY)
            }
            stillCounts[p] = count

            if (count >= SETTLE_STEPS) {
                vel[0] = 0.0; vel[1] = 0.0; vel[2] = 0.0
            } else if (count > 0) {
                val suppress = 1.0 - count.toDouble() / SETTLE_STEPS
                vel[0] *= suppress; vel[1] *= suppress; vel[2] *= suppress
            }

            pos.x = clamp(pos.x + vel[0], 0.0, width)
            pos.y = clamp(pos.y + vel[1], 0.0, height)
            pos.z = clamp(pos.z + vel[2], 0.0, depth)
        }

        // Animate pinned node toward centre using a smoothstep curve
        val pp = pinnedPage?.let { _positions[it] }
        if (pp != null) {
            if (transStep < TRANSITION_STEPS) {
                transStep++
                val t  = transStep.toDouble() / TRANSITION_STEPS
                val t2 = t * t * (3 - 2 * t)
                pp.x = transStartX + (width  / 2.0 - transStartX) * t2
                pp.y = transStartY + (height / 2.0 - transStartY) * t2
                pp.z = transStartZ + (0.0          - transStartZ) * t2
            } else {
                pp.x = width  / 2.0
                pp.y = height / 2.0
                pp.z = 0.0
            }
        }
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
