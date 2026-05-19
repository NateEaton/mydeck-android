# Bookmark Compact Date Fast Scroll Prototype Spec

**Status:** Draft prototype spec
**Date:** 2026-05-18
**Build Context:** Future branch; do not implement on the current branch
**Related:**
- `docs/specs/highlights-date-fast-scroll-spec.md`
- `docs/specs/highlights-search-filter-fast-scroll-spec.md`

## 1. Summary

Prototype a date fast-scroll overlay for the main bookmark card list, limited to
the Compact layout and date-based sort modes.

Unlike Readeck's native paged bookmark lists, MyDeck renders bookmark results as
one continuous list. For accounts with many years of sporadic bookmarks, this
creates the same long-list navigation problem as Highlights, but with a better
local test dataset: bookmark `created` dates already span many years.

This prototype should validate the interaction model before reviving the
Highlights fast-scroll work.

## 2. Scope

Implement only for:

- `BookmarkListView`
- `LayoutMode.COMPACT`
- non-wide/single-column rendering path using `LazyColumn`
- date-based sort modes:
  - `SortOption.ADDED_NEWEST`
  - `SortOption.ADDED_OLDEST`

When these conditions are not met, keep the existing passive `VerticalScrollbar`
behavior.

## 3. Non-Goals

- Do not support grid, mosaic, or multi-column layouts in the prototype.
- Do not support title, site, duration, or other non-date sort modes.
- Do not change bookmark query, filtering, sync, or sorting semantics.
- Do not add date section headers to the list.
- Do not modify the Readeck API or local schema.
- Do not revive or depend on the shelved Highlights implementation branch.
- Do not attempt a pixel-perfect Google Photos clone.

## 4. User Experience

In Compact layout with Added newest/oldest sorting:

- while scrolling, show a transient right-edge date fast-scroll control,
- show the active month/year and available years represented by visible
  bookmarks,
- allow dragging the right-edge handle to jump by month/year,
- fade the overlay out after scrolling stops,
- preserve all normal card taps, swipe actions, overflow actions, pull-to-refresh,
  and scroll-to-top behavior.

In any other layout or sort mode:

- show the current passive scrollbar only,
- do not show date labels or drag-to-date behavior.

## 5. Date Basis

For this prototype, anchors use `BookmarkListItem.created`.

Do not support `published` sort in the first prototype. Published dates can be
missing, may not reflect the user's library-building history, and would add
fallback semantics before the interaction itself is proven.

The overlay follows visible list order:

- `ADDED_NEWEST`: top is newest, bottom is oldest,
- `ADDED_OLDEST`: top is oldest, bottom is newest.

## 6. Anchor Model

Build anchors from the currently visible `bookmarks` list passed to
`BookmarkListView`.

Recommended model:

```kotlin
data class BookmarkDateAnchor(
    val year: Int,
    val month: Int,
    val itemIndex: Int,
)
```

For Compact `LazyColumn`, item index is simply the bookmark index:

```kotlin
bookmarks.forEachIndexed { index, bookmark ->
    val key = bookmark.created.year to bookmark.created.monthNumber
    if (key not seen) add anchor(itemIndex = index)
}
```

The anchor list should represent the currently filtered and sorted list. If the
user filters to one label, one search, archived items, favorites, or a date range,
fast scroll should only expose dates in that filtered result set.

## 7. UI Placement

Add a Bookmark-list-specific prototype composable, or a small generic date
fast-scroller if the extraction is genuinely simple.

Prototype signature:

```kotlin
@Composable
private fun BookmarkDateFastScroller(
    anchors: List<BookmarkDateAnchor>,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
)
```

Place it in the Compact `LazyColumn` branch of `BookmarkListView`, where the
current passive `VerticalScrollbar(lazyListState = lazyListState)` is rendered.

Behavior:

- if eligible and `anchors.size > 1`, show the fast-scroll overlay,
- otherwise show the passive `VerticalScrollbar`,
- do not render both simultaneously.

## 8. Label Strategy

Use a restrained, collision-resistant label approach:

- active month/year pill, e.g. `Jul 2025`,
- year chips for available years when they fit,
- downsample year chips when too many years are present,
- preserve first, last, and current year when downsampling,
- keep labels transient and right-aligned.

Locale-aware month formatting should be used.

## 9. Interaction Details

- The drag handle touch target should be about 48 dp wide on the right edge.
- Dragging maps vertical position to nearest date anchor.
- Use `lazyListState.scrollToItem(anchor.itemIndex)` for immediate jumps.
- Skip duplicate scroll calls when the selected anchor has not changed.
- Keep normal list scrolling and pull-to-refresh gestures outside the handle.
- Ensure card swipe gestures remain unaffected.

## 10. Sort And Layout Eligibility

Recommended helper:

```kotlin
private fun shouldUseBookmarkDateFastScroll(
    layoutMode: LayoutMode,
    isMultiColumn: Boolean,
    sortOption: SortOption,
): Boolean
```

Returns true only when:

- `layoutMode == LayoutMode.COMPACT`,
- `isMultiColumn == false`,
- `sortOption == ADDED_NEWEST || sortOption == ADDED_OLDEST`.

This means the prototype appears only in the single-column Compact path. Tablet
or wide layouts remain unchanged.

## 11. Accessibility

Add a content description such as:

- `bookmark_fast_scroll`: `Fast scroll bookmarks`

Expose the current month/year label through visible text and/or state
description. Do not rely on color alone.

Add the string as an English placeholder to all language `strings.xml` files.

## 12. User Guide

If the prototype ships to testers, update
`app/src/main/assets/guide/en/your-bookmarks.md` to note:

- Compact layout can show date fast-scroll controls while scrolling,
- the control appears for Added newest/oldest sorting,
- dragging jumps by month/year within the current filtered bookmark list.

If the prototype is hidden behind local-only/testing conditions, defer guide
updates until it becomes user-facing.

## 13. Tests

Pure unit tests should cover:

- anchors generated by first visible month,
- anchor item indices equal bookmark indices,
- newest-first and oldest-first lists preserve visible order,
- filtered lists produce anchors only from visible bookmarks,
- same-month bookmarks share the first month anchor,
- empty or single-month lists produce no fast-scroll requirement.

UI/manual verification should cover:

- Compact + Added newest shows overlay while scrolling,
- Compact + Added oldest shows overlay while scrolling,
- Grid/Mosaic layouts do not show the overlay,
- non-date sorts do not show the overlay,
- dragging jumps to expected month/year,
- search/filter/label views constrain available dates,
- swipe actions still work,
- pull-to-refresh still works,
- small phone viewport readability,
- light and dark theme readability.

## 14. Implementation Plan

1. Add `BookmarkDateAnchor` and a pure anchor builder.
2. Add unit tests for anchor generation.
3. Add eligibility helper for Compact + Added sort modes.
4. Add the Compact-only `BookmarkDateFastScroller` overlay.
5. Replace the passive scrollbar only when eligible and there is more than one
   month anchor.
6. Add strings and optional guide documentation.
7. Run the standard serial verification tasks.

## 15. Verification

Run Gradle tasks serially:

```bash
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Because this touches UI and strings, include lint.
