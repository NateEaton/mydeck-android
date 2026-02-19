# Phase 5 v2: Tablet / Adaptive Layout — Revised Implementation Plan

## Context

Phase 5 v1 implemented M3 adaptive layouts using `ListDetailPaneScaffold` for expanded devices. After evaluation against the Readeck web UI reference and testing on emulators, the list-detail side-by-side pattern doesn't match the desired UX. This v2 plan replaces the architecture with a simpler approach: the navigation component varies by size class, but **reading is always full-screen**.

**Reference screenshots** are in `/screenshots/` — both MyDeck emulator captures and Readeck web UI captures for comparison.

---

## Design Principles

1. **Reading is always full-screen** — no rail, no drawer visible during reading on any device
2. **Navigation component varies by size** — hamburger drawer (compact), persistent rail (medium), persistent drawer (expanded) — for the **list view only**
3. **Multi-column card grid** on wider screens (medium and expanded), adaptive column count
4. **Settings/About shown inline** with navigation visible — no back button when rail/drawer is present; user navigates away via rail/drawer items
5. **Mobile portrait is unchanged** — zero changes to the compact layout

---

## Revised Layout Matrix

| Layout | Detection | List View Nav | Reading View | List Cards | Settings/About |
|---|---|---|---|---|---|
| **Compact** | W < 600dp | ModalNavigationDrawer (hamburger) | Full screen, back button | Single column | Full screen, back button |
| **Medium** | W ≥ 600dp, not Expanded | NavigationRail (persistent) | Full screen, no rail, back button | Multi-column adaptive | Inline next to rail, no back button |
| **Expanded** | W ≥ 840dp AND landscape AND H ≥ 480dp | PermanentNavigationDrawer | Full screen, no drawer, back button | Multi-column adaptive | Inline next to drawer, no back button |

### Breakpoint Detection Logic

Standard M3 `WindowWidthSizeClass` alone is insufficient — a phone in landscape (~914dp) hits EXPANDED, but should be MEDIUM. The fix uses width class + orientation + height:

```kotlin
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
val configuration = LocalConfiguration.current
val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
val isTabletHeight = windowSizeClass.windowHeightSizeClass != WindowHeightSizeClass.COMPACT

val layoutTier = when {
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT -> COMPACT
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
        && isLandscape && isTabletHeight -> EXPANDED
    else -> MEDIUM
}
```

**Verification across devices:**

| Device + Orientation | Width | Height | Width Class | Landscape? | Tablet Height? | → Layout |
|---|---|---|---|---|---|---|
| Phone portrait | ~412dp | ~900dp | COMPACT | No | — | **Compact** ✓ |
| Phone landscape | ~914dp | ~412dp | EXPANDED | Yes | No (COMPACT) | **Medium** ✓ |
| 10" tablet portrait | ~800dp | ~1280dp | MEDIUM | No | — | **Medium** ✓ |
| 10" tablet landscape | ~1280dp | ~800dp | EXPANDED | Yes | Yes (MEDIUM) | **Expanded** ✓ |
| 12" tablet portrait | ~960dp | ~1280dp | EXPANDED | No | — | **Medium** ✓ |
| 12" tablet landscape | ~1280dp | ~960dp | EXPANDED | Yes | Yes (EXPANDED) | **Expanded** ✓ |

---

## What's Salvageable from v1

### Keep (significant completed work, all reusable)
- **CompactAppShell** — completely unchanged, zero modifications needed
- **AppNavigationRailContent** — rail component with correct icons and selection state
- **AppDrawerContent** `usePermanentSheet` parameter — permanent drawer sheet variant
- **BookmarkDetailHost** extraction — reusable detail composable wrapper
- **BookmarkDetailViewModel** `loadBookmark()` refactor — harmless, useful for future
- **Card adaptations** — `isWideLayout` wide-screen card variants (grid image-above, compact actions-beside-title)
- **`showNavigationIcon`** parameter on `BookmarkListScreen`
- **Dependency updates** — material3-adaptive split artifacts
- **Localized strings** — `select_bookmark` string (can repurpose or remove)
- **`LocalReaderMaxWidth`** CompositionLocal and `Dimens` reader width constants
- **`LocalIsWideLayout`** CompositionLocal for card variant switching

### Remove
- **`ListDetailLayout.kt`** — `ExpandedListDetailLayout` composable (wrong pattern)
- **`BookmarkDetailPaneHost.kt`** — no longer needed (detail always via NavHost)
- **`selectedBookmarkId` / `selectedShowOriginal`** hoisted state in `AppShell`
- **Complex navigation event interception** in `ExpandedAppShell` (bookmark detail → pane state)
- **`adaptive-layout`** and **`adaptive-navigation`** dependencies (only needed for `ListDetailPaneScaffold`)

### Modify
- **`AppShell`** — new breakpoint logic, simplified expanded shell, route-aware nav visibility
- **`MediumAppShell`** — route-aware rail visibility (hide during reading)
- **`ExpandedAppShell`** — complete rewrite to simple Row(Drawer, NavHost) with push nav
- **`BookmarkListView`** — `LazyColumn` → conditional `LazyVerticalGrid` for multi-column
- **`SettingsScreen`** / **`AboutScreen`** — conditional back button parameter

---

## Implementation Phases

### Phase A: Architecture Cleanup & Breakpoint Fix
**Model: Opus** — Navigation restructuring with subtle route-aware visibility and breakpoint logic

**Files modified:** `AppShell.kt`, `ListDetailLayout.kt` (delete), `BookmarkDetailPaneHost.kt` (delete), `libs.versions.toml`, `build.gradle.kts`

**Steps:**

1. **Add height-based breakpoint detection** in `AppShell`
   - Import `WindowHeightSizeClass` and `LocalConfiguration`
   - Implement `layoutTier` logic as specified above
   - Replace the `when (windowSizeClass.windowWidthSizeClass)` branching with `when (layoutTier)`
   - Remove `selectedBookmarkId` and `selectedShowOriginal` hoisted state

2. **Add route-aware navigation visibility helper**
   - Determine current route from `navController.currentBackStackEntryAsState()`
   - Define `hideNavigation` = true when on `BookmarkDetailRoute` or `WelcomeRoute`
   - Pass this flag to medium/expanded shell composables

3. **Rewrite `MediumAppShell`**
   - Wrap `AppNavigationRailContent` in `AnimatedVisibility(visible = !hideNavigation)`
   - Rail hides during reading and welcome; content takes full width
   - Keep standard NavHost push navigation (already correct)

4. **Rewrite `ExpandedAppShell`**
   - Remove `PermanentNavigationDrawer` wrapper
   - Replace with `Row { AnimatedVisibility { PermanentDrawerSheet { ... } }; Surface { NavHost } }`
   - Use standard NavHost push navigation for `BookmarkDetailRoute` (same as CompactAppShell)
   - Standard navigation events — `NavigateToBookmarkDetail` pushes to NavHost, no pane state
   - Drawer hides during reading and welcome; content takes full width

5. **Delete removed files**
   - Delete `ListDetailLayout.kt`
   - Delete `BookmarkDetailPaneHost.kt`

6. **Clean up dependencies**
   - Remove `adaptive-layout` and `adaptive-navigation` from `libs.versions.toml` and `build.gradle.kts`
   - Keep `adaptive` (provides `currentWindowAdaptiveInfo()`)

**Verification:**
- Phone portrait: identical to current (CompactAppShell unchanged)
- Phone landscape: rail visible on list, hidden during reading
- Tablet portrait: rail visible on list, hidden during reading
- Tablet landscape: permanent drawer visible on list, hidden during reading
- All layouts: bookmark detail is full-screen push navigation with back button

---

### Phase B: Navigation & Settings Fixes
**Model: Sonnet** — Well-defined behavior fixes with clear specs

**Files modified:** `AppShell.kt` (medium/expanded click handlers), `SettingsScreen.kt`, `AboutScreen.kt`

**Steps:**

1. **Fix drawer/rail click handlers for medium/expanded**
   - When a list-related item is clicked (My List, Archive, Favorites, Articles, Videos, Pictures, Labels), navigate back to `BookmarkListRoute` if not already there
   - Pattern:
     ```kotlin
     onClickMyList = {
         navController.navigate(BookmarkListRoute()) {
             popUpTo(BookmarkListRoute()) { inclusive = true }
             launchSingleTop = true
         }
         bookmarkListViewModel.onClickMyList()
     }
     ```
   - Apply this pattern to all list-filter click handlers in both MediumAppShell and ExpandedAppShell

2. **Fix Settings/About navigation stacking**
   - Add `launchSingleTop = true` to Settings and About navigation in all three shells
   - In the navigation event handler:
     ```kotlin
     is NavigateToSettings -> navController.navigate(SettingsRoute) { launchSingleTop = true }
     is NavigateToAbout -> navController.navigate(AboutRoute) { launchSingleTop = true }
     ```

3. **Conditional back button on Settings and About screens**
   - Add `showBackButton: Boolean = true` parameter to `SettingsScreen` and `AboutScreen`
   - When `showBackButton` is false, hide the back arrow `IconButton` in `TopAppBar`
   - In medium/expanded shell composables, pass `showBackButton = false`:
     ```kotlin
     composable<SettingsRoute> { SettingsScreen(navController, showBackButton = false) }
     composable<AboutRoute> { AboutScreen(navHostController = navController, showBackButton = false) }
     ```
   - Sub-settings screens (Account, Sync, UI, Logs) keep their back buttons always — they navigate within the settings flow

4. **Verify Settings/About route-awareness**
   - Settings, About, and all sub-settings routes should keep rail/drawer visible (only `BookmarkDetailRoute` and `WelcomeRoute` hide navigation)

**Verification:**
- On tablet landscape: Click Settings in drawer → Settings shows next to drawer. Click My List → returns to list. Click Settings twice → only one instance (no stacking).
- On phone landscape: Same behavior with rail instead of drawer.
- Sub-settings (Account, Sync, etc.) retain back buttons on all layouts.

---

### Phase C: Multi-Column Grid
**Model: Sonnet** — UI-focused work with clear per-mode specs

**Files modified:** `BookmarkListScreen.kt` (BookmarkListView), `BookmarkCard.kt` (minor adjustments)

**Steps:**

1. **Replace `LazyColumn` with conditional `LazyVerticalGrid`**
   - Add `isMultiColumn: Boolean` parameter to `BookmarkListView` (sourced from `LocalIsWideLayout`)
   - When `isMultiColumn` is true, use `LazyVerticalGrid` with mode-specific column sizing:
     - **GRID**: `GridCells.Adaptive(minSize = 250.dp)` — yields 2–4 columns
     - **COMPACT**: `GridCells.Adaptive(minSize = 350.dp)` — yields 1–2 columns
     - **MOSAIC**: `GridCells.Adaptive(minSize = 180.dp)` — yields 3–5 columns
   - When `isMultiColumn` is false (compact layout): keep existing `LazyColumn`

2. **Grid item spacing**
   - Add `horizontalArrangement = Arrangement.spacedBy(8.dp)` and `verticalArrangement = Arrangement.spacedBy(8.dp)` to the grid
   - Mosaic mode: reduce to `4.dp` spacing for tighter image grid (matching Readeck)

3. **Card fill behavior**
   - Ensure cards use `Modifier.fillMaxWidth()` within their grid cell
   - Grid cards: image-above-text variant already works well for grid cells
   - Compact cards: thumbnail + text in row, works in wider cells
   - Mosaic cards: image fills cell, title/icon overlay — existing format unchanged

4. **Scrollbar compatibility**
   - Update `VerticalScrollbar` to accept `LazyGridState` (or create a grid-compatible variant)
   - `rememberLazyGridState()` replaces `rememberLazyListState()` when in grid mode
   - `scrollToTopTrigger` and `filterKey` logic must work with grid state

5. **Pull-to-refresh and FAB**
   - Verify `PullToRefreshBox` works with grid content
   - FAB positioning should remain correct over the grid

**Verification:**
- Phone portrait: single-column list (unchanged)
- Phone landscape: multi-column grid cards (2 columns for GRID mode)
- Tablet portrait: multi-column grid (2–3 columns)
- Tablet landscape: multi-column grid (3–4 columns)
- All three layout modes (grid, compact, mosaic) render correctly in multi-column
- Scrollbar, pull-to-refresh, and FAB all function

---

### Phase D: Polish & Cleanup
**Model: Sonnet** — Mechanical cleanup and animation work

**Files modified:** Various

**Steps:**

1. **Animation polish**
   - `AnimatedVisibility` for rail/drawer transitions: use `expandHorizontally`/`shrinkHorizontally` with appropriate easing
   - Content area should smoothly expand when rail/drawer hides (reading mode entry)
   - Content should smoothly contract when rail/drawer appears (reading mode exit)

2. **Reader width constraints verification**
   - Verify `LocalReaderMaxWidth` still works correctly on all layouts
   - Compact: `Dp.Unspecified` (no constraint)
   - Medium: `Dimens.ReaderMaxWidthMedium` (720.dp)
   - Expanded: `Dimens.ReaderMaxWidthExpanded` (840.dp)
   - Since reading is always full-screen, the reader width constraint centers content within the full screen width

3. **Edge cases**
   - Configuration change (rotation): verify state preservation across layout tier changes
   - Deep links to `BookmarkDetailRoute`: should work on all sizes (full-screen reading)
   - System back: correct behavior on all form factors (pops reading view, returns to list with nav visible)
   - Welcome screen: no rail/drawer visible

4. **Dead code removal**
   - Remove any remaining references to deleted files (`ListDetailLayout`, `BookmarkDetailPaneHost`)
   - Remove `adaptive-layout` and `adaptive-navigation` imports if any linger
   - Remove `select_bookmark` string if no longer used (or keep for future)

5. **Update plan documentation**
   - Archive Phase-5-Tablet-Adaptive-Layout-Plan.md (v1) or add deprecation note
   - Ensure CLAUDE.md or project docs reference the correct plan

---

## Files Summary

### Modified Files
| File | Change |
|---|---|
| `AppShell.kt` | Breakpoint logic, simplified expanded/medium shells, route-aware nav visibility |
| `BookmarkListScreen.kt` | `BookmarkListView` multi-column grid support |
| `SettingsScreen.kt` | `showBackButton` parameter |
| `AboutScreen.kt` | `showBackButton` parameter |
| `libs.versions.toml` | Remove adaptive-layout, adaptive-navigation |
| `build.gradle.kts` | Remove adaptive-layout, adaptive-navigation dependencies |

### Deleted Files
| File | Reason |
|---|---|
| `ui/shell/ListDetailLayout.kt` | ListDetailPaneScaffold no longer used |
| `ui/shell/BookmarkDetailPaneHost.kt` | Pane-aware detail wrapper no longer needed |

### Unchanged Files (from v1, kept as-is)
| File | What's kept |
|---|---|
| `AppNavigationRailContent.kt` | Rail component |
| `AppDrawerContent.kt` | `usePermanentSheet` parameter |
| `BookmarkDetailScreen.kt` | `BookmarkDetailHost` extraction |
| `BookmarkDetailViewModel.kt` | Reactive `_bookmarkId`, `loadBookmark()` |
| `BookmarkCard.kt` | `isWideLayout` card variants, `LocalIsWideLayout` |
| `Dimens.kt` | Reader width constants, `LocalReaderMaxWidth` |

---

## Verification Checklist

1. **Phone portrait**: All behavior identical to current (hamburger drawer, single column, full-screen push detail)
2. **Phone landscape**: Rail visible on list, hides during reading. Multi-column cards. Full-screen reading.
3. **Tablet portrait**: Rail visible on list, hides during reading. Multi-column cards. Full-screen reading.
4. **Tablet landscape**: Permanent drawer visible on list, hides during reading. Multi-column cards. Full-screen reading.
5. **Settings/About on medium/expanded**: Shown inline next to rail/drawer, no back button. Clicking other nav items returns to list.
6. **Settings stacking**: Clicking Settings multiple times doesn't stack instances.
7. **Category change**: Changing category via drawer/rail pops any non-list route first.
8. **Deep links**: BookmarkDetail deep links work on all size classes (full-screen reading).
9. **System back**: Correct on all form factors.
10. **Welcome screen**: No rail/drawer visible on any size class.
11. **Card adaptations**: Wide-layout card variants render in grid cells correctly.
12. **Mosaic multi-column**: Tight grid of image cards with title/icon overlay.

---

## Future Work (unchanged from v1)

- **Filter form as persistent pane:** On expanded, the filter bottom sheet could instead be a right-side pane that persists until closed, with cards reflowing to fill remaining width (see Readeck filter pane reference screenshot)
- **Bookmark detail as reading-view side pane:** On expanded landscape, the Bookmark Detail info could show as a persistent side pane to the right of reading content instead of a dialog
