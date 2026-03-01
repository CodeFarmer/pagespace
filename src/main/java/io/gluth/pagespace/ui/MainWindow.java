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
import java.util.concurrent.atomic.AtomicInteger;

public class MainWindow extends JFrame implements NavigationListener {

    private final ContentBackend backend;
    private final PageGraph graph;
    private final ForceDirectedLayout layout;
    private final ContentPane contentPane;
    private final SpatialPane spatialPane;
    private final Deque<Page> history = new ArrayDeque<>();
    /** Monotonically incremented on every navigation; workers check before committing results. */
    private final AtomicInteger navGeneration = new AtomicInteger(0);

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
        int myGen = navGeneration.incrementAndGet();

        // Show loading state immediately on EDT
        contentPane.setLoading(page.title());

        SwingWorker<NavResult, Void> worker = new SwingWorker<>() {
            @Override
            protected NavResult doInBackground() {
                try {
                    List<Page> linkedPages = backend.fetchLinks(page.id());
                    String body = backend.fetchBody(page.id());
                    return new NavResult(page, linkedPages, body, null);
                } catch (PageNotFoundException e) {
                    return new NavResult(page, List.of(), "", e.getMessage());
                }
            }

            @Override
            protected void done() {
                // Discard if a newer navigation has started
                if (navGeneration.get() != myGen) return;

                NavResult result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = new NavResult(page, List.of(), "", e.getMessage());
                }

                if (result.error != null) {
                    JOptionPane.showMessageDialog(MainWindow.this,
                        "Page not found: " + page.id(), "Navigation Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                for (Page linked : result.linkedPages) {
                    graph.addLink(new Link(page, linked));
                }
                layout.syncWithGraph();
                layout.setPinnedPage(page);

                history.push(page);
                contentPane.setContent(page, result.body);
                spatialPane.setCurrentPage(page);
            }
        };

        worker.execute();
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

    private record NavResult(Page page, List<Page> linkedPages, String body, String error) {}
}
