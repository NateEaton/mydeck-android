# Feature Spec: Global Full Text Search

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

Global full-text search feature that allows users to search across all bookmarks by title, site name, and labels. Includes support for Gmail-style search operators (`is:`, `has:`) to filter by bookmark state and article content availability.

---

## User-Facing Behavior

### Search Activation
- **Entry Point:** Search icon button in the TopAppBar (visible when not viewing labels list and not already searching)
- **UI Change:** When activated, the TopAppBar title area transforms into a search text field
- **Navigation Icon:** Menu icon (☰) changes to Clear icon (X) to exit search mode
- **Action Icons:** Search icon is replaced with a Clear icon (X) when search query is not empty

### Search Input
- **Text Field:** Full-width `OutlinedTextField` in TopAppBar (provides a visible outlined border around the search box)
- **Leading Icon:** Magnifying glass icon (`Icons.Filled.Search`) displayed to the left of the text entry field
- **Placeholder:** "Search bookmarks" (string resource: `R.string.search_bookmarks`)
- **Single Line:** Yes
- **Real-time:** Search executes as user types with debounce delay

### Search Results
- **Display:** Results shown in the main bookmark list view (replaces current filter view)
- **Empty State:** Shows "No results found" message (string resource: `R.string.search_no_results`)
- **Sorting:** Results ordered by bookmark creation date (DESC)

### Search Operators
Search supports Gmail-style operators that can be combined with text queries:

| Operator | Function | Example |
|----------|----------|---------|
| `is:error` | Show bookmarks with ERROR state | `is:error kubernetes` |
| `is:loaded` | Show bookmarks with LOADED state | `is:loaded` |
| `is:loading` | Show bookmarks with LOADING state | `is:loading` |
| `is:empty` | Bookmarks that should have content but don't (hasArticle=true but no content) | `is:empty` |
| `has:content` | Show bookmarks with article content | `has:content python` |
| `has:no-content` | Show bookmarks without article content | `has:no-content` |

**Operator Rules:**
- Case-insensitive
- Can be combined with text query
- Multiple operators allowed in one query
- Operators are whitespace-separated tokens

---

## Implementation Details

### File Structure

**New Files:**
- `/app/src/main/java/de/readeckapp/util/SearchOperators.kt` - Search operator parsing logic

**Modified Files:**
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListViewModel.kt` - Search state management
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListScreen.kt` - Search UI components
- `/app/src/main/java/de/readeckapp/io/db/dao/BookmarkDao.kt` - Search database queries
- `/app/src/main/java/de/readeckapp/domain/BookmarkRepository.kt` - Search repository interface
- `/app/src/main/java/de/readeckapp/domain/BookmarkRepositoryImpl.kt` - Search repository implementation

### Data Models

**SearchOperators.kt:**
```kotlin
data class SearchOperators(
    val textQuery: String,              // Text to search for (after operators removed)
    val state: Bookmark.State? = null,  // Filter by bookmark state
    val hasArticleContent: Boolean? = null  // Filter by content availability
)

fun parseSearchQuery(query: String): SearchOperators {
    // Splits query by whitespace
    // Identifies operator tokens (is:*, has:*)
    // Returns remaining tokens as textQuery
}
```

### ViewModel State Management

**BookmarkListViewModel.kt:**

**State Variables:**
```kotlin
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

private val _isSearchActive = MutableStateFlow(false)
val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
```

**Key Functions:**
```kotlin
fun onSearchQueryChange(query: String)
fun onSearchActiveChange(active: Boolean)  // Clears query when deactivating
fun onClearSearch()  // Clears search query
```

**Search Observation Logic:**
```kotlin
private fun observeBookmarksWithSearch() {
    viewModelScope.launch {
        combine(filterState, searchQuery) { filter, query ->
            Pair(filter, query)
        }
        .flatMapLatest { (filter, query) ->
            if (query.isNotBlank()) {
                // Debounce search queries (delay inside flow builder)
                delay(300)

                // Parse search operators
                val searchOps = parseSearchQuery(query)

                // Special handling for is:empty
                val requiresArticle = query.contains("is:empty", ignoreCase = true)
                val searchState = searchOps.state ?: Bookmark.State.LOADED

                // Execute search
                bookmarkRepository.searchBookmarkListItems(
                    searchQuery = searchOps.textQuery,
                    state = searchState,
                    hasArticleContent = searchOps.hasArticleContent,
                    requiresArticle = requiresArticle,
                    // ... other filter params
                )
            } else {
                // Return regular filtered list when not searching
                bookmarkRepository.observeBookmarkListItems(...)
            }
        }
        .catch { ... }
        .collectLatest { bookmarks ->
            // Update UI state
            if (bookmarks.isEmpty()) {
                if (searchQuery.value.isNotBlank()) {
                    _uiState.value = UiState.Empty(R.string.search_no_results)
                }
            } else {
                _uiState.value = UiState.Success(bookmarks = bookmarks)
            }
        }
    }
}
```

### Database Query (BookmarkDao.kt)

**Function Signature:**
```kotlin
fun searchBookmarkListItems(
    searchQuery: String,
    type: BookmarkEntity.Type? = null,
    isUnread: Boolean? = null,
    isArchived: Boolean? = null,
    isFavorite: Boolean? = null,
    state: BookmarkEntity.State? = null,
    hasArticleContent: Boolean? = null,
    requiresArticle: Boolean = false
): Flow<List<BookmarkListItemEntity>>
```

**Query Details:**
- Uses `SimpleSQLiteQuery` with dynamic SQL building
- LEFT JOIN with `article_content` table to check content existence
- Searches across: `b.title`, `b.labels`, `b.siteName`
- Search pattern: `%$searchQuery%` with `COLLATE NOCASE`
- Content filtering logic:
  - `requiresArticle=true`: `hasArticle = 1 AND (content IS NULL OR content = '')`
  - `hasArticleContent=true`: `content IS NOT NULL AND content != ''`
  - `hasArticleContent=false`: `content IS NULL OR content = ''`
- Order by: `b.created DESC`

**Example Query:**
```sql
SELECT b.id, b.url, b.title, b.siteName, b.isMarked, b.isArchived,
       b.readProgress, b.icon_src AS iconSrc, b.image_src AS imageSrc,
       b.labels, b.thumbnail_src AS thumbnailSrc, b.type
FROM bookmarks b
LEFT JOIN article_content ac ON b.id = ac.bookmarkId
WHERE 1=1
  AND (b.title LIKE ? COLLATE NOCASE
       OR b.labels LIKE ? COLLATE NOCASE
       OR b.siteName LIKE ? COLLATE NOCASE)
  AND b.state = ?
  AND ac.content IS NOT NULL AND ac.content != ''
ORDER BY b.created DESC
```

### Repository Layer

**BookmarkRepository.kt Interface:**
```kotlin
fun searchBookmarkListItems(
    searchQuery: String,
    type: Bookmark.Type? = null,
    unread: Boolean? = null,
    archived: Boolean? = null,
    favorite: Boolean? = null,
    state: Bookmark.State? = null,
    hasArticleContent: Boolean? = null,
    requiresArticle: Boolean = false
): Flow<List<BookmarkListItem>>
```

**BookmarkRepositoryImpl.kt:**
- Converts domain types to entity types (Bookmark.Type → BookmarkEntity.Type)
- Converts entity results back to domain models (BookmarkListItem)
- Maps `readProgress == 100` to `isRead` boolean

### UI Components (BookmarkListScreen.kt)

**TopAppBar Title Area:**
```kotlin
title = {
    if (isSearchActive.value) {
        OutlinedTextField(
            value = searchQuery.value,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = { Text(stringResource(R.string.search_bookmarks)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // ... normal title display
    }
}
```

**Navigation Icon:**
```kotlin
navigationIcon = {
    if (isSearchActive.value) {
        IconButton(onClick = { viewModel.onSearchActiveChange(false) }) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.close_search))
        }
    } else {
        IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
        }
    }
}
```

**Action Icons:**
```kotlin
actions = {
    if (!isSearchActive.value && !filterState.value.viewingLabelsList) {
        IconButton(onClick = { viewModel.onSearchActiveChange(true) }) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        }
    } else if (searchQuery.value.isNotEmpty()) {
        IconButton(onClick = { viewModel.onClearSearch() }) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
        }
    }
}
```

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `search_bookmarks` | "Search bookmarks" | Search field placeholder |
| `search` | "Search" | Search icon content description |
| `close_search` | "Close search" | Close search icon description |
| `clear_search` | "Clear search" | Clear search query icon description |
| `search_no_results` | "No results found" | Empty search results message |

---

## Key Behaviors

1. **Debouncing:** 300ms delay before executing search query to avoid excessive database queries
2. **State Preservation:** Search is active within bookmark list, not a separate screen
3. **Empty Query:** When search query is blank, returns to normal filtered list view
4. **Operator Parsing:** Whitespace-separated tokens checked against known operators
5. **Case Insensitive:** All text searching uses `COLLATE NOCASE`
6. **Default State:** When using `is:empty`, defaults to showing LOADED state bookmarks
7. **Content Searching:** Searches title, siteName, and labels (comma-separated string)

---

## Dependencies

**No External Libraries Required** - Feature uses only:
- Android Jetpack Compose
- Room Database (existing)
- Kotlin Coroutines & Flow (existing)
- Material Icons (existing)

---

## Testing Considerations

**Test Cases:**
1. Search with plain text (matches title)
2. Search with `is:error` operator
3. Search with `has:content` operator
4. Search combining operator + text query
5. Search with multiple spaces between tokens
6. Search clearing (empty query returns to normal view)
7. Case insensitive matching
8. Search across labels (comma-separated field)
9. Empty search results handling
10. Search debounce behavior (rapid typing)

---

## Implementation Notes for Refactor

1. **Search Operator Parser:** The `parseSearchQuery()` function in `SearchOperators.kt` is standalone and has no dependencies - can be copied as-is
2. **Database Query:** The dynamic SQL query in `BookmarkDao.searchBookmarkListItems()` requires LEFT JOIN with article_content table
3. **State Management:** Search state lives in BookmarkListViewModel alongside filter state - they work together
4. **UI State Transition:** Search UI replaces TopAppBar title but doesn't navigate to new screen
5. **Operator Extensibility:** Additional operators can be added by extending `SearchOperators` data class and updating parser
