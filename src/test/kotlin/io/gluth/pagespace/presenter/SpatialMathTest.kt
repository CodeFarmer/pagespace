package io.gluth.pagespace.presenter

import io.gluth.pagespace.domain.Page
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialMathTest {

    private val W = 800.0
    private val H = 600.0
    private val D = 600.0  // min(W, H)

    @Test
    fun viewTransformIdentityRotation() {
        val result = SpatialMath.viewTransform(100.0, 200.0, 300.0, 0.0, 0.0, W, H, D)
        assertClose(100.0, result[0])
        assertClose(200.0, result[1])
        assertClose(300.0, result[2])
    }

    @Test
    fun viewTransformCentreUnchangedByRotation() {
        // The centre of the layout should remain at centre regardless of rotation
        val cx = W / 2.0; val cy = H / 2.0; val cz = D / 2.0
        val result = SpatialMath.viewTransform(cx, cy, cz, 0.5, 0.3, W, H, D)
        assertClose(cx, result[0])
        assertClose(cy, result[1])
        assertClose(cz, result[2])
    }

    @Test
    fun viewTransformNonZeroAnglesMovesPoint() {
        val result = SpatialMath.viewTransform(100.0, 200.0, 300.0, 0.5, 0.3, W, H, D)
        // With non-zero rotation, the point should be different from identity
        val identity = SpatialMath.viewTransform(100.0, 200.0, 300.0, 0.0, 0.0, W, H, D)
        val moved = abs(result[0] - identity[0]) > 0.01 ||
                    abs(result[1] - identity[1]) > 0.01 ||
                    abs(result[2] - identity[2]) > 0.01
        assertTrue(moved, "Non-zero rotation should move the point")
    }

    @Test
    fun projectCentreMapsToScreenCentre() {
        // A point at layout centre with z=0 projects to screen centre
        val cx = W / 2.0; val cy = H / 2.0
        val vw = 800.0; val vh = 600.0
        val result = SpatialMath.project(cx, cy, 0.0, vw, vh, W, H, D)
        assertEquals((vw / 2.0).toInt(), result[0])
        assertEquals((vh / 2.0).toInt(), result[1])
    }

    @Test
    fun perspScaleAtZeroReturnsOne() {
        assertClose(1.0, SpatialMath.perspScale(0.0, D))
    }

    @Test
    fun perspScalePositiveZLessThanOne() {
        val scale = SpatialMath.perspScale(300.0, D)
        assertTrue(scale < 1.0)
        assertTrue(scale > 0.0)
    }

    @Test
    fun alphaForAtZeroReturnsOne() {
        assertClose(1.0, SpatialMath.alphaFor(0.0, D))
    }

    @Test
    fun alphaForAtDepthReturnsMinAlpha() {
        val alpha = SpatialMath.alphaFor(D, D, 0.20)
        assertClose(0.20, alpha)
    }

    @Test
    fun alphaForZeroDepthReturnsOne() {
        assertEquals(1.0, SpatialMath.alphaFor(100.0, 0.0))
    }

    @Test
    fun nearestPageFindsClosest() {
        val a = Page("a", "A")
        val b = Page("b", "B")
        val projections = mapOf(
            a to intArrayOf(100, 100),
            b to intArrayOf(200, 200)
        )
        val scales = mapOf(a to 1.0, b to 1.0)

        val result = SpatialMath.nearestPage(105, 105, projections, scales, 18, 28)
        assertEquals(a, result)
    }

    @Test
    fun nearestPageReturnsNullWhenFarAway() {
        val a = Page("a", "A")
        val projections = mapOf(a to intArrayOf(100, 100))
        val scales = mapOf(a to 1.0)

        val result = SpatialMath.nearestPage(500, 500, projections, scales, 18, 28)
        assertNull(result)
    }

    @Test
    fun nearestPageEmptyProjections() {
        val result = SpatialMath.nearestPage(100, 100, emptyMap(), emptyMap(), 18, 28)
        assertNull(result)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.001) {
        assertTrue(abs(expected - actual) < tolerance,
            "Expected $expected but got $actual (tolerance $tolerance)")
    }
}
