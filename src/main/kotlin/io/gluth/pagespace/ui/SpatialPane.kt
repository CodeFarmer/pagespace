package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.layout.NodePosition
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.*

class SpatialPane(private val layout: ForceDirectedLayout) : JPanel() {

    companion object {
        private const val BASE_RADIUS  = 18
        private const val CLICK_RADIUS = 28
        private val NODE_COLOR  = Color(70, 130, 200)
        private val CURR_COLOR  = Color(230, 120, 30)
        private val EDGE_COLOR  = Color(140, 160, 200)
        private val LABEL_COLOR = Color(220, 225, 245)
        private const val MIN_ALPHA = 0.20
        private const val DRAG_SENSITIVITY = 0.005
        private const val DRAG_THRESHOLD   = 5
        private const val VIEW_RESET_SPEED = 0.08
        private const val VIEW_RESET_SNAP  = 0.001
    }

    private var currentPage: Page? = null
    private var navigationListener: NavigationListener? = null

    private var viewYaw   = 0.0
    private var viewPitch = 0.0
    private var resettingView = false

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragging   = false

    // Cached projected coordinates from last paintComponent, keyed by page
    private var cachedProjections: Map<Page, IntArray> = emptyMap()
    private var cachedScales:      Map<Page, Double>   = emptyMap()

    init {
        background = Color(18, 20, 35)

        val timer = Timer(30) {
            layout.step()
            if (resettingView) {
                viewYaw   *= (1 - VIEW_RESET_SPEED)
                viewPitch *= (1 - VIEW_RESET_SPEED)
                if (abs(viewYaw) < VIEW_RESET_SNAP && abs(viewPitch) < VIEW_RESET_SNAP) {
                    viewYaw = 0.0; viewPitch = 0.0; resettingView = false
                }
            }
            repaint()
        }
        timer.start()

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragStartX = e.x
                dragStartY = e.y
                dragging = false
            }
            override fun mouseReleased(e: MouseEvent) {
                if (!dragging) {
                    val nearest = nearestPage(e.x, e.y)
                    if (nearest != null) navigationListener?.navigateTo(nearest)
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val dx = e.x - dragStartX
                val dy = e.y - dragStartY
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) < DRAG_THRESHOLD) return
                dragging = true
                resettingView = false
                viewYaw   += dx * DRAG_SENSITIVITY
                viewYaw    = normalizeAngle(viewYaw)
                viewPitch += dy * DRAG_SENSITIVITY
                viewPitch  = viewPitch.coerceIn(-Math.PI / 2, Math.PI / 2)
                dragStartX = e.x
                dragStartY = e.y
                repaint()
            }
        })
    }

    fun setNavigationListener(listener: NavigationListener) {
        navigationListener = listener
    }

    fun setCurrentPage(page: Page) {
        currentPage = page
        if (abs(viewYaw) > VIEW_RESET_SNAP || abs(viewPitch) > VIEW_RESET_SNAP) {
            resettingView = true
        }
        repaint()
    }

    private fun normalizeAngle(a: Double): Double {
        var r = a % (2 * Math.PI)
        if (r > Math.PI) r -= 2 * Math.PI
        if (r < -Math.PI) r += 2 * Math.PI
        return r
    }

    // ------------------------------------------------------------------ paint

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val positions = layout.positions
        if (positions.isEmpty()) return

        // Pre-compute rotated coordinates for all nodes
        val rotated = LinkedHashMap<Page, DoubleArray>()
        for ((page, np) in positions) rotated[page] = viewTransform(np)

        // Cache projected coordinates and scales for hit-testing
        val projCache = LinkedHashMap<Page, IntArray>()
        val scaleCache = LinkedHashMap<Page, Double>()
        for ((page, r3) in rotated) {
            projCache[page] = project(r3[0], r3[1], r3[2])
            scaleCache[page] = perspScale(r3[2])
        }
        cachedProjections = projCache
        cachedScales = scaleCache

        // Sort back-to-front by rotated z (largest first)
        val entries = rotated.entries.sortedByDescending { it.value[2] }

        // Draw edges first
        g2.stroke = BasicStroke(1.0f)
        for (entry in entries) {
            val src = entry.key; val sr = entry.value
            for (tgt in layout.edgesFrom(src)) {
                val tr = rotated[tgt] ?: continue
                val avgZ  = (sr[2] + tr[2]) / 2.0
                val alpha = alphaFor(avgZ).toFloat()
                val sp    = projCache[src] ?: continue
                val ep    = projCache[tgt] ?: continue
                g2.color  = withAlpha(EDGE_COLOR, alpha * 0.55f)
                g2.drawLine(sp[0], sp[1], ep[0], ep[1])
            }
        }

        // Draw nodes back-to-front
        val baseFont = font
        for (entry in entries) {
            val page  = entry.key
            val r3    = entry.value
            val curr  = page == currentPage

            val scale = scaleCache[page] ?: perspScale(r3[2])
            val alpha = if (curr) 1.0f else alphaFor(r3[2]).toFloat()
            val sc    = projCache[page] ?: project(r3[0], r3[1], r3[2])
            val cx    = sc[0]; val cy = sc[1]
            val r     = max(4, (BASE_RADIUS * scale).toInt())

            val baseColor = if (curr) CURR_COLOR else NODE_COLOR
            g2.color = withAlpha(baseColor, alpha)
            g2.fillOval(cx - r, cy - r, r * 2, r * 2)

            g2.color = withAlpha(Color.WHITE, alpha * 0.5f)
            g2.stroke = BasicStroke(1.2f)
            g2.drawOval(cx - r, cy - r, r * 2, r * 2)

            val fontSize = max(8f, (12.0 * scale).toFloat())
            g2.font = baseFont.deriveFont(if (curr) Font.BOLD else Font.PLAIN, fontSize)
            val fm    = g2.fontMetrics
            val label = page.title
            val lw    = fm.stringWidth(label)
            g2.color  = withAlpha(LABEL_COLOR, alpha)
            g2.drawString(label, cx - lw / 2, cy + r + fm.ascent + 1)
        }
    }

    // -------------------------------------------------------------- hit-test

    private fun nearestPage(mx: Int, my: Int): Page? {
        val projections = cachedProjections
        val scales = cachedScales
        if (projections.isEmpty()) return null

        var nearest: Page? = null
        var minDist = CLICK_RADIUS.toDouble()

        for ((page, sc) in projections) {
            val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
            val scale = scales[page] ?: 1.0
            val r = BASE_RADIUS * scale
            if (dist < max(r, CLICK_RADIUS / 2.0) && dist < minDist) {
                minDist = dist
                nearest = page
            }
        }
        if (nearest == null) {
            for ((page, sc) in projections) {
                val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
                if (dist < CLICK_RADIUS && dist < minDist) {
                    minDist = dist
                    nearest = page
                }
            }
        }
        return nearest
    }

    // ---------------------------------------------------- view transform

    /** Rotates a world position around the sphere centre by viewYaw/viewPitch. Returns [rx, ry, rz]. */
    private fun viewTransform(np: NodePosition): DoubleArray {
        val ocx = layout.width  / 2.0
        val ocy = layout.height / 2.0
        val ocz = layout.depth  / 2.0

        // translate to origin
        var x = np.x - ocx
        var y = np.y - ocy
        var z = np.z - ocz

        // yaw: rotate around Y axis
        val cosY = cos(viewYaw); val sinY = sin(viewYaw)
        val x1 = x * cosY + z * sinY
        val z1 = -x * sinY + z * cosY

        // pitch: rotate around X axis
        val cosP = cos(viewPitch); val sinP = sin(viewPitch)
        val y2 = y * cosP - z1 * sinP
        val z2 = y * sinP + z1 * cosP

        return doubleArrayOf(x1 + ocx, y2 + ocy, z2 + ocz)
    }

    // ------------------------------------------------------------ projection

    private fun project(rx: Double, ry: Double, rz: Double): IntArray {
        val pw = width.toDouble()
        val ph = height.toDouble()
        val cx = pw / 2.0
        val cy = ph / 2.0
        val bx = rx * pw / layout.width
        val by = ry * ph / layout.height
        val s  = perspScale(rz)
        return intArrayOf((cx + (bx - cx) * s).toInt(), (cy + (by - cy) * s).toInt())
    }

    private fun perspScale(wz: Double): Double {
        val focal = layout.depth
        return focal / (focal + wz)
    }

    private fun alphaFor(wz: Double): Double {
        val d = layout.depth
        if (d <= 0) return 1.0
        return 1.0 - (wz / d) * (1.0 - MIN_ALPHA)
    }

    private fun withAlpha(c: Color, alpha: Float): Color {
        val a = alpha.coerceIn(0f, 1f)
        return Color(c.red, c.green, c.blue, (a * 255).toInt())
    }
}
