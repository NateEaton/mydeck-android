# MyDeck UI/UX Refactor — Implementation Roadmap

---

## A) Unified Architecture & UX Strategy

### Cohesive UX Model

The four initiatives converge around one principle: **the Bookmark List is the center of the universe**. Labels, filters, and layout adaptations are all ways of *viewing* that list — not separate destinations.

- **Labels** become a filter parameter, not a navigation route. Users stay on `BookmarkListScreen` and apply label filters via a modal sheet.
- **Filtering** is a unified system where label, status, type, and sort are all peers in a single `FilterState` object (which already partially exists in `BookmarkListViewModel`).
- **Tablet layout** wraps this same content in an adaptive scaffold — the list/detail split and persistent navigation are *container-level* concerns, not screen-level ones.

### Current Navigation Conflicts

1. **Drawer ownership:** `BookmarkListScreen.kt` currently hosts its own `ModalNavigationDrawer`. For tablet, the drawer/rail must exist *outside* the NavHost so it persists across destinations. This is the single largest blocker.
2. **Labels as a route:** The current `filterState.viewingLabelsList` boolean creates a pseudo-destination inside the list screen. This conflates navigation state with filter state and is the root cause of the labels sub-heading bug.
3. **No adaptive container:** There is no `WindowSizeClass` usage. The entire UI assumes compact width.

### End-State UI Patterns

| Window Size Class | Navigation | Content | Detail |
|:---|:---|:---|:---|
| **Compact** (phone portrait) | `ModalNavigationDrawer` | Full-width bookmark list | Full-screen push navigation |
| **Medium** (phone landscape, small tablet) | `NavigationRail` | Full-width bookmark list | Full-screen push navigation |
| **Expanded** (tablet landscape) | `PermanentNavigationDrawer` | List pane (~40%) | Detail pane (~60%) via `ListDetailPaneScaffold` |

Filter UI (summary bar + filter panel) sits *inside* the content area, below the TopAppBar, across all size classes.

---

## B) Work Buckets + Sequencing Plan

### Recommended Sequence

```
Phase 0: Foundation & Design Tokens
    ↓
Phase 1: Navigation Architecture Refactor  ← critical prerequisite
    ↓
Phase 2: M3 Component Remediation
    ↓
Phase 3: Labels → Filter State Migration
    ↓
Phase 4: Unified Filtering System
    ↓
Phase 5: Tablet / Adaptive Layout
    ↓
Phase 6: Polish & Transitions
```

### Justification

- **Phase 0 before everything:** `Dimens.kt` and design tokens are referenced by all subsequent phases.
- **Phase 1 before anything else:** Lifting the drawer out of `BookmarkListScreen` is a strict prerequisite for tablet (Phase 5) and prevents rework in Phases 2-4. This is the finding from the M3 Compliance Review.
- **Phase 2 before filtering:** Adopting `ListItem`, `FilterChip`, `InputChip` gives us the building blocks that Phases 3-4 consume.
- **Phase 3 before Phase 4:** The label-as-filter migration must happen first so that the unified filter system doesn't need to special-case labels.
- **Phase 4 before Phase 5:** Filter UI components must be stable before embedding them in adaptive layouts.
- **Phase 5 last:** Tablet depends on clean navigation (Phase 1), modular list components (Phase 2), and stable filter state (Phase 4).
- **Phase 6 anytime after Phase 1:** Transitions and polish are low-risk and can be done in parallel with later phases.

### Dependency Graph

```
Phase 0 ──→ Phase 1 ──→ Phase 2 ──→ Phase 3 ──→ Phase 4 ──→ Phase 5
                  │                                              ↑
                  └──────── Phase 6 (parallel after Phase 1) ────┘
```

---

## C) Implementation Plan Per Phase

---

### Phase 0: Foundation & Design Tokens

**Goals:** Establish shared constants and prepare the build for adaptive libraries.

**Scope:**
- Create `ui/theme/Dimens.kt` with centralized spacing, icon size, and corner radius tokens
- Add `androidx.compose.material3.adaptive` dependencies to `build.gradle.kts`
- Add `WindowSizeClass` dependency (`material3-window-size-class`)

**Files impacted:**
- `app/build.gradle.kts` — new dependencies
- New: `app/src/main/java/com/mydeck/app/ui/theme/Dimens.kt`

**New components:** `Dimens` object

**Refactor vs new:** 100% new, zero refactoring

**Risks:** Minimal. Pure additive.

**Acceptance criteria:**
- [ ] `Dimens.kt` exists with tokens matching the M3 Review doc
- [ ] Project compiles with adaptive library dependencies
- [ ] No existing UI changes

---

### Phase 1: Navigation Architecture Refactor

**Goals:** Lift navigation chrome (drawer, scaffold, top bar) out of individual screens into an app-level shell. This is the **most important phase** — all subsequent work depends on it.

**Scope:**
- Create an `AppShell` composable that owns `ModalNavigationDrawer` + `Scaffold`
- `NavHost` becomes a *child* of this shell
- Drawer content is extracted from `BookmarkListScreen` into a shared `AppDrawerContent` composable
- `BookmarkListScreen` loses its drawer and scaffold; it becomes pure content
- Top bar management moves to the shell (with per-route customization via callbacks or state)
- **White flash fix:** The persistent themed Scaffold wrapping the NavHost ensures a consistent background color is always rendered, eliminating the white flash visible during screen transitions (the flash occurs because the NavHost briefly renders empty/default background between destinations when no outer Scaffold exists)

**Files impacted:**
- `MainActivity.kt` — NavHost restructured inside new AppShell
- `BookmarkListScreen.kt` — remove ModalNavigationDrawer, Scaffold wrapping
- New: `ui/shell/AppShell.kt` — top-level layout composable
- New: `ui/shell/AppDrawerContent.kt` — extracted drawer content
- `Routes.kt` — no changes expected

**New components:**
- `AppShell` — owns Drawer + Scaffold + NavHost
- `AppDrawerContent` — reusable drawer content

**Refactor vs new:**
- ~70% refactor (extracting existing drawer/scaffold logic)
- ~30% new (AppShell wiring, state hoisting)

**Risks:**
- **High regression risk** — this touches the navigation backbone. Every screen flows through this path.
- **Mitigation:** Keep functional behavior identical. Do not change routes, destinations, or any ViewModel logic. This is purely a *structural* lift.
- **Testing:** After this phase, verify: drawer opens/closes, all destinations reachable, back navigation correct, deep links work.

**Acceptance criteria:**
- [ ] Drawer exists at app level, not inside any screen composable
- [ ] All existing routes navigate correctly
- [ ] Drawer state persists when navigating between destinations
- [ ] Deep link to BookmarkDetail still works
- [ ] Share intent flow still works
- [ ] No visual changes to the user (pixel-identical behavior)
- [ ] No white flash between screen transitions (themed background always visible)

---

### Phase 2: M3 Component Remediation

**Goals:** Replace custom layouts with standard M3 composables. This creates the component vocabulary for Phases 3-5.

**Scope:**
- Replace custom `Row` layouts in Settings screens with `ListItem`
- Fix Mosaic card text contrast (gradient → `Surface` scrim)
- Switch TopAppBar → `CenterAlignedTopAppBar` on main screens
- Standardize drawer header typography
- Add `contentPadding` to Settings `LazyColumn` for gesture bar
- **Label chip parity across card variants:** Currently only Mosaic cards show label chips. Add tappable label chips to Grid cards (space permitting). For Compact cards, use a single truncated chip or omit — the FilterBar (Phase 4) will make active label state visible regardless of card variant. This ensures labels are tappable entry points to filtering across all view modes.
- Quick wins from the M3 Review checklist

**Files impacted:**
- `SettingsScreen.kt` — `ListItem` adoption
- `AccountSettingsScreen.kt` — same
- `UiSettingsScreen.kt` — same
- `SyncSettingsScreen.kt` — same
- `BookmarkCard.kt` — Mosaic contrast fix, label chip additions to Grid/Compact variants
- `BookmarkListScreen.kt` — TopAppBar update, drawer header
- `BookmarkDetailTopBar.kt` — TopAppBar type

**New components:** None (using existing M3 components)

**Refactor vs new:** 100% refactor

**Risks:**
- Settings layout changes could shift element positions. Visual regression testing recommended.
- Mosaic card scrim change affects the most visible UI element.
- **Mitigation:** Change one screen at a time. Test each independently.

**Acceptance criteria:**
- [ ] All Settings screens use `ListItem` — verify 56dp/72dp min heights
- [ ] Mosaic cards pass WCAG AA contrast on bright images
- [ ] TopAppBar is `CenterAlignedTopAppBar` on BookmarkList
- [ ] No custom `Row` layouts remain in Settings
- [ ] Label chips appear on Grid cards (tappable, triggering label filter)
- [ ] Compact cards show a single truncated label chip or gracefully omit if no space
- [ ] Quick wins checklist items from M3 Review are complete

---

### Phase 3: Labels → Filter State Migration

**Goals:** Remove Labels as a pseudo-navigation destination. Convert it to a filter parameter surfaced via a Bottom Sheet.

**Scope:**
- Remove `viewingLabelsList` boolean from `FilterState`
- Remove the labels list view mode from `BookmarkListScreen`
- Create `LabelsBottomSheet` composable with searchable label list
- Drawer "Labels" item now opens the sheet instead of switching view mode
- Selected label populates `filterState.label` (already exists)
- Label management (rename/delete) moves into the sheet via context menu or long-press

**Files impacted:**
- `BookmarkListViewModel.kt` — remove `viewingLabelsList`, `onClickLabelsView`; add sheet open/close state
- `BookmarkListScreen.kt` — remove labels list rendering, add LabelsBottomSheet integration
- `AppDrawerContent.kt` — "Labels" item triggers sheet
- New: `ui/list/LabelsBottomSheet.kt`

**New components:**
- `LabelsBottomSheet` — `ModalBottomSheet` containing:
  - Search bar (M3 `SearchBar` or `DockedSearchBar`)
  - `LazyColumn` of `ListItem` rows (icon, label name, count badge)
  - Long-press context menu for edit/delete

**Refactor vs new:**
- ~50% refactor (removing old label list code)
- ~50% new (LabelsBottomSheet)

**Risks:**
- Users who relied on the labels list as a "screen" will experience a UX change. This is intentional and an improvement.
- Label rename/delete dialogs must still work — they're being re-hosted, not rewritten.
- **Mitigation:** Keep the existing rename/delete dialog composables. Only change *where* they're triggered from.

**Acceptance criteria:**
- [ ] "Labels" in drawer opens a bottom sheet, not a list view
- [ ] Sheet shows all labels with counts, supports search/filter
- [ ] Selecting a label dismisses sheet and filters the bookmark list
- [ ] Long-press on a label in the sheet opens edit/delete options
- [ ] Tapping a label chip on a Mosaic card filters by that label (existing behavior preserved)
- [ ] `viewingLabelsList` is fully removed from codebase
- [ ] The sub-heading label bug is gone (no more label state leaking)

---

### Phase 4: Unified Filtering System

**Goals:** Implement a multi-filter system with filter panel and persistent summary bar.

**Scope:**

#### Filter Dimensions

| Filter | Type | Values | Current State |
|:---|:---|:---|:---|
| **Status** | Single-select | My List (unread), Archived, Favorites | Exists in `FilterState` |
| **Content Type** | Multi-select | Article, Video, Photo | Partially exists (`type` in DAO) |
| **Label** | Single-select | Dynamic from data | Exists in `FilterState.label` |
| **Sort** | Single-select | 10 options (Newest, Oldest, Published, etc.) | Exists as `SortOption` |

#### UI Components

1. **FilterBar** (always visible below TopAppBar when any filter is active):
   - `LazyRow` of `InputChip` elements showing active filters
   - Each chip is dismissible (trailing X icon)
   - Tapping the bar area (or a "Filters" action chip) opens the Filter Panel

2. **FilterPanel** (expandable section below FilterBar):
   - Revealed with `AnimatedVisibility(expandVertically + fadeIn)`
   - Background: `Surface(color = surfaceContainer)`
   - **Status row:** `FilterChip` chips for My List / Archived / Favorites
   - **Type row:** `FilterChip` chips for Article / Video / Photo (multi-select)
   - **Sort row:** `SingleChoiceSegmentedButtonRow` for primary sort options or a compact dropdown
   - **Labels:** Button/chip that opens the `LabelsBottomSheet` from Phase 3

#### State Representation

```kotlin
// Expanded FilterState in BookmarkListViewModel
data class FilterState(
    val status: StatusFilter,       // MY_LIST, ARCHIVED, FAVORITES
    val types: Set<BookmarkType>,   // ARTICLE, VIDEO, PHOTO (multi-select)
    val label: String?,             // selected label or null
    val sortOption: SortOption,     // existing enum
)
```

- Filter state lives in ViewModel (not route params) — it's session-scoped and doesn't need to survive process death beyond what SavedStateHandle provides.
- The DAO already supports these filter dimensions. The `getBookmarksByFilters()` query accepts type, unread, archived, favorite parameters.

**Files impacted:**
- `BookmarkListViewModel.kt` — expand `FilterState`, consolidate sort into it
- `BookmarkListScreen.kt` — integrate FilterBar and FilterPanel
- `BookmarkDao.kt` — may need minor query updates for multi-type filtering
- New: `ui/components/FilterBar.kt`
- New: `ui/components/FilterPanel.kt`

**New components:**
- `FilterBar` — `LazyRow` of active `InputChip` elements
- `FilterPanel` — expandable `Surface` with `FilterChip` groups
- `FilterChipGroup` — reusable row of `FilterChip` for a single dimension

**Refactor vs new:**
- ~30% refactor (consolidating existing filter/sort state)
- ~70% new (FilterBar, FilterPanel composables)

**Risks:**
- The DAO's dynamic query may need adjustment for multi-type selection (currently single type filter). Verify `getBookmarksByFilters()` supports OR logic on types.
- Filter state explosion — too many active filters may confuse users.
- **Mitigation:** Keep the FilterPanel simple. Status is single-select, Type is multi-select, Label is single-select. This matches Readeck web behavior.

**Acceptance criteria:**
- [ ] FilterBar appears when any non-default filter is active
- [ ] Each active filter shows as a dismissible InputChip
- [ ] Dismissing a chip removes that filter and refreshes the list
- [ ] FilterPanel opens/closes with smooth animation
- [ ] Status, Type, Label, Sort all function correctly
- [ ] Clearing all filters returns to default "My List" view
- [ ] Bookmark counts update to reflect filtered results
- [ ] Search works independently of (and in combination with) filters

---

### Phase 5: Tablet / Adaptive Layout

**Goals:** Implement responsive layouts using M3 adaptive APIs for medium and expanded window size classes.

#### Adaptive Strategy

**Library:** `androidx.compose.material3.adaptive` with `ListDetailPaneScaffold`

**Breakpoints** (M3 standard WindowSizeClass):
- **Compact** (< 600dp): Current phone layout, no changes
- **Medium** (600-840dp): NavigationRail replaces ModalDrawer
- **Expanded** (> 840dp): PermanentNavigationDrawer + ListDetailPaneScaffold

#### Layout Changes Per Size Class

**Compact (no change):**
- ModalNavigationDrawer (from Phase 1 AppShell)
- Full-screen list → full-screen detail (push navigation)

**Medium:**
- `NavigationRail` on the leading edge (icons only, no labels by default)
- Full-screen list → full-screen detail
- Reader content constrained to `widthIn(max = 720.dp)`

**Expanded:**
- `PermanentNavigationDrawer` on the leading edge
- `ListDetailPaneScaffold`:
  - List pane: BookmarkList with filter UI
  - Detail pane: BookmarkDetail
  - Selecting a bookmark loads it in the detail pane without navigating away from the list
- Reader content constrained to `widthIn(max = 840.dp)` with centered alignment
- Labels sheet becomes a `ModalSideSheet` instead of `ModalBottomSheet`

**Scope:**
- Compute `WindowSizeClass` in `MainActivity`/`AppShell` and pass it down
- Refactor `AppShell` to swap navigation component based on size class
- Wrap BookmarkList + BookmarkDetail in `ListDetailPaneScaffold` for expanded
- Constrain reader content width on medium/expanded
- **Reader width / typography controls interaction:** The existing user typography settings (text width %, font size, line spacing in `TypographySettings`) must compose correctly with the adaptive `widthIn(max)` constraint. The `max` container width sets the outer bound; the user's text width % setting should operate *within* that bound. Verify behavior across compact (no outer constraint), medium (720dp max), and expanded detail pane (~60% of screen). Test in both portrait and landscape orientations.
- Refactor `VerticalScrollbar` to accept `ScrollableState` (for grid compatibility)

**Files impacted:**
- `MainActivity.kt` — `calculateWindowSizeClass()`
- `AppShell.kt` — adaptive navigation switching
- `BookmarkListScreen.kt` — pane-aware navigation callbacks
- `BookmarkDetailScreen.kt` — width constraints, pane awareness
- `LabelsBottomSheet.kt` — bottom sheet vs side sheet based on size class
- `VerticalScrollbar.kt` — generalize to `ScrollableState`
- New: `ui/shell/AdaptiveNavigation.kt` — navigation component selection logic
- New: `ui/shell/ListDetailLayout.kt` — `ListDetailPaneScaffold` wrapper

**New components:**
- `AdaptiveNavigation` — selects Drawer/Rail/PermanentDrawer by size class
- `ListDetailLayout` — `ListDetailPaneScaffold` integration
- `AppNavigationRailContent` — rail icon layout

**Refactor vs new:**
- ~40% refactor (navigation switching, width constraints)
- ~60% new (adaptive scaffold, rail, list-detail pane logic)

**Risks:**
- **Highest complexity phase.** The `ListDetailPaneScaffold` changes how navigation works for detail views — on expanded, selecting a bookmark should NOT push a new route; it should update the detail pane.
- Back button behavior changes on tablet (back from detail should clear the pane, not pop the nav stack).
- **Mitigation:** Use `ListDetailPaneScaffold`'s built-in navigator. Keep the phone NavHost path as-is for compact. Only tablet introduces pane-based navigation.
- Test on: phone portrait, phone landscape, tablet portrait, tablet landscape, foldable.

**Acceptance criteria:**
- [ ] Phone behavior unchanged (compact size class)
- [ ] Medium devices show NavigationRail
- [ ] Expanded devices show PermanentNavigationDrawer + list/detail split
- [ ] Selecting a bookmark on expanded loads detail in adjacent pane
- [ ] Back button behavior correct on all form factors
- [ ] Reader content has max-width constraint on medium/expanded
- [ ] User typography settings (text width %, font size, line spacing) work correctly within adaptive width constraints across all size classes and orientations
- [ ] No text overflow or squashing when switching between portrait and landscape on tablet
- [ ] Labels sheet adapts (bottom sheet on compact, side sheet on expanded)
- [ ] Filter UI works correctly in both pane and full-screen modes

---

### Phase 6: Polish & Transitions

**Goals:** Add navigation transitions, haptic feedback, and visual refinements.

**Scope:**
- Add `slideIntoContainer`/`slideOutOfContainer` transitions to NavHost composable definitions
- Add haptic feedback to bookmark actions (archive, delete, favorite)
- Implement skeleton/shimmer loading for bookmark cards
- Polish empty state screens (48dp icon + headline + body pattern)
- Edge-to-edge status bar transparency

**Files impacted:**
- `MainActivity.kt` — transition definitions
- `BookmarkCard.kt` — shimmer loading
- `BookmarkListScreen.kt` — empty state, haptics
- `Theme.kt` — edge-to-edge

**Risks:** Low. These are independent, incremental improvements.

**Acceptance criteria:**
- [ ] Screen transitions animate smoothly (300ms slide)
- [ ] Haptic feedback on long-press actions
- [ ] Empty states follow M3 pattern
- [ ] Status bar is transparent (edge-to-edge)

---

## D) Labels Feature Replacement Proposal

**Decision: Labels are a Filter State, not a Navigation Destination.**

### Justification

1. **Eliminates the state bug:** No separate "labels screen" means no stale sub-heading when switching back.
2. **Unified model:** Labels join status, type, and sort as peers in `FilterState`. One code path handles all filtering.
3. **Tablet-ready:** A bottom/side sheet for label selection works on all form factors. A full-screen labels list wastes space on tablets.
4. **Readeck familiarity preserved:** Users still see the searchable list of labels with counts — it's just in a sheet instead of a full screen.

### Phone Behavior
- Drawer → tap "Labels" → `ModalBottomSheet` slides up with searchable label list
- Select label → sheet dismisses → list filters → `InputChip` appears in FilterBar
- Tap chip X → filter cleared → back to full list

### Tablet Behavior
- PermanentDrawer → tap "Labels" → `ModalSideSheet` slides in from right
- Same interaction as phone, but spatially the sheet overlays the detail pane while the list remains visible

### Label Management
- Long-press a label in the sheet → context menu with "Rename" and "Delete"
- Same dialog composables as today, just triggered from the sheet instead of the list view

---

## E) List Filtering Feature Proposal

### Available Filters

| Filter | Type | Default | Values |
|:---|:---|:---|:---|
| Status | Single-select | My List | My List, Archived, Favorites |
| Content Type | Multi-select | All | Article, Video, Photo |
| Label | Single-select | None | Dynamic from database |
| Sort | Single-select | Newest | Newest, Oldest, Published (New→Old), Published (Old→New), Title (A→Z), Title (Z→A), Site (A→Z), Site (Z→A), Duration (Short→Long), Duration (Long→Short) |

### Filter UI Behavior

1. **FilterBar** (persistent when filters active):
   - Sits directly below TopAppBar
   - `LazyRow` of `InputChip` elements: one per active non-default filter
   - Leading "Filters" `AssistChip` toggles the FilterPanel open/closed
   - Each chip has trailing dismiss icon

2. **FilterPanel** (expandable):
   - `AnimatedVisibility` with `expandVertically() + fadeIn()`
   - `Surface(color = surfaceContainer)` background
   - Status row: `FilterChip` for each status option
   - Type row: `FilterChip` for each type (multi-select)
   - Sort: `SingleChoiceSegmentedButtonRow` for the two most common (Newest/Oldest), with a "More" overflow for the full list
   - Labels: `AssistChip` labeled "Label: [name]" or "Select Label" — tapping opens `LabelsBottomSheet`

3. **Behavior:**
   - Changing a filter immediately refreshes the list (no "Apply" button)
   - Clearing all filters resets to default (My List, All types, no label, Newest)
   - Search operates independently — you can search within a filtered set

### Internal State Representation

```kotlin
// Expanded FilterState in BookmarkListViewModel
data class FilterState(
    val status: StatusFilter,       // MY_LIST, ARCHIVED, FAVORITES
    val types: Set<BookmarkType>,   // ARTICLE, VIDEO, PHOTO (multi-select)
    val label: String?,             // selected label or null
    val sortOption: SortOption,     // existing enum
)
```

- Lives in `BookmarkListViewModel` as `MutableStateFlow<FilterState>`
- Drives the `BookmarkDao.getBookmarksByFilters()` query
- No route params needed — filter state is ViewModel-scoped
- `SavedStateHandle` can persist it across config changes if desired

---

## F) Tablet Layout Plan

### Adaptive Strategy

Use `androidx.compose.material3.adaptive` library with `ListDetailPaneScaffold`.

### Breakpoints

| Size Class | Width | Navigation | Content Layout |
|:---|:---|:---|:---|
| Compact | < 600dp | `ModalNavigationDrawer` | Single pane, push navigation |
| Medium | 600-840dp | `NavigationRail` | Single pane, push navigation |
| Expanded | > 840dp | `PermanentNavigationDrawer` | `ListDetailPaneScaffold` (list + detail side-by-side) |

### Navigation Adaptation

```
AppShell:
  when (windowSizeClass.widthSizeClass) {
      Compact -> ModalNavigationDrawer { Scaffold { NavHost } }
      Medium  -> Row { NavigationRail(); Scaffold { NavHost } }
      Expanded -> PermanentNavigationDrawer { ListDetailPaneScaffold { ... } }
  }
```

### List/Detail Behavior on Expanded

- `ListDetailPaneScaffold` manages two panes
- **List pane:** `BookmarkListScreen` content (cards + filter UI)
- **Detail pane:** `BookmarkDetailScreen` content
- Selecting a bookmark updates the detail pane *without* NavHost navigation — the scaffold handles pane visibility
- On compact/medium, bookmark selection still uses NavHost push (existing behavior)
- Back on expanded: clears detail pane (shows empty/placeholder state)

### Reader Width Constraint

On medium and expanded, article content is wrapped:
```kotlin
Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
) {
    Column(modifier = Modifier.widthIn(max = 840.dp)) {
        // Reader content / WebView
    }
}
```

This prevents unreadably long lines on wide screens.

### Labels Sheet Adaptation

- Compact/Medium: `ModalBottomSheet`
- Expanded: `ModalSideSheet` (or a standard `ModalBottomSheet` — side sheets are not yet stable in M3 Compose, so evaluate at implementation time)

---

## G) Deliverables

### Phase Summary Table

| Phase | Name | Effort | Risk | Dependencies | Key Output |
|:---|:---|:---|:---|:---|:---|
| **0** | Foundation & Design Tokens | Low | Minimal | None | `Dimens.kt`, adaptive deps |
| **1** | Navigation Architecture Refactor | Medium | **High** | Phase 0 | `AppShell`, drawer lifted |
| **2** | M3 Component Remediation | Medium | Low-Med | Phase 1 | `ListItem` adoption, card contrast |
| **3** | Labels → Filter State | Medium | Medium | Phases 1, 2 | `LabelsBottomSheet`, no labels screen |
| **4** | Unified Filtering System | Medium-High | Medium | Phase 3 | `FilterBar`, `FilterPanel` |
| **5** | Tablet / Adaptive Layout | **High** | **High** | Phases 1, 4 | Adaptive navigation, list/detail pane |
| **6** | Polish & Transitions | Low | Low | Phase 1 | Transitions, haptics, shimmer |

### Recommended Reusable UI Components

| Component | Phase | Description |
|:---|:---|:---|
| `AppShell` | 1 | Top-level layout: navigation + scaffold + content |
| `AppDrawerContent` | 1 | Shared drawer menu content |
| `LabelsBottomSheet` | 3 | Searchable label list in a modal sheet |
| `FilterBar` | 4 | Horizontal row of active filter chips |
| `FilterPanel` | 4 | Expandable filter controls surface |
| `FilterChipGroup` | 4 | Reusable row of FilterChips for a dimension |
| `AdaptiveNavigation` | 5 | Drawer/Rail/PermanentDrawer switcher |
| `ListDetailLayout` | 5 | ListDetailPaneScaffold wrapper |
| `AppNavigationRailContent` | 5 | Rail icon layout for medium width |

### Quick Wins vs Large Refactors

**Quick Wins (can be done in a day each):**
- [ ] Edge-to-edge status bar transparency
- [ ] Haptic feedback on bookmark actions
- [ ] `contentPadding` on Settings LazyColumn
- [ ] Favicon-title spacing fix (8dp → 12dp)
- [ ] IconButton `onClickLabel` for TalkBack
- [ ] `Dimens.kt` creation
- [ ] Mosaic card contrast fix (gradient → Surface scrim)

**Medium Refactors (2-3 days each):**
- [ ] Settings screens → `ListItem` adoption
- [ ] Labels bottom sheet (Phase 3)
- [ ] FilterBar + FilterPanel (Phase 4)
- [ ] Navigation transitions

**Large Refactors (1+ weeks each):**
- [ ] Navigation architecture lift (Phase 1) — highest priority, highest risk
- [ ] Tablet adaptive layout (Phase 5) — highest complexity
- [ ] ListDetailPaneScaffold integration — requires careful navigation rethinking
