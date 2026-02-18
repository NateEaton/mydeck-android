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

### Phase 3: Labels → Cross-Cutting Selection Mode

**Goals:** Remove Labels as a pseudo-navigation destination. Convert it to a cross-cutting selection mode surfaced via a Bottom Sheet. Labels mode shows all bookmarks with a given label regardless of archive/favorite status — it is NOT a filter within the current list view.

**Scope:**
- Remove `viewingLabelsList` boolean from `FilterState`
- Remove the labels list view mode from `BookmarkListScreen`
- Create `LabelsBottomSheet` composable with searchable label list
- Drawer "Labels" item now opens the sheet instead of switching view mode
- Selecting a label enters "label mode" — shows ALL bookmarks with that label (ignoring status filters)
- Tapping a label chip on a bookmark card in the list does the same
- Label management (rename/delete) available via both the sheet (long-press) AND the label mode TopAppBar overflow menu
- Label counts in the sheet always reflect all bookmarks (not scoped to current list)

#### Label Mode TopAppBar

When in label mode, the TopAppBar should be configured as follows:

**Title area:**
- Inline `Icons.Outlined.Label` (or `Icons.Outlined.Tag`) at 18-20dp, decorative (no `IconButton` wrapper), same color as title text (`onSurface`)
- 8dp gap, then the label name as title text
- The icon is NOT an action — it's a visual indicator that this is a label-filtered view

**Action icons (trailing):**
- **Sort icon:** Use an up/down arrow icon (like Google Keep) instead of the generic sort icon. Tapping opens a **dropdown menu** anchored to the icon with all sort options as single-select items. A dropdown is more appropriate than a bottom sheet for a simple single-select list.
- **Layout toggle icon:** Icon reflects the currently selected layout (grid/compact/mosaic). Tapping opens a **dropdown menu** with all three layout options, each with its representative icon. This is clearer than cycling through options when there are 3+ choices.
- **Overflow menu (3-dot):** Contains:
  - "Rename Label" — opens an `AlertDialog` with an `OutlinedTextField` for editing the label name
  - "Delete Label" — opens a confirmation `AlertDialog` with text like "Are you sure you want to delete the label [xyz]? It will be removed from all bookmarks." with Cancel/Delete buttons

**No filter icon in label mode.** This aligns with Readeck where label views have no filter form — only sort and card type selection. The existing search icon/bar should also NOT appear in label mode.

#### Rename Label Dialog

- `AlertDialog` with title "Rename Label"
- `OutlinedTextField` pre-populated with the current label name
- Cancel / Rename buttons
- Same dialog pattern as other dialogs in the app

#### Delete Label Dialog

- Fix the existing dialog text: currently says "Label [xyz] deleted" (past tense before action)
- Change to: "Are you sure you want to delete the label [xyz]? It will be removed from all bookmarks."
- Cancel / Delete buttons
- Note on delete consistency: label delete uses a confirmation dialog (appropriate for broad-impact actions affecting multiple bookmarks). Bookmark delete uses a snackbar with undo (appropriate for single-item recoverable actions). This is intentional — severity determines the pattern.

#### Sort and Layout Controls (applies to all list views, not just label mode)

These TopAppBar action changes should apply consistently across all list views (My List, Archive, Favorites, and label mode):

- **Sort icon:** Replace current sort icon with up/down arrow icon (similar to Keep). Tap opens dropdown menu with sort options.
- **Layout toggle:** Replace current layout toggle with an icon reflecting current layout. Tap opens dropdown menu with Grid/Compact/Mosaic options (each with representative icon).

#### DAO Bug Fix: Label Filtering (Case Sensitivity + Exact Match)

The current label filter query in `BookmarkDao.kt` has two bugs:
- **Substring matching:** `LIKE '%ai%'` matches "email" because "ai" is a substring
- **Case insensitive:** SQLite `LIKE` is case-insensitive for ASCII, so "ai" matches "AI"

Labels are stored as JSON arrays (e.g., `["AI","email","Nature"]`). Fix both queries (line ~308 and ~439):
- Change `AND labels LIKE ?` to `AND labels LIKE ? COLLATE BINARY`
- Change the arg from `%$it%` to `%"$it"%` (wrap in JSON quotes for exact match)

This ensures `"AI"` matches only `"AI"` in the JSON, not `"email"` or `"ai"`.

The **label search within the LabelsBottomSheet** should remain case-insensitive (searching for "nat" should find "Nature"). This is a local filter on the label list UI, not the DAO query.

**Files impacted:**
- `BookmarkListViewModel.kt` — remove `viewingLabelsList`, `onClickLabelsView`; add label mode state; label selection clears status filter and shows all bookmarks with that label
- `BookmarkListScreen.kt` — remove labels list rendering, add LabelsBottomSheet integration; label mode TopAppBar configuration (label icon + title, overflow menu, no search/filter icon)
- `BookmarkListComponents.kt` — sort icon change (up/down arrow), layout toggle change (dropdown menu), apply to all list views
- `AppDrawerContent.kt` — "Labels" item triggers sheet
- `BookmarkDao.kt` — fix label filter queries (case sensitivity + exact match)
- New: `ui/list/LabelsBottomSheet.kt`

**New components:**
- `LabelsBottomSheet` — `ModalBottomSheet` containing:
  - Search bar (M3 `SearchBar` or `DockedSearchBar`)
  - `LazyColumn` of `ListItem` rows (icon, label name, count badge)
  - Long-press context menu for edit/delete

**Refactor vs new:**
- ~50% refactor (removing old label list code, TopAppBar reconfiguration)
- ~50% new (LabelsBottomSheet, label mode overflow menu, sort/layout dropdowns)

**Risks:**
- Users who relied on the labels list as a "screen" will experience a UX change. This is intentional and an improvement.
- Label rename/delete dialogs must still work — they're being re-hosted, not rewritten. The rename dialog is new (AlertDialog with OutlinedTextField). The delete dialog exists but its text needs fixing.
- The label mode (cross-cutting, ignoring status) is different from filtering within a view. The ViewModel must clearly distinguish these two states.
- Sort and layout dropdown changes affect ALL list views, not just label mode. Test across My List, Archive, Favorites, and label mode.
- **Mitigation:** Keep the existing rename/delete dialog composables. Only change *where* they're triggered from and fix the delete dialog text.

**Acceptance criteria:**
- [x] "Labels" in drawer opens a bottom sheet, not a list view
- [x] Sheet shows all labels with counts (counts reflect ALL bookmarks, not scoped to current list)
- [x] Label search within the sheet is case-insensitive
- [x] Selecting a label dismisses sheet and shows ALL bookmarks with that label (cross-cutting, ignoring My List/Archive/Favorites status)
- [x] Label filtering is case-sensitive ("AI" ≠ "ai")
- [x] Label filtering is exact-match ("ai" does NOT match "email")
- [x] Long-press on a label in the sheet opens edit/delete options
- [x] Tapping a label chip on a card enters label mode for that label (same cross-cutting behavior)
- [x] `viewingLabelsList` is fully removed from codebase
- [x] The sub-heading label bug is gone (no more label state leaking)
- [x] When in label mode, the page title or FilterBar clearly indicates which label is active
- [x] Dismissing the label filter returns to the previously selected list view (My List/Archive/Favorites)
- [ ] Label mode TopAppBar shows inline label icon (18-20dp, decorative) + label name as title
- [ ] Label mode TopAppBar has overflow menu with "Rename Label" and "Delete Label"
- [ ] Rename Label opens AlertDialog with OutlinedTextField pre-populated with current name
- [ ] Delete Label dialog text says "Are you sure you want to delete the label [xyz]? It will be removed from all bookmarks." (not past tense)
- [ ] No search icon or filter icon shown in label mode
- [ ] Sort icon is up/down arrow (like Keep) across all list views; tap opens dropdown menu with sort options
- [ ] Layout toggle icon reflects current layout; tap opens dropdown menu with Grid/Compact/Mosaic options
- [ ] Sort and layout dropdown menus work consistently in My List, Archive, Favorites, and label mode

---

### Phase 4: Unified Filtering System

**Goals:** Implement a Readeck-aligned filtering system with a contextual filter form, new drawer preset items, and a persistent summary bar for active filters. Replace the existing top-bar search with a filter icon that opens the filter form.

#### Conceptual Model

**Drawer items are preset filter configurations.** Each drawer item pre-populates the filter form with specific defaults:

| Drawer Item | Pre-populated Filter Defaults |
|:---|:---|
| **My List** | Is Archived = No (all others null) |
| **Archive** | Is Archived = Yes |
| **Favorites** | Is Favorite = Yes |
| **Articles** (new) | Type = Article |
| **Videos** (new) | Type = Video |
| **Pictures** (new) | Type = Picture |

**Labels mode (Phase 3) is separate from the filter system.** When viewing by label, there is no filter form — only sort and card type selection. The filter icon does not appear in label mode.

**The filter form is contextual.** When opened, it reflects the current drawer preset's defaults. The user can modify any field. "Reset" clears all fields, including the contextual ones (e.g., resetting while in Archive clears Is Archived = Yes).

#### Drawer Changes

Add new drawer items below Favorites, with separators:
```
My List
Archive
Favorites
─────────────
Articles
Videos
Pictures
─────────────
Labels
─────────────
Settings
About
```

Each new item (Articles/Videos/Pictures) behaves like Favorites — it's a preset that sets a specific filter and shows the bookmark list.

#### Filter Form — Bottom Sheet

Replace the existing top-bar search icon/bar with a **filter icon** (funnel) that opens a `ModalBottomSheet` containing the filter form.

**Filter form fields:**

| Field | Type | M3 Component | Notes |
|:---|:---|:---|:---|
| **Search** | Free text | `OutlinedTextField` with trailing clear (X) icon | Full-text search across title, labels, siteName, (and article content if feasible) |
| **Title** | Free text | `OutlinedTextField` with trailing clear (X) icon | Searches within title only |
| **Author** | Free text | `OutlinedTextField` with trailing clear (X) icon | Searches within authors |
| **Site** | Free text | `OutlinedTextField` with trailing clear (X) icon | Searches within siteName |
| **Label** | Picker | `OutlinedTextField` (read-only, tappable) that opens `LabelsBottomSheet` | Single label selection within filter context; distinct from the drawer Labels cross-cutting mode |
| **From Date** | Date | `OutlinedTextField` with `DatePickerDialog` on tap | Published date lower bound |
| **To Date** | Date | `OutlinedTextField` with `DatePickerDialog` on tap | Published date upper bound |
| **Type** | Multi-select | `FilterChip` row: Article / Video / Picture | |
| **Progress** | Multi-select | `FilterChip` row: Unviewed / In-progress / Completed | Maps to readProgress: 0%, 1-99%, 100% |
| **Is Favorite** | Tri-state | `TriStateToggle` or `SegmentedButtonRow`: — / Yes / No | Null = not filtering |
| **Is Archived** | Tri-state | Same pattern | |
| **Is Loaded** | Tri-state | Same pattern | Maps to contentState (DOWNLOADED vs NOT_ATTEMPTED) |
| **With Labels** | Tri-state | Same pattern | Has any labels vs no labels |
| **With Errors** | Tri-state | Same pattern | Maps to state or contentState error states |

**All text fields** should show a trailing X icon when they contain text, allowing quick clearing of individual fields.

**Form actions (bottom of sheet):**
- **Search** button — applies the filter and dismisses the sheet
- **Reset** button (visible when any filter is active) — clears ALL filter fields including contextual presets, resets to unfiltered "all bookmarks" state
- **Create Collection** button (future feature, not in this phase — reserve space in the design)

#### FilterBar (Summary)

Below the TopAppBar, a persistent summary bar showing active non-default filters:
- `LazyRow` of `InputChip` elements, one per active filter
- Each chip shows the filter name and value (e.g., `[x] Type: Article`, `[x] Author: smith`)
- Trailing X on each chip dismisses that individual filter
- Tapping the FilterBar area opens the filter bottom sheet for editing

The FilterBar is visible when any filter is active beyond the drawer preset defaults. If the user is on "My List" and hasn't modified any filters, no FilterBar is shown.

#### State Representation

```kotlin
data class FilterFormState(
    val search: String? = null,              // full-text search
    val title: String? = null,               // title search
    val author: String? = null,              // author search
    val site: String? = null,                // site search
    val label: String? = null,               // label filter (within filter form, not label mode)
    val fromDate: Instant? = null,           // published date lower bound
    val toDate: Instant? = null,             // published date upper bound
    val types: Set<BookmarkType> = emptySet(), // empty = all types
    val progress: Set<ProgressFilter> = emptySet(), // UNVIEWED, IN_PROGRESS, COMPLETED
    val isFavorite: Boolean? = null,         // null = don't filter, true/false = filter
    val isArchived: Boolean? = null,         // null = don't filter, true/false = filter
    val isLoaded: Boolean? = null,           // null = don't filter, true/false = filter
    val withLabels: Boolean? = null,         // null = don't filter, true/false = filter
    val withErrors: Boolean? = null,         // null = don't filter, true/false = filter
)

enum class ProgressFilter { UNVIEWED, IN_PROGRESS, COMPLETED }

// The active drawer preset determines the initial filter defaults
enum class DrawerPreset {
    MY_LIST,     // isArchived = false
    ARCHIVE,     // isArchived = true
    FAVORITES,   // isFavorite = true
    ARTICLES,    // types = {ARTICLE}
    VIDEOS,      // types = {VIDEO}
    PICTURES,    // types = {PICTURE}
}
```

- Filter state lives in ViewModel as `MutableStateFlow<FilterFormState>`
- The `DrawerPreset` determines initial values; user modifications overlay on top
- "Reset" clears everything back to an unfiltered state (even the preset defaults)
- Sort remains separate (not part of the filter form, controlled by sort icon/menu)

#### DAO Changes

`BookmarkDao.kt` needs significant expansion to support the new filter fields:
- Add `title LIKE ?`, `authors LIKE ?`, `siteName LIKE ?` filter clauses
- Add `published >= ?` and `published <= ?` date range clauses
- Add `readProgress` range filtering for Progress (0 for unviewed, 1-99 for in-progress, 100 for completed)
- Add multi-type support (`type IN (?, ?)` instead of single `type = ?`)
- Add `contentState` filtering for Is Loaded
- Add `state` filtering for With Errors
- Add `labels != '[]'` / `labels = '[]'` for With Labels
- Full-text search field should search across title, labels, siteName, authors (and article content if feasible — may require a JOIN to ArticleContentEntity)

**Files impacted:**
- `BookmarkListViewModel.kt` — replace existing `FilterState` with `FilterFormState` + `DrawerPreset`; remove existing search state; add filter form open/close state
- `BookmarkListScreen.kt` — remove search bar; add filter icon in TopAppBar; integrate FilterBar and FilterBottomSheet
- `BookmarkDao.kt` — expand dynamic query builder to support all new filter fields
- `BookmarkRepository.kt` / `BookmarkRepositoryImpl.kt` — update observe methods to accept `FilterFormState`
- `AppDrawerContent.kt` — add Articles, Videos, Pictures drawer items with separators
- New: `ui/components/FilterBar.kt` — summary bar of active filter chips
- New: `ui/components/FilterBottomSheet.kt` — filter form in a ModalBottomSheet
- New: `ui/components/TriStateToggle.kt` — reusable null/Yes/No control (or SegmentedButtonRow helper)

**New components:**
- `FilterBar` — `LazyRow` of active `InputChip` elements showing non-default filters
- `FilterBottomSheet` — `ModalBottomSheet` with the full filter form
- `TriStateToggle` — reusable component for null/Yes/No tri-state fields
- `ProgressFilter` enum — maps to readProgress thresholds

**Refactor vs new:**
- ~30% refactor (replacing existing search/filter state, DAO query expansion)
- ~70% new (FilterBottomSheet, FilterBar, TriStateToggle, new drawer items, DAO clauses)

**Risks:**
- **Scope:** This is significantly larger than originally planned. Consider splitting into sub-phases:
  - 4a: New drawer items + filter icon + basic FilterBottomSheet with core fields (search, type, progress, is-favorite, is-archived)
  - 4b: Remaining fields (title, author, site, label-in-filter, dates, is-loaded, with-labels, with-errors) + FilterBar summary
- **DAO complexity:** The dynamic query builder will grow substantially. Consider extracting it into a dedicated `BookmarkQueryBuilder` class for maintainability.
- **Label dual model:** The label field in the filter form (filter within current view) is distinct from the drawer Labels mode (cross-cutting). The implementation must keep these clearly separated in both state and UI.
- **Full-text search:** Searching within article content requires joining `ArticleContentEntity`. This may have performance implications on large collections — consider making it optional or async.
- **Mitigation:** Implement sub-phase 4a first and validate the pattern before expanding to 4b.

**Acceptance criteria:**
- [ ] New drawer items: Articles, Videos, Pictures with separators and correct preset filters
- [ ] Filter icon (funnel) in TopAppBar replaces the existing search bar
- [ ] Filter icon opens FilterBottomSheet
- [ ] Filter form pre-populates based on active drawer preset
- [ ] All text fields have trailing X clear button when populated
- [ ] Type chips (Article/Video/Picture) work as multi-select `FilterChip` elements
- [ ] Progress chips (Unviewed/In-progress/Completed) work correctly and map to readProgress thresholds
- [ ] Tri-state fields (Is Favorite, Is Archived, Is Loaded, With Labels, With Errors) cycle through null/Yes/No
- [ ] Date pickers work for From/To published date
- [ ] "Search" button applies filter and dismisses sheet
- [ ] "Reset" button clears ALL filters including contextual preset defaults
- [ ] FilterBar shows below TopAppBar when any non-default filter is active
- [ ] Each FilterBar chip is dismissible and removes that specific filter
- [ ] Filter form label field opens LabelsBottomSheet for selection (this is filter-within-view, distinct from drawer Labels mode)
- [ ] Filter icon does NOT appear in label mode (Phase 3 cross-cutting label selection)
- [ ] Sort remains independent of the filter form (separate control)
- [ ] DAO correctly handles all new filter clauses including multi-type, date range, progress range, and content state

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
- [x] Screen transitions animate smoothly (300ms slide)
- [x] Haptic feedback on bookmark actions (archive, delete, favorite)
- [x] Empty states follow M3 pattern
- [x] Status bar is transparent (edge-to-edge)

---

## D) Labels Feature Replacement Proposal

**Decision: Labels are a Cross-Cutting Selection Mode, not a Navigation Destination or a simple Filter.**

### Dual Label Model

Labels appear in two distinct contexts with different behaviors:

1. **Label Mode (Phase 3 — drawer "Labels" item or tapping a label chip on a card):**
   - Shows ALL bookmarks with that label, regardless of archive/favorite/status
   - Cross-cutting: ignores the current list view context
   - No filter form available in this mode (matches Readeck)
   - Sort and card type selection still available
   - This is conceptually similar to Readeck's Labels, Highlights, and Collections — they are distinct selections, not filters within a view

2. **Label as a filter field (Phase 4 — within the filter form):**
   - Filters within the current drawer preset view (e.g., "show me archived bookmarks with label 'nature'")
   - One of 14+ filter fields in the filter form
   - Composable with other filters (type, progress, date range, etc.)

### Justification

1. **Eliminates the state bug:** No separate "labels screen" means no stale sub-heading when switching back.
2. **Readeck alignment:** Matches Readeck's model where Labels are a distinct selection that cuts across all bookmarks, separate from the filter system.
3. **Tablet-ready:** A bottom/side sheet for label selection works on all form factors.
4. **Readeck familiarity preserved:** Users still see the searchable list of labels with counts — it's just in a sheet instead of a full screen.

### Phone Behavior
- Drawer → tap "Labels" → `ModalBottomSheet` slides up with searchable label list
- Select label → sheet dismisses → enters label mode showing ALL bookmarks with that label
- Label name shown in page title or FilterBar chip
- Dismiss label → returns to previously selected drawer preset (My List/Archive/etc.)

### Tablet Behavior
- PermanentDrawer → tap "Labels" → `ModalSideSheet` slides in from right
- Same interaction as phone, but spatially the sheet overlays the detail pane while the list remains visible

### Label Management
- Long-press a label in the sheet → context menu with "Rename" and "Delete"
- Same dialog composables as today, just triggered from the sheet instead of the list view

### Label Filtering Correctness
- Label filter matching is **case-sensitive** ("AI" ≠ "ai") — matches Readeck behavior
- Label filter matching is **exact-match** ("ai" does NOT match "email")
- Label **search within the sheet** is case-insensitive for usability

---

## E) List Filtering Feature Proposal

> **Note:** This section has been superseded by the detailed Phase 4 design in Section C above. The Phase 4 section contains the complete filter form specification, state representation, DAO changes, and acceptance criteria. Key differences from the original proposal:
> - Filter form is a **bottom sheet** (not an inline expandable panel) — matches Readeck's filter form approach
> - **14+ filter fields** including text search, title, author, site, label, date range, type, progress, and 5 tri-state boolean fields
> - **Drawer items are preset filter configurations** — My List, Archive, Favorites, Articles (new), Videos (new), Pictures (new)
> - **Label has dual behavior:** drawer Labels = cross-cutting selection mode (Phase 3); filter form Label = filter within current view (Phase 4)
> - **Existing search bar replaced** by filter icon (funnel) that opens the filter form
> - **"Reset" clears all filters** including contextual presets
> - **"Create Collection"** reserved as future feature (saved searches)

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
| **2** | M3 Component Remediation | Medium | Low-Med | Phase 1 | `ListItem` adoption, card redesigns |
| **3** | Labels → Cross-Cutting Selection | Medium | Medium | Phases 1, 2 | `LabelsBottomSheet`, label mode, DAO fix |
| **4** | Unified Filtering System | **High** | **High** | Phase 3 | `FilterBottomSheet`, `FilterBar`, new drawer items, DAO expansion |
| **4a** | Core Filter (sub-phase) | Medium-High | Medium | Phase 3 | New drawer items, filter icon, basic filter form |
| **4b** | Extended Filter (sub-phase) | Medium | Medium | Phase 4a | Remaining fields, FilterBar summary, full DAO |
| **5** | Tablet / Adaptive Layout | **High** | **High** | Phases 1, 4 | Adaptive navigation, list/detail pane |
| **6** | Polish & Transitions | Low | Low | Phase 1 | Transitions, haptics, shimmer |

### Recommended Reusable UI Components

| Component | Phase | Description |
|:---|:---|:---|
| `AppShell` | 1 | Top-level layout: navigation + scaffold + content |
| `AppDrawerContent` | 1 | Shared drawer menu content |
| `LabelsBottomSheet` | 3 | Searchable label list in a modal sheet (used in both label mode and filter form) |
| `FilterBottomSheet` | 4 | Full filter form in a ModalBottomSheet |
| `FilterBar` | 4 | Horizontal row of active non-default filter chips |
| `TriStateToggle` | 4 | Reusable null/Yes/No toggle for boolean filter fields |
| `AdaptiveNavigation` | 5 | Drawer/Rail/PermanentDrawer switcher |
| `ListDetailLayout` | 5 | ListDetailPaneScaffold wrapper |
| `AppNavigationRailContent` | 5 | Rail icon layout for medium width |

### Quick Wins vs Large Refactors

**Completed (Phases 0-2):**
- [x] `Dimens.kt` creation (Phase 0)
- [x] Navigation architecture lift (Phase 1)
- [x] Settings screens → `ListItem` adoption (Phase 2)
- [x] Mosaic card gradient/overlay redesign (Phase 2)
- [x] Label chip parity — Grid and Compact get chips, Mosaic has none (Phase 2)
- [x] Drawer typography and divider updates (Phase 2)
- [x] Theme inline controls and Sepia separation (Phase 2)

**Quick Wins (remaining):**
- [x] Edge-to-edge status bar transparency (Phase 6)
- [x] Haptic feedback on bookmark actions (Phase 6)
- [x] Navigation transitions (Phase 6)

**Medium Refactors:**
- [x] Labels bottom sheet + cross-cutting label mode + DAO bug fix (Phase 3)
- [ ] New drawer items: Articles, Videos, Pictures (Phase 4a)
- [ ] FilterBar summary chips (Phase 4b)

**Large Refactors:**
- [ ] Filter form bottom sheet + DAO expansion (Phase 4)
- [ ] Tablet adaptive layout + ListDetailPaneScaffold (Phase 5)

---

## H) Test Coverage Expansion — Required Phase of Work

### Background

During Phases 0–4b, several significant refactors were made to `BookmarkListViewModel` and `BookmarkRepositoryImpl` that left the test suite out of sync with the production code. The following issues were identified and partially resolved:

**Already Fixed (pre-Phase 5):**
- `BookmarkListViewModelTest` referenced `FilterState` / `filterState` (old API) — updated to `FilterFormState` / `filterFormState`
- `BookmarkListViewModelTest` mocked `observeBookmarkListItems()` — updated to `observeFilteredBookmarkListItems()` to match the refactored ViewModel
- Three search-related tests (`onSearchQueryChange`, `onClearSearch`, `searchBookmarkListItems is called`) referenced non-existent ViewModel methods and were removed; the underlying search functionality is now handled through `FilterFormState.search` via the filter form
- `onClickLabel` and `onRenameLabel` tests updated to use `activeLabel` instead of the removed `filterState.label`

### Remaining Test Coverage Gaps

#### `BookmarkRepositoryImpl` — Missing Method Coverage

The following public methods have **no test coverage** and should be addressed before Phase 5:

| Method | Complexity | Priority | Notes |
|:---|:---|:---|:---|
| `observeAllBookmarkCounts()` | Low | High | Flow testing with database entities |
| `observeAllLabelsWithCounts()` | Medium | High | String parsing + counting logic |
| `renameLabel()` | High | High | Bulk operations across multiple bookmarks |
| `deleteLabel()` | High | High | Label removal with server sync |
| `updateTitle()` | Low | Medium | Simple pending action queue |
| `observeFilteredBookmarkListItems()` | Medium | Medium | New DAO query with all filter params |

#### `BookmarkListViewModelTest` — Removed Tests Needing Replacement

Three search-related tests were removed because they referenced non-existent methods. The equivalent behavior is now handled through the filter form. Replacement tests should cover:

1. **Filter form search field applies correctly** — set `FilterFormState(search = "query")` via `onApplyFilter()`, verify `observeFilteredBookmarkListItems` is called with `searchQuery = "query"`
2. **Reset filter clears search** — apply a filter with search text, call `onResetFilter()`, verify `filterFormState.value.search == null`
3. **Filter form state flows correctly through to repository** — verify the full pipeline from `onApplyFilter()` → `filterFormState` → `observeFilteredBookmarkListItems()` args

### Recommended Implementation Approach

**Effort estimate:** 4–6 hours across 1–2 sessions

**Session 1 — Repository tests (3–4 hours):**
- `observeAllBookmarkCounts()`: mock `bookmarkDao.observeAllBookmarkCounts()` returning a flow, verify `BookmarkCounts` mapping
- `observeAllLabelsWithCounts()`: test JSON label string parsing (e.g., `["AI","nature"]` → `{"AI": 1, "nature": 1}`), edge cases (empty, null, duplicates)
- `renameLabel()`: mock `bookmarkDao.getAllBookmarksWithContent()`, verify `editBookmark` API calls and local DB updates
- `deleteLabel()`: similar pattern to rename

**Session 2 — ViewModel filter pipeline tests (1–2 hours):**
- Replace the 3 removed search tests with filter-form-based equivalents
- Add `onApplyFilter` → repository call verification
- Add `onResetFilter` state verification

### Risk Notes

- `renameLabel()` and `deleteLabel()` involve bulk operations with potential partial failures — test both full-success and partial-failure paths
- `observeAllLabelsWithCounts()` parsing logic is pure Kotlin (no DB/API) — straightforward to test but edge cases matter (empty JSON array `[]`, null labels field, duplicate label names across bookmarks)
- The `observeFilteredBookmarkListItems()` DAO query has 15 parameters — use `any()` matchers for the majority and only assert on the specific params under test
