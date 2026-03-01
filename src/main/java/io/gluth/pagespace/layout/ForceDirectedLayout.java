package io.gluth.pagespace.layout;

import io.gluth.pagespace.domain.Page;
import io.gluth.pagespace.domain.PageGraph;

import java.util.*;

/**
 * 3-D Fruchterman-Reingold force-directed layout with adaptive settling.
 *
 * Oscillation detector: when a node's velocity vector reverses direction
 * (dot product with the previous velocity is negative), a moderate extra-
 * damping impulse is applied and a still-counter accumulates.  Slow
 * residual motion increments the counter one step at a time; fast motion
 * without reversal decrements it slowly (so brief excursions don't reset
 * all progress).  When the counter reaches SETTLE_STEPS the node's velocity
 * is zeroed and subsequent force updates are skipped — the node is frozen.
 *
 * Navigation events (setPinnedPage / syncWithGraph) reset all counters so
 * every node re-animates and re-settles around the new focus.
 */
public class ForceDirectedLayout {

    private static final double BASE_DAMPING     = 0.75;
    private static final double FORCE_SCALE      = 0.08;
    private static final double MAX_SPEED        = 60.0;
    /** Extra-damping multiplier applied on each velocity reversal. */
    private static final double REVERSAL_DAMP    = 0.45;
    /** Still-counter credits added per reversal. */
    private static final int    REVERSAL_CREDIT  = 8;
    /** Speed below which the still-counter increments each step. */
    private static final double SETTLE_THRESHOLD = 4.0;
    /** Counter decrement per step when moving fast without reversal. */
    private static final int    FAST_DECAY       = 2;
    /** Counter value at which the node is considered fully settled. */
    private static final int    SETTLE_STEPS     = 40;

    private static final double BOUNDARY_MARGIN   = 0.12;
    private static final double BOUNDARY_STRENGTH = 50.0;
    /** Steps over which the focus node travels from its old position to centre. */
    private static final int    TRANSITION_STEPS  = 35;

    private final PageGraph graph;
    private final double width;
    private final double height;
    private final double depth;
    private final Random random;

    private final Map<Page, NodePosition> positions   = new LinkedHashMap<>();
    private final Map<Page, double[]>     velocities  = new LinkedHashMap<>();
    private final Map<Page, Integer>      stillCounts = new LinkedHashMap<>();

    private Page   pinnedPage;
    private double transStartX, transStartY, transStartZ;
    private int    transStep = TRANSITION_STEPS; // starts "done" so first pinning transitions

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
                positions.put(page,    spawnPosition(page));
                velocities.put(page,   new double[3]);
                stillCounts.put(page,  0);
            }
        }
    }

    private NodePosition spawnPosition(Page page) {
        List<NodePosition> nb = new ArrayList<>();
        for (Page n : graph.linksFrom(page)) { NodePosition np = positions.get(n); if (np != null) nb.add(np); }
        for (Page n : graph.linksTo(page))   { NodePosition np = positions.get(n); if (np != null) nb.add(np); }
        if (!nb.isEmpty()) {
            double sx = 0, sy = 0, sz = 0;
            for (NodePosition np : nb) { sx += np.x(); sy += np.y(); sz += np.z(); }
            double cnt = nb.size(), j = Math.min(width, height) * 0.18;
            return new NodePosition(
                clamp(sx/cnt + (random.nextDouble()-0.5)*j,        0, width),
                clamp(sy/cnt + (random.nextDouble()-0.5)*j,        0, height),
                clamp(sz/cnt + (random.nextDouble()-0.5)*j*0.4,   0, depth));
        }
        double xm = width*BOUNDARY_MARGIN, ym = height*BOUNDARY_MARGIN, zm = depth*BOUNDARY_MARGIN;
        return new NodePosition(
            xm + random.nextDouble()*(width  - 2*xm),
            ym + random.nextDouble()*(height - 2*ym),
            zm + random.nextDouble()*(depth  - 2*zm));
    }

    public void setPinnedPage(Page page) {
        this.pinnedPage = page;
        NodePosition pos = positions.get(page);
        if (pos != null) {
            transStartX = pos.x();
            transStartY = pos.y();
            transStartZ = pos.z();
        } else {
            transStartX = width / 2.0;
            transStartY = height / 2.0;
            transStartZ = 0.0;
        }
        transStep = 0;
        wakeAll();
    }

    public void syncWithGraph() {
        boolean added = false;
        for (Page page : graph.pages()) {
            if (!positions.containsKey(page)) {
                positions.put(page,   spawnPosition(page));
                velocities.put(page,  new double[3]);
                stillCounts.put(page, 0);
                added = true;
            }
        }
        if (added) wakeAll();
    }

    /** Reset all still-counters so every node re-animates after a navigation event. */
    private void wakeAll() {
        for (Page p : stillCounts.keySet()) stillCounts.put(p, 0);
        for (double[] v : velocities.values()) { v[0] = 0; v[1] = 0; v[2] = 0; }
    }

    public void step() {
        List<Page> pageList = new ArrayList<>(positions.keySet());
        int n = pageList.size();
        if (n == 0) return;

        double k  = Math.cbrt((width * height * depth) / Math.max(n, 1));
        double xm = width*BOUNDARY_MARGIN, ym = height*BOUNDARY_MARGIN, zm = depth*BOUNDARY_MARGIN;

        Map<Page, double[]> forces = new HashMap<>();
        for (Page p : pageList) forces.put(p, new double[3]);

        // Repulsion between all pairs
        for (int i = 0; i < n; i++) {
            Page u = pageList.get(i); NodePosition pu = positions.get(u);
            for (int j = i + 1; j < n; j++) {
                Page v = pageList.get(j); NodePosition pv = positions.get(v);
                double dx=pu.x()-pv.x(), dy=pu.y()-pv.y(), dz=pu.z()-pv.z();
                double dist=Math.max(Math.sqrt(dx*dx+dy*dy+dz*dz),0.001), rep=(k*k)/dist;
                double[] fu=forces.get(u), fv=forces.get(v);
                fu[0]+=(dx/dist)*rep; fu[1]+=(dy/dist)*rep; fu[2]+=(dz/dist)*rep;
                fv[0]-=(dx/dist)*rep; fv[1]-=(dy/dist)*rep; fv[2]-=(dz/dist)*rep;
            }
        }

        // Attraction along edges weighted by shared-link count
        for (Page u : pageList) {
            for (Page v : graph.linksFrom(u)) {
                if (!positions.containsKey(v)) continue;
                NodePosition pu=positions.get(u), pv=positions.get(v);
                double dx=pu.x()-pv.x(), dy=pu.y()-pv.y(), dz=pu.z()-pv.z();
                double dist=Math.max(Math.sqrt(dx*dx+dy*dy+dz*dz),0.001);
                double att=(dist*dist)/k*(1.0+graph.sharedLinkCount(u,v));
                double[] fu=forces.get(u), fv=forces.get(v);
                fu[0]-=(dx/dist)*att; fu[1]-=(dy/dist)*att; fu[2]-=(dz/dist)*att;
                fv[0]+=(dx/dist)*att; fv[1]+=(dy/dist)*att; fv[2]+=(dz/dist)*att;
            }
        }

        // Quadratic boundary repulsion
        for (Page p : pageList) {
            if (p.equals(pinnedPage)) continue;
            NodePosition pos=positions.get(p); double[] f=forces.get(p);
            if (pos.x()<xm)          {double t=(xm-pos.x())/xm;          f[0]+=k*t*t*BOUNDARY_STRENGTH;}
            if (pos.x()>width-xm)    {double t=(pos.x()-(width-xm))/xm;  f[0]-=k*t*t*BOUNDARY_STRENGTH;}
            if (pos.y()<ym)          {double t=(ym-pos.y())/ym;          f[1]+=k*t*t*BOUNDARY_STRENGTH;}
            if (pos.y()>height-ym)   {double t=(pos.y()-(height-ym))/ym; f[1]-=k*t*t*BOUNDARY_STRENGTH;}
            if (pos.z()<zm)          {double t=(zm-pos.z())/zm;          f[2]+=k*t*t*BOUNDARY_STRENGTH;}
            if (pos.z()>depth-zm)    {double t=(pos.z()-(depth-zm))/zm;  f[2]-=k*t*t*BOUNDARY_STRENGTH;}
        }

        // Integrate: skip frozen nodes entirely so applied forces don't re-wake them.
        for (Page p : pageList) {
            if (p.equals(pinnedPage)) continue;

            int count = stillCounts.getOrDefault(p, 0);
            if (count >= SETTLE_STEPS) continue;  // frozen — don't touch velocity

            double[] f=forces.get(p), vel=velocities.get(p);
            NodePosition pos=positions.get(p);

            double pv0=vel[0], pv1=vel[1], pv2=vel[2];

            vel[0]=(vel[0]+f[0]*FORCE_SCALE)*BASE_DAMPING;
            vel[1]=(vel[1]+f[1]*FORCE_SCALE)*BASE_DAMPING;
            vel[2]=(vel[2]+f[2]*FORCE_SCALE)*BASE_DAMPING;

            double speed=Math.sqrt(vel[0]*vel[0]+vel[1]*vel[1]+vel[2]*vel[2]);
            if (speed>MAX_SPEED) {
                double s=MAX_SPEED/speed; vel[0]*=s; vel[1]*=s; vel[2]*=s; speed=MAX_SPEED;
            }

            double dot = pv0*vel[0]+pv1*vel[1]+pv2*vel[2];
            if (dot < 0) {
                // Velocity reversal — approaching equilibrium; apply extra damping
                vel[0]*=REVERSAL_DAMP; vel[1]*=REVERSAL_DAMP; vel[2]*=REVERSAL_DAMP;
                speed*=REVERSAL_DAMP;
                count = Math.min(count+REVERSAL_CREDIT, SETTLE_STEPS);
            } else if (speed < SETTLE_THRESHOLD) {
                count = Math.min(count+1, SETTLE_STEPS);
            } else {
                // Moving fast without reversal: decay counter slowly
                count = Math.max(0, count-FAST_DECAY);
            }
            stillCounts.put(p, count);

            if (count >= SETTLE_STEPS) {
                vel[0]=0; vel[1]=0; vel[2]=0;
            } else if (count > 0) {
                double suppress = 1.0-(double)count/SETTLE_STEPS;
                vel[0]*=suppress; vel[1]*=suppress; vel[2]*=suppress;
            }

            pos.setX(clamp(pos.x()+vel[0], 0, width));
            pos.setY(clamp(pos.y()+vel[1], 0, height));
            pos.setZ(clamp(pos.z()+vel[2], 0, depth));
        }

        // Animate pinned node toward centre using a smoothstep curve
        if (pinnedPage != null && positions.containsKey(pinnedPage)) {
            NodePosition pp = positions.get(pinnedPage);
            if (transStep < TRANSITION_STEPS) {
                transStep++;
                double t  = (double) transStep / TRANSITION_STEPS;
                double t2 = t * t * (3 - 2 * t);           // smoothstep
                pp.setX(transStartX + (width  / 2.0 - transStartX) * t2);
                pp.setY(transStartY + (height / 2.0 - transStartY) * t2);
                pp.setZ(transStartZ + (0.0          - transStartZ) * t2);
            } else {
                pp.setX(width / 2.0);
                pp.setY(height / 2.0);
                pp.setZ(0.0);
            }
        }
    }

    public Map<Page, NodePosition> positions()  { return Collections.unmodifiableMap(positions); }
    public PageGraph graph()       { return graph; }
    public double width()          { return width; }
    public double height()         { return height; }
    public double depth()          { return depth; }
    public int    transitionSteps(){ return TRANSITION_STEPS; }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
