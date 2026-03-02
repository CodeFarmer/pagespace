package io.gluth.pagespace.domain

class Link(source: Page?, target: Page?) {

    val source: Page = source ?: throw NullPointerException("source")
    val target: Page = target ?: throw NullPointerException("target")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Link) return false
        return source == other.source && target == other.target
    }

    override fun hashCode(): Int = 31 * source.hashCode() + target.hashCode()

    override fun toString(): String = "Link[${source.id} -> ${target.id}]"
}
