package io.gluth.pagespace.domain

class PageGraph {

    private val outEdges: MutableMap<Page, MutableSet<Page>> = LinkedHashMap()
    private val inEdges:  MutableMap<Page, MutableSet<Page>> = LinkedHashMap()
    private var sharedLinkCache: MutableMap<Pair<Page, Page>, Int>? = null

    fun addPage(page: Page) {
        outEdges.getOrPut(page) { LinkedHashSet() }
        inEdges.getOrPut(page)  { LinkedHashSet() }
    }

    fun addLink(link: Link) {
        addPage(link.source)
        addPage(link.target)
        val added = outEdges[link.source]!!.add(link.target)
        inEdges[link.target]!!.add(link.source)
        if (added) sharedLinkCache = null
    }

    fun removeLink(link: Link) {
        val removed = outEdges[link.source]?.remove(link.target) ?: false
        inEdges[link.target]?.remove(link.source)
        if (removed) sharedLinkCache = null
    }

    fun removePage(page: Page) {
        val targets = outEdges.remove(page)
        targets?.forEach { t -> inEdges.getOrDefault(t, mutableSetOf()).remove(page) }
        val sources = inEdges.remove(page)
        sources?.forEach { s -> outEdges.getOrDefault(s, mutableSetOf()).remove(page) }
        sharedLinkCache = null
    }

    fun linksFrom(page: Page): Set<Page> = (outEdges[page] ?: emptySet()).toSet()
    fun linksTo(page: Page):   Set<Page> = (inEdges[page]  ?: emptySet()).toSet()
    fun pages():               Set<Page> = outEdges.keys.toSet()

    fun pageCount(): Int = outEdges.size
    fun linkCount(): Int = outEdges.values.sumOf { it.size }

    fun sharedLinkCount(a: Page, b: Page): Int {
        val cache = sharedLinkCache ?: buildSharedLinkCache().also { sharedLinkCache = it }
        return cache[Pair(a, b)] ?: cache[Pair(b, a)] ?: 0
    }

    private fun buildSharedLinkCache(): MutableMap<Pair<Page, Page>, Int> {
        val cache = HashMap<Pair<Page, Page>, Int>()
        val pageList = outEdges.keys.toList()
        val neighbours = HashMap<Page, Set<Page>>(pageList.size)
        for (p in pageList) {
            val nb = HashSet<Page>()
            outEdges[p]?.let { nb.addAll(it) }
            inEdges[p]?.let { nb.addAll(it) }
            neighbours[p] = nb
        }
        for (i in pageList.indices) {
            val a = pageList[i]
            val nbA = neighbours[a] ?: continue
            for (j in i + 1 until pageList.size) {
                val b = pageList[j]
                val nbB = neighbours[b] ?: continue
                val smaller = if (nbA.size <= nbB.size) nbA else nbB
                val larger  = if (nbA.size <= nbB.size) nbB else nbA
                val count = smaller.count { it in larger }
                if (count > 0) cache[Pair(a, b)] = count
            }
        }
        return cache
    }
}
