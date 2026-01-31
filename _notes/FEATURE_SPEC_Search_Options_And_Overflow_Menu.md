# Feature Spec: Search Options & Overflow Menu

**Document Type:** UX Design Spec (Future)
**Purpose:** Planning document for search enhancements and TopAppBar overflow menu
**Date:** 2026-01-31
**Status:** Proposed

---

## Overview

Extend the existing global search feature with two optional search modes (full-text search and cross-view search) and introduce a three-dot overflow menu to the TopAppBar that provides context-sensitive actions including Sort and search options.

---

## Current State

- Search filters the **current view** (My List, Archive, or Favorites)
- Search matches against **title**, **site name**, and **labels** only
- TopAppBar has: hamburger menu (left), title (center), search icon (right)
- In search mode: back arrow (left), OutlinedTextField with magnifying glass and inline clear (center)

---

## Proposed UX

### Three-Dot Overflow Menu

A `MoreVert` (⋮) icon button added to the TopAppBar `actions`, always visible in both normal and search modes. The menu content changes based on context.

**Normal mode layout:**
```
[☰]  My List                    [🔍] [⋮]
                                      ├─ Sort          ▸
                                      └─ (future items)
```

**Search mode layout:**
```
[←]  [🔍 Search bookmarks  ✕]        [⋮]
                                      ├─ Search all views   ☐
                                      └─ Include content    ☐
```

### Search Options

Two toggleable options available from the overflow menu when search is active:

#### 1. Search All Views
- **Default:** OFF (unchecked)
- **Behavior when OFF:** Search respects the current filter (My List, Archive, Favorites). This is the existing behavior.
- **Behavior when ON:** Search ignores archived/favorite/unread filters and queries all bookmarks regardless of view. The type filter (Articles, Videos, Pictures) is still respected.
- **Implementation:** When enabled, pass `null` for `archived`, `favorite`, and `unread` parameters to `searchBookmarkListItems()` instead of the current filter values.
- **Placeholder text change:** "Search all bookmarks" instead of "Search bookmarks"

#### 2. Include Content (Full-Text Search)
- **Default:** OFF (unchecked)
- **Behavior when OFF:** Search matches title, site name, and labels only. This is the existing behavior.
- **Behavior when ON:** Search additionally matches against the `article_content` table's `content` column.
- **Implementation:** Requires LEFT JOIN with `article_content` table and adding `OR ac.content LIKE ? COLLATE NOCASE` to the WHERE clause in `BookmarkDao.searchBookmarkListItems()`.
- **Placeholder text change:** "Search all text" (or "Search all bookmarks and text" if combined with Search All Views)

### Sort (Normal Mode)

Available from the overflow menu when not searching:

| Sort Option | SQL | Notes |
|------------|-----|-------|
| Newest first | `ORDER BY created DESC` | Default, current behavior |
| Oldest first | `ORDER BY created ASC` | |
| By site name | `ORDER BY siteName ASC` | Alphabetical |
| By title | `ORDER BY title ASC` | Alphabetical |

---

## State Management

### New ViewModel State

```kotlin
data class SearchOptions(
    val searchAllViews: Boolean = false,
    val includeContent: Boolean = false
)

enum class SortOrder {
    NEWEST_FIRST,   // created DESC (default)
    OLDEST_FIRST,   // created ASC
    BY_SITE_NAME,   // siteName ASC
    BY_TITLE        // title ASC
}

// New state flows
private val _searchOptions = MutableStateFlow(SearchOptions())
val searchOptions: StateFlow<SearchOptions> = _searchOptions.asStateFlow()

private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
```

### State Reset Behavior

- Search options reset to defaults when search is deactivated (back arrow pressed)
- Sort order persists across sessions (save to SettingsDataStore)

### Reactive Flow Changes

The `combine` in the ViewModel init block would expand to include search options:

```kotlin
combine(_filterState, _searchQuery, _searchOptions) { filter, query, options ->
    Triple(filter, query, options)
}.flatMapLatest { (filter, query, options) ->
    if (query.isNotBlank()) {
        delay(300)
        bookmarkRepository.searchBookmarkListItems(
            searchQuery = query,
            type = filter.type,
            unread = if (options.searchAllViews) null else filter.unread,
            archived = if (options.searchAllViews) null else filter.archived,
            favorite = if (options.searchAllViews) null else filter.favorite,
            state = Bookmark.State.LOADED,
            includeContent = options.includeContent
        )
    } else {
        // normal filtered list
    }
}
```

---

## Database Changes

### Full-Text Search Query

When `includeContent = true`, the search query in `BookmarkDao.searchBookmarkListItems()` changes from:

```sql
SELECT ... FROM bookmarks WHERE ...
  AND (title LIKE ? COLLATE NOCASE
       OR labels LIKE ? COLLATE NOCASE
       OR siteName LIKE ? COLLATE NOCASE)
```

To:

```sql
SELECT ... FROM bookmarks b
  LEFT JOIN article_content ac ON b.id = ac.bookmarkId
WHERE ...
  AND (b.title LIKE ? COLLATE NOCASE
       OR b.labels LIKE ? COLLATE NOCASE
       OR b.siteName LIKE ? COLLATE NOCASE
       OR ac.content LIKE ? COLLATE NOCASE)
```

### Performance Considerations

- LIKE on `article_content.content` is expensive — article bodies can be large HTML/text
- Consider adding a debounce increase (e.g., 500ms instead of 300ms) when content search is enabled
- Long-term: SQLite FTS5 virtual table would dramatically improve full-text search performance but requires a database migration and keeping the FTS index in sync with the content table
- For the initial implementation, LIKE is acceptable given typical collection sizes (hundreds to low thousands of bookmarks)

### Sort Order

Add `sortOrder` parameter to both `searchBookmarkListItems()` and `getBookmarkListItemsByFilters()` DAO methods. Map enum to SQL:

```kotlin
val orderClause = when (sortOrder) {
    SortOrder.NEWEST_FIRST -> "created DESC"
    SortOrder.OLDEST_FIRST -> "created ASC"
    SortOrder.BY_SITE_NAME -> "siteName COLLATE NOCASE ASC"
    SortOrder.BY_TITLE -> "title COLLATE NOCASE ASC"
}
```

---

## UI Components

### Overflow Menu (DropdownMenu)

```kotlin
// In TopAppBar actions:
var showMenu by remember { mutableStateOf(false) }

IconButton(onClick = { showMenu = true }) {
    Icon(Icons.Default.MoreVert, contentDescription = "More options")
}

DropdownMenu(
    expanded = showMenu,
    onDismissRequest = { showMenu = false }
) {
    if (isSearchActive.value) {
        // Search options with checkmarks
        DropdownMenuItem(
            text = { Text("Search all views") },
            leadingIcon = {
                if (searchOptions.searchAllViews)
                    Icon(Icons.Default.Check, contentDescription = null)
            },
            onClick = { viewModel.toggleSearchAllViews() }
        )
        DropdownMenuItem(
            text = { Text("Include content") },
            leadingIcon = {
                if (searchOptions.includeContent)
                    Icon(Icons.Default.Check, contentDescription = null)
            },
            onClick = { viewModel.toggleIncludeContent() }
        )
    } else {
        // Sort submenu
        DropdownMenuItem(
            text = { Text("Sort") },
            trailingIcon = { Icon(Icons.Default.ArrowRight, ...) },
            onClick = { /* expand sort submenu or navigate */ }
        )
    }
}
```

### Dynamic Placeholder Text

```kotlin
val searchPlaceholder = when {
    searchOptions.searchAllViews && searchOptions.includeContent ->
        "Search all bookmarks and text"
    searchOptions.searchAllViews ->
        "Search all bookmarks"
    searchOptions.includeContent ->
        "Search all text"
    else ->
        stringResource(R.string.search_bookmarks)  // "Search bookmarks"
}
```

---

## String Resources

```xml
<string name="more_options">More options</string>
<string name="search_all_views">Search all views</string>
<string name="include_content">Include content</string>
<string name="search_all_bookmarks">Search all bookmarks</string>
<string name="search_all_text">Search all text</string>
<string name="search_all_bookmarks_and_text">Search all bookmarks and text</string>
<string name="sort">Sort</string>
<string name="sort_newest_first">Newest first</string>
<string name="sort_oldest_first">Oldest first</string>
<string name="sort_by_site_name">By site name</string>
<string name="sort_by_title">By title</string>
```

---

## Implementation Order

1. Add three-dot overflow menu (empty, structural only)
2. Add Sort feature (normal mode) — most broadly useful
3. Add "Search all views" toggle
4. Add "Include content" toggle (requires DAO changes)
5. (Future) Consider FTS5 migration for performance at scale
