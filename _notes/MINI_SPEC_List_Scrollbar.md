# Mini Spec: List View Scrollbar

## Overview

Add a visual scrollbar indicator to the right side of all scrollable list views,
giving users a cue for their current position within long lists.

## Approach

Extend the existing `VerticalScrollbar` composable (`ui/components/VerticalScrollbar.kt`)
with a new overload that accepts `LazyListState` (used by `LazyColumn`). The existing
`ScrollState` overload already covers `Column + verticalScroll` views.

### Why extend vs. replace

The existing component already provides auto-hide, fade animation, and themed styling.
Only the scroll-fraction calculation differs between `ScrollState` and `LazyListState`.
Extracting the shared rendering into an internal composable and adding a thin adapter
for each state type keeps the code DRY.

## Design

### Scroll fraction calculation for LazyListState

```
visibleItems = layoutInfo.visibleItemsInfo
totalItems   = layoutInfo.totalItemsCount

scrollFraction = firstVisibleItemIndex / max(1, totalItems - visibleItems.size)
```

This is approximate for variable-height items but provides a good-enough positional
indicator for typical list content.

### Behavior

- **Auto-hide**: Scrollbar appears when scrolling starts, fades out 1 second after
  scrolling stops (matches existing behavior).
- **Thumb size**: Fixed at 10% of track height (matches existing reader-mode scrollbar).
- **Appearance**: 4dp wide, rounded, `onSurface` at 50% alpha.
- **Position**: Overlaid on the right edge (`Alignment.CenterEnd`) of the list container.

## Integration Points

| Screen               | Component           | State type      | Change needed                         |
|----------------------|---------------------|-----------------|---------------------------------------|
| Bookmark list        | `BookmarkListView`  | `LazyListState` | Wrap in `Box`, overlay scrollbar      |
| Labels list          | `LabelsListView`    | `LazyListState` | Wrap in `Box`, overlay scrollbar      |
| Navigation drawer    | Drawer `Column`     | `ScrollState`   | Wrap in `Box`, overlay scrollbar      |
| Log viewer           | Log `Column`        | `ScrollState`   | Wrap in `Box`, overlay scrollbar      |
| Account settings     | Settings form       | `LazyListState` | Short list — skip (rarely scrollable) |

## Files Modified

- `app/src/main/java/com/mydeck/app/ui/components/VerticalScrollbar.kt` — add `LazyListState` overload
- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt` — integrate into `BookmarkListView` and `LabelsListView`
- `app/src/main/java/com/mydeck/app/ui/settings/LogViewScreen.kt` — integrate into log viewer

## Out of Scope

- Draggable / interactive scrollbar (thumb is indicator-only)
- Account settings screen (too short to benefit)
- WebView content (has its own scroll handling)
