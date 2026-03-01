package io.gluth.pagespace;

import io.gluth.pagespace.backend.ContentBackend;
import io.gluth.pagespace.backend.WikipediaContentBackend;
import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;
import io.gluth.pagespace.layout.ForceDirectedLayout;
import io.gluth.pagespace.ui.MainWindow;

import javax.swing.*;

public class PageSpaceApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ContentBackend backend = new WikipediaContentBackend();
            PageGraph graph = new PageGraph();
            ForceDirectedLayout layout = new ForceDirectedLayout(graph, 760, 660);

            MainWindow window = new MainWindow(backend, graph, layout);
            window.setVisible(true);

            // Navigate to default page on startup
            Page defaultPage = backend.defaultPage();
            window.navigateTo(defaultPage);
        });
    }
}
