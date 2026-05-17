# Highlights List Reactivity Mini-Spec

## Problem

The Highlights screen loads global annotation summaries once when `HighlightsViewModel`
is created. When a user opens a highlight, changes annotations in the reader, then
navigates back, the Highlights destination is still on the navigation back stack and
reuses the old ViewModel state. Color changes, deletions, and new highlights made in
the reader are therefore not reflected until the screen is recreated or manually
reloaded.

## Goal

Make the Highlights list reactive to annotation changes made elsewhere in the app,
while preserving the existing global API refresh behavior.

## Approach

- Treat `cached_annotation` as the local source of truth for the rendered Highlights
  list.
- Add a Room query that observes all cached annotations joined with bookmark metadata.
- Expose that query as `HighlightsRepository.observeHighlights()`.
- Keep a separate `HighlightsRepository.refreshHighlights()` method that fetches
  `GET /bookmarks/annotations` and atomically replaces the full cached annotation set
  after the full network response succeeds.
- Have `HighlightsViewModel` collect the local flow and run a background refresh on
  entry/retry.

## Important Details

- The full-cache replacement must be all-or-nothing. This prevents the deleted-last-
  highlight case from leaving stale rows behind.
- Annotation mutations in `BookmarkDetailViewModel` already refresh
  `cached_annotation` for the affected bookmark, so the Highlights list should update
  immediately when the cached rows change.
- The global refresh should update `cached_annotation` only after all pages have been
  fetched successfully.
- No schema migration is required because this only adds queries/projections over the
  existing `cached_annotation` table.

## Non-Goals

- Offline creation, editing, or deletion of annotations.
- Editing or deleting highlights directly from the Highlights list.
- Adding paging or search to the Highlights list.
