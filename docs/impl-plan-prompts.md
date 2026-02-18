 Summary

  ┌─────────────────────────┬───────────────────────┬──────────────────────────────────────────────────┐
  │          Phase          │   Recommended Model   │                    Rationale                     │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 0 — Design Tokens       │ Haiku                 │ Trivial, mechanical                              │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 1 — Navigation Refactor │ Opus                  │ High-risk architecture, subtle interdependencies │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 2 — M3 Remediation      │ Sonnet                │ Localized replacements, clear specs              │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 3 — Labels Migration    │ Sonnet                │ Design decided, moderate refactor                │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 4 — Filtering System    │ Sonnet (Opus for DAO) │ Mostly net-new UI, some ViewModel complexity     │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 5 — Tablet Layout       │ Opus                  │ Complex adaptive navigation, dual pane logic     │
  ├─────────────────────────┼───────────────────────┼──────────────────────────────────────────────────┤
  │ 6 — Polish              │ Sonnet                │ Independent low-risk changes                     │
  └─────────────────────────┴───────────────────────┴──────────────────────────────────────────────────┘

Generic Prompt (Phases 0, 2, 3, 4, 6)

Implement Phase [2]: [M3 Remediation] from @docs/M3-and-Related-Features-Impl-Roadmap.md

Follow CLAUDE.md and Android/Jetpack Compose best practices. Do only what the roadmap calls for — no additional refactoring, no speculative improvements.
Follow existing code patterns and conventions in the repo.

Work through the scope items methodically. After each file change, verify it compiles. When done, confirm all acceptance criteria from the roadmap are met.


  ---
Phase 1: Navigation Architecture Refactor (Opus)

This one needs guardrails because of the regression risk and because the model needs to understand what not to change.

Implement Phase 1: Navigation Architecture Refactor from @docs/M3-and-Related-Features-Impl-Roadmap.md

Follow CLAUDE.md and Android/Jetpack Compose best practices.

Critical constraints:
- This is a STRUCTURAL refactor only. Do not change any ViewModel logic, route
definitions, or screen-level business logic.
- Do not change how any screen looks or behaves. The only user-visible change
should be the elimination of white flashes between screen transitions (because
the themed Scaffold now persists).
- Preserve all existing navigation: deep links, share intent flow, back stack
behavior, drawer item actions.
- The drawer content you extract into AppDrawerContent must remain functionally
identical — same items, same callbacks, same visual appearance.

Approach:
1. Read BookmarkListScreen.kt thoroughly first to understand everything the
    drawer and scaffold currently own (state, callbacks, top bar config).
2. Create the AppShell and AppDrawerContent composables.
3. Migrate the drawer/scaffold out of BookmarkListScreen into AppShell.
4. Ensure the NavHost sits inside the AppShell scaffold content area.
5. Verify every route destination still works by tracing the navigation calls.

Do only what the roadmap calls for. When done, confirm all Phase 1 acceptance
criteria from the roadmap are met.


  ---
  Phase 5: Tablet / Adaptive Layout (Opus)

  This one needs specific guidance because of the dual navigation model and the pane interaction complexity.

  Implement Phase 5: Tablet / Adaptive Layout from @docs/M3-and-Related-Features-Impl-Roadmap.md

  Follow CLAUDE.md and Android/Jetpack Compose best practices.

  Critical constraints:
  - Phone behavior (compact size class) must remain UNCHANGED. Do not break the
    existing phone UX to accommodate tablet.
  - The compact path should continue using NavHost push navigation for detail
    views. Only expanded introduces pane-based navigation via ListDetailPaneScaffold.
  - User typography settings (text width %, font size, line spacing) must compose
    correctly WITHIN the adaptive widthIn(max) constraints, not fight them. The
    outer container sets the max; the user's text width % operates within that.

  Approach:
  1. Start by reading AppShell.kt (from Phase 1), BookmarkListScreen.kt,
     BookmarkDetailScreen.kt, and BookmarkDetailViewModel.kt to understand
     current navigation and state flow.
  2. Add WindowSizeClass calculation in MainActivity and thread it through AppShell.
  3. Implement AdaptiveNavigation that swaps Modal/Rail/Permanent based on size class.
  4. Implement ListDetailPaneScaffold for expanded — use its built-in navigator,
     NOT the NavHost, for detail pane updates on expanded.
  5. Add reader width constraints to BookmarkDetailScreen.
  6. Adapt LabelsBottomSheet to use side sheet on expanded if the M3 Compose API
     supports it; otherwise keep ModalBottomSheet.
  7. Test the following matrix mentally and note any issues:
     - Compact portrait: unchanged behavior
     - Compact landscape: unchanged behavior
     - Medium portrait: NavigationRail, single pane
     - Medium landscape: NavigationRail, single pane
     - Expanded portrait: PermanentDrawer, list+detail panes
     - Expanded landscape: PermanentDrawer, list+detail panes

  Do only what the roadmap calls for. When done, confirm all Phase 5 acceptance
  criteria from the roadmap are met.


  ---
  That gives you three prompts total — one generic for five phases, and one specific each for Phases 1 and 5. The generic prompt is intentionally short; the
  roadmap document itself has all the detail the model needs for those phases.

  ---
  Revised prompts for 3/4:
  ---
Phase 3 has already had a first pass implemented. This session addresses the remaining items and corrections. Read the current state of each file before making changes.

Remaining work:

1. **DAO Bug Fix (critical):** In BookmarkDao.kt, the label filter query has
    two bugs. Labels are stored as JSON arrays (e.g., ["AI","email","Nature"]).
    - Substring match: `LIKE '%ai%'` incorrectly matches "email". Fix by
    wrapping the arg in JSON quotes: `%"$it"%`
    - Case insensitive: SQLite LIKE is case-insensitive for ASCII. Fix by
    adding `COLLATE BINARY` to the label filter clause.
    - Apply this fix to BOTH label filter queries (the main list query and
    the search query, around lines 308 and 439).
    - Do NOT change the label search within LabelsBottomSheet — that should
    remain case-insensitive.

2. **Cross-cutting label mode:** When a label is selected (from the
    LabelsBottomSheet or from tapping a label chip on a card), it should show
    ALL bookmarks with that label regardless of archive/favorite status.
    Currently it filters within the current list view. The ViewModel should
    clear/ignore the status filter (isArchived, isFavorite) when in label mode.
    Label counts in the sheet should reflect all bookmarks, not just the
    current list.

3. **Verify acceptance criteria** from the Phase 3 section of the roadmap.
    All unchecked items must be confirmed working.

Follow CLAUDE.md and Android/Jetpack Compose best practices. Do only what is
called for — no additional refactoring, no speculative improvements. Follow
existing code patterns and conventions in the repo.


Phase 4a — Opus

Implement Phase 4a (core filter system) from @docs/M3-and-Related-Features-Impl-Roadmap.md

This is the first sub-phase of Phase 4: Unified Filtering System. Read the
full Phase 4 section carefully before starting — it contains the complete
specification including the conceptual model, filter form fields, state
representation, and DAO changes.

Phase 4a scope:

1. **New drawer items:** Add Articles, Videos, Pictures to AppDrawerContent
    below Favorites, with separators between groups as specified in the roadmap.
    Each item is a preset that sets a specific filter and shows the bookmark list.

2. **Replace search bar with filter icon:** Remove the existing top-bar search
    bar. Add a filter icon (funnel/tune icon) to the TopAppBar that opens the
    FilterBottomSheet.

3. **FilterBottomSheet — core fields only:** Create a ModalBottomSheet with:
    - Search (full text) — OutlinedTextField with trailing X clear icon
    - Type — FilterChip row: Article / Video / Picture (multi-select)
    - Progress — FilterChip row: Unviewed / In-progress / Completed (multi-select,
    maps to readProgress: 0%, 1-99%, 100%)
    - Is Favorite — tri-state control (null/Yes/No)
    - Is Archived — tri-state control (null/Yes/No)
    - "Search" button at bottom to apply and dismiss
    - "Reset" button (visible when any filter is active) to clear ALL filters
    including contextual preset defaults

4. **Drawer preset model:** Implement DrawerPreset enum. When a drawer item is
    selected, pre-populate the FilterFormState accordingly (e.g., My List sets
    isArchived=false, Articles sets types={ARTICLE}). When the filter form opens,
    it should reflect these preset values. Reset clears everything.

5. **FilterFormState:** Implement the new state model in BookmarkListViewModel.
    Replace the existing FilterState and search state with FilterFormState +
    DrawerPreset as specified in the roadmap.

6. **DAO expansion:** Update BookmarkDao to support:
    - Multi-type filtering (type IN (?, ?) instead of single type = ?)
    - Progress filtering (readProgress ranges for unviewed/in-progress/completed)
    - Full-text search across title, labels, siteName, authors

7. **Label mode coexistence:** The filter icon should NOT appear when in label
    mode (Phase 3). Label mode and filter mode are mutually exclusive — label
    mode is a cross-cutting selection, filter mode operates within a drawer preset.

Critical constraints:
- Do NOT implement the extended fields yet (title, author, site, label-in-filter,
dates, is-loaded, with-labels, with-errors) — those are Phase 4b.
- Do NOT implement the FilterBar summary chips yet — that is Phase 4b.
- Do NOT change label mode behavior (Phase 3) — only ensure it coexists
correctly with the new filter system.
- All text fields must have a trailing X icon when populated for quick clearing.
- The tri-state control for boolean fields should cycle: null → Yes → No → null
(or use a SegmentedButtonRow with three options: —/Yes/No).

Follow CLAUDE.md and Android/Jetpack Compose best practices. Do only what is
called for — no additional refactoring, no speculative improvements. Follow
existing code patterns and conventions in the repo.

Work through each item methodically. After each file change, verify it
compiles. When done, confirm Phase 4 acceptance criteria relevant to 4a scope
are met.


Phase 4b — Sonnet

Implement Phase 4b (extended filter fields + FilterBar) from @docs/M3-and-Related-Features-Impl-Roadmap.md

This is the second sub-phase of Phase 4. Phase 4a has already been implemented —
the FilterBottomSheet, DrawerPreset model, FilterFormState, core filter fields,
and DAO expansion are in place. Read the current state of each file before
making changes.

Phase 4b scope:

1. **Extended filter form fields:** Add to the existing FilterBottomSheet:
    - Title — OutlinedTextField with trailing X clear icon
    - Author — OutlinedTextField with trailing X clear icon
    - Site — OutlinedTextField with trailing X clear icon
    - Label (within filter) — read-only OutlinedTextField, tappable, opens
    LabelsBottomSheet for single label selection. This is filtering within
    the current view, distinct from the drawer Labels cross-cutting mode.
    - From Date / To Date — OutlinedTextField with DatePickerDialog on tap,
    filters on published date
    - Is Loaded — tri-state (null/Yes/No), maps to contentState
    (DOWNLOADED vs NOT_ATTEMPTED)
    - With Labels — tri-state, maps to labels != '[]' / labels = '[]'
    - With Errors — tri-state, maps to state or contentState error values

2. **FilterBar summary:** Add a FilterBar composable below the TopAppBar:
    - LazyRow of InputChip elements, one per active non-default filter
    - Each chip shows filter name and value (e.g., "Type: Article", "Author: smith")
    - Each chip has trailing X dismiss icon to remove that specific filter
    - Tapping the FilterBar area opens the FilterBottomSheet for editing
    - Only visible when any filter is active beyond drawer preset defaults
    - Not visible in label mode (Phase 3)

3. **DAO expansion:** Update BookmarkDao to support the new fields:
    - title LIKE ? COLLATE NOCASE for title search
    - authors LIKE ? COLLATE NOCASE for author search
    - siteName LIKE ? COLLATE NOCASE for site search
    - Label filter within the filter form (use the same COLLATE BINARY exact
    match pattern from Phase 3)
    - published >= ? and published <= ? for date range
    - contentState filtering for Is Loaded
    - state/contentState filtering for With Errors
    - labels != '[]' / labels = '[]' for With Labels

Follow CLAUDE.md and Android/Jetpack Compose best practices. Do only what is
called for — no additional refactoring, no speculative improvements. Follow
existing code patterns and conventions in the repo.

Work through each item methodically. After each file change, verify it
compiles. When done, confirm all remaining Phase 4 acceptance criteria from
  the roadmap are met.