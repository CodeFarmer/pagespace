package io.gluth.pagespace.android

import io.gluth.pagespace.backend.PageNotFoundException
import java.net.HttpURLConnection
import java.net.URL

fun androidHttpGet(): (String) -> String {
    val userAgent = "page-space/0.1 (https://github.com/CodeFarmer/page-space; educational)"

    return { url ->
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Connection", "close")
            conn.requestMethod = "GET"

            val code = conn.responseCode
            if (code == 404) throw PageNotFoundException(url)
            if (code != 200) throw PageNotFoundException("HTTP $code for $url")

            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
