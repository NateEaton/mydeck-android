# Phase 5: Tablet / Adaptive Layout — Implementation Plan

> **Note:** This plan (v1) has been superseded by `Phase-5-Tablet-Adaptive-Layout-Plan-v2.md`. The ListDetailPaneScaffold approach was replaced with full-screen reading across all device sizes.

## Context

Phase 5 adds responsive layouts using M3 adaptive APIs. All prior phases (0–4, 6) are complete. The navigation architecture was lifted to `AppShell` in Phase 1, making adaptive layout feasible.

**Goal:** Compact devices (phones) stay unchanged. Medium devices (600–840dp) get a `NavigationRail`. Expanded devices (>840dp) get `PermanentNavigationDrawer` + `ListDetailPaneScaffold` for side-by-side list/detail. Bookmark cards adapt their layout for wider screens.

**M3 Breakpoints:**

| Size Class | Width | Navigation | Content |
|---|---|---|---|
| Compact | < 600dp | ModalNavigationDrawer | Single pane, push nav |
| Medium | 600–840dp | NavigationRail | Single pane, push nav |
| Expanded | > 840dp | PermanentNavigationDrawer | ListDetailPaneScaffold |

**Example devices:**
- Compact: Any phone in portrait
- Medium: Phone in landscape, Galaxy Z Fold inner display portrait, 10" tablet portrait (~800dp)
- Expanded: 10" tablet landscape (~1200dp), 12" tablet portrait (~960dp)

---

## Implementation Phases & Model Recommendations

This work is split into 5 phases (A–E), executed sequentially. Each phase should be a separate commit.

| Phase | Steps | Model | Rationale |
|---|---|---|---|
| **A: Foundation** | 1, 2, 3 | **Opus** | ViewModel refactor has subtle reactive-flow and SavedStateHandle interactions; BookmarkDetailHost extraction is a careful ~200-line refactor |
| **B: Medium Layout** | 4, 5, 8-medium, 9 | **Sonnet** | New composable following clear existing patterns; moderate AppShell refactor with well-defined spec |
| **C: Expanded Layout** | 6, 7, 8-expanded | **Opus** | ListDetailPaneScaffold integration is architecturally complex; navigation event interception, deep links, and back handling have subtle interactions |
| **D: Card Adaptations** | 10 | **Sonnet** | UI-focused work with clear per-card-type specs |
| **E: Polish** | 11, 12 | **Sonnet** | CompositionLocal plumbing and mechanical string additions |

---

## Phase A: Foundation (Steps 1–3)

### Step 1: Dependency Updates

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`

The current `material3-adaptive:1.0.0-alpha02` is the old monolithic artifact. Replace with stable split artifacts.

**libs.versions.toml:**
- Remove `material3WindowSizeClass = "1.0.0-alpha02"` version entry
- Change `material3Adaptive = "1.0.0-alpha02"` → `"1.1.0"`
- Remove old library entry `androidx-material3-windowsize`
- Change old `androidx-material3-adaptive` group from `androidx.compose.material3` to `androidx.compose.material3.adaptive` and name from `material3-adaptive` to `adaptive`
- Add two new library entries with same group and version:
  - `adaptive-layout` (provides `ListDetailPaneScaffold`)
  - `adaptive-navigation` (provides `rememberListDetailPaneScaffoldNavigator`)

**build.gradle.kts:** Replace the 2 old implementation lines with 3 new ones. Remove `libs.androidx.material3.windowsize`.

### Step 2: Refactor BookmarkDetailViewModel for Pane Reuse

**File:** `BookmarkDetailViewModel.kt`

**Problem:** `uiState` property uses `bookmarkRepository.observeBookmark(bookmarkId!!)` where `bookmarkId` comes from `SavedStateHandle`. In the expanded pane context (no NavHost for detail), SavedStateHandle is empty → NPE.

**Changes:**
1. Replace `private val bookmarkId: String? = savedStateHandle["bookmarkId"]` with `private val _bookmarkId = MutableStateFlow<String?>(savedStateHandle["bookmarkId"])`
2. Make `uiState` reactive: `_bookmarkId.filterNotNull().flatMapLatest { id -> combine(bookmarkRepository.observeBookmark(id), ...) }.stateIn(...)`
3. Extract init block loading logic (lines 116–166) into `private fun initializeBookmark(id: String)`
4. Init block: if `_bookmarkId.value != null`, call `initializeBookmark(it)`
5. Add public `fun loadBookmark(bookmarkId: String)` that resets state, sets `_bookmarkId.value`, and calls `initializeBookmark()`
6. Update all internal refs from `bookmarkId` to `_bookmarkId.value` (`saveCurrentProgress`, etc.)

The NavHost path (compact/medium) is unchanged — `SavedStateHandle` still provides the initial value.

### Step 3: Extract BookmarkDetailHost for Reuse

**File:** `BookmarkDetailScreen.kt`

Extract the body of the outer `BookmarkDetailScreen` (lines 104–296) into a shared composable:

```kotlin
@Composable
fun BookmarkDetailHost(
    viewModel: BookmarkDetailViewModel,
    showOriginal: Boolean,
    onNavigateBack: () -> Unit
)
```

Contains all state collection, side-effect handling (openUrl, shareIntent, delete snackbar), and renders the inner pure `BookmarkDetailScreen` (line 301).

The existing outer composable becomes a thin wrapper:
```kotlin
fun BookmarkDetailScreen(navHostController, bookmarkId, showOriginal) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel()
    BookmarkDetailHost(viewModel, showOriginal, onNavigateBack = { navHostController.popBackStack() })
}
```

---

## Phase B: Medium Layout (Steps 4, 5, 8-medium, 9)

### Step 4: Create AppNavigationRailContent

**New file:** `ui/shell/AppNavigationRailContent.kt`

A `NavigationRail` mirroring `AppDrawerContent`:
- Same icons (TaskAlt, Inventory2, Grade, Article, VideoLibrary, Image, Label, Settings, Info)
- Icons only (no labels) — saves width on 600–840dp
- No badges (too compact for rail)
- Settings/About pushed to bottom via `Spacer(Modifier.weight(1f))`
- Same selected state logic and callbacks as `AppDrawerContent`

### Step 5: Adapt AppDrawerContent for Permanent Drawer

**File:** `AppDrawerContent.kt`

Currently wraps content in `ModalDrawerSheet` (line 59). For expanded, needs `PermanentDrawerSheet`.

Add parameter `usePermanentSheet: Boolean = false`. When true, use `PermanentDrawerSheet`; when false, use `ModalDrawerSheet` (existing behavior).

### Step 8 (medium part): Add MediumAppShell to AppShell

**File:** `AppShell.kt`

1. Add `currentWindowAdaptiveInfo()` to get WindowSizeClass
2. Extract current code into `CompactAppShell` (pure extraction, zero behavior change)
3. Add `MediumAppShell`: `Row { AppNavigationRailContent(...); Surface { NavHost(...) } }`
4. Branch on `windowSizeClass.windowWidthSizeClass` — Compact goes to existing code, Medium to new rail layout

### Step 9: Add showNavigationIcon to BookmarkListScreen

**File:** `BookmarkListScreen.kt`

Add `showNavigationIcon: Boolean = true` parameter. When false, hide the hamburger `IconButton` in TopAppBar (rail or permanent drawer is already visible).

---

## Phase C: Expanded Layout (Steps 6, 7, 8-expanded)

### Step 6: Create BookmarkDetailPaneHost

**New file:** `ui/shell/BookmarkDetailPaneHost.kt`

```kotlin
@Composable
fun BookmarkDetailPaneHost(
    bookmarkId: String,
    showOriginal: Boolean,
    onNavigateBack: () -> Unit
) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel(key = "detail_pane")
    LaunchedEffect(bookmarkId) { viewModel.loadBookmark(bookmarkId) }
    BookmarkDetailHost(viewModel, showOriginal, onNavigateBack)
}
```

Single ViewModel instance (`key = "detail_pane"`) that switches bookmarks via `loadBookmark()`.

### Step 7: Create ListDetailLayout

**New file:** `ui/shell/ListDetailLayout.kt`

`ExpandedListDetailLayout` composable wrapping `ListDetailPaneScaffold`:
- List pane: `BookmarkListScreen` with `showNavigationIcon = false`
- Detail pane: `BookmarkDetailPaneHost` when bookmark selected, empty placeholder when not
- Uses `rememberListDetailPaneScaffoldNavigator<String>()` for pane management
- `BackHandler(navigator.canNavigateBack())` for system back
- `LaunchedEffect(selectedBookmarkId)` syncs selection with scaffold navigator

### Step 8 (expanded part): Add ExpandedAppShell

**File:** `AppShell.kt`

1. Add `selectedBookmarkId: MutableState<String?>` at AppShell level
2. `ExpandedAppShell`: `PermanentNavigationDrawer { Surface { NavHost } }` where `BookmarkListRoute` renders `ExpandedListDetailLayout`
3. Navigation event interception: On expanded, `NavigateToBookmarkDetail` sets `selectedBookmarkId` instead of NavHost navigate
4. Deep link handling: `BookmarkDetailRoute` composable on expanded redirects to list+detail pane (sets selectedBookmarkId, pops self, navigates to BookmarkListRoute)

---

## Phase D: Card Adaptations (Step 10)

**Files:** `BookmarkCard.kt`, `BookmarkListScreen.kt`

On medium/expanded width, adapt card layouts for wider screens:

**Grid cards:**
- Image above text content instead of left-side thumbnail — full card width for image, title/metadata/actions below
- Slightly more spacing between cards

**Compact cards:**
- Action icons (favorite, archive, delete) moved up to title row (right side) instead of below
- Labels on same line as site name, to its right (instead of separate row)

**Mosaic cards:**
- Keep current overlay design
- Reduce spacing between cards to near-zero (tight grid, image-dominant)

**Implementation:** Each card composable receives `isWideLayout: Boolean` and renders the appropriate variant. Passed from `BookmarkListScreen` or via `CompositionLocal`.

---

## Phase E: Polish (Steps 11–12)

### Step 11: Reader Width Constraints

**Files:** `Dimens.kt`, `BookmarkDetailScreen.kt`

Add to `Dimens`:
```kotlin
val ReaderMaxWidthMedium = 720.dp
val ReaderMaxWidthExpanded = 840.dp
```

Add `CompositionLocal`:
```kotlin
val LocalReaderMaxWidth = compositionLocalOf { Dp.Unspecified }
```

Provide in AppShell based on size class. Consume in the inner `BookmarkDetailScreen` wrapping Scaffold content in `Box(Modifier.widthIn(max = ...))`. User typography settings (`fillMaxWidth(0.9f)` / `fillMaxWidth(0.75f)`) compose naturally within this outer constraint.

### Step 12: Localized Strings

**All 10 strings.xml files:**
```xml
<string name="select_bookmark">Select a bookmark to read</string>
```

For the empty detail pane placeholder on expanded.

---

## Files Modified (summary)

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Dependency migration |
| `app/build.gradle.kts` | New artifact refs |
| `BookmarkDetailViewModel.kt` | Reactive bookmarkId, loadBookmark() |
| `BookmarkDetailScreen.kt` | Extract BookmarkDetailHost, reader width |
| `AppShell.kt` | Size-class branching, 3 layout variants |
| `AppDrawerContent.kt` | usePermanentSheet param |
| `BookmarkListScreen.kt` | showNavigationIcon param |
| `BookmarkCard.kt` | Wide-layout card variants |
| `Dimens.kt` | Reader width tokens, CompositionLocal |

## New Files

| File | Purpose |
|---|---|
| `ui/shell/AppNavigationRailContent.kt` | NavigationRail items |
| `ui/shell/ListDetailLayout.kt` | ListDetailPaneScaffold wrapper |
| `ui/shell/BookmarkDetailPaneHost.kt` | Pane-aware detail wrapper |

---

## Verification

1. **Build**: `./gradlew assembleDebug` passes
2. **Compact portrait/landscape**: All behavior identical to current (drawer, NavHost push, transitions)
3. **Medium**: NavigationRail visible, no hamburger, NavHost push for detail
4. **Expanded**: PermanentDrawer visible, selecting bookmark shows detail in adjacent pane, back clears detail
5. **Deep links**: BookmarkDetail deep links work on all size classes
6. **Reader width**: Content constrained to 720dp (medium) / 840dp (expanded), typography settings compose within
7. **System back**: Correct on all form factors (pops detail pane on expanded, NavHost pop on compact/medium)
8. **LabelsBottomSheet**: Stays as ModalBottomSheet (ModalSideSheet not stable yet)
9. **Card adaptations**: Grid image-above, compact action icons beside title, mosaic tight spacing on medium/expanded

---

## Future Work (not in this phase)

- **Filter form as persistent pane:** On expanded, the filter bottom sheet could instead be a right-side pane that persists until closed, with cards reflowing to fill remaining width
- **Bookmark detail as reading-view side pane:** On expanded landscape, the Bookmark Detail info could show as a persistent side pane to the right of reading content instead of a dialog. This could justify moving the Info icon back to the TopAppBar as a direct action (similar to Readeck's detail icon)
