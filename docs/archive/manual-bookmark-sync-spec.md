# Mini Spec: Manual "Sync Bookmarks Now" Button

## Current Sync Architecture (Confirmed)

**Manual Sync** (Pull-to-refresh / App open):
- Triggers: `LoadBookmarksWorker` via `onPullToRefresh()` or on app startup
- Performs: **Delta sync only** - fetches bookmarks created/updated after last timestamp (`updatedSince` parameter)
- Behavior: Picks up new/updated bookmarks ✅ | Does NOT detect deletions ❌

**Scheduled Background Sync** (Periodic):
- Triggers: `FullSyncWorker` runs on schedule (hourly, daily, etc.)
- Performs TWO operations:
  1. **Deletion detection** (`bookmarkRepository.performFullSync()`) - Only runs if 24+ hours since last full sync
     - Fetches ALL bookmark IDs from server
     - Compares with local database
     - Deletes bookmarks that exist locally but not on server
  2. **Delta sync** (`loadBookmarksUseCase.execute()`) - Gets new/updated bookmarks

**Issue**: Users must wait for the scheduled sync (or 24 hours) to see deleted bookmarks disappear.

---

## Proposed Solution

**Add a "Sync Bookmarks Now" button** in the Sync Settings screen that performs an immediate full sync with deletion detection, bypassing the 24-hour gate.

---

## Implementation Requirements

### 1. **Backend Logic** (Minimal changes needed)

**Option A: Force Full Sync Flag** (Recommended)
- Modify `FullSyncWorker.doWork()` to accept an `INPUT_FORCE_FULL_SYNC` parameter
- When this flag is true, bypass the 24-hour check and always run `performFullSync()`
- Location: `/app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt:46-61`

```kotlin
// Current logic (line 46-61):
val needsFullSync = lastFullSyncTimestamp == null ||
    Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL

// Proposed change:
val forceFullSync = inputData.getBoolean(INPUT_FORCE_FULL_SYNC, false)
val needsFullSync = forceFullSync || lastFullSyncTimestamp == null ||
    Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL
```

- Add method to `FullSyncUseCase` to trigger forced sync:
```kotlin
fun performForcedFullSync() {
    val inputData = Data.Builder()
        .putBoolean(FullSyncWorker.INPUT_IS_MANUAL_SYNC, true)
        .putBoolean(FullSyncWorker.INPUT_FORCE_FULL_SYNC, true) // NEW
        .build()
    // ... rest of enqueue logic
}
```

**Option B: Direct Repository Call** (Alternative)
- Create a new use case that directly calls `bookmarkRepository.performFullSync()` followed by `loadBookmarksUseCase.execute()`
- Bypasses FullSyncWorker entirely for manual triggers
- Simpler but less consistent with existing architecture

---

### 2. **UI Changes**

**Location**: Sync Settings screen (`/app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsViewModel.kt`)

**Add**:
- Button labeled "Sync Bookmarks Now" (similar to "Download Content" button for date range)
- Display sync in progress indicator (reuse existing `fullSyncUseCase.syncIsRunning` flow)
- Optional: Show toast/snackbar on completion with count of deleted bookmarks

**ViewModel method**:
```kotlin
fun onClickSyncBookmarksNow() {
    fullSyncUseCase.performForcedFullSync() // or performFullSync() if using Option B
}
```

**UI State**:
- Add `isBookmarkSyncRunning: Boolean` to `SyncSettingsUiState`
- Observe `fullSyncUseCase.syncIsRunning` to disable button while running
- Position: Below "Bookmark Sync Frequency" setting, above "Content Sync" section

---

### 3. **Strings** (Localization Required per CLAUDE.md)

Add to `values/strings.xml` and all language files:
```xml
<string name="sync_settings_sync_bookmarks_now">Sync Bookmarks Now</string>
<string name="sync_settings_sync_bookmarks_now_description">Check for deleted bookmarks and sync all changes immediately</string>
<string name="sync_settings_sync_running">Syncing…</string>
```

---

## Benefits

✅ **User control**: No waiting for scheduled sync to see deletions
✅ **Non-disruptive**: Doesn't change scheduled sync behavior
✅ **Consistent**: Reuses existing `FullSyncWorker` infrastructure
✅ **Minimal code**: ~20-30 lines of new code + localization strings

---

## Testing Checklist

- [ ] Delete bookmark on server → tap "Sync Bookmarks Now" → bookmark removed in app
- [ ] Verify sync doesn't interfere with scheduled sync
- [ ] Verify button disabled while sync running
- [ ] Verify works on WiFi/cellular
- [ ] Verify all localization strings present

---

## Timeline Estimate

- Implementation: 1-2 hours
- Testing: 30 minutes
- Localization: 15 minutes (English placeholders per CLAUDE.md)

---

## Future Considerations

When Redek 0.22's `/api/sync` endpoint becomes available, this button can be updated to use the new endpoint without changing the UI.
