# MyDeck Android – Revised Sync Model

**Design & Implementation Specification**

---

## 1. Goals & Non-Goals

### 1.1 Goals

* Align sync behavior with **Pocket's UX model** while respecting **Readeck's immutable-content philosophy**
* Decouple **bookmark metadata sync** from **content sync**
* Minimize unnecessary background work, bandwidth, and storage
* Improve user understanding of:
  * online/offline state
  * sync status
  * why some bookmarks do not (and will never) have content
* Enable intentional offline preparation via **date-range content download**

### 1.2 Non-Goals

* No use of Readeck's `/api/sync` endpoint (server-side SQLite bug; see `BookmarkRepositoryImpl.performDeltaSync`)
* No automatic re-fetch or refresh of already-downloaded content
* No "global Sync Now" button
* No notification spam (notifications are optional and limited to long-running date-range sync completion, if implemented later)

---

## 2. Conceptual Model (Key Paradigm Shift)

### 2.1 Core Principle

**Bookmarks are live and cheap.
Content is immutable and expensive.**

### 2.2 High-Level Separation

| Concern           | Behavior                                          |
| ----------------- | ------------------------------------------------- |
| Bookmark metadata | Always synced automatically                       |
| Content           | Synced based on explicit user policy or on-demand |
| Sync triggers     | Contextual (app open, pull-to-refresh, read view) |
| Retry behavior    | Explicit and visible                              |

This replaces the current "everything syncs together" approach.

---

## 3. Revised Sync State Machine

### 3.1 Bookmark State Machine

```
┌──────────────┐
│ Not Present  │
└──────┬───────┘
       │ metadata sync
       ▼
┌──────────────┐
│ Present      │
│ (metadata)   │
└──────┬───────┘
       │ deleted on server
       ▼
┌──────────────┐
│ Deleted      │
└──────────────┘
```

* Bookmark metadata is:
  * fetched on login
  * fetched on app open
  * fetched on pull-to-refresh
* Bookmark sync is **non-configurable**, except for periodic frequency while app is open.

---

### 3.2 Content State Machine (Per Bookmark)

```
┌─────────────────────┐
│ No Content Attempted│
└──────────┬──────────┘
           │ fetch content
           ▼
┌─────────────────────┐
│ Content Downloaded  │◄────────────┐
└─────────────────────┘             │
                                     │ (no re-fetch)
┌─────────────────────┐             │
│ Fetch Failed (Dirty)│─────────────┘
└──────────┬──────────┘
           │ retry
           ▼
┌─────────────────────┐
│ Permanent No Content│
│ (media / invalid /  │
│ extraction failure) │
└─────────────────────┘
```

#### Notes:

* **Dirty** means:
  * transient failure
  * eligible for retry
* **Permanent No Content** includes:
  * video-only bookmarks
  * image-only bookmarks
  * unreachable or invalid URLs
  * Readeck extraction failure with error flag
* Once content is successfully downloaded, it is **never fetched again**

---

## 4. Sync Triggers & Behavior

### 4.1 Bookmark Sync (Metadata)

**Triggers**

* Initial login
* App open
* Pull-to-refresh on list view
* Periodic while app is open (default: 1 hour)

**Behavior**

* Fetch bookmark IDs + metadata
* Insert new bookmarks
* Remove deleted bookmarks
* Update metadata fields only
* No content fetch performed unless explicitly allowed by content policy

---

### 4.2 Content Sync

Content sync behavior is controlled by **Content Sync Policy** (see Section 5).

#### Trigger Matrix

| Trigger               | Automatic        | Manual           | Date Range       |
| --------------------- | ---------------- | ---------------- | ---------------- |
| Bookmark sync         | Fetch content    | No               | No               |
| Open reading view     | Fetch if missing | Fetch if missing | Fetch if missing |
| Pull-to-refresh       | Fetch content    | No               | No               |
| Date-range "Download" | N/A              | N/A              | Fetch            |

---

### 4.3 Reading View On-Demand Fetch

When a bookmark is opened:

1. If content exists locally → render article view
2. If content does not exist:
   * Attempt content fetch
   * Show spinner / loading indicator
3. If fetch succeeds → render article view
4. If fetch fails:
   * Mark bookmark **dirty**
   * Show retry control in reading view
5. If server reports no content available:
   * Switch automatically to **Original (Web) view**
   * Reading view supports two modes:
     * **Article** (Readeck content)
     * **Original** (embedded WebView)

---

## 5. Settings | Sync – Revised UX & Behavior

### 5.1 Structure

**Settings → Sync**

#### Section 1: Bookmark Sync

* Description: "Bookmarks are always kept in sync."
* Option:
  * Sync frequency while app is open
    * Default: once per hour
    * Existing interval options reused
* No manual option
* No disable option

---

#### Section 2: Content Sync

**Options (radio-style selection):**

1. **Automatic**
   * Content is fetched during bookmark sync
   * Requires background sync permission
2. **Manual**
   * No batch content sync
   * Content fetched only when opening a bookmark
3. **Date Range**
   * Two date pickers:
     * Added from
     * Added to
   * Grouped **Download** button:
     * Label: "Download"
     * Scope: content for bookmarks added in selected range
   * On-demand only

---

#### Section 3: Constraints

* Only download content on Wi-Fi
* Allow content download when battery saver is enabled (toggle)

---

## 6. Permissions & Background Work

### 6.1 When to Prompt

Prompt for background sync permission only when:

* User selects **Automatic** content sync
* User taps **Download** in Date Range sync

### 6.2 Rationale Displayed to User

* Explain:
  * why background work is needed
  * that content downloads may continue while app is closed
* Prompt only once per scenario

---

## 7. Offline / Online Indicator

### 7.1 Location

* Navigation drawer header (next to app name)

### 7.2 Behavior

* Icon-only indicator
* Tooltip on long press:
  * "Offline – unable to reach server"
* Shown when:
  * no network connectivity
  * or server unreachable

No text labels. No list-header clutter.

---

## 8. Sync Status & Reporting

### 8.1 Visual Treatment

* Sync status block visually separated from other settings
  * border or card-style container

### 8.2 Metrics Displayed

**Bookmarks**

* Total bookmarks
* Unread
* Archived
* Favorites

**Content**

* Content downloaded
* Content available to download
* Content download failed (dirty)
* Bookmarks without article content (video/photo)
* Invalid or unreachable URLs
* Extraction errors reported by server

Counts are derived from existing bookmark fields and Readeck flags.

---

## 9. Failure Handling & Retry Semantics

### 9.1 Dirty State

A bookmark is marked **dirty** when:

* content fetch fails
* network error occurs
* server returns transient error

Dirty bookmarks:

* are eligible for retry on next content sync
* can be retried manually from reading view

### 9.2 Reading View Retry

* Visible retry control when content fetch fails
* Retry attempts respect:
  * Wi-Fi setting
  * battery saver setting
  * offline state

---

## 10. Current Codebase – Architecture Baseline

This section documents the current state of the codebase as of the start of implementation. Understanding what exists (and what's broken) is essential context for the implementation plan.

### 10.1 Existing Components & Their Roles

| Component | File | Current Role |
|-----------|------|-------------|
| `FullSyncWorker` | `worker/FullSyncWorker.kt` | Performs deletion detection (full ID comparison) then calls `LoadBookmarksUseCase` which fetches metadata **and** enqueues content loading. Handles both periodic (auto) and manual (one-shot) sync. |
| `LoadBookmarksWorker` | `worker/LoadBookmarksWorker.kt` | Incremental metadata sync with pagination. Delegates to `LoadBookmarksUseCase`. |
| `LoadBookmarksUseCase` | `domain/usecase/LoadBookmarksUseCase.kt` | Fetches bookmarks page-by-page from Readeck API, saves via `bookmarkRepository.insertBookmarks()`, then **always enqueues `BatchArticleLoadWorker`** on completion (line 85). This is the primary coupling point between metadata and content sync. |
| `BatchArticleLoadWorker` | `worker/BatchArticleLoadWorker.kt` | Queries `getBookmarkIdsWithoutContent()` and fetches articles in batches of 5 via `LoadArticleUseCase`. |
| `LoadArticleUseCase` | `domain/usecase/LoadArticleUseCase.kt` | Fetches a single article's content from the API and saves it via `bookmarkRepository.insertBookmarks()`. |
| `LoadArticleWorker` | `worker/LoadArticleWorker.kt` | One-shot WorkManager wrapper around `LoadArticleUseCase` for individual article downloads. |
| `BookmarkDao` | `io/db/dao/BookmarkDao.kt` | Room DAO. Uses `OnConflictStrategy.REPLACE` for bookmark inserts (lines 44, 62). |
| `ArticleContentEntity` | `io/db/model/ArticleContentEntity.kt` | Separate table with `ForeignKey(onDelete = CASCADE)` to `bookmarks`. |
| `BookmarkRepositoryImpl` | `domain/BookmarkRepositoryImpl.kt` | Repository implementation. `insertBookmarks()` delegates to `bookmarkDao.insertBookmarksWithArticleContent()`. `performFullSync()` handles deletion detection via temporary `remote_bookmark_ids` table. |
| `BookmarkListViewModel` | `ui/list/BookmarkListViewModel.kt` | Contains sync-on-app-open trigger in `init` block (line 187-193) and the shared-text dialog loop bug (line 123-137). |
| `BookmarkDetailViewModel` | `ui/detail/BookmarkDetailViewModel.kt` | Loads bookmark + article content via `observeBookmark()`. No on-demand content fetch — just renders whatever is in the DB. |
| `SyncSettingsScreen` | `ui/settings/SyncSettingsScreen.kt` | Current sync settings UI with auto-sync toggle, schedule selector, notifications toggle, sync-on-app-open toggle, and a "Sync Now" button. |
| `SettingsDataStore` | `io/prefs/SettingsDataStore.kt` | Encrypted SharedPreferences storing sync preferences: `autoSyncEnabled`, `autoSyncTimeframe`, `syncOnAppOpenEnabled`, `syncNotificationsEnabled`, timestamps, etc. |

### 10.2 Known Bugs (Prerequisites — Must Be Fixed Before or During Phase 1)

These bugs are documented in `_notes/FIX_Dialog_Loop_and_Sync_Issues.md` and remain **unfixed** in the codebase:

#### Bug 1: Content Invalidation via CASCADE Delete

**Location:** `BookmarkDao.kt` lines 44, 62 — `@Insert(onConflict = OnConflictStrategy.REPLACE)`

**Mechanism:** When a bookmark is re-inserted during sync, Room's `REPLACE` strategy deletes the existing row and inserts a new one. Because `ArticleContentEntity` has `ForeignKey(onDelete = CASCADE)`, the associated article content is automatically deleted. This means **every metadata sync destroys all previously downloaded article content**.

**Impact:** Critical data loss. Users lose offline content silently during background sync.

#### Bug 2: Add Bookmark Dialog Loop

**Location:** `BookmarkListViewModel.kt` lines 123-137

**Mechanism:** The `init` block reads `sharedText` from `SavedStateHandle` but never removes it. On process death + restore, the ViewModel recreates, reads the persisted value again, and reopens the dialog.

#### Bug 3: Sync Triggered on ViewModel Recreation

**Location:** `BookmarkListViewModel.kt` lines 187-193

**Mechanism:** Sync-on-app-open logic lives in the ViewModel's `init` block. This ViewModel is scoped to `BookmarkListRoute`, so it recreates on configuration changes and process restoration — triggering unnecessary syncs.

### 10.3 Database Schema (Current)

**`bookmarks` table** (`BookmarkEntity.kt`):
```
id (PK), href, created, updated, state (LOADED/ERROR/LOADING), loaded,
url, title, siteName, site, authors, lang, textDirection, documentTpe,
type (article/video/photo), hasArticle, description, isDeleted, isMarked,
isArchived, labels, readProgress, wordCount, readingTime, published,
embed, embedHostname,
article_* (ResourceEntity), icon_* (ImageResourceEntity),
image_* (ImageResourceEntity), log_* (ResourceEntity),
props_* (ResourceEntity), thumbnail_* (ImageResourceEntity)
```

**`article_content` table** (`ArticleContentEntity.kt`):
```
bookmarkId (PK, FK → bookmarks.id, onDelete=CASCADE), content (String)
```

**`remote_bookmark_ids` table** (`RemoteBookmarkIdEntity.kt`):
```
id (PK) — temporary table used during full sync deletion detection
```

**Key observations:**
- No content state tracking fields (no dirty flag, no failure reason, no permanent-no-content marker)
- `hasArticle` on `BookmarkEntity` is a server-provided flag indicating whether Readeck extracted article content — it does NOT indicate whether the app has downloaded it locally
- Content presence can only be inferred by joining `article_content` (e.g., `getBookmarkIdsWithoutContent()`)

### 10.4 Sync Settings (Current Preferences)

| Key | Type | Purpose |
|-----|------|---------|
| `autoSyncEnabled` | Boolean | Master toggle for periodic background sync |
| `autoSyncTimeframe` | Enum | Schedule: MANUAL, 1H, 6H, 12H, 1D, 7D, 14D, 30D |
| `syncOnAppOpenEnabled` | Boolean | Trigger full sync on app foreground |
| `syncNotificationsEnabled` | Boolean | Show notification on sync completion |
| `lastSyncTimestamp` | Instant | Last incremental sync time |
| `lastFullSyncTimestamp` | Instant | Last full sync (deletion detection) time |
| `lastBookmarkTimestamp` | Instant | Cursor for incremental bookmark fetching |
| `initialSyncPerformed` | Boolean | Whether first sync has completed |

---

## 11. Migration & Compatibility Considerations

* Existing users with downloaded content:
  * Content remains valid
  * No re-fetch required
* Existing sync interval settings:
  * Reused for bookmark sync
* Existing manual sync button:
  * Removed
  * Pull-to-refresh becomes primary manual trigger

---

## 12. Summary

This design:

* Reduces unnecessary background work
* Improves perceived performance
* Matches Pocket's successful UX patterns
* Respects Readeck's architectural philosophy
* Clarifies sync state for users and developers alike

It is a **refactor**, not a rewrite, and leverages existing infrastructure while significantly improving clarity, efficiency, and user trust.

---

# Implementation Plan

## Phasing Rationale

The original spec proposed 7 phases. This was overly conservative — several phases (permissions, offline indicator, migration cleanup, settings UI, policy layer) are too thin to stand alone and create unnecessary integration overhead when split apart. The revised plan consolidates into **3 phases**, each producing a testable, shippable increment:

| Phase | Focus | Summary |
|-------|-------|---------|
| 1 | **Foundation** | Fix critical bugs, isolate metadata sync, add content state tracking |
| 2 | **Content Policy + Reading View** | Policy engine, on-demand fetch, retry, constraints |
| 3 | **UI Overhaul** | Settings redesign, sync status, offline indicator, permissions |

Each phase must be fully tested before beginning the next.

---

## Phase 1: Foundation (Bug Fixes + Metadata Sync Isolation + Content State Tracking)

### 1.1 Goals

* Fix the three critical bugs documented in Section 10.2
* Decouple metadata sync from content sync at the worker level
* Introduce content state tracking in the database
* Ensure existing content is preserved during sync operations

### 1.2 Deliverables

#### 1.2.1 Fix: BookmarkDao INSERT Strategy (Content Invalidation)

**Files to modify:**
- `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

**Changes:**

1. Replace bulk insert method:
```kotlin
// REMOVE:
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

// ADD:
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertBookmarksIgnore(bookmarks: List<BookmarkEntity>): List<Long>

@Update
suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)

@Transaction
suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>) {
    val insertResults = insertBookmarksIgnore(bookmarks)
    val toUpdate = bookmarks.filterIndexed { index, _ -> insertResults[index] == -1L }
    if (toUpdate.isNotEmpty()) {
        updateBookmarks(toUpdate)
    }
}
```

2. Replace single insert method:
```kotlin
// REMOVE:
@Insert(onConflict = REPLACE)
suspend fun insertBookmark(bookmarkEntity: BookmarkEntity)

// ADD:
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertBookmarkIgnore(bookmarkEntity: BookmarkEntity): Long

@Update
suspend fun updateBookmark(bookmarkEntity: BookmarkEntity)

@Transaction
suspend fun upsertBookmark(bookmarkEntity: BookmarkEntity) {
    val rowId = insertBookmarkIgnore(bookmarkEntity)
    if (rowId == -1L) {
        updateBookmark(bookmarkEntity)
    }
}
```

3. Update `insertBookmarkWithArticleContent` transaction:
```kotlin
@Transaction
suspend fun insertBookmarkWithArticleContent(bookmarkWithArticleContent: BookmarkWithArticleContent) {
    with(bookmarkWithArticleContent) {
        upsertBookmark(bookmark)
        articleContent?.run { insertArticleContent(this) }
    }
}
```

4. Update all call sites that use `insertBookmark()` or `insertBookmarks()`:
   - `BookmarkRepositoryImpl.renameLabel()` (line 673) — change `insertBookmark(updatedBookmark)` → `upsertBookmark(updatedBookmark)`
   - `BookmarkRepositoryImpl.deleteLabel()` (line 723) — same change
   - Any other internal callers

**Article content insert can remain REPLACE** (`insertArticleContent` at line 132) because article content is truly an upsert — we only write it once, and if we write again it means we're intentionally replacing (e.g., the content was re-fetched after a dirty state).

**Verification:**
- Write a test: insert bookmark with content, then re-insert same bookmark with updated metadata (no content). Assert article content still exists.
- Write a test: insert bookmark, then upsert with changed title. Assert title is updated and article content is preserved.

#### 1.2.2 Fix: Dialog Loop (SavedStateHandle)

**File:** `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`

**Change at lines 122-137:**
```kotlin
// BEFORE:
savedStateHandle.get<String>("sharedText").takeIf { it != null }?.let {
    val sharedText = it.extractUrlAndTitle()
    // ...
}

// AFTER:
savedStateHandle.get<String>("sharedText")?.let { raw ->
    savedStateHandle.remove<String>("sharedText") // Consume immediately
    val sharedText = raw.extractUrlAndTitle()
    val urlError = if (sharedText == null) {
        R.string.account_settings_url_error
    } else {
        null
    }
    _createBookmarkUiState.value = CreateBookmarkUiState.Open(
        title = sharedText?.title ?: "",
        url = sharedText?.url ?: "",
        urlError = urlError,
        isCreateEnabled = urlError == null
    )
}
```

**Verification:**
- Manual test: share a URL to the app, background it, kill process via ADB, restore. Dialog should NOT reappear.

#### 1.2.3 Fix: Move Sync-on-App-Open to MainActivity

**File:** `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`

Remove lines 187-193 (the `viewModelScope.launch { if (settingsDataStore.isSyncOnAppOpenEnabled()) ... }` block).

**File:** `app/src/main/java/com/mydeck/app/MainActivity.kt`

Add `FullSyncUseCase` injection and cold-start sync trigger:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var fullSyncUseCase: FullSyncUseCase

    private lateinit var intentState: MutableState<Intent?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sync on app open — only on cold start, not on config change or process restore
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (settingsDataStore.isSyncOnAppOpenEnabled()) {
                    Timber.d("App Open: Triggering sync")
                    fullSyncUseCase.performFullSync()
                }
            }
        }

        setContent {
            // ... existing content ...
        }
    }
    // ... rest unchanged ...
}
```

**Required imports:** `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.launch`

**Verification:**
- Set sync-on-app-open enabled. Cold start app → sync triggered (check Timber logs).
- Rotate device → sync NOT re-triggered.
- Background + restore → sync NOT re-triggered.

#### 1.2.4 Decouple Metadata Sync from Content Sync

**Goal:** `LoadBookmarksUseCase` must stop automatically enqueuing `BatchArticleLoadWorker`. Content loading must be a separate, policy-driven decision.

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt`

1. Remove `enqueueBatchArticleLoader()` private method (lines 94-114)
2. Remove the call `enqueueBatchArticleLoader()` at line 85
3. Remove the `workManager` constructor parameter (it's only used for batch article enqueueing)
4. Remove the `BatchArticleLoadWorker` import

**After this change**, bookmark sync (metadata) will complete without triggering any content downloads.

**Temporary compatibility:** During Phase 1, users on "Automatic" content policy (which doesn't exist yet as a setting) will not get automatic content downloads. This is acceptable because:
- The existing behavior was actively destroying content (Bug 1)
- Content can still be loaded on-demand when opening a bookmark (existing `LoadArticleWorker` path)
- Phase 2 restores automatic content loading behind the new policy engine

**File:** `app/src/main/java/com/mydeck/app/worker/FullSyncWorker.kt`

No changes needed — it already delegates to `LoadBookmarksUseCase` which will now be metadata-only.

**Verification:**
- Trigger a sync. Assert no `BatchArticleLoadWorker` is enqueued (check WorkManager state or Timber logs).
- Assert bookmark metadata is still correctly inserted/updated.
- Assert existing article content is NOT deleted during sync (combined with Bug 1 fix).

#### 1.2.5 Add Content State Tracking to Database

**New enum — Content State:**

**File:** `app/src/main/java/com/mydeck/app/io/db/model/BookmarkEntity.kt`

Add to the `BookmarkEntity` class:

```kotlin
enum class ContentState(val value: Int) {
    NOT_ATTEMPTED(0),   // Content has never been fetched
    DOWNLOADED(1),      // Content successfully downloaded and stored
    DIRTY(2),           // Fetch failed with transient error, eligible for retry
    PERMANENT_NO_CONTENT(3) // Content will never be available (wrong type, extraction failure, etc.)
}
```

Add new fields to `BookmarkEntity`:

```kotlin
val contentState: ContentState = ContentState.NOT_ATTEMPTED,
val contentFailureReason: String? = null  // Human-readable reason for DIRTY or PERMANENT_NO_CONTENT
```

**Database migration:**

**File:** Create `app/src/main/java/com/mydeck/app/io/db/migration/Migration_X_Y.kt` (version numbers depend on current schema version)

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add content state columns with defaults
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN contentState INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN contentFailureReason TEXT DEFAULT NULL")

        // Backfill: mark bookmarks that already have content as DOWNLOADED
        db.execSQL("""
            UPDATE bookmarks SET contentState = 1
            WHERE EXISTS (SELECT 1 FROM article_content WHERE article_content.bookmarkId = bookmarks.id)
        """)

        // Backfill: mark non-article types as PERMANENT_NO_CONTENT
        // (photos and videos don't have extractable article content)
        db.execSQL("""
            UPDATE bookmarks SET contentState = 3, contentFailureReason = 'Non-article type'
            WHERE type IN ('photo', 'video') AND contentState = 0
        """)

        // Backfill: mark bookmarks where server says no article as PERMANENT_NO_CONTENT
        db.execSQL("""
            UPDATE bookmarks SET contentState = 3, contentFailureReason = 'No article available on server'
            WHERE hasArticle = 0 AND type = 'article' AND contentState = 0
        """)
    }
}
```

Register the migration in the Room database builder (in `AppDatabase.kt` or wherever the database is configured).

**Update content fetch pipeline to set state:**

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt`

```kotlin
class LoadArticleUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val bookmarkDao: BookmarkDao
) {
    sealed class Result {
        data object Success : Result()
        data object AlreadyDownloaded : Result()
        data class TransientFailure(val reason: String) : Result()
        data class PermanentFailure(val reason: String) : Result()
    }

    suspend fun execute(bookmarkId: String): Result {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

        // Guard: never re-fetch downloaded content
        if (bookmark.contentState == ContentState.DOWNLOADED) {
            return Result.AlreadyDownloaded
        }

        // Guard: don't attempt for permanent no-content
        if (bookmark.contentState == ContentState.PERMANENT_NO_CONTENT) {
            return Result.PermanentFailure(bookmark.contentFailureReason ?: "No content available")
        }

        if (!bookmark.hasArticle) {
            // Server says no article content — mark permanent
            bookmarkDao.updateContentState(
                bookmarkId, ContentState.PERMANENT_NO_CONTENT.value,
                "No article content available (type=${bookmark.type})"
            )
            Timber.i("Bookmark has no article [type=${bookmark.type}]")
            return Result.PermanentFailure("No article content available")
        }

        return try {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!
                val bookmarkToSave = bookmark.copy(
                    articleContent = content,
                    contentState = ContentState.DOWNLOADED,
                    contentFailureReason = null
                )
                bookmarkRepository.insertBookmarks(listOf(bookmarkToSave))
                Result.Success
            } else {
                val reason = "HTTP ${response.code()}"
                bookmarkDao.updateContentState(bookmarkId, ContentState.DIRTY.value, reason)
                Timber.w("Content fetch failed for $bookmarkId: $reason")
                Result.TransientFailure(reason)
            }
        } catch (e: IOException) {
            val reason = "Network error: ${e.message}"
            bookmarkDao.updateContentState(bookmarkId, ContentState.DIRTY.value, reason)
            Timber.w(e, "Content fetch network error for $bookmarkId")
            Result.TransientFailure(reason)
        } catch (e: Exception) {
            val reason = "Unexpected error: ${e.message}"
            bookmarkDao.updateContentState(bookmarkId, ContentState.DIRTY.value, reason)
            Timber.e(e, "Content fetch unexpected error for $bookmarkId")
            Result.TransientFailure(reason)
        }
    }
}
```

**New DAO method:**

**File:** `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

```kotlin
@Query("UPDATE bookmarks SET contentState = :state, contentFailureReason = :reason WHERE id = :id")
suspend fun updateContentState(id: String, state: Int, reason: String?)
```

**Update `BatchArticleLoadWorker` to respect content state:**

**File:** `app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt`

Replace `getBookmarkIdsWithoutContent()` usage with a new query that respects content state:

```kotlin
// In BookmarkDao, replace or supplement:
@Query("""
    SELECT b.id FROM bookmarks b
    WHERE b.contentState IN (0, 2)
    AND b.hasArticle = 1
    ORDER BY b.created DESC
""")
suspend fun getBookmarkIdsEligibleForContentFetch(): List<String>
```

This returns bookmarks that are either NOT_ATTEMPTED (0) or DIRTY (2), and where the server indicates article content exists.

#### 1.2.6 Update Domain Model

**File:** `app/src/main/java/com/mydeck/app/domain/model/Bookmark.kt`

Add to the `Bookmark` domain model:

```kotlin
val contentState: ContentState = ContentState.NOT_ATTEMPTED,
val contentFailureReason: String? = null
```

Where `ContentState` is an enum mirroring the entity enum:
```kotlin
enum class ContentState {
    NOT_ATTEMPTED, DOWNLOADED, DIRTY, PERMANENT_NO_CONTENT
}
```

**Update mappers** in `domain/mapper/BookmarkMapper.kt` to map between entity and domain content states.

### 1.3 Acceptance Criteria

- [ ] Syncing bookmarks does NOT delete existing article content (REPLACE → IGNORE+UPDATE verified)
- [ ] Shared-text dialog does not reappear after process death
- [ ] Sync-on-app-open triggers only on cold start, not on config change or ViewModel recreation
- [ ] Metadata sync completes without enqueuing any content download workers
- [ ] Existing downloaded content is backfilled as `DOWNLOADED` state after migration
- [ ] Non-article bookmarks are correctly marked `PERMANENT_NO_CONTENT`
- [ ] `LoadArticleUseCase` sets content state appropriately on success, transient failure, and permanent failure
- [ ] Content that is already `DOWNLOADED` is never re-fetched
- [ ] All existing unit tests pass
- [ ] New unit tests cover: upsert behavior, content state transitions, LoadArticleUseCase result handling

---

## Phase 2: Content Policy Engine + Reading View On-Demand Fetch

### 2.1 Goals

* Introduce the content sync policy layer (Automatic / Manual / Date Range)
* Implement on-demand content fetch with retry in the reading view
* Enforce constraints (Wi-Fi, battery saver) consistently
* Re-enable automatic content loading behind the policy engine

### 2.2 Deliverables

#### 2.2.1 Content Sync Policy Evaluator

**New file:** `app/src/main/java/com/mydeck/app/domain/sync/ContentSyncPolicy.kt`

```kotlin
enum class ContentSyncMode {
    AUTOMATIC,   // Content fetched during bookmark sync
    MANUAL,      // Content fetched only when user opens a bookmark
    DATE_RANGE   // Content fetched for a user-specified date range on demand
}

data class ContentSyncConstraints(
    val wifiOnly: Boolean,
    val allowOnBatterySaver: Boolean
)

data class DateRangeParams(
    val from: LocalDate,
    val to: LocalDate
)
```

**New file:** `app/src/main/java/com/mydeck/app/domain/sync/ContentSyncPolicyEvaluator.kt`

```kotlin
class ContentSyncPolicyEvaluator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectivityMonitor: ConnectivityMonitor  // new, see below
) {
    data class Decision(
        val allowed: Boolean,
        val blockedReason: String? = null
    )

    suspend fun canFetchContent(): Decision {
        val constraints = settingsDataStore.getContentSyncConstraints()

        if (constraints.wifiOnly && !connectivityMonitor.isOnWifi()) {
            return Decision(false, "Wi-Fi required")
        }

        if (!constraints.allowOnBatterySaver && connectivityMonitor.isBatterySaverOn()) {
            return Decision(false, "Battery saver active")
        }

        if (!connectivityMonitor.isNetworkAvailable()) {
            return Decision(false, "No network")
        }

        return Decision(true)
    }

    suspend fun shouldAutoFetchContent(): Boolean {
        return settingsDataStore.getContentSyncMode() == ContentSyncMode.AUTOMATIC
            && canFetchContent().allowed
    }
}
```

**New file:** `app/src/main/java/com/mydeck/app/domain/sync/ConnectivityMonitor.kt`

Interface + implementation using Android's `ConnectivityManager` and `PowerManager`:

```kotlin
interface ConnectivityMonitor {
    fun isNetworkAvailable(): Boolean
    fun isOnWifi(): Boolean
    fun isBatterySaverOn(): Boolean
    fun observeConnectivity(): Flow<Boolean>  // For UI offline indicator (Phase 3)
}
```

Implementation via `ConnectivityManager.NetworkCallback` and `PowerManager.isPowerSaveMode`.

#### 2.2.2 New Settings Preferences

**File:** `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt` (interface) and `SettingsDataStoreImpl.kt`

Add:

```kotlin
// Content sync mode
suspend fun getContentSyncMode(): ContentSyncMode
suspend fun saveContentSyncMode(mode: ContentSyncMode)

// Constraints
suspend fun getContentSyncConstraints(): ContentSyncConstraints
suspend fun saveWifiOnly(enabled: Boolean)
suspend fun saveAllowBatterySaver(enabled: Boolean)

// Date range
suspend fun getDateRangeParams(): DateRangeParams?
suspend fun saveDateRangeParams(params: DateRangeParams)
```

**Default values for existing users:**
- `contentSyncMode` = `AUTOMATIC` (preserves existing behavior where content was auto-fetched)
- `wifiOnly` = `false`
- `allowOnBatterySaver` = `true`

#### 2.2.3 Wire Policy into Content Fetch Flows

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt`

After the metadata sync loop completes (where `enqueueBatchArticleLoader()` used to be), add policy-gated content sync:

```kotlin
// After metadata sync completes:
if (policyEvaluator.shouldAutoFetchContent()) {
    enqueueBatchArticleLoader()
}
```

Re-add the `enqueueBatchArticleLoader()` private method, but now it is only called when the policy allows it.

Constructor now requires `ContentSyncPolicyEvaluator`.

**File:** `app/src/main/java/com/mydeck/app/worker/BatchArticleLoadWorker.kt`

Update to use `getBookmarkIdsEligibleForContentFetch()` instead of `getBookmarkIdsWithoutContent()`.

Also add constraint checking before each batch:
```kotlin
// Before processing each batch:
if (!policyEvaluator.canFetchContent().allowed) {
    Timber.i("Content fetch blocked by constraints, stopping batch")
    return Result.success() // Don't retry, constraints may change
}
```

#### 2.2.4 Date Range Content Sync Worker

**New file:** `app/src/main/java/com/mydeck/app/worker/DateRangeContentSyncWorker.kt`

A one-shot worker that:
1. Reads date range params from `SettingsDataStore`
2. Queries bookmarks where `created` is within the date range AND `contentState` is NOT_ATTEMPTED or DIRTY
3. Fetches content for each, respecting constraints
4. Reports progress (for future notification support)

```kotlin
@HiltWorker
class DateRangeContentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fromEpoch = inputData.getLong(PARAM_FROM_EPOCH, 0)
        val toEpoch = inputData.getLong(PARAM_TO_EPOCH, 0)

        val eligibleIds = bookmarkDao.getBookmarkIdsForDateRangeContentFetch(
            fromEpoch = fromEpoch, toEpoch = toEpoch
        )

        for (id in eligibleIds) {
            if (!policyEvaluator.canFetchContent().allowed) {
                Timber.i("Constraints no longer met, stopping date range sync")
                return Result.success()
            }
            try {
                loadArticleUseCase.execute(id)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load article $id in date range sync")
            }
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "date_range_content_sync"
        const val PARAM_FROM_EPOCH = "from_epoch"
        const val PARAM_TO_EPOCH = "to_epoch"
    }
}
```

**New DAO query:**
```kotlin
@Query("""
    SELECT b.id FROM bookmarks b
    WHERE b.contentState IN (0, 2)
    AND b.hasArticle = 1
    AND b.created >= :fromEpoch AND b.created <= :toEpoch
    ORDER BY b.created DESC
""")
suspend fun getBookmarkIdsForDateRangeContentFetch(fromEpoch: Long, toEpoch: Long): List<String>
```

#### 2.2.5 Reading View On-Demand Content Fetch

**File:** `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`

Add content loading capability to the detail ViewModel:

```kotlin
// New dependencies (constructor injection):
private val loadArticleUseCase: LoadArticleUseCase
private val policyEvaluator: ContentSyncPolicyEvaluator

// New state:
private val _contentLoadState = MutableStateFlow<ContentLoadState>(ContentLoadState.Idle)
val contentLoadState: StateFlow<ContentLoadState> = _contentLoadState.asStateFlow()

sealed class ContentLoadState {
    data object Idle : ContentLoadState()
    data object Loading : ContentLoadState()
    data object Loaded : ContentLoadState()
    data class Failed(val reason: String, val canRetry: Boolean) : ContentLoadState()
}
```

In the `init` block, after loading the bookmark, check if content needs fetching:

```kotlin
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    currentScrollProgress = bookmark.readProgress
    isReadLocked = bookmark.isRead()

    // If no content downloaded, attempt on-demand fetch
    if (bookmark.contentState != ContentState.DOWNLOADED &&
        bookmark.contentState != ContentState.PERMANENT_NO_CONTENT) {
        fetchContentOnDemand(bookmarkId)
    }
}
```

```kotlin
private fun fetchContentOnDemand(bookmarkId: String) {
    viewModelScope.launch {
        _contentLoadState.value = ContentLoadState.Loading
        val result = loadArticleUseCase.execute(bookmarkId)
        _contentLoadState.value = when (result) {
            is LoadArticleUseCase.Result.Success -> ContentLoadState.Loaded
            is LoadArticleUseCase.Result.AlreadyDownloaded -> ContentLoadState.Loaded
            is LoadArticleUseCase.Result.TransientFailure -> ContentLoadState.Failed(
                reason = result.reason,
                canRetry = true
            )
            is LoadArticleUseCase.Result.PermanentFailure -> ContentLoadState.Failed(
                reason = result.reason,
                canRetry = false
            )
        }
    }
}

fun retryContentFetch() {
    bookmarkId?.let { fetchContentOnDemand(it) }
}
```

**File:** `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`

Update the reading view to observe `contentLoadState`:

1. When `ContentLoadState.Loading` — show a centered `CircularProgressIndicator` instead of the "No content" message
2. When `ContentLoadState.Failed(canRetry=true)` — show the failure reason and a "Retry" button
3. When `ContentLoadState.Failed(canRetry=false)` — auto-switch to Original (WebView) mode
4. When content loads successfully, the existing `observeBookmarkWithArticleContent` Flow will emit a new value with content populated, causing the UI to re-render with the article

**Specifics for the retry UI:**

```kotlin
// Within the Reader content mode, when articleContent is null:
when (val loadState = contentLoadState) {
    is ContentLoadState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    is ContentLoadState.Failed -> {
        if (!loadState.canRetry) {
            // Auto-switch to original view
            LaunchedEffect(Unit) { showOriginal = true }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Unable to load article content")
                Text(loadState.reason, style = Typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.retryContentFetch() }) {
                    Text("Retry")
                }
            }
        }
    }
    else -> {
        // Existing "no content" placeholder
        EmptyBookmarkDetailArticle()
    }
}
```

### 2.3 Acceptance Criteria

- [ ] Content sync mode setting is persisted and respected (Automatic / Manual / Date Range)
- [ ] In Automatic mode: content is fetched after metadata sync completes
- [ ] In Manual mode: content is fetched ONLY when opening a bookmark in reading view
- [ ] In Date Range mode: content is fetched only when user triggers "Download" for the selected range
- [ ] Wi-Fi constraint blocks content fetch when on mobile data (if enabled)
- [ ] Battery saver constraint blocks content fetch when active (if enabled)
- [ ] Opening a bookmark without content shows loading → article (on success) or loading → retry (on failure)
- [ ] Permanent no-content bookmarks auto-switch to Original view
- [ ] Retry button in reading view re-triggers content fetch
- [ ] `ContentSyncPolicyEvaluator` is the single source of truth for fetch decisions
- [ ] All Phase 1 tests continue to pass
- [ ] New tests cover: policy evaluation, constraint checking, on-demand fetch flow, retry behavior

---

## Phase 3: UI Overhaul (Settings Redesign + Sync Status + Offline Indicator + Permissions)

### 3.1 Goals

* Implement the revised Settings → Sync UI per Section 5
* Display comprehensive sync status metrics per Section 8
* Add offline/online indicator to navigation drawer per Section 7
* Implement permission prompting for background work per Section 6
* Remove legacy sync UI elements

### 3.2 Deliverables

#### 3.2.1 Revised Settings → Sync Screen

**File:** `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsScreen.kt` (rewrite)

Replace the current layout with three sections:

**Section 1: Bookmark Sync**
- Static description text: "Bookmarks are always kept in sync."
- Sync frequency selector (reuse existing `AutoSyncTimeframe` options from `SettingsDataStore`)
- Remove the "Auto Sync" on/off toggle — bookmark sync is always on
- Remove the "Sync on App Open" toggle — it becomes implicit (always syncs on open)
- Remove the "Sync Now" button — replaced by pull-to-refresh

**Section 2: Content Sync**
- Radio group with three options:
  - **Automatic** — "Content is downloaded during bookmark sync"
  - **Manual** — "Content is downloaded only when you open a bookmark"
  - **Date Range** — "Download content for bookmarks added in a date range"
- When "Date Range" is selected, show:
  - "From" date picker
  - "To" date picker
  - "Download" button
- Selection saved via `settingsDataStore.saveContentSyncMode()`

**Section 3: Constraints**
- "Only download on Wi-Fi" toggle
- "Allow download on battery saver" toggle

**Section 4: Sync Status** (card/bordered container)
- See Section 3.2.2

**File:** `app/src/main/java/com/mydeck/app/ui/settings/SyncSettingsViewModel.kt` (update)

Replace the current ViewModel state to match new UI:

```kotlin
data class SyncSettingsUiState(
    // Bookmark sync
    val bookmarkSyncFrequency: AutoSyncTimeframe,
    val bookmarkSyncFrequencyOptions: List<AutoSyncTimeframeOption>,

    // Content sync
    val contentSyncMode: ContentSyncMode,
    val dateRangeFrom: LocalDate?,
    val dateRangeTo: LocalDate?,
    val isDateRangeDownloading: Boolean,

    // Constraints
    val wifiOnly: Boolean,
    val allowBatterySaver: Boolean,

    // Sync status
    val syncStatus: SyncStatus,

    // Dialog state
    val showDialog: Dialog?
)

data class SyncStatus(
    val totalBookmarks: Int,
    val unread: Int,
    val archived: Int,
    val favorites: Int,
    val contentDownloaded: Int,
    val contentAvailable: Int,
    val contentDirty: Int,
    val permanentNoContent: Int,
    val lastSyncTimestamp: String?
)
```

#### 3.2.2 Sync Status Metrics

**New DAO queries:**

**File:** `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

```kotlin
data class DetailedSyncStatusCounts(
    val total: Int,
    val unread: Int,
    val archived: Int,
    val favorites: Int,
    val contentDownloaded: Int,
    val contentAvailable: Int,
    val contentDirty: Int,
    val permanentNoContent: Int
)

@Query("""
    SELECT
        (SELECT COUNT(*) FROM bookmarks WHERE state = 0) AS total,
        (SELECT COUNT(*) FROM bookmarks WHERE readProgress < 100 AND state = 0) AS unread,
        (SELECT COUNT(*) FROM bookmarks WHERE isArchived = 1 AND state = 0) AS archived,
        (SELECT COUNT(*) FROM bookmarks WHERE isMarked = 1 AND state = 0) AS favorites,
        (SELECT COUNT(*) FROM bookmarks WHERE contentState = 1) AS contentDownloaded,
        (SELECT COUNT(*) FROM bookmarks WHERE contentState = 0 AND hasArticle = 1) AS contentAvailable,
        (SELECT COUNT(*) FROM bookmarks WHERE contentState = 2) AS contentDirty,
        (SELECT COUNT(*) FROM bookmarks WHERE contentState = 3) AS permanentNoContent
    FROM bookmarks LIMIT 1
""")
fun observeDetailedSyncStatus(): Flow<DetailedSyncStatusCounts?>
```

#### 3.2.3 Offline / Online Indicator

**File:** `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt`

In the navigation drawer header (where the app name is displayed), add a connectivity indicator:

```kotlin
// In the drawer header composable:
val isOnline by connectivityMonitor.observeConnectivity().collectAsState(initial = true)

Row(verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.app_name), /* existing styling */)
    if (!isOnline) {
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { /* show tooltip: "Offline – unable to reach server" */ }
                ),
            tint = MaterialTheme.colorScheme.error
        )
    }
}
```

The `connectivityMonitor` should be injected into the `BookmarkListViewModel` and exposed as a StateFlow.

#### 3.2.4 Permission Prompts

**When user selects "Automatic" content sync mode or taps "Download" for date range:**

1. Check if background work permission is needed (Android 12+ has exact alarm restrictions; POST_NOTIFICATIONS for Android 13+)
2. Show rationale dialog explaining:
   - "Content downloads may continue while the app is in the background"
   - "This allows your bookmarks to be ready for offline reading"
3. Request permission
4. Track `permissionPromptShown` in `SettingsDataStore` to avoid re-prompting

Reuse the existing `NotificationRationaleDialog` pattern from `SyncSettingsScreen.kt` and the accompanist permissions library already in the project.

#### 3.2.5 Migration Cleanup

1. **Remove "Sync Now" button** from Settings UI (replaced by pull-to-refresh)
2. **Remove "Sync on App Open" toggle** — this is now always-on when the user is logged in (the `savedInstanceState == null` guard in `MainActivity` handles it)
3. **Remove "Auto Sync" master toggle** — bookmark sync is always on; only the frequency changes
4. **Migrate existing settings:**
   - If `autoSyncEnabled` was `true`: set `contentSyncMode = AUTOMATIC`
   - If `autoSyncEnabled` was `false`: set `contentSyncMode = MANUAL`
   - Existing `autoSyncTimeframe` → reuse as bookmark sync frequency
   - Run this migration once on first launch after update, tracked by a preference flag

### 3.3 Acceptance Criteria

- [ ] Settings → Sync shows three sections: Bookmark Sync, Content Sync, Constraints
- [ ] Content sync mode radio group persists selection correctly
- [ ] Date Range mode shows date pickers and Download button; tapping Download enqueues `DateRangeContentSyncWorker`
- [ ] Sync status card displays all metrics from `DetailedSyncStatusCounts` and updates live
- [ ] Offline indicator appears in drawer header when network unavailable or server unreachable
- [ ] Offline indicator disappears when connectivity is restored
- [ ] Permission prompt appears when selecting Automatic mode or tapping Date Range Download (first time only)
- [ ] "Sync Now" button is removed
- [ ] "Sync on App Open" toggle is removed
- [ ] "Auto Sync" master toggle is removed; bookmark sync frequency selector remains
- [ ] Existing user settings are correctly migrated to new model on upgrade
- [ ] All Phase 1 and Phase 2 tests continue to pass
- [ ] New tests cover: settings migration, sync status query, UI state management

---

## Appendix A: File Change Summary

| File | Phase | Change Type |
|------|-------|-------------|
| `io/db/dao/BookmarkDao.kt` | 1, 2, 3 | REPLACE → IGNORE+UPDATE, new queries for content state, date range, detailed sync status |
| `io/db/model/BookmarkEntity.kt` | 1 | Add `contentState`, `contentFailureReason` fields and `ContentState` enum |
| `io/db/model/ArticleContentEntity.kt` | — | No changes (CASCADE FK remains; harmless now that bookmarks use UPDATE not REPLACE) |
| `io/db/migration/Migration_X_Y.kt` | 1 | New file: schema migration + backfill |
| `domain/model/Bookmark.kt` | 1 | Add `contentState`, `contentFailureReason` |
| `domain/mapper/BookmarkMapper.kt` | 1 | Map new content state fields |
| `domain/usecase/LoadArticleUseCase.kt` | 1 | Add content state tracking, guard against re-fetch and permanent no-content |
| `domain/usecase/LoadBookmarksUseCase.kt` | 1, 2 | Remove auto content enqueue (Phase 1), re-add behind policy gate (Phase 2) |
| `domain/usecase/FullSyncUseCase.kt` | — | No changes |
| `domain/sync/ContentSyncPolicy.kt` | 2 | New file: policy enums and data classes |
| `domain/sync/ContentSyncPolicyEvaluator.kt` | 2 | New file: policy evaluation logic |
| `domain/sync/ConnectivityMonitor.kt` | 2 | New file: network + battery state interface |
| `domain/sync/ConnectivityMonitorImpl.kt` | 2 | New file: Android implementation |
| `domain/BookmarkRepository.kt` | — | No changes needed (insertBookmarks signature unchanged) |
| `domain/BookmarkRepositoryImpl.kt` | 1 | Update `renameLabel` and `deleteLabel` to use `upsertBookmark` |
| `worker/FullSyncWorker.kt` | — | No changes (delegates to LoadBookmarksUseCase) |
| `worker/LoadBookmarksWorker.kt` | — | No changes |
| `worker/BatchArticleLoadWorker.kt` | 1, 2 | Use content-state-aware query, add constraint checking |
| `worker/LoadArticleWorker.kt` | — | No changes (wraps LoadArticleUseCase) |
| `worker/DateRangeContentSyncWorker.kt` | 2 | New file |
| `ui/list/BookmarkListViewModel.kt` | 1, 3 | Fix dialog loop, remove sync-on-open, add connectivity state |
| `ui/list/BookmarkListScreen.kt` | 3 | Add offline indicator to drawer header |
| `ui/detail/BookmarkDetailViewModel.kt` | 2 | Add on-demand content fetch + retry |
| `ui/detail/BookmarkDetailScreen.kt` | 2 | Add loading/retry/auto-switch UI |
| `ui/settings/SyncSettingsScreen.kt` | 3 | Rewrite with new sections |
| `ui/settings/SyncSettingsViewModel.kt` | 3 | Rewrite for new state model |
| `io/prefs/SettingsDataStore.kt` | 2, 3 | Add content sync mode, constraints, date range prefs |
| `io/prefs/SettingsDataStoreImpl.kt` | 2, 3 | Implement new preferences |
| `MainActivity.kt` | 1 | Add sync-on-open trigger |

## Appendix B: New String Resources Needed

```xml
<!-- Sync Settings -->
<string name="sync_bookmark_section_title">Bookmark Sync</string>
<string name="sync_bookmark_description">Bookmarks are always kept in sync.</string>
<string name="sync_content_section_title">Content Sync</string>
<string name="sync_content_automatic">Automatic</string>
<string name="sync_content_automatic_desc">Content is downloaded during bookmark sync</string>
<string name="sync_content_manual">Manual</string>
<string name="sync_content_manual_desc">Content is downloaded only when you open a bookmark</string>
<string name="sync_content_date_range">Date Range</string>
<string name="sync_content_date_range_desc">Download content for bookmarks added in a date range</string>
<string name="sync_content_download_button">Download</string>
<string name="sync_constraints_section_title">Constraints</string>
<string name="sync_wifi_only">Only download on Wi-Fi</string>
<string name="sync_allow_battery_saver">Allow download on battery saver</string>

<!-- Sync Status -->
<string name="sync_status_total">Total bookmarks: %d</string>
<string name="sync_status_unread">Unread: %d</string>
<string name="sync_status_archived">Archived: %d</string>
<string name="sync_status_favorites">Favorites: %d</string>
<string name="sync_status_content_downloaded">Content downloaded: %d</string>
<string name="sync_status_content_available">Available to download: %d</string>
<string name="sync_status_content_dirty">Download failed (retryable): %d</string>
<string name="sync_status_no_content">No article content: %d</string>

<!-- Reading View -->
<string name="content_loading">Loading article content…</string>
<string name="content_load_failed">Unable to load article content</string>
<string name="content_retry">Retry</string>

<!-- Offline Indicator -->
<string name="offline_tooltip">Offline – unable to reach server</string>

<!-- Permission Rationale -->
<string name="background_sync_rationale_title">Background Downloads</string>
<string name="background_sync_rationale_body">Content downloads may continue while the app is in the background. This allows your bookmarks to be ready for offline reading.</string>
```

## Appendix C: Post-Implementation Cleanup Notes

The following minor items were identified during Phase 2 code review and should be addressed in a separate cleanup pass:

1. **Remove `@Transaction` from `LoadBookmarksUseCase.execute()`** — This annotation holds a database transaction open for the duration of the method, which includes paginated API calls in a loop. The transaction could be held for seconds or minutes. Since the method doesn't require atomicity across the full loop (each page is independently inserted), the annotation should be removed.

2. **Remove dead `isContentLoading` variable in `BookmarkDetailScreen.kt`** — Line 145 computes `val isContentLoading = contentLoadState is ContentLoadState.Loading` but the variable is never referenced. It was likely intended to influence the initial `contentMode` selection but was never wired up. Remove it to avoid confusion.

3. **Add parameter validation logging to `DateRangeContentSyncWorker`** — The worker accepts `fromEpoch`/`toEpoch` from input data with a default of `0` but doesn't log a warning when both are 0 (which would indicate the caller forgot to set params). The query would return nothing so it fails safely, but a log warning would aid debugging.
