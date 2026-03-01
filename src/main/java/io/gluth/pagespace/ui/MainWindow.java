package io.gluth.pagespace.ui;

import io.gluth.pagespace.backend.ContentBackend;
import io.gluth.pagespace.backend.PageNotFoundException;
import io.gluth.pagespace.domain.Link;
import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;
import io.gluth.pagespace.layout.ForceDirectedLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class MainWindow extends JFrame implements NavigationListener {

    private final ContentBackend backend;
    private final PageGraph graph;
    private final ForceDirectedLayout layout;
    private final ContentPane contentPane;
    private final SpatialPane spatialPane;
    private final Deque<Page> history = new ArrayDeque<>();

    public MainWindow(ContentBackend backend, PageGraph graph, ForceDirectedLayout layout) {
        super("page-space");
        this.backend = backend;
        this.graph   = graph;
        this.layout  = layout;

        contentPane = new ContentPane();
        spatialPane = new SpatialPane(layout);

        contentPane.setNavigationListener(this);
        spatialPane.setNavigationListener(this);
        contentPane.setBackAction(this::navigateBack);

        JSplitPane splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, contentPane, spatialPane);
        splitPane.setResizeWeight(0.4);

        setContentPane(splitPane);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
    }

    @Override
    public void navigateTo(Page page) {
        SwingUtilities.invokeLater(() -> {
            try {
                List<Page> linkedPages = backend.fetchLinks(page.id());
                // If the page doesn't exist in our fetched pages, use the resolved one
                Page resolvedPage = linkedPages.isEmpty() ? page
                    : backend.defaultPage().id().equals(page.id()) ? backend.defaultPage() : page;

                // Try to get a proper page title by looking up in backend links
                Page titledPage = page;
                // Add linked pages to graph
                for (Page linked : linkedPages) {
                    graph.addLink(new Link(page, linked));
                    // Use the actual page with correct title
                    if (linked.id().equals(page.id())) {
                        titledPage = linked;
                    }
                }

                layout.syncWithGraph();
                layout.setPinnedPage(page);

                String body = backend.fetchBody(page.id());
                history.push(page);
                contentPane.setContent(page, body);
                spatialPane.setCurrentPage(page);

            } catch (PageNotFoundException e) {
                JOptionPane.showMessageDialog(this,
                    "Page not found: " + page.id(), "Navigation Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void navigateBack() {
        if (history.size() > 1) {
            history.pop(); // current
            Page previous = history.peek();
            if (previous != null) {
                history.pop(); // will be re-pushed by navigateTo
                navigateTo(previous);
            }
        }
    }

    public ContentPane contentPane()  { return contentPane; }
    public SpatialPane spatialPane()  { return spatialPane; }
    public JSplitPane  splitPane()    { return (JSplitPane) getContentPane(); }
}
