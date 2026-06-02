# List Multi-Select — Batch Add Labels (Addendum)

Status: Draft for implementation. Addendum to
[`list-multi-select-actions-spec.md`](./list-multi-select-actions-spec.md);
extends that spec's §6 action set with a fourth batch action. Depends on
multi-select Phase 3 (batch delete + Undo) having landed on
`feat/multi-select-phase-1`, since it reuses that branch's unified
Snackbar + Undo machinery.

Date: 2026-06-01

## 1. Purpose & Scope

Add a **batch "Add labels"** action to selection mode: pick one or more labels
and apply them additively to every selected bookmark, with a confirming
Snackbar and Undo — consistent with the favorite/archive/delete batch actions.

**In scope:** additively *adding* labels to a selection (union per bookmark);
creating brand-new labels from the picker; Undo.

**Out of scope (explicitly deferred):** batch *removing* labels. Removal
requires showing the selection's current (mixed) label state in the picker,
which is materially more complex. See §11.

## 2. Why This Is Low-Risk

Most of the machinery already exists:

- **Picker UI is reusable.** `LabelPickerBottomSheet`
  ([LabelsBottomSheet.kt](../../app/src/main/java/com/mydeck/app/ui/list/LabelsBottomSheet.kt))
  has a `LabelPickerMode.MultiSelect(initialSelection: Set<String>, onDone: (Set<String>) -> Unit)`
  mode, is not coupled to a single-bookmark ViewModel, and can create a new
  label from search text. We use it as-is with an **empty** initial selection.
- **Multi-bookmark label mutation has precedent.** `renameLabel` /
  `deleteLabel`
  ([BookmarkRepositoryImpl.kt:1131-1181](../../app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt#L1131-L1181))
  already iterate affected bookmarks in a `database.performTransaction { }`,
  compute a per-bookmark updated label list, write it via
  `bookmarkDao.updateLabels(id, json)`, and `upsertPendingAction(id,
  ActionType.UPDATE_LABELS, LabelsPayload(updatedLabels))`. Batch add follows
  the identical structure.
- **Sync path exists.** `ActionType.UPDATE_LABELS` is applied at
  [BookmarkRepositoryImpl.kt:1027-1030](../../app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt#L1027-L1030)
  as `editBookmark(id, EditBookmarkDto(labels = payload.labels))` — a full-list
  replace. No new ActionType or DTO field is required.

## 3. User Experience

### 3.1 Entry point

The selection bar already carries Favorite, Archive, and Delete icons (plus the
✕/count and overflow). To avoid crowding in phone portrait, **"Add labels" is a
selection-mode overflow item**, not a fourth bar icon. Leading icon: a
label/tag glyph (e.g. `Icons.AutoMirrored.Outlined.Label`),
`contentDescription = null`. It is enabled whenever the selection is non-empty.

### 3.2 Picker

Tapping **Add labels** opens `LabelPickerBottomSheet` in `MultiSelect` mode:

- `initialSelection = emptySet()` — the action is "which labels to **add**",
  not "edit this selection's labels". Starting empty deliberately sidesteps any
  mixed-state display across the selection.
- `labels` map comes from the existing `observeAllLabelsWithCounts()`.
- The user may pick existing labels and/or create new ones from search text
  (existing picker behavior).
- `onDone(chosen: Set<String>)`:
  - `chosen.isEmpty()` → dismiss, no action, no Snackbar.
  - otherwise → apply (§3.3).
- Dismissing the picker without **Done** cancels with no change.

The selected bookmark ids are captured when the picker opens, so selection mode
state changes don't affect the pending apply.

### 3.3 Apply, Snackbar, Undo

On Done with a non-empty `chosen` set:

1. Apply additively to each selected bookmark (§4), capturing each **changed**
   bookmark's prior label list.
2. **Stay in selection mode** (consistent with favorite/archive, which are also
   non-destructive immediate actions). The selection is preserved so the user can
   run further actions on the same set. Auto-exit-on-empty still applies if the
   add removes the last visible item from the current view.
3. Show a Snackbar: **"Labels added to N bookmarks"**, where N is the number of
   bookmarks that **actually changed** (a bookmark that already had every chosen
   label is a no-op and is not counted). If N is 0, still show a Snackbar
   confirming the tap landed (e.g. same string with 0), or suppress — pick one
   and note it; recommend showing it for parity with favorite/archive's
   "fires regardless" behavior.
4. The Snackbar carries **Undo**. Labels apply immediately (like
   favorite/archive, not deferred like delete). Undo restores each changed
   bookmark's captured prior label list (§4). Bookmarks that didn't change are
   never touched.

## 4. Semantics

**Additive union, full-replace persistence.** For each selected bookmark:

```kotlin
val merged = (existing.labels + chosen).distinct()
if (merged.size != existing.labels.size) {
    // changed: record prior, write merged
}
```

- Labels already present are preserved; duplicates collapse.
- A bookmark whose label set is unchanged (already had all chosen labels) gets
  **no DB write, no pending action, and is excluded from the changed count**.
- Persistence reuses the existing **full-list** `UPDATE_LABELS` path
  (`LabelsPayload(merged)`), exactly like `renameLabel`/`deleteLabel`. We do
  **not** introduce a new `ADD_LABELS` action type (see §7 for why, and the
  server-side `add_labels` option).

**Undo is symmetric.** Because persistence is full-list-replace, undo simply
re-applies each changed bookmark's captured prior list via the same
`UPDATE_LABELS` path. No set-difference computation is needed, and labels that
were already present before the action are correctly retained.

## 5. Data Layer

Add to `BookmarkRepository` / `BookmarkRepositoryImpl`, modeled directly on
`renameLabel` ([:1131](../../app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt#L1131)):

```kotlin
/** Adds [labels] to each bookmark, returning the prior label list of every
 *  bookmark that actually changed (for Undo). */
suspend fun addLabelsToBookmarks(
    ids: List<String>,
    labels: List<String>
): Map<String, List<String>>   // changedId -> priorLabels

/** Restores the captured prior label lists (Undo). */
suspend fun restoreBookmarkLabels(priorByBookmark: Map<String, List<String>>)
```

Both run in a single `database.performTransaction { }`, write via
`bookmarkDao.updateLabels`, `upsertPendingAction(id, UPDATE_LABELS,
LabelsPayload(...))`, then `syncScheduler.scheduleActionSync()`. Expose
matching methods on `UpdateBookmarkUseCase`.

## 6. State Model & Wiring (`BookmarkListViewModel`)

Extend the Phase 3 `BatchActionSnackbarEvent` family with:

```kotlin
data class LabelsAdded(
    override val count: Int,
    val priorLabelsByBookmark: Map<String, List<String>>
) : BatchActionSnackbarEvent()
```

- Add UI state to open the label picker from selection mode and to hold the
  captured selected ids while it is open (e.g.
  `_pendingLabelTargetIds: StateFlow<List<String>>` + a
  `showAddLabelsPicker` flag).
- `onAddLabelsToSelection()` — capture `selectedIds`, open picker.
- `onLabelsPicked(chosen: Set<String>)` — if empty, dismiss; else call
  `addLabelsToBookmarks`, keep the selection active, `trySend(LabelsAdded(...))`.
- Route through the **existing** unified Snackbar collector: `LabelsAdded`
  uses `SnackbarDuration.Long`, `actionLabel = action_undo`,
  `ActionPerformed → onUndoBatchAction`. In `onUndoBatchAction`, the
  `LabelsAdded` branch calls `restoreBookmarkLabels(priorLabelsByBookmark)`.
  No confirm-on-dismiss branch (labels are not deferred, unlike delete).

## 7. Sync (and a deliberate non-choice)

Reuses `ActionType.UPDATE_LABELS` (full-list replace). The local optimistic DB
write already computes the union, so there is nothing to gain locally from a
delta action.

**Server-side `add_labels` — noted, not used here.** The Readeck PATCH endpoint
supports `add_labels` (openapi-spec.json:735-739) and `EditBookmarkDto` already
exposes it. Using it would make sync robust against a concurrent server-side
label change in the window between local read and sync (full-replace is
last-writer-wins per field). We **defer** that hardening because:

- It would need a new `ADD_LABELS` action type with accumulate-on-upsert
  semantics and a superseded-by-`UPDATE_LABELS` rule — more moving parts.
- The existing `renameLabel`/`deleteLabel` already accept the same small
  full-replace race, so this introduces no new risk relative to today.

If label-edit races are observed in practice, migrating add to server-side
`add_labels` is a self-contained follow-up.

## 8. Strings & Localization

One new flat `%1$d` string (no `<plurals>`), added to all ten
`values*/strings.xml` with English placeholders per `CLAUDE.md`:

```xml
<string name="multi_select_labels_added">Labels added to %1$d bookmarks</string>
```

Reuse `action_undo` and `action_select_bookmarks`. The overflow item label can
reuse the existing `add_labels` string.

## 9. Tests

ViewModel / repository (mockk + `runTest`):

- Union preserves existing labels and collapses duplicates.
- Bookmarks already carrying all chosen labels are excluded from the changed
  set and the count.
- `addLabelsToBookmarks` returns correct prior-label snapshots only for changed
  bookmarks; `restoreBookmarkLabels` reverts exactly those.
- Empty `chosen` is a no-op (no event, no DB write).
- `LabelsAdded` undo invokes `restoreBookmarkLabels` with the carried map.

Compose:

- Selection-mode overflow shows **Add labels** when a selection exists and
  invoking it opens the picker.

## 10. Documentation

- `your-bookmarks.md` → "Selecting Multiple Bookmarks": add "Add labels" to the
  set of batch actions and the Undo description (labels apply immediately; Undo
  restores prior labels on the bookmarks that changed).
- `organizing.md` → "Labels": add a multi-select add note mirroring the
  Favorites/Archive multi-select paragraphs.

## 11. Out of Scope — Batch Remove

Removing labels in batch needs the picker to represent the selection's *current*
labels — which differ per bookmark (present-on-all vs. present-on-some vs.
absent). That tri-state UI, plus `remove_labels` semantics and a more involved
Undo, is a separate effort and is intentionally not specified here.

## 12. Effort Estimate

- Data layer (`addLabelsToBookmarks` + `restoreBookmarkLabels`): **small** —
  near-copy of `renameLabel`.
- ViewModel event + picker wiring: **small–moderate**.
- Undo: **small** (symmetric full-list restore).
- Strings (×10) + tests + docs: **small**.

Overall **moderate-but-modest**, lighter than batch delete was, because the
picker, the multi-bookmark label transaction pattern, and the unified
Snackbar/Undo collector all already exist.
