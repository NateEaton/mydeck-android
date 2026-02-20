# Phase 5 Implementation Prompts

Use these prompts sequentially. Each phase should compile and not regress before
moving to the next. Commit after each phase.

Recommended: Run each prompt in a fresh conversation on the specified branch.

---

## Phase A: Foundation (Opus)

```
Implement Phase 5A (Foundation) from @docs/Phase-5-Tablet-Adaptive-Layout-Plan.md

This phase covers Steps 1–3: dependency updates, ViewModel refactor, and
BookmarkDetailHost extraction. Follow CLAUDE.md and Android/Jetpack Compose
best practices.

Critical constraint: Phone behavior (compact size class) must remain UNCHANGED
after this phase. These are purely structural changes that enable tablet support
without altering existing UX.

Step 1 — Dependency updates:
- Read gradle/libs.versions.toml and app/build.gradle.kts
- Replace the old monolithic material3-adaptive:1.0.0-alpha02 with split
  artifacts at 1.1.0 (adaptive, adaptive-layout, adaptive-navigation) under
  the group androidx.compose.material3.adaptive
- Remove the material3-window-size-class entry entirely (currentWindowAdaptiveInfo()
  from the adaptive artifact replaces it)
- Verify the project compiles after these changes

Step 2 — BookmarkDetailViewModel refactor:
- Read BookmarkDetailViewModel.kt fully before making changes
- The bookmarkId field (line 62) currently reads from SavedStateHandle and is
  used directly in the uiState combine (line 207) as bookmarkId!! — this will
  NPE when the ViewModel is used outside a NavHost (expanded pane)
- Change bookmarkId to a MutableStateFlow initialized from SavedStateHandle
- Make uiState reactive to bookmarkId changes using filterNotNull().flatMapLatest
- Extract the init block loading logic (lines 116-166) into a private
  initializeBookmark(id) method
- Add a public loadBookmark(bookmarkId: String) method that resets state, sets
  the flow value, and calls initializeBookmark
- Update all internal references (saveCurrentProgress, etc.) to use the flow value
- The existing SavedStateHandle path must continue working for compact/medium

Step 3 — Extract BookmarkDetailHost:
- Read BookmarkDetailScreen.kt fully before making changes
- The outer BookmarkDetailScreen (line 104) creates a ViewModel and hard-codes
  navHostController.popBackStack() for back navigation
- Extract the body (lines ~106-296) into a new BookmarkDetailHost composable
  that takes viewModel, showOriginal, and onNavigateBack as parameters
- The existing outer composable becomes a thin wrapper passing
  onNavigateBack = { navHostController.popBackStack() }
- All behavior must be identical after this refactor

After all three steps, run ./gradlew assembleDebug to verify compilation.
Confirm that the existing phone UX is structurally unchanged.
```

---

## Phase B: Medium Layout (Sonnet)

```
Implement Phase 5B (Medium Layout) from @docs/Phase-5-Tablet-Adaptive-Layout-Plan.md

This phase covers Steps 4, 5, 8 (medium part), and 9: NavigationRail, drawer
adaptation, MediumAppShell, and showNavigationIcon. Follow CLAUDE.md.

Critical constraint: Phone behavior (compact size class) must remain UNCHANGED.
The compact path should be a pure extraction of existing code with zero behavior
changes.

Step 4 — Create AppNavigationRailContent:
- Read AppDrawerContent.kt to understand the existing drawer items, icons, and
  selected state logic
- Create ui/shell/AppNavigationRailContent.kt with a NavigationRail containing
  NavigationRailItem elements mirroring each drawer item
- Same icons: TaskAlt (My List), Inventory2 (Archive), Grade (Favorites),
  Article (Articles), VideoLibrary (Videos), Image (Pictures), Label (Labels),
  Settings, Info (About)
- Icons only — no text labels
- No badges (too compact)
- Settings and About pushed to bottom via Spacer(Modifier.weight(1f))
- Same selected-state logic as AppDrawerContent (check drawerPreset and
  activeLabel)
- Same callback signature as AppDrawerContent

Step 5 — Adapt AppDrawerContent:
- Add usePermanentSheet: Boolean = false parameter
- When true, wrap the Column in PermanentDrawerSheet instead of ModalDrawerSheet
- All content stays identical

Step 8 (medium) — Refactor AppShell:
- Read AppShell.kt fully before making changes
- Add currentWindowAdaptiveInfo() call to get WindowSizeClass (from the adaptive
  artifact added in Phase A)
- Extract the current ModalNavigationDrawer + Surface + NavHost block into a
  private CompactAppShell composable (PURE extraction, no changes)
- Create a private MediumAppShell composable:
  Row { AppNavigationRailContent(...); Surface { NavHost (same routes/transitions) } }
- In the main AppShell body, branch on
  windowSizeClass.windowWidthSizeClass: Compact → CompactAppShell,
  Medium → MediumAppShell, Expanded → MediumAppShell (temporary, Phase C adds
  the real expanded layout)
- The NavHost route definitions and transition specs should be shared (extracted
  to a helper or inline function) to avoid duplication between compact/medium

Step 9 — showNavigationIcon:
- Read BookmarkListScreen.kt, find the hamburger icon button in the TopAppBar
- Add showNavigationIcon: Boolean = true parameter to BookmarkListScreen
- When false, hide the hamburger IconButton
- Pass showNavigationIcon = false from MediumAppShell's BookmarkListRoute

After all steps, run ./gradlew assembleDebug. Confirm compact behavior unchanged.
On a medium-width device/emulator, the NavigationRail should appear on the left
with the bookmark list filling the remaining width.
```

---

## Phase C: Expanded Layout (Opus)

```
Implement Phase 5C (Expanded Layout) from @docs/Phase-5-Tablet-Adaptive-Layout-Plan.md

This phase covers Steps 6, 7, and 8 (expanded part): BookmarkDetailPaneHost,
ListDetailLayout, and ExpandedAppShell. This is the most architecturally complex
phase. Follow CLAUDE.md.

Critical constraints:
- Phone behavior (compact) must remain UNCHANGED
- Medium behavior (rail + single pane) must remain UNCHANGED
- The compact path continues using NavHost push for detail views
- Only expanded introduces pane-based navigation via ListDetailPaneScaffold
- User typography settings must compose WITHIN adaptive widthIn(max) constraints

Start by reading these files to understand the current state after Phases A and B:
- AppShell.kt (now has CompactAppShell and MediumAppShell)
- BookmarkDetailScreen.kt (now has BookmarkDetailHost extraction)
- BookmarkDetailViewModel.kt (now has loadBookmark method)
- ListDetailLayout.kt does NOT exist yet
- BookmarkDetailPaneHost.kt does NOT exist yet

Step 6 — Create BookmarkDetailPaneHost:
- New file: ui/shell/BookmarkDetailPaneHost.kt
- Takes bookmarkId: String, showOriginal: Boolean, onNavigateBack: () -> Unit
- Creates ViewModel via hiltViewModel(key = "detail_pane") — single instance
  for the pane that switches bookmarks via loadBookmark()
- LaunchedEffect(bookmarkId) calls viewModel.loadBookmark(bookmarkId)
- Delegates to BookmarkDetailHost(viewModel, showOriginal, onNavigateBack)

Step 7 — Create ListDetailLayout:
- New file: ui/shell/ListDetailLayout.kt
- ExpandedListDetailLayout composable wrapping ListDetailPaneScaffold
- Takes: bookmarkListViewModel, selectedBookmarkId (MutableState<String?>),
  selectedShowOriginal (MutableState<Boolean>), navController
- Uses rememberListDetailPaneScaffoldNavigator<String>()
- List pane: BookmarkListScreen with showNavigationIcon = false, drawerState
  can use a dummy closed DrawerState since drawer isn't used on expanded
- Detail pane: When navigator has content, show BookmarkDetailPaneHost with
  onNavigateBack clearing selectedBookmarkId and calling navigator.navigateBack().
  When no content, show a centered placeholder with the select_bookmark string.
- LaunchedEffect(selectedBookmarkId.value) syncs selection with scaffold navigator
  via navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
- BackHandler(navigator.canNavigateBack()) clears selection and navigates back

Step 8 (expanded) — ExpandedAppShell:
- In AppShell.kt, add selectedBookmarkId and selectedShowOriginal MutableState
- Create ExpandedAppShell composable:
  PermanentNavigationDrawer(
    drawerContent = { AppDrawerContent(usePermanentSheet = true, ...) }
  ) {
    Surface { NavHost (same routes/transitions as compact/medium) }
  }
- In the BookmarkListRoute composable for expanded, render
  ExpandedListDetailLayout instead of plain BookmarkListScreen
- Navigation event interception: In the LaunchedEffect collecting
  bookmarkListViewModel.navigationEvent, check the width size class. On expanded,
  NavigateToBookmarkDetail sets selectedBookmarkId instead of navController.navigate
- Deep link handling: Register BookmarkDetailRoute in the expanded NavHost. Its
  composable body does: LaunchedEffect(route.bookmarkId) { set selectedBookmarkId,
  pop self, navigate to BookmarkListRoute with launchSingleTop = true }
- Replace the temporary Expanded → MediumAppShell fallback with the real
  ExpandedAppShell
- On PermanentNavigationDrawer, drawer callbacks should NOT call drawerState.close()
  (there's nothing to close)

After all steps, run ./gradlew assembleDebug. Test mentally:
- Compact: drawer, push nav, transitions — all unchanged
- Medium: rail, push nav — unchanged from Phase B
- Expanded: permanent drawer on left, list in center, detail pane on right when
  bookmark selected. Back clears detail pane. Deep links work.
```

---

## Phase D: Card Adaptations (Sonnet)

```
Implement Phase 5D (Card Adaptations) from @docs/Phase-5-Tablet-Adaptive-Layout-Plan.md

This phase covers Step 10: adapting bookmark card layouts for wider screens.
Follow CLAUDE.md.

Read BookmarkCard.kt fully to understand the three card variants (Mosaic, Grid,
Compact) and their current layouts. Also read BookmarkListScreen.kt to understand
how cards are rendered and how to pass layout context.

Add an isWideLayout: Boolean parameter to each card variant (default false).
Thread it from BookmarkListScreen, which determines wide layout based on
WindowWidthSizeClass (Medium or Expanded = wide). Use a CompositionLocal
(LocalIsWideLayout) provided in AppShell to avoid threading through many layers.

Grid cards (isWideLayout = true):
- Image displayed ABOVE text content instead of as a left-side thumbnail
- Image uses the full card width with a fixed aspect ratio (16:9 or similar)
- Title, metadata, actions, and label chips below the image
- Slightly more spacing between cards (12dp instead of current spacing)

Compact cards (isWideLayout = true):
- Action icons (favorite, archive, delete) moved UP to the title row, aligned
  to the right, instead of in a separate row below
- Labels shown on the SAME LINE as site name, to its right, instead of a
  separate row
- This makes compact cards more horizontally efficient on wider screens

Mosaic cards (isWideLayout = true):
- Keep the current gradient overlay design unchanged
- Reduce spacing between cards to 2dp (near-zero, tight image grid)

Ensure all card variants still work correctly with isWideLayout = false (phone).
Run ./gradlew assembleDebug to verify.
```

---

## Phase E: Polish (Sonnet)

```
Implement Phase 5E (Polish) from @docs/Phase-5-Tablet-Adaptive-Layout-Plan.md

This phase covers Steps 11–12: reader width constraints and localized strings.
Follow CLAUDE.md.

Step 11 — Reader width constraints:
- Read Dimens.kt and BookmarkDetailScreen.kt
- Add to Dimens.kt:
  val ReaderMaxWidthMedium = 720.dp
  val ReaderMaxWidthExpanded = 840.dp
- Create a CompositionLocal in Dimens.kt (or a new file):
  val LocalReaderMaxWidth = compositionLocalOf { Dp.Unspecified }
- In AppShell.kt, provide LocalReaderMaxWidth based on window size class:
  Medium → ReaderMaxWidthMedium, Expanded → ReaderMaxWidthExpanded,
  Compact → Dp.Unspecified
- In the inner BookmarkDetailScreen composable (the one with Scaffold), wrap
  the content area in:
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = existingModifier.then(
      if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier
    )) { ... existing content ... }
  }
- User typography text width settings (fillMaxWidth 0.9f/0.75f) already operate
  as fractions and compose naturally within the outer widthIn constraint

Step 12 — Localized strings:
- Add to ALL 10 strings.xml files (values/ plus 9 locale variants):
  <string name="select_bookmark">Select a bookmark to read</string>
- This is used for the empty detail pane placeholder on expanded

Run ./gradlew assembleDebug and ./gradlew lintDebug to verify compilation
and lint pass.
```
