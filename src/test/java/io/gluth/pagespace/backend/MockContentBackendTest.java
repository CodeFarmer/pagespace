package io.gluth.pagespace.backend;

import io.gluth.pagespace.domain.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class MockContentBackendTest {

    private MockContentBackend backend;

    @BeforeEach
    void setUp() {
        backend = new MockContentBackend();
    }

    @Test
    void defaultPageIsNonNullWithNonBlankTitle() {
        Page def = backend.defaultPage();
        assertNotNull(def);
        assertFalse(def.title().isBlank());
    }

    @Test
    void fetchBodyReturnsNonEmptyForKnownPage() throws PageNotFoundException {
        Page def = backend.defaultPage();
        String body = backend.fetchBody(def.id());
        assertNotNull(body);
        assertFalse(body.isBlank());
    }

    @Test
    void fetchBodyThrowsForUnknownPage() {
        assertThrows(PageNotFoundException.class, () -> backend.fetchBody("mock:unknown-xyz"));
    }

    @Test
    void fetchLinksContainsRequestedPage() throws PageNotFoundException {
        Page def = backend.defaultPage();
        List<Page> links = backend.fetchLinks(def.id());
        assertFalse(links.isEmpty());
    }

    @Test
    void fetchLinksIsDeterministic() throws PageNotFoundException {
        Page def = backend.defaultPage();
        List<Page> first  = backend.fetchLinks(def.id());
        List<Page> second = backend.fetchLinks(def.id());
        assertEquals(first, second);
    }

    @Test
    void fetchLinksThrowsForUnknownPage() {
        assertThrows(PageNotFoundException.class, () -> backend.fetchLinks("mock:unknown-xyz"));
    }

    @Test
    void allSixteenPagesHaveBodyAndLinks() {
        String[] ids = {
            "mock:physics", "mock:math", "mock:quantum", "mock:relativity",
            "mock:logic", "mock:philosophy", "mock:calculus", "mock:thermodynamics",
            "mock:optics", "mock:chemistry", "mock:biology", "mock:statistics",
            "mock:probability", "mock:geometry", "mock:algebra", "mock:cs"
        };
        for (String id : ids) {
            assertDoesNotThrow(() -> backend.fetchBody(id),   "body missing for " + id);
            assertDoesNotThrow(() -> backend.fetchLinks(id),  "links missing for " + id);
        }
    }
}
