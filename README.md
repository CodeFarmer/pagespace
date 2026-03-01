# page-space

This is a spatial browser for hypertext. Originally this was conceived
as a navigator for everything2, but a better modern target would be
Wikipedia.

The underlying engine can have multiple different targets, but the
reference implementation is Java/Swing.

On one pane is a standard hypertext window, displaying a current page
and traditional coloured hyperlinks.

On the secondary pane is a spatial representation containing page
titles - in the foreground is the title of the current page, and
spatially arranged around it in three dimenstions are the titles of
linked pages.

Titles of linked pages are in turn arranged closer to the pages that
they in turn are linked with. This proximity can be achieved by
gradual motion due to radial forces based on inter-node link density.
