package io.gluth.pagespace.layout;

import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;

import java.util.*;

/**
 * 3-D Fruchterman-Reingold force-directed layout with velocity damping.
 *
 * Repulsion between every pair of nodes; attraction along edges,
 * scaled by (1 + sharedLinkCount) to pull heavily cross-linked pages closer.
 * Velocity is damped each step so the simulation reaches equilibrium quickly.
 * A single "pinned" page is held stationary at the centre (width/2, height/2, 0).
 */
public class ForceDirectedLayout {

    private static final double DAMPING      = 0.60;
    private static final double FORCE_SCALE  = 0.08;
    private static final double MAX_SPEED    = 60.0;  // world-units/step cap
    private static final double SLEEP_SPEED  = 0.05;  // zero velocity below this

    private final PageGraph graph;
    private final double width;
    private final double height;
    private final double depth;
    private final Random random;

    private final Map<Page, NodePosition> positions  = new LinkedHashMap<>();
    private final Map<Page, double[]>     velocities = new LinkedHashMap<>();

    private Page pinnedPage;

    public ForceDirectedLayout(PageGraph graph, double width, double height) {
        this(graph, width, height, System.nanoTime());
    }

    public ForceDirectedLayout(PageGraph graph, double width, double height, long seed) {
        this.graph  = graph;
        this.width  = width;
        this.height = height;
        this.depth  = Math.min(width, height);
        this.random = new Random(seed);
        initPositions();
    }

    private void initPositions() {
        for (Page page : graph.pages()) {
            if (!positions.containsKey(page)) {
                positions.put(page, randomPosition());
                velocities.put(page, new double[3]);
            }
        }
    }

    private NodePosition randomPosition() {
        return new NodePosition(
            random.nextDouble() * width,
            random.nextDouble() * height,
            random.nextDouble() * depth);
    }

    /** Mark a page as pinned; it will stay at (width/2, height/2, 0) on every step. */
    public void setPinnedPage(Page page) {
        this.pinnedPage = page;
        NodePosition pos = positions.get(page);
        if (pos != null) {
            pos.setX(width  / 2.0);
            pos.setY(height / 2.0);
            pos.setZ(0.0);
            velocities.put(page, new double[3]);
        }
    }

    /** Add positions for any pages added to the graph since last call. */
    public void syncWithGraph() {
        for (Page page : graph.pages()) {
            if (!positions.containsKey(page)) {
                positions.put(page, randomPosition());
                velocities.put(page, new double[3]);
            }
        }
    }

    /** Advance the simulation by one step. */
    public void step() {
        List<Page> pageList = new ArrayList<>(positions.keySet());
        int n = pageList.size();
        if (n == 0) return;

        double k = Math.cbrt((width * height * depth) / Math.max(n, 1));

        // net force accumulator [fx, fy, fz]
        Map<Page, double[]> forces = new HashMap<>();
        for (Page p : pageList) forces.put(p, new double[3]);

        // Repulsion between all pairs
        for (int i = 0; i < n; i++) {
            Page u = pageList.get(i);
            NodePosition pu = positions.get(u);
            for (int j = i + 1; j < n; j++) {
                Page v = pageList.get(j);
                NodePosition pv = positions.get(v);

                double dx = pu.x() - pv.x();
                double dy = pu.y() - pv.y();
                double dz = pu.z() - pv.z();
                double dist = Math.max(Math.sqrt(dx*dx + dy*dy + dz*dz), 0.001);
                double rep  = (k * k) / dist;

                double fx = (dx / dist) * rep;
                double fy = (dy / dist) * rep;
                double fz = (dz / dist) * rep;

                double[] fu = forces.get(u);
                double[] fv = forces.get(v);
                fu[0] += fx; fu[1] += fy; fu[2] += fz;
                fv[0] -= fx; fv[1] -= fy; fv[2] -= fz;
            }
        }

        // Attraction along edges, weighted by shared link count
        for (Page u : pageList) {
            for (Page v : graph.linksFrom(u)) {
                if (!positions.containsKey(v)) continue;
                NodePosition pu = positions.get(u);
                NodePosition pv = positions.get(v);

                double dx = pu.x() - pv.x();
                double dy = pu.y() - pv.y();
                double dz = pu.z() - pv.z();
                double dist   = Math.max(Math.sqrt(dx*dx + dy*dy + dz*dz), 0.001);
                double weight = 1.0 + graph.sharedLinkCount(u, v);
                double att    = (dist * dist) / k * weight;

                double fx = (dx / dist) * att;
                double fy = (dy / dist) * att;
                double fz = (dz / dist) * att;

                double[] fu = forces.get(u);
                double[] fv = forces.get(v);
                fu[0] -= fx; fu[1] -= fy; fu[2] -= fz;
                fv[0] += fx; fv[1] += fy; fv[2] += fz;
            }
        }

        // Update velocities and positions
        for (Page p : pageList) {
            if (p.equals(pinnedPage)) continue;

            double[] f   = forces.get(p);
            double[] vel = velocities.get(p);
            NodePosition pos = positions.get(p);

            vel[0] = (vel[0] + f[0] * FORCE_SCALE) * DAMPING;
            vel[1] = (vel[1] + f[1] * FORCE_SCALE) * DAMPING;
            vel[2] = (vel[2] + f[2] * FORCE_SCALE) * DAMPING;

            // cap speed
            double speed = Math.sqrt(vel[0]*vel[0] + vel[1]*vel[1] + vel[2]*vel[2]);
            if (speed > MAX_SPEED) {
                double s = MAX_SPEED / speed;
                vel[0] *= s; vel[1] *= s; vel[2] *= s;
            } else if (speed < SLEEP_SPEED) {
                vel[0] = 0; vel[1] = 0; vel[2] = 0;
            }

            pos.setX(Math.max(0, Math.min(width,  pos.x() + vel[0])));
            pos.setY(Math.max(0, Math.min(height, pos.y() + vel[1])));
            pos.setZ(Math.max(0, Math.min(depth,  pos.z() + vel[2])));
        }

        // Enforce pinned position
        if (pinnedPage != null && positions.containsKey(pinnedPage)) {
            NodePosition pp = positions.get(pinnedPage);
            pp.setX(width  / 2.0);
            pp.setY(height / 2.0);
            pp.setZ(0.0);
        }
    }

    /** Unmodifiable view of current positions. */
    public Map<Page, NodePosition> positions() {
        return Collections.unmodifiableMap(positions);
    }

    public PageGraph graph()  { return graph; }
    public double width()     { return width; }
    public double height()    { return height; }
    public double depth()     { return depth; }
}
