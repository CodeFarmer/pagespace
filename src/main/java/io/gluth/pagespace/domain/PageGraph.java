package io.gluth.pagespace.domain;

import java.util.*;

public class PageGraph {

    // out-edges: page -> set of target pages
    private final Map<Page, Set<Page>> outEdges = new LinkedHashMap<>();
    // in-edges: page -> set of source pages
    private final Map<Page, Set<Page>> inEdges  = new LinkedHashMap<>();

    public void addPage(Page page) {
        outEdges.putIfAbsent(page, new LinkedHashSet<>());
        inEdges.putIfAbsent(page, new LinkedHashSet<>());
    }

    public void addLink(Link link) {
        addPage(link.source());
        addPage(link.target());
        outEdges.get(link.source()).add(link.target());
        inEdges.get(link.target()).add(link.source());
    }

    public void removePage(Page page) {
        // remove outbound links
        Set<Page> targets = outEdges.remove(page);
        if (targets != null) {
            for (Page t : targets) {
                inEdges.getOrDefault(t, Collections.emptySet()).remove(page);
            }
        }
        // remove inbound links
        Set<Page> sources = inEdges.remove(page);
        if (sources != null) {
            for (Page s : sources) {
                outEdges.getOrDefault(s, Collections.emptySet()).remove(page);
            }
        }
    }

    public Set<Page> linksFrom(Page page) {
        return Collections.unmodifiableSet(outEdges.getOrDefault(page, Collections.emptySet()));
    }

    public Set<Page> linksTo(Page page) {
        return Collections.unmodifiableSet(inEdges.getOrDefault(page, Collections.emptySet()));
    }

    public Set<Page> pages() {
        return Collections.unmodifiableSet(outEdges.keySet());
    }

    public int pageCount() { return outEdges.size(); }

    public int linkCount() {
        return outEdges.values().stream().mapToInt(Set::size).sum();
    }

    /** Number of neighbours shared by a and b (union of out+in neighbours). */
    public int sharedLinkCount(Page a, Page b) {
        Set<Page> neighboursA = new HashSet<>();
        neighboursA.addAll(outEdges.getOrDefault(a, Collections.emptySet()));
        neighboursA.addAll(inEdges.getOrDefault(a, Collections.emptySet()));

        Set<Page> neighboursB = new HashSet<>();
        neighboursB.addAll(outEdges.getOrDefault(b, Collections.emptySet()));
        neighboursB.addAll(inEdges.getOrDefault(b, Collections.emptySet()));

        neighboursA.retainAll(neighboursB);
        return neighboursA.size();
    }
}
