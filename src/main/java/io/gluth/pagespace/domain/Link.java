package io.gluth.pagespace.domain;

import java.util.Objects;

public final class Link {

    private final Page source;
    private final Page target;

    public Link(Page source, Page target) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
    }

    public Page source() { return source; }
    public Page target() { return target; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link other)) return false;
        return source.equals(other.source) && target.equals(other.target);
    }

    @Override
    public int hashCode() { return Objects.hash(source, target); }

    @Override
    public String toString() { return "Link[" + source.id() + " -> " + target.id() + "]"; }
}
