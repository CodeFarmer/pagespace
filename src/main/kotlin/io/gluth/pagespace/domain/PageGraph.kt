package io.gluth.pagespace.domain

class PageGraph {

    private val outEdges: MutableMap<Page, MutableSet<Page>> = LinkedHashMap()
    private val inEdges:  MutableMap<Page, MutableSet<Page>> = LinkedHashMap()

    fun addPage(page: Page) {
        outEdges.getOrPut(page) { LinkedHashSet() }
        inEdges.getOrPut(page)  { LinkedHashSet() }
    }

    fun addLink(link: Link) {
        addPage(link.source)
        addPage(link.target)
        outEdges[link.source]!!.add(link.target)
        inEdges[link.target]!!.add(link.source)
    }

    fun removePage(page: Page) {
        val targets = outEdges.remove(page)
        targets?.forEach { t -> inEdges.getOrDefault(t, mutableSetOf()).remove(page) }
        val sources = inEdges.remove(page)
        sources?.forEach { s -> outEdges.getOrDefault(s, mutableSetOf()).remove(page) }
    }

    fun linksFrom(page: Page): Set<Page> = (outEdges[page] ?: emptySet()).toSet()
    fun linksTo(page: Page):   Set<Page> = (inEdges[page]  ?: emptySet()).toSet()
    fun pages():               Set<Page> = outEdges.keys.toSet()

    fun pageCount(): Int = outEdges.size
    fun linkCount(): Int = outEdges.values.sumOf { it.size }

    fun sharedLinkCount(a: Page, b: Page): Int {
        val neighboursA = mutableSetOf<Page>()
        neighboursA.addAll(outEdges.getOrDefault(a, emptySet()))
        neighboursA.addAll(inEdges.getOrDefault(a, emptySet()))

        val neighboursB = mutableSetOf<Page>()
        neighboursB.addAll(outEdges.getOrDefault(b, emptySet()))
        neighboursB.addAll(inEdges.getOrDefault(b, emptySet()))

        neighboursA.retainAll(neighboursB)
        return neighboursA.size
    }
}
