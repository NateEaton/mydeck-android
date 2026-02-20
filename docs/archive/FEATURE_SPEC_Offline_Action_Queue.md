# Revised Feature Spec: Offline Action Queue

## 1. Overview
Currently, state changes (Read Progress, Archive, Favorite, Delete) fail immediately if the device is offline. This feature introduces a local "Pending Actions" queue to enable an **Offline-First** experience.

**Core Philosophy:**
*   **Optimistic UI:** The UI updates immediately, regardless of network state.
*   **Last Write Wins:** Local user actions supersede conflicting server state during sync.
*   **Coalescing:** Multiple local updates to the same field are merged to minimize network chatter.

## 2. Scope (Phase 1)
**Included:**
*   Read Progress (scrolling position)
*   Mark as Read / Unread
*   Favorite / Unfavorite
*   Archive / Unarchive
*   Delete Bookmark

**Excluded (Phase 2):**
*   Create Bookmark (Requires handling temporary local IDs vs. server UUIDs). Note: The Unified Add Bookmark spec (Phase 2) introduces a standalone `CreateBookmarkWorker` as an interim solution. Once the queue supports `CREATE` in Phase 2, that worker should be migrated to use this queue's `ActionSyncWorker`.
*   Edit Labels (Requires complex diffing logic).

---

## 3. Database Changes

### 3.1 New Entity: `PendingActionEntity`
We use the Command Pattern to store actions. The `payload` field stores serialized JSON data specific to the action type.

```kotlin
@Entity(tableName = "pending_actions",
        indices = [Index(value = ["bookmarkId", "actionType"])]) // Index for fast coalescing lookups
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookmarkId: String,
    val actionType: ActionType,
    val payload: String?, // JSON serialized via kotlinx.serialization (see §4.1)
    val createdAt: Instant
)

enum class ActionType {
    UPDATE_PROGRESS,
    TOGGLE_FAVORITE,
    TOGGLE_ARCHIVE,
    TOGGLE_READ,
    DELETE
}
```

### 3.2 Update Entity: `BookmarkEntity`
Support "Soft Deletion" so the bookmark is hidden from the UI immediately but remains in the DB until the server confirms deletion.

```kotlin
// In BookmarkEntity.kt
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    // ... existing fields
    val isLocalDeleted: Boolean = false // New Column, default false
)
```

### 3.3 DAO Updates (`BookmarkDao`)
All UI-facing queries (List, Detail, Search) must be updated to filter out soft-deleted items.

```sql
SELECT * FROM bookmarks WHERE isLocalDeleted = 0 ...
```

**Scope of query changes:** Every `@Query` in `BookmarkDao` that returns bookmarks for display must add the `isLocalDeleted = 0` filter. This includes:
*   List queries (My List, Archive, Favorites)
*   Search queries
*   Detail/single-bookmark queries
*   Count queries used for badge numbers in the nav drawer

Queries used internally by the sync process (e.g., `getAllBookmarkIds()` for full-sync deletion detection) should **not** filter on `isLocalDeleted`, since those need to see the full local state.

---

## 4. Logic & Rules of the Road

### 4.1 Payload Contract
Payloads are serialized using **`kotlinx.serialization`** (the project's existing JSON library). Define `@Serializable` data classes:

```kotlin
@Serializable
data class ProgressPayload(val progress: Int, val timestamp: Instant)

@Serializable
data class TogglePayload(val value: Boolean) // Used for Favorite, Archive, Read

// DELETE actions have no payload (null)
```

Use `Json.encodeToString()` / `Json.decodeFromString()` from the existing `kotlinx.serialization.json` dependency. No additional libraries needed.

### 4.2 Action Coalescing (The "Debounce" Rule)
To prevent queue bloat and network waste, we apply **Last Write Wins** logic before inserting into the queue.

*   **Logic:** Before inserting a new action, query `pending_actions` for an existing row with the same `bookmarkId` and `actionType`.
    *   **If exists:** Update the `payload` of the existing row. *Do not create a new row.*
    *   **If not exists:** Insert a new row.
*   **Example:** User taps "Favorite" (True) -> "Unfavorite" (False) -> "Favorite" (True) while offline.
    *   *Result:* Only **one** row exists in `pending_actions` for `TOGGLE_FAVORITE` with payload `true`.

### 4.3 Delete Supremacy
A deletion action renders all previous pending updates for that bookmark irrelevant.

*   **Logic:** When queuing a `DELETE` action:
    1.  Delete **all** existing rows in `pending_actions` matching that `bookmarkId` (Progress, Toggles, etc.).
    2.  Insert the `DELETE` action.
    3.  Set `isLocalDeleted = true` on the `BookmarkEntity`.

### 4.4 Conflict Resolution
**Strategy:** Local Wins (Overwrite).
*   If the user modifies a bookmark offline, and the server state has changed in the background (e.g., via web dashboard), the offline action overwrites the server state upon reconnection. This is standard behavior for read-later apps (Pocket style).

### 4.5 Transaction Boundaries
**Critical:** The local DB update and the pending action queue insert/update must be wrapped in a single Room `@Transaction` (or `withTransaction` block) to ensure atomicity. If the app crashes between the local update and the queue insert, the UI would show a changed state with no pending sync to back it up.

```kotlin
suspend fun toggleFavorite(bookmarkId: String, isFavorite: Boolean) {
    database.withTransaction {
        // 1. Optimistic Local Update
        bookmarkDao.updateIsMarked(bookmarkId, isFavorite)
        // 2. Coalescing Queue Logic
        upsertPendingAction(bookmarkId, ActionType.TOGGLE_FAVORITE, TogglePayload(isFavorite))
    }
    // 3. Trigger Sync (outside transaction)
    ActionSyncWorker.enqueue(context)
}
```

---

## 5. Repository Implementation

`BookmarkRepositoryImpl` serves as the orchestrator. It acts on the local DB first, then schedules the sync.

### 5.1 Current State (What Changes)

The existing repository methods follow an **API-first** pattern:

```
Current: API call → if success → local DB update → return result
New:     Local DB update + queue insert (in transaction) → trigger worker → return immediately
```

Specifically, these methods in `BookmarkRepositoryImpl` must be rewritten:
*   `updateBookmark()` (lines ~323-399) — currently calls `readeckApi.editBookmark()` then updates locally on success
*   `deleteBookmark()` (lines ~401-431) — currently calls `readeckApi.deleteBookmark()` then deletes locally on success
*   `updateReadProgress()` (lines ~484-523) — currently calls `readeckApi.editBookmark()` then updates locally on success

After the refactor, these methods will:
1.  Update the local DB optimistically
2.  Insert/coalesce a pending action
3.  Enqueue the `ActionSyncWorker`
4.  Return immediately (no `UpdateResult.Error` for network failures)

### 5.2 Logic Flow (Pseudo-code)

```kotlin
suspend fun toggleFavorite(bookmarkId: String, isFavorite: Boolean) {
    database.withTransaction {
        // 1. Optimistic Local Update
        bookmarkDao.updateIsMarked(bookmarkId, isFavorite)

        // 2. Coalescing Queue Logic
        val existingAction = pendingActionDao.find(bookmarkId, ActionType.TOGGLE_FAVORITE)
        val payload = json.encodeToString(TogglePayload(isFavorite))

        if (existingAction != null) {
            pendingActionDao.updatePayload(existingAction.id, payload)
        } else {
            pendingActionDao.insert(PendingActionEntity(..., payload = payload))
        }
    }

    // 3. Trigger Sync
    ActionSyncWorker.enqueue(context)
}

suspend fun deleteBookmark(bookmarkId: String) {
    database.withTransaction {
        // 1. Optimistic Soft Delete
        bookmarkDao.softDelete(bookmarkId) // sets isLocalDeleted = 1

        // 2. Delete Supremacy Logic
        pendingActionDao.deleteAllForBookmark(bookmarkId)

        // 3. Queue Delete Action
        pendingActionDao.insert(PendingActionEntity(..., actionType = DELETE))
    }

    // 4. Trigger Sync
    ActionSyncWorker.enqueue(context)
}
```

---

## 6. Background Sync: `ActionSyncWorker`

### 6.1 Configuration
*   **Unique Work Name:** `OfflineActionSync`
*   **Policy:** `ExistingWorkPolicy.APPEND_OR_REPLACE` (not `APPEND`, which is deprecated in WorkManager 2.6+).
    *   *Why APPEND_OR_REPLACE?* If the worker is currently running and a new action is queued, we want the worker to run again immediately after it finishes to pick up the new data. Unlike `APPEND`, `APPEND_OR_REPLACE` will replace a *failed* chain rather than silently dropping the new work.
*   **Constraints:** `NetworkType.CONNECTED`
*   **Backoff:** Exponential backoff, initial delay 30 seconds, max ~5 minutes. This provides reasonable retry frequency without hammering a struggling server.

```kotlin
companion object {
    private const val UNIQUE_WORK_NAME = "OfflineActionSync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ActionSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
    }
}
```

### 6.2 Execution Logic
The worker drains the queue sequentially. It must handle app process death gracefully (idempotency).

```kotlin
override suspend fun doWork(): Result {
    // Fetch all actions, sorted by creation time to preserve causal order across DIFFERENT bookmarks
    val actions = pendingActionDao.getAllActionsSorted()

    for (action in actions) {
        try {
            processAction(action)
            // Critical: Remove only after successful server confirmation
            pendingActionDao.delete(action)
        } catch (e: Exception) {
            if (isTransientError(e)) {
                // Network blip, server 500, etc.
                // Stop processing queue, return Retry to let WorkManager handle backoff
                return Result.retry()
            } else {
                // Permanent Error (400 Bad Request, Parser Error).
                // Log and drop to prevent queue clogging.
                Timber.e(e, "Permanent failure for action ${action.actionType} on ${action.bookmarkId}")
                pendingActionDao.delete(action)
            }
        }
    }
    return Result.success()
}
```

### 6.3 Error Classification

| HTTP Status | Classification | Action |
|-------------|---------------|--------|
| 2xx | Success | Delete from queue |
| 400 Bad Request | **Permanent** | Log + delete from queue |
| 401 Unauthorized | **Permanent** | Log + delete from queue (auth issue, not recoverable by retry) |
| 404 Not Found | **Special** | See §6.4 |
| 408, 429 | **Transient** | Return `Result.retry()` |
| 5xx | **Transient** | Return `Result.retry()` |
| Network exceptions (IOException, timeout) | **Transient** | Return `Result.retry()` |

### 6.4 404 Handling (Deleted Elsewhere)
If the API returns `404 Not Found` during any `editBookmark` or `deleteBookmark` call:
1.  Assume the bookmark was deleted on another device/server.
2.  **Action:** Hard delete the local bookmark (`bookmarkDao.deleteBookmark(id)`).
3.  **Result:** Treat the pending action as "completed" (remove from queue).
4.  Also remove any other pending actions for this bookmark from the queue (they're all irrelevant now).

---

## 7. Interaction with Existing Sync

### 7.1 The Problem

The app has two existing sync mechanisms:
*   **Full Sync (`performFullSync`)** — runs once per 24 hours, compares local vs. remote bookmark IDs, deletes local bookmarks missing from the server.
*   **Incremental Sync (`LoadBookmarksUseCase`)** — runs hourly (via `FullSyncWorker`) and on pull-to-refresh, fetches bookmarks updated since `lastBookmarkTimestamp`, upserts into local DB.

Both can overwrite local state. If the user toggles "Favorite" offline but an incremental sync runs (on reconnect) before the `ActionSyncWorker` drains, the sync will overwrite `isFavorite` back to the server's value.

### 7.2 The Solution: Pending Action Guard

When the incremental sync upserts a bookmark, it must **skip fields that have pending actions**:

```kotlin
// In the sync/upsert logic
suspend fun upsertBookmarkFromSync(remote: BookmarkEntity) {
    val pendingActions = pendingActionDao.getActionsForBookmark(remote.id)

    val entity = if (pendingActions.isEmpty()) {
        remote // No pending changes — accept server state fully
    } else {
        // Merge: start with remote, but preserve local values for fields with pending actions
        var merged = remote
        for (action in pendingActions) {
            merged = when (action.actionType) {
                TOGGLE_FAVORITE -> merged.copy(isMarked = bookmarkDao.getIsMarked(remote.id))
                TOGGLE_ARCHIVE -> merged.copy(isArchived = bookmarkDao.getIsArchived(remote.id))
                TOGGLE_READ -> merged.copy(isRead = bookmarkDao.getIsRead(remote.id))
                UPDATE_PROGRESS -> merged.copy(readProgress = bookmarkDao.getReadProgress(remote.id))
                DELETE -> return // Don't upsert a bookmark we've locally deleted
            }
        }
        merged
    }

    bookmarkDao.upsert(entity)
}
```

### 7.3 Full Sync Deletion Detection

The existing full sync deletes local bookmarks that are missing from the server. This must also respect the queue:

*   **Soft-deleted bookmarks** (`isLocalDeleted = true`): Do **not** hard-delete these during full sync. They have a pending `DELETE` action that needs to reach the server first. The `ActionSyncWorker` will handle server-side deletion and then hard-delete locally.
*   **Bookmarks deleted on server but not locally queued:** Hard-delete as usual (existing behavior). Also clean up any pending actions for these bookmarks (edge case: user queued an action, then the bookmark was deleted server-side between syncs).

### 7.4 Sync Ordering

When both `ActionSyncWorker` and `FullSyncWorker`/`LoadBookmarksWorker` need to run (e.g., device comes back online):

*   **Preferred order:** Drain the action queue first, then run the incremental sync.
*   **Implementation:** The `FullSyncWorker.doWork()` should call `ActionSyncWorker.enqueue()` and await its completion (or simply drain the queue inline) before fetching remote state. This ensures the server has the user's latest changes before we pull.
*   **Alternative (simpler):** If the pending action guard (§7.2) is correctly implemented, ordering doesn't strictly matter — the guard prevents data loss regardless. But drain-first produces cleaner results.

---

## 8. UI State Changes

### 8.1 What Changes for ViewModels

Currently, `BookmarkListViewModel` and `BookmarkDetailViewModel` handle `UpdateBookmarkState` (Loading, Success, Error) for toggle/delete operations, showing error toasts on failure.

After this feature:
*   **Remove** loading states for toggle operations. They are now instant (local-only).
*   **Remove** error toasts for toggle operations. Errors are handled silently in the background worker.
*   **Keep** confirmation feedback: Snackbar on archive ("Bookmark archived") and delete ("Bookmark deleted") with undo support where applicable.
*   The `updateBookmark()` / `deleteBookmark()` repository methods no longer return `UpdateResult`. They return `Unit` (fire-and-forget from the ViewModel's perspective).

### 8.2 Pending Actions Indicator

Display a pending action count in the navigation drawer, adjacent to the existing offline icon (`Icons.Default.CloudOff`):

```
┌──────────────────────────┐
│  MyDeck  ☁✗  ⟳ 3        │  ← offline icon + pending count
│──────────────────────────│
│  My List            (12) │
│  Archive             (5) │
│  ...                     │
```

**Implementation:**
*   Expose a `Flow<Int>` from `PendingActionDao`: `@Query("SELECT COUNT(*) FROM pending_actions")`
*   Collect in `BookmarkListViewModel` as `pendingActionCount: StateFlow<Int>`
*   In the drawer header, next to the `CloudOff` icon, show a sync icon (`Icons.Default.Sync`) with the count when `pendingActionCount > 0`
*   This indicator is visible regardless of online/offline state (actions may be pending even when online if the worker hasn't run yet)
*   When count reaches 0, the indicator disappears

### 8.3 No User-Facing Error Notifications (Phase 1)

If a pending action permanently fails (400, dropped from queue), there is no user notification in Phase 1. The action is silently dropped. This is acceptable because:
*   Permanent failures are rare (bad server state, not user error)
*   The local UI already reflects the intended state
*   Adding a notification system for background failures is significant scope

A future enhancement could add a "sync issues" screen or notification for permanent failures.

---

## 9. Implementation Plan

**Step 1: Database Migration**
*   Create Migration `5_6` (current DB version is 5).
*   SQL: `CREATE TABLE pending_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookmarkId TEXT NOT NULL, actionType TEXT NOT NULL, payload TEXT, createdAt INTEGER NOT NULL)`
*   SQL: `CREATE INDEX index_pending_actions_bookmarkId_actionType ON pending_actions (bookmarkId, actionType)`
*   SQL: `ALTER TABLE bookmarks ADD COLUMN isLocalDeleted INTEGER DEFAULT 0 NOT NULL`
*   Update `MyDeckDatabase` version from 5 to 6.

**Step 2: Data Layer**
*   Create `PendingActionEntity.kt` with the entity class and `ActionType` enum.
*   Create `PendingActionDao.kt` with methods: `insert`, `find(bookmarkId, actionType)`, `updatePayload`, `delete`, `deleteAllForBookmark`, `getAllActionsSorted`, `getActionsForBookmark`, `getCount` (Flow).
*   Create `ProgressPayload.kt` and `TogglePayload.kt` as `@Serializable` data classes.
*   Update `BookmarkDao` — add `WHERE isLocalDeleted = 0` to all UI-facing queries. Add `softDelete(bookmarkId)` method.

**Step 3: Worker Implementation**
*   Create `ActionSyncWorker.kt` in the `worker/` package.
*   Implement `processAction()` mapping each `ActionType` to the appropriate `readeckApi` call.
*   Implement error classification (§6.3) and 404 handling (§6.4).
*   Configure `APPEND_OR_REPLACE` policy and exponential backoff (§6.1).

**Step 4: Repository Refactor**
*   Rewrite `updateBookmark()`, `deleteBookmark()`, `updateReadProgress()` in `BookmarkRepositoryImpl`.
*   Wrap local update + queue insert in `database.withTransaction`.
*   Remove direct `readeckApi` calls from these methods.
*   Change return types from `UpdateResult` to `Unit`.

**Step 5: Sync Guard**
*   Update the bookmark upsert logic in `LoadBookmarksUseCase` / `BookmarkRepositoryImpl.insertBookmarks()` to check for pending actions before overwriting fields (§7.2).
*   Update `performFullSync()` to skip hard-deletion of soft-deleted bookmarks (§7.3).

**Step 6: ViewModel Cleanup**
*   Remove `UpdateBookmarkState` loading/error handling from `BookmarkListViewModel` and `BookmarkDetailViewModel`.
*   Update toggle/delete call sites to fire-and-forget (no result handling).
*   Add `pendingActionCount: StateFlow<Int>` to `BookmarkListViewModel`.

**Step 7: UI — Pending Action Indicator**
*   In the nav drawer header (next to the `CloudOff` icon in `BookmarkListScreen.kt`), add a sync indicator showing the pending action count.

---

## 10. Test Plan

### 10.1 Unit Tests (Repository)
*   **Coalescing:** Call `toggleFavorite(true)` then `toggleFavorite(false)`. Verify `pending_actions` has 1 row with `value=false`.
*   **Delete Supremacy:** Queue `updateProgress`, then `toggleFavorite`, then `deleteBookmark`. Verify `pending_actions` has only 1 row (`DELETE`).
*   **Transaction atomicity:** Verify that local update and queue insert both succeed or both fail.

### 10.2 Integration Tests (Worker)
*   **Success Flow:** Mock API 200 OK. Run Worker. Verify queue empty.
*   **Transient Fail:** Mock API 500. Run Worker. Verify queue *not* empty, Result is `Retry`.
*   **Permanent Fail:** Mock API 400. Run Worker. Verify queue empty (action dropped).
*   **404 Handling:** Mock API 404. Run Worker. Verify bookmark hard-deleted locally, queue cleared for that bookmark.

### 10.3 Sync Guard Tests
*   **Pending action preserved during sync:** Toggle favorite offline → run incremental sync with server state showing `isFavorite=false` → verify local DB still shows `isFavorite=true`.
*   **Soft-deleted not overwritten:** Delete bookmark offline → run full sync → verify bookmark still soft-deleted locally (not hard-deleted or re-inserted).

### 10.4 End-to-End Scenarios
*   **Offline Toggle:**
    *   Airplane mode ON. Toggle "Archive".
    *   Restart App. Verify item still shows as Archived in UI.
    *   Airplane mode OFF. Verify sync runs and clears queue.
    *   Verify pending action indicator shows count, then disappears after sync.
*   **Cross-Device Delete (The 404 Case):**
    *   Device A: Delete Bookmark X (Online).
    *   Device B (Offline): Mark Bookmark X as Favorite.
    *   Device B: Go Online. Worker runs `TOGGLE_FAVORITE`. Server returns 404.
    *   **Verify:** Device B local DB removes Bookmark X. Queue clears. App does not crash.
