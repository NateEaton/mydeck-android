# Highlights Search, Filter, Sort, And Scroll Spec

**Status:** Corrected implementation spec
**Date:** 2026-05-11
**Build Context:** Highlights local-first sync refinement
**Related:**
- `docs/specs/highlights-nav-drawer-list-spec.md`
- `docs/specs/highlights-list-local-first-scalability-amendment.md`
- `docs/specs/highlights-sync-refinement-delta-guided.md`

## 1. Summary

The Highlights list needs discovery and long-list navigation affordances while
preserving the Readeck-like grouped presentation. Highlights must remain grouped
by bookmark/article. Search, filters, sort, and scrollbars operate over cached
local Room data and must not trigger a server-side search or a global annotation
refresh.

This spec adds:

- a single search/filter entry point in the top app bar,
- local text search over highlight text, notes, bookmark title, and site name,
- lightweight filters for search target, highlight color, and note presence,
- newest-first and oldest-first bookmark group sort order,
- an existing passive `VerticalScrollbar`,
- title tap to return to the top when the title is visible,
- clear empty states for no matching grouped results.

## 2. Product Direction

Do not convert the Highlights screen into a flat highlight timeline. The rendered
model is bookmark/article groups:

```kotlin
data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val highlights: List<HighlightSummary>,
)
```

The list order is based on visible highlights in each group:

- descending: groups ordered by newest visible highlight in the group,
- ascending: groups ordered by oldest visible highlight in the group.

Highlight ordering within each group follows the same selected order:

- descending: highlights inside each group are newest-first,
- ascending: highlights inside each group are oldest-first.

## 3. Goals

- Keep Highlights local-first and responsive with thousands of cached highlights.
- Preserve bookmark/article grouping and navigation to bookmark plus annotation ID.
- Filter grouped output without flattening the rendered list.
- Make active filters discoverable when controls are hidden.
- Preserve the standard scroll progress indicator used by other long lists.
- Add all new strings to every `strings.xml` file as English placeholders.
- Document user-visible behavior in `app/src/main/assets/guide/en/`.

## 4. Non-Goals

- Do not implement server-side search.
- Do not require a global highlight reconciliation for search/filter correctness.
- Do not add saved searches.
- Do not add Boolean query syntax.
- Do not introduce Paging 3 or FTS unless in-memory filtering clearly fails.
- Do not redesign reader/deep-link behavior.
- Do not add a jump-to-end or jump-to-oldest top app bar action.

## 5. Grouped Search And Filtering

Search/filter derives a filtered list of `BookmarkHighlightGroup` values:

- a group remains visible only if it has one or more visible highlights,
- text and note matches show matching highlights,
- title and site matches may include all highlights in the matching group that also
  pass color and note filters,
- an empty query returns all highlights, subject to active non-query filters,
- result counts should describe grouped data, for example
  `N highlights in M articles`.

Default search targets:

- Text,
- Notes,
- Title,
- Site.

If disabling targets would leave no active target, keep the last target enabled.

Color filter:

- Any color,
- Yellow,
- Red,
- Blue,
- Green,
- None.

Notes filter:

- Any notes,
- With notes,
- Without notes.

All filtering is local-only over cached highlights.

## 6. Top App Bar

Default mode:

- back navigation,
- clickable `Highlights` title that scrolls to the top,
- one filter/search icon that opens the controls,
- one sort icon that toggles newest-first and oldest-first order for groups and
  highlights inside each group.

Search/filter mode:

- close/X hides the controls but does not clear query or filters,
- search text field is focused,
- clear-search icon clears only the query,
- filter chips remain visible below the app bar,
- explicit clear action resets query and filters to the unfiltered list.

When search/filter controls are hidden but filters are active, the filter/search
entry point should indicate the active state, for example with primary tint.

## 7. Sort Order

Add a simple sort-order toggle:

- Descending/newest-first: sort groups by newest visible highlight date/time.
- Ascending/oldest-first: sort groups by oldest visible highlight date/time.
- Apply the same order to visible highlights inside each group.

The sort key uses only currently visible highlights after search/filter is applied.
For example, if a recent highlight in a group is filtered out, the group should be
ordered by the newest or oldest remaining visible highlight.

## 8. Scrollbar

Add the existing passive `VerticalScrollbar` used by main list views:

- use `rememberLazyListState()`,
- pass it to `LazyColumn(state = lazyListState)`,
- overlay `VerticalScrollbar(lazyListState = lazyListState)` at center/end.

The interactive date fast-scroll handle is deferred. Do not show a separate
right-edge fast-scroll control, month/year drag label, or extra list padding for it
in this implementation. The standard passive scroll indicator should remain.

## 9. Empty And Refresh States

Use distinct states:

- no saved highlights at all: existing empty message,
- no matches after search/filter: `No matching highlights`,
- refresh failed with cached rows: keep cached browsing available and use an
  unobtrusive retry affordance rather than a persistent large banner.

## 10. ViewModel State

The ViewModel should keep sync state separate from local search/filter/sort state.
The final shape can vary, but it should include:

```kotlin
data class HighlightsUiState(
    val groups: List<BookmarkHighlightGroup> = emptyList(),
    val filteredGroups: List<BookmarkHighlightGroup> = emptyList(),
    val query: String = "",
    val searchTargets: SearchTargets = SearchTargets.All,
    val selectedColor: HighlightColorFilter = HighlightColorFilter.Any,
    val noteFilter: HighlightNoteFilter = HighlightNoteFilter.Any,
    val sortOrder: HighlightSortOrder = HighlightSortOrder.Descending,
    val isSearchActive: Boolean = false,
    val isInitialLocalLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)
```

Changing query, filters, or sort must not request a network refresh.

## 11. Logging

Add concise debug logs:

```text
Highlights search changed: queryLength=... targets=... colors=... noteFilter=...
Highlights filtered results: total=... filtered=...
Highlights scroll title tapped: action=top
```

Do not log actual query text.

## 12. Strings

Likely strings:

- `highlights_search_hint`: `Search highlights`
- `highlights_clear_search`: `Clear search`
- `highlights_filter`: `Filter highlights`
- `highlights_no_matches`: `No matching highlights`
- `highlights_clear_filters`: `Clear filters`
- `highlights_filter_text`: `Text`
- `highlights_filter_notes`: `Notes`
- `highlights_filter_title`: `Title`
- `highlights_filter_site`: `Site`
- `highlights_filter_color_any`: `Any color`
- `highlights_filter_color_yellow`: `Yellow`
- `highlights_filter_color_red`: `Red`
- `highlights_filter_color_blue`: `Blue`
- `highlights_filter_color_green`: `Green`
- `highlights_filter_color_none`: `None`
- `highlights_filter_notes_any`: `Any notes`
- `highlights_filter_with_notes`: `With notes`
- `highlights_filter_without_notes`: `Without notes`
- `highlights_grouped_results_count`: `%1$d highlights in %2$d articles`
- `highlights_sort_newest_first`: `Newest first`
- `highlights_sort_oldest_first`: `Oldest first`

Add English placeholders to all language `strings.xml` files.

## 13. User Guide

Update the English guide under `app/src/main/assets/guide/en/` to document:

- Highlights remain grouped by bookmark/article,
- search is local and can include text, notes, title, and site,
- color and notes filters narrow visible highlights,
- the sort toggle changes newest-first or oldest-first order for article groups and
  highlights inside each group,
- tapping the title scrolls to the top,
- the standard scroll progress indicator appears while scrolling long lists,
- browsing/search uses saved highlights while sync runs in the background.

## 14. Tests

Unit/ViewModel tests should cover:

- search/filter preserves grouped output,
- filtering removes nonmatching highlights/groups without flattening the rendered model,
- query matches highlight text,
- query matches note,
- query matches bookmark title,
- query matches site name,
- search target filters restrict matching,
- color filter restricts results,
- notes filter restricts results,
- descending group order uses newest visible highlight per group,
- ascending group order uses oldest visible highlight per group,
- descending and ascending sort order visible highlights within each group,
- empty query returns all highlights,
- no-match state is derived correctly,
- cached highlights remain visible during/after refresh failure,
- screen-open/search/filter/sort does not trigger extra network/global refresh,
- closing search/filter controls does not clear active query/filter state,
- explicit clear action resets to the unfiltered grouped list.

## 15. Verification

Run Gradle tasks serially:

```bash
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```
