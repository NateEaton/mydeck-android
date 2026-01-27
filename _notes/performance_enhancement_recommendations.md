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

# Technical Specification

## Enhancement 1: Remove Post-Update Sync Trigger

### File Changes

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/UpdateBookmarkUseCase.kt`

**Current code (lines 44-53):**
```kotlin
private fun handleResult(result: BookmarkRepository.UpdateResult): Result {
    return when(result) {
        is BookmarkRepository.UpdateResult.Success -> {
            LoadBookmarksWorker.enqueue(context, isInitialLoad = false)  // DELETE THIS LINE
            Result.Success
        }
        is BookmarkRepository.UpdateResult.Error -> Result.GenericError(result.errorMessage)
        is BookmarkRepository.UpdateResult.NetworkError -> Result.NetworkError(result.errorMessage)
    }
}
```

**Modified code:**
```kotlin
private fun handleResult(result: BookmarkRepository.UpdateResult): Result {
    return when(result) {
        is BookmarkRepository.UpdateResult.Success -> Result.Success
        is BookmarkRepository.UpdateResult.Error -> Result.GenericError(result.errorMessage)
        is BookmarkRepository.UpdateResult.NetworkError -> Result.NetworkError(result.errorMessage)
    }
}
```

**Remove unused import:**
```kotlin
// DELETE: import com.mydeck.app.worker.LoadBookmarksWorker
```

**Remove unused constructor parameter:**
```kotlin
// The context parameter may no longer be needed after this change
// Review if @ApplicationContext private val context: Context can be removed
```

### Testing
- Verify archive/favorite/mark-read operations complete without triggering sync
- Confirm UI updates immediately from local database
- Verify next scheduled sync still picks up server-side changes

---

## Enhancement 2: Implement Sync API for Deletion Detection

### New Files to Create

**File:** `app/src/main/java/com/mydeck/app/io/rest/model/SyncStatusDto.kt`
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncStatusDto(
    val id: String,
    val status: String,  // "ok" or "deleted"
    val updated: String? = null  // ISO 8601 timestamp
)

@Serializable
data class SyncStatusResponseDto(
    val bookmarks: List<SyncStatusDto>
)
```

### File Modifications

**File:** `app/src/main/java/com/mydeck/app/io/rest/ReadeckApi.kt`

**Add new endpoint:**
```kotlin
@GET("bookmarks/sync")
suspend fun getSyncStatus(
    @Query("since") since: String?  // ISO 8601 formatted timestamp
): Response<List<SyncStatusDto>>
```

**File:** `app/src/main/java/com/mydeck/app/domain/BookmarkRepository.kt`

**Add new method to interface:**
```kotlin
suspend fun performDeltaSync(since: Instant?): SyncResult
```

**File:** `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt`

**Add new implementation (replaces `performFullSync` logic):**
```kotlin
override suspend fun performDeltaSync(since: Instant?): SyncResult = withContext(dispatcher) {
    try {
        val sinceParam = since?.toString()  // ISO 8601 format
        val response = readeckApi.getSyncStatus(sinceParam)

        if (!response.isSuccessful) {
            return@withContext SyncResult.Error(
                errorMessage = "Sync status failed",
                code = response.code()
            )
        }

        val syncStatuses = response.body() ?: emptyList()

        // Separate updated and deleted bookmarks
        val deletedIds = syncStatuses
            .filter { it.status == "deleted" }
            .map { it.id }

        val updatedIds = syncStatuses
            .filter { it.status == "ok" }
            .map { it.id }

        // Delete locally removed bookmarks
        deletedIds.forEach { id ->
            bookmarkDao.deleteBookmark(id)
        }

        // Fetch updated bookmarks (can be done via existing LoadBookmarksUseCase
        // or fetch individually if list is small)
        // Note: For updated bookmarks, you may want to trigger LoadBookmarksWorker
        // with a specific list of IDs, or implement a targeted fetch

        Timber.i("Delta sync complete: ${deletedIds.size} deleted, ${updatedIds.size} updated")

        SyncResult.Success(countDeleted = deletedIds.size)
    } catch (e: Exception) {
        Timber.e(e, "Delta sync failed")
        SyncResult.NetworkError(errorMessage = "Network error during delta sync", ex = e)
    }
}
```

**File:** `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`

**Add new method to interface:**
```kotlin
suspend fun saveLastSyncTimestamp(timestamp: Instant)
suspend fun getLastSyncTimestamp(): Instant?
```

**File:** `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStoreImpl.kt`

**Add implementation:**
```kotlin
private val LAST_SYNC_TIMESTAMP = stringPreferencesKey("lastSyncTimestamp")

override suspend fun saveLastSyncTimestamp(timestamp: Instant) {
    dataStore.edit { preferences ->
        preferences[LAST_SYNC_TIMESTAMP] = timestamp.toString()
    }
}

override suspend fun getLastSyncTimestamp(): Instant? {
    return dataStore.data.first()[LAST_SYNC_TIMESTAMP]?.let {
        Instant.parse(it)
    }
}
```

**File:** `app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt`

**Modify to use delta sync:**
```kotlin
override suspend fun doWork(): Result {
    val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()

    // Use delta sync if we have a previous timestamp, otherwise full sync
    val syncResult = if (lastSyncTimestamp != null) {
        bookmarkRepository.performDeltaSync(lastSyncTimestamp)
    } else {
        // First sync or reset - use existing full sync
        bookmarkRepository.performFullSync()
    }

    return when (syncResult) {
        is BookmarkRepository.SyncResult.Success -> {
            settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
            Result.success()
        }
        else -> Result.retry()
    }
}
```

### Database Changes (if needed)

The `RemoteBookmarkIdEntity` table and related methods can be deprecated after delta sync is implemented, as they're only used for the full-download deletion detection approach.

### Testing
- Test with empty `since` parameter (should return all bookmark statuses)
- Test with recent `since` parameter (should return only recent changes)
- Verify deleted bookmarks are removed locally
- Verify updated bookmarks trigger content refresh
- Test network error handling

---

## Enhancement 3: Add Sync Status to Dialog

### File Modifications

**File:** `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

**Add new queries:**
```kotlin
@Query("SELECT COUNT(*) FROM bookmarks")
fun observeTotalBookmarkCount(): Flow<Int>

@Query("SELECT COUNT(*) FROM bookmarks WHERE id IN (SELECT bookmarkId FROM article_content)")
fun observeBookmarksWithContentCount(): Flow<Int>

// Alternative single query approach:
@Query("""
    SELECT
        (SELECT COUNT(*) FROM bookmarks) AS total,
        (SELECT COUNT(*) FROM article_content) AS withContent
""")
fun observeSyncStatus(): Flow<SyncStatusCounts>

data class SyncStatusCounts(
    val total: Int,
    val withContent: Int
)
```

**File:** `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsUiState.kt` (or within SyncSettingsViewModel.kt)

**Modify data class:**
```kotlin
@Immutable
data class SyncSettingsUiState(
    val autoSyncEnabled: Boolean,
    val autoSyncTimeframe: AutoSyncTimeframe,
    val autoSyncTimeframeOptions: List<AutoSyncTimeframeOption>,
    val showDialog: Dialog?,
    @StringRes
    val autoSyncTimeframeLabel: Int,
    val nextAutoSyncRun: String?,
    val autoSyncButtonEnabled: Boolean,
    // NEW FIELDS
    val totalBookmarks: Int = 0,
    val bookmarksWithContent: Int = 0,
    val lastSyncTimestamp: String? = null
)
```

**File:** `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsViewModel.kt`

**Add new flows and combine them:**
```kotlin
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val bookmarkDao: BookmarkDao,  // ADD THIS
    private val settingsDataStore: SettingsDataStore,
    private val fullSyncUseCase: FullSyncUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Add sync status flows
    private val syncStatusCounts = bookmarkDao.observeSyncStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatusCounts(0, 0))

    private val lastSyncTimestamp = flow {
        emit(settingsDataStore.getLastSyncTimestamp())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState = combine(
        autoSyncEnabled,
        autoSyncTimeframe,
        showDialog,
        workInfo,
        fullSyncUseCase.syncIsRunning,
        syncStatusCounts,      // ADD
        lastSyncTimestamp      // ADD
    ) { values ->
        val autoSyncEnabled = values[0] as Boolean
        val autoSyncTimeframe = values[1] as AutoSyncTimeframe
        val showDialog = values[2] as Dialog?
        val workInfo = values[3] as WorkInfo?
        val syncIsRunning = values[4] as Boolean
        val counts = values[5] as SyncStatusCounts
        val lastSync = values[6] as Instant?

        // ... existing logic ...

        SyncSettingsUiState(
            // ... existing fields ...
            totalBookmarks = counts.total,
            bookmarksWithContent = counts.withContent,
            lastSyncTimestamp = lastSync?.let { dateFormat.format(Date(it.toEpochMilliseconds())) }
        )
    }
}
```

**File:** `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsScreen.kt`

**Add UI elements after existing content:**
```kotlin
// After the existing Column content, before the Button
Spacer(modifier = Modifier.height(16.dp))

// Sync Status Section
Text(
    text = stringResource(R.string.sync_status_heading),
    style = Typography.titleSmall
)
Text(
    text = stringResource(
        R.string.sync_status_bookmarks,
        settingsUiState.bookmarksWithContent,
        settingsUiState.totalBookmarks
    ),
    style = Typography.bodySmall
)
Text(
    text = settingsUiState.lastSyncTimestamp?.let {
        stringResource(R.string.sync_status_last_sync, it)
    } ?: stringResource(R.string.sync_status_never),
    style = Typography.bodySmall
)
```

**File:** `app/src/main/res/values/strings.xml`

**Add new strings:**
```xml
<string name="sync_status_heading">Sync Status</string>
<string name="sync_status_bookmarks">Articles downloaded: %1$d / %2$d</string>
<string name="sync_status_last_sync">Last sync: %1$s</string>
<string name="sync_status_never">Last sync: Never</string>
```

**Update existing string to be accurate:**
```xml
<!-- OLD: -->
<string name="settings_sync_support_text">To keep your bookmark list up-to-date, the app occasionally checks for and removes bookmarks you\'ve deleted on the Readeck server. Since this process requires downloading all your bookmark data, it runs automatically in the background. This helps ensure your app accurately reflects your current Readeck bookmarks without you having to manually delete them.</string>

<!-- NEW (after implementing sync API): -->
<string name="settings_sync_support_text">To keep your bookmark list up-to-date, the app periodically syncs with the Readeck server to detect new, updated, and deleted bookmarks. Article content is downloaded in the background for offline reading.</string>
```

### Testing
- Verify counts update in real-time as bookmarks sync
- Verify timestamp updates after sync completes
- Test with 0 bookmarks
- Test UI layout with large numbers

---

## Enhancement 4: Batch Article Loading

### New Files to Create

**File:** `app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt`
```kotlin
package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BatchArticleLoadWorker starting")

        try {
            val pendingBookmarkIds = bookmarkDao.getBookmarkIdsWithoutContent()
            Timber.i("Found ${pendingBookmarkIds.size} bookmarks without content")

            if (pendingBookmarkIds.isEmpty()) {
                return Result.success()
            }

            pendingBookmarkIds.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                Timber.d("Processing batch ${index + 1}, size=${batch.size}")

                coroutineScope {
                    batch.map { id ->
                        async {
                            try {
                                loadArticleUseCase.execute(id)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load article $id")
                            }
                        }
                    }.awaitAll()
                }

                // Brief pause between batches to reduce resource contention
                if (index < pendingBookmarkIds.chunked(BATCH_SIZE).size - 1) {
                    delay(BATCH_DELAY_MS)
                }
            }

            Timber.i("BatchArticleLoadWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker failed")
            return Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        private const val BATCH_SIZE = 5
        private const val BATCH_DELAY_MS = 500L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,  // Don't restart if already running
                request
            )
        }
    }
}
```

### File Modifications

**File:** `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

**Add new query:**
```kotlin
@Query("""
    SELECT b.id FROM bookmarks b
    WHERE NOT EXISTS (SELECT 1 FROM article_content ac WHERE ac.bookmarkId = b.id)
    ORDER BY b.created DESC
""")
suspend fun getBookmarkIdsWithoutContent(): List<String>
```

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt`

**Replace individual worker enqueueing (lines 62-67):**
```kotlin
// OLD CODE - DELETE:
bookmarks.forEach {
    val request = OneTimeWorkRequestBuilder<LoadArticleWorker>().setInputData(
        Data.Builder().putString(LoadArticleWorker.PARAM_BOOKMARK_ID, it.id).build()
    ).build()
    workManager.enqueue(request)
}

// NEW CODE - REPLACE WITH:
// Enqueue batch article loader after bookmark sync completes
// This will be called once at the end of execute(), not per-page
```

**Move batch enqueue to end of execute() method:**
```kotlin
@Transaction
suspend fun execute(pageSize: Int = DEFAULT_PAGE_SIZE, initialOffset: Int = 0): UseCaseResult<Unit> {
    // ... existing pagination logic ...

    // After all pages are loaded, enqueue batch article loading
    BatchArticleLoadWorker.enqueue(context)  // Need to inject context

    return UseCaseResult.Success(Unit)
}
```

**Update constructor to inject Context:**
```kotlin
class LoadBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val workManager: WorkManager,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context  // ADD THIS
)
```

### Deprecation

**File:** `app/src/main/java/com/mydeck/app/worker/LoadArticleWorker.kt`

This file can be deprecated but kept for potential on-demand single article loading (e.g., if user opens a bookmark that hasn't been synced yet).

### Testing
- Test with large bookmark collections (>1000)
- Verify batch processing completes all articles
- Test battery constraint (should pause when battery low)
- Test network constraint (should pause when offline)
- Verify UI remains responsive during batch processing
- Test cancellation behavior

---

## Migration Notes

### Backward Compatibility
- The `performFullSync()` method should be retained as a fallback for:
  - First-time sync (no `lastSyncTimestamp` exists)
  - Manual "full sync" trigger from settings
  - Recovery from corrupted sync state

### Database Migration
- No schema changes required for core functionality
- The `remote_bookmark_ids` table can remain but will be unused after delta sync is implemented
- Consider adding a migration to clean up this table in a future release

### Rollback Plan
- All changes are feature-flaggable at the UseCase level
- If issues arise, revert to calling `performFullSync()` instead of `performDeltaSync()`
- Individual worker enqueueing can be restored by reverting `LoadBookmarksUseCase` changes

---

## References

- [Readeck Documentation](https://readeck.org/en/docs/)
- [Readeck 0.20 Release Notes](https://readeck.org/en/blog/202508-readeck-20/) - Sync API introduction
- [Readeck Source Code](https://codeberg.org/readeck/readeck)
