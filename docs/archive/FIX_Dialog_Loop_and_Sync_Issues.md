Based on the code review, here are the specific changes required to fix the "Dialog Loop," "Repeated Sync," and "Content Invalidation" bugs.

### 1. Fix "Add Bookmark Dialog" Reappearing
**Problem:** The `BookmarkListViewModel` reads the `sharedText` from `SavedStateHandle` in its `init` block. When the app is backgrounded and the system kills the process (or on configuration change), the ViewModel is recreated. The `SavedStateHandle` persists the data, so the `init` block runs again, seeing the `sharedText` and reopening the dialog.

**Fix:** You must consume (remove) the data from `SavedStateHandle` immediately after reading it.

**File:** `src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`

```kotlin
// In the init block
init {
    // OLD CODE:
    // savedStateHandle.get<String>("sharedText").takeIf { it != null }?.let { ... }

    // NEW CODE:
    val sharedText = savedStateHandle.get<String>("sharedText")
    if (sharedText != null) {
        // 1. Consume the value so it doesn't survive recreation
        savedStateHandle.remove<String>("sharedText")
        
        // 2. Existing Logic to open dialog
        val extracted = sharedText.extractUrlAndTitle()
        // ... (rest of logic setting _createBookmarkUiState)
    }
    
    // ...
}
```

---

### 2. Fix "Sync Triggered on Foreground"
**Problem:** The logic to "Sync on App Open" is currently located in the `init` block of `BookmarkListViewModel`.
*   This ViewModel is scoped to the `BookmarkListRoute`.
*   Whenever the ViewModel is recreated (process restoration, graph recreation), the `init` block runs and triggers a Full Sync.
*   This is incorrect lifecycle placement for "App Open" logic.

**Fix:** Move the sync trigger to `MainActivity.onCreate`. This ensures it only runs once when the app is actually started (Cold Start), not when a specific screen is recreated.

**Step A: Remove from ViewModel**
**File:** `src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`
*   **Remove** the `viewModelScope.launch { if (settingsDataStore.isSyncOnAppOpenEnabled()) ... }` block from `init`.

**Step B: Add to Activity**
**File:** `src/main/java/com/mydeck/app/MainActivity.kt`

```kotlin
// Inject FullSyncUseCase into MainActivity
@Inject lateinit var fullSyncUseCase: FullSyncUseCase

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // ... existing setup ...

    // NEW CODE: Only trigger sync if this is a fresh start (not a recreation)
    if (savedInstanceState == null) {
        lifecycleScope.launch {
            if (settingsDataStore.isSyncOnAppOpenEnabled()) {
                Timber.d("App Open: Triggering Full Sync")
                fullSyncUseCase.performFullSync()
            }
        }
    }
    
    // ... setContent ...
}
```

---

### 3. Fix "Content Invalidation" (Data Loss)
**Problem:** The user noted that the background sync "invalidates previously stored content." This is a critical bug in the Database Access Object (DAO).
*   **Cause:** `BookmarkDao.insertBookmark` uses `OnConflictStrategy.REPLACE`.
*   **Effect:** When a bookmark is updated (or re-inserted) during sync, Room deletes the old row and inserts the new one.
*   **The Trap:** The `article_content` table has a Foreign Key to `bookmarks` with `onDelete = ForeignKey.CASCADE`.
*   **Result:** When `REPLACE` deletes the bookmark row, the Database automatically deletes the associated article content.

**Fix:** Change the insert strategy to `IGNORE` and manually `UPDATE`.

**File:** `src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

```kotlin
@Dao
interface BookmarkDao {
    
    // 1. Change Strategy to IGNORE (don't overwrite if exists)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmarkIgnore(bookmarkEntity: BookmarkEntity): Long

    // 2. Add an Update method
    @Update
    suspend fun updateBookmark(bookmarkEntity: BookmarkEntity)

    // 3. Refactor the transaction method
    @Transaction
    suspend fun insertBookmarkWithArticleContent(bookmarkWithArticleContent: BookmarkWithArticleContent) {
        with(bookmarkWithArticleContent) {
            // Try to insert
            val rowId = insertBookmarkIgnore(bookmark)
            
            // If rowId is -1, it exists. Update it instead.
            if (rowId == -1L) {
                // Ideally, we only update metadata fields here to preserve local state if needed, 
                // but for a sync, updating the row is standard.
                // The key is that UPDATE does not trigger the Cascade Delete of the foreign key.
                updateBookmark(bookmark) 
            }

            // Handle article content (upsert)
            articleContent?.run { insertArticleContent(this) }
        }
    }
}
```

### Summary of Fixes
1.  **Stop Dialog Loop:** `savedStateHandle.remove("sharedText")` in VM init.
2.  **Stop Sync Spam:** Move sync logic from VM `init` to `MainActivity.onCreate(savedInstanceState == null)`.
3.  **Stop Data Loss:** Replace `OnConflictStrategy.REPLACE` with `Insert(IGNORE)` + `Update` pattern in `BookmarkDao` to prevent cascading deletion of offline content.