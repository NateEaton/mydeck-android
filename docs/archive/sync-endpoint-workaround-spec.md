# Implementation Spec: Workaround for Broken /api/bookmarks/sync Endpoint

## Problem Statement

The `GET /api/bookmarks/sync?since={timestamp}` endpoint returns HTTP 500 errors when the Readeck instance uses SQLite. This is a server-side issue that requires investigation and fixing in the Readeck codebase.

In the meantime, the Android app needs a workaround to ensure reliable sync functionality.

## Current Behavior

From `FullSyncWorker.kt:39-72`:

```kotlin
// Step 1: Handle deletions via delta sync or full sync
var syncResult = if (lastSyncTimestamp != null) {
    Timber.d("Performing delta sync since=$lastSyncTimestamp")
    bookmarkRepository.performDeltaSync(lastSyncTimestamp)  // ← FAILS with 500
} else {
    Timber.d("Performing full sync (no previous timestamp)")
    bookmarkRepository.performFullSync()
}

// If delta sync failed with an error (e.g., HTTP 500), fall back to full sync
if (syncResult is SyncResult.Error && lastSyncTimestamp != null) {
    Timber.w("Delta sync failed, falling back to full sync")
    syncResult = bookmarkRepository.performFullSync()  // ← This works!
}
```

**Current behavior:**
1. Try delta sync (fails with 500)
2. Fall back to full sync (succeeds)
3. Fetch updated bookmarks with `LoadBookmarksUseCase`

This works but is inefficient - every sync triggers a full bookmark list download.

## Solution: Skip the Broken Endpoint

Instead of attempting delta sync and failing, directly use the working approach:

1. **Always use full sync for deletion detection** (periodically)
2. **Use incremental bookmark loading** via `GET /api/bookmarks?updated_since=...` (which works perfectly)
3. **Make full syncs less frequent** to reduce overhead

## Implementation Changes

### Change 1: Modify FullSyncWorker Strategy

**File:** `app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt`

**Current code (lines 42-57):**
```kotlin
val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()

// Step 1: Handle deletions via delta sync or full sync
var syncResult = if (lastSyncTimestamp != null) {
    Timber.d("Performing delta sync since=$lastSyncTimestamp")
    bookmarkRepository.performDeltaSync(lastSyncTimestamp)
} else {
    Timber.d("Performing full sync (no previous timestamp)")
    bookmarkRepository.performFullSync()
}

// If delta sync failed with an error (e.g., HTTP 500), fall back to full sync
if (syncResult is SyncResult.Error && lastSyncTimestamp != null) {
    Timber.w("Delta sync failed, falling back to full sync")
    syncResult = bookmarkRepository.performFullSync()
}
```

**New code:**
```kotlin
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

**Add constant:**
```kotlin
companion object {
    const val UNIQUE_NAME_AUTO = "auto_full_sync_work"
    const val UNIQUE_NAME_MANUAL = "manual_full_sync_work"
    const val TAG = "full_sync"
    const val OUTPUT_DATA_COUNT = "count"
    const val NOTIFICATION_ID = 0
    const val INPUT_IS_MANUAL_SYNC = "is_manual_sync"
    val FULL_SYNC_INTERVAL = 24.hours  // Run full sync once per day
}
```

### Change 2: Add Full Sync Timestamp Tracking

**File:** `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`

Add two new methods:

```kotlin
suspend fun saveLastFullSyncTimestamp(timestamp: Instant) {
    dataStore.edit { preferences ->
        preferences[LAST_FULL_SYNC_TIMESTAMP] = timestamp.toString()
    }
}

suspend fun getLastFullSyncTimestamp(): Instant? {
    return dataStore.data.map { preferences ->
        preferences[LAST_FULL_SYNC_TIMESTAMP]?.let { Instant.parse(it) }
    }.first()
}
```

Add the preference key:
```kotlin
private val LAST_FULL_SYNC_TIMESTAMP = stringPreferencesKey("last_full_sync_timestamp")
```

### Change 3: Update LoadBookmarksUseCase (Already Works!)

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt`

**No changes needed.** The existing code at line 43 already uses the working endpoint:

```kotlin
val response = readeckApi.getBookmarks(
    pageSize,
    offset,
    lastLoadedTimestamp,  // This becomes the updated_since parameter
    ReadeckApi.SortOrder(ReadeckApi.Sort.Created)
)
```

This generates `GET /api/bookmarks?updated_since=...` which works perfectly (even with nanosecond precision).

### Change 4: Remove/Deprecate Delta Sync

**File:** `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt`

**Option A (Conservative):** Add a warning comment and disable it:
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

**Option B (Clean):** Remove the method entirely from the interface and all implementations (breaking change).

## Sync Strategy Summary

After implementation:

| Trigger | Deletion Detection | Bookmark Updates | Behavior |
|---------|-------------------|------------------|----------|
| **Pull-to-refresh** | No | Yes (incremental via `updated_since`) | Fast, lightweight |
| **Sync Now button** | Yes (if >24h since last full sync) | Yes (incremental) | Efficient |
| **Background auto-sync** | Yes (if >24h since last full sync) | Yes (incremental) | Efficient |

**Deletion detection frequency:** Once per 24 hours (configurable via `FULL_SYNC_INTERVAL`)

**Advantages:**
- No more 500 errors
- Deletion detection still works (just less frequently)
- Most syncs are incremental and fast
- Pull-to-refresh remains instant

**Trade-offs:**
- Deleted bookmarks may linger locally for up to 24 hours before being removed
- Full syncs are heavier (but only happen daily)

## Testing Plan

1. **Test incremental sync:** Pull-to-refresh multiple times, verify no 500 errors
2. **Test full sync:**
   - Clear `last_full_sync_timestamp` preference
   - Trigger sync, verify full sync runs
   - Delete a bookmark on server
   - Wait for next sync, verify local bookmark is removed
3. **Test sync interval:**
   - Trigger sync immediately after full sync
   - Verify it skips deletion detection (logs "Skipping deletion check")
   - Manually advance time by 24+ hours (or reduce interval for testing)
   - Verify full sync runs again

## Future Improvements

When the Readeck server bug is fixed:
1. Re-enable `performDeltaSync()`
2. Update `FullSyncWorker` to prefer delta sync
3. Keep the `FULL_SYNC_INTERVAL` logic as a safety fallback

## Migration Notes

**Preference key addition:** `last_full_sync_timestamp` (string, ISO-8601 format)

**No database schema changes required.**

**No breaking changes** if using Option A for delta sync deprecation.
