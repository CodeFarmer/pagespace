# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**page-space** is a spatial browser for hypertext (Wikipedia or similar resources). It presents two panes:

1. **Content pane**: Standard hypertext viewer with coloured hyperlinks
2. **Spatial pane**: 3D spatial representation where the current page title is in the foreground and linked page titles are arranged around it in three dimensions. Pages are positioned by a force simulation based on inter-node link density — pages that are heavily cross-linked cluster closer together.

## Architecture

The design is intentionally target-agnostic: a core spatial navigation engine supports multiple content backends. The **reference implementation is Kotlin/Swing**.

```
src/main/kotlin/io/gluth/pagespace/
├── domain/          Page, Link, PageGraph
├── backend/         ContentBackend (interface), PageNotFoundException, MockContentBackend
├── layout/          NodePosition, ForceDirectedLayout
├── ui/              NavigationListener, ContentPane, SpatialPane, MainWindow
└── PageSpaceApp.kt
```

## Key Decisions

### Package
`io.gluth.pagespace` — groupId `io.gluth`, artifactId `page-space`.

### Domain model
- `Page` — value object, equality by `id` only (title can differ across sources)
- `Link` — directed edge; equality by both endpoints
- `PageGraph` — mutable aggregate using `LinkedHashMap<Page, Set<Page>>` for out- and in-edges; `sharedLinkCount` counts common neighbours (union of in+out)

### Backend interface
`ContentBackend` is the sole extension point for content sources.  `MockContentBackend` provides a hardcoded 16-page corpus (physics, math, quantum, …).  A Wikipedia backend can be wired in `PageSpaceApp` with zero engine changes.

### 3D layout — `ForceDirectedLayout`
- Fruchterman-Reingold: repulsion between all pairs + attraction along edges weighted by `(1 + sharedLinkCount)`
- Forces and velocities are 3-dimensional; depth = `min(width, height)`
- Velocity + damping (`BASE_DAMPING = 0.75`) replaces temperature cooling
- **Quadratic boundary repulsion** (`BOUNDARY_STRENGTH = 50`, margin = 12 % of each dimension) prevents nodes from pinning to canvas edges
- **Adaptive settling**: per-node still-counter driven by velocity-reversal detection. Each direction reversal applies `REVERSAL_DAMP = 0.45×` and adds `REVERSAL_CREDIT = 8` to the counter; slow motion increments it by 1; fast motion without reversal decrements it by `FAST_DECAY = 2`. When the counter reaches `SETTLE_STEPS = 40` the velocity is zeroed and force updates are skipped entirely — frozen nodes cannot be re-woken by residual forces.
- **Focus transition**: `setPinnedPage(page)` records the node's current position as `transStartX/Y/Z` and resets `transStep = 0`. Each `step()` call advances the counter and smoothstep-interpolates the node toward `(width/2, height/2, 0)` over `TRANSITION_STEPS = 35` frames. Navigation calls `wakeAll()`, resetting all still-counters and zeroing velocities so the network re-settles around the moving focus.
- New nodes spawned by `syncWithGraph()` are placed near the centroid of their already-positioned neighbours (with jitter), falling back to a random interior position.

### 3D rendering — `SpatialPane`
- **Perspective projection**: focalLength = `depth`; `scale = depth / (depth + wz)`. The pinned node at `z = 0` projects to exact screen centre at full size.
- **Depth dimming**: `alpha = 1.0 − (wz / depth) × 0.8`; front nodes are full-brightness, rear nodes fade to 20 %.
- **Back-to-front paint order**: entries sorted by `z` descending before drawing, so near nodes occlude far ones.
- Node radius and label font size scale with the perspective factor.
- 30 ms Swing `Timer` calls `layout.step()` then `repaint()`.

### Navigation flow (`MainWindow`)
1. `backend.fetchLinks(id)` → merge into `PageGraph`
2. `layout.syncWithGraph()` — spawn positions for new nodes
3. `layout.setPinnedPage(page)` — start transition animation
4. `backend.fetchBody(id)` → push to `ContentPane`
5. `spatialPane.setCurrentPage(page)` — highlight current node

All steps run on the EDT.

## Development Workflow

TDD: write a failing test first, then implement the minimum to pass, then refactor. Commit messages: no co-authoring stanzas.

## Build & Run

```bash
mvn test                          # run all tests (49 as of Kotlin port)
mvn test -Dtest=ForceDirectedLayoutTest
mvn package && java -jar target/page-space-0.1.0-SNAPSHOT.jar
```

## TODO

- **Spherical layout volume** (`ForceDirectedLayout`): replace the axis-aligned box boundary repulsion with a radial boundary centred on `(width/2, height/2, depth/2)` with radius `min(width, height) / 2`, so the node cloud forms a sphere rather than a box.
- **Graph pruning on navigation** (`MainWindow`): when navigating to a new page, remove nodes that have no path of length ≤ N to the current page, so the graph doesn't grow unboundedly during a long browsing session.
- **Node search / jump bar** (`MainWindow` / `SpatialPane`): add a `JTextField` above the spatial pane that filters visible node labels as the user types and navigates to the matching page on Enter.
- **Package as an Android app**
- **Related nodes together**: Load second-order nodes and arrange them spatially closer if they also have links between them

## Extension Points

- **Wikipedia backend**: implement `ContentBackend` → wire in `PageSpaceApp`. Zero engine changes.
- **3D upgrade**: `NodePosition` already has `x/y/z`; swap `SpatialPane` renderer to JOGL/JavaFX 3D. Zero domain/layout changes.
- **Thread safety**: `layout.step()` and graph mutation both run on EDT — no sync needed now. If `step()` moves to a background thread, wrap graph accesses.
