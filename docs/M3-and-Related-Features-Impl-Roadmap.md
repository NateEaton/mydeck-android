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
- **Mosaic card redesign:** Revert to a gradient-over-image overlay approach but with improved contrast. Default state shows a compact bottom overlay (title only, 2 lines max ellipsized, plus favorite star icon) with a sharp gradient covering the bottom ~25% of the card. No label chips in Mosaic (matches Readeck). Full actions (archive, delete, share, labels) accessible via long-press context menu or bottom sheet — this replaces Readeck's hover-to-reveal pattern with a standard Android interaction. The gradient must be strong enough to pass WCAG AA contrast on bright images.
- Standardize drawer header typography
- Add `contentPadding` to Settings `LazyColumn` for gesture bar
- **Label chips per card variant:**
  - **Mosaic:** No label chips (matches Readeck, keeps cards image-dominant)
  - **Grid:** Add tappable label chips (cards have dedicated text areas with room)
  - **Compact:** Show all labels as chips (not a truncated "first + count" pattern)
- Quick wins from the M3 Review checklist (excluding haptic feedback and edge-to-edge, which are Phase 6 scope)

#### REVISION 2a: Mosaic Card — Restore Action Icons Over Image ✅ DONE

Implemented. Mosaic card now shows: 3-stop gradient (~90-100dp), title on top row, action icons (favorite, archive, view original, delete) on bottom row. No 3-dot overflow menu. No long-press dropdown. Open in Browser removed from Mosaic, Compact, and Grid card variants for consistency with Readeck.

#### REVISION 2b: Title Edit Icon Size ✅ DONE

Implemented. Edit icon is 24dp inside an `IconButton` with 48dp touch target.

#### REVISION 2c: Reading View Title Width — STILL NEEDED

**Problem:** The title in `BookmarkDetailHeader.kt` does not honor the user's text width setting. The typography controls apply text width via CSS (`body.style.maxWidth`) inside the WebView, but the title is a Compose `Text` composable rendered *outside* the WebView.

**Implementation direction:** The title and header content sit in the same scrollable `Column` as the `BookmarkDetailArticle` WebView in `BookmarkDetailScreen.kt`. The text width setting uses CSS viewport-relative units (`90vw` for Wide, `75vw` for Narrow) which don't translate directly to Compose.

The fix should:
1. In `BookmarkDetailScreen.kt`, wrap the header content (title, metadata, etc.) in a centered `Box` with `Modifier.fillMaxWidth()` + `Modifier.widthIn(max = ...)` that mirrors the text width setting
2. Map `TextWidth.WIDE` to `Modifier.fillMaxWidth(0.9f)` and `TextWidth.NARROW` to `Modifier.fillMaxWidth(0.75f)` — these are the Compose equivalents of the CSS `90vw`/`75vw` values
3. The `typographySettings` are already available in the `uiState` passed to `BookmarkDetailScreen`. Use `uiState.typographySettings.textWidth` to select the appropriate modifier
4. Apply this constraint to the header's outer container, NOT to individual text elements
5. Do NOT change anything about how the WebView applies text width — that CSS approach is correct for WebView content

#### REVISION 2d: Drawer Dividers — Lighter, Full Width ✅ DONE

Implemented.

#### REVISION 2e: Drawer & Settings Typography Sizing ✅ DONE

Implemented. Drawer typography updated to match Google Settings app sizing.

#### REVISION 2f: Theme Selection — Sepia Independence Fix — STILL NEEDED

The inline `SingleChoiceSegmentedButtonRow` (Light/Dark/System) is implemented. The Sepia toggle is implemented. However, the current Sepia logic has a critical limitation:

**Current behavior (broken):** Sepia is stored as `Theme.SEPIA` in the `Theme` enum — it replaces the theme mode rather than modifying it. The toggle only shows/works when Light is explicitly selected. When System Default is selected and resolves to light, Sepia does not apply.

**Required behavior:** Sepia should be an independent preference that applies whenever the *effective* theme is light, regardless of how it got there:
- User selects Light + Sepia ON → Sepia applies
- User selects System Default + Sepia ON → Sepia applies when system is in light mode, normal dark when system is in dark mode
- User selects Dark + Sepia ON → Sepia does NOT apply (dark overrides), but the preference is remembered so switching back to Light or System activates it

**Implementation direction:**
1. **Store Sepia as a separate boolean preference** in `SettingsDataStore`, not as a `Theme` enum value. Add a new `sepiaEnabled: Boolean` field (default `false`).
2. **Remove `Theme.SEPIA`** from the `Theme` enum. The enum should only contain `LIGHT`, `DARK`, `SYSTEM`.
3. **In `Theme.kt` (UI composition):** Resolve the effective theme by first determining light vs dark (from the theme mode + system setting), then checking `if (isLight && sepiaEnabled) SepiaColorScheme else ...`
4. **In `UiSettingsViewModel`:**
   - `themeMode` is always one of LIGHT/DARK/SYSTEM (no SEPIA)
   - `sepiaEnabled` is a separate StateFlow from `SettingsDataStore`
   - `onSepiaToggled(Boolean)` writes to the separate preference
   - `onThemeModeSelected(Theme)` writes only the mode
5. **In `UiSettingsScreen`:** The Sepia toggle should ALWAYS be visible and enabled regardless of which theme mode is selected. Its description should indicate it applies in light mode.
6. **In `MainViewModel`:** Collect both theme mode and sepia preference, resolve the effective theme to pass to the composable theme wrapper.
7. **Migration:** Handle users who currently have `Theme.SEPIA` stored — on first read, migrate to `theme=LIGHT` + `sepiaEnabled=true`.

**Files impacted:** `Theme.kt` (domain enum), `Theme.kt` (UI composition), `SettingsDataStore.kt`, `SettingsDataStoreImpl.kt`, `UiSettingsViewModel.kt`, `UiSettingsScreen.kt`, `MainViewModel.kt`

#### REVISION 2g: Reading View — Relocate Find in Article and Bookmark Detail Actions — NEW

**Current state:** Find in Article is in the overflow menu of the reading view top bar. Bookmark Detail (info icon) is a direct icon in the top bar.

**Required changes in `BookmarkDetailTopBar.kt` and/or `BookmarkDetailScreen.kt`:**
1. **Find in Article:** Move from overflow menu to a direct icon button in the top bar header (icon only, no label). Use `Icons.Outlined.Search` or equivalent. This makes search more discoverable and quicker to access.
2. **Bookmark Detail:** Move from direct top bar icon to the overflow menu. Place it between the Read/Unread item and the Delete item. Use the Info icon and "Bookmark Detail" label in the menu.

This swaps their positions — the more frequently used action (search) becomes a direct icon, while the less frequently used action (detail/info) moves to the overflow menu.

**Files impacted (remaining items only — completed items omitted):**
- `BookmarkCard.kt` — label chip additions to Grid/Compact variants
- `BookmarkDetailScreen.kt` — header width constraint wrapping (REV 2c)
- `BookmarkDetailHeader.kt` — title width constraint (REV 2c)
- `BookmarkDetailTopBar.kt` — swap Find in Article and Bookmark Detail positions (REV 2g)
- `UiSettingsViewModel.kt` — separate Sepia from theme mode state (REV 2f)
- `UiSettingsScreen.kt` — Sepia toggle always visible/enabled (REV 2f)
- `MainViewModel.kt` — resolve effective theme from mode + sepia preference (REV 2f)
- `SettingsDataStore.kt` — add `sepiaEnabled` boolean preference (REV 2f)
- `SettingsDataStoreImpl.kt` — implement `sepiaEnabled` storage (REV 2f)
- `Theme.kt` (domain enum) — remove `SEPIA` from `Theme` enum (REV 2f)
- `Theme.kt` (UI composition) — resolve sepia at composition time (REV 2f)

**New components:** None (using existing M3 components)

**Refactor vs new:** 100% refactor

**Risks:**
- Settings layout changes could shift element positions. Visual regression testing recommended.
- Mosaic card gradient/overlay redesign affects the most visible UI element.
- Sepia theme separation changes how theme state is stored — ensure no regressions for existing Sepia users (theme preference migration may be needed).
- **Mitigation:** Change one screen at a time. Test each independently.

**Acceptance criteria:**
- [x] All Settings screens use `ListItem` — verify 56dp/72dp min heights
- [x] Mosaic cards show 3-stop gradient (~90-100dp height) with title on top row and action icons (favorite, archive, view original, delete) on bottom row
- [x] Mosaic cards have NO label chips and NO long-press dropdown (actions are visible in overlay)
- [x] Open in Browser icon removed from Mosaic, Compact, and Grid card variants
- [x] TopAppBar remains left-aligned (not CenterAligned) on all screens
- [x] No custom `Row` layouts remain in Settings
- [x] Grid cards show tappable label chips
- [x] Compact cards show all labels as chips
- [x] Title edit icon is 24dp inside an `IconButton` (48dp touch target) (REV 2b)
- [x] Reading view title/header width honors user's text width setting — header uses `fillMaxWidth(0.9f)` for Wide, `fillMaxWidth(0.75f)` for Narrow, centered (REV 2c)
- [x] Drawer dividers are lighter (`outlineVariant` at 50% alpha) and full width (REV 2d)
- [x] Drawer item labels, badges, and section titles are appropriately sized per M3 defaults (REV 2e)
- [x] Settings item headlines use `bodyLarge`, supporting text uses `bodyMedium` (REV 2e)
- [x] Theme selection uses inline `SingleChoiceSegmentedButtonRow` (Light/Dark/System), no dialog (REV 2f)
- [x] Sepia is stored as a separate boolean preference, independent of theme mode (REV 2f)
- [x] Sepia applies when effective theme is light (whether from Light or System Default) (REV 2f)
- [x] Sepia toggle is always visible and enabled regardless of selected theme mode (REV 2f)
- [x] `Theme.SEPIA` removed from enum; migration handles existing users (REV 2f)
- [x] Find in Article is a direct icon button in reading view top bar (REV 2g)
- [x] Bookmark Detail is in overflow menu between Read/Unread and Delete (REV 2g)
- [x] Quick wins checklist items from M3 Review are complete (excluding haptics and edge-to-edge, which are Phase 6)

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
- [ ] Edge-to-edge status bar transparency (Phase 6)
- [ ] Haptic feedback on bookmark actions (Phase 6)
- [ ] `contentPadding` on Settings LazyColumn (Phase 2)
- [ ] Favicon-title spacing fix (8dp → 12dp) (Phase 2)
- [ ] IconButton `onClickLabel` for TalkBack (Phase 2)
- [ ] `Dimens.kt` creation (Phase 0)

**Medium Refactors (can be done in a day or two each):**
- [ ] Mosaic card gradient/overlay redesign — compact overlay with sharp gradient, long-press for actions (Phase 2)
- [ ] Label chip parity — Grid gets chips, Compact shows all labels, Mosaic has none (Phase 2)

**Medium Refactors (2-3 days each):**
- [ ] Settings screens → `ListItem` adoption
- [ ] Labels bottom sheet (Phase 3)
- [ ] FilterBar + FilterPanel (Phase 4)
- [ ] Navigation transitions

**Large Refactors (1+ weeks each):**
- [ ] Navigation architecture lift (Phase 1) — highest priority, highest risk
- [ ] Tablet adaptive layout (Phase 5) — highest complexity
- [ ] ListDetailPaneScaffold integration — requires careful navigation rethinking
