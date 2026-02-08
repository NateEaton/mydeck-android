# Feature Spec: Search in Article

## Overview

Add a "Search in Article" option to the reading view overflow menu that enables in-content text search with match highlighting and navigation. Available only for Article-type bookmarks in Reader mode. Hidden entirely for Photo and Video types. Disabled (greyed out) in Original view mode.

This spec also covers a companion change: adding an "Open in Browser" option to the overflow menu, and updating icon semantics on both the reading view menu and list view bookmark cards to properly distinguish between "view original in-app" and "open in external browser."

---

## Part 1: Overflow Menu Restructure

### New Menu Order

The reading view overflow menu (`BookmarkDetailMenu`) is reordered as follows:

| # | Item | Icon | Visibility / Conditions |
|---|------|------|------------------------|
| 1 | Increase text size | `Icons.Filled.TextIncrease` | Always shown |
| 2 | Decrease text size | `Icons.Filled.TextDecrease` | Always shown |
| 3 | View Original / View Article / View Photo / View Video | See icon table below | Always shown (disabled when no content available) |
| 4 | Search in Article | `Icons.Filled.Search` | **Hidden** for Photo/Video types. **Disabled** (greyed out) in Original mode or when article content is empty. |
| 5 | Is Read | `Icons.Filled.CheckCircle` / `Icons.Outlined.CheckCircle` | Always shown. Label changed from "Mark Read" to "Is Read" |
| 6 | Open in Browser | `Icons.AutoMirrored.Filled.OpenInNew` | Always shown |
| 7 | Share Link | `Icons.Outlined.Share` | Always shown |
| 8 | Delete | `Icons.Filled.Delete` | Always shown |

### View Original / View Content — Icon Update

| Current Mode | Label | New Icon | Rationale |
|-------------|-------|----------|-----------|
| READER → switch to Original | "View Original" | `Icons.Filled.Language` (globe) | Globe = web content rendered in-app |
| ORIGINAL → switch to Article | "View Article" | `Icons.Outlined.Description` | No change |
| ORIGINAL → switch to Photo | "View Photo" | `Icons.Filled.Image` | No change |
| ORIGINAL → switch to Video | "View Video" | `Icons.Filled.Movie` | No change |

The "View Original" option currently uses `Icons.AutoMirrored.Filled.OpenInNew` (external link). This is misleading because it opens content *within* the app. Changing to the globe icon (`Icons.Filled.Language`) makes the distinction clear: globe = in-app web view, external-link = opens device browser.

### Open in Browser (New Item)

- **Label**: "Open in Browser"
- **Icon**: `Icons.AutoMirrored.Filled.OpenInNew` (external link arrow)
- **Action**: Opens the bookmark URL in the device's default browser via `Intent(ACTION_VIEW, url.toUri())`
- **Position**: Between "Is Read" and "Share Link"
- **Always enabled**: The bookmark always has a URL

Implementation note: Use a simple `ACTION_VIEW` intent rather than `CustomTabsIntent`. The existing `openUrlInCustomTab()` function opens a Chrome Custom Tab (which still looks like an in-app experience). A dedicated "Open in Browser" should launch the full standalone browser, making it clearly distinct from the in-app Original view.

---

## Part 2: List View Card Icon Changes

### Current Behavior (Problem)

All three card layouts (Mosaic, Grid, Compact) show an `Icons.AutoMirrored.Filled.OpenInNew` (external link) icon button. Tapping it calls `onClickBookmarkOpenOriginal(bookmarkId)`, which navigates to the detail screen with `showOriginal = true` — an **in-app** WebView. The external-link icon falsely suggests it opens an external browser.

### New Behavior

Replace the single icon button with **two** icon buttons, placed to the right of the archive icon:

| Order | Icon | Action | Content Description |
|-------|------|--------|-------------------|
| 1st (left) | `Icons.Filled.Language` (globe) | Navigate to detail screen with `showOriginal = true` (in-app Original view) | "View Original" |
| 2nd (right) | `Icons.AutoMirrored.Filled.OpenInNew` (external link) | Open bookmark URL in default browser via `Intent(ACTION_VIEW)` | "Open in Browser" |

This applies to all three card composables:
- `BookmarkMosaicCard` (line ~275)
- `BookmarkGridCard` (line ~508)
- `BookmarkCompactCard` (line ~693)

Each card will need the bookmark URL passed through (currently only `bookmark.id` is available; the URL may need to be added to `BookmarkListItem` if not already present).

### Sizing

The additional icon button is small. Current card icon buttons use:
- Mosaic: 48.dp button, 20.dp icon
- Grid: 36.dp button, 20.dp icon
- Compact: 32.dp button, 18.dp icon

These sizes can accommodate one more button without layout issues. If horizontal space is tight on the Compact layout, consider showing only the globe icon (Original view) and omitting the external-link icon on Compact cards.

---

## Part 3: Search in Article

### User Flow

1. User opens an Article bookmark in reading view (Reader mode)
2. Taps overflow menu (⋮) → selects "Search in Article"
3. The standard TopAppBar is replaced with a **search toolbar**
4. User types a search term; matches are highlighted in the WebView content in real-time
5. User navigates between matches with up/down arrows
6. User exits search via the back arrow, restoring the normal toolbar

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

### Menu Item Conditions

- **Visibility**: Hidden entirely when `bookmark.type` is Photo or Video
- **Enabled state**:
  - **READER mode with content**: Enabled
  - **READER mode without content** (articleContent is null/blank): Disabled (greyed out)
  - **ORIGINAL mode**: Disabled (greyed out) — Original view is out of scope for search

### Highlight Colors

| Highlight | Color | Purpose |
|-----------|-------|---------|
| All matches | `#FFFF00` (yellow) with dark text | Background highlight for every occurrence |
| Current match | `#FF8C00` (dark orange) with dark text | Background highlight for the actively-selected occurrence |

These colors are injected via JavaScript/CSS into the WebView and work across all three article themes (light/dark/sepia).

---

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

A `WebViewSearchBridge` object or set of helper functions that call `WebView.evaluateJavascript()`:

#### 1. `searchAndHighlight(webView, query)` → returns match count

Injected JS logic:
- Remove any existing highlight `<span>` wrappers
- Walk all text nodes in `<div class="container">`
- For each text node, find all case-insensitive occurrences of `query`
- Wrap each match in `<span class="mydeck-search-match" data-match-index="N">` with yellow background
- Return total match count via `evaluateJavascript` result callback

#### 2. `highlightCurrentMatch(webView, index)`

- Remove `.mydeck-search-current` class from all match spans
- Add `.mydeck-search-current` class to the span with `data-match-index == index`
- Scroll that span into view (`element.scrollIntoView({block: 'center', behavior: 'smooth'})`)

#### 3. `clearHighlights(webView)`

- Remove all `<span class="mydeck-search-match">` wrappers, restoring original text nodes

#### Injected CSS (added to HTML templates)

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

The article content WebView reference (`webViewRef` at `BookmarkDetailScreen.kt:445`) is already tracked via `mutableStateOf<WebView?>`. Pass a lambda `onSearchAction: (SearchAction) -> Unit` down from the composable that holds `webViewRef`, where `SearchAction` is a sealed class (`Search(query)`, `GoTo(index)`, `Clear`). The composable translates these to `evaluateJavascript` calls. This keeps the WebView lifecycle in Compose-land.

### JavaScript Enablement

Currently, article-type WebViews have `settings.javaScriptEnabled = false` (only videos enable JS at `BookmarkDetailScreen.kt:457`). Always enable JS for article WebViews — the Sakura CSS template contains no scripts, so this is safe and avoids content-reload complexity when search is activated.

### HTML Template Changes

Add the search highlight CSS to all three template files (`html_template_light.html`, `html_template_dark.html`, `html_template_sepia.html`) so styles are available without runtime injection.

### Interaction with Existing Features

- **Zoom factor**: Highlight CSS uses `!important` so zoom changes don't affect highlight visibility.
- **Scroll progress tracking**: Search-initiated scrolling (scrollIntoView) will trigger scroll progress updates — acceptable behavior.
- **Theme switching**: If theme changes while search is active, content reloads. Re-apply search state after content reload via a `LaunchedEffect` watching `content.value`.
- **Read progress restore**: No conflict — search activation happens after initial content load and scroll restore.

---

## String Resources

```xml
<string name="action_search_in_article">Search in Article</string>
<string name="search_in_article_hint">Search in article…</string>
<string name="search_match_counter">%1$d/%2$d</string>
<string name="search_next_match">Next match</string>
<string name="search_previous_match">Previous match</string>
<string name="close_article_search">Close search</string>
<string name="action_is_read">Is Read</string>
<string name="action_open_in_browser">Open in Browser</string>
```

## Files to Create/Modify

| File | Change |
|------|--------|
| `BookmarkDetailScreen.kt` | Add search toolbar, restructure menu order/icons, add Open in Browser action, wire WebView JS calls |
| `BookmarkDetailViewModel.kt` | Add `ArticleSearchState`, search functions, debounced query flow |
| `BookmarkCard.kt` | Replace single OpenInNew icon with globe (View Original) + OpenInNew (Open in Browser) on all 3 card layouts |
| `BookmarkListScreen.kt` | Wire new `onClickOpenInBrowser` callback for cards |
| `BookmarkListViewModel.kt` | Add `onClickOpenInBrowser(bookmarkId)` to resolve URL and fire intent (or emit event) |
| `BookmarkListItem.kt` | Add `url` field if not already present (needed for Open in Browser from card) |
| `html_template_light.html` | Add `.mydeck-search-match` / `.mydeck-search-current` CSS |
| `html_template_dark.html` | Same CSS addition |
| `html_template_sepia.html` | Same CSS addition |
| `strings.xml` | Add/update ~8 string resources |
| **New:** `ArticleSearchBar.kt` (ui/detail/) | Extracted composable for the search toolbar |
| **New:** `WebViewSearchBridge.kt` (ui/detail/) | JavaScript generation and WebView evaluation helpers |

## Edge Cases

- **Empty article content**: Search menu item disabled if `articleContent` is null/blank
- **Original mode**: Search menu item disabled (greyed out)
- **Photo/Video types**: Search menu item hidden entirely
- **Special characters in query**: JS search must escape regex special characters (use literal string matching)
- **HTML entities**: JS walker operates on text nodes (already decoded), so `&amp;` etc. are handled transparently
- **WebView destroyed**: Guard `evaluateJavascript` calls against null WebView ref
- **Rapid typing**: 300ms debounce prevents excessive DOM manipulation
- **Theme change during search**: Re-run search after content reload
- **Compact card space**: If two icon buttons don't fit on Compact cards, show only the globe (View Original); omit the external-link icon

## Testing

- Unit tests for ViewModel search state transitions (activate, query change, next/prev, deactivate)
- Unit tests for match counter wrap-around logic
- Manual testing: search across light/dark/sepia themes, zoom levels, long articles, articles with code blocks and images
- Verify Search in Article hidden for Photo/Video types
- Verify Search in Article greyed out in Original mode
- Verify highlights cleared on search exit
- Verify Open in Browser launches default browser (not Custom Tab)
- Verify card icons: globe opens in-app Original, external-link opens browser
- Verify menu order matches spec

## Out of Scope

- Search in Original (WebView) mode
- Search in Photo or Video content types
- Full-text search across all bookmarks (separate feature)
- Persistent search terms / search history

---

## Implementation Changes from Original Spec

During implementation and user testing, several refinements were made to improve the user experience:

### Terminology Changes

- **User-facing text**: Changed from "Search in Article" to "**Find in Article**" throughout the UI
- **Placeholder text**: Changed from "Search in article…" to simply "**Find**" for brevity
- **String resources**: Updated `action_search_in_article` label to "Find in Article", placeholder to "Find"
- **Internal code**: Remains as "search" (see Appendix A for complexity assessment of a full rename)

### Menu Changes

- **Title-case formatting**: All menu items now use title-case for consistency (e.g., "Increase Text Size" instead of "Increase text size")
- **Menu order**: Finalized order is:
  1. Increase Text Size
  2. Decrease Text Size
  3. Find in Article
  4. View Original (or View Article/Photo/Video depending on mode)
  5. Open in Browser
  6. Share Link
  7. Is Read
  8. Delete
- **Icon change**: Menu icon changed from `Icons.Filled.Search` to `Icons.Filled.FindInPage` (magnifying glass on page) to better match browser find functionality

### Search Bar UI Changes

- **Icon removal**: Removed the leading magnifying glass icon from inside the search field for a cleaner appearance
- **Font consistency**: Set explicit `textStyle = MaterialTheme.typography.bodyMedium` to ensure placeholder and typed text use the same font size
- **Match counter position**: Counter remains as trailing content inside the text field showing "n/m" format

### List Search Consistency

- **Icon removal**: Also removed the magnifying glass icon from the list view search field for UI consistency
- **Font normalization**: Set `textStyle = MaterialTheme.typography.bodyLarge` to match placeholder and input text sizes

### Rationale

- "Find" is more concise and matches browser terminology (Ctrl+F / Cmd+F)
- Title-case menu items provide better visual hierarchy
- FindInPage icon clearly indicates in-page search vs. general search
- Removing interior icons reduces visual clutter while maintaining clarity
- Font normalization prevents awkward size mismatches during typing

---

## Appendix A: Complexity Assessment — Renaming Internal Code from "Search" to "Find"

### Overview

This appendix documents the estimated effort required to rename internal code references from "search" to "find" to match the user-facing terminology. This was assessed but **not implemented**, as the user-facing terminology is already "Find" and internal code consistency is maintained with "search."

### Scope of Changes

A full rename would affect:

#### 1. File Renames (2 files)
- `ArticleSearchBar.kt` → `ArticleFindBar.kt`
- `WebViewSearchBridge.kt` → `WebViewFindBridge.kt`

#### 2. ViewModel Functions (~20 occurrences)
- `ArticleSearchState` → `ArticleFindState`
- `_articleSearchState` → `_articleFindState`
- `articleSearchState` → `articleFindState`
- `onArticleSearchActivate()` → `onArticleFindActivate()`
- `onArticleSearchDeactivate()` → `onArticleFindDeactivate()`
- `onArticleSearchQueryChange()` → `onArticleFindQueryChange()`
- `onArticleSearchUpdateResults()` → `onArticleFindUpdateResults()`
- `onArticleSearchNext()` → `onArticleFindNext()`
- `onArticleSearchPrevious()` → `onArticleFindPrevious()`
- `searchDebounceJob` → `findDebounceJob`

#### 3. Composable Functions & Parameters (~15 occurrences)
- `ArticleSearchBar()` → `ArticleFindBar()`
- `WebViewSearchBridge` → `WebViewFindBridge`
- All function calls and imports across:
  - `BookmarkDetailScreen.kt`
  - `BookmarkDetailViewModel.kt`
  - `ArticleSearchBar.kt` (renamed file)
  - `WebViewSearchBridge.kt` (renamed file)

#### 4. Variable Names (~15 occurrences)
- `articleSearchState` → `articleFindState`
- Parameter names in composables
- Local variable references

#### 5. CSS Classes (2 occurrences)
- `.mydeck-search-match` → `.mydeck-find-match`
- `.mydeck-search-current` → `.mydeck-find-current`
- Requires updates in:
  - `html_template_light.html`
  - `html_template_dark.html`
  - `html_template_sepia.html`
  - JavaScript in `WebViewSearchBridge.kt`

#### 6. Comments & Documentation (~15 occurrences)
- KDoc comments
- Inline code comments
- Function descriptions

### Estimated Effort

**Total time: 4-5 hours**

Breakdown:
- **1 hour**: Rename files and update imports across the codebase
- **1.5 hours**: Rename all functions, variables, and state classes
- **0.5 hours**: Update CSS classes in HTML templates and JavaScript
- **0.5 hours**: Update comments and documentation
- **1 hour**: Testing — verify no regressions, test all find functionality
- **0.5 hours**: Code review and polish

### Risks & Considerations

1. **Import churn**: All files importing `ArticleSearchBar` or `WebViewSearchBridge` need updates
2. **String resource confusion**: Internal "find" vs. string resource keys still named "search_*" could cause confusion for future developers
3. **Git history**: File renames can make blame/history harder to follow
4. **Limited value**: Since user-facing terminology is already "Find," internal naming consistency doesn't impact users
5. **CSS class changes**: Must ensure JavaScript correctly references new class names

### Recommendation

**Do not rename at this time.** The user-facing experience is already correct ("Find in Article"). Internal code uses "search" consistently, which is semantically accurate (the feature *is* searching). The effort-to-value ratio is poor — renaming would consume 4-5 hours with no functional or UX benefit.

Consider renaming only if:
- A major refactor touches these files anyway
- New "find" features are added and naming conflicts arise
- Code review feedback specifically requests alignment

### Testing Checklist (if rename is pursued)

- [ ] Find toolbar appears and hides correctly
- [ ] Text highlighting works (yellow for all matches, orange for current)
- [ ] Navigation between matches (up/down arrows) works
- [ ] Match counter displays correct "n/m" values
- [ ] Debouncing works (no excessive DOM manipulation)
- [ ] Theme changes (light/dark/sepia) preserve highlights
- [ ] Original mode correctly disables find menu item
- [ ] Photo/Video types correctly hide find menu item
- [ ] All imports resolve without errors
- [ ] No console errors in WebView JavaScript
