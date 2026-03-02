package io.gluth.pagespace.layout

import kotlin.math.max
import kotlin.math.sqrt

class NodePosition internal constructor(x: Double, y: Double, z: Double) {

    var x: Double = x
        internal set
    var y: Double = y
        internal set
    var z: Double = z
        internal set

    fun distanceTo(other: NodePosition): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return max(sqrt(dx * dx + dy * dy + dz * dz), 0.001)
    }

    override fun toString(): String = "NodePosition[$x, $y, $z]"
}
