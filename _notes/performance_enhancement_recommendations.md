# MyDeck Performance Enhancement Recommendations

## Executive Summary

The app has two primary performance issues:
1. **Aggressive article loading** - Enqueues individual workers for each bookmark during sync
2. **Unnecessary post-update syncs** - Triggers incremental sync after every bookmark update

Additionally, the app is **not utilizing the Readeck sync API** (`/api/bookmarks/sync`) which provides efficient deletion detection without requiring a full bookmark download.

---

## Readeck API: Current Usage vs Available Capabilities

### What the App Currently Uses

| Endpoint | Purpose | App Usage |
|----------|---------|-----------|
| `GET /api/bookmarks` | List bookmarks with pagination | ✅ Used for incremental sync with `updated_since` parameter |
| `GET /api/bookmarks/{id}/article` | Get article HTML content | ✅ Used to load article content |
| `PATCH /api/bookmarks/{id}` | Update bookmark properties | ✅ Used for archive/favorite/read status |
| `DELETE /api/bookmarks/{id}` | Delete a bookmark | ✅ Used for local deletion |

### What the App Does NOT Use

| Endpoint | Purpose | Benefit |
|----------|---------|---------|
| `GET /api/bookmarks/sync` | Sync-optimized endpoint | Returns bookmark IDs and status only (not full data) |
| `GET /api/bookmarks/sync?since=<timestamp>` | Delta sync | **Returns ONLY updated OR DELETED bookmarks since timestamp** |
| `POST /api/bookmarks/sync` | Batch content fetch | Streamed response of requested bookmarks with optional content/resources |

### Key Finding: Deletion Detection

The app's explanation text states:
> "Since this process requires downloading all your bookmark data..."

**This is incorrect.** The Readeck sync API (`/api/bookmarks/sync?since=<timestamp>`) returns:
- IDs of bookmarks **updated** since the timestamp
- IDs of bookmarks **deleted** since the timestamp

This means deletion detection does NOT require downloading all bookmarks. The server tracks deletions and reports them via the sync endpoint.

---

## Recommended Enhancements

### 1. Implement Sync API for Deletion Detection (High Impact)

**Current behavior:** `performFullSync()` pages through ALL bookmarks to build a list of remote IDs, then compares with local IDs.

**Recommended:** Use `/api/bookmarks/sync?since=<lastSyncTimestamp>` which returns:
- Updated bookmark IDs (to fetch new data for)
- Deleted bookmark IDs (to remove locally)

**Benefits:**
- Reduces network traffic from O(n) to O(changes)
- Eliminates need to download all bookmark metadata
- Server-side deletion tracking is more reliable

**Implementation:**
```kotlin
// Add to ReadeckApi.kt
@GET("bookmarks/sync")
suspend fun getSyncStatus(
    @Query("since") since: Instant?
): Response<SyncStatusResponse>

data class SyncStatusResponse(
    val updated: List<SyncBookmarkStatus>,
    val deleted: List<String>  // deleted bookmark IDs
)
```

### 2. Remove Post-Update Sync Trigger (High Impact)

**Current behavior:** `UpdateBookmarkUseCase.handleResult()` triggers `LoadBookmarksWorker.enqueue()` after every successful update.

**Recommended:** Remove this trigger entirely.

**Rationale:**
- The PATCH request already updates the server
- UI should update optimistically from local database
- No new data is expected from server after a local update
- Any server-side effects (timestamps, etc.) will sync on next regular sync

**Risk assessment:**
- **Low risk** - Server is already updated; local state is authoritative for user-initiated changes
- **Mitigation** - If server rejects update (rare), error handling already exists

**Implementation:**
```kotlin
// UpdateBookmarkUseCase.kt - Remove line 47
private fun handleResult(result: BookmarkRepository.UpdateResult): Result {
    return when(result) {
        is BookmarkRepository.UpdateResult.Success -> {
            // REMOVE: LoadBookmarksWorker.enqueue(context, isInitialLoad = false)
            Result.Success
        }
        // ...
    }
}
```

### 3. Batch Article Loading with Lower Priority (Medium Impact)

**Current behavior:** For each bookmark synced, an individual `LoadArticleWorker` is enqueued immediately.

**Recommended:**
- Create a single `BatchArticleLoadWorker` that processes articles in chunks
- Add constraints for battery and network conditions
- Implement configurable concurrency (e.g., 3-5 concurrent downloads)

**Implementation approach:**
```kotlin
// New: BatchArticleLoadWorker.kt
class BatchArticleLoadWorker : CoroutineWorker {
    override suspend fun doWork(): Result {
        val pendingBookmarkIds = bookmarkDao.getBookmarksWithoutContent()

        pendingBookmarkIds.chunked(BATCH_SIZE).forEach { batch ->
            batch.map { id ->
                async { loadArticleUseCase.execute(id) }
            }.awaitAll()

            // Brief pause between batches to reduce resource contention
            delay(BATCH_DELAY_MS)
        }
        return Result.success()
    }

    companion object {
        const val BATCH_SIZE = 5
        const val BATCH_DELAY_MS = 500L
    }
}
```

**Constraints to add:**
```kotlin
.setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()
)
```

### 4. Add Sync Status to Synchronize Dialog (User Request)

Display in the Sync Settings screen:
- Number of bookmarks synced / total
- Number of articles pending content download / total
- Last sync timestamp

**Implementation approach:**
```kotlin
// SyncSettingsUiState.kt - Add fields
data class SyncSettingsUiState(
    // existing fields...
    val totalBookmarks: Int = 0,
    val bookmarksWithContent: Int = 0,
    val lastSyncTimestamp: Instant? = null,
    val syncInProgress: Boolean = false
)

// Add to UI
Text("Bookmarks: $bookmarksWithContent / $totalBookmarks synced")
Text("Last sync: ${lastSyncTimestamp?.format() ?: "Never"}")
```

**Data sources:**
- Total bookmarks: `bookmarkDao.count()`
- With content: `bookmarkDao.countWithArticleContent()`
- Last sync: `settingsDataStore.getLastBookmarkTimestamp()`

---

## Readeck API Deficiencies

Based on research, no significant functional deficiencies were identified. The API provides:

| Feature | Status |
|---------|--------|
| Delta sync with `since` parameter | ✅ Available |
| Deleted bookmark tracking | ✅ Available via sync endpoint |
| Pagination headers | ✅ Available (total-count, total-pages, current-page) |
| Batch content retrieval | ✅ Available via POST to sync endpoint |
| Individual article content | ✅ Available |

The app is simply not utilizing the sync-specific endpoint that was designed for mobile synchronization.

---

## Implementation Priority

| Enhancement | Impact | Effort | Priority |
|-------------|--------|--------|----------|
| Remove post-update sync trigger | High | Low | 1 |
| Implement sync API for deletions | High | Medium | 2 |
| Add sync status to dialog | Medium | Low | 3 |
| Batch article loading | Medium | Medium | 4 |

---

## Expected Outcomes

| Metric | Current | After Enhancements |
|--------|---------|-------------------|
| Network requests per update | 1 + full sync | 1 |
| Deletion detection requests | O(n) all bookmarks | O(1) sync endpoint |
| Article loading workers | 1 per bookmark | 1 batch worker |
| Post-update UI lag | Noticeable | Eliminated |
| Sync status visibility | None | Full visibility in dialog |

---

## References

- [Readeck Documentation](https://readeck.org/en/docs/)
- [Readeck 0.20 Release Notes](https://readeck.org/en/blog/202508-readeck-20/) - Sync API introduction
- [Readeck Source Code](https://codeberg.org/readeck/readeck)
