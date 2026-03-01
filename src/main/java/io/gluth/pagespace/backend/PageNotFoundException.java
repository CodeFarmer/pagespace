package io.gluth.pagespace.backend;

public class PageNotFoundException extends Exception {
    public PageNotFoundException(String id) {
        super("Page not found: " + id);
    }
}
