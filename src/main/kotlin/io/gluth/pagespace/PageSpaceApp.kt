@file:JvmName("PageSpaceApp")

package io.gluth.pagespace

import io.gluth.pagespace.backend.WikipediaContentBackend
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        val backend = WikipediaContentBackend()
        val graph   = PageGraph()
        val layout  = ForceDirectedLayout(graph, 760.0, 660.0)

        val window = MainWindow(backend, graph, layout)
        window.isVisible = true

        val defaultPage = backend.defaultPage()
        window.navigateTo(defaultPage)
    }
}
