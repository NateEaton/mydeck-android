# List Multi-Select — Phase 4 Refinements Specification

Status: Draft for implementation on the existing `feat/multi-select-phase-1`
branch (Phase 3 shipped on the same branch; this work continues there).

Date: 2026-06-02

## 1. Purpose

Refine the shipped multi-select UX based on hands-on tester feedback. The
core issues:

1. The selection-mode 3-dot overflow is **un-discoverable**. Users can't tell
   what is inside before entering select mode, and once in select mode the
   relationship between the bar icons and the overflow items is not obvious.
2. The current bar↔overflow **opposite-action pair** (Favorite on the bar /
   Remove favorite in overflow, swapped when all-favorited) is clever but
   requires several uses to internalize.
3. Every batch action **exits select mode**, so reversing a wrong tap or
   chaining a follow-on action requires re-selecting from scratch.
4. **Delete** sits on the bar next to Favorite/Archive, inviting mis-taps
   on a destructive action.

Phase 4 keeps the Phase 1/3 architecture (single-screen CAB in the existing
top app bar, dimmed per-card state indicators, Snackbar+Undo) and changes
only the selection-mode bar layout, action-method post-behavior, and one
ViewModel auto-exit rule. No new domain or repository surface.

## 2. Out of Scope

- Bottom action bar (`BottomAppBar`) for selection mode. Considered and
  rejected: bigger UI surface change, breaks consistency with the rest of
  the app's top-bar chrome, the rest of this spec captures most of the same
  ergonomic wins.
- **Batch label assignment.** Deferred to Phase 5 (separate spec). Phase 4
  leaves room in the overflow ordering for the Phase 5 Label item to slot
  in without further reshuffling.
- Selection-mode entry point (still the bookmark-list top-bar overflow's
  "Select bookmarks" item per the bar refactor spec).
- Long-press entry (still unavailable; long-press is bound to per-card
  context menu, mirroring Readeck).

## 3. Current State (Phase 3, shipped)

Selection-mode top app bar, left to right:

1. **X** (exit selection mode).
2. Title: "N selected".
3. **Favorite slot** — `Icons.Filled.Favorite` (Add) when any selected item
   is not favorited, otherwise `Icons.Outlined.FavoriteBorder` (Remove).
4. **Archive slot** — `Icons.Filled.Inventory2` (Archive) when any selected
   item is not archived, otherwise `Icons.Outlined.Inventory2` (Unarchive).
5. **Delete** — `Icons.Filled.Delete`.
6. **Overflow** (`MoreVert`) containing:
   - The opposite of whatever the Favorite slot is currently showing,
     greyed-out when no-op.
   - The opposite of whatever the Archive slot is currently showing,
     greyed-out when no-op.
   - **Select all / Deselect all** (toggle item).

After any of Favorite / Unfavorite / Archive / Unarchive / Delete, the
selection clears and select mode exits. A Snackbar describing the post-state
appears with an Undo button that reverts exactly the `changedIds` from
that action.

## 4. Target Design (Phase 4)

### 4.1 Selection-mode bar shape

Left to right:

1. **X** (unchanged).
2. Title: "N selected" (unchanged).
3. **Archive slot** — single context-aware icon. Same swap rule as Phase 3:
   - If `selectedAllArchived` → `Icons.Outlined.Inventory2`, cd
     `action_remove_from_archive` ("Unarchive"), tap calls
     `onUnarchiveSelectedBookmarks`.
   - Otherwise → `Icons.Filled.Inventory2`, cd `action_add_to_archive`
     ("Archive"), tap calls `onArchiveSelectedBookmarks`.
4. **Favorite slot** — single context-aware icon. Same swap rule:
   - If `selectedAllFavorited` → `Icons.Outlined.FavoriteBorder`, cd
     `action_remove_from_favorites` ("Remove favorite"), tap calls
     `onUnfavoriteSelectedBookmarks`.
   - Otherwise → `Icons.Filled.Favorite`, cd `action_add_to_favorites`
     ("Add favorite"), tap calls `onFavoriteSelectedBookmarks`.
5. **Overflow** (`MoreVert`).

Net change vs Phase 3:

- **Archive moves to position 3** (was position 4). Archive-first matches
  the read-later workflow primacy in MyDeck.
- **Favorite moves to position 4** (was position 3).
- **Delete moves off the bar** and into the overflow (was position 5).
- The **opposite-action overflow entries are removed** entirely. The
  context-aware bar icons are the sole entry point for Favorite/Unfavorite
  and Archive/Unarchive; with stay-in-mode (§4.3) the icons flip in place
  after each tap and reversal is a single tap.

### 4.2 Overflow contents (with selection)

Top-down order:

1. **Delete** — `Icons.Filled.Delete`, cd `action_delete`, tap calls
   `onDeleteSelectedBookmarks`.
2. *(Phase 5 slot — Label batch action goes here.)*
3. **Select all / Deselect all** (toggle item, leading icon swaps
   `Icons.Filled.SelectAll` ↔ `Icons.Filled.Deselect`, label swaps
   `action_select_all` ↔ `action_deselect_all`).

When the selection is empty, only **Select all** is shown (matches Phase 3
behavior; the bar's Archive and Favorite slots are also hidden when there's
no selection).

### 4.3 Stay-in-select-mode after Favorite/Unfavorite/Archive/Unarchive

The four reversible favorite/archive batch actions **no longer clear the
selection or exit select mode**. The bar's context-aware icons re-evaluate
against the post-action selection state and swap accordingly on the next
frame. The Snackbar still fires with the same copy and Undo button.

Concretely in `BookmarkListViewModel`: drop the `clearMultiSelectState()`
call from `applyBatchFavorite` and `applyBatchArchive`.

User benefit: chaining a follow-on action (e.g. Favorite-all then
Unfavorite-all to test) and recovering from a wrong tap no longer requires
re-selecting from scratch. With one icon that flips state in place after a
tap, the swap is observable and the next tap is the reverse.

**Delete still exits select mode.** Batch delete stages items to the
batch-pending set and removes them from the list (greyed out); after the
Snackbar resolves it confirms the delete. There is no productive
"stay in mode" state for delete — the items are either on their way out or
about to be restored via Undo, and re-acting on them in selection mode
between those states would be confusing. Phase 3 behavior preserved.

### 4.4 Auto-exit when selection becomes empty

Add a rule: when `_multiSelectState.active` is true and the visible-bookmark
intersection of `selectedIds` becomes empty, auto-exit select mode.

This already partially happens via `dropSelectedIdsMissingFrom` — which
prunes invisible IDs from `selectedIds`. Phase 4 just adds the explicit
follow-on: if pruning leaves `selectedIds` empty, also flip `active = false`.

Why it matters:

- **Archive in My List** removes the selected items from the current view.
  The `dropSelectedIdsMissingFrom` rule then empties the selection. Without
  auto-exit, the bar would show "0 selected" with no actions visible — dead
  state. Auto-exit cleans up.
- **Unarchive in Archive view** — same dynamic.
- **Filter/preset/label changes** already clear multi-select via separate
  rules; this addition doesn't conflict.
- **Favorite in My List** does not remove items from view; selection
  remains non-empty; no exit.

## 5. Snackbar Behavior

Unchanged from Phase 3:

- Plural-free flat copy via `action_batch_set_as_favorite` /
  `action_batch_unset_as_favorite` / `action_batch_set_as_archived` /
  `action_batch_unset_as_archived` with `%1$d` substitution, plus
  `batch_delete_count` for Delete.
- Undo button reverts exactly `changedIds` for favorite/archive, or the
  batch-pending set for Delete.

**New consequence to call out in the spec:** because the user can fire
multiple actions in quick succession without leaving select mode, each new
Snackbar dismisses the previous one (standard `SnackbarHostState` behavior).
The Undo on a superseded Snackbar is lost. This matches Gmail's behavior and
is acceptable — users who want a definitive reversal should tap Undo before
acting again.

## 6. Strings and Localization

No new user-facing strings. All strings reused unchanged:

- `action_add_to_favorites`, `action_remove_from_favorites`
- `action_add_to_archive`, `action_remove_from_archive`
- `action_delete`
- `action_select_all`, `action_deselect_all`
- `more_options`
- The `action_batch_*_as_*` snackbar strings and `batch_delete_count`

No locale-file edits required.

## 7. Tests

### ViewModel (`BookmarkListViewModelTest`)

Update existing assertions to reflect stay-in-mode:

- `onFavoriteSelectedBookmarks` test: assert selection **preserved** post
  action (was: `MultiSelectState()` empty).
- `onUnfavoriteSelectedBookmarks` test: same.
- `onArchiveSelectedBookmarks` test: same.
- `onUnarchiveSelectedBookmarks` test: same.
- Snackbar event + `changedIds` assertions stay valid.
- Add: auto-exit when post-action visible intersection is empty (simulate
  the `bookmarkRepository` flow dropping the selected items after archive in
  My List, advance, assert `active = false` and `selectedIds` empty).

Existing Delete tests: no change. Delete still clears selection / exits mode
as part of the batch-pending staging.

### Compose / UI

- In select mode with selection present, the bar contains: X, "N selected"
  title, Archive icon, Favorite icon, Overflow. **No bar Delete icon.**
- In select mode with no selection, the bar contains: X, "0 selected", just
  the Overflow (no Archive, no Favorite).
- Overflow with selection: Delete, Select all / Deselect all (in that
  order, no opposite-action entries).
- Overflow without selection: only Select all.
- Tapping the bar's Favorite icon when not all are favorited: the action
  fires, the bar's Favorite icon switches to the Outlined variant, the
  selection remains intact, Snackbar appears.
- Tapping the bar's Favorite icon a second time (now Outlined) reverses
  the action with selection still intact.
- Tapping Delete in the overflow: select mode exits (per Phase 3 batch
  delete staging).
- Auto-exit: when post-action item removal empties the selection, select
  mode exits.

`BookmarkCardSelectionTest`: unchanged (per-card dimmed indicators don't
move).

## 8. Migration Notes

Files affected:

- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt` —
  remove `clearMultiSelectState()` from `applyBatchFavorite` and
  `applyBatchArchive`; extend the post-`dropSelectedIdsMissingFrom` check
  to also flip `active = false` when the resulting set is empty (one
  small helper or inline `update {}` block).
- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt` —
  reorder the bar actions (Archive before Favorite); delete the bar's
  Delete `IconButton`; remove the favorite/archive opposite-action
  `DropdownMenuItem` blocks from the overflow; add a new Delete
  `DropdownMenuItem` to the overflow (above Select all).
- `app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt`
  — flip selection-preservation assertions, add auto-exit-on-empty test.

No string, locale, layout, or schema changes.

## 9. User Guide Updates

Update English guide passages that describe the selection-mode bar:

- `app/src/main/assets/guide/en/your-bookmarks.md` — selection-mode section:
  describe the new bar order (Archive, Favorite, Overflow), note that
  Delete now lives in the overflow, and document the stay-in-mode behavior
  ("Tap Archive or Favorite as many times as you need — you stay in
  selection mode until you tap X or until all your selected items leave
  the list").
- `app/src/main/assets/guide/en/organizing.md` — Favorites and Archive
  sections: keep the contextual-icon explanation (the icon swap is what
  remains the user-visible behavior), drop references to the "overflow
  holds the opposite action" pattern.

## 10. Phase 5 Forward-Pointer

Phase 5 will add **batch label assignment** as a new overflow item placed
between Delete and Select all (per the order in §4.2). That spec is
maintained separately; it covers reusing the existing per-card
`LabelsBottomSheet` against a batch ID set. Phase 4 reserves the slot but
does not ship a placeholder/stub item — the slot is purely a documented
ordering decision, not a UI element to render now.

## 11. Verification

Standard:

```sh
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Manual on-device (Pixel 9, `./scripts/install-phone.sh`):

1. Enter select mode, select 3 items (mixed favorite state). Bar shows
   Archive then Favorite, plus Overflow. Tap Favorite → all 3 become
   favorited, Favorite icon swaps to Outlined, **selection preserved**,
   Snackbar appears.
2. Tap (now-Outlined) Favorite again → all 3 unfavorited, icon swaps back
   to Filled, selection still preserved, new Snackbar.
3. Tap Archive → items leave My List view, selection auto-empties,
   **select mode auto-exits**, Snackbar with Undo appears.
4. Open overflow → only **Delete** and **Select all** are listed (no
   favorite/archive opposite entries).
5. Tap Delete in overflow → items grey out, select mode exits, Snackbar
   with Undo appears (Phase 3 behavior preserved).

## 12. Open Decisions

None blocking. Considered and resolved during spec drafting:

- **Bottom action bar instead of top CAB**: no (out of scope per §2).
- **Delete on bar vs in overflow**: overflow (destructive + lower
  frequency).
- **Select all in overflow vs on bar**: overflow (lowest-frequency
  meta-action, fine at bottom of overflow).
- **Label slot rendered now as disabled placeholder**: no (no dead UI;
  reserved only as ordering decision).
- **Auto-exit when selection empties**: yes (avoids dead "0 selected"
  state).
- **Stay-in-mode for Delete**: no (Phase 3 batch staging + Undo flow
  doesn't benefit from staying in selection between stage and confirm).
