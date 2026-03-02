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
import javax.swing.JSplitPane
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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

        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            window.navigateTo(physics)
            SwingUtilities.invokeLater { latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(200)

        assertEquals("Physics", window.contentPane().currentPageTitle())
    }
}
