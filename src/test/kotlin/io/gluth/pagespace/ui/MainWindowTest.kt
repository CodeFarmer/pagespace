package io.gluth.pagespace.ui

import io.gluth.pagespace.backend.MockContentBackend
import io.gluth.pagespace.domain.Page
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import java.awt.GraphicsEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisabledIf("io.gluth.pagespace.ui.MainWindowTest#isHeadless")
class MainWindowTest {

    companion object {
        @JvmStatic
        fun isHeadless(): Boolean = GraphicsEnvironment.isHeadless()
    }

    private fun createWindow(): MainWindow {
        val backend = MockContentBackend()
        val graph   = PageGraph()
        val layout  = ForceDirectedLayout(graph, 800.0, 600.0, 42L)

        val ref   = AtomicReference<MainWindow>()
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            ref.set(MainWindow(backend, graph, layout))
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return ref.get()
    }

    private fun navigateAndWait(window: MainWindow, page: Page) {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            window.navigateTo(page)
            // Schedule countdown after current EDT events complete
            SwingUtilities.invokeLater { latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        // Allow SwingWorker to complete
        Thread.sleep(300)
    }

    @Test
    fun splitPaneExists() {
        val window = createWindow()
        assertNotNull(window.splitPane())
        assertIs<JSplitPane>(window.splitPane())
    }

    @Test
    fun navigateToUpdatesContentPaneTitle() {
        val window  = createWindow()
        val physics = Page("mock:physics", "Physics")

        navigateAndWait(window, physics)

        assertEquals("Physics", window.contentPane().currentPageTitle())
    }

    @Test
    fun historyGrowsOnNavigation() {
        val window  = createWindow()
        val physics = Page("mock:physics", "Physics")
        val math    = Page("mock:math", "Mathematics")

        navigateAndWait(window, physics)
        navigateAndWait(window, math)

        val pages = window.historyPages()
        assertEquals(2, pages.size)
        assertEquals(physics, pages[0])
        assertEquals(math, pages[1])
        assertEquals(1, window.historyIndex())
    }

    @Test
    fun navigateBackDoesNotLoseHistoryEntries() {
        val window  = createWindow()
        val physics = Page("mock:physics", "Physics")
        val math    = Page("mock:math", "Mathematics")
        val quantum = Page("mock:quantum", "Quantum Mechanics")

        navigateAndWait(window, physics)
        navigateAndWait(window, math)
        navigateAndWait(window, quantum)

        assertEquals(3, window.historyPages().size)
        assertEquals(2, window.historyIndex())

        // Simulate back navigation via reflection (navigateBack is private)
        val backMethod = MainWindow::class.java.getDeclaredMethod("navigateBack")
        backMethod.isAccessible = true

        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            backMethod.invoke(window)
            SwingUtilities.invokeLater { latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(300)

        // History should still have 3 entries, but index should be 1 (pointing to math)
        assertEquals(3, window.historyPages().size)
        assertEquals(1, window.historyIndex())
    }

    @Test
    fun pruningRemovesDistantNodesAfterNavigation() {
        val window = createWindow()
        val graph = window.graph()
        val physics = Page("mock:physics", "Physics")
        val philosophy = Page("mock:philosophy", "Philosophy")

        navigateAndWait(window, physics)
        // After navigating to physics, graph contains physics and its neighbors
        assertTrue(graph.pages().contains(physics))

        navigateAndWait(window, philosophy)
        // Philosophy links to logic only; physics is more than 2 hops away
        // so pruning should remove it
        val pages = graph.pages()
        assertFalse(pages.contains(physics), "Physics should be pruned after navigating to Philosophy")
        assertTrue(pages.contains(philosophy))
        assertTrue(pages.contains(Page("mock:logic", "Logic")))
    }

    @Test
    fun searchFieldExistsInHierarchy() {
        val window = createWindow()
        val field = window.searchField()
        assertNotNull(field)
        assertIs<JTextField>(field)

        // Verify it's in the component hierarchy: spatialWrapper is the right side of the split
        val splitPane = window.splitPane()
        val spatialWrapper = splitPane.rightComponent as JPanel
        val searchField = spatialWrapper.getComponent(0) // NORTH is first in BorderLayout
        assertEquals(field, searchField)
    }

    @Test
    fun seeAlsoEscapesHtmlEntities() {
        // Test through buildSeeAlso via reflection
        val window = createWindow()
        val buildMethod = MainWindow::class.java.getDeclaredMethod(
            "buildSeeAlso", String::class.java, List::class.java
        )
        buildMethod.isAccessible = true

        val links = listOf(
            Page("AT&T", "AT&T"),
            Page("A<B", "A<B"),
            Page("Gödel's theorem", "Gödel's theorem")
        )
        val result = buildMethod.invoke(window, "<p>body</p>", links) as String

        assertTrue(result.contains("AT&amp;T"))
        assertTrue(result.contains("A&lt;B"))
        assertTrue(result.contains("G\u00f6del&#39;s theorem"))
        // Should not contain unescaped special chars in href or text
        assertTrue(!result.contains("href=\"AT&T\""))
        assertTrue(!result.contains(">AT&T<"))
    }
}
