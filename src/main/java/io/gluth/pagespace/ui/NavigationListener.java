package io.gluth.pagespace.ui;

import io.gluth.pagespace.domain.Page;

@FunctionalInterface
public interface NavigationListener {
    void navigateTo(Page page);
}
