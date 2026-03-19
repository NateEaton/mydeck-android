# Spec: Sync Architecture Optimization Status and Follow-Up

## Superseded Status

This document is superseded as the forward-looking sync plan by:

- `docs/specs/sync-multipart-adoption-spec.md`

Keep this file as a historical record of the `v0.11.1` baseline and the earlier phase-based
planning that led to the multipart adoption decision.

## Summary

This document now tracks the status of the original sync optimization work after the
phase 1 and phase 2 implementation on this branch.

Completed:

1. **Phase 1: non-blocking sync UX and sync-cursor correctness**
2. **Phase 2: metadata insert-path cleanup**

Deferred follow-up:

1. **Phase 3: adopt `POST /bookmarks/sync` for content sync**
2. **Phase 4: migrate incremental metadata reloads from paginated
   `GET /bookmarks?updated_since=` to the sync API**

Phase 3 is now tracked separately in
`docs/specs/sync-content-api-omit-description-spec.md`.

Phase 4 remains on the back burner because phase 1 and phase 2 removed the highest-value
correctness and UX problems without requiring a multipart-based metadata ingestion
pipeline.

## Current Architecture Overview

### Sync Triggers

The app syncs bookmarks in three contexts:

| Trigger | Worker | Entry Point |
|---|---|---|
| App open (if enabled) | `LoadBookmarksWorker` | `BookmarkListViewModel.init` |
| Pull-to-refresh | `LoadBookmarksWorker` | `BookmarkListViewModel.onPullToRefresh()` |
| Periodic background sync | `FullSyncWorker` | WorkManager periodic schedule |

### Read Path Today (Server -> Local)

1. **Delta detection** — `GET /bookmarks/sync?since=` is used to detect updated and
   deleted bookmark IDs since the last sync cursor.
2. **Incremental metadata load** — `GET /bookmarks?updated_since=&limit=50` fetches
   bookmark summaries page by page. Each page is written via
   `BookmarkRepositoryImpl.insertBookmarks()`.
3. **Content fetch** — article HTML is still fetched with `GET /bookmarks/{id}/article`.
   All three content-sync modes share the same path:
   - on-demand content load from the detail screen
   - automatic background content sync (`BatchArticleLoadWorker`)
   - date-range content sync (`DateRangeContentSyncWorker`)

### Write Path (Local -> Server)

1. User mutations are applied locally immediately.
2. A `PendingActionEntity` is queued for each mutation.
3. `ActionSyncWorker` drains the queue by calling `PATCH /bookmarks/{id}` or
   `DELETE /bookmarks/{id}`.
4. During metadata sync, remote bookmark state is merged with pending local actions so
   unsynced user changes are not overwritten.

### Deferred Content Model

Article content lives in a separate `article_content` table linked to `bookmarks`.
The content lifecycle remains:

- `NOT_ATTEMPTED`
- `DOWNLOADED`
- `DIRTY`
- `PERMANENT_NO_CONTENT`

This model is unchanged by the completed work in phases 1 and 2.

## `omit_description` Status: Implemented on the Shared Content Path

`omit_description` no longer needs to wait for multipart sync-API adoption.

The incremental metadata list flow still cannot provide the field because
`GET /bookmarks?updated_since=` returns bookmark summaries, not bookmark detail. Instead,
the app now enriches the existing shared content-download path:

- successful article downloads still use `GET /bookmarks/{id}/article`
- when an article bookmark has a description and local `omitDescription` is unknown, that
  same shared path also calls `GET /bookmarks/{id}` to read `omit_description`
- the stored value is then reused by on-demand, automatic, and date-range content sync

To keep the cached hint correct when metadata changes ahead of content:

- metadata-only sync preserves `omitDescription` only while the description text is
  unchanged
- metadata-only sync clears it when description text changes without a content refresh
- local metadata edits do the same invalidation

UI behavior stays conservative:

- article bookmarks hide the separate italic header description only when
  `omitDescription == true` and article HTML is present
- photo and video fallbacks continue showing description when it is still the only useful
  summary text
- the raw description is always kept; `omitDescription` is a presentation hint only

## Phase 1 Status: Completed

### Problem That Was Solved

Returning users were seeing the pull-to-refresh spinner for app-open syncs even when
cached bookmarks were already visible. At the same time, `LoadBookmarksWorker` advanced
`lastSyncTimestamp` too early, which could cause acknowledged-but-not-yet-loaded updates
to be skipped after a failure.

### What Shipped

1. `LoadBookmarksWorker` now carries an explicit trigger:
   - `INITIAL`
   - `APP_OPEN`
   - `PULL_TO_REFRESH`
2. `BookmarkListViewModel` now splits list loading state into:
   - `isInitialLoading`
   - `isUserRefreshing`
3. `BookmarkListScreen` now drives the refresh affordance only from:
   - true first sync on an empty database
   - explicit user pull-to-refresh
4. `LoadBookmarksWorker` now persists `lastSyncTimestamp` only:
   - immediately in the no-update fast path
   - after metadata reload succeeds when updates must be reloaded

### Design Decision: No Background Sync Indicator

The original proposal included a separate background-sync indicator for cached-data
refreshes. That was explored during implementation, but it was removed before landing.

Reasoning:

- it added UI churn without solving a correctness issue
- it introduced layout/visibility trade-offs that were worse than simply not treating
  app-open sync as a pull-to-refresh state
- once the blocking refresh spinner was limited to initial sync and explicit user refresh,
  the core UX problem was already resolved

Current product behavior is therefore:

- **initial sync** remains visibly blocking
- **user pull-to-refresh** remains visibly refreshing
- **app-open refresh with cached data** is non-blocking and intentionally quiet

### Validation and Test Coverage

The branch includes tests around:

- app-open sync enqueue behavior
- pull-to-refresh enqueue behavior
- initial-sync error state handling
- sync-cursor regression coverage so a failed metadata reload does not persist the new
  delta cursor

## Phase 2 Status: Completed

### Problem That Was Solved

Normal metadata syncs were using the content-aware replace-and-restore path even when the
incoming page did not intend to touch article HTML or content state.

That made metadata sync do unnecessary per-bookmark preservation work just to avoid
damaging `article_content`.

### What Shipped

1. `BookmarkDao.upsertBookmarksMetadataOnly()` was added on top of the existing
   `upsertBookmark()` behavior.
2. `BookmarkRepositoryImpl.insertBookmarks()` now splits merged bookmarks into:
   - **metadata-only writes**
   - **content-bearing or explicit content-state writes**
3. Metadata-only sync pages now use the metadata-only upsert path.
4. Explicit content downloads and explicit content-state changes still use
   `insertBookmarksWithArticleContent()`.

### Why This Was Enough

This preserved the important invariants while simplifying the common path:

- existing downloaded article HTML remains intact
- `contentState` remains intact for metadata-only refreshes
- `contentFailureReason` remains intact for metadata-only refreshes
- pending local actions still merge ahead of persistence

### Validation and Test Coverage

The branch includes tests covering:

- metadata-only upserts preserving downloaded article content
- metadata-only upserts preserving `contentState`
- metadata-only upserts preserving `contentFailureReason`
- content-bearing writes using the content-aware path
- explicit content-state changes without article HTML still using the content-aware path

## Phase 3 Follow-Up

Phase 3 remains a valid follow-up and is now tracked separately in:

`docs/specs/sync-content-api-omit-description-spec.md`

That work is still the right place to address:

- `POST /bookmarks/sync` for content sync
- multipart parsing
- unifying on-demand, automatic, and date-range content sync transport
- optionally moving `omit_description` acquisition onto the multipart payload later

## Phase 4 Status: Deferred

### What Phase 4 Would Do

Phase 4 would replace incremental metadata reloads based on paginated
`GET /bookmarks?updated_since=` with:

1. `GET /bookmarks/sync?since=`
2. `POST /bookmarks/sync` for updated IDs with `with_json = true`

### Why It Is Deferred

After phases 1 and 2, the main user-facing and correctness pain points are already
addressed:

- returning-user refreshes are no longer presented as blocking pull-to-refresh work
- sync cursor advancement is no longer incorrect
- metadata sync no longer pays the content-preservation cost on the common path

Phase 4 still has architectural value, but it now falls into the category of
lower-priority optimization:

- it requires multipart parsing and a new ingestion path even for metadata-only updates
- it increases failure-mode complexity
- it is harder to test and harder to debug than the current paginated JSON path
- it no longer unlocks a top-priority correctness fix

### Revisit Criteria

Revisit phase 4 only if at least one of these becomes true:

- incremental metadata pagination is still measurably too slow
- server/API evolution makes the sync API clearly better supported than the list endpoint
- phase 3 multipart infrastructure is already in place and proven stable

## Constraints

- **Deferred content model remains intact.** Content is still a separately tracked local
  cache with on-demand and background sync modes.
- **Pending-action merge logic remains intact.** Remote metadata still must be merged with
  unsynced local actions before persistence.
- **Wire compatibility is preserved.** All completed work remains client-side and uses the
  published Readeck API surface already captured in the repo.
- **Schema migration is now required for `omitDescription`.** The local bookmark schema now
  persists the presentation hint independently of future sync-API transport work.

## Remaining Risks

- **Phase 3:** multipart parsing introduces a new failure surface:
  partial streams, malformed parts, missing `Bookmark-Id` headers, and mismatched json/html
  parts must all fail safely.
- **`omitDescription`:** it must remain a presentation hint, not a signal to delete
  description data. The raw description still needs to be stored for fallback cases.
- **Phase 4:** moving incremental metadata reloads to `POST /bookmarks/sync` should keep a
  clear rollback option until the new ingestion path is proven in production.

## Next Actions

1. Land phases 1 and 2 with their regression coverage.
2. Keep this document in `docs/specs/` as the status record for the completed work.
3. Track phase 3 independently as a follow-up feature.
4. Leave phase 4 deferred unless future profiling or production evidence justifies it.
