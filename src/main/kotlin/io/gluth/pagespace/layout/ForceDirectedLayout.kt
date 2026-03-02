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
        private const val SPHERE_MARGIN     = 0.12
        private const val BOUNDARY_STRENGTH = 50.0
        private const val TRANSITION_STEPS = 35
        private const val MAX_COMPUTE_ITERATIONS = 500
    }

    enum class Phase { IDLE, ANIMATING }

    val depth: Double = min(width, height)
    val sphereRadius: Double = min(width, height) / 2.0
    private val cx = width / 2.0
    private val cy = height / 2.0
    private val cz = depth / 2.0
    val transitionSteps: Int = TRANSITION_STEPS

    private val random = java.util.Random(seed)

    private val _positions:  MutableMap<Page, NodePosition> = LinkedHashMap()
    private val velocities:  MutableMap<Page, DoubleArray>  = LinkedHashMap()
    private val stillCounts: MutableMap<Page, Int>          = LinkedHashMap()

    private var pinnedPage: Page? = null

    private var phase: Phase = Phase.IDLE
    private val animStart:  MutableMap<Page, NodePosition> = LinkedHashMap()
    private val animTarget: MutableMap<Page, NodePosition> = LinkedHashMap()
    private var animStep: Int = 0

    val positions: Map<Page, NodePosition>
        get() = Collections.unmodifiableMap(_positions)

    val isSettled: Boolean
        get() = isConverged()

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
        // Random point inside sphere (radius * 0.8 to keep away from boundary)
        val r   = sphereRadius * 0.8 * cbrt(random.nextDouble())
        val theta = random.nextDouble() * 2 * Math.PI
        val phi   = Math.acos(2 * random.nextDouble() - 1)
        return NodePosition(
            cx + r * Math.sin(phi) * Math.cos(theta),
            cy + r * Math.sin(phi) * Math.sin(theta),
            cz + r * Math.cos(phi))
    }

    fun setPinnedPage(page: Page) {
        pinnedPage = page
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

    fun computeEquilibrium() {
        // 1. Snapshot current positions as animation start
        animStart.clear()
        for ((page, pos) in _positions) {
            animStart[page] = NodePosition(pos.x, pos.y, pos.z)
        }

        // 2. Wake all nodes and run force simulation to convergence
        wakeAll()
        for (i in 0 until MAX_COMPUTE_ITERATIONS) {
            stepForces()
            if (isConverged()) break
        }

        // 3. Snapshot converged positions as animation targets
        animTarget.clear()
        for ((page, pos) in _positions) {
            animTarget[page] = NodePosition(pos.x, pos.y, pos.z)
        }

        // 4. Restore positions to animation start
        for ((page, startPos) in animStart) {
            val pos = _positions[page] ?: continue
            pos.x = startPos.x
            pos.y = startPos.y
            pos.z = startPos.z
        }

        // 5. Begin animation phase
        animStep = 0
        phase = Phase.ANIMATING
    }

    fun completeAnimation() {
        for ((page, target) in animTarget) {
            val pos = _positions[page] ?: continue
            pos.x = target.x
            pos.y = target.y
            pos.z = target.z
        }
        phase = Phase.IDLE
        animStart.clear()
        animTarget.clear()
    }

    private fun isConverged(): Boolean {
        if (_positions.isEmpty()) return false
        for ((page, count) in stillCounts) {
            if (page == pinnedPage) continue
            if (count < SETTLE_STEPS) return false
        }
        return true
    }

    private fun stepForces() {
        val pageList = _positions.keys.toList()
        val n = pageList.size
        if (n == 0) return

        val k  = cbrt((width * height * depth) / max(n.toDouble(), 1.0))
        val innerRadius = sphereRadius * (1 - SPHERE_MARGIN)

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

        // Spherical boundary repulsion: repel nodes that exceed the inner radius toward centre
        for (p in pageList) {
            if (p == pinnedPage) continue
            val pos = _positions[p]!!; val f = forces[p]!!
            val dx = pos.x - cx; val dy = pos.y - cy; val dz = pos.z - cz
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > innerRadius && dist > 0.001) {
                val t = (dist - innerRadius) / (sphereRadius - innerRadius)
                val mag = k * t * t * BOUNDARY_STRENGTH
                f[0] -= (dx / dist) * mag
                f[1] -= (dy / dist) * mag
                f[2] -= (dz / dist) * mag
            }
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

        // Clamp pinned page to center
        val pp = pinnedPage?.let { _positions[it] }
        if (pp != null) {
            pp.x = width  / 2.0
            pp.y = height / 2.0
            pp.z = 0.0
        }
    }

    fun step() {
        when (phase) {
            Phase.IDLE -> return
            Phase.ANIMATING -> {
                animStep++
                val t  = min(animStep.toDouble() / TRANSITION_STEPS, 1.0)
                val t2 = t * t * (3 - 2 * t)

                for ((page, pos) in _positions) {
                    val start  = animStart[page]  ?: continue
                    val target = animTarget[page] ?: continue
                    pos.x = start.x + (target.x - start.x) * t2
                    pos.y = start.y + (target.y - start.y) * t2
                    pos.z = start.z + (target.z - start.z) * t2
                }

                if (animStep >= TRANSITION_STEPS) {
                    for ((page, target) in animTarget) {
                        val pos = _positions[page] ?: continue
                        pos.x = target.x
                        pos.y = target.y
                        pos.z = target.z
                    }
                    phase = Phase.IDLE
                    animStart.clear()
                    animTarget.clear()
                }
            }
        }
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
