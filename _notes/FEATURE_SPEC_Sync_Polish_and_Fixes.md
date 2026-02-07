# MyDeck Android – Sync Polish & Remaining Fixes

**Post-Implementation Specification**

This document covers items remaining from the original Revised Sync Model spec and issues identified during testing of the Phase 1–3 implementation.

---

## 1. Sync Settings UI Polish

### 1.1 Heading Hierarchy

**Problem:** All section headings in `SyncSettingsScreen.kt` use the same `Typography.titleSmall` style, making it hard to distinguish section titles from sub-headings and labels.

**Fix:** Establish a clear visual hierarchy:

| Level | Current Style | New Style | Examples |
|-------|--------------|-----------|----------|
| Section title | `Typography.titleSmall` | `Typography.titleMedium` | "Bookmark Sync", "Content Sync", "Download Constraints", "Sync Status" |
| Sub-heading / field label | `Typography.bodyMedium` / default `Text` | `Typography.bodyMedium` (unchanged) | "Sync frequency", radio option titles |
| Description text | `Typography.bodySmall` | `Typography.bodySmall` (unchanged) | "Bookmarks are always kept in sync.", radio descriptions |

**Files to modify:**
- `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsScreen.kt`

**Changes:**

In `BookmarkSyncSection`, `ContentSyncSection`, `ConstraintsSection`, and `SyncStatusSection`, change the section title `Text` style from `Typography.titleSmall` to `Typography.titleMedium`:

```kotlin
// BEFORE:
Text(
    text = stringResource(R.string.sync_bookmark_section_title),
    style = Typography.titleSmall
)

// AFTER:
Text(
    text = stringResource(R.string.sync_bookmark_section_title),
    style = Typography.titleMedium
)
```

Apply this same change to all four section title `Text` composables.

Inside the Sync Status card, the sub-headings "Bookmarks" and "Content" currently use `Typography.labelMedium`. These should remain smaller than section titles but visually distinct from data rows. Change to `Typography.titleSmall`:

```kotlin
// BEFORE:
Text(
    text = "Bookmarks",
    style = Typography.labelMedium,
    color = MaterialTheme.colorScheme.primary
)

// AFTER:
Text(
    text = stringResource(R.string.sync_status_bookmarks_heading),
    style = Typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
)
```

Apply this to both the "Bookmarks" and "Content" sub-headings inside the card.

---

### 1.2 Bookmark Sync Description

**Problem:** The current description "Bookmarks are always kept in sync." doesn't explain what bookmark sync actually does. Users may not understand the distinction between bookmark metadata and content.

**Fix:** Update the description string to clarify that bookmark sync keeps the local bookmark list up to date with changes made on other devices or the server.

**File to modify:**
- `app/src/main/res/values/strings.xml` (and all locale files)

**Change:**

```xml
<!-- BEFORE: -->
<string name="sync_bookmark_description">Bookmarks are always kept in sync.</string>

<!-- AFTER: -->
<string name="sync_bookmark_description">Your bookmark list is automatically kept up to date with changes from other devices and the server.</string>
```

---

### 1.3 Rename "Unread" to "My List" in Sync Status

**Problem:** The sync status shows "Unread: N" but the app's primary list view follows a Pocket-like UX where the main list is called "My List" (all non-archived bookmarks with `readProgress < 100`). The label should match.

**Fix:** Update the string resource.

**File to modify:**
- `app/src/main/res/values/strings.xml` (and all locale files)

**Change:**

```xml
<!-- BEFORE: -->
<string name="sync_status_unread">Unread: %d</string>

<!-- AFTER: -->
<string name="sync_status_unread">My List: %d</string>
```

---

### 1.4 Last Sync Timestamp Placement

**Problem:** The "Last sync" timestamp currently appears at the bottom of the Sync Status card, below the Content section. However, this timestamp tracks the last *bookmark metadata* sync, not the last content sync. Its placement under Content is misleading.

**Fix:**
1. Move the existing "Last sync" timestamp to display under the Bookmarks sub-section of the status card
2. Add a separate "Last content sync" timestamp under the Content sub-section

**Files to modify:**
- `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsScreen.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsViewModel.kt`
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt` (interface)
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStoreImpl.kt` (implementation)
- `app/src/main/res/values/strings.xml` (and locale files)
- Workers that perform content sync (to record timestamp)

**Data model changes:**

In `SyncStatus` and `SyncSettingsUiState`, replace the single timestamp with two:

```kotlin
@Immutable
data class SyncStatus(
    // ... existing fields ...
    val lastBookmarkSyncTimestamp: String? = null,
    val lastContentSyncTimestamp: String? = null
)
```

**SettingsDataStore changes:**

Add a new preference for the last content sync timestamp:

```kotlin
suspend fun getLastContentSyncTimestamp(): kotlinx.datetime.Instant?
suspend fun saveLastContentSyncTimestamp(timestamp: kotlinx.datetime.Instant)
```

**Worker changes:**

After content sync operations complete successfully, save the content sync timestamp:
- In `BatchArticleLoadWorker` — after processing all batches
- In `DateRangeContentSyncWorker` — after processing all eligible bookmarks

```kotlin
// At end of successful content sync:
settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())
```

**UI changes in `SyncStatusSection`:**

```kotlin
// Bookmarks sub-section
Text(
    text = stringResource(R.string.sync_status_bookmarks_heading),
    style = Typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
)
// ... bookmark count rows ...
syncStatus.lastBookmarkSyncTimestamp?.let { ts ->
    Text(
        text = stringResource(R.string.sync_status_last_sync, ts),
        style = Typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

Spacer(modifier = Modifier.height(8.dp))

// Content sub-section
Text(
    text = stringResource(R.string.sync_status_content_heading),
    style = Typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
)
// ... content count rows ...
syncStatus.lastContentSyncTimestamp?.let { ts ->
    Text(
        text = stringResource(R.string.sync_status_last_content_sync, ts),
        style = Typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

**New string resources:**

```xml
<string name="sync_status_bookmarks_heading">Bookmarks</string>
<string name="sync_status_content_heading">Content</string>
<string name="sync_status_last_content_sync">Last content sync: %1$s</string>
```

---

## 2. Archive Thumbnails Missing When Offline

### 2.1 Problem

Bookmark thumbnails are not loaded/cached during initial sync. When the device goes offline, bookmarks in both My List and Archive show no thumbnails because the image URLs point to the Readeck server, which is unreachable.

### 2.2 Expected Behavior

On initial login and subsequent bookmark syncs, thumbnail images for all bookmarks (both active and archived) should be cached locally so they display when offline.

### 2.3 Investigation Required

Before implementing, determine the current image loading stack:
- What library is used for image loading? (Likely Coil or Glide)
- Are thumbnails served from `thumbnail_src` on `BookmarkEntity`?
- Does the image loader have disk caching enabled?
- Are the URLs relative (requiring the server base URL) or absolute?

### 2.4 Likely Implementation

Assuming Coil is the image loader (common in Compose apps):

**Option A: Pre-warm the disk cache during sync**

After bookmark metadata is saved during sync, iterate through bookmarks and enqueue image prefetch requests:

```kotlin
// In LoadBookmarksUseCase or a new ThumbnailPrefetchUseCase:
val imageLoader = context.imageLoader
bookmarks.forEach { bookmark ->
    bookmark.thumbnailSrc?.let { url ->
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED) // Only disk cache
            .build()
        imageLoader.enqueue(request)
    }
    bookmark.iconSrc?.let { url ->
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        imageLoader.enqueue(request)
    }
}
```

**Option B: Ensure the image loader's disk cache is sufficient**

If the image loader already has disk caching enabled, thumbnails may already be cached for bookmarks the user has scrolled past. The issue may be that archived bookmarks are never scrolled, so their thumbnails are never loaded.

In this case, the fix is Option A — explicitly prefetch during sync.

**Constraints:**
- Thumbnail prefetch should respect the same Wi-Fi/battery constraints as content sync
- Prefetching should run at lower priority than content downloads
- Consider limiting batch size to avoid overwhelming the network

**Files likely to modify:**
- New file: `app/src/main/java/com/mydeck/app/domain/usecase/ThumbnailPrefetchUseCase.kt`
- `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt` (call prefetch after metadata sync)
- Image loader configuration (ensure adequate disk cache size)

---

## 3. Remaining Items from Original Spec

### 3.1 DAO Insert Strategy Deviation

**Original spec recommended:** `IGNORE + UPDATE` (Appendix A, Section 1.2.1)

**What was implemented:** Read-preserve-REPLACE — the `insertBookmarkWithArticleContent` transaction reads existing `contentState` and `articleContent` before the REPLACE, then restores them after insert.

**Assessment:** The current approach works correctly and preserves content during sync. However, the IGNORE+UPDATE approach would be architecturally cleaner since it avoids the CASCADE DELETE entirely rather than working around it. Consider refactoring in a future cleanup pass if the current approach causes issues.

**No action required** unless problems are observed.

### 3.2 Parameter Validation in DateRangeContentSyncWorker

**Original spec:** Appendix C, item 3 — Log a warning when `fromEpoch` and `toEpoch` are both 0.

**File to modify:**
- `app/src/main/java/com/mydeck/app/worker/DateRangeContentSyncWorker.kt`

**Change:**

```kotlin
override suspend fun doWork(): Result {
    val fromEpoch = inputData.getLong(PARAM_FROM_EPOCH, 0)
    val toEpoch = inputData.getLong(PARAM_TO_EPOCH, 0)

    if (fromEpoch == 0L && toEpoch == 0L) {
        Timber.w("DateRangeContentSyncWorker: both fromEpoch and toEpoch are 0 — likely missing input params")
        return Result.success()
    }

    // ... rest of method
}
```

### 3.3 Sync Status Card Sub-Headings Should Use String Resources

**Problem:** The "Bookmarks" and "Content" labels inside the Sync Status card are hardcoded strings instead of using string resources.

**File to modify:**
- `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsScreen.kt`

**Change:**

Replace hardcoded `"Bookmarks"` and `"Content"` text with `stringResource(R.string.sync_status_bookmarks_heading)` and `stringResource(R.string.sync_status_content_heading)`.

These string resources are defined in Section 1.4 above.

---

## 4. Summary of Changes

| Item | Section | Priority | Complexity |
|------|---------|----------|------------|
| Heading hierarchy | 1.1 | Medium | Low |
| Bookmark sync description | 1.2 | Low | Low |
| "Unread" → "My List" | 1.3 | Medium | Low |
| Last sync timestamp placement | 1.4 | Medium | Medium |
| Archive thumbnails offline | 2 | High | Medium-High |
| DateRangeWorker param validation | 3.2 | Low | Low |
| Hardcoded sub-heading strings | 3.3 | Low | Low |

### Suggested Implementation Order

1. **Heading hierarchy + hardcoded strings** (1.1 + 3.3) — Quick UI fix, all in `SyncSettingsScreen.kt`
2. **"Unread" → "My List" + bookmark sync description** (1.3 + 1.2) — String resource changes only
3. **Last sync timestamp split** (1.4) — Touches ViewModel, DataStore, workers, and UI
4. **DateRangeWorker param validation** (3.2) — Trivial one-liner
5. **Archive thumbnails offline** (2) — Requires investigation and is the most complex item
