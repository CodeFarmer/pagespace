package io.gluth.pagespace.domain

class Page(id: String?, title: String?) {

    val id: String = id ?: throw NullPointerException("id")
    val title: String = title ?: throw NullPointerException("title")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Page) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Page[$id, $title]"
}
