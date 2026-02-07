# Feature Spec: Search in Article

## Overview

Add a "Search in Article" option to the reading view overflow menu that enables in-content text search with match highlighting and navigation. Available only for Article-type bookmarks; greyed out for Photo and Video types.

## User Flow

1. User opens an Article bookmark in reading view
2. Taps overflow menu (⋮) → selects "Search in Article"
3. The standard TopAppBar is replaced with a **search toolbar**
4. User types a search term; matches are highlighted in the WebView content in real-time
5. User navigates between matches with up/down arrows
6. User exits search via the back arrow, restoring the normal toolbar

## UI Design

### Menu Item

- **Label**: "Search in Article" with `Icons.Filled.Search` leading icon
- **Position**: After "Mark Read/Unread", before "View Original/Article" toggle
- **Visibility**: Only shown when `bookmark.type == Article`
- **Enabled state**:
  - In **READER mode**: Always enabled (article HTML content is searchable)
  - In **ORIGINAL mode**: Greyed out and disabled (third-party page content is not reliably searchable via injected JS due to cross-origin frames, dynamic content, and varied DOM structures)

### Search Toolbar (replaces TopAppBar)

Modeled after the existing list view search bar (`BookmarkListScreen.kt:381-407`) with additions:

```
[←] [ 🔍  Search in article...   n/m ] [↑] [↓]
```

| Element | Details |
|---------|---------|
| **Back arrow** (left) | `Icons.AutoMirrored.Filled.ArrowBack` — exits search mode, clears highlights, restores normal toolbar |
| **Search field** (center) | `OutlinedTextField`, single-line, auto-focused on entry. Leading icon: `Icons.Filled.Search` (magnifying glass inside the field). Placeholder: "Search in article..." |
| **Match counter** (inside field, trailing) | Text label `"n/m"` where n = current match index (1-based), m = total matches. Shown only when query is non-empty. Shows `"0/0"` when no matches found. |
| **Up arrow** (right of field) | `Icons.Filled.KeyboardArrowUp` — navigates to previous match (wraps from first to last) |
| **Down arrow** (right of field) | `Icons.Filled.KeyboardArrowDown` — navigates to next match (wraps from last to first) |

The up/down arrows should be disabled (alpha 0.38) when there are 0 matches.

### Highlight Colors

| Highlight | Color | Purpose |
|-----------|-------|---------|
| All matches | `#FFFF00` (yellow) with dark text | Background highlight for every occurrence |
| Current match | `#FF8C00` (dark orange) with dark text | Background highlight for the actively-selected occurrence |

These colors are injected via JavaScript/CSS into the WebView and work across all three article themes (light/dark/sepia).

## Technical Design

### Architecture

The feature involves three layers:

1. **Compose UI** — search toolbar state, user input, navigation buttons
2. **ViewModel** — search state management (query, match count, current index)
3. **WebView JavaScript bridge** — highlight injection, scroll-to-match, clear highlights

### State Model (BookmarkDetailViewModel)

```kotlin
data class ArticleSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val totalMatches: Int = 0,
    val currentMatch: Int = 0  // 1-based, 0 when no matches
)

private val _articleSearchState = MutableStateFlow(ArticleSearchState())
val articleSearchState: StateFlow<ArticleSearchState> = _articleSearchState.asStateFlow()
```

**ViewModel functions:**

```kotlin
fun onArticleSearchActivate()        // Sets isActive = true
fun onArticleSearchDeactivate()      // Resets entire state, triggers JS clear
fun onArticleSearchQueryChange(query: String)  // Updates query, triggers JS search
fun onArticleSearchNext()            // Increments currentMatch (wrapping)
fun onArticleSearchPrevious()        // Decrements currentMatch (wrapping)
```

Query changes should be debounced (~300ms, matching list search behavior) before triggering the JavaScript search.

### WebView JavaScript Bridge

A `WebViewJsBridge` object or set of helper functions that call `WebView.evaluateJavascript()`:

#### 1. `searchAndHighlight(webView, query)` → returns match count

Injected JS logic:
- Remove any existing highlight `<span>` wrappers
- Walk all text nodes in `<div class="container">`
- For each text node, find all case-insensitive occurrences of `query`
- Wrap each match in `<span class="mydeck-search-match" data-match-index="N">` with yellow background
- Return total match count via `JavascriptInterface` callback or `evaluateJavascript` result callback

#### 2. `highlightCurrentMatch(webView, index)`

- Remove `.mydeck-search-current` class from all match spans
- Add `.mydeck-search-current` class to the span with `data-match-index == index`
- Scroll that span into view (`element.scrollIntoView({block: 'center', behavior: 'smooth'})`)

#### 3. `clearHighlights(webView)`

- Remove all `<span class="mydeck-search-match">` wrappers, restoring original text nodes

#### Injected CSS (prepended once)

```css
.mydeck-search-match {
    background-color: #FFFF00 !important;
    color: #000000 !important;
    border-radius: 2px;
}
.mydeck-search-current {
    background-color: #FF8C00 !important;
    color: #000000 !important;
    border-radius: 2px;
}
```

### WebView Reference Management

The article content WebView reference (`webViewRef` at `BookmarkDetailScreen.kt:445`) is already tracked via `mutableStateOf<WebView?>`. This reference will be hoisted/shared so the search toolbar can trigger JS calls on it. Options:

- **Option A (recommended)**: Pass a lambda `onSearchAction: (SearchAction) -> Unit` down from the composable that holds `webViewRef`, where `SearchAction` is a sealed class (`Search(query)`, `GoTo(index)`, `Clear`). The composable handles translating these to `evaluateJavascript` calls.
- **Option B**: Hoist `webViewRef` into the ViewModel (less idiomatic for Compose but simpler bridging).

Option A keeps the WebView lifecycle in Compose-land where it belongs.

### JavaScript Enablement

Currently, article-type WebViews have `settings.javaScriptEnabled = false` (only videos enable JS). This must be changed:

- When article search is active, JavaScript must be enabled on the article WebView
- **Approach**: Always enable JS for article WebViews. The Sakura CSS template contains no scripts, so this is safe. Alternatively, enable JS only when search is activated (requires a WebView reload which is disruptive).
- **Recommendation**: Always enable JS for articles. It's a minimal change (`BookmarkDetailScreen.kt:457`) and avoids content reload complexity.

### HTML Template Changes

Add the search highlight CSS to all three template files (`html_template_light.html`, `html_template_dark.html`, `html_template_sepia.html`) so styles are available without runtime injection. This avoids a FOUC (flash of unstyled content) if the user searches immediately.

### Integration with Content Modes

| Mode | Search Available | Reason |
|------|-----------------|--------|
| READER (Article) | Yes | Content is local HTML in a controlled template; JS injection is reliable |
| ORIGINAL (WebView) | No (greyed out) | Third-party pages may have CSP headers blocking injected scripts, iframes with cross-origin content, dynamically loaded content, and complex DOM structures that break text-node walking |

If ORIGINAL mode support is desired in the future, Android's built-in `WebView.findAllAsync()` / `WebView.findNext()` API could be explored, but it has limited styling control and is deprecated in favor of custom solutions.

### Interaction with Existing Features

- **Zoom factor**: Highlight CSS uses `!important` so zoom changes don't affect highlight visibility. JS re-search is not needed on zoom change.
- **Scroll progress tracking**: Search-initiated scrolling (scrollIntoView) will trigger scroll progress updates, which is acceptable — the user is still reading.
- **Theme switching**: If the user changes theme while search is active, content reloads. Search state (query, matches) should be re-applied after content reload via a `LaunchedEffect` watching `content.value`.
- **Read progress restore**: No conflict — search activation happens after initial content load and scroll restore.

## String Resources

```xml
<string name="action_search_in_article">Search in Article</string>
<string name="search_in_article_hint">Search in article…</string>
<string name="search_match_counter">%1$d/%2$d</string>
<string name="search_next_match">Next match</string>
<string name="search_previous_match">Previous match</string>
<string name="close_article_search">Close search</string>
```

## Files to Create/Modify

| File | Change |
|------|--------|
| `BookmarkDetailScreen.kt` | Add search toolbar composable, menu item, wire WebView JS calls |
| `BookmarkDetailViewModel.kt` | Add `ArticleSearchState`, search functions, debounced query flow |
| `html_template_light.html` | Add `.mydeck-search-match` / `.mydeck-search-current` CSS |
| `html_template_dark.html` | Same CSS addition |
| `html_template_sepia.html` | Same CSS addition |
| `strings.xml` | Add 6 new string resources |
| **New:** `ArticleSearchBar.kt` (ui/detail/) | Extracted composable for the search toolbar (keeps DetailScreen manageable) |
| **New:** `WebViewSearchBridge.kt` (ui/detail/) | JavaScript generation and WebView evaluation helpers |

## Edge Cases

- **Empty article content**: Search menu item should be disabled if `articleContent` is null/blank (same as "View Original" disabled logic)
- **Very long articles**: JS text-node walking should be performant for typical article lengths (< 100KB HTML). No pagination needed.
- **Special characters in query**: The JS search must escape regex special characters in the user's query string (use literal string matching, not regex)
- **HTML entities**: The JS walker operates on text nodes (already decoded), so `&amp;` etc. are handled transparently
- **WebView destroyed**: Guard `evaluateJavascript` calls against null WebView ref
- **Rapid typing**: 300ms debounce prevents excessive DOM manipulation during fast typing
- **Search across theme change**: Re-run search after content reload (detected via `content.value` change)

## Testing

- Unit tests for ViewModel search state transitions (activate, query change, next/prev, deactivate)
- Unit tests for match counter wrap-around logic
- Manual testing: search in light/dark/sepia themes, zoom levels, long articles, articles with code blocks, articles with images interspersed
- Verify menu item hidden for Photo/Video types
- Verify menu item greyed out in Original mode
- Verify highlights cleared on search exit
- Verify scroll-to-match works correctly at top/bottom of article

## Out of Scope

- Full-text search across all bookmarks (separate feature: Global Full Text Search)
- Search in Original (WebView) mode — documented as greyed out
- Search in Photo or Video content types
- Persistent search terms / search history
