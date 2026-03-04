package io.gluth.pagespace.presenter

import io.gluth.pagespace.domain.Page
import kotlin.math.*

object SpatialMath {

    /**
     * Rotates a world position around the layout centre by yaw/pitch.
     * Returns [rx, ry, rz] in layout coordinates.
     */
    fun viewTransform(
        x: Double, y: Double, z: Double,
        viewYaw: Double, viewPitch: Double,
        layoutWidth: Double, layoutHeight: Double, layoutDepth: Double
    ): DoubleArray {
        val ocx = layoutWidth / 2.0
        val ocy = layoutHeight / 2.0
        val ocz = layoutDepth / 2.0

        // translate to origin
        var lx = x - ocx
        var ly = y - ocy
        var lz = z - ocz

        // yaw: rotate around Y axis
        val cosY = cos(viewYaw); val sinY = sin(viewYaw)
        val x1 = lx * cosY + lz * sinY
        val z1 = -lx * sinY + lz * cosY

        // pitch: rotate around X axis
        val cosP = cos(viewPitch); val sinP = sin(viewPitch)
        val y2 = ly * cosP - z1 * sinP
        val z2 = ly * sinP + z1 * cosP

        return doubleArrayOf(x1 + ocx, y2 + ocy, z2 + ocz)
    }

    /**
     * Projects a rotated 3D point to 2D screen coordinates.
     * Returns [screenX, screenY].
     */
    fun project(
        rx: Double, ry: Double, rz: Double,
        viewWidth: Double, viewHeight: Double,
        layoutWidth: Double, layoutHeight: Double, layoutDepth: Double
    ): IntArray {
        val cx = viewWidth / 2.0
        val cy = viewHeight / 2.0
        val bx = rx * viewWidth / layoutWidth
        val by = ry * viewHeight / layoutHeight
        val s = perspScale(rz, layoutDepth)
        return intArrayOf((cx + (bx - cx) * s).toInt(), (cy + (by - cy) * s).toInt())
    }

    /** Perspective scale factor: focal length / (focal length + z). */
    fun perspScale(wz: Double, depth: Double): Double {
        return depth / (depth + wz)
    }

    /** Depth-based alpha: 1.0 at z=0, fading to [minAlpha] at z=depth. */
    fun alphaFor(wz: Double, depth: Double, minAlpha: Double = 0.20): Double {
        if (depth <= 0) return 1.0
        return 1.0 - (wz / depth) * (1.0 - minAlpha)
    }

    /**
     * Finds the nearest page to a mouse click position from pre-computed projections.
     * Two-pass: first checks scaled radius, then falls back to fixed click radius.
     */
    fun nearestPage(
        mx: Int, my: Int,
        projections: Map<Page, IntArray>,
        scales: Map<Page, Double>,
        baseRadius: Int, clickRadius: Int
    ): Page? {
        if (projections.isEmpty()) return null

        var nearest: Page? = null
        var minDist = clickRadius.toDouble()

        for ((page, sc) in projections) {
            val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
            val scale = scales[page] ?: 1.0
            val r = baseRadius * scale
            if (dist < max(r, clickRadius / 2.0) && dist < minDist) {
                minDist = dist
                nearest = page
            }
        }
        if (nearest == null) {
            for ((page, sc) in projections) {
                val dist = hypot((mx - sc[0]).toDouble(), (my - sc[1]).toDouble())
                if (dist < clickRadius && dist < minDist) {
                    minDist = dist
                    nearest = page
                }
            }
        }
        return nearest
    }
}
