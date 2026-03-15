# Spec: API Sync Architecture Optimization

## Summary

The current sync architecture has two categories of problems: (1) a blocking sync spinner
that shows for ~10 seconds on app open, creating a negative UX impression, and (2) an
insert path with an N+1 query pattern that scales poorly with library size. This spec
proposes targeted fixes to the sync pipeline while preserving the existing deferred
content load model.

## Current Architecture Overview

### Sync Triggers

The app syncs bookmarks in three contexts:

| Trigger | Worker | Entry Point |
|---|---|---|
| App open (if enabled) | `LoadBookmarksWorker` | `BookmarkListViewModel.init` |
| Pull-to-refresh | `LoadBookmarksWorker` | `BookmarkListViewModel.loadBookmarks()` |
| Periodic background sync | `FullSyncWorker` | WorkManager periodic schedule |

### Read Path (Server → Local)

1. **Delta sync** — `GET /bookmarks/sync?since=` returns changed IDs and deletions since
   last sync. If no updates exist, the worker exits early (fast path).
2. **Incremental load** — `GET /bookmarks?updated_since=&limit=50` fetches metadata page
   by page. Each page is inserted via `BookmarkRepositoryImpl.insertBookmarks()`.
3. **Content fetch** (deferred) — article HTML is fetched separately via
   `GET /bookmarks/{id}/article`, either on-demand when the user opens a bookmark or
   asynchronously via `BatchArticleLoadWorker` in AUTOMATIC sync mode.

### Write Path (Local → Server)

1. User actions (favorite, archive, read progress, labels, delete, etc.) are applied
   locally to the Room database immediately.
2. A `PendingActionEntity` is enqueued for each mutation.
3. `ActionSyncWorker` drains the queue, calling `PATCH /bookmarks/{id}` or
   `DELETE /bookmarks/{id}` for each action.
4. On metadata sync, `insertBookmarks()` merges remote state with any locally pending
   actions to avoid overwriting unsynced changes.

### Deferred Content Load Model

Article content is stored in a separate `article_content` table linked by foreign key
to the `bookmarks` table. The content lifecycle is tracked by `ContentState`:

- `NOT_ATTEMPTED` — default for newly synced bookmarks
- `DOWNLOADED` — content fetched and cached
- `DIRTY` — fetch failed, eligible for retry
- `PERMANENT_NO_CONTENT` — server has no article content (e.g., image/video bookmarks)

Content is fetched lazily when the user opens a bookmark (`BookmarkDetailViewModel.fetchContentOnDemand()`),
or proactively in the background when sync mode is AUTOMATIC (`BatchArticleLoadWorker`).

**This model is preserved as-is by all changes in this spec.** The optimizations below
affect only the metadata sync and insert paths.

## Problem 1: Blocking Sync Spinner (~10s on App Open)

### Root Cause

The bookmark list UI shows a pull-to-refresh spinner whenever `LoadBookmarksWorker` is
in RUNNING, ENQUEUED, or BLOCKED state (`BookmarkListViewModel.loadBookmarksIsRunning`).
The spinner persists for the entire duration of the worker, which includes:

1. Delta sync API call (~0.5–1s)
2. Paginated bookmark fetch, typically 2–4 pages (~1–3s)
3. Database inserts with N+1 queries (~5–8s for ~200 bookmarks)

The user sees no bookmarks updating during this time — the list only changes after the
entire worker completes, because Room's `@Transaction` annotation and the insert loop
structure prevent incremental visibility.

### Proposed Fix

**A. Make the spinner non-blocking for returning users.** The spinner should only block
the UI during initial sync (empty database). For returning users with cached data:

- Show cached bookmarks immediately from Room (already happens via Flow observation).
- Run sync in the background without showing the pull-to-refresh spinner.
- Optionally show a subtle, non-blocking sync indicator (e.g., a thin progress bar at
  the top of the list, or a small icon in the toolbar) so the user knows sync is
  happening without blocking interaction.

Implementation: In `BookmarkListViewModel`, split `loadBookmarksIsRunning` into two
states:

```kotlin
// Full-screen blocking spinner: only for initial sync with empty database
val isInitialLoading: StateFlow<Boolean>

// Subtle background indicator: for incremental syncs with cached data
val isSyncingInBackground: StateFlow<Boolean>
```

The `BookmarkListScreen` PullToRefreshBox uses `isInitialLoading` for its
`isRefreshing` property. A separate subtle indicator observes `isSyncingInBackground`.

Pull-to-refresh gestures can still show the standard refresh indicator since the user
explicitly requested it.

**B. Make inserts visible incrementally.** Each page of bookmarks (50 items) should be
committed to the database and visible in the UI before the next page is fetched. This is
already structurally close to how `LoadBookmarksUseCase.execute()` works (it calls
`insertBookmarks()` per page), but the spinner currently hides updates until the worker
finishes.

With fix A in place, cached data is visible immediately, and each page insert triggers
a Room Flow emission that updates the list in real-time.

## Problem 2: N+1 Database Insert Pattern

### Root Cause

`BookmarkDao.insertBookmarksWithArticleContent()` (line 139) iterates each bookmark
individually:

```kotlin
suspend fun insertBookmarksWithArticleContent(bookmarks: List<BookmarkWithArticleContent>) {
    bookmarks.forEach {
        insertBookmarkWithArticleContent(it)  // 3-4 queries per bookmark
    }
}
```

Each call to `insertBookmarkWithArticleContent()` executes:

1. `getContentStateById(id)` — SELECT query
2. `getArticleContent(id)` — SELECT query (conditional)
3. `insertBookmark(entity)` — INSERT/REPLACE
4. `insertArticleContent(entity)` — INSERT/REPLACE (conditional)

For 200 bookmarks, this is 600–800 sequential SQLite operations. The per-item queries
exist because Room's `REPLACE` conflict strategy does DELETE+INSERT, which triggers
CASCADE DELETE on the `article_content` foreign key. The code must read and restore
content state before each replace.

### Proposed Fix

**Batch the pre-insert lookups and use a single transaction:**

```kotlin
@Transaction
suspend fun insertBookmarksWithArticleContentBatch(bookmarks: List<BookmarkWithArticleContent>) {
    if (bookmarks.isEmpty()) return

    val ids = bookmarks.map { it.bookmark.id }

    // Batch fetch: 2 queries total instead of 2N
    val existingStates = getContentStatesByIds(ids).associateBy { it.bookmarkId }
    val existingContents = getArticleContentByIds(ids).associateBy { it.bookmarkId }

    for (bwac in bookmarks) {
        val id = bwac.bookmark.id
        val existingState = existingStates[id]

        val bookmarkToInsert = if (existingState != null &&
            bwac.bookmark.contentState == BookmarkEntity.ContentState.NOT_ATTEMPTED) {
            bwac.bookmark.copy(
                contentState = existingState.contentState,
                contentFailureReason = existingState.contentFailureReason
            )
        } else {
            bwac.bookmark
        }

        insertBookmark(bookmarkToInsert)

        val contentToSave = bwac.articleContent ?: existingContents[id]?.let {
            ArticleContentEntity(bookmarkId = id, content = it)
        }
        contentToSave?.run { insertArticleContent(this) }
    }
}
```

New DAO queries needed:

```kotlin
@Query("SELECT id AS bookmarkId, content_state AS contentState, content_failure_reason AS contentFailureReason FROM bookmarks WHERE id IN (:ids)")
suspend fun getContentStatesByIds(ids: List<String>): List<ContentStateProjection>

@Query("SELECT bookmark_id AS bookmarkId, content FROM article_content WHERE bookmark_id IN (:ids)")
suspend fun getArticleContentByIds(ids: List<String>): List<ArticleContentProjection>
```

Room's `IN` clause has a SQLite limit of 999 variables, so for pages of 50 this is well
within bounds. The existing page size of 50 in `LoadBookmarksUseCase` means this batch
query approach works without chunking.

**Expected improvement:** From ~800 queries to ~102 queries (2 batch SELECTs + 50
INSERTs for bookmarks + 50 INSERTs for content) for a 50-bookmark page. Estimated time
reduction from ~5–8s to <1s for the insert phase.

## Problem 3: Full Sync Pagination Redundancy

### Current Behavior

`performFullSync()` paginates through ALL bookmarks just to collect their IDs (for
deletion detection), storing them in a temporary `remote_bookmark_ids` table. This
duplicates much of the work that `LoadBookmarksUseCase.execute()` does immediately after.

### Proposed Fix (Lower Priority)

Combine the full sync ID collection with the bookmark metadata fetch. Instead of two
separate paginated walks:

1. Walk 1: `performFullSync()` fetches all bookmark IDs → stores in temp table → deletes
   missing
2. Walk 2: `loadBookmarksUseCase.execute()` fetches all bookmark metadata → inserts

Merge into a single walk that collects IDs for deletion detection AND inserts metadata
in one pass. This halves the number of API calls during a full sync.

This is lower priority because full syncs are infrequent (weekly fallback) and the delta
sync fast path avoids this entirely for most sync cycles.

## Implementation Plan

### Phase 1: Non-Blocking Sync UX (Addresses the 10s spinner)

1. Add `isInitialSyncPerformed` check to `BookmarkListViewModel` to distinguish first
   sync from incremental syncs.
2. Split `loadBookmarksIsRunning` into `isInitialLoading` and `isSyncingInBackground`.
3. Update `BookmarkListScreen` to use `isInitialLoading` for the PullToRefreshBox
   `isRefreshing` property.
4. Add a subtle sync indicator (thin linear progress or toolbar icon) driven by
   `isSyncingInBackground`.
5. Preserve pull-to-refresh spinner behavior for user-initiated refreshes.

### Phase 2: Batch Insert Optimization (Addresses the root performance issue)

1. Add `getContentStatesByIds()` and `getArticleContentByIds()` batch queries to
   `BookmarkDao`.
2. Add `ContentStateProjection` and `ArticleContentProjection` data classes.
3. Implement `insertBookmarksWithArticleContentBatch()` in `BookmarkDao`.
4. Update `BookmarkDao.insertBookmarksWithArticleContent()` to delegate to the batch
   method.
5. Unit test the batch path with edge cases: empty list, new bookmarks (no existing
   state), bookmarks with existing downloaded content, mixed states.

### Phase 3: Full Sync Consolidation (Optional, lower priority)

1. Refactor `performFullSync()` and `LoadBookmarksUseCase.execute()` to share a single
   paginated walk.
2. Collect remote IDs for deletion detection during the metadata fetch pass.
3. Delete orphaned local bookmarks after the combined walk completes.

## Constraints

- **Deferred content model is unchanged.** Article content fetching (on-demand and
  AUTOMATIC batch) is not modified by any phase.
- **Pending action merge logic is unchanged.** The `insertBookmarks()` method in
  `BookmarkRepositoryImpl` that merges remote state with pending local actions remains
  as-is. It already does batch lookups for pending actions and existing bookmarks.
- **Wire-compatible.** No API contract changes required — all fixes are client-side.
- **Backward-compatible database.** No schema migrations needed for Phase 1 or 2. The
  batch queries use the existing schema.

## Risks

- **Phase 1** changes the UX contract around sync feedback. Users accustomed to the
  spinner may not realize sync completed. The subtle indicator needs to be discoverable
  enough to provide confidence that data is fresh.
- **Phase 2** changes the transaction boundary. The current per-item transaction ensures
  partial progress is committed if the worker is killed mid-page. The batch approach
  within a single `@Transaction` means a page is all-or-nothing. This is acceptable
  because pages are 50 items and the worker will retry on next sync.
- **Room's REPLACE + CASCADE** behavior is the fundamental reason the read-before-write
  pattern exists. An alternative would be to switch to `INSERT ... ON CONFLICT UPDATE`
  via `@Upsert` (Room 2.5+), which avoids the DELETE+INSERT cycle entirely. This would
  eliminate the need to preserve content state and article content across replaces. Worth
  evaluating as a follow-up but requires careful migration testing.

## Testing

- **Unit tests:** Verify batch insert preserves content state for existing bookmarks,
  handles new bookmarks correctly, and produces the same results as the current per-item
  path.
- **Integration test:** Measure insert time for 200 bookmarks before and after the batch
  optimization.
- **Manual QA:** Verify the non-blocking spinner UX feels natural — cached data appears
  instantly, sync indicator is visible but not intrusive, pull-to-refresh still works as
  expected.
