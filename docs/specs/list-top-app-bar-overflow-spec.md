# List View Top App Bar — Overflow Refactor Specification

Status: Implemented on `feat/multi-select-phase-1` (alongside multi-select
Phase 3). The selection-mode app bar introduced on that branch is left
untouched, as specified.

Date: 2026-06-01

## 1. Purpose

Reduce action-icon density in the bookmark-list top app bar by consolidating
infrequent actions into a 3-dot overflow menu, while keeping the highest-value
controls one tap away. Resolve the current label-mode UX gap where the
multi-select entry icon sits awkwardly to the right of the overflow.

Out of scope:

- Anything inside selection-mode (the multi-select spec governs that bar).
- Search surfacing changes (Search currently lives only inside the Filter
  sheet; this spec does not promote it).

## 2. Current State

Normal-mode bar (non-label), left to right after the drawer hamburger and
title:

1. **Layout** — `Icons.Filled.Apps` / `Icons.AutoMirrored.Filled.List` /
   `Icons.Filled.GridView` (icon reflects current mode). Opens a 3-item
   dropdown.
2. **Sort** — `Icons.Filled.SwapVert`. Opens a per-category dropdown with
   direction arrows.
3. **Filter** — `Icons.Filled.FilterList`. Opens the `FilterBottomSheet`.
4. **Multi-select** — `Icons.Filled.RadioButtonUnchecked`. Enters
   selection mode.

Label-mode variation:

- Filter is replaced by a 3-dot **Overflow** containing Rename label /
  Delete label.
- Multi-select **stays to the right of** the overflow icon, producing the
  unusual sequence Layout / Sort / Overflow / Multi-select. This is the
  current design gap.

## 3. Target Design

Normal-mode bar (non-label):

1. **Layout** — unchanged position, icon, behavior.
2. **Sort** — unchanged position, icon, behavior.
3. **Overflow** (`Icons.Filled.MoreVert`) — new in this position. Contains:
   - **Filter** (`Icons.Filled.FilterList`) — opens `FilterBottomSheet`.
   - **Select bookmarks** (`Icons.Filled.RadioButtonUnchecked`) — enters
     multi-select mode.

Label-mode variation:

1. **Layout** — unchanged.
2. **Sort** — unchanged.
3. **Overflow** (`Icons.Filled.MoreVert`) — contents in this order:
   - **Rename label** (icon: `Icons.Outlined.Edit` — see §6).
   - **Delete label** (icon: `Icons.Outlined.Delete` — see §6).
   - Visual divider.
   - **Select bookmarks** (`Icons.Filled.RadioButtonUnchecked`).
   - (No Filter item — label is itself the filter; matches current behavior
     of hiding Filter in label mode.)

The bar shape is identical between normal and label modes: drawer hamburger,
title, Layout, Sort, Overflow. Only the overflow contents differ. The
"multi-select-to-the-right-of-overflow" anti-pattern is eliminated.

## 4. Why These Choices

- **Layout and Sort stay on the bar** because their dropdowns make them
  effectively two-tap actions even from the bar, so any extra wrapping (e.g.
  Overflow → Layout → option) would be three taps for a routine choice.
  Their icons also reflect current state (current layout glyph, current sort
  direction arrow), so the bar slot doubles as a status indicator.
- **Filter moves to overflow** despite being a high-frequency action because
  it is two taps regardless of placement (the FilterBottomSheet is the second
  tap) and because the `FilterBar` chip surface below the app bar already
  surfaces the active filter state and provides a path to reopen the sheet
  by tapping a chip. Consolidating it into the overflow trades one tap of
  bar prominence for visual calm in the bar's steady state.
- **Multi-select moves to overflow** because (a) it is a
  low-to-medium-frequency action that does not need permanent bar real
  estate, and (b) long-press entry to selection mode is unavailable in
  MyDeck (long-press is already bound to the per-card context menu, mirroring
  Readeck). The overflow placement becomes the discoverable secondary entry
  rather than a primary affordance.
- **Rename / Delete label move to the top of the overflow** in label mode
  because they are label-scope actions and conceptually outrank generic list
  actions. Putting them above a divider, with Multi-select below, mirrors
  Android's "primary/destructive item, then list-scoped items" grouping
  pattern.

## 5. Behavioral Notes

- The overflow icon (`MoreVert`) is **always present** in both modes. This
  is a deliberate change from the current behavior, where the overflow
  appears only in label mode. Always-present keeps bar shape stable.
- Filter is **hidden in label mode** (unchanged behavior; the label acts as
  the filter).
- Existing `FilterBar` chip surface below the app bar is unaffected by this
  refactor. Chips remain the visible state of active filters and the
  one-tap-to-edit affordance.
- The selection-mode bar (X / count / favorite / archive / overflow) is
  governed by `list-multi-select-actions-spec.md`. This spec leaves it
  unchanged. The selection-mode overflow uses the same `MoreVert` icon —
  unifying the two overflow surfaces visually.
- Drawer hamburger position and behavior unchanged.

## 6. Icons for Rename / Delete Label

The Rename and Delete label items in label-mode overflow currently render
text-only — they have no leading icons. This is a current gap relative to
MD3 convention. This spec adopts:

- **Rename label** — `Icons.Outlined.Edit` (already imported elsewhere in
  the screen).
- **Delete label** — `Icons.Outlined.Delete` (already imported via the
  card delete path; verify in the import block before adding).

Both leading icons render with `contentDescription = null` since the item's
text label is the accessible label.

## 7. Strings and Localization

No new user-facing strings are introduced by this refactor. All actions
already have localized strings:

- `action_select_bookmarks` — used unchanged for the new overflow item.
- `filter_bookmarks` — used unchanged for the new overflow item.
- `rename_label`, `delete_label` — used unchanged.
- `more_options` — used for the overflow icon's contentDescription
  (already used in the selection-mode overflow added in Phase 1
  follow-up).

No locale-file edits are required.

## 8. Tests

ViewModel:

- No new ViewModel surface — purely a UI reshuffle. Existing
  `onOpenFilterSheet`, `onEnterMultiSelectMode`, `onRenameLabel`,
  `onDeleteLabel` paths are reused. No ViewModel test changes required.

Compose / UI:

- In normal (non-label) mode the bar contains exactly Layout, Sort,
  Overflow (in that order, after the navigation icon and title).
- In normal mode, the overflow menu contains Filter and Select bookmarks,
  in that order.
- In label mode the bar contains exactly Layout, Sort, Overflow.
- In label mode, the overflow menu contains Rename label, Delete label,
  Select bookmarks (with a divider between the label-scoped items and the
  list-scoped item).
- Tapping the overflow Filter item opens `FilterBottomSheet`.
- Tapping the overflow Select bookmarks item enters multi-select mode.
- The selection-mode bar is unaffected and matches the multi-select spec.

## 9. Migration Notes

- This refactor lands on its own branch (Phase 3) and replaces the
  normal-mode `Filter` icon and `Multi-select` icon in
  `BookmarkListScreen.kt`. The corresponding icon imports
  (`Icons.Filled.FilterList`, `Icons.Filled.RadioButtonUnchecked`) move
  inline into the overflow `DropdownMenuItem` blocks; they remain imported.
- The existing label-mode `Box { IconButton + DropdownMenu }` block becomes
  the **only** overflow block in the screen, parameterized by mode rather
  than duplicated. The current "Filter button (non-label mode only)" branch
  and the trailing always-on Multi-select `IconButton` block are deleted.
- Update the user guide (`your-bookmarks.md`) sections that describe the bar
  in normal/label modes to reflect the new shape.
- No data, schema, or settings changes.

## 10. Verification

Run the standard verification tasks:

```sh
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Manual on-device sanity (Pixel 9):

- Phone portrait, normal mode → bar shows Layout, Sort, Overflow only.
- Tap overflow → Filter and Select bookmarks listed.
- Open a label from the drawer → bar shape unchanged; overflow now shows
  Rename, Delete, divider, Select bookmarks. Verify icons render on the
  Rename and Delete items.
- Enter and exit selection mode from the overflow — verify selection-mode
  bar replaces the normal bar and exits cleanly to the new bar shape.

## 11. Open Decisions

None blocking. Considered and resolved:

- Whether to put Layout in the overflow → no; submenu cost + status-icon
  value justify the bar slot.
- Whether Filter belongs in the overflow → yes; FilterBar chips compensate
  for the lost bar prominence.
- Whether long-press should be the primary multi-select entry → no;
  long-press is already bound to per-card context (mirrors Readeck). The
  overflow entry is the discoverable secondary affordance.
- Whether the overflow should appear only when it has more than one item →
  no; always-present overflow keeps bar shape stable across mode changes.
