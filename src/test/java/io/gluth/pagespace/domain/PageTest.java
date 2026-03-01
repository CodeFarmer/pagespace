package io.gluth.pagespace.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void storesIdAndTitle() {
        Page page = new Page("id1", "Title One");
        assertEquals("id1", page.id());
        assertEquals("Title One", page.title());
    }

    @Test
    void equalById() {
        Page a = new Page("same", "Alpha");
        Page b = new Page("same", "Beta");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualForDifferentIds() {
        Page a = new Page("id1", "Alpha");
        Page b = new Page("id2", "Alpha");
        assertNotEquals(a, b);
    }

    @Test
    void nullIdThrows() {
        assertThrows(NullPointerException.class, () -> new Page(null, "Title"));
    }

    @Test
    void nullTitleThrows() {
        assertThrows(NullPointerException.class, () -> new Page("id", null));
    }
}
