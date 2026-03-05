package io.gluth.pagespace.android

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val BASE_URL = "https://pagespace.local/"

private val CSS = """
    body {
        font-family: -apple-system, sans-serif;
        font-size: 16px;
        line-height: 1.5;
        color: #DCE1F5;
        background-color: #1A1C30;
        padding: 12px 16px;
        margin: 0;
    }
    a { color: #4682C8; text-decoration: none; }
    a:active { color: #E6781E; }
    hr { border: none; border-top: 1px solid #2A2D45; margin: 16px 0; }
    b { color: #E6781E; }
""".trimIndent()

private val HTML_TEMPLATE = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>$CSS</style>
    </head>
    <body>%s</body>
    </html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContentView(
    htmlBody: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF1A1C30.toInt())
                settings.javaScriptEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url
                        if (url.host == "pagespace.local") {
                            val pageId = url.lastPathSegment ?: return true
                            onNavigate(pageId)
                            return true
                        }
                        return true  // Block all external navigation
                    }
                }
            }
        },
        update = { webView ->
            val html = HTML_TEMPLATE.format(htmlBody)
            webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
        }
    )
}
