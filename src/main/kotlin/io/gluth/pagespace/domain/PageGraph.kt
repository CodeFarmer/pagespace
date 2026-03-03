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

    fun pagesWithinDistance(start: Page, maxDistance: Int): Set<Page> {
        val visited = LinkedHashSet<Page>()
        val queue = ArrayDeque<Pair<Page, Int>>()
        if (start !in outEdges) return visited
        visited.add(start)
        queue.add(start to 0)
        while (queue.isNotEmpty()) {
            val (current, dist) = queue.removeFirst()
            if (dist >= maxDistance) continue
            val neighbors = LinkedHashSet<Page>()
            outEdges[current]?.let { neighbors.addAll(it) }
            inEdges[current]?.let { neighbors.addAll(it) }
            for (n in neighbors) {
                if (n !in visited) {
                    visited.add(n)
                    queue.add(n to dist + 1)
                }
            }
        }
        return visited
    }

    fun pruneDistant(keep: Page, maxDistance: Int) {
        val reachable = pagesWithinDistance(keep, maxDistance)
        val toRemove = pages().filter { it !in reachable }
        for (p in toRemove) removePage(p)
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
