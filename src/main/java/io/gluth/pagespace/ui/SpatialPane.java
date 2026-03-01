package io.gluth.pagespace.ui;

import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.layout.ForceDirectedLayout;
import io.gluth.pagespace.layout.NodePosition;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class SpatialPane extends JPanel {

    private static final int    BASE_RADIUS  = 18;
    private static final int    CLICK_RADIUS = 28;
    private static final Color  NODE_COLOR   = new Color(70, 130, 200);
    private static final Color  CURR_COLOR   = new Color(230, 120, 30);
    private static final Color  EDGE_COLOR   = new Color(140, 160, 200);
    private static final Color  LABEL_COLOR  = new Color(220, 225, 245);
    private static final double MIN_ALPHA    = 0.20;

    private final ForceDirectedLayout layout;
    private Page currentPage;
    private NavigationListener navigationListener;

    public SpatialPane(ForceDirectedLayout layout) {
        this.layout = layout;
        setBackground(new Color(18, 20, 35));

        Timer timer = new Timer(30, e -> {
            layout.step();
            repaint();
        });
        timer.start();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (navigationListener == null) return;
                Page nearest = nearestPage(e.getX(), e.getY());
                if (nearest != null) navigationListener.navigateTo(nearest);
            }
        });
    }

    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }

    public void setCurrentPage(Page page) {
        this.currentPage = page;
        repaint();
    }

    // ------------------------------------------------------------------ paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Map<Page, NodePosition> positions = layout.positions();
        if (positions.isEmpty()) return;

        // Sort back-to-front (largest z first)
        List<Map.Entry<Page, NodePosition>> entries = new ArrayList<>(positions.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue().z(), a.getValue().z()));

        // Draw edges first (below nodes)
        g2.setStroke(new BasicStroke(1.0f));
        for (Map.Entry<Page, NodePosition> entry : entries) {
            Page src = entry.getKey();
            for (Page tgt : layout.graph().linksFrom(src)) {
                NodePosition tp = positions.get(tgt);
                if (tp == null) continue;
                double avgZ  = (entry.getValue().z() + tp.z()) / 2.0;
                float  alpha = (float) alphaFor(avgZ);
                int[]  sp    = project(entry.getValue());
                int[]  ep    = project(tp);
                g2.setColor(withAlpha(EDGE_COLOR, alpha * 0.55f));
                g2.drawLine(sp[0], sp[1], ep[0], ep[1]);
            }
        }

        // Draw nodes back-to-front
        Font baseFont = getFont();
        for (Map.Entry<Page, NodePosition> entry : entries) {
            Page         page = entry.getKey();
            NodePosition np   = entry.getValue();
            boolean      curr = page.equals(currentPage);

            double scale = perspScale(np.z());
            float  alpha = (float) alphaFor(np.z());
            int[]  sc    = project(np);
            int    cx    = sc[0], cy = sc[1];
            int    r     = Math.max(4, (int) (BASE_RADIUS * scale));

            // node fill
            Color baseColor = curr ? CURR_COLOR : NODE_COLOR;
            g2.setColor(withAlpha(baseColor, alpha));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);

            // rim
            g2.setColor(withAlpha(Color.WHITE, alpha * 0.5f));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // label
            float fontSize = Math.max(8f, (float) (12.0 * scale));
            g2.setFont(baseFont.deriveFont(curr ? Font.BOLD : Font.PLAIN, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            String label = page.title();
            int lw = fm.stringWidth(label);
            g2.setColor(withAlpha(LABEL_COLOR, alpha));
            g2.drawString(label, cx - lw / 2, cy + r + fm.getAscent() + 1);
        }
    }

    // -------------------------------------------------------------- hit-test

    private Page nearestPage(int mx, int my) {
        Map<Page, NodePosition> positions = layout.positions();
        if (positions.isEmpty()) return null;
        Page   nearest = null;
        double minDist = CLICK_RADIUS;
        for (Map.Entry<Page, NodePosition> entry : positions.entrySet()) {
            int[] sc   = project(entry.getValue());
            double dist = Math.hypot(mx - sc[0], my - sc[1]);
            double r   = BASE_RADIUS * perspScale(entry.getValue().z());
            if (dist < Math.max(r, CLICK_RADIUS / 2.0) && dist < minDist) {
                minDist = dist;
                nearest = entry.getKey();
            }
        }
        // fallback: within absolute CLICK_RADIUS
        if (nearest == null) {
            for (Map.Entry<Page, NodePosition> entry : positions.entrySet()) {
                int[] sc   = project(entry.getValue());
                double dist = Math.hypot(mx - sc[0], my - sc[1]);
                if (dist < CLICK_RADIUS && dist < minDist) {
                    minDist = dist;
                    nearest = entry.getKey();
                }
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------ projection

    /**
     * Simple perspective projection: layout space [0,W]×[0,H]×[0,D]
     * centred on screen. focalLength in layout units = depth.
     * z=0 → front (scale 1), z=depth → back (scale ~0.5).
     */
    private int[] project(NodePosition np) {
        int    pw  = getWidth();
        int    ph  = getHeight();
        double cx  = pw / 2.0;
        double cy  = ph / 2.0;
        // map world x,y to pixel space, then offset from centre
        double bx  = np.x() * pw / layout.width();
        double by  = np.y() * ph / layout.height();
        double s   = perspScale(np.z());
        int    sx  = (int) (cx + (bx - cx) * s);
        int    sy  = (int) (cy + (by - cy) * s);
        return new int[]{sx, sy};
    }

    private double perspScale(double wz) {
        double focal = layout.depth();
        return focal / (focal + wz);
    }

    private double alphaFor(double wz) {
        double d = layout.depth();
        if (d <= 0) return 1.0;
        return 1.0 - (wz / d) * (1.0 - MIN_ALPHA);
    }

    private static Color withAlpha(Color c, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (a * 255));
    }
}
