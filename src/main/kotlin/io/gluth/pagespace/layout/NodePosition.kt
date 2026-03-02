package io.gluth.pagespace.layout

import kotlin.math.max
import kotlin.math.sqrt

class NodePosition(x: Double, y: Double, z: Double) {

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodePosition) return false
        return x == other.x && y == other.y && z == other.z
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun toString(): String = "NodePosition[$x, $y, $z]"
}
