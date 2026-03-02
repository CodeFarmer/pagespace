package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page

fun interface NavigationListener {
    fun navigateTo(page: Page)
}
