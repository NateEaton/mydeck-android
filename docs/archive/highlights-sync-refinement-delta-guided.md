# Highlights Sync Refinement: Delta-Guided Annotation Reconciliation

**Status:** Draft refinement for implementation
**Date:** 2026-05-10
**Refines:**
- `docs/archive/highlights-list-local-first-scalability-amendment.md`
- `docs/archive/highlights-list-reactivity-mini-spec.md`
- `docs/specs/offline-annotation-crud-spec.md`

---

## 1. Summary

The local-first Highlights architecture remains correct: the Highlights screen,
drawer, and navigation rail badge should render from `cached_annotation` and should
not block on a global annotation crawl.

The next refinement is to make synchronization smarter. The current global refresh
still tries to crawl `GET /bookmarks/annotations` from screen-driven code paths. Logs
from a large-highlight account show repeated refresh failures caused by coroutine
cancellation while navigating, not by server HTTP failures. Opening the Highlights
screen repeatedly can therefore restart the expensive crawl before it ever records a
successful sync.

Readeck's bookmark delta feed also appears to emit bookmark "update" records for
bookmarks that were merely opened in the Readeck UI. That means bookmark delta
updates are too noisy to prove annotation changes. However, they are still useful as
cheap hints: if a bookmark was touched remotely, checking that single bookmark's
annotations is far cheaper than crawling every annotation.

This refinement introduces a tiered sync model:

- `cached_annotation` remains the UI and badge source of truth.
- Bookmark delta `updatedIds` trigger bounded, bookmark-scoped annotation checks.
- Direct MyDeck annotation mutations update `cached_annotation` immediately.
- Returning from a reader/detail flow reconciles that bookmark, not the whole list.
- Full global annotation reconciliation runs infrequently in background sync as a
  completeness backstop.

---

## 2. Problem Statement

The first local-first patch fixed the worst user-visible behavior by showing cached
highlights immediately. It did not fully solve sync fragility because the global
refresh can still be:

- triggered too often by screen opens,
- cancelled by the Highlights `ViewModel` lifecycle,
- expensive for thousands of highlights because it uses many serialized pages,
- repeated after every cancelled run because no successful refresh timestamp is
  recorded,
- noisy in the UI because every page can update Room and force regrouping of a large
  list.

Meanwhile, relying solely on bookmark delta sync would be incorrect because the delta
feed appears to include false positives. A bookmark can be reported as updated after
being opened in Readeck, even if no highlight changed. The app should use those IDs
as a signal to inspect annotations, not as proof that annotations changed.

---

## 3. Goals

- Keep Highlights list and badge local-first.
- Stop screen lifecycle from cancelling global annotation sync.
- Avoid global annotation crawls on every Highlights open.
- Use bookmark delta `updatedIds` as lightweight candidates for annotation checks.
- Keep the selected/visited bookmark current after returning from the reader.
- Preserve correctness for annotation create, update, and delete.
- Keep global reconciliation as an infrequent backstop for missed or external
  changes.
- Bound network and database work during app open and periodic sync.
- Add clear debug logs that explain which sync lane ran and why.

---

## 4. Non-Goals

- Do not make the global Highlights list API-only.
- Do not make annotation sync depend on offline article download settings.
- Do not fetch annotations for every bookmark after every bookmark full sync.
- Do not make Readeck bookmark delta updates the authoritative annotation-change
  signal.
- Do not introduce a user-visible setting in this refinement unless product scope
  changes.
- Do not require device/emulator-only verification tasks.

---

## 5. Architecture Overview

Highlights sync should have four lanes.

### 5.1 Direct Local Mutation Lane

When MyDeck creates, updates, or deletes an annotation through Readeck's annotation
API, the affected `cached_annotation` rows should be updated immediately after the
server mutation succeeds.

Expected behavior:

- Create: insert the returned annotation row into `cached_annotation`.
- Update: update color, note, text, and any other returned fields for the annotation.
- Delete: delete that annotation row locally after server success.
- Failure: leave local cache unchanged unless the existing optimistic mutation system
  explicitly models pending annotation actions.

This lane keeps same-device edits reactive without waiting for any refresh job.

### 5.2 Reader/Selected Bookmark Lane

When a user navigates from Highlights to a bookmark, or returns from a bookmark after
interacting with annotation UI, reconcile that bookmark's annotations only.

Use:

```text
GET /bookmarks/{bookmarkId}/annotations
```

On success, replace/prune `cached_annotation` rows for that bookmark. On failure,
leave existing cached rows intact.

This path is allowed to bypass normal per-bookmark freshness throttles because the
user can immediately see whether the selected highlight still exists.

### 5.3 Delta-Guided Bookmark Annotation Lane

After bookmark delta sync reports `updatedIds`, enqueue bookmark-scoped annotation
checks for those IDs.

Treat `updatedIds` as "bookmarks touched remotely", not "annotations changed".
False positives are expected and acceptable because each check is only one bookmark's
annotation endpoint.

This lane should run after successful bookmark delta processing so bookmark metadata
is already present locally for the highlights join.

### 5.4 Global Reconciliation Lane

Run `GET /bookmarks/annotations` as a background full reconciliation only when due:

- first bootstrap of the highlights cache,
- after an incomplete or never-successful global reconciliation,
- manual Highlights retry or pull-to-refresh,
- periodic background backstop, for example every 12-24 hours,
- optionally after a very large delta batch where per-bookmark checks would be more
  expensive than a global crawl.

This job must not be owned by `HighlightsViewModel` or any screen coroutine scope.
Use application scope or WorkManager.

---

## 6. Trigger Matrix

| Trigger | Work To Run | Blocking UI? | Notes |
| --- | --- | --- | --- |
| App open normal sync | Bookmark delta sync, then per-bookmark annotation checks for delta `updatedIds` | No | Do not run global annotation crawl unless due or cache incomplete. |
| Periodic background sync | Bookmark sync, delta-guided annotation checks, optional due global reconciliation | No | Global reconciliation has separate freshness policy. |
| Highlights screen open | Render Room immediately; maybe enqueue due sync | No | Should never force a full crawl just for display. |
| Highlights manual retry | Enqueue global reconciliation | No, except empty cache can show progress/error | User explicitly asked for a refresh. |
| Navigate from highlight to reader | Reconcile that bookmark on return or when safe | No | High priority; bypass per-bookmark throttle. |
| MyDeck annotation create/update/delete | Apply local `cached_annotation` mutation after server success | No | Do not wait for sync. |
| Readeck/native UI annotation change | Detected either by delta-guided bookmark check or later global reconciliation | No | Delta IDs are hints; global is backstop. |

---

## 7. Data And Metadata

### 7.1 Existing Tables

Keep using `cached_annotation` as the canonical local table for the Highlights UI and
badge.

Keep `remote_annotation_ids` for global reconciliation, but ensure it cannot cause
stale deletion after a failed or cancelled crawl. The current simple table is
acceptable if only one global job can run and stale deletion occurs only after a
successful crawl.

If implementation complexity remains manageable, prefer adding a run identifier:

```kotlin
@Entity(tableName = "remote_annotation_ids")
data class RemoteAnnotationIdEntity(
    @PrimaryKey val id: String,
    val syncRunId: String
)
```

Then stale deletion can be scoped to the completed run:

```sql
DELETE FROM cached_annotation
WHERE NOT EXISTS (
    SELECT 1
    FROM remote_annotation_ids
    WHERE remote_annotation_ids.id = cached_annotation.id
      AND remote_annotation_ids.syncRunId = :syncRunId
)
```

The run-id version is more robust, but not required if the repository guarantees
single-flight global sync and clears the table at safe boundaries.

### 7.2 Highlights Sync Metadata

Persist sync metadata outside the screen lifecycle. This can be in `SettingsDataStore`
or a small Room metadata table.

Recommended fields:

```kotlin
data class HighlightsSyncMetadata(
    val lastGlobalAttemptAt: Instant?,
    val lastGlobalSuccessAt: Instant?,
    val lastGlobalFailureAt: Instant?,
    val globalFailureCount: Int,
    val globalBackoffUntil: Instant?,
    val cacheComplete: Boolean
)
```

`cacheComplete` means a full global reconciliation has completed successfully at
least once for the current account/database. It should be false after account switch,
logout, or destructive cache reset.

### 7.3 Per-Bookmark Annotation Check Metadata

To avoid noisy delta updates causing repeated annotation calls for the same bookmark,
track recent per-bookmark checks.

Recommended fields:

```kotlin
data class BookmarkAnnotationSyncMetadata(
    val bookmarkId: String,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val failureCount: Int,
    val backoffUntil: Instant?
)
```

This can be implemented as a Room table or a bounded in-memory cache plus persisted
timestamps if app-open frequency makes persistence worthwhile. Prefer Room if the
logic is used by WorkManager.

---

## 8. Repository And Scheduler API

The repository should expose local observation plus non-blocking sync requests.

Suggested shape:

```kotlin
interface HighlightsRepository {
    fun observeHighlights(): Flow<List<HighlightSummary>>
    fun observeHighlightCount(): Flow<Int>
    fun observeSyncState(): StateFlow<HighlightsSyncState>

    fun requestGlobalReconciliation(reason: HighlightsGlobalSyncReason): SyncRequestHandle
    fun requestBookmarkAnnotationChecks(
        bookmarkIds: Collection<String>,
        reason: BookmarkAnnotationSyncReason,
        priority: SyncPriority = SyncPriority.Normal
    ): SyncRequestHandle

    suspend fun reconcileBookmarkAnnotationsNow(
        bookmarkId: String,
        reason: BookmarkAnnotationSyncReason
    ): Result<BookmarkAnnotationReconcileResult>
}
```

Reasons:

```kotlin
enum class HighlightsGlobalSyncReason {
    FIRST_BOOTSTRAP,
    PERIODIC_BACKSTOP,
    USER_RETRY,
    CACHE_INCOMPLETE,
    LARGE_DELTA_BATCH
}

enum class BookmarkAnnotationSyncReason {
    BOOKMARK_DELTA_HINT,
    HIGHLIGHT_NAVIGATION_RETURN,
    READER_REFRESH,
    CONTENT_PACKAGE_ENRICHMENT,
    MANUAL_BOOKMARK_REFRESH
}

enum class SyncPriority {
    Normal,
    UserVisible
}
```

The exact API can differ, but the behavioral requirements are:

- UI observations are pure Room flows.
- Request methods return quickly and do not perform long crawls in caller scope.
- Global sync is single-flight.
- Per-bookmark checks are deduped by bookmark ID.
- User-visible bookmark checks can bypass normal freshness throttles.
- Failures leave existing cached rows intact.

---

## 9. App Scope Or WorkManager

Prefer WorkManager for work that may continue after the app leaves foreground or that
is naturally part of background sync:

- global annotation reconciliation,
- batches of delta-guided bookmark annotation checks,
- periodic backstop runs.

An application-scoped coroutine is acceptable for short user-visible checks:

- selected bookmark reconciliation on reader return,
- immediate post-mutation cache updates,
- small batches after an app-open delta sync if existing sync worker infrastructure
  already owns the lifecycle.

Do not let `HighlightsViewModel.viewModelScope` own global reconciliation. The screen
may observe sync state and request work, but the job lifetime must be independent of
the screen.

---

## 10. Bookmark Sync Integration

### 10.1 Delta Sync Path

Current bookmark sync already obtains `updatedIds` from `performDeltaSync()`.

After delta sync succeeds and updated bookmark metadata has been fetched or persisted:

```kotlin
if (updatedIds.isNotEmpty()) {
    highlightsRepository.requestBookmarkAnnotationChecks(
        bookmarkIds = updatedIds,
        reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT
    )
}
```

Run this after bookmark metadata load so a new annotation row can join to a local
bookmark row for display.

If metadata reload fails, skip annotation checks for that run. The next sync can try
again.

### 10.2 Full Bookmark Sync Path

Do not enqueue per-bookmark annotation checks for every bookmark after a full bookmark
sync. That would be worse than the current global annotation crawl.

After full bookmark sync:

- if highlights cache is incomplete, enqueue global reconciliation;
- else if global reconciliation is due by the highlights-specific schedule, enqueue
  global reconciliation;
- else do nothing for annotations.

### 10.3 App Open

App open should follow the normal sync path:

1. Run bookmark delta sync if due.
2. Reload changed bookmark metadata if delta returned updates.
3. Enqueue per-bookmark annotation checks for the delta `updatedIds`.
4. Enqueue global annotation reconciliation only if due or cache is incomplete.

The drawer/rail badge should continue to observe Room and should not trigger any of
these network calls directly.

---

## 11. Per-Bookmark Reconciliation Algorithm

For a set of candidate bookmark IDs:

```kotlin
suspend fun reconcileBookmarkAnnotations(bookmarkId: String, force: Boolean): Result<Unit> {
    if (!force && !bookmarkCheckDue(bookmarkId)) return Result.success(Unit)

    markBookmarkAnnotationAttempt(bookmarkId)

    val response = readeckApi.getAnnotations(bookmarkId)
    if (!response.isSuccessful) {
        recordBookmarkAnnotationFailure(bookmarkId, response.code())
        return Result.failure(HttpException(response))
    }

    val remote = response.body().orEmpty()
    val entities = remote.map { dto ->
        CachedAnnotationEntity(
            id = dto.id,
            bookmarkId = bookmarkId,
            text = dto.text,
            color = dto.color.takeIf { it.isNotBlank() } ?: "yellow",
            note = dto.note.takeIf { it.isNotBlank() },
            created = dto.created
        )
    }

    cachedAnnotationDao.replaceAnnotationsForBookmark(bookmarkId, entities)
    recordBookmarkAnnotationSuccess(bookmarkId, changed = trueOrFalse)
    return Result.success(Unit)
}
```

Important details:

- Only prune rows for a bookmark after the per-bookmark endpoint succeeds.
- If the endpoint fails or times out, preserve existing cached highlights.
- An empty successful response means the bookmark currently has no annotations; local
  rows for that bookmark should be removed.
- If the bookmark is locally deleted, skip the check or delete local annotations via
  existing bookmark-deletion cascade/cleanup.
- Compute and log whether the reconcile actually changed local rows. This helps
  distinguish noisy bookmark delta hints from real annotation changes.

Recommended change detection:

```kotlin
val before = cachedAnnotationDao.getAnnotationsForBookmark(bookmarkId)
val beforeFingerprint = before.annotationFingerprint()
val afterFingerprint = entities.annotationFingerprint()
val changed = beforeFingerprint != afterFingerprint
```

Use stable fields: annotation ID, text, color, note, created.

---

## 12. Global Reconciliation Algorithm

Global reconciliation remains similar to the local-first amendment but must be
scheduled independently from screens.

Pseudo-flow:

```kotlin
suspend fun reconcileAllAnnotations(reason: HighlightsGlobalSyncReason): Result<Unit> {
    if (!globalMutex.tryLock()) return Result.success(Unit)
    val runId = randomSyncRunId()

    try {
        markGlobalAttempt(reason)
        syncState.value = HighlightsSyncState.Running(loadedCount = 0)
        prepareRemoteAnnotationIds(runId)

        var offset = 0
        var loaded = 0

        while (true) {
            val page = readeckApi.getAnnotationSummaries(
                limit = pageSize,
                offset = offset
            ).successfulBodyOrThrow()

            database.performTransaction {
                cachedAnnotationDao.insertAnnotations(page.map { it.toCachedEntity() })
                cachedAnnotationDao.insertRemoteAnnotationIds(
                    page.map { RemoteAnnotationIdEntity(it.id, runId) }
                )
            }

            loaded += page.size
            syncState.value = HighlightsSyncState.Running(loadedCount = loaded)

            if (page.size < pageSize) break
            offset += pageSize
        }

        database.performTransaction {
            cachedAnnotationDao.removeAnnotationsMissingFromRemote(runId)
            cachedAnnotationDao.clearRemoteAnnotationIds(runId)
        }

        markGlobalSuccess(reason, loaded)
        syncState.value = HighlightsSyncState.Idle
        return Result.success(Unit)
    } catch (e: Exception) {
        markGlobalFailure(reason, e)
        syncState.value = HighlightsSyncState.Failed("Could not refresh highlights", e)
        return Result.failure(e)
    } finally {
        globalMutex.unlock()
    }
}
```

Requirements:

- Do not clear `cached_annotation` before the crawl succeeds.
- Delete stale rows only after every remote page succeeds.
- A cancellation/failure must not delete rows.
- The job must survive navigation away from Highlights.
- Use a freshness throttle and backoff.
- Consider increasing page size only after testing against Readeck behavior.
- Consider batching UI-visible Room writes if page-by-page emissions cause list churn.

---

## 13. Freshness, Throttling, And Backoff

Recommended initial defaults:

- Per-bookmark delta-hint checks: skip if the same bookmark succeeded in the last
  5-15 minutes.
- Per-bookmark user-visible checks: do not skip solely because of freshness.
- Per-bookmark failure backoff: exponential backoff up to a modest cap, for example
  15-60 minutes.
- Global screen-open request: do not run unless cache is incomplete or the global
  backstop is due.
- Global periodic backstop: 12-24 hours.
- Global failure backoff: exponential backoff, but manual retry bypasses it.
- Large delta batch threshold: if a delta returns more than a configured count, for
  example 100-250 IDs, prefer scheduling a global reconciliation if due or process
  the bookmark checks in bounded chunks.

The exact values can be tuned, but the invariant is that repeated navigation should
not repeatedly start large network jobs.

---

## 14. Sync State And UI

Highlights UI should keep the same local-first rules:

- The list renders from Room immediately.
- Refresh indicators are non-blocking.
- Failed refresh with cached rows shows saved rows.
- Empty cache plus failed refresh can show full-screen retry.
- Badge count observes local Room count.

If the repository exposes more detailed sync state, distinguish:

- `globalRunning`
- `bookmarkChecksRunning`
- `lastFailure`
- `loadedCount`
- `pendingBookmarkCheckCount`

Do not expose noisy per-bookmark checks as a disruptive screen-level loading state.
A subtle "refreshing" indicator is enough when the user is on Highlights.

---

## 15. Logging Requirements

Add concise debug logs for each sync lane. Logs should answer:

- Why did sync start?
- Was it global or bookmark-scoped?
- Was work skipped by throttle/backoff?
- How many delta IDs were received?
- How many bookmark annotation checks were enqueued, skipped, succeeded, failed, and
  changed local rows?
- Did a global reconciliation complete, fail, or get skipped?

Recommended log examples:

```text
Bookmark delta sync annotation hints: updatedIds=17 reason=APP_OPEN
Bookmark annotation checks enqueued: reason=BOOKMARK_DELTA_HINT requested=17 enqueued=9 skippedRecent=8
Bookmark annotation check started: bookmarkId=... reason=BOOKMARK_DELTA_HINT force=false
Bookmark annotation check succeeded: bookmarkId=... remote=3 previous=2 changed=true durationMs=...
Bookmark annotation check failed: bookmarkId=... code=503 durationMs=...
Global highlights reconciliation skipped: reason=SCREEN_OPEN cacheComplete=true lastSuccess=...
Global highlights reconciliation started: reason=PERIODIC_BACKSTOP pageSize=...
Global highlights reconciliation succeeded: reason=... loaded=... staleDeleted=... durationMs=...
```

For production privacy/noise, consider truncating IDs or logging full IDs only in
debug builds. The immediate tester builds can log full bookmark IDs if useful.

---

## 16. Tests

Add focused unit tests around scheduler/repository behavior.

Repository tests:

- Per-bookmark reconcile inserts a newly added remote annotation.
- Per-bookmark reconcile updates changed note/color/text.
- Per-bookmark reconcile deletes stale rows only after successful empty response.
- Per-bookmark reconcile failure preserves existing rows.
- Per-bookmark check is skipped when recently successful and not forced.
- User-visible per-bookmark check bypasses freshness throttle.
- Duplicate bookmark IDs are deduped.

Bookmark sync integration tests:

- Delta sync with `updatedIds` enqueues annotation checks after metadata reload.
- Delta sync with no updated IDs enqueues no annotation checks.
- Failed metadata reload does not enqueue annotation checks.
- Full bookmark sync does not enqueue checks for all bookmarks.
- Full bookmark sync enqueues global reconciliation only when cache is incomplete or
  global backstop is due.

Global reconciliation tests:

- Mid-refresh failure preserves cached rows.
- Successful full crawl deletes stale rows.
- Cancellation does not delete stale rows.
- Screen/ViewModel cancellation does not cancel repository/worker-owned global sync.
- Global sync is skipped when freshness throttle says it is not due.
- Manual retry bypasses global freshness throttle.

UI/ViewModel tests:

- Cached highlights remain visible while bookmark checks run.
- Cached highlights remain visible while global reconciliation runs.
- Cached highlights remain visible after per-bookmark/global failure.
- Empty cache plus failed refresh shows retry/error state.
- Badge count is driven from Room and does not trigger network refresh.

---

## 17. Implementation Slices

### Slice 1: Lifecycle And Sync Metadata

- Move global reconciliation ownership out of `HighlightsViewModel`.
- Add persisted global sync metadata.
- Preserve current local-first UI behavior.
- Add logs showing skipped/due global reconciliation decisions.

### Slice 2: Bookmark-Scoped Reconciliation

- Add repository API for reconciling one bookmark or a set of bookmark IDs.
- Implement success/failure-safe per-bookmark replace/prune.
- Add per-bookmark freshness/backoff metadata.
- Add focused DAO/repository tests.

### Slice 3: Bookmark Sync Integration

- After successful delta sync and metadata reload, enqueue annotation checks for
  delta `updatedIds`.
- Ensure full bookmark sync does not check every bookmark.
- Add integration/unit tests for delta and full sync paths.

### Slice 4: Global Backstop Scheduling

- Wire global reconciliation into app-open/periodic/manual sync as an independently
  throttled background job.
- Keep manual Highlights retry as an explicit global refresh request.
- Add tests for due/not-due decisions and cancellation safety.

### Slice 5: UI Polish And Diagnostics

- Keep list/badge local-first.
- Add non-disruptive sync-state display if needed.
- Add debug logs for counts, skips, and changed rows.
- Document user-visible behavior in the English guide if UI copy/behavior changes.

---

## 18. Verification

Run Gradle tasks serially after implementation:

```bash
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Also perform a manual sync test:

1. Clear/export logs.
2. Open a bookmark in Readeck without changing annotations.
3. Run MyDeck delta sync.
4. Confirm delta reports an updated bookmark.
5. Confirm bookmark annotation check runs and logs `changed=false`.
6. Add, edit, and delete an annotation from Readeck.
7. Run MyDeck delta sync after each action.
8. Confirm bookmark annotation check logs `changed=true` and Room count/list update
   without a global crawl.
9. Open Highlights repeatedly and confirm no full global crawl restarts on every
   screen open.

---

## 19. Open Questions

- Does Readeck emit bookmark delta updates for annotation edits and deletes, or only
  for opens/adds? The architecture works either way, but this determines how much
  freshness can rely on delta hints before the global backstop runs.
- What page size does `GET /bookmarks/annotations` reliably support for large
  accounts?
- Should bookmark annotation check metadata live in Room or DataStore?
- Should WorkManager own all annotation sync work, or should short user-visible
  bookmark checks stay in application-scoped coroutines?
- Should full IDs be logged only in debug builds?
