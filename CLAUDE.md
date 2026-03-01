# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**page-space** is a spatial browser for hypertext (Wikipedia or similar resources). It presents two panes:

1. **Content pane**: Standard hypertext viewer with colored hyperlinks
2. **Spatial pane**: 3D spatial representation where the current page title is in the foreground and linked page titles are arranged around it in three dimensions. Pages are positioned by radial force simulation based on inter-node link density — pages that are heavily cross-linked cluster closer together.

## Architecture

The design is intentionally target-agnostic: a core spatial navigation engine supports multiple content backends. The **reference implementation is Java/Swing**.

Key concepts:
- **Force-directed graph**: Node positions are driven by radial forces derived from link density between pages
- **Gradual motion**: Nodes move toward their equilibrium positions over time, giving a sense of the graph's topology evolving

## Development Workflow

Use a TDD cycle: write a failing test first, then implement the minimum code to make it pass, then refactor.

## Status

Early-stage project. As of now the repository contains only this README and CLAUDE.md. Implementation files have not yet been committed.
