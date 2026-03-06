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
     * Rotates a world position using a 3×3 rotation matrix (row-major, 9 elements).
     * Returns [rx, ry, rz] in layout coordinates.
     */
    fun viewTransform(
        x: Double, y: Double, z: Double,
        rot: DoubleArray,
        layoutWidth: Double, layoutHeight: Double, layoutDepth: Double
    ): DoubleArray {
        val ocx = layoutWidth / 2.0
        val ocy = layoutHeight / 2.0
        val ocz = layoutDepth / 2.0
        val lx = x - ocx; val ly = y - ocy; val lz = z - ocz
        val rx = rot[0]*lx + rot[1]*ly + rot[2]*lz
        val ry = rot[3]*lx + rot[4]*ly + rot[5]*lz
        val rz = rot[6]*lx + rot[7]*ly + rot[8]*lz
        return doubleArrayOf(rx + ocx, ry + ocy, rz + ocz)
    }

    /** Left-multiplies R by Ry(dYaw): always rotates around world Y axis. */
    fun applyYaw(R: DoubleArray, dYaw: Double): DoubleArray {
        val c = cos(dYaw); val s = sin(dYaw)
        return doubleArrayOf(
             c*R[0] + s*R[6],  c*R[1] + s*R[7],  c*R[2] + s*R[8],
             R[3],              R[4],              R[5],
            -s*R[0] + c*R[6], -s*R[1] + c*R[7], -s*R[2] + c*R[8]
        )
    }

    /** Left-multiplies R by Rx(dPitch): always rotates around world X axis. */
    fun applyPitch(R: DoubleArray, dPitch: Double): DoubleArray {
        val c = cos(dPitch); val s = sin(dPitch)
        return doubleArrayOf(
            R[0], R[1], R[2],
            c*R[3] - s*R[6], c*R[4] - s*R[7], c*R[5] - s*R[8],
            s*R[3] + c*R[6], s*R[4] + c*R[7], s*R[5] + c*R[8]
        )
    }

    /** Linearly interpolates R toward identity by fraction t, then re-orthonormalises. */
    fun lerpToIdentity(R: DoubleArray, t: Double): DoubleArray {
        val m = DoubleArray(9) { i ->
            val id = if (i == 0 || i == 4 || i == 8) 1.0 else 0.0
            R[i] * (1.0 - t) + id * t
        }
        return orthonormalize(m)
    }

    /** Returns true when R is within [threshold] of identity on every element. */
    fun isNearIdentity(R: DoubleArray, threshold: Double): Boolean {
        return abs(R[0] - 1.0) < threshold && abs(R[4] - 1.0) < threshold && abs(R[8] - 1.0) < threshold &&
               abs(R[1]) < threshold && abs(R[2]) < threshold && abs(R[3]) < threshold &&
               abs(R[5]) < threshold && abs(R[6]) < threshold && abs(R[7]) < threshold
    }

    private fun orthonormalize(m: DoubleArray): DoubleArray {
        var x0 = m[0]; var x1 = m[1]; var x2 = m[2]
        var y0 = m[3]; var y1 = m[4]; var y2 = m[5]
        var len = sqrt(x0*x0 + x1*x1 + x2*x2)
        if (len > 0) { x0 /= len; x1 /= len; x2 /= len }
        val dot = y0*x0 + y1*x1 + y2*x2
        y0 -= dot*x0; y1 -= dot*x1; y2 -= dot*x2
        len = sqrt(y0*y0 + y1*y1 + y2*y2)
        if (len > 0) { y0 /= len; y1 /= len; y2 /= len }
        val z0 = x1*y2 - x2*y1; val z1 = x2*y0 - x0*y2; val z2 = x0*y1 - x1*y0
        return doubleArrayOf(x0, x1, x2, y0, y1, y2, z0, z1, z2)
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
