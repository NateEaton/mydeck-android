# Highlights Date Fast Scroll Spec

**Status:** Draft for discussion
**Date:** 2026-05-18
**Build Context:** Highlights local-first search/filter/sort is already implemented
**Related:**
- `docs/specs/highlights-search-filter-fast-scroll-spec.md`
- `docs/archive/highlights-list-local-first-scalability-amendment.md`
- `docs/archive/highlights-list-reactivity-mini-spec.md`

## 1. Summary

The Highlights screen now supports local-first browsing, search, filters, and
sort. The remaining long-list navigation gap is fast scrolling by date.

This spec adds a Google Photos-inspired fast-scroll overlay for the global
Highlights list:

- show a transient fast-scroll affordance while the user scrolls,
- expose the years and month/year positions represented by the currently visible
  highlight result set,
- allow the user to drag the affordance to jump through the list by date,
- keep all behavior local to the currently rendered `filteredGroups`,
- preserve the existing search, filter, sort, sync, and reader navigation
  behavior.

This is not a revival of the abandoned search/filter implementation plan. Treat
that older spec as historical context only.

## 2. Current Baseline

The current Highlights screen already has:

- cached local highlight observation via Room/repository flows,
- local search over highlight text, notes, bookmark title, and site name,
- filters for search target, highlight color, and note presence,
- newest-first and oldest-first sort toggle,
- date-aware grouping where groups are keyed by bookmark and highlight local
  date,
- stable highlight navigation to a bookmark and optional annotation ID,
- a passive `VerticalScrollbar` overlay.

The list model is still grouped by bookmark, but only within a date:

```kotlin
data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val groupDate: LocalDate,
    val highlights: List<HighlightSummary>,
)
```

The rendered order is effectively:

1. date according to the active sort order,
2. bookmark/article groups within that date,
3. highlights inside each group.

The fast-scroll design should build on this shape instead of reworking it.

## 3. Goals

- Make thousands of highlights navigable without repeated flick scrolling.
- Let users understand the temporal range of their current highlight list.
- Support both newest-first and oldest-first sort order.
- Support active search/filter results without jumping to hidden highlights.
- Preserve grouping by bookmark/date.
- Avoid network requests or highlight reconciliation when fast scrolling.
- Keep the default browsing UI quiet; only show the heavy date controls while
  scrolling or dragging.
- Keep the implementation scoped to Highlights unless a reusable primitive falls
  out naturally.

## 4. Non-Goals

- Do not change search, filter, or sort semantics.
- Do not reintroduce the abandoned all-in-one search/filter/scroll approach.
- Do not add server-side search or date queries.
- Do not add Paging 3 as part of this feature.
- Do not change reader deep-link behavior.
- Do not add date section headers to the main list unless separately approved.
- Do not make the shared passive `VerticalScrollbar` interactive for every list.
- Do not attempt to match Google Photos pixel-for-pixel.

## 5. User Experience

### Default State

When the list is idle, the screen remains visually close to today:

- no permanent right-edge date rail,
- no persistent month/year labels,
- existing top bar, search/filter controls, sort toggle, and list content remain
  unchanged.

The existing passive scrollbar may either remain for non-drag scroll feedback or
be replaced on this screen by the new overlay when it is visible. Avoid showing
two competing right-edge scroll indicators at the same time.

### While Scrolling

When the user scrolls the Highlights list:

- fade in a right-edge fast-scroll control,
- show date labels for the available years and the active month/year,
- keep labels readable over the dark and light themes,
- fade the overlay out shortly after scrolling stops.

The overlay should not cover the main highlight text more than necessary. The
right edge is acceptable because the current cards already leave tappable content
across the full row, but the rail should stay narrow and transient.

### While Dragging

When the user presses or drags the fast-scroll control:

- keep the overlay visible,
- map the vertical drag position to the nearest date anchor,
- jump the `LazyColumn` to that anchor,
- show a prominent floating label for the selected month/year,
- optionally show a year-only stack for surrounding years.

Dragging should feel like scrubbing through a time index, not like dragging the
existing passive scrollbar thumb.

### Search And Filter Mode

When search/filter controls are active, the fast-scroll overlay should use only
`uiState.filteredGroups`.

Example: if a search narrows 1,803 highlights to 18 matches in 2024 and 2026,
the fast-scroll labels should expose only 2024 and 2026 anchors. It should not
jump into hidden months from the unfiltered list.

### Sort Order

The overlay follows the visible list order:

- newest-first: top is newest, bottom is oldest,
- oldest-first: top is oldest, bottom is newest.

The date labels should not imply the opposite direction. If needed, order the
year chips in the same direction as the list.

## 6. Date Anchors

Build a lightweight date-index model from `uiState.filteredGroups`.

Recommended model:

```kotlin
data class HighlightDateAnchor(
    val year: Int,
    val month: Int,
    val label: String,
    val itemIndex: Int,
)
```

The anchor list should contain the first rendered item index for each month in
the current filtered and sorted list. A companion year list can be derived from
the month anchors.

Index calculation can happen in the UI layer because it depends on how
`LazyColumn` items are emitted:

- each highlight renders as one item,
- each bookmark/date group title renders as one item,
- the group title key is currently emitted after that group's highlights.

For each group:

1. if this group starts a month not yet seen, record the current item index,
2. add `group.highlights.size`,
3. add `1` for the bookmark title row.

If the row structure changes later, this index builder must be updated with the
list emission code.

## 7. Overlay Layout

Recommended composable:

```kotlin
@Composable
private fun HighlightsDateFastScroller(
    anchors: List<HighlightDateAnchor>,
    lazyListState: LazyListState,
    sortOrder: HighlightSortOrder,
    modifier: Modifier = Modifier,
)
```

Responsibilities:

- observe `lazyListState.isScrollInProgress`,
- derive the currently active month from the first visible list item,
- fade in while scrolling or dragging,
- render a narrow right-edge drag handle,
- render compact date labels,
- launch `lazyListState.scrollToItem(anchor.itemIndex)` while dragging.

Use a `Box` overlay in `HighlightsScreen` around the `LazyColumn`, replacing or
coordinating with the current `VerticalScrollbar` on this screen.

## 8. Label Strategy

The screenshot inspiration shows many year labels and a current month label.
For Highlights, use a responsive, collision-resistant version:

- always show the current month/year floating pill while the overlay is visible,
- show year chips for all years when they fit,
- if years do not fit, downsample year chips while preserving first, last, and
  current year,
- avoid showing every month label at once for long histories,
- during drag, show the selected month/year more prominently than the passive
  scroll state.

Possible labels:

- year chip: `2026`
- month chip: `Jul 2025`
- active drag pill: `Jul 2025`

Use locale-aware month formatting.

## 9. Interaction Details

- The drag target should be at least 48 dp wide/tall where practical.
- The visual handle can be smaller than the touch target.
- Use vertical drag gestures only.
- Clamp drag position to the overlay height.
- Use nearest anchor by normalized vertical position.
- Debounce or skip duplicate `scrollToItem` calls when the anchor has not
  changed.
- Keep pull-to-refresh behavior intact; the fast-scroll handle lives on the right
  edge and should not consume normal vertical drags outside its touch target.
- Keep highlight cards tappable.

## 10. Accessibility

At minimum:

- provide a content description for the fast-scroll handle,
- expose the current month/year label as state or text when visible,
- ensure the touch target is large enough,
- do not rely on color alone.

Open question: whether TalkBack users need explicit previous/next year actions
on the control. This can be deferred if the first implementation remains a visual
and touch affordance layered on top of the normal scrollable list.

## 11. Strings

Likely new strings:

- `highlights_fast_scroll`: `Fast scroll highlights`
- `highlights_fast_scroll_to_date`: `Jump to %1$s`

Add English placeholders to all language `strings.xml` files if these strings
are added.

If the implementation can satisfy accessibility with existing visible text and a
generic content description, keep the string set smaller.

## 12. User Guide

Update `app/src/main/assets/guide/en/highlights.md` to document:

- long highlight lists show date fast-scroll controls while scrolling,
- dragging the control jumps by month/year,
- search and filter narrow the dates available in fast scroll,
- sort order controls whether newer or older dates are at the top.

## 13. Logging

Add concise debug logs only if useful during implementation:

```text
Highlights fast scroll anchors built: months=... years=... highlights=...
Highlights fast scroll drag: label=... itemIndex=...
```

Do not log highlight text, query text, notes, or bookmark titles.

## 14. Tests

Unit tests should cover date-index derivation if it is extracted into a pure
function:

- anchors are generated by month from filtered groups,
- anchor item indices account for highlight rows plus title rows,
- newest-first anchors follow descending list order,
- oldest-first anchors follow ascending list order,
- filtered results produce anchors only for visible groups,
- multiple bookmark groups in the same month share the first month anchor,
- empty result sets produce no anchors.

UI/manual verification should cover:

- overlay appears while normal scrolling,
- overlay fades after scroll stops,
- dragging jumps to expected month/year,
- active label updates during drag,
- search/filter mode only exposes matching dates,
- newest-first and oldest-first both map top/bottom correctly,
- pull-to-refresh still works,
- card taps and title taps still work,
- dark and light theme readability,
- small phone viewport similar to the provided screenshots.

## 15. Implementation Plan

1. Add a pure date-anchor builder for the current `filteredGroups` list.
2. Add focused unit tests for anchor generation and item indices.
3. Add `HighlightsDateFastScroller` in the Highlights UI package.
4. Overlay it in `HighlightsScreen` and coordinate it with the existing passive
   scrollbar.
5. Add strings and English guide updates.
6. Polish drag behavior and label collision handling after device/screenshot
   review.

## 16. Verification

Run Gradle tasks serially:

```bash
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Because this touches UI, resources, and user-visible documentation, include lint.
