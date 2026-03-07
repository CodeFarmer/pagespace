# page-space

A spatial browser for Wikipedia (and similar hypertext resources). Two panes work together:

- **Content pane** — standard article view with coloured hyperlinks
- **Spatial pane** — 3D representation where the current page title floats in the foreground and linked page titles are arranged around it in three dimensions, clustered by inter-node link density via a force-directed simulation

Runs on **desktop (Kotlin/Swing)** and **Android (Jetpack Compose)**.

## How it works

Navigating to a page fetches its links ranked by importance (lead-section links first, then remaining links). Those links are merged into a live graph and laid out by a Fruchterman-Reingold force simulation confined to a sphere. The simulation converges offline, then the spatial view smoothly animates to the new equilibrium. Link density (5–50 links, default 20) is adjustable at runtime.

## Architecture

```
pagespace-core  (pure Kotlin JVM — Gradle)
  domain/       Page, Link, PageGraph
  backend/      ContentBackend, WikipediaContentBackend, MockContentBackend
  layout/       ForceDirectedLayout, NodePosition
  presenter/    NavigationPresenter, SpatialMath, NavigationHistory

desktop  (Kotlin/Swing — Maven)
  ui/           MainWindow, ContentPane, SpatialPane
  PageSpaceApp.kt

pagespace-android  (Jetpack Compose — Gradle)
  MainActivity.kt, PageSpaceViewModel.kt
  PageSpaceScreen.kt, SpatialCanvas.kt, ContentView.kt, SearchBar.kt
```

## Build & run — desktop

Requires Java 21 and Maven.

```bash
# Run tests
mvn test

# Build and run
mvn package
java -jar target/page-space-0.1.0-SNAPSHOT.jar
```

## Build & install — Android

Requires the Android SDK (API 26+) and a connected device or emulator.

```bash
# Build a debug APK
gradle :pagespace-android:assembleDebug

# Build and install directly to a connected device/emulator
gradle :pagespace-android:installDebug
```

The APK is written to `pagespace-android/build/outputs/apk/debug/`.

## Core module (shared library)

```bash
gradle :pagespace-core:build   # compile + test
```
