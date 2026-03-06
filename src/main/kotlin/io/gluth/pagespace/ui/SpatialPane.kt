package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.presenter.SpatialMath
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

    private var viewRotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
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
                viewRotation = SpatialMath.lerpToIdentity(viewRotation, VIEW_RESET_SPEED)
                if (SpatialMath.isNearIdentity(viewRotation, VIEW_RESET_SNAP)) {
                    viewRotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
                    resettingView = false
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
                    val nearest = SpatialMath.nearestPage(
                        e.x, e.y, cachedProjections, cachedScales, BASE_RADIUS, CLICK_RADIUS
                    )
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
                viewRotation = SpatialMath.applyYaw(viewRotation, dx * DRAG_SENSITIVITY)
                viewRotation = SpatialMath.applyPitch(viewRotation, dy * DRAG_SENSITIVITY)
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
        if (!SpatialMath.isNearIdentity(viewRotation, VIEW_RESET_SNAP)) {
            resettingView = true
        }
        repaint()
    }

    // ------------------------------------------------------------------ paint

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val positions = layout.positions
        if (positions.isEmpty()) return

        val lw = layout.width
        val lh = layout.height
        val ld = layout.depth
        val vw = width.toDouble()
        val vh = height.toDouble()

        // Pre-compute rotated coordinates for all nodes
        val rotated = LinkedHashMap<Page, DoubleArray>()
        for ((page, np) in positions) {
            rotated[page] = SpatialMath.viewTransform(
                np.x, np.y, np.z, viewRotation, lw, lh, ld
            )
        }

        // Cache projected coordinates and scales for hit-testing
        val projCache = LinkedHashMap<Page, IntArray>()
        val scaleCache = LinkedHashMap<Page, Double>()
        for ((page, r3) in rotated) {
            projCache[page] = SpatialMath.project(r3[0], r3[1], r3[2], vw, vh, lw, lh, ld)
            scaleCache[page] = SpatialMath.perspScale(r3[2], ld)
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
                val alpha = SpatialMath.alphaFor(avgZ, ld, MIN_ALPHA).toFloat()
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

            val scale = scaleCache[page] ?: SpatialMath.perspScale(r3[2], ld)
            val alpha = if (curr) 1.0f else SpatialMath.alphaFor(r3[2], ld, MIN_ALPHA).toFloat()
            val sc    = projCache[page] ?: SpatialMath.project(r3[0], r3[1], r3[2], vw, vh, lw, lh, ld)
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
            val labelWidth = fm.stringWidth(label)
            g2.color  = withAlpha(LABEL_COLOR, alpha)
            g2.drawString(label, cx - labelWidth / 2, cy + r + fm.ascent + 1)
        }
    }

    private fun withAlpha(c: Color, alpha: Float): Color {
        val a = alpha.coerceIn(0f, 1f)
        return Color(c.red, c.green, c.blue, (a * 255).toInt())
    }
}
