# Technical Specification: Test Suite Remediation & Expansion

> **Revision Note (2026-02-07):** This spec has been revised after a codebase audit.
> Phase 1 was largely already completed. The original spec described remediation work
> that had already been done in DAO, Repository, and UseCase tests. The remaining
> compilation errors are isolated to two ViewModel test files. This revision corrects
> inaccuracies, removes completed items, and adds missing items discovered during review.

---

## Phase 0: Verify Current State (Pre-Requisite)

Before beginning any work, confirm the actual compilation errors by running:
```
./gradlew testGithubReleaseDebugUnitTest --dry-run
```
or attempting a full compilation. CI is currently disabled in both `.github/workflows/build.yml`
and `.github/workflows/run-tests.yml` with the comment "TEMPORARILY DISABLED - Test compilation
errors need to be fixed". Re-enabling CI should be the final step after all fixes.

---

## Phase 1: Restore Functionality to Existing Tests

### Status of Previously Identified Items

The following items from the original spec have been verified as **already completed**
and require no further action:

| Area | Status | Evidence |
| :--- | :--- | :--- |
| **BookmarkDaoTest** - `generateTestData()` uses `BookmarkWithArticleContent` | DONE | Lines 112-122 already use `insertBookmarksWithArticleContent` |
| **BookmarkDaoTest** - `GetBookmarkListItemsByFiltersTest` updated | DONE | Lines 129-155 test with correct entity projection |
| **MyDeckDatabaseMigrationTest** - `migrate1To2` validates `article_content` table | DONE | Lines 277-287 query `article_content` and assert content moved |
| **BookmarkRepositoryImplTest** - `updateBookmark` nullable booleans | DONE | All tests pass `isFavorite`, `isArchived`, `isRead` as nullable |
| **BookmarkRepositoryImplTest** - `performFullSync` temp table mocks | DONE | Lines 388-403 mock and verify `clearRemoteBookmarkIds`, `insertRemoteBookmarkIds`, `removeDeletedBookmars` |
| **BookmarkRepositoryImplTest** - `performDeltaSync` expects Error | DONE | Lines 448-459 assert `SyncResult.Error` with "Delta sync disabled" |
| **BookmarkRepositoryImplTest** - `createBookmark` verifies WorkManager enqueue | DONE | Line 490 verifies `workManager.enqueue(any<WorkRequest>())` |
| **LoadBookmarksUseCaseTest** - constructor includes `ContentSyncPolicyEvaluator` + `WorkManager` | DONE | Lines 30-48 mock both and pass to constructor |

### Remaining Compilation Errors (Actual Blockers)

These are the **real** issues preventing test compilation. There are exactly two test
files with errors, both in the ViewModel layer.

#### 1. BookmarkListViewModelTest — Missing `ConnectivityMonitor`
**File:** `app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt`

The `BookmarkListViewModel` constructor requires 8 parameters, but all ViewModel
instantiations in the test pass only 7 — `ConnectivityMonitor` is missing.

*   **Action A — Add mock declaration:**
    In `@Before setup()`, add:
    ```kotlin
    private lateinit var connectivityMonitor: ConnectivityMonitor
    // in setup():
    connectivityMonitor = mockk()
    every { connectivityMonitor.observeConnectivity() } returns flowOf(true)
    ```
    This prevents coroutine hangs from the `isOnline` StateFlow in the ViewModel.

*   **Action B — Fix all ViewModel instantiations (approx. 20 occurrences):**
    Every call to `BookmarkListViewModel(...)` must add `connectivityMonitor` as the
    8th parameter:
    ```kotlin
    viewModel = BookmarkListViewModel(
        updateBookmarkUseCase,
        fullSyncUseCase,
        workManager,
        bookmarkRepository,
        context,
        settingsDataStore,
        savedStateHandle,
        connectivityMonitor  // <-- ADD THIS
    )
    ```

*   **Action C — Fix incomplete `BookmarkListItem` dummy data (line ~1100):**
    The `bookmarks` val at the bottom of the file creates `BookmarkListItem` objects
    missing 4 required fields that have no default values in the data class
    (`BookmarkListItem.kt` lines 19-22):
    ```kotlin
    private val bookmarks = listOf(
        BookmarkListItem(
            // ... existing fields ...
            imageSrc = "",
            readingTime = null,           // ADD
            created = Clock.System.now()   // ADD
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            wordCount = null,             // ADD
            published = null              // ADD
        )
    )
    ```
    Note: The `BookmarkListItem` at lines 238-257 already has these fields — only the
    shared `bookmarks` val at the bottom is broken.

#### 2. BookmarkDetailViewModelTest — Missing `LoadArticleUseCase`
**File:** `app/src/test/java/com/mydeck/app/ui/detail/BookmarkDetailViewModelTest.kt`

The `BookmarkDetailViewModel` constructor requires 6 parameters, but all ViewModel
instantiations in the test pass only 5 — `LoadArticleUseCase` is missing.

*   **Action A — Add mock declaration:**
    In `@Before setup()`, add:
    ```kotlin
    private lateinit var loadArticleUseCase: LoadArticleUseCase
    // in setup():
    loadArticleUseCase = mockk(relaxed = true)
    ```

*   **Action B — Fix all ViewModel instantiations (approx. 15 occurrences):**
    Every call to `BookmarkDetailViewModel(...)` must add `loadArticleUseCase` as the
    5th parameter (between `settingsDataStore` and `savedStateHandle`):
    ```kotlin
    viewModel = BookmarkDetailViewModel(
        updateBookmarkUseCase,
        bookmarkRepository,
        assetLoader,
        settingsDataStore,
        loadArticleUseCase,  // <-- ADD THIS
        savedStateHandle
    )
    ```

*   **Action C — Fix `sampleBookmark` missing `published` field (line ~467):**
    The `Bookmark` data class requires `published: LocalDateTime?` (line 30 of
    `Bookmark.kt`) with no default value. The `sampleBookmark` val is missing it:
    ```kotlin
    val sampleBookmark = Bookmark(
        // ... existing fields ...
        embedHostname = null,
        published = null,  // ADD — required, no default
        article = Bookmark.Resource(""),
        // ...
    )
    ```
    The same fix is needed for all inline `Bookmark(...)` instantiations in the file
    (lines 71-106, 136-171, 189-224) that are also missing `published`. Verify each
    one during remediation.

---

## Phase 2: Coverage for New Features

This phase defines new test classes and methods required to cover functionality added
since the fork.

### 1. New Unit Tests: Sync Logic & Policy
**New Test Class:** `ContentSyncPolicyEvaluatorTest.kt`
**Location:** `app/src/test/java/com/mydeck/app/domain/sync/`
*   **Scenario:** Verify `canFetchContent` returns `Decision(allowed=false)` when `wifiOnly` is true but `ConnectivityMonitor.isOnWifi()` is false.
*   **Scenario:** Verify `canFetchContent` returns `Decision(allowed=false)` when `allowOnBatterySaver` is false and `ConnectivityMonitor.isBatterySaverOn()` is true.
*   **Scenario:** Verify `canFetchContent` returns `Decision(allowed=true)` when all constraints are satisfied.
*   **Scenario:** Verify `shouldAutoFetchContent` returns false if `SettingsDataStore.getContentSyncMode()` is `MANUAL`.
*   **Scenario:** Verify `shouldAutoFetchContent` returns true if mode is `AUTOMATIC` and `canFetchContent` allows it.

**New Test Class:** `SyncSettingsViewModelTest.kt`
**Location:** `app/src/test/java/com/mydeck/app/ui/settings/`
*   **Scenario:** Verify `onDateRangeFromSelected` and `onDateRangeToSelected` trigger a save to `SettingsDataStore` only when both dates are non-null.
*   **Scenario:** Verify `onClickDateRangeDownload` enqueues `DateRangeContentSyncWorker` with correct epoch input data.

### 2. New Unit Tests: Search Functionality
**Update:** `app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt`
*   **Scenario:** `onSearchQueryChange` emits new query to `searchQuery` flow.
*   **Scenario:** Verify that when `searchQuery` flow emits a non-empty value, `bookmarkRepository.searchBookmarkListItems` is called instead of `observeBookmarkListItems`.
*   **Scenario:** Verify `onClearSearch` resets query and toggles `isSearchActive`.

### 3. New Unit Tests: Label Management
**Update:** `app/src/test/java/com/mydeck/app/ui/detail/BookmarkDetailViewModelTest.kt`
*   **Scenario:** `onUpdateLabels` calls `bookmarkRepository.updateLabels`. Verify the list of strings is passed correctly.

**Update:** `app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt`
*   **Scenario:** `onClickLabel` updates `FilterState` to include the selected label (`FilterState.label`).
*   **Scenario:** `onRenameLabel` calls repository and refreshes filter if the renamed label was currently active.

### 4. New Unit Tests: Layout & Sorting
**Update:** `app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt`
*   **Scenario:** `onLayoutModeSelected` persists value to `SettingsDataStore` and updates the `layoutMode` StateFlow.
*   **Scenario:** `onSortOptionSelected` persists value and triggers a reload of bookmarks with the new SQL `ORDER BY` clause.

### 5. New Unit Tests: Content State & Loading
**Update:** `app/src/test/java/com/mydeck/app/ui/detail/BookmarkDetailViewModelTest.kt`
*   **Scenario:** Initialization: If `Bookmark.contentState` is `NOT_ATTEMPTED` or `DIRTY` and `hasArticle` is true, verify `loadArticleUseCase.execute` is launched immediately (confirmed in `BookmarkDetailViewModel.kt` lines 128-141).
*   **Scenario:** If `loadArticleUseCase` returns a permanent failure, verify `contentLoadState` updates to a failed state with `canRetry=false`.
*   **Scenario:** If `Bookmark.contentState` is `DOWNLOADED`, verify `loadArticleUseCase.execute` is NOT called.
*   **Scenario:** If `Bookmark.contentState` is `PERMANENT_NO_CONTENT`, verify `loadArticleUseCase.execute` is NOT called and appropriate state is set.

### 6. Worker Tests (Optional but Recommended)
**New Test Class:** `BatchArticleLoadWorkerTest.kt`
**Location:** `app/src/test/java/com/mydeck/app/worker/`
**Dependency:** Requires `androidx.work:work-testing` (already in `build.gradle.kts` dependencies).
*   **Scenario:** Verify `doWork` queries `bookmarkDao.getBookmarkIdsEligibleForContentFetch`.
*   **Scenario:** Verify it stops processing a batch if `ContentSyncPolicyEvaluator` returns disallowed mid-run.

### 7. UI Testing (Robolectric)
**Update:** `BookmarkListScreenTest.kt`
*   **Scenario:** Verify that clicking the "Grid" icon in the top bar creates a specific Grid layout semantic node.
*   **Scenario:** Verify that the "My List" drawer item sets the correct filter state.

### 8. Integration: Utility Tests
**Target:** `app/src/test/java/com/mydeck/app/util/UtilsTest.kt`
*   **Scenario:** Expand `extractUrlAndTitle` tests. Add cases for text shared from specific apps (e.g., "Check this out: https://example.com"). Ensure it correctly strips surrounding text and uses it as the title.
*   **Scenario:** Add edge cases: multiple URLs in text, URLs with query parameters, URLs with fragments, unicode surrounding text.

---

## Priority Matrix

| Feature Area | Criticality | Test Type | Difficulty |
| :--- | :--- | :--- | :--- |
| **Phase 1: Fix ViewModel compilation** | 🔴 **BLOCKER** | Fix | Low |
| **Sync Logic & Policy** | 🔴 **Critical** | Gap Fill | Low |
| **Content State Machine** | 🔴 **Critical** | Gap Fill | Medium |
| **Workers** | 🟠 **High** | **New Territory** | High |
| **Search Functionality** | 🟠 **High** | Gap Fill | Medium |
| **Utils (Url Extraction)** | 🟠 **High** | Gap Fill | Low |
| **Label Management** | 🟡 Medium | Gap Fill | Low |
| **UI (List Screen)** | 🟡 Medium | Gap Fill* | High |
| **Layout & Sorting** | 🟢 Low | Gap Fill | Low |

---

## Detailed Breakdown

### 1. "Gap Fill" Tests (Existing Patterns)
*These tests follow patterns already established in `BookmarkDetailViewModelTest` or `SettingsViewModelTest`. You just need to apply the logic to the new features.*

**A. Sync Logic & Policy (ContentSyncPolicyEvaluator)**
*   **Criticality: 🔴 Critical**
*   **Why:** This is the "brain" of the new background sync. If this logic fails, the app might blow through user data on cellular networks or fail to download content entirely.
*   **Context:** Pure unit tests. Very easy to write but absolutely essential to have.

**B. Content State & Loading (DetailViewModel)**
*   **Criticality: 🔴 Critical**
*   **Why:** The architectural shift to split `ArticleContent` from `Bookmarks` introduces a complex state machine (`NOT_ATTEMPTED` -> `LOADING` -> `DOWNLOADED`/`FAILED`). If this breaks, the user sees infinite spinners or empty screens.
*   **Context:** ViewModel unit tests using Coroutines/Flows.

**C. Search Functionality**
*   **Criticality: 🟠 High**
*   **Why:** Search is a primary navigation tool. Regressions here make the app feel broken immediately.
*   **Context:** Testing `Flow` operators (debounce/flatMap). Existing ViewModel tests perform similar actions, so this is just adding new scenarios.

**D. Utils (URL Extraction)**
*   **Criticality: 🟠 High**
*   **Why:** The "Share to MyDeck" feature relies entirely on this regex logic. If it fails, the "Add Bookmark" dialog populates with garbage data.
*   **Context:** Simple JUnit tests.

**E. Label Management**
*   **Criticality: 🟡 Medium**
*   **Why:** Important for organization, but if it breaks, the core reading functionality remains intact.
*   **Context:** Standard ViewModel function calls.

**F. UI Testing (BookmarkListScreen)**
*   **Criticality: 🟡 Medium**
*   **Why:** *Technically* a gap fill because `SettingsScreenUnitTest` exists. However, testing the Main List is much harder than the Settings screen (LazyLists, async images, complex navigation).
*   **Note:** While valuable, these are expensive to write and maintain. Prioritize unit logic over these if resources are tight.

**G. Layout & Sorting**
*   **Criticality: 🟢 Low**
*   **Why:** Purely preference-based. If sorting defaults to "Newest" instead of "Oldest" due to a bug, it is a minor annoyance, not a crash or data loss.

---

### 2. "New Territory" Tests (New Infrastructure)
*These require setting up new testing libraries or configurations that do not currently exist in the repo.*

**A. Worker Tests (BatchArticleLoadWorker)**
*   **Criticality: 🟠 High**
*   **Why:** Background workers run silently. If they fail (e.g., crash on database access or get stuck in a retry loop), you won't know until users complain about battery drain or missing content.
*   **New Infrastructure:** Requires `androidx.work:work-testing`. You currently have no worker tests. You need to instantiate a `TestDriver` to manually simulate constraints (network connected, charging) and ensure the worker triggers correctly.

---

## Recommendation for Implementation Order

1.  **Phase 1 (BLOCKER):** Fix the two ViewModel test files so the entire suite compiles. This unblocks everything else.
2.  **Sync Policy & Content State (Critical / Gap Fill):** Do this next. It secures the new architecture's stability.
3.  **Utils (High / Gap Fill):** Low effort, high value for the "Share" feature.
4.  **Search (High / Gap Fill):** Secures the main navigation feature.
5.  **Worker Tests (High / New Territory):** Do this once the Unit tests are stable. It requires the most setup but covers the "invisible" part of the app.
6.  **UI & Layout (Medium/Low):** Save these for last. They are the most brittle and time-consuming.
7.  **Re-enable CI:** Uncomment test steps in `.github/workflows/build.yml` and `.github/workflows/run-tests.yml`.
