# List Multi-Select Actions — Feasibility and Design Specification

Status: Draft for implementation. Phase 1 (selection mode plus batch favorite/archive) is implemented on branch `feat/multi-select-phase-1`. A Phase 1 follow-up on the same branch lands the batch favorite/archive UX as **explicit dual actions with contextual elevation** (MD3 Pattern B, refined):

- Each of the two action axes — favorite and archive — is computed independently from the current selection. For each axis the system promotes the single most useful action to the top app bar; the inverse goes into the overflow menu, greyed-out when it would be a no-op.
  - **All selected are already in the target state** → the inverse action (Remove favorite / Unarchive) is elevated to the bar with the Outlined icon variant. The overflow holds the matching Add action, disabled.
  - **None or some are in the target state** → the additive action (Add favorite / Archive) sits on the bar with the Filled icon variant. The overflow holds the Remove/Unarchive action, enabled iff at least one selected item is in the matching state.
- Terminology is aligned with the Reading-view overflow: **Add favorite**, **Remove favorite**, **Archive**, **Unarchive**.
- Select all / Deselect all is a single toggle item in the overflow that swaps label and leading icon based on whether all visible bookmarks are selected.
- Every batch action emits a confirmation Snackbar describing the post-state of the full selection using a uniform "N set/unset as X" copy pattern: "%d set as favorite(s)", "%d unset as favorite(s)", "%d set as archived", "%d unset as archived" (plurals from `multi_select_set_as_*` / `multi_select_unset_as_*`). The count is the selected count.
- DB writes only go to items whose state would actually change; the Snackbar fires regardless, so a uniform-state tap still confirms the user's intent landed.

The favorite/archive icons on each card remain visible in select mode as **non-interactive state indicators** at 38% alpha so the user can see per-card state at a glance.

Phase 3 remains. It bundles two related workstreams that both touch the bookmark-list top app bar and should land together to avoid two consecutive bar reshuffles for users:

1. **Batch delete + Snackbar Undo** (this spec, §6.7) — extends the delete path to the batch case and adds Undo affordances to the favorite/archive/delete Snackbars. The Snackbar+Undo pattern supersedes the previously-planned "Confirm multi-select actions" Settings switch — §7 is no longer planned.
2. **List view top app bar overflow refactor** — relocates the multi-select entry icon from a dedicated bar slot into a list-view top app bar overflow menu and addresses the current label-mode bar gap. Specified in [`list-top-app-bar-overflow-spec.md`](./list-top-app-bar-overflow-spec.md). Selection-mode bar contents (X, count, primary actions, selection-mode overflow) are unaffected by that refactor.

Date: 2026-05-12

## 1. Purpose

Add an explicit multi-select mode to bookmark list views so users can apply
Favorite, Archive, and Delete actions to multiple bookmarks at once.

The feature should feel native to Material Design 3 and Android list-selection
patterns, while preserving MyDeck's existing single-card actions, pending-delete
undo behavior, and offline-first sync model.

This is a specification only. Do not implement as part of this document change.

## 2. Current Codebase Assessment

The feature is feasible in the current architecture.

Existing strengths:

- `BookmarkListScreen` already owns top app bar action composition, snackbar
  behavior, and per-list card rendering.
- `BookmarkListViewModel` already has a pending-delete ID list, so the UI can
  visually gray multiple bookmarks.
- `BookmarkRepositoryImpl` already performs local-first bookmark mutations and
  queues pending sync actions.
- Existing Favorite, Archive, and Delete icons already map to the correct
  business actions.
- The pending swipe-action specs require the same design principle that this
  feature should follow: reuse existing action semantics rather than creating a
  separate behavior model.

Main implementation risk:

- Delete is currently presented as a singleton snackbar interaction, even though
  pending-delete UI state can hold multiple IDs. Multi-select delete needs a
  batch staging, batch undo, and batch confirmation path.

Overall complexity:

- UI state and card rendering: medium.
- Top app bar mode switching: low-medium.
- Batch Favorite/Archive: medium, mostly because target values differ per item.
- Batch Delete with undo: medium-high.
- Settings confirmation bottom sheet: medium.
- Tests/localization/docs: medium.

Recommended implementation size:

- One focused feature branch.
- Prefer two implementation phases if risk needs to be reduced:
  1. Multi-select state, top app bar, card selection, batch Favorite/Archive.
  2. Batch Delete/Undo plus optional confirmation setting.

## 3. Scope

Applies to bookmark card lists in:

- My List
- Archive
- Favorites
- Articles
- Videos
- Pictures
- Label-filtered bookmark lists, unless explicitly excluded during implementation

Multi-select mode is list-local and transient. It should reset when:

- A batch action completes.
- The user exits multi-select with the X action.
- The visible list context changes, such as drawer preset, active label, or
  filter form changes.
- The screen is left.

## 4. Non-Goals

- No server-side batch API requirement.
- No Room schema migration unless implementation discovers a durable pending
  batch identity is required.
- No replacement of existing per-card single actions in normal mode.
- No change to bookmark filters, sorting, labels, or detail navigation behavior
  outside multi-select mode.
- No long-press entry point in v1.

A Phase 1 follow-up adds a **Select all / Deselect all** toggle in the
selection-mode overflow menu. It applies only to the reversible
favorite/archive actions; select-all-for-delete is deferred to Phase 3 along
with the Undo design.

## 5. Material Design and Android Pattern Alignment

Android's Compose guidance for dynamic top app bars supports changing top app
bar title/actions based on selected list items. This maps directly to MyDeck's
selection count plus contextual action icons.

Android's checkbox guidance says checkboxes are for selecting one or more items
from a list and should be used instead of radio buttons when multiple items can
be selected.

Decision:

- Use checkbox semantics, not radio-button semantics.
- The visual indicator may be circular to match the requested unfilled-circle
  affordance, but accessibility semantics and content descriptions must describe
  selection, not radio choice.
- Selected state should use a filled primary-color circle with a checkmark.

Reference guidance:

- Android dynamic top app bar:
  `https://developer.android.com/develop/ui/compose/components/app-bars-dynamic?hl=en`
- Android Compose checkbox:
  `https://developer.android.com/develop/ui/compose/components/checkbox?hl=en`

## 6. User Experience

### 6.1 Entering multi-select mode

In normal list mode, add a multi-select icon at the far right side of the top app
bar.

Preferred icon:

- `RadioButtonUnchecked` or equivalent unfilled circle icon as the visual.
- Content description should be `Select bookmarks` or similar.
- Although the visual is a circle, this must behave as a multi-select control,
  not a radio button.

> **Phase 3 follow-on:** the multi-select entry point relocates from a
> dedicated bar icon into a list-view top app bar overflow menu. See
> `list-top-app-bar-overflow-spec.md`. Selection-mode bar contents (X, count,
> primary actions, selection-mode overflow) are unaffected.

Top app bar action ordering in normal mode:

- Preserve existing actions.
- Add the multi-select entry icon as the far-right action because this action is
  not present in the Readeck web UI and should be visually secondary to existing
  Readeck-aligned actions.

When selected:

- Enter multi-select mode.
- Initial selected count is `0`.
- The FAB is hidden.
- Filter/sort/layout/menu actions are hidden while in multi-select mode.

### 6.2 Multi-select top app bar

When multi-select mode is active (Phase 1 follow-up — **explicit dual actions with contextual elevation**):

- Left navigation icon becomes `X`.
- Title becomes the selected count, initially `0`.
- A 3-dot **overflow** menu (`MoreVert`) is always shown on the right and
  contains, at minimum, **Select all / Deselect all** (a single toggle item
  whose label and leading icon swap between the two states based on whether all
  visible items are already selected).
- When one or more cards are selected, the bar gains two action icons (one per
  axis) and the overflow gains two corresponding items (the inverse per axis).
  Each axis is computed independently from the current selection:
  - **Favorite axis**:
    - If every selected item is already favorited → bar = **Remove favorite**
      (`Icons.Outlined.FavoriteBorder`, cd `action_remove_from_favorites`).
      Overflow entry = **Add favorite** (`Icons.Filled.Favorite`), disabled.
    - Otherwise → bar = **Add favorite** (`Icons.Filled.Favorite`, cd
      `action_add_to_favorites`). Overflow entry = **Remove favorite**
      (`Icons.Outlined.FavoriteBorder`), enabled iff at least one selected item
      is currently favorited.
  - **Archive axis**: mirrors favorite, using `Icons.Filled.Inventory2` /
    `Icons.Outlined.Inventory2`, `action_add_to_archive` /
    `action_remove_from_archive`.
  - Delete remains on the bar (Phase 3 will add Undo to its Snackbar).

The promotion logic guarantees one-tap reversal for uniform-state selections
(you don't have to dig into the overflow to undo a uniform state) while keeping
mixed-state selections additive-by-default and reversible via the overflow.

The `X` action exits multi-select mode and clears the selection.

Selected count should be a plain number or a short localized selected-count
string. Prefer the shortest form that fits compact phones.

### 6.3 Card behavior in multi-select mode

When multi-select mode is active:

- Per-card interactive action icons (overflow/delete/open URL) are hidden.
- The favorite and archive icons on each card remain visible as
  **non-interactive state indicators** at 38% alpha (Phase 1 follow-up): the
  filled/outline variant reflects each bookmark's current favorite/archive
  state, and the content description announces state (e.g. "Favorited" / "Not
  favorited") rather than an action. They are not tappable in select mode.
- The far-right action slot where Delete normally appears is replaced by the
  multi-select indicator.
- Tapping anywhere on a card toggles selection for that card.
- Tapping the indicator toggles selection for that card.
- Selected cards show a filled primary-color circle with a white checkmark.
- Unselected cards show an unfilled circle.
- Card open/navigation is disabled.
- Long press is disabled.
- Swipe gestures are disabled.
- Any currently revealed swipe state should close when multi-select mode starts.

This applies across all card layouts:

- Grid
- Compact
- Mosaic
- Mobile portrait grid variant
- Wide/grid variants

### 6.4 Empty selection

Multi-select mode may remain active with `0` selected cards.

This supports intentional entry into selection mode before choosing cards and
matches the requested initial-count behavior.

Batch actions are hidden or disabled until at least one card is selected. Prefer
hidden to reduce clutter, matching the requested behavior.

### 6.5 Batch Favorite (Contextual Elevation)

Two explicit actions, surfaced according to the selection's current favorite
uniformity (computed independently from archive):

- **Add favorite** (Filled heart) sets `isMarked = true` on every selected
  item. It lives on the bar by default and slides into the overflow (disabled)
  when every selected item is already favorited.
- **Remove favorite** (Outlined heart) sets `isMarked = false`. It lives in
  the overflow and is promoted to the bar when every selected item is already
  favorited.

Behavior:

- Filter the selection to items whose current state differs from the target;
  issue a single batch DB update for that subset only. Items already in the
  target state get no DB write and no sync churn.
- Always emit a Snackbar describing the post-state of the **full selection**,
  using "set/unset as X" copy and the selected count:
  - After Add favorite → `multi_select_set_as_favorite` ("%d set as
    favorite" / "%d set as favorites").
  - After Remove favorite → `multi_select_unset_as_favorite` ("%d unset as
    favorite" / "%d unset as favorites").
- Reset the list to non-multi-select mode after the action is accepted.

### 6.6 Batch Archive (Contextual Elevation)

Mirrors §6.5 on the archive axis using `Icons.Filled.Inventory2` /
`Icons.Outlined.Inventory2`. **Archive** is the additive default;
**Unarchive** lives in the overflow until every selected item is already
archived, at which point it is promoted to the bar. The Snackbar uses
`multi_select_set_as_archived` ("%d set as archived") or
`multi_select_unset_as_archived` ("%d unset as archived").

Favorite and archive uniformity are computed independently — promoting one axis
to its inverse does not affect the other axis's slot.

List removal should be controlled by existing filters:

- My List: archiving removes affected items because they no longer match.
- Archive: unarchiving removes affected items because they no longer match.
- Favorites/Articles/Videos/Pictures: items may remain if they still match the
  current filter.

### 6.7 Batch Delete

> **Phase 3 bundling:** this section is one of two Phase 3 workstreams that
> reshape the bookmark-list top app bar. The companion refactor —
> [`list-top-app-bar-overflow-spec.md`](./list-top-app-bar-overflow-spec.md) —
> relocates the multi-select entry icon into a normal-mode overflow menu and
> closes the current label-mode bar gap. Ship the two together so users see a
> single bar reshuffle, not two.

When Delete is selected:

- Mark all selected cards as pending-delete.
- Reset the list to non-multi-select mode.
- Show a snackbar indicating the number of bookmarks deleted.
- Include an Undo action.

Undo:

- Restores all batch-pending cards from pending-delete UI state.
- Does not enqueue delete actions.

Dismissal or interaction elsewhere:

- Confirms the batch delete.
- Soft-deletes each bookmark locally.
- Enqueues delete pending actions for sync.

The existing singleton delete helper should not be used directly for batch
delete. Add explicit batch staging/cancel/confirm paths so snackbar lifecycle and
pending ID cleanup are atomic from the user's perspective.

Recommended snackbar copy:

- Single item can keep existing copy.
- Multiple items: `N bookmarks deleted`.
- Action: `Undo`.

## 7. Confirmation — Snackbar, Not a Setting

_Originally a "Confirm multi-select actions" Settings toggle. Superseded
during the Phase 1 follow-up by the MD3 Snackbar + Undo pattern._

Every batch action — favorite, unfavorite, archive, unarchive, delete —
displays an MD3 Snackbar on completion summarizing the operation. The Snackbar
text reflects the post-state of the full selection sized by the selected count
(e.g. "Added 3 to favorites", "Moved 5 to My List", "3 bookmarks deleted").

The Snackbar is the confirmation surface: explicit, low-friction, reversible
via Undo (Phase 3 for favorite/archive; already implemented for single-item
delete). No pre-action confirmation dialog or bottom sheet is shown.

This eliminates the need for a Settings toggle: there is nothing for the user
to configure, because every action provides immediate visible feedback and
(eventually) an undo path.

## 8. State Model

Add a transient multi-select state to `BookmarkListViewModel`.

Suggested shape:

```kotlin
data class MultiSelectState(
    val active: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)
```

The UI should derive:

- `selectedCount`
- `hasSelection`
- Per-card `isSelected`

For batch action execution, capture selected bookmark snapshots from the current
visible list before mutating:

```kotlin
data class SelectedBookmarkActionState(
    val id: String,
    val isMarked: Boolean,
    val isArchived: Boolean
)
```

This prevents filter-driven removal from changing the intended batch while it is
being applied.

State clearing rules:

- Exit action clears state.
- Batch action confirm clears state.
- Drawer/filter/label/search context changes clear state.
- Refresh should not necessarily clear state unless selected IDs disappear from
  the visible list. If selected IDs are no longer visible, remove them from the
  selection.

## 9. Data and Sync Design

### 9.1 Favorite and Archive

Current single-item paths:

- `UpdateBookmarkUseCase.updateIsFavorite`
- `UpdateBookmarkUseCase.updateIsArchived`
- `BookmarkRepository.updateBookmark`
- `BookmarkRepositoryImpl.updateBookmark`

Recommended batch approach:

- Add batch methods rather than looping through UI handlers.
- Perform local updates in a single Room transaction where practical.
- Queue pending actions for each affected bookmark.
- Schedule action sync once after the transaction, not once per bookmark.

Potential repository API:

```kotlin
suspend fun updateBookmarks(
    updates: List<BookmarkBatchUpdate>
): UpdateResult

data class BookmarkBatchUpdate(
    val bookmarkId: String,
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
    val isRead: Boolean? = null
)
```

The implementation may keep the existing single-item method and add a batch
method beside it.

### 9.2 Delete

Current single-item delete:

- UI stages pending-delete state.
- Snackbar result either cancels pending-delete UI state or confirms delete.
- Confirm delete calls `UpdateBookmarkUseCase.deleteBookmark`.
- Repository soft-deletes locally, removes other pending actions for that
  bookmark, inserts a `DELETE` pending action, and schedules sync.

Recommended batch approach:

- Add ViewModel methods:
  - `onDeleteBookmarks(bookmarkIds: Set<String>)`
  - `onConfirmDeleteBookmarks(bookmarkIds: Set<String>)`
  - `onCancelDeleteBookmarks(bookmarkIds: Set<String>)`
- Add use-case/repository batch delete:
  - Soft-delete all selected IDs in one transaction where practical.
  - Remove other pending actions for each bookmark.
  - Insert one `DELETE` pending action per bookmark.
  - Schedule action sync once.

Potential DAO additions:

```kotlin
@Query("UPDATE bookmarks SET isLocalDeleted = 1 WHERE id IN (:ids)")
suspend fun softDeleteBookmarks(ids: List<String>)
```

Pending action insertion can use existing `@Insert` overloads if added, or loop
inside the repository transaction.

## 10. Interaction With Pending Swipe Specs

The pending swipe-action specs remain compatible with multi-select if both
features share a clear interaction mode boundary.

Rules:

- Normal mode: per-card icons and swipe actions are available.
- Multi-select mode: per-card icons, swipe actions, long press, and card
  navigation are disabled.
- Starting multi-select closes any revealed swipe action state.
- Swipe gestures must not enter multi-select mode.
- Multi-select taps must not trigger swipe commit/reveal behavior.

The swipe specs' principle still applies:

- Swipe actions trigger existing single-item action paths.
- Multi-select actions trigger explicit batch action paths.

## 11. Interaction With Detail Error Spec

The pending extraction-error detail panel spec does not materially conflict with
multi-select because it primarily touches detail-screen API/UI state.

Possible overlap:

- `BookmarkRepository` may receive new methods from both specs.
- String resources and tests may overlap.

Implementation guidance:

- Keep repository additions additive and clearly named.
- Avoid broad repository refactors while both specs are pending.

## 12. Accessibility

Required semantics:

- Multi-select top-bar icon: `Select bookmarks`.
- Exit icon: `Exit selection mode`.
- Selected count should be announced as the top app bar title or via state
  description.
- Per-card selection indicator:
  - Unselected: `Select bookmark`
  - Selected: `Deselect bookmark`
- Card root in multi-select mode should expose selected state.

Do not expose selection indicators as radio buttons.

## 13. Visual Design Notes

Selection indicator:

- Unselected: outline circle, on-surface-variant or outline color.
- Selected: filled circle using `MaterialTheme.colorScheme.primary`.
- Checkmark: on-primary color.
- Size should match existing action icon tap target expectations.

The selected indicator deliberately differs from current read-progress/read
complete indicators:

- Existing read indicator may use a checkmark.
- Selected indicator uses primary filled circle with selected semantics.
- Placement is in the far-right action slot, not the thumbnail/status area.

Top app bar:

- Normal mode: existing title/navigation/actions, plus multi-select entry at far
  right.
- Selection mode: X, count, Favorite/Archive/Delete actions only when count > 0.

FAB:

- Hidden while multi-select mode is active.

## 14. Strings and Localization

Add new strings to every `values*/strings.xml` file using English placeholders,
per `CLAUDE.md`.

Likely strings:

- `action_select_bookmarks`
- `action_exit_selection_mode`
- `action_select_bookmark`
- `action_deselect_bookmark`
- `selected_bookmark_count`
- `selected_bookmark_count_many`
- `multi_select_deleted_count`
- `multi_select_confirm_setting_title`
- `multi_select_confirm_setting_description`
- `multi_select_confirm_favorite_title`
- `multi_select_confirm_archive_title`
- `multi_select_confirm_delete_title`
- `multi_select_confirm_delete_description`
- `confirm`
- `cancel` if not already available

Use Android plural resources if the project already uses them. If not, add
plurals only if needed for clean count handling.

## 15. User Guide Updates

Update English user guide files only:

- `app/src/main/assets/guide/en/your-bookmarks.md`
- `app/src/main/assets/guide/en/organizing.md`
- `app/src/main/assets/guide/en/settings.md`

Cover:

- How to enter and exit multi-select mode.
- How to select/deselect bookmarks.
- Available batch actions.
- Delete undo behavior.
- Confirmation setting.

## 16. Tests

ViewModel tests:

- Entering multi-select starts with zero selected.
- Tapping a card toggles selected ID in multi-select mode.
- Exiting clears selected IDs.
- Context changes clear selection.
- Batch favorite toggles each selected bookmark from its captured state.
- Batch archive toggles each selected bookmark from its captured state.
- Batch delete stages all selected IDs.
- Batch delete undo cancels all staged IDs.
- Batch delete confirm calls batch delete once with all selected IDs.

Repository tests:

- Batch favorite/archive updates local rows and queues pending actions.
- Batch update schedules sync once.
- Batch delete soft-deletes all IDs.
- Batch delete removes prior pending actions for affected bookmarks.
- Batch delete inserts delete pending actions for all IDs.
- Batch delete schedules sync once.

Compose/UI tests:

- Normal top app bar shows multi-select icon at far right.
- Selection top app bar shows X and selected count.
- Batch action icons are hidden at zero selected and visible above zero.
- FAB is hidden in multi-select mode.
- Card action icons are hidden in multi-select mode.
- Card tap toggles selected state instead of opening detail.
- Long press does not share in multi-select mode.

## 17. Verification

When implemented, run the project default verification tasks serially:

```sh
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
```

Because this touches UI, resources, settings, and documentation, also run:

```sh
./gradlew :app:lintDebugAll
```

Do not run emulator/device-required tasks.

## 18. Open Decisions

Resolved for this spec:

- Hide the FAB in multi-select mode: yes.
- Place the multi-select entry icon at the far right of the normal top app bar:
  yes.
- Disable long press in multi-select mode: yes.
- Disable swipe in multi-select mode: yes.
- Select-all (favorite/archive only) included in Phase 1 follow-up: yes;
  lives in the overflow menu as a single toggle item.
- Batch favorite/archive UX: **explicit dual actions with contextual
  elevation** (Phase 1 follow-up). Each axis (favorite, archive) independently
  promotes the most useful action to the bar; the inverse lives in the
  overflow and is greyed-out when it would be a no-op. The bar icon swaps
  Filled↔Outlined to reflect which action is currently elevated.
- Favorite/archive remain visible on cards in select mode as dimmed,
  non-interactive state indicators: yes (Phase 1 follow-up).
- Batch favorite/archive show a completion Snackbar (Phase 1 follow-up).
  Snackbar uses the uniform **"N set/unset as X"** copy pattern with the
  selected count, reflecting the post-state of the full selection, not the
  diff.
- Settings-based confirmation switch: **dropped**. Superseded by the
  Snackbar + Undo pattern (Phase 3 will add Undo).

Remaining implementation choices:

- Whether selected count uses a bare number or localized "N selected" text.
- Whether select-all applies to batch delete in Phase 3 — pending the undo
  design.
