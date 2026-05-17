# Highlights List Local-First Scalability Amendment

**Status:** Draft amendment for implementation
**Date:** 2026-05-09
**Amends:**
- `docs/archive/highlights-nav-drawer-list-spec.md`
- `docs/archive/highlights-list-reactivity-mini-spec.md`

---

## 1. Summary

The original Highlights list implementation assumed a user would have tens to low
hundreds of highlights. That assumption is no longer safe. A tester with hundreds or
thousands of historic highlights sees the Highlights page remain on a spinner for a
long time and eventually fail with an apparent server/network error even though the
server is healthy.

This amendment changes the architecture from "fetch the full global annotation list
before the screen is useful" to "render from the local annotation cache immediately
and synchronize highlights in the background." This mirrors the core bookmark
metadata model: Room is the UI source of truth; network work updates Room.

The current `HighlightsRepository.refreshHighlights()` still fetches every page of
`GET /bookmarks/annotations`, accumulates all results in memory, and replaces the
entire local cache only after the final page succeeds. That full refresh is triggered
both when the Highlights screen opens and during app open for the drawer badge. This
is the main scalability flaw this amendment addresses.

---

## 2. Problem Diagnosis

Current branch behavior:

- `BookmarkListViewModel` refreshes all highlights during app open so the drawer badge
  has a current count.
- `HighlightsViewModel` calls `loadHighlights()` during init and on `ON_RESUME`.
- `HighlightsRepository.refreshHighlights()` requests `/bookmarks/annotations` with
  `limit = 50`, increasing `offset` until the server returns a short page.
- The repository stores every `AnnotationSummaryDto` in a mutable list.
- Room is updated only after the complete remote list is fetched successfully.
- On a cold local cache, the Highlights screen has no useful local data to show and
  remains in `Loading` until the full refresh succeeds or fails.

Why this fails for large historic accounts:

- Hundreds or thousands of highlights require many serialized HTTP requests.
- Any slow page, proxy timeout, server-side expensive query, or mobile-network stall
  fails the whole screen load.
- The UI cannot show partial progress because the database write is all-or-nothing
  after the last page.
- The app pays the same full-refresh cost at app open for a badge count, even if the
  user never opens Highlights.
- The screen also materializes and sorts the entire highlight list in ViewModel memory
  before Compose can render it.

This is an architectural issue rather than primarily a UI rendering bug.

---

## 3. Revised Architecture

`cached_annotation` becomes the local source of truth for every Highlights-list UI
surface:

- Highlights screen list content
- Highlights empty state
- Drawer or rail badge count
- Reactivity after create/update/delete in the reader

Remote refresh becomes a background synchronization concern. Opening the Highlights
destination may start or request a sync, but it must not block rendering of cached
data.

### Required Behaviour

- If cached highlights exist, the screen shows them immediately.
- If a sync is running, show a non-blocking refreshing indicator.
- If the sync fails and cached highlights exist, keep showing cached highlights and
  expose a retry affordance.
- If the local cache is empty, show an empty state with a refreshing indicator while
  a sync is in progress.
- Only show a full-screen error when there is no cached data and the refresh failed.
- The drawer/rail badge reads from local Room only; it must not trigger a global
  annotation network refresh.

### Relationship To Bookmark Sync

This should be managed like bookmark metadata in philosophy, but not necessarily with
the exact same implementation. Bookmarks have a server delta endpoint and richer sync
state. Unless Readeck exposes annotation deltas, highlights need a lighter full
reconciliation loop over `/bookmarks/annotations`.

---

## 4. Data Layer Changes

### 4.1 Existing `cached_annotation`

Keep `cached_annotation` as the canonical local annotation table:

```kotlin
data class CachedAnnotationEntity(
    @PrimaryKey val id: String,
    val bookmarkId: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String
)
```

Do not store bookmark title/site name redundantly in this table. Continue joining to
`bookmarks` for display metadata so bookmark title changes are reflected.

### 4.2 Add Ordering Index

Add a Room migration that creates an index for the global list query:

```sql
CREATE INDEX IF NOT EXISTS index_cached_annotation_created
ON cached_annotation(created);
```

If query plans show the join needs it, prefer this composite index instead:

```sql
CREATE INDEX IF NOT EXISTS index_cached_annotation_created_bookmarkId
ON cached_annotation(created, bookmarkId);
```

Use the smallest index that measurably improves the global list query.

### 4.3 Optional Reconciliation Table

For scalable full reconciliation without holding the entire remote annotation set in
memory, add a temporary persisted table analogous to `remote_bookmark_ids`:

```kotlin
@Entity(tableName = "remote_annotation_ids")
data class RemoteAnnotationIdEntity(
    @PrimaryKey val id: String
)
```

DAO operations:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertRemoteAnnotationIds(ids: List<RemoteAnnotationIdEntity>)

@Query("DELETE FROM remote_annotation_ids")
suspend fun clearRemoteAnnotationIds()

@Query("""
    DELETE FROM cached_annotation
    WHERE NOT EXISTS (
        SELECT 1 FROM remote_annotation_ids
        WHERE remote_annotation_ids.id = cached_annotation.id
    )
""")
suspend fun removeAnnotationsMissingFromRemote(): Int
```

This lets the sync write pages incrementally and delete stale local rows only after
the remote crawl completes successfully.

If the implementer chooses not to add this table, the fallback is to keep collecting
all remote IDs in memory and delete stale rows in one final transaction. That is
acceptable for low thousands, but the table is cleaner and matches existing bookmark
sync architecture.

---

## 5. DAO API

Extend `CachedAnnotationDao` with local-first list and count queries.

### 5.1 Observe Full List

The existing `observeAllHighlights()` is acceptable for now, but it should be treated
as a source-of-truth query, not as a post-network rendering path:

```sql
SELECT
    ca.id AS id,
    ca.bookmarkId AS bookmarkId,
    ca.text AS text,
    ca.color AS color,
    ca.note AS note,
    ca.created AS created,
    b.title AS bookmarkTitle,
    b.siteName AS bookmarkSiteName
FROM cached_annotation ca
INNER JOIN bookmarks b ON b.id = ca.bookmarkId
WHERE b.isLocalDeleted = 0
ORDER BY ca.created DESC
```

### 5.2 Observe Count For Badge

Move the badge count away from `BookmarkDao.observeAllBookmarkCounts()` if that query
becomes expensive or semantically muddy. Prefer a direct highlights count flow:

```kotlin
@Query("""
    SELECT COUNT(*)
    FROM cached_annotation ca
    INNER JOIN bookmarks b ON b.id = ca.bookmarkId
    WHERE b.isLocalDeleted = 0
""")
fun observeHighlightCount(): Flow<Int>
```

The drawer badge should observe this local count. It should not cause a network call.

### 5.3 Future Paging Query

For very large accounts, introduce Paging 3 over Room:

```kotlin
@Query("""
    SELECT ...
    FROM cached_annotation ca
    INNER JOIN bookmarks b ON b.id = ca.bookmarkId
    WHERE b.isLocalDeleted = 0
    ORDER BY ca.created DESC
""")
fun pagingSourceForHighlights(): PagingSource<Int, CachedAnnotationHighlightEntity>
```

Paging 3 is preferred once the UI must handle several thousand highlights smoothly.
If implementation scope needs to be smaller, keep the Flow list for this patch and
add Paging 3 as a follow-up. Do not keep blocking network refresh as the tradeoff.

---

## 6. Repository API

Replace the current all-or-nothing refresh API with local observation plus explicit
sync state.

Suggested interface:

```kotlin
interface HighlightsRepository {
    fun observeHighlights(): Flow<List<HighlightSummary>>
    fun observeHighlightCount(): Flow<Int>
    fun observeSyncState(): StateFlow<HighlightsSyncState>

    suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit>
}

enum class HighlightsRefreshReason {
    SCREEN_OPEN,
    USER_RETRY,
    APP_BACKGROUND
}

sealed interface HighlightsSyncState {
    data object Idle : HighlightsSyncState
    data class Running(val loadedCount: Int? = null) : HighlightsSyncState
    data class Failed(val message: String, val cause: Throwable? = null) : HighlightsSyncState
}
```

Implementation notes:

- `observeHighlights()` must be pure Room observation.
- `observeHighlightCount()` must be pure Room observation.
- `requestRefresh()` should avoid starting duplicate refreshes.
- `SCREEN_OPEN` can be rate-limited, for example skip if a refresh succeeded recently.
- `USER_RETRY` should bypass the freshness throttle.
- Do not clear `cached_annotation` before the network crawl completes.

---

## 7. Remote Sync Algorithm

Use `/bookmarks/annotations` as the full reconciliation source until Readeck exposes a
dedicated annotation delta endpoint.

### 7.1 Page Loop

```kotlin
suspend fun refreshAllHighlights(): Result<Unit> {
    if (!refreshMutex.tryLock()) return Result.success(Unit)
    try {
        syncState.value = HighlightsSyncState.Running(loadedCount = 0)

        cachedAnnotationDao.clearRemoteAnnotationIds()

        var offset = 0
        var loaded = 0
        while (true) {
            val response = readeckApi.getAnnotationSummaries(
                limit = HIGHLIGHTS_PAGE_SIZE,
                offset = offset,
            )
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("HTTP ${response.code()}"))
            }

            val page = response.body().orEmpty()
            if (page.isEmpty() && offset == 0) {
                break
            }

            database.withTransaction {
                cachedAnnotationDao.upsertAnnotations(page.map { it.toCachedEntity() })
                cachedAnnotationDao.insertRemoteAnnotationIds(
                    page.map { RemoteAnnotationIdEntity(it.id) }
                )
            }

            loaded += page.size
            syncState.value = HighlightsSyncState.Running(loadedCount = loaded)

            if (page.size < HIGHLIGHTS_PAGE_SIZE) break
            offset += HIGHLIGHTS_PAGE_SIZE
        }

        database.withTransaction {
            cachedAnnotationDao.removeAnnotationsMissingFromRemote()
            cachedAnnotationDao.clearRemoteAnnotationIds()
        }

        saveLastSuccessfulHighlightsRefresh(Clock.System.now())
        syncState.value = HighlightsSyncState.Idle
        return Result.success(Unit)
    } catch (e: Exception) {
        syncState.value = HighlightsSyncState.Failed(e.message ?: "Failed to refresh highlights", e)
        return Result.failure(e)
    } finally {
        refreshMutex.unlock()
    }
}
```

Details to preserve:

- Upsert page results as they arrive so the UI can populate during a long first sync.
- Delete stale local annotations only after every remote page succeeds.
- Clear the remote-ID table after successful reconciliation and at the start of the
  next run.
- If the refresh fails midway, leave existing cached rows intact.
- Use a larger page size only after testing. `50` is safe but chatty; `100` or `200`
  may be reasonable if Readeck handles it reliably.

### 7.2 Empty Remote Result

If the server returns an empty first page, the correct final state is an empty local
cache. Still perform the stale-row deletion after the crawl succeeds.

### 7.3 Missing Pagination Headers

The current endpoint wrapper does not use pagination headers. Continue supporting the
short-page termination rule. If Readeck returns reliable `total-pages/current-page`
headers for this endpoint, use them to avoid an extra request when the total is an
exact multiple of the page size.

### 7.4 Error Messages

Do not expose raw exception strings as the primary user-facing text. Convert common
failures to existing localized strings or add new strings:

- "Could not refresh highlights"
- "Showing saved highlights"
- "Retry"

Any new strings must be added as English placeholders to all language `strings.xml`
files per project rules.

---

## 8. ViewModel Changes

`HighlightsViewModel` should combine local highlights and sync state.

Suggested state:

```kotlin
data class HighlightsUiState(
    val groups: List<BookmarkHighlightGroup> = emptyList(),
    val isInitialLocalLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
)
```

Derived UI rules:

- `isInitialLocalLoad`: only while waiting for the first Room emission.
- Empty state: `groups.isEmpty() && !isInitialLocalLoad`.
- Full-screen loading: only `isInitialLocalLoad`.
- Refresh indicator: `isRefreshing`, rendered without hiding existing groups.
- Error banner/snackbar: `refreshError != null`.
- Full-screen error: only when `groups.isEmpty()` and the last refresh failed.

Implementation outline:

```kotlin
init {
    observeCachedHighlights()
    observeSyncState()
    requestRefresh(HighlightsRefreshReason.SCREEN_OPEN)
}

fun retry() {
    requestRefresh(HighlightsRefreshReason.USER_RETRY)
}
```

Remove the current pattern where `loadHighlights()` sets `Loading` before the remote
refresh. A screen-open refresh should be background work from the UI perspective.

Also reconsider the `ON_RESUME` refresh in `HighlightsScreen`. If retained, it should
call the throttled `SCREEN_OPEN` refresh path and never force a full blocking reload.

---

## 9. UI Changes

The Highlights screen should always render local content when available.

Recommended UI:

- Top app bar title remains `Highlights`.
- If `isRefreshing`, show a small top linear progress indicator below the app bar or
  a pull-to-refresh indicator.
- If `refreshError` and `groups` is not empty, show a non-blocking message such as
  "Could not refresh highlights. Showing saved highlights." with Retry.
- If `groups` is empty and refresh is running, show the normal empty-state area plus
  a small progress indicator and "Refreshing highlights..." text.
- If `groups` is empty and refresh failed, show the empty/error state with Retry.

The current `LazyColumn` renders each `HighlightCard` and then a bookmark title line.
That can remain for the first scalability patch. For very large local caches, migrate
to Paging 3 and avoid pre-grouping the entire list in memory.

If retaining grouping without Paging 3, cap text length in each card to avoid extreme
single-highlight cards making the list unwieldy:

```kotlin
Text(
    text = highlight.text,
    maxLines = 5,
    overflow = TextOverflow.Ellipsis,
)
```

This is a display ergonomics improvement, not the root fix.

---

## 10. Drawer And App-Open Behaviour

Remove this behaviour:

- App open calls `highlightsRepository.refreshHighlights()` just to populate the
  drawer/rail highlight badge.

Replace it with:

- Drawer/rail badge observes `cached_annotation` count from Room.
- App open may schedule a background highlights refresh only if:
  - user is authenticated,
  - network is available,
  - no highlight refresh is already running,
  - a freshness throttle says refresh is due.

Do not delay bookmark sync behind highlight refresh. Bookmark metadata sync is more
central to the app and should not wait for a large annotation crawl.

Recommended throttle:

- Screen open: skip if a refresh succeeded in the last 5-15 minutes.
- App background/app open: skip if a refresh succeeded in the last 6-24 hours.
- User retry/pull-to-refresh: run immediately.

Store the last successful highlights refresh timestamp in `SettingsDataStore`.

---

## 11. Mutation Reactivity

Reader-side annotation creation/update/delete should continue to update
`cached_annotation` for the affected bookmark immediately after the server mutation
and local HTML refresh succeed. This is what makes the Highlights screen reactive when
returning from the reader.

Important cases:

- Create: insert/update the new annotation row locally.
- Update color/note: update the row locally without waiting for global refresh.
- Delete: remove the row locally.
- Per-bookmark refresh: `replaceAnnotationsForBookmark()` remains valid for detail
  screen reconciliation.

Do not rely on the next global refresh to reflect a just-completed local user action.

---

## 12. WorkManager Option

The first implementation can run the refresh inside `HighlightsRepository` using an
application-scoped coroutine and a mutex.

If the refresh may outlive the screen or should run periodically, add a
`HighlightsSyncWorker`:

- Unique work name: `HighlightsSync`
- Constraint: `NetworkType.CONNECTED`
- Existing work policy: `KEEP`
- Input reason: screen open, user retry, app background
- Output is logged; UI observes Room and sync-state storage rather than the worker
  result directly.

Do not run multiple global annotation refreshes in parallel.

---

## 13. Implementation Plan

1. Add DAO support:
   - `observeHighlightCount()`
   - page/upsert helpers as needed
   - optional `remote_annotation_ids` table
   - migration for the new index and optional table

2. Refactor `HighlightsRepository`:
   - make observation local-only
   - add sync state
   - replace atomic full-list replacement with page-by-page upsert
   - delete stale rows only after a successful full crawl
   - add duplicate-refresh protection

3. Refactor `BookmarkListViewModel`:
   - remove app-open blocking call to `refreshHighlightsForDrawer()`
   - expose local highlight count through existing `bookmarkCounts` or a separate
     count flow
   - optionally schedule throttled background refresh after bookmark sync starts

4. Refactor `HighlightsViewModel`:
   - combine local rows and sync state
   - remove blocking `Loading` before network refresh
   - make retry request an explicit refresh reason

5. Update `HighlightsScreen`:
   - render cached content during refresh
   - show non-blocking refresh/error UI
   - keep navigation-to-bookmark behavior unchanged

6. Add tests:
   - repository does not clear cache on mid-refresh failure
   - repository upserts page results incrementally
   - stale rows are deleted only after successful full refresh
   - ViewModel shows cached success while refresh is running
   - ViewModel shows cached success plus error when refresh fails
   - empty cache plus refresh failure produces full-screen retry state
   - app open no longer invokes a blocking full annotation refresh for the badge

7. Run required verification:
   - `./gradlew :app:assembleDebugAll`
   - `./gradlew :app:testDebugUnitTestAll`
   - `./gradlew :app:lintDebugAll`

Run Gradle tasks serially.

---

## 14. Acceptance Criteria

- Opening Highlights with an existing local cache displays rows immediately.
- Opening Highlights with thousands of cached rows does not wait on network before
  drawing the screen.
- A failed remote refresh does not remove or hide cached highlights.
- The drawer/rail highlight badge does not trigger network traffic.
- The app-open bookmark sync path is not delayed by highlight refresh.
- A successful full highlight refresh reconciles creates, updates, and deletions from
  the server.
- Reader-side annotation mutations still update the Highlights list reactively.
- Tests cover failure mid-refresh and stale-row deletion after success.

---

## 15. Stress Testing

A synthetic-data script can still be useful, but it should validate the revised
architecture rather than drive the design.

Recommended stress cases:

- 0 highlights
- 25 highlights
- 500 highlights
- 2,500 highlights
- 10,000 highlights if the test server can handle it
- Long highlight text and long notes
- Many highlights on one bookmark
- One highlight each across many bookmarks
- Deleted remote highlights after local cache exists
- Network failure after page 1 of many

The first functional stress target is 2,500 highlights:

- cached list appears immediately on second open,
- refresh progress is non-blocking,
- no timeout produces a blank list if cached rows exist,
- memory and UI responsiveness remain acceptable.

Do not build the stress script as part of this amendment unless the implementer needs
test data after the local-first refactor is underway.

---

## 16. Notes For A New Thread

Start by reading:

- `app/src/main/java/com/mydeck/app/domain/HighlightsRepository.kt`
- `app/src/main/java/com/mydeck/app/ui/highlights/HighlightsViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/highlights/HighlightsScreen.kt`
- `app/src/main/java/com/mydeck/app/io/db/dao/CachedAnnotationDao.kt`
- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`
- `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`
- `docs/archive/highlights-nav-drawer-list-spec.md`
- `docs/archive/highlights-list-reactivity-mini-spec.md`

The key implementation choice is whether to introduce `remote_annotation_ids` now.
Prefer adding it if doing the migration anyway; it keeps memory bounded and matches
the full bookmark sync pattern.

Avoid changing the existing video annotation fixes unless tests show they interact
with this refactor. This amendment is about the global Highlights list architecture.
