package io.gluth.pagespace.domain;

import java.util.Objects;

public final class Page {

    private final String id;
    private final String title;

    public Page(String id, String title) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
    }

    public String id() { return id; }
    public String title() { return title; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Page other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "Page[" + id + ", " + title + "]"; }
}
