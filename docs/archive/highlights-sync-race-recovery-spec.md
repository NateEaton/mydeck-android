# Spec: Bookmark Sync Serialization and Highlight Recovery (DRAFT)

## Status

**DRAFT** on 2026-05-12.

This spec is intended for a follow-up implementation thread. It captures the root cause and
proposed fix for the build 141 tester report where global highlight refresh stopped at about
2,998 highlights and then repeatedly showed "Could not refresh highlights. Showing saved
highlights".

## Source Material

- Tester log bundle: `debug/mydeck-logs-2026-05-12-101127`
- Relevant reported behavior:
  - First post-auth content load also started loading highlights.
  - Highlight refresh stopped at about 2,998 and never progressed.
  - Adding/removing a new highlight reflected instantly.
  - Global refresh kept reporting a saved-highlights fallback.
- Relevant commits:
  - `5b4e961` Slice 5 diagnostics commit under test
  - `b7f547b` later guard for highlight bootstrap during initial sync
  - `aba5516` later sticky error UI change, not yet tester-verified

## Summary

The immediate failure in the logs is not malformed highlight content. It is a bookmark sync race
that leaves the local database missing bookmark rows that still exist on the server. Global
highlight reconciliation then receives remote annotations for those server bookmarks and fails
when inserting into `cached_annotation`, because `cached_annotation.bookmarkId` has a foreign key
to `bookmarks.id`.

Fix this at the source:

1. Prevent overlapping bookmark metadata syncs from sharing and corrupting deletion state.
2. Make deletion detection isolated per sync run and safe to abort.
3. Prevent global highlight reconciliation from running while bookmark metadata is unstable.
4. Add a defensive highlight orphan path that should almost never run. It should log loudly,
   attempt recovery if practical, continue the refresh, and show at most a non-sticky warning.

The defensive path in item 4 is containment, not normal control flow. In a healthy sync model,
there should never be a server bookmark with highlights that is missing locally after bookmark
sync has completed.

## Log Evidence

### Overlapping Full Syncs

The log shows initial sync starting first:

```text
05-11 20:15:53:574 D/LoadBookmarksWorker : LoadBookmarksWorker start [trigger=INITIAL, isInitialLoad=true]
05-11 20:15:53:592 D/LoadBookmarksWorker : Initial load: performing full sync to bootstrap bookmarks
```

Before that initial sync finishes, a separate full sync starts:

```text
05-11 20:16:12:714 D/FullSyncWorker : Start Work
05-11 20:16:12:722 D/FullSyncWorker : Performing full sync for deletion detection
```

Both paths call `BookmarkRepositoryImpl.performFullSync()`, which currently uses the shared
`remote_bookmark_ids` staging table:

- `BookmarkRepositoryImpl.performFullSync()` clears the table at the start.
- Each page inserts IDs into that same table.
- At the end, `BookmarkDao.removeDeletedBookmars()` deletes all local bookmarks absent from the
  shared staging table.
- The table is cleared again after deletion detection.

Because two full syncs overlap, the second run observes only a partial staging set and deletes
valid local bookmarks.

### Destructive Delete

The initial full sync completes correctly:

```text
05-11 20:16:26:255 I/BookmarkRepositoryImpl : Full sync complete: inserted 4149 bookmarks, deleted 0
05-11 20:16:26:257 I/LoadBookmarksWorker : Initial full sync: inserted 4149 bookmarks, deleted 0
```

Then the overlapping `FullSyncWorker` completes with a destructive partial delete:

```text
05-11 20:16:36:292 I/BookmarkRepositoryImpl : Deleted bookmarks: 2550
05-11 20:16:36:316 I/BookmarkRepositoryImpl : Full sync complete: inserted 4149 bookmarks, deleted 2550
```

The visible bookmark count drops from 4,149 to 1,599.

### Highlight Foreign Key Failure

Global highlight reconciliation then fails while inserting annotation rows:

```text
05-11 20:16:48:668 D/HighlightsRepositoryImpl : Global highlights reconciliation page received: reason=MANUAL_SYNC offset=3000 size=50 loadedBefore=3000
05-11 20:16:48:723 W/HighlightsRepositoryImpl : Global highlights reconciliation failed: reason=MANUAL_SYNC offset=3000 loaded=3000 cacheComplete=false
android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed
    at com.mydeck.app.io.db.dao.CachedAnnotationDao_Impl.insertAnnotations
    at com.mydeck.app.domain.HighlightsRepositoryImpl$reconcileAllAnnotations$2.invokeSuspend(HighlightsRepository.kt:393)
```

On manual retry, the failure happens much earlier:

```text
05-11 20:17:07:286 D/HighlightsRepositoryImpl : Global highlights reconciliation page received: reason=USER_RETRY offset=50 size=50 loadedBefore=50
05-11 20:17:07:308 W/HighlightsRepositoryImpl : Global highlights reconciliation failed: reason=USER_RETRY offset=50 loaded=50
android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed
```

This is expected after the local bookmark table has already been damaged: many server annotations
now point at locally missing bookmark parents.

## Root Cause

`remote_bookmark_ids` is treated as a global temporary table, but full syncs are allowed to run in
parallel through different worker paths.

The current workers use separate WorkManager identities:

- `LoadBookmarksWorker.UNIQUE_WORK_NAME = "LoadBookmarksSync"`
- `FullSyncWorker.UNIQUE_NAME_AUTO = "auto_full_sync_work"`
- `FullSyncWorker.UNIQUE_NAME_MANUAL = "manual_full_sync_work"`

Those unique names prevent duplicate work only within each lane. They do not prevent an initial
bookmark bootstrap, a manual full sync, and an automatic full sync from overlapping.

The later `b7f547b` guard prevents highlight reconciliation from starting before
`initial_sync_performed` is true. That is useful, but insufficient. In this log, the second full
sync keeps running after initial sync marks bootstrap complete, so highlights can still run while
bookmark deletion detection is unstable.

## Goals

1. Preserve the invariant that every server bookmark represented by the sync API exists locally
   after bookmark sync completes.
2. Prevent overlapping bookmark sync workers from corrupting shared deletion-detection state.
3. Make full-sync deletion detection safe even if future scheduling changes accidentally overlap
   sync work again.
4. Prevent global highlight reconciliation from failing permanently when it encounters an
   unexpected missing bookmark parent.
5. Avoid sticky or repeatedly re-shown highlight refresh errors for recoverable or contained
   partial-refresh conditions.
6. Add regression coverage for the large-library sequence observed in the logs.

## Non-Goals

- Do not change Readeck server highlight semantics.
- Do not attempt to support overlapping highlights locally in this fix.
- Do not remove the `cached_annotation.bookmarkId` foreign key. The foreign key correctly exposed
  the local data integrity problem.
- Do not make orphan-highlight skipping the primary solution. It is a final safety net.

## Proposed Implementation

### Workstream 1: Serialize Bookmark Metadata Syncs

Prevent `LoadBookmarksWorker` and `FullSyncWorker` from running bookmark metadata sync logic at the
same time.

Recommended approach:

1. Introduce a shared bookmark metadata sync gate.
2. Route initial bootstrap, app-open refresh, pull-to-refresh, manual full sync, forced full sync,
   and periodic full sync through that gate.
3. Ensure the gate covers all bookmark metadata mutation phases:
   - `performFullSync()`
   - `performDeltaSync()`
   - multipart metadata reload through `LoadBookmarksUseCase.execute(updatedIds = ...)`
   - deletion detection
   - updates to last sync/full-sync timestamps
4. Log when work is skipped, queued, or delayed because another bookmark metadata sync is active.

Implementation options:

- WorkManager-level: converge related one-time sync work onto a single unique bookmark metadata
  sync lane, with trigger/force flags in input data.
- Repository-level: add a process-wide sync coordinator used by all workers before they call
  repository sync methods.
- Best protection: do both lightweight scheduling serialization and storage-level run isolation
  from Workstream 2.

Do not rely only on `initial_sync_performed`. That flag means "bootstrap has completed at least
once", not "bookmark metadata is currently stable".

### Workstream 2: Isolate Full Sync Deletion Detection Per Run

Make `remote_bookmark_ids` safe if two full syncs ever overlap.

Recommended database change:

1. Add a `syncRunId` column to `remote_bookmark_ids`.
2. Use a composite primary key such as `(syncRunId, id)`.
3. Generate a unique `syncRunId` at the start of `performFullSync()`.
4. Insert remote IDs with that `syncRunId`.
5. Run deletion detection only against the current run:

```sql
DELETE FROM bookmarks
WHERE isLocalDeleted = 0
AND NOT EXISTS (
    SELECT 1
    FROM remote_bookmark_ids
    WHERE remote_bookmark_ids.syncRunId = :syncRunId
    AND remote_bookmark_ids.id = bookmarks.id
)
```

6. Clear only the current run's rows in `finally`.

Add a safety check before deletion:

- Track expected `totalCount` from response headers.
- Track fetched unique remote ID count for this `syncRunId`.
- If fetched unique count does not match expected `totalCount`, abort deletion detection and
  return a retryable sync failure.
- Log the mismatch with expected count, staged count, fetched page count, and sync run ID.

This would have prevented the `2550` false deletion in the tester log.

### Workstream 3: Stabilize Highlight Reconciliation Scheduling

Global highlight reconciliation must run only when bookmark metadata is stable.

Implementation requirements:

1. Before `HighlightsRepositoryImpl.reconcileAllAnnotations()` starts paging, acquire a shared
   bookmark-stability gate and hold it until the global annotation run has finished inserting
   pages, deleting stale annotation rows, and updating sync metadata. A point-in-time "is active"
   check is not enough, because a bookmark sync could start immediately after the check and prune
   parents while highlight rows are being written.
2. If a bookmark metadata sync is active:
   - do not set global highlight state to failed;
   - skip/defer with a debug log such as "bookmark metadata sync active";
   - allow the bookmark sync worker to request the backstop again when it completes.
3. Ensure a queued or just-started `FullSyncWorker` cannot mutate bookmark parents while global
   highlight reconciliation is writing pages.

If a shared sync coordinator is used, global highlight reconciliation should acquire the same
stability gate in a mode that prevents concurrent bookmark full/delta metadata mutation for the
whole reconciliation transaction sequence. Bookmark metadata syncs should acquire the exclusive
side of that gate before any metadata mutation or deletion detection. It is acceptable for global
highlight reconciliation to wait or skip; it is not acceptable for it to run against a bookmark
table that is actively being deletion-pruned.

### Workstream 4: Defensive Orphan Highlight Handling

This path should be rare. Treat it as an invariant violation, not as expected data.

Problem to contain:

- `GET /annotations` can return an annotation summary whose `bookmark_id` is not present in local
  `bookmarks`.
- In a healthy local cache this should not happen after bookmark sync completes.
- If it does happen, one bad annotation page should not permanently fail the whole global
  highlight refresh.

Recommended behavior:

1. For each highlight page, compute distinct `bookmark_id`s.
2. Query local bookmarks for those IDs before inserting `CachedAnnotationEntity` rows.
3. If any parent bookmarks are missing:
   - log a warning with reason, offset, missing bookmark count, annotation count, and a small
     sample of bookmark IDs and annotation IDs;
   - record that highlight reconciliation encountered an invariant violation;
   - do not run bookmark metadata backfill while holding the highlight stability gate.
4. Insert all annotations whose parent bookmarks now exist.
5. For annotations whose parent bookmark still does not exist:
   - skip those rows;
   - continue paging;
   - count them in a `skippedOrphanAnnotations` counter.
6. At the end of the run:
   - if pages completed and `skippedOrphanAnnotations == 0`, finish as normal;
   - if pages completed and `skippedOrphanAnnotations > 0`, finish as "completed with warnings",
     not failed.

Recovery/backfill flow:

The highlight reconciliation path must not attempt bookmark metadata repair while it holds the
bookmark-stability gate. If implementation chooses to recover missing parents, use a two-phase
flow:

1. Finish or abort the current highlight run after recording missing bookmark diagnostics.
2. Release the highlight stability gate.
3. Request a safe bookmark repair:
   - targeted metadata backfill for the missing bookmark IDs, if a clean API path already exists;
   - otherwise a forced full sync through the serialized bookmark metadata lane.
4. When bookmark repair completes, request the global highlight backstop again.

This avoids lock inversion between "highlight run needs stable bookmarks" and "bookmark repair
needs exclusive metadata mutation". It also keeps orphan handling a containment path rather than a
hidden metadata-sync side effect inside annotation paging.

UI behavior:

- Do not keep showing the saved-highlights error for this condition.
- Show a one-shot snackbar or transient warning only if annotations were actually skipped in the
  completed highlight run.
- Suggested message: "Some highlights were skipped because their bookmarks are not available
  locally."
- If adding a string resource, add English placeholders to all localized `strings.xml` files.

Metadata behavior:

- Do not leave global sync in a sticky failed state if all pages were fetched.
- Set `cacheComplete = true` for a run that fetched all annotation pages but skipped unrecoverable
  orphan annotations, so the app does not repeatedly retry and warn for the same contained
  condition.
- If a bookmark repair was requested because of skipped orphan annotations, the repair completion
  should request the global highlight backstop again; that follow-up run may replace the warning
  state with a clean success if all parents are restored.
- Store enough diagnostics for logs and future debugging:
  - skipped orphan annotation count
  - missing bookmark count
  - first failure offset
  - last warning timestamp
- Preserve diagnostics even when `cacheComplete = true` so the issue does not disappear from logs
  or future debugging.

Important: if this defensive path is hit after Workstreams 1 through 3, it likely means there is a
new bookmark sync correctness bug or an unexpected server-side API inconsistency. Logs should make
that obvious.

### Workstream 5: Clean Up Existing Damaged Local State

Users who hit the race may already have a local database missing thousands of bookmarks.

Recommended recovery:

1. After the fix, a forced full sync should repopulate missing bookmarks safely.
2. If global highlight reconciliation detects orphan annotations, request or schedule a safe
   bookmark full sync/backfill after releasing the highlight stability gate, then request the
   highlight backstop again after bookmark repair completes.
3. Avoid requiring account logout/login to repair the local cache.

## Acceptance Criteria

1. Initial login sync and automatic/manual full sync cannot run `performFullSync()` concurrently.
2. A full sync cannot delete local bookmarks unless the current sync run has staged the complete
   remote ID set.
3. `remote_bookmark_ids` staging from one sync run cannot affect deletion detection for another
   sync run.
4. Global highlight reconciliation does not start while bookmark metadata sync is actively
   mutating the bookmark table, and once started it holds a stability gate that prevents bookmark
   metadata mutation until the highlight run has finished writing pages, deleting stale annotation
   rows, and updating sync metadata.
5. If highlight reconciliation receives annotations for missing bookmark parents, it logs an
   invariant warning, skips only unrecoverable orphan annotations, continues fetching pages, and
   does not enter a sticky failed state.
6. The normal path for a large account with 4,000+ bookmarks and 4,000+ highlights completes with
   all valid highlights cached.
7. A tester who previously hit the `2550` false delete can recover via a safe full sync.
8. Orphan-highlight repair, if requested, runs only after highlight reconciliation releases the
   bookmark-stability gate and completion of that repair schedules a follow-up highlight backstop.

## Regression Tests

Add focused tests around the failure mode instead of only UI-level coverage.

### Bookmark Sync Tests

- Two overlapping full sync attempts cannot share deletion staging.
- Full sync deletion detection aborts if staged remote ID count is lower than API `totalCount`.
- Full sync with complete staged IDs deletes only truly absent local bookmarks.
- Initial `LoadBookmarksWorker` and `FullSyncWorker` cannot both execute bookmark metadata mutation
  at the same time.

### Highlight Sync Tests

- Global highlight reconciliation skips or defers when bookmark metadata sync is active.
- A page with all valid parent bookmarks inserts as before.
- A page with one missing parent bookmark:
  - logs warning;
  - inserts valid annotations;
  - skips only the orphan annotation;
  - completes paging;
  - does not set `HighlightsSyncState.Failed`.
- A hard network or HTTP failure still sets failed state/backoff as before.

### Worker Scheduling Tests

- Manual full sync does not replace or overlap an in-flight initial bootstrap in a way that can
  corrupt local bookmark state.
- Periodic full sync does not overlap app-open or pull-to-refresh metadata sync.

### Migration Tests

- Migrating from the previous schema recreates `remote_bookmark_ids` with `syncRunId` and `id`.
- The migrated `remote_bookmark_ids` table has a composite primary key on `(syncRunId, id)`.
- Existing stale staging rows from a pre-fix database do not participate in any future deletion
  detection run.

## Implementation Notes

- Keep `cached_annotation.bookmarkId` foreign key intact.
- Prefer adding small DAO helpers over ad hoc raw SQL in repository code.
- Changing `remote_bookmark_ids` requires a normal Room migration:
  - bump the database schema version;
  - update `RemoteBookmarkIdEntity` to use a composite primary key;
  - recreate the table in migration SQL because SQLite cannot alter a primary key in place;
  - update DAO methods to insert, count, query, delete, and run deletion detection by `syncRunId`;
  - update migration validation tests and exported schemas.
- Keep logging structured and grep-friendly:
  - `syncRunId`
  - `reason`
  - `offset`
  - `loaded`
  - `expectedTotal`
  - `stagedTotal`
  - `missingBookmarkCount`
  - `skippedOrphanAnnotations`
- Do not run Gradle verification tasks in parallel.
- If user-visible strings are added for a snackbar/warning, update every localized
  `strings.xml` with English placeholders and document user-visible behavior in
  `app/src/main/assets/guide/en/` if the final UX warrants it.

## Suggested Implementation Order

1. Add sync-run isolation and deletion safety checks.
2. Add bookmark metadata sync serialization.
3. Add highlight scheduling stability gate.
4. Add defensive orphan-highlight handling and warning UX.
5. Add damaged-state recovery behavior.
6. Add/expand tests.

The first two items are the critical correctness fix. The orphan-highlight fallback should be
implemented after the parent-bookmark invariant is protected, so it remains a safety net rather
than a mask for destructive bookmark sync behavior.
