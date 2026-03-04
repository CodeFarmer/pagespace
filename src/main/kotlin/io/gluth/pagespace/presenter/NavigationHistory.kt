package io.gluth.pagespace.presenter

import io.gluth.pagespace.domain.Page

class NavigationHistory {
    private val pages = mutableListOf<Page>()
    private var index = -1
    private var navigatingBack = false

    fun push(page: Page) {
        if (!navigatingBack) {
            while (pages.size > index + 1) pages.removeAt(pages.size - 1)
            pages.add(page)
            index = pages.size - 1
        }
        navigatingBack = false
    }

    fun back(): Page? {
        if (index <= 0) return null
        index--
        navigatingBack = true
        return pages[index]
    }

    fun canGoBack(): Boolean = index > 0
    fun isNavigatingBack(): Boolean = navigatingBack
    fun pages(): List<Page> = pages.toList()
    fun currentIndex(): Int = index
}
