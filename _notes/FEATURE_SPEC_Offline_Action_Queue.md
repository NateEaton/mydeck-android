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
*   Create Bookmark (Requires handling temporary local IDs vs. server UUIDs).
*   Edit Labels (Requires complex diffing logic).

---

## 3. Database Changes

### 3.1 New Entity: `PendingActionEntity`
We uses the Command Pattern to store actions. The `payload` field stores serialized JSON data specific to the action type.

```kotlin
@Entity(tableName = "pending_actions",
        indices = [Index(value = ["bookmarkId", "actionType"])]) // Index for fast coalescing lookups
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookmarkId: String,
    val actionType: ActionType,
    val payload: String?, // JSON serialized data classes
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

---

## 4. Logic & Rules of the Road

### 4.1 Payload Contract
To ensure type safety within the JSON payload, we define specific data classes for serialization.

```kotlin
// Payload Schemas
data class ProgressPayload(val progress: Int, val timestamp: Instant)
data class TogglePayload(val value: Boolean) // Used for Favorite, Archive, Read
// DELETE actions have no payload
```

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

---

## 5. Repository Implementation

`BookmarkRepositoryImpl` serves as the orchestrator. It acts on the local DB first, then schedules the sync.

### 5.1 Logic Flow (Pseudo-code)

```kotlin
suspend fun toggleFavorite(bookmarkId: String, isFavorite: Boolean) {
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

    // 3. Trigger Sync
    ActionSyncWorker.enqueue(context)
}

suspend fun deleteBookmark(bookmarkId: String) {
    // 1. Optimistic Soft Delete
    bookmarkDao.softDelete(bookmarkId) // sets isLocalDeleted = 1

    // 2. Delete Supremacy Logic
    pendingActionDao.deleteAllForBookmark(bookmarkId) 
    
    // 3. Queue Delete Action
    pendingActionDao.insert(PendingActionEntity(..., actionType = DELETE))

    // 4. Trigger Sync
    ActionSyncWorker.enqueue(context)
}
```

---

## 6. Background Sync: `ActionSyncWorker`

### 6.1 Configuration
*   **Unique Work Name:** `OfflineActionSync`
*   **Policy:** `ExistingWorkPolicy.APPEND`.
    *   *Why APPEND?* If the worker is currently running and a new action is queued, we want the worker to run again immediately after it finishes to pick up the new data.
*   **Constraints:** `NetworkType.CONNECTED`

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
                Timber.e(e, "Permanent failure")
                pendingActionDao.delete(action)
            }
        }
    }
    return Result.success()
}
```

### 6.3 404 Handling (Deleted Elsewhere)
If the API returns `404 Not Found` during any `editBookmark` or `deleteBookmark` call:
1.  Assume the bookmark was deleted on another device/server.
2.  **Action:** Hard delete the local bookmark (`bookmarkDao.deleteBookmark(id)`).
3.  **Result:** Treat the pending action as "completed" (remove from queue).

---

## 7. Implementation Plan

**Step 1: Database Migration**
*   Create Migration `4_5`.
*   SQL: `CREATE TABLE pending_actions...`
*   SQL: `ALTER TABLE bookmarks ADD COLUMN isLocalDeleted INTEGER DEFAULT 0 NOT NULL`

**Step 2: Data Layer**
*   Create `PendingActionDao`.
*   Define Payload Data Classes.
*   Update `BookmarkDao` queries to respect `isLocalDeleted`.

**Step 3: Worker Implementation**
*   Implement `ActionSyncWorker` with the API handling logic.
*   Ensure rigorous error classification (Transient vs Permanent).

**Step 4: Repository Refactor**
*   Rewrite `updateBookmark`, `deleteBookmark`, `updateReadProgress`.
*   Implement Coalescing and Delete Supremacy logic.
*   Remove direct API calls from these methods.

**Step 5: Cleanup**
*   Remove `UpdateBookmarkState` error handling from ViewModels (as errors are now handled in background).

---

## 8. Test Plan

### 8.1 Unit Tests (Repository)
*   **Coalescing:** Call `toggleFavorite(true)` then `toggleFavorite(false)`. Verify `pending_actions` has 1 row with `value=false`.
*   **Delete Supremacy:** Queue `updateProgress`, then `toggleFavorite`, then `deleteBookmark`. Verify `pending_actions` has only 1 row (`DELETE`).

### 8.2 Integration Tests (Worker)
*   **Success Flow:** Mock API 200 OK. Run Worker. Verify queue empty.
*   **Transient Fail:** Mock API 500. Run Worker. Verify queue *not* empty, Result is `Retry`.
*   **Permanent Fail:** Mock API 400. Run Worker. Verify queue empty (action dropped).

### 8.3 End-to-End Scenarios
*   **Offline Toggle:**
    *   Airplane mode ON. Toggle "Archive".
    *   Restart App. Verify item still shows as Archived in UI.
    *   Airplane mode OFF. Verify sync runs and clears queue.
*   **Cross-Device Delete (The 404 Case):**
    *   Device A: Delete Bookmark X (Online).
    *   Device B (Offline): Mark Bookmark X as Favorite.
    *   Device B: Go Online. Worker runs `TOGGLE_FAVORITE`. Server returns 404.
    *   **Verify:** Device B local DB removes Bookmark X. Queue clears. App does not crash.