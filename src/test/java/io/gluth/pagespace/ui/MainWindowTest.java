package io.gluth.pagespace.ui;

import io.gluth.pagespace.backend.MockContentBackend;
import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;
import io.gluth.pagespace.layout.ForceDirectedLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisabledIf("io.gluth.pagespace.ui.MainWindowTest#isHeadless")
class MainWindowTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    private MainWindow createWindow() throws Exception {
        MockContentBackend backend = new MockContentBackend();
        PageGraph graph = new PageGraph();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph, 800, 600, 42L);

        AtomicReference<MainWindow> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            ref.set(new MainWindow(backend, graph, layout));
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);
        return ref.get();
    }

    @Test
    void splitPaneExists() throws Exception {
        MainWindow window = createWindow();
        assertNotNull(window.splitPane());
        assertInstanceOf(JSplitPane.class, window.splitPane());
    }

    @Test
    void navigateToUpdatesContentPaneTitle() throws Exception {
        MainWindow window = createWindow();
        Page physics = new Page("mock:physics", "Physics");

        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            window.navigateTo(physics);
            // navigateTo itself invokes later, so we schedule check after
            SwingUtilities.invokeLater(latch::countDown);
        });
        // give EDT time to process
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        assertEquals("Physics", window.contentPane().currentPageTitle());
    }
}
