package io.gluth.pagespace.backend

import io.gluth.pagespace.domain.Page

interface ContentBackend {
    fun defaultPage(): Page
    fun fetchBody(id: String): String
    fun fetchLinks(id: String): List<Page>
    fun searchPages(query: String): List<Page>
}
