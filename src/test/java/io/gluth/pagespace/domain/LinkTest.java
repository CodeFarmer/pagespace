package io.gluth.pagespace.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkTest {

    private final Page physics = new Page("physics", "Physics");
    private final Page math    = new Page("math", "Mathematics");

    @Test
    void storesSourceAndTarget() {
        Link link = new Link(physics, math);
        assertEquals(physics, link.source());
        assertEquals(math, link.target());
    }

    @Test
    void equalByEndpoints() {
        Link a = new Link(physics, math);
        Link b = new Link(physics, math);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualForDifferentEndpoints() {
        Link a = new Link(physics, math);
        Link b = new Link(math, physics);
        assertNotEquals(a, b);
    }

    @Test
    void nullSourceThrows() {
        assertThrows(NullPointerException.class, () -> new Link(null, math));
    }

    @Test
    void nullTargetThrows() {
        assertThrows(NullPointerException.class, () -> new Link(physics, null));
    }
}
