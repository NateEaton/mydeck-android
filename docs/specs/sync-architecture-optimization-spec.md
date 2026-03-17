# Spec: Sync Architecture Optimization and Sync API Adoption

## Summary

This spec updates the sync proposal to match the current Android implementation and to
set a clearer priority order:

1. **Fix the blocking spinner UX for returning users and close the sync-cursor
   correctness gap.**
2. **Clean up the metadata insert path**, which currently does unnecessary per-bookmark
   preservation work during normal metadata syncs.
3. **Adopt the Readeck sync API more fully**, using the latest API spec in the repo as
   the source of truth:
   - keep `GET /bookmarks/sync?since=` as the delta detector
   - adopt `POST /bookmarks/sync` for content sync
   - capture and persist `omit_description` as part of that work
   - then migrate incremental metadata fetches toward the sync API as a follow-up

The deferred content model remains intact: content is still downloaded on demand or in
the background according to the user's content sync settings. The changes here focus on
how sync work is presented, how bookmark metadata is persisted, and how sync API
responses are consumed.

## Current Architecture Overview

### Sync Triggers

The app syncs bookmarks in three contexts:

| Trigger | Worker | Entry Point |
|---|---|---|
| App open (if enabled) | `LoadBookmarksWorker` | `BookmarkListViewModel.init` |
| Pull-to-refresh | `LoadBookmarksWorker` | `BookmarkListViewModel.onPullToRefresh()` |
| Periodic background sync | `FullSyncWorker` | WorkManager periodic schedule |

### Read Path Today (Server -> Local)

1. **Delta detection** — `GET /bookmarks/sync?since=` is already used to detect updated
   and deleted bookmark IDs since the last sync cursor.
2. **Incremental metadata load** — `GET /bookmarks?updated_since=&limit=50` fetches
   bookmark summaries page by page. Each page is written via
   `BookmarkRepositoryImpl.insertBookmarks()`.
3. **Content fetch** — article HTML is currently fetched with `GET /bookmarks/{id}/article`.
   All three content-sync modes share the same path:
   - on-demand content load from the detail screen
   - automatic background content sync (`BatchArticleLoadWorker`)
   - date-range content sync (`DateRangeContentSyncWorker`)

### Write Path (Local -> Server)

1. User mutations (favorite, archive, progress, labels, delete, metadata edits, etc.)
   are applied locally immediately.
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

This model is preserved. What changes is the transport used to fetch content and the
metadata fields stored alongside it.

## Priority 1: Non-Blocking Sync UX and Sync-Cursor Correctness

### Root Cause

The main UX problem is not that Room hides incremental updates. The current code already
loads and inserts bookmark pages one page at a time. The actual issue is that the list
screen shows the pull-to-refresh spinner whenever `LoadBookmarksWorker` is active,
regardless of whether the load was:

- the very first sync on an empty database
- a user-initiated pull-to-refresh
- a background app-open refresh with cached bookmarks already visible

That makes returning users see a long-running refresh affordance for background work even
though cached bookmarks are already on screen and the list can update incrementally as
Room emits new pages.

There is also a correctness issue to fix in the same phase: the current worker advances
`lastSyncTimestamp` immediately after delta detection succeeds, before the metadata reload
has completed. If the metadata load then fails, a later run can skip updates that were
already acknowledged by the delta cursor.

### Proposed Fix

#### A. Split loading state by intent, not just by worker activity

Introduce explicit UI states in `BookmarkListViewModel`:

```kotlin
// Full-screen loading state: only when there is no completed initial sync yet
val isInitialLoading: StateFlow<Boolean>

// Standard pull-to-refresh spinner: only for a user-initiated refresh
val isUserRefreshing: StateFlow<Boolean>

// Non-blocking background sync indicator for cached-data refreshes
val isSyncingInBackground: StateFlow<Boolean>
```

Implementation notes:

- Add an input flag or enum to `LoadBookmarksWorker` to identify the trigger:
  `INITIAL`, `APP_OPEN`, `PULL_TO_REFRESH`.
- Treat app-open sync for returning users as background work.
- Keep the normal pull-to-refresh spinner only for explicit user refreshes.
- Show a subtle non-blocking indicator for background sync, such as:
  - a thin linear progress bar under the app bar, or
  - a small sync icon/progress affordance in the toolbar

#### B. Preserve incremental visibility

No special batching change is required for list visibility. The existing per-page load
structure already allows incremental Room emissions. Once the spinner stops masking all
active work as a pull-to-refresh, users will see cached data immediately and page-level
updates as they arrive.

#### C. Fix sync cursor persistence

Update `LoadBookmarksWorker` so that:

- the delta result can still short-circuit the run when `countUpdated == 0`
- but `lastSyncTimestamp` is only persisted immediately in that no-update fast path
- when updated bookmarks must be reloaded, persist the new delta cursor only after the
  metadata load succeeds

This prevents acknowledged-but-unloaded updates from being skipped on the next run.

## Priority 2: Metadata Insert-Path Cleanup

### Root Cause

The current metadata sync path writes bookmark pages through
`BookmarkDao.insertBookmarksWithArticleContent()`, which iterates per bookmark and
preserves `contentState` and any existing `article_content` before doing an
`INSERT OR REPLACE`.

That preservation logic is correct for **content-bearing writes**, but it is overkill for
the common **metadata-only sync** case:

- metadata sync payloads normally have `articleContent = null`
- incoming content state is normally `NOT_ATTEMPTED`
- most sync pages do not intend to touch `article_content` at all

Because the current path always goes through the replace-and-restore logic, metadata sync
still pays the cost of per-item reads whose only purpose is to avoid deleting content
that the metadata update did not intend to change.

### Recommended Cleanup

Prefer a **dedicated metadata-only upsert path** over a new batch pre-read path.

The DAO already has an `upsertBookmark()` method that:

- uses `INSERT ... IGNORE` + `UPDATE`
- updates bookmark metadata fields in place
- avoids replacing the row
- therefore avoids cascading deletion of `article_content`
- naturally preserves `contentState` and `contentFailureReason`

For normal metadata sync pages, route writes through that path instead of
`insertBookmarkWithArticleContent()`.

### Proposed Persistence Split

In `BookmarkRepositoryImpl.insertBookmarks()`:

1. Keep the existing pending-action merge logic.
2. Split the resulting bookmark writes into two categories:
   - **metadata-only sync writes**
     - `articleContent == null`
     - incoming `contentState == NOT_ATTEMPTED`
     - use metadata-only upsert
   - **content-bearing or explicit content-state writes**
     - article HTML is present, or
     - content state is explicitly being changed
     - keep using the content-aware insert path

Illustrative shape:

```kotlin
val metadataOnly = merged.filter {
    it.articleContent == null &&
        it.bookmark.contentState == BookmarkEntity.ContentState.NOT_ATTEMPTED
}

val contentBearing = merged - metadataOnly.toSet()

bookmarkDao.upsertBookmarksMetadataOnly(metadataOnly.map { it.bookmark })
bookmarkDao.insertBookmarksWithArticleContent(contentBearing)
```

### Why This Is Preferred

Compared with adding batch preservation queries:

- it attacks the common path directly
- it is simpler to reason about
- it reuses a DAO capability that already exists
- it avoids extra schema-specific projection queries
- it lowers the risk of subtle regressions in article-content preservation

### Expected Outcome

This should remove the unnecessary per-bookmark preservation reads from the normal
metadata sync path while keeping the current content-preservation behavior for explicit
content downloads and refreshes.

## Priority 3: Adopt the Sync API for Content Sync and `omit_description`

### Goal

Move content sync toward the Readeck sync API in a way that:

- centralizes content download transport
- reduces repeated per-bookmark fetches
- captures richer bookmark metadata that the list endpoint does not provide
- persists `omit_description` consistently whenever content is synced

The latest OpenAPI spec in the repo should be treated as the source of truth for this
work.

### API Direction

Use the sync endpoints as follows:

- `GET /bookmarks/sync?since=` remains the delta detector for updated and deleted IDs
- `POST /bookmarks/sync` becomes the content-sync transport

From the current spec:

- request body uses `id`, not `ids`
- request parameters include:
  - `with_json`
  - `with_html`
  - `with_markdown`
  - `with_resources`
  - `resource_prefix`
- the response is `multipart/mixed`
- each part is identified by headers including `Bookmark-Id` and `Type`
- `Type: json` is documented as equivalent to bookmark information payloads, which is the
  variant that includes `omit_description`

### Proposed Content Sync Request Shape

For content sync, use:

```json
{
  "id": ["bookmark-id-1", "bookmark-id-2"],
  "with_json": true,
  "with_html": true,
  "with_resources": false
}
```

Notes:

- `with_resources` stays `false` initially to preserve the current storage model. The app
  does not currently persist sync-stream binary resources.
- never call `POST /bookmarks/sync` with an empty `id` list
- batch sizes should remain modest for automatic/date-range sync to limit memory pressure
  while the multipart parser is being introduced

### Unify All Content Sync Modes on `POST /bookmarks/sync`

Replace the current `GET /bookmarks/{id}/article` transport in the shared content-fetch
path with a sync-stream client:

- on-demand content sync:
  - call `POST /bookmarks/sync` with a single bookmark ID
- automatic background content sync:
  - call `POST /bookmarks/sync` with batched IDs
- date-range content sync:
  - call `POST /bookmarks/sync` with the selected ID batches

This keeps one transport for all content modes and ensures they all receive the same JSON
metadata and HTML content shape.

### Persist `omit_description`

Add a nullable `omitDescription` field across the model stack:

- REST DTO
- domain model
- Room entity

Populate it from the `Type: json` parts returned by `POST /bookmarks/sync`.

This field should be considered part of the same feature as sync-stream adoption, because
the existing list metadata endpoint does not expose it while the sync/detail payload does.

### UI Rule for `omit_description`

Use `omitDescription` to prevent duplicated text in the reader UI:

- if `omitDescription == true` and article HTML is present, hide the standalone
  description block in the bookmark detail header
- otherwise keep the current description behavior
- for photo/video bookmarks, keep using the description as a fallback when no article
  HTML exists; `omitDescription` should only suppress duplication, not remove the only
  available summary text

## Priority 4: Incremental Metadata Migration to the Sync API

### Goal

Once the spinner fix, cursor fix, insert cleanup, and multipart content client are in
place, move incremental metadata reloads away from paginated
`GET /bookmarks?updated_since=` and onto the sync API.

### Proposed Shape

For an incremental sync run:

1. Call `GET /bookmarks/sync?since=...`
2. Apply deletions locally
3. Collect updated IDs
4. If there are updated IDs, call `POST /bookmarks/sync` with:

```json
{
  "id": ["updated-1", "updated-2"],
  "with_json": true,
  "with_html": false,
  "with_resources": false
}
```

5. Upsert the returned bookmark metadata
6. Persist the new sync cursor after successful metadata application

### Why This Is a Later Phase

This is a larger architectural change than the top two priorities because it requires:

- a multipart parser
- a new sync-stream ingestion path
- stronger test coverage around partial stream processing
- a rollout strategy for full-sync/bootstrap behavior

It is still the preferred direction, but it should follow the simpler, higher-value fixes
that unblock the current UX and performance pain first.

## Full Sync / Bootstrap Follow-Up

The current weekly/forced full sync path still uses paginated `GET /bookmarks` to collect
all remote IDs for deletion detection. That can remain in place initially.

After incremental sync-stream ingestion is stable, revisit whether to migrate bootstrap
and full-library sync to the sync API as well. That can be evaluated separately from the
top-priority work because:

- full syncs are relatively infrequent
- the delta fast path already avoids them most of the time
- the first win comes from improving returning-user refreshes and metadata persistence

## Implementation Plan

### Phase 1: Spinner UX and Cursor Correctness

1. Add a load-trigger marker to `LoadBookmarksWorker` input data.
2. Split list-screen loading state into:
   - `isInitialLoading`
   - `isUserRefreshing`
   - `isSyncingInBackground`
3. Update `BookmarkListScreen` so the pull-to-refresh indicator is driven only by:
   - initial sync on an empty database, or
   - explicit pull-to-refresh
4. Add a subtle background sync indicator for cached-data refreshes.
5. Persist `lastSyncTimestamp` only after metadata reload succeeds, except for the
   no-update fast path.

#### Interactive Validation

1. Validate returning-user app-open behavior.
   - Make sure the app already has cached bookmarks and **Sync on app open** is enabled.
   - Force-close the app and reopen it.
   - Expected result: cached bookmarks appear immediately, the list stays interactive, and
     you do not see the pull-to-refresh spinner.
   - If sync work is running, you should only see the subtle background indicator.
2. Validate pull-to-refresh behavior.
   - From the bookmark list, pull down to refresh.
   - Expected result: the normal pull-to-refresh spinner appears only for this user action.
   - When the refresh completes, the spinner disappears and the list remains scrollable the
     whole time.
3. Validate first-sync behavior separately from returning-user behavior.
   - Use a fresh install, clear app data, or sign in on a second test device.
   - Launch the app and wait for the first bookmark sync to complete.
   - Expected result: the initial loading state is still shown for the true first sync, but
     subsequent launches use the returning-user behavior above.
4. Validate the sync-cursor fix with a forced failure between delta detection and metadata
   reload.
   - On the server, edit a bookmark title so there is a real metadata change to pull.
   - Trigger a sync on the device, then quickly disable network access before the metadata
     reload finishes. Using a slow network profile or a larger library makes this easier to
     catch.
   - Re-enable network and trigger sync again.
   - Expected result: the edited title still arrives on the second sync and is not skipped
     as already acknowledged.

### Phase 2: Metadata Insert Cleanup

1. Add a DAO method for metadata-only bookmark upserts (reusing `upsertBookmark()`).
2. Update `BookmarkRepositoryImpl.insertBookmarks()` to route metadata-only writes to
   that path.
3. Keep the current content-aware insert path for article downloads and explicit
   content-state changes.
4. Add tests covering:
   - metadata-only sync preserving downloaded article content
   - metadata-only sync preserving content state and failure reason
   - content-bearing writes still storing article HTML correctly
   - pending local action merge behavior remaining unchanged

#### Interactive Validation

1. Validate that metadata refresh does not discard downloaded article content.
   - Pick an article bookmark and open it once so its content is downloaded locally.
   - Put the device in airplane mode and reopen that bookmark to confirm the article is
     available offline.
   - Re-enable network, change that bookmark's title or description on the server, then sync
     bookmarks in MyDeck.
   - Return to airplane mode and reopen the same bookmark.
   - Expected result: the new metadata is visible, and the previously downloaded article
     still opens offline without being re-fetched.
2. Validate that metadata refresh does not disturb a no-content or failed-content state.
   - Pick a bookmark that currently falls back to web view or shows a content-load failure.
   - Change only its metadata on the server, then sync in MyDeck.
   - Expected result: the updated metadata appears, but the bookmark keeps the same content
     availability behavior it had before the metadata sync.
3. Validate that explicit content downloads still work through the content-aware path.
   - Use **Manual** content sync mode with **On demand** selected.
   - Open a bookmark that does not yet have local content so MyDeck downloads it.
   - After the content is present, run bookmark sync again.
   - Expected result: the bookmark still has its downloaded article afterward, and opening
     it offline still works.
4. Validate that pending local changes still win during sync.
   - In MyDeck, change a bookmark locally, such as favorite state, archive state, or labels,
     without waiting for the server to reflect the same change.
   - Trigger a bookmark sync while the local pending action still exists.
   - Expected result: the local state is preserved and is not overwritten by the remote
     metadata refresh.

### Phase 3: `POST /bookmarks/sync` for Content Sync + `omit_description`

1. Add request/response plumbing for `POST /bookmarks/sync` using the latest spec.
2. Implement a multipart parser that groups parts by `Bookmark-Id`.
3. Support at least:
   - `Type: json`
   - `Type: html`
4. Update the shared content-sync path to use the sync endpoint for:
   - on-demand content
   - automatic background content sync
   - date-range content sync
5. Add `omitDescription` to DTO/domain/entity models and persist it with synced content.
6. Update the detail UI to honor `omitDescription` when article HTML is present.

#### Interactive Validation

Run these checks once Phase 3 is implemented.

1. Validate on-demand content sync through `POST /bookmarks/sync`.
   - In Sync Settings, choose **Manual** and keep **On demand** selected.
   - Pick a bookmark whose article has not been downloaded yet and open it.
   - Expected result: the article loads normally, then remains available offline when you
     reopen it with network disabled.
   - If you inspect traffic or logs, you should see a `POST /bookmarks/sync` request for a
     single bookmark ID instead of `GET /bookmarks/{id}/article`.
2. Validate automatic content sync batching.
   - In Sync Settings, choose **Automatic**.
   - Add or update several bookmarks on the server, then trigger bookmark sync in the app.
   - After sync finishes, disable network and open multiple newly synced bookmarks.
   - Expected result: their content is already available offline, and network inspection
     shows batched `POST /bookmarks/sync` calls rather than one article request per bookmark.
3. Validate date-range content sync batching.
   - In Sync Settings, choose **Manual**, then switch the sub-option to **Date Range**.
   - Pick **Past week** or a custom range and tap **Download**.
   - After the job completes, disable network and open bookmarks inside and outside the
     selected range.
   - Expected result: bookmarks in range have offline content; bookmarks outside the range
     are unchanged.
4. Validate `omit_description` behavior in the detail UI.
   - Use a bookmark whose sync JSON includes `omit_description = true` and whose article HTML
     already contains the same summary text.
   - Open the bookmark detail screen.
   - Expected result: the standalone description block in the header is hidden, while the
     article body still renders normally.
5. Validate the fallback cases for `omit_description`.
   - Open a bookmark with `omit_description = false`.
   - Open a photo or video bookmark where article HTML is absent.
   - Expected result: descriptions still appear in the cases where they are the only useful
     summary text.
6. Validate persistence of the new field.
   - Open a bookmark after Phase 3 sync has fetched both JSON and HTML for it.
   - Force-close the app, disable network, and reopen the same bookmark.
   - Expected result: the same description-suppression behavior remains, proving
     `omitDescription` was stored locally and not just applied transiently.

### Phase 4: Incremental Metadata Migration

1. Replace `GET /bookmarks?updated_since=` for incremental reloads with:
   - `GET /bookmarks/sync?since=`
   - followed by `POST /bookmarks/sync` for updated IDs
2. Keep deletion handling driven by the delta list.
3. Reuse the metadata-only upsert path for the returned JSON records.
4. Re-evaluate full-sync/bootstrap migration after this is stable.

#### Interactive Validation

Run these checks once Phase 4 is implemented.

1. Validate the no-change fast path.
   - Trigger app-open sync or pull-to-refresh when nothing has changed on the server.
   - Expected result: sync completes quickly after `GET /bookmarks/sync?since=` and does not
     perform any follow-up metadata fetch.
2. Validate updated bookmark metadata ingestion through the sync API.
   - On the server, change titles, labels, or descriptions for several bookmarks.
   - Trigger sync in MyDeck.
   - Expected result: those metadata changes appear locally without relying on paginated
     `GET /bookmarks?updated_since=` requests.
3. Validate deletion handling still works.
   - Delete a bookmark on the server.
   - Trigger sync in MyDeck.
   - Expected result: the deleted bookmark disappears locally during the same sync flow.
4. Validate combined update-and-delete runs.
   - In one batch on the server, edit some bookmarks and delete another.
   - Trigger sync in MyDeck.
   - Expected result: edits are applied, deletions are removed, and there is no need for a
     separate fallback sync to reconcile the library.
5. Validate transport at the network or log level.
   - Inspect traffic or logs during an incremental sync with updates present.
   - Expected result: you see `GET /bookmarks/sync?since=` followed by `POST /bookmarks/sync`
     with `with_json = true` and `with_html = false`, and you no longer see incremental
     `GET /bookmarks?updated_since=` pagination for the same run.
6. Re-run the cursor failure scenario from Phase 1 after the transport migration.
   - Force a metadata-sync failure after delta detection but before all updated JSON records
     are applied.
   - Retry sync after restoring network.
   - Expected result: no acknowledged update is skipped on retry.

## Constraints

- **Deferred content model remains intact.** Content is still a separately tracked local
  cache with on-demand and background sync modes.
- **Pending-action merge logic remains intact.** Remote metadata still must be merged with
  unsynced local actions before persistence.
- **Wire compatibility is preserved.** All changes remain client-side and rely on the
  published Readeck API surface already captured in the repo.
- **Schema migration is not required for Phases 1 and 2.** A migration will be required
  once `omitDescription` is added to persisted models in Phase 3.

## Risks

- **Phase 1:** changing the spinner behavior can make sync feel less visible. The
  background indicator should be noticeable enough to signal freshness without implying a
  blocking refresh state.
- **Phase 2:** the metadata-only routing logic must not accidentally swallow legitimate
  content-bearing writes. Detection rules need tests around article downloads, annotation
  refreshes, and explicit content-state updates.
- **Phase 3:** multipart parsing introduces a new failure mode surface:
  partial streams, malformed parts, missing `Bookmark-Id` headers, and mismatched json/html
  parts must all fail safely.
- **Phase 3:** `omitDescription` must be treated as a presentation hint, not as a signal
  to delete description data. The raw description still needs to be stored for fallback
  cases.
- **Phase 4:** moving incremental metadata reloads to `POST /bookmarks/sync` changes a
  long-standing code path and should keep a clear rollback option until the new ingestion
  path is proven in production.

## Testing

- **Phase 1 unit/integration tests:** verify app-open sync, initial sync, and
  pull-to-refresh each drive the correct UI state; verify failed metadata reloads do not
  advance the sync cursor.
- **Phase 2 DAO/repository tests:** verify metadata-only sync preserves existing article
  HTML, `contentState`, and `contentFailureReason`; verify content-bearing writes still
  update article HTML and explicit content states correctly.
- **Phase 3 parser tests:** verify multipart streams containing `Type: json` and
  `Type: html` parts are grouped correctly by bookmark ID, and verify empty/malformed
  streams fail safely.
- **Phase 3 model/UI tests:** verify `omitDescription` is persisted and suppresses the
  duplicate description header only when article HTML is present.
- **Phase 4 sync-flow tests:** verify incremental runs using `GET /bookmarks/sync` plus
  `POST /bookmarks/sync` correctly apply deletes, upsert updated records, and advance the
  cursor only after successful persistence.
