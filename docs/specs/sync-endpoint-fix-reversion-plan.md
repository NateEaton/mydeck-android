# Reversion Plan: Re-enabling Delta Sync After Server Fix

## Background

The workaround implemented in commit `b1d7993` addresses a server-side SQLite bug in the `/api/bookmarks/sync` endpoint that causes HTTP 500 errors. Once the Readeck server fix is deployed and running in your instance, this workaround should be reverted to restore the more efficient delta sync mechanism.

## Why Delta Sync is Better

### Current Workaround Behavior:
- **Full sync** runs every 24 hours (fetches ALL bookmark IDs from server)
- **Incremental loading** via `/api/bookmarks?updated_since=X` (fetches full bookmark objects)
- **Trade-off**: Deleted bookmarks linger locally up to 24 hours

### Delta Sync Behavior (When Working):
- **Delta sync** fetches only changed bookmark IDs via `/api/bookmarks/sync?since=X`
- Separates deletions from updates in a single lightweight request
- Fetches full content only for bookmarks that actually changed
- **Immediate deletion detection** (not delayed by 24h)
- More efficient overall (smaller payloads, faster sync)

### Efficiency Comparison:

| Operation | Workaround (Current) | Delta Sync (Fixed) |
|-----------|---------------------|-------------------|
| **Sync frequency** | Every pull/auto-sync | Every pull/auto-sync |
| **Deletion detection** | Every 24h (full sync) | Every sync (delta) |
| **Network overhead** | Full ID list every 24h + changed bookmarks | Only changed bookmark IDs + content |
| **Deletion latency** | Up to 24 hours | Immediate |
| **API calls per sync** | 1-2 (periodic full sync) + incremental | 1 (delta) or 1 (full, first time) |

## Recommended Post-Fix Approach

**Option B: Restore delta sync + keep interval fallback (RECOMMENDED)**

This approach:
1. Restores the efficient delta sync mechanism
2. Keeps the 24-hour full sync interval as a **safety fallback**
3. Provides resilience against future server issues
4. Maintains the improvements made during the workaround

This is actually mentioned in the original spec under "Future Improvements":
> When the Readeck server bug is fixed:
> 1. Re-enable `performDeltaSync()`
> 2. Update `FullSyncWorker` to prefer delta sync
> 3. **Keep the `FULL_SYNC_INTERVAL` logic as a safety fallback**

## Implementation Steps

### Step 1: Restore `performDeltaSync()` in BookmarkRepositoryImpl.kt

**Current code (lines 445-454):**
```kotlin
override suspend fun performDeltaSync(since: kotlinx.datetime.Instant?): BookmarkRepository.SyncResult = withContext(dispatcher) {
    // DEPRECATED: The /api/bookmarks/sync endpoint has a server-side bug with SQLite.
    // This method is kept for future use if/when the server bug is fixed.
    // For now, always return an error to trigger fallback to full sync.
    Timber.w("Delta sync is disabled due to server-side SQLite compatibility issue")
    return@withContext BookmarkRepository.SyncResult.Error(
        errorMessage = "Delta sync disabled - server endpoint incompatible with SQLite",
        code = 500
    )
}
```

**Restored code:**
```kotlin
override suspend fun performDeltaSync(since: kotlinx.datetime.Instant?): BookmarkRepository.SyncResult = withContext(dispatcher) {
    try {
        val sinceParam = since?.let {
            // Truncate to seconds to avoid SQLite timestamp parsing errors on server
            kotlinx.datetime.Instant.fromEpochSeconds(it.epochSeconds).toString()
        }
        Timber.d("Starting delta sync with since=$sinceParam")

        val response = readeckApi.getSyncStatus(sinceParam)

        if (!response.isSuccessful) {
            Timber.e("Sync status failed with code: ${response.code()}")
            return@withContext BookmarkRepository.SyncResult.Error(
                errorMessage = "Sync status failed",
                code = response.code()
            )
        }

        val syncStatuses = response.body() ?: emptyList()
        Timber.d("Received ${syncStatuses.size} sync status entries")

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

        // Fetch content for updated bookmarks
        if (updatedIds.isNotEmpty()) {
            val contentResponse = readeckApi.syncContent(SyncContentRequestDto(ids = updatedIds))
            if (contentResponse.isSuccessful) {
                val bookmarks = contentResponse.body()
                if (bookmarks != null) {
                    Timber.d("Fetched ${bookmarks.size} bookmarks content")
                    bookmarkDao.insertBookmarksWithArticleContent(bookmarks.map { it.toDomain().toEntity() })
                }
            } else {
                 Timber.e("Sync content failed with code: ${contentResponse.code()}")
                 // We don't fail the whole sync here, as deletions might have succeeded.
                 // But strictly speaking, it is a partial failure.
             }
        }

        Timber.i("Delta sync complete: ${deletedIds.size} deleted, ${updatedIds.size} updated")

        BookmarkRepository.SyncResult.Success(countDeleted = deletedIds.size)
    } catch (e: IOException) {
        Timber.e(e, "Network error during delta sync: ${e.message}")
        BookmarkRepository.SyncResult.NetworkError(errorMessage = "Network error during delta sync", ex = e)
    } catch (e: Exception) {
        Timber.e(e, "Delta sync failed: ${e.message}")
        BookmarkRepository.SyncResult.Error(errorMessage = "Delta sync failed: ${e.message}", ex = e)
    }
}
```

### Step 2: Update FullSyncWorker.kt Strategy

**Current code (lines 42-61):**
```kotlin
Timber.d("Start Work")
val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()
val lastFullSyncTimestamp = settingsDataStore.getLastFullSyncTimestamp()

// Step 1: Determine if we need a full sync for deletion detection
val needsFullSync = lastFullSyncTimestamp == null ||
    Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL

var syncResult = if (needsFullSync) {
    Timber.d("Performing full sync for deletion detection")
    val result = bookmarkRepository.performFullSync()
    if (result is SyncResult.Success) {
        settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
    }
    result
} else {
    // Skip deletion detection - we did a full sync recently
    Timber.d("Skipping deletion check (last full sync was recent)")
    SyncResult.Success(countDeleted = 0)
}
```

**Enhanced code with delta sync + safety fallback:**
```kotlin
Timber.d("Start Work")
val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()
val lastFullSyncTimestamp = settingsDataStore.getLastFullSyncTimestamp()

// Step 1: Attempt delta sync first, with periodic full sync as fallback
var syncResult = if (lastSyncTimestamp != null) {
    // Check if we should do a periodic full sync (safety fallback)
    val shouldDoFullSync = lastFullSyncTimestamp == null ||
        Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL

    if (shouldDoFullSync) {
        Timber.d("Performing periodic full sync (safety fallback)")
        val result = bookmarkRepository.performFullSync()
        if (result is SyncResult.Success) {
            settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
        }
        result
    } else {
        // Prefer delta sync for efficiency
        Timber.d("Performing delta sync since=$lastSyncTimestamp")
        val deltaResult = bookmarkRepository.performDeltaSync(lastSyncTimestamp)

        // If delta sync fails, fall back to full sync
        if (deltaResult is SyncResult.Error) {
            Timber.w("Delta sync failed, falling back to full sync")
            val result = bookmarkRepository.performFullSync()
            if (result is SyncResult.Success) {
                settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
            }
            result
        } else {
            deltaResult
        }
    }
} else {
    // First sync ever - must do full sync
    Timber.d("Performing full sync (no previous timestamp)")
    val result = bookmarkRepository.performFullSync()
    if (result is SyncResult.Success) {
        settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
    }
    result
}
```

### Step 3: Optional - Adjust FULL_SYNC_INTERVAL

Consider increasing the interval since it's now just a safety fallback:

```kotlin
companion object {
    const val UNIQUE_NAME_AUTO = "auto_full_sync_work"
    const val UNIQUE_NAME_MANUAL = "manual_full_sync_work"
    const val TAG = "full_sync"
    const val OUTPUT_DATA_COUNT = "count"
    const val NOTIFICATION_ID = 0
    const val INPUT_IS_MANUAL_SYNC = "is_manual_sync"
    val FULL_SYNC_INTERVAL = 168.hours  // Weekly safety fallback (instead of daily)
}
```

### Step 4: Keep SettingsDataStore Changes (NO CHANGES NEEDED)

The `last_full_sync_timestamp` tracking should remain in place for the safety fallback mechanism:
- `SettingsDataStore.kt` - keep interface methods
- `SettingsDataStoreImpl.kt` - keep implementation

### Step 5: LoadBookmarksUseCase (NO CHANGES NEEDED)

This already uses the working `/api/bookmarks?updated_since=` endpoint and should remain unchanged.

## Sync Flow After Reversion

### Normal Sync (lastSyncTimestamp exists, < 168h since full sync):
1. **Delta sync** via `/api/bookmarks/sync?since=X`
   - Gets list of changed/deleted bookmark IDs
   - Deletes removed bookmarks immediately
   - Fetches full content for updated bookmarks
2. **Incremental bookmark loading** via LoadBookmarksUseCase
   - Uses `/api/bookmarks?updated_since=X` as secondary check
3. **Result**: Fast, efficient, immediate deletion detection

### Periodic Safety Sync (> 168h since last full sync):
1. **Full sync** via `/api/bookmarks` (paginated)
   - Fetches all bookmark IDs from server
   - Compares with local database
   - Removes any orphaned local bookmarks
   - Updates `last_full_sync_timestamp`
2. **Incremental bookmark loading** via LoadBookmarksUseCase
3. **Result**: Comprehensive cleanup, catches any edge cases

### First Sync (no lastSyncTimestamp):
1. **Full sync** (required for initial population)
2. **Incremental bookmark loading**
3. **Result**: Complete database initialization

## Testing Checklist

After implementing the reversion:

- [ ] Verify delta sync works without 500 errors
- [ ] Confirm deleted bookmarks are removed immediately (not after 24h)
- [ ] Test fallback to full sync if delta sync fails
- [ ] Verify periodic full sync still runs (check logs after interval)
- [ ] Test first-time sync (clear app data and sync)
- [ ] Verify manual "Sync Now" works correctly
- [ ] Test pull-to-refresh sync
- [ ] Check background auto-sync behavior

## Rollback Plan

If issues arise after reversion:

1. Revert to commit `b1d7993` (the workaround):
   ```bash
   git revert HEAD
   git push
   ```

2. Or restore the deprecation stub in `performDeltaSync()` to disable it again

## Summary

**What to do:** Re-enable delta sync with the enhanced fallback mechanism (Option B)

**Why:**
- Restores efficient sync behavior
- Maintains immediate deletion detection
- Keeps safety fallback for resilience
- Best of both worlds: efficiency + robustness

**When:** After confirming the Readeck server fix is deployed and working in your instance

**Files to modify:**
1. `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt` - Restore delta sync
2. `app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt` - Enhanced sync strategy
3. `app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt` - Optional: adjust interval

**Files to keep as-is:**
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStoreImpl.kt`
- `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt`
