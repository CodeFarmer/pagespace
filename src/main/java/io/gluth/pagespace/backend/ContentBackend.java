package io.gluth.pagespace.backend;

import io.gluth.pagespace.domain.Page;
import java.util.List;

public interface ContentBackend {

    /** The default page to load on startup. */
    Page defaultPage();

    /** Fetch the HTML body for the given page id. */
    String fetchBody(String id) throws PageNotFoundException;

    /** Fetch the list of pages linked from the given page id. */
    List<Page> fetchLinks(String id) throws PageNotFoundException;
}
