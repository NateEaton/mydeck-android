# Phase 5 v2: Implementation Prompts

Each prompt below is designed to be used as a standalone instruction for the recommended model. They should be executed sequentially — each phase builds on the previous. After each phase, verify with `./gradlew assembleDebug` and test on emulators (phone portrait/landscape, tablet portrait/landscape).

**Reference document:** `docs/Phase-5-Tablet-Adaptive-Layout-Plan-v2.md`

---

## Phase A: Architecture Cleanup & Breakpoint Fix

**Model: Opus**
**Estimated effort: 2–3 hours**
**Commit message: `refactor: replace ListDetailPaneScaffold with full-screen reading, fix breakpoints`**

### Prompt

```
I need to restructure the tablet adaptive layout in my Android Compose app. The current implementation uses ListDetailPaneScaffold for expanded devices (showing list and reading pane side-by-side), but the desired behavior is that reading is ALWAYS full-screen on every device size. The navigation component (drawer, rail, or permanent drawer) should only be visible on the list view, not during reading.

Please make the following changes:

### 1. Fix breakpoint detection in AppShell.kt

The current code at line 85 uses `windowSizeClass.windowWidthSizeClass` to branch between COMPACT, MEDIUM, and EXPANDED. The problem is that a phone in landscape (~914dp width) hits EXPANDED, but should be MEDIUM (rail layout). 

Replace the current branching with a layout tier that uses width + orientation + height:

```kotlin
val configuration = LocalConfiguration.current
val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
val isTabletHeight = windowSizeClass.windowHeightSizeClass != WindowHeightSizeClass.COMPACT

val layoutTier = when {
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT -> "compact"
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED 
        && isLandscape && isTabletHeight -> "expanded"
    else -> "medium"
}
```

This ensures:
- Phone portrait → Compact (hamburger drawer)
- Phone landscape → Medium (rail) — NOT expanded
- Tablet portrait (any size) → Medium (rail)
- Tablet landscape → Expanded (permanent drawer)

You'll need to add imports for `WindowHeightSizeClass` and `LocalConfiguration`.

### 2. Remove hoisted pane state from AppShell

Remove these lines from the AppShell composable (around lines 82-83):
```kotlin
val selectedBookmarkId: MutableState<String?> = remember { mutableStateOf(null) }
val selectedShowOriginal: MutableState<Boolean> = remember { mutableStateOf(false) }
```

And remove `selectedBookmarkId` and `selectedShowOriginal` from the ExpandedAppShell call.

### 3. Add route-aware navigation visibility

In AppShell, after the navController is available, determine whether navigation should be hidden:

```kotlin
val currentBackStackEntry = navController.currentBackStackEntryAsState().value
val currentRoute = currentBackStackEntry?.destination?.route
val hideNavigation = currentRoute?.let {
    it.startsWith(BookmarkDetailRoute::class.qualifiedName ?: "") ||
    it.startsWith(WelcomeRoute::class.qualifiedName ?: "")
} ?: true
```

Pass `hideNavigation` to MediumAppShell and ExpandedAppShell as a parameter.

### 4. Rewrite MediumAppShell

Current: `Row { AppNavigationRailContent(...); Surface { NavHost(...) } }`

New: Wrap the rail in `AnimatedVisibility(visible = !hideNavigation)` so it hides during reading and on the welcome screen. The NavHost Surface should use `Modifier.weight(1f)` so it expands to fill the width when the rail is hidden.

```kotlin
Row(modifier = Modifier.fillMaxSize()) {
    AnimatedVisibility(visible = !hideNavigation) {
        AppNavigationRailContent(...)
    }
    Surface(
        modifier = Modifier.weight(1f).fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(...)
    }
}
```

The navigation event handler and NavHost routes stay the same as current MediumAppShell — standard push navigation for everything including BookmarkDetailRoute.

### 5. Rewrite ExpandedAppShell

This is the biggest change. Currently it uses `PermanentNavigationDrawer` wrapping the NavHost, with `ListDetailPaneScaffold` inside the BookmarkListRoute.

Replace with a simple Row layout, similar to MediumAppShell but with a drawer sheet instead of a rail:

```kotlin
@Composable
private fun ExpandedAppShell(
    navController: NavHostController,
    settingsDataStore: SettingsDataStore?,
    bookmarkListViewModel: BookmarkListViewModel,
    drawerPreset: DrawerPreset,
    activeLabel: String?,
    bookmarkCounts: BookmarkCounts,
    labelsWithCounts: Map<String, Int>,
    isOnline: Boolean,
    hideNavigation: Boolean,
) {
    // Standard navigation events — same pattern as CompactAppShell
    LaunchedEffect(Unit) {
        bookmarkListViewModel.navigationEvent.collectLatest { event ->
            when (event) {
                is BookmarkListViewModel.NavigationEvent.NavigateToSettings -> {
                    navController.navigate(SettingsRoute)
                }
                is BookmarkListViewModel.NavigationEvent.NavigateToAbout -> {
                    navController.navigate(AboutRoute)
                }
                is BookmarkListViewModel.NavigationEvent.NavigateToBookmarkDetail -> {
                    navController.navigate(
                        BookmarkDetailRoute(event.bookmarkId, event.showOriginal)
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalIsWideLayout provides true) {
        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !hideNavigation) {
                PermanentDrawerSheet {
                    DrawerColumnContent(
                        // ... same parameters as current ExpandedAppShell drawer content
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                // Same NavHost as CompactAppShell — standard push navigation
                NavHost(...) {
                    composable<BookmarkListRoute> {
                        BookmarkListScreen(
                            navController,
                            bookmarkListViewModel,
                            drawerState = rememberDrawerState(DrawerValue.Closed),
                            showNavigationIcon = false,
                        )
                    }
                    // All other routes identical to CompactAppShell
                    composable<BookmarkDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<BookmarkDetailRoute>()
                        BookmarkDetailScreen(
                            navController,
                            route.bookmarkId,
                            showOriginal = route.showOriginal
                        )
                    }
                    // ... Settings, About, Welcome, sub-settings routes
                }
            }
        }
    }
}
```

Key differences from current ExpandedAppShell:
- NO PermanentNavigationDrawer wrapper — use Row with PermanentDrawerSheet directly
- NO ListDetailPaneScaffold — BookmarkListRoute renders BookmarkListScreen directly
- NO selectedBookmarkId/selectedShowOriginal pane state
- NO navigation event interception — BookmarkDetail navigates via NavHost push (same as Compact)
- AnimatedVisibility hides the drawer during reading

Note: `DrawerColumnContent` is a private composable in AppDrawerContent.kt. Since ExpandedAppShell was previously using `AppDrawerContent(usePermanentSheet = true)`, you can continue to use that. But instead of wrapping in PermanentNavigationDrawer, place the AppDrawerContent (which internally renders PermanentDrawerSheet) directly in the AnimatedVisibility within the Row.

Actually, the cleaner approach: use `AppDrawerContent(usePermanentSheet = true, ...)` inside the AnimatedVisibility. This avoids needing to access the private DrawerColumnContent.

### 6. Delete removed files

- Delete `app/src/main/java/com/mydeck/app/ui/shell/ListDetailLayout.kt`
- Delete `app/src/main/java/com/mydeck/app/ui/shell/BookmarkDetailPaneHost.kt`

### 7. Clean up dependencies

In `gradle/libs.versions.toml`:
- Remove the library entries for `androidx-material3-adaptive-layout` and `androidx-material3-adaptive-navigation`
- Keep `androidx-material3-adaptive` (provides currentWindowAdaptiveInfo())

In `app/build.gradle.kts`:
- Remove `implementation(libs.androidx.material3.adaptive.layout)` (line 146)
- Remove `implementation(libs.androidx.material3.adaptive.navigation)` (line 147)
- Keep `implementation(libs.androidx.material3.adaptive)` (line 145)

### 8. Add necessary imports

In AppShell.kt, add:
```kotlin
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.core.layout.WindowHeightSizeClass
```

Remove any imports that referenced deleted files (ListDetailPaneScaffold, etc.).

### Important constraints
- Do NOT modify CompactAppShell at all — it must remain identical
- Do NOT modify BookmarkDetailScreen, BookmarkDetailHost, or BookmarkDetailViewModel
- Do NOT modify AppNavigationRailContent or AppDrawerContent
- Do NOT modify BookmarkListScreen or BookmarkCard
- The AnimatedVisibility should use default animation (expandHorizontally/shrinkHorizontally) for now — we'll polish in Phase D
- Verify the app builds with `./gradlew assembleDebug`
```

---

## Phase B: Navigation & Settings Fixes

**Model: Sonnet**
**Estimated effort: 1–2 hours**
**Commit message: `fix: navigation stacking, Settings/About back button, drawer/rail click handlers`**

### Prompt

```
I need to fix several navigation behavior issues in my Android Compose app's tablet adaptive layout. The app has three layout tiers: Compact (hamburger drawer), Medium (navigation rail), and Expanded (permanent drawer). All are in AppShell.kt.

### Problem 1: Settings/About stacking
When the user clicks Settings (or About) multiple times from the drawer/rail, multiple instances stack on the back stack. Fix by adding `launchSingleTop = true`.

In ALL THREE shell composables (CompactAppShell, MediumAppShell, ExpandedAppShell), find the navigation event handlers and update:

```kotlin
is BookmarkListViewModel.NavigationEvent.NavigateToSettings -> {
    navController.navigate(SettingsRoute) { launchSingleTop = true }
    // keep any existing scope.launch { drawerState.close() } for CompactAppShell
}
is BookmarkListViewModel.NavigationEvent.NavigateToAbout -> {
    navController.navigate(AboutRoute) { launchSingleTop = true }
    // keep any existing scope.launch { drawerState.close() } for CompactAppShell
}
```

### Problem 2: Drawer/rail clicks don't leave Settings/About
When Settings or About is displayed and the user clicks a list-filter item (My List, Archive, Favorites, etc.) in the rail or drawer, the list filter changes but the NavHost stays on the Settings/About route.

Fix: In MediumAppShell and ExpandedAppShell ONLY (not Compact — Compact closes the drawer which is correct), update all list-filter click handlers to navigate back to BookmarkListRoute first:

```kotlin
// Helper function — define inside each shell composable or extract to a shared function
fun navigateToListAndApply(action: () -> Unit) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    if (currentRoute?.startsWith(BookmarkListRoute::class.qualifiedName ?: "") != true) {
        navController.navigate(BookmarkListRoute()) {
            popUpTo(BookmarkListRoute()) { inclusive = true }
            launchSingleTop = true
        }
    }
    action()
}
```

Apply this to ALL list-filter click handlers in MediumAppShell and ExpandedAppShell:
- onClickMyList → `navigateToListAndApply { bookmarkListViewModel.onClickMyList() }`
- onClickArchive → `navigateToListAndApply { bookmarkListViewModel.onClickArchive() }`
- onClickFavorite → `navigateToListAndApply { bookmarkListViewModel.onClickFavorite() }`
- onClickArticles → `navigateToListAndApply { bookmarkListViewModel.onClickArticles() }`
- onClickVideos → `navigateToListAndApply { bookmarkListViewModel.onClickVideos() }`
- onClickPictures → `navigateToListAndApply { bookmarkListViewModel.onClickPictures() }`
- onClickLabels → `navigateToListAndApply { bookmarkListViewModel.onOpenLabelsSheet() }`

Do NOT apply this to Settings/About click handlers — those should navigate TO Settings/About, not back to the list.

### Problem 3: Conditional back button on Settings and About screens

In medium/expanded layouts, the rail/drawer is always visible when Settings or About is shown. The user navigates away by clicking other rail/drawer items, so the back button is redundant.

**SettingsScreen.kt:**
1. Add `showBackButton: Boolean = true` parameter to `SettingsScreen`
2. Pass it through to `SettingScreenView` (also add the parameter there)
3. In `SettingScreenView`, wrap the `navigationIcon` content in an `if (showBackButton)` block:
```kotlin
navigationIcon = {
    if (showBackButton) {
        IconButton(onClick = onClickBack, modifier = Modifier.testTag(...)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, ...)
        }
    }
}
```

**AboutScreen.kt:**
1. Add `showBackButton: Boolean = true` parameter to `AboutScreen`
2. Pass it through to `AboutScreenContent` (also add the parameter there)  
3. In `AboutScreenContent`, wrap the `navigationIcon` content in an `if (showBackButton)` block

**AppShell.kt — MediumAppShell and ExpandedAppShell NavHost routes:**
Update the Settings and About composable registrations:
```kotlin
composable<SettingsRoute> { SettingsScreen(navController, showBackButton = false) }
composable<AboutRoute> { AboutScreen(navHostController = navController, showBackButton = false) }
```

CompactAppShell should remain unchanged (showBackButton defaults to true).

Sub-settings screens (AccountSettingsScreen, SyncSettingsScreen, UiSettingsScreen, LogViewScreen) should NOT be changed — they always show a back button because they navigate within the settings flow.

### Important constraints
- Do NOT modify CompactAppShell click handlers (only add launchSingleTop to its nav events)
- Do NOT modify BookmarkListScreen, BookmarkCard, or BookmarkDetailScreen
- Verify the app builds with `./gradlew assembleDebug`
```

---

## Phase C: Multi-Column Grid

**Model: Sonnet**
**Estimated effort: 2–3 hours**
**Commit message: `feat: multi-column card grid for medium/expanded layouts`**

### Prompt

```
I need to add multi-column card grid support to my Android Compose bookmark list app. Currently, `BookmarkListView` in `BookmarkListScreen.kt` uses a `LazyColumn` for all layouts. On wider screens (medium and expanded layouts), the cards should be displayed in a multi-column grid using `LazyVerticalGrid`.

### Context
- `BookmarkListView` is at line ~622 in BookmarkListScreen.kt
- It currently uses `LazyColumn` with `items(bookmarks)` to render cards
- There are three layout modes: GRID, COMPACT, MOSAIC (from the `LayoutMode` enum)
- The app provides `LocalIsWideLayout` (a CompositionLocal<Boolean>) that is `true` on medium/expanded layouts
- The existing card composables (BookmarkGridCard, BookmarkCompactCard, BookmarkMosaicCard) already have wide-layout variants via `isWideLayout` parameter

### Changes to BookmarkListView

1. Add parameter `isMultiColumn: Boolean = LocalIsWideLayout.current`

2. When `isMultiColumn` is true, replace `LazyColumn` with `LazyVerticalGrid`:

```kotlin
if (isMultiColumn) {
    val columns = when (layoutMode) {
        LayoutMode.GRID -> GridCells.Adaptive(minSize = 250.dp)
        LayoutMode.COMPACT -> GridCells.Adaptive(minSize = 350.dp)
        LayoutMode.MOSAIC -> GridCells.Adaptive(minSize = 180.dp)
    }
    val spacing = when (layoutMode) {
        LayoutMode.MOSAIC -> 4.dp
        else -> 8.dp
    }
    
    val lazyGridState = key(filterKey) { rememberLazyGridState() }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            lazyGridState.animateScrollToItem(0)
        }
    }
    
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = columns,
            state = lazyGridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = spacing),
        ) {
            items(bookmarks) { bookmark ->
                when (layoutMode) {
                    LayoutMode.GRID -> BookmarkGridCard(...)
                    LayoutMode.COMPACT -> BookmarkCompactCard(...)
                    LayoutMode.MOSAIC -> BookmarkMosaicCard(...)
                }
            }
        }
        // Scrollbar for grid — see note below
    }
} else {
    // Existing LazyColumn code — keep completely unchanged
    val lazyListState = key(filterKey) { rememberLazyListState() }
    // ... existing code ...
}
```

3. Required imports to add at the top of BookmarkListScreen.kt:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
```

### Scrollbar handling

The existing `VerticalScrollbar` composable takes a `LazyListState`. For the grid path, either:
- Create an overloaded `VerticalScrollbar` that accepts `LazyGridState`
- Or skip the scrollbar on grid layouts (the grid is usually short enough with multiple columns)

Check the VerticalScrollbar implementation — if it only uses `firstVisibleItemIndex` and `layoutInfo.totalItemsCount`, those properties exist on both LazyListState and LazyGridState, so you may be able to extract a common interface or just duplicate the scrollbar call with the grid state.

### Card fill behavior in grid cells

Ensure each card composable fills its grid cell width. The cards should already use `fillMaxWidth()` on their root Card modifier. If any don't, add it.

For MOSAIC mode specifically: the cards should fill the cell completely. The mosaic card's image should fill the cell width and maintain aspect ratio.

### Pull-to-refresh and FAB

The `BookmarkListView` is wrapped in a `PullToRefreshBox` in the parent `BookmarkListScreen`. This should continue to work with the grid layout since PullToRefreshBox wraps around the entire content.

The FAB is in the `Scaffold` above `BookmarkListView` — its positioning is independent and should not be affected.

### Important constraints
- Keep the LazyColumn path completely unchanged for compact layouts (isMultiColumn = false)
- Do NOT modify any card composables (BookmarkGridCard, BookmarkCompactCard, BookmarkMosaicCard)
- Do NOT modify AppShell.kt
- The grid should work with the existing filterKey-based state reset and scrollToTopTrigger
- Verify the app builds with `./gradlew assembleDebug`
```

---

## Phase D: Polish & Cleanup

**Model: Sonnet**
**Estimated effort: 1–2 hours**
**Commit message: `polish: animation transitions, reader width, edge case fixes`**

### Prompt

```
I need to polish the tablet adaptive layout implementation in my Android Compose app. The core architecture is complete — this phase is about animation quality, reader width constraints, and edge case cleanup.

### 1. Animation polish for rail/drawer visibility

In `AppShell.kt`, the `MediumAppShell` and `ExpandedAppShell` use `AnimatedVisibility` to show/hide the rail and drawer when entering/exiting reading mode. Improve the animation:

```kotlin
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment

// For the rail in MediumAppShell:
AnimatedVisibility(
    visible = !hideNavigation,
    enter = expandHorizontally(
        animationSpec = tween(300),
        expandFrom = Alignment.Start
    ),
    exit = shrinkHorizontally(
        animationSpec = tween(300),
        shrinkTowards = Alignment.Start
    )
) {
    AppNavigationRailContent(...)
}

// Same pattern for the drawer in ExpandedAppShell
```

The 300ms duration matches the existing NavHost transition animations in the app.

### 2. Reader width constraints verification

The app has `LocalReaderMaxWidth` CompositionLocal provided in AppShell:
- Compact: `Dp.Unspecified`
- Medium: `Dimens.ReaderMaxWidthMedium` (720.dp)
- Expanded: `Dimens.ReaderMaxWidthExpanded` (840.dp)

Since reading is now always full-screen (the rail/drawer hides), the reader content will span the full device width. The `LocalReaderMaxWidth` should constrain the text content to a readable line length. Verify that `BookmarkDetailScreen` (the inner pure composable, around line 317) consumes this value and applies it.

If it's not currently consumed, add to the inner BookmarkDetailScreen:
```kotlin
val readerMaxWidth = LocalReaderMaxWidth.current
// Apply to the content column:
Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter
) {
    Column(
        modifier = Modifier
            .then(if (readerMaxWidth != Dp.Unspecified) Modifier.widthIn(max = readerMaxWidth) else Modifier)
            .fillMaxWidth()
    ) {
        // existing content
    }
}
```

### 3. Edge case: deep links to BookmarkDetailRoute

When a deep link opens BookmarkDetailRoute directly, the behavior should be:
- On all layouts: full-screen reading with back button
- Back should return to BookmarkListRoute

Verify the current NavHost deep link handling works. The previous v1 had special deep link handling in ExpandedAppShell that redirected to the pane state — that code should already be removed (Phase A replaced it with standard composable<BookmarkDetailRoute> push navigation). Just verify it works.

### 4. Edge case: configuration change during reading

When the user is reading a bookmark and rotates the device:
- Phone portrait → phone landscape: reading stays full-screen, layout tier changes from Compact to Medium, but since reading hides the rail, the experience is seamless
- Tablet landscape → tablet portrait: layout tier changes from Expanded to Medium, reading stays full-screen

The BookmarkDetailViewModel preserves its state via SavedStateHandle, so the content should survive rotation. Verify this works and document any issues.

### 5. Remove dead code

Check for any remaining references to:
- `ExpandedListDetailLayout`
- `BookmarkDetailPaneHost`
- `ListDetailPaneScaffoldRole`
- `rememberListDetailPaneScaffoldNavigator`
- `selectedBookmarkId` / `selectedShowOriginal` (the MutableState versions in AppShell)

Remove any stale imports or references.

### 6. Update the v1 plan document

Add a note at the top of `docs/Phase-5-Tablet-Adaptive-Layout-Plan.md`:
```markdown
> **Note:** This plan (v1) has been superseded by `Phase-5-Tablet-Adaptive-Layout-Plan-v2.md`. The ListDetailPaneScaffold approach was replaced with full-screen reading across all device sizes.
```

### Important constraints
- Do NOT modify CompactAppShell
- Do NOT change the NavHost route definitions (those are finalized)
- Do NOT modify card composables
- Verify the app builds with `./gradlew assembleDebug`
```

---

## Verification Checklist (post all phases)

Test on emulators after completing all four phases:

| Test Case | Phone Portrait | Phone Landscape | Tablet Portrait | Tablet Landscape |
|---|---|---|---|---|
| List view — navigation visible | Hamburger drawer | Rail | Rail | Permanent drawer |
| List view — multi-column cards | Single column | 2+ columns | 2–3 columns | 3–4 columns |
| Reading view — full screen | ✓ No nav | ✓ No rail | ✓ No rail | ✓ No drawer |
| Reading view — back button | ✓ | ✓ | ✓ | ✓ |
| Settings — shown inline with nav | N/A (full screen) | ✓ Next to rail | ✓ Next to rail | ✓ Next to drawer |
| Settings — no back button | Has back button | No back button | No back button | No back button |
| Settings → click My List | Returns to list | Returns to list | Returns to list | Returns to list |
| Settings → click Settings again | No stacking | No stacking | No stacking | No stacking |
| Layout mode: Grid | Single column | Multi-col grid | Multi-col grid | Multi-col grid |
| Layout mode: Compact | Single column | 1–2 columns | 1–2 columns | 1–2 columns |
| Layout mode: Mosaic | Single column | Multi-col tight | Multi-col tight | Multi-col tight |
| Welcome screen | No drawer | No rail | No rail | No drawer |
| Deep link to bookmark | Full screen | Full screen | Full screen | Full screen |
| Rotation during reading | Stays in reader | Stays in reader | Stays in reader | Stays in reader |
