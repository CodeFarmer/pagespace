@file:JvmName("PageSpaceApp")

package io.gluth.pagespace

import io.gluth.pagespace.backend.PageNotFoundException
import io.gluth.pagespace.backend.WikipediaContentBackend
import io.gluth.pagespace.domain.PageGraph
import io.gluth.pagespace.layout.ForceDirectedLayout
import io.gluth.pagespace.presenter.NavigationPresenter
import io.gluth.pagespace.ui.MainWindow
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        val backend = WikipediaContentBackend(defaultHttpGet())
        val graph   = PageGraph()
        val layout  = ForceDirectedLayout(graph, 760.0, 660.0)

        val presenter = NavigationPresenter(graph, layout, backend)
        val window = MainWindow(presenter)
        window.isVisible = true

        val defaultPage = backend.defaultPage()
        window.navigateTo(defaultPage)
    }
}

private fun defaultHttpGet(): (String) -> String {
    val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    val userAgent = "page-space/0.1 (https://github.com/CodeFarmer/page-space; educational)"

    return { url ->
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 404) throw PageNotFoundException(url)
        if (resp.statusCode() != 200)
            throw PageNotFoundException("HTTP ${resp.statusCode()} for $url")
        resp.body()
    }
}
