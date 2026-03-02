package io.gluth.pagespace.backend

class PageNotFoundException(id: String) : Exception("Page not found: $id")
