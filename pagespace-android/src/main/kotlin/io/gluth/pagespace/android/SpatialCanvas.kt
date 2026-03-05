package io.gluth.pagespace.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.presenter.SpatialMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.hypot

private const val BASE_RADIUS = 18
private const val CLICK_RADIUS = 28
private val NODE_COLOR = Color(0xFF4682C8)
private val CURR_COLOR = Color(0xFFE6781E)
private val EDGE_COLOR = Color(0xFF8CA0C8)
private val LABEL_COLOR = Color(0xFFDCE1F5)
private val BG_COLOR = Color(0xFF121423)
private const val MIN_ALPHA = 0.20f
private const val DRAG_SENSITIVITY = 0.005f
private const val DRAG_THRESHOLD = 5f
private const val VIEW_RESET_SPEED = 0.08f
private const val VIEW_RESET_SNAP = 0.001f

@Composable
fun SpatialCanvas(
    layout: ForceDirectedLayout,
    currentPage: Page?,
    positionsVersion: Int,
    onNavigate: (Page) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewYaw by remember { mutableDoubleStateOf(0.0) }
    var viewPitch by remember { mutableDoubleStateOf(0.0) }
    var resettingView by remember { mutableStateOf(false) }
    var frameCount by remember { mutableIntStateOf(0) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Cache projected coordinates for tap hit-testing
    var cachedProjections by remember { mutableStateOf<Map<Page, IntArray>>(emptyMap()) }
    var cachedScales by remember { mutableStateOf<Map<Page, Double>>(emptyMap()) }

    // Track current page changes to trigger view reset
    var lastNavPage by remember { mutableStateOf<Page?>(null) }
    if (currentPage != null && currentPage != lastNavPage) {
        lastNavPage = currentPage
        if (abs(viewYaw) > VIEW_RESET_SNAP || abs(viewPitch) > VIEW_RESET_SNAP) {
            resettingView = true
        }
    }

    val textMeasurer = rememberTextMeasurer()

    // Animation loop
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                layout.step()
                if (resettingView) {
                    viewYaw *= (1 - VIEW_RESET_SPEED)
                    viewPitch *= (1 - VIEW_RESET_SPEED)
                    if (abs(viewYaw) < VIEW_RESET_SNAP && abs(viewPitch) < VIEW_RESET_SNAP) {
                        viewYaw = 0.0
                        viewPitch = 0.0
                        resettingView = false
                    }
                }
                frameCount++
            }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val nearest = SpatialMath.nearestPage(
                        offset.x.toInt(), offset.y.toInt(),
                        cachedProjections, cachedScales,
                        BASE_RADIUS, CLICK_RADIUS
                    )
                    if (nearest != null) onNavigate(nearest)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    resettingView = false
                    viewYaw += dragAmount.x * DRAG_SENSITIVITY
                    viewYaw = normalizeAngle(viewYaw)
                    viewPitch += dragAmount.y * DRAG_SENSITIVITY
                    viewPitch = viewPitch.coerceIn(-PI / 2, PI / 2)
                }
            }
    ) {
        canvasWidth = size.width
        canvasHeight = size.height

        // Force recomposition on frame tick and navigation changes
        frameCount.let {}
        positionsVersion.let {}

        val positions = layout.positions
        if (positions.isEmpty()) return@Canvas

        val lw = layout.width
        val lh = layout.height
        val ld = layout.depth
        val vw = size.width.toDouble()
        val vh = size.height.toDouble()

        // Pre-compute rotated coordinates
        val rotated = LinkedHashMap<Page, DoubleArray>()
        for ((page, np) in positions) {
            rotated[page] = SpatialMath.viewTransform(
                np.x, np.y, np.z, viewYaw, viewPitch, lw, lh, ld
            )
        }

        // Cache projected coordinates and scales
        val projCache = LinkedHashMap<Page, IntArray>()
        val scaleCache = LinkedHashMap<Page, Double>()
        for ((page, r3) in rotated) {
            projCache[page] = SpatialMath.project(r3[0], r3[1], r3[2], vw, vh, lw, lh, ld)
            scaleCache[page] = SpatialMath.perspScale(r3[2], ld)
        }
        cachedProjections = projCache
        cachedScales = scaleCache

        // Sort back-to-front
        val entries = rotated.entries.sortedByDescending { it.value[2] }

        // Draw edges
        for (entry in entries) {
            val src = entry.key
            val sr = entry.value
            for (tgt in layout.edgesFrom(src)) {
                val tr = rotated[tgt] ?: continue
                val avgZ = (sr[2] + tr[2]) / 2.0
                val alpha = SpatialMath.alphaFor(avgZ, ld, MIN_ALPHA.toDouble()).toFloat() * 0.55f
                val sp = projCache[src] ?: continue
                val ep = projCache[tgt] ?: continue
                drawLine(
                    color = EDGE_COLOR.copy(alpha = alpha),
                    start = Offset(sp[0].toFloat(), sp[1].toFloat()),
                    end = Offset(ep[0].toFloat(), ep[1].toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        // Draw nodes back-to-front
        for (entry in entries) {
            val page = entry.key
            val r3 = entry.value
            val curr = page == currentPage

            val scale = scaleCache[page] ?: SpatialMath.perspScale(r3[2], ld)
            val alpha = if (curr) 1f else SpatialMath.alphaFor(r3[2], ld, MIN_ALPHA.toDouble()).toFloat()
            val sc = projCache[page] ?: continue
            val cx = sc[0].toFloat()
            val cy = sc[1].toFloat()
            val r = max(4f, (BASE_RADIUS * scale).toFloat())

            val baseColor = if (curr) CURR_COLOR else NODE_COLOR
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = r,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.5f),
                radius = r,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )

            // Label
            val fontSize = max(8f, (12f * scale.toFloat()))
            drawNodeLabel(
                textMeasurer, page.title,
                cx, cy + r + 2f,
                fontSize, curr, alpha
            )
        }
    }
}

private fun DrawScope.drawNodeLabel(
    textMeasurer: TextMeasurer,
    label: String,
    cx: Float, topY: Float,
    fontSize: Float, isCurrent: Boolean, alpha: Float
) {
    val style = TextStyle(
        color = LABEL_COLOR.copy(alpha = alpha),
        fontSize = fontSize.sp,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
    )
    val result = textMeasurer.measure(label, style)
    drawText(
        result,
        topLeft = Offset(cx - result.size.width / 2f, topY)
    )
}

private fun normalizeAngle(a: Double): Double {
    var r = a % (2 * PI)
    if (r > PI) r -= 2 * PI
    if (r < -PI) r += 2 * PI
    return r
}
