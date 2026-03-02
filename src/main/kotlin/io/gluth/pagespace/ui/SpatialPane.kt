package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.layout.NodePosition
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.hypot
import kotlin.math.max

class SpatialPane(private val layout: ForceDirectedLayout) : JPanel() {

    companion object {
        private const val BASE_RADIUS  = 18
        private const val CLICK_RADIUS = 28
        private val NODE_COLOR  = Color(70, 130, 200)
        private val CURR_COLOR  = Color(230, 120, 30)
        private val EDGE_COLOR  = Color(140, 160, 200)
        private val LABEL_COLOR = Color(220, 225, 245)
        private const val MIN_ALPHA = 0.20
    }

    private var currentPage: Page? = null
    private var navigationListener: NavigationListener? = null

    init {
        background = Color(18, 20, 35)

        val timer = Timer(30) {
            layout.step()
            repaint()
        }
        timer.start()

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val nearest = nearestPage(e.x, e.y)
                if (nearest != null) navigationListener?.navigateTo(nearest)
            }
        })
    }

    fun setNavigationListener(listener: NavigationListener) {
        navigationListener = listener
    }

    fun setCurrentPage(page: Page) {
        currentPage = page
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

        // Sort back-to-front (largest z first)
        val entries = positions.entries.sortedByDescending { it.value.z }

        // Draw edges first
        g2.stroke = BasicStroke(1.0f)
        for (entry in entries) {
            val src = entry.key
            for (tgt in layout.graph.linksFrom(src)) {
                val tp = positions[tgt] ?: continue
                val avgZ  = (entry.value.z + tp.z) / 2.0
                val alpha = alphaFor(avgZ).toFloat()
                val sp    = project(entry.value)
                val ep    = project(tp)
                g2.color  = withAlpha(EDGE_COLOR, alpha * 0.55f)
                g2.drawLine(sp[0], sp[1], ep[0], ep[1])
            }
        }

        // Draw nodes back-to-front
        val baseFont = font
        for (entry in entries) {
            val page  = entry.key
            val np    = entry.value
            val curr  = page == currentPage

            val scale = perspScale(np.z)
            val alpha = alphaFor(np.z).toFloat()
            val sc    = project(np)
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
        val positions = layout.positions
        if (positions.isEmpty()) return null
        var nearest: Page? = null
        var minDist = CLICK_RADIUS.toDouble()
        for ((page, np) in positions) {
            val sc   = project(np)
            val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
            val r    = BASE_RADIUS * perspScale(np.z)
            if (dist < max(r, CLICK_RADIUS / 2.0) && dist < minDist) {
                minDist = dist
                nearest = page
            }
        }
        if (nearest == null) {
            for ((page, np) in positions) {
                val sc   = project(np)
                val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
                if (dist < CLICK_RADIUS && dist < minDist) {
                    minDist = dist
                    nearest = page
                }
            }
        }
        return nearest
    }

    // ------------------------------------------------------------ projection

    private fun project(np: NodePosition): IntArray {
        val pw = width.toDouble()
        val ph = height.toDouble()
        val cx = pw / 2.0
        val cy = ph / 2.0
        val bx = np.x * pw / layout.width
        val by = np.y * ph / layout.height
        val s  = perspScale(np.z)
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
