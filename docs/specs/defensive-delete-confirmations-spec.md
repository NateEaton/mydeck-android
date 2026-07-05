# Defensive Delete Confirmations

**Branch:** `feat/defensive-delete-confirmations`
**Status:** Implemented

## Motivation

Two deletion paths could silently destroy significant amounts of data with minimal friction:

- **Label delete** — the confirmation dialog said "It will be removed from all bookmarks" with no indication of how many bookmarks that is.
- **Bulk bookmark delete** — a user could filter to all bookmarks, enter multi-select, select all, tap Delete, then accidentally dismiss the undo snackbar in a handful of clicks, permanently deleting their entire library.

## Changes

### 1. Label Delete Dialog — Bookmark Count

The confirmation dialog (shown from both the TopAppBar overflow menu and the label picker context menu) now reads:

> Are you sure you want to delete the label "…"? It will be removed from **N** bookmarks.

**String change (`delete_label_confirm_message`):**
- From: `"…It will be removed from all bookmarks."`
- To: `"…It will be removed from %2$d bookmarks."` (second format arg = count)

**BookmarkListScreen.kt:**
- `labelsWithCounts: StateFlow<Map<String, Int>>` is already collected at the screen level.
- Look up `labelsWithCounts.value[currentLabel] ?: 0` when rendering the dialog and pass as the second string arg.

**LabelsBottomSheet.kt:**
- The `labels: Map<String, Int>` parameter is already in scope.
- Look up `labels[labelToDelete] ?: 0` when rendering the picker's delete dialog and pass as the second string arg.

### 2. Bookmark Bulk Delete — Confirmation Dialog at ≥ 25 Selected

When 25 or more bookmarks are selected in multi-select mode and the user taps Delete, a confirmation `AlertDialog` appears before anything is staged:

> **Delete**
> Delete 47 selected bookmarks?
> [ Cancel ] [ Delete ]

Confirming proceeds to the **existing** greyed-out + indefinite snackbar + Undo flow, unchanged. Under the threshold, the existing immediate-stage path is unchanged.

**New string (`delete_selected_bookmarks_confirm_message`):**
`"Delete %1$d selected bookmarks?"`

**BookmarkListScreen.kt:**
- Add `var showDeleteSelectionConfirmDialog by remember { mutableStateOf(false) }` near the other dialog state vars.
- In the multi-select Delete menu item `onClick`: if `multiSelectState.value.selectedCount >= 25`, set the flag; otherwise call `viewModel.onDeleteSelectedBookmarks()` directly as before.
- Add `AlertDialog` gated on the flag — confirm calls `dismissPendingDeleteSnackbar()` then `viewModel.onDeleteSelectedBookmarks()`; cancel dismisses.

## Files Changed

| File | Change |
|---|---|
| `BookmarkListScreen.kt` | Label delete dialog: count lookup + second string arg. Multi-select Delete: threshold check, new state var, new `AlertDialog`. |
| `LabelsBottomSheet.kt` | Label picker delete dialog: count lookup + second string arg. |
| `values/strings.xml` + 9 locale files | `delete_label_confirm_message` updated to `%2$d`; new `delete_selected_bookmarks_confirm_message`. |
| `CHANGELOG.md` | Entries under `[Unreleased] → ### Changed`. |

## Threshold Rationale

25 is low enough to catch "select all" on any meaningful library, but high enough that deliberate batch operations on small sets aren't interrupted. The threshold is a single constant in the `onClick` lambda and can be adjusted without touching the dialog logic.

## What Doesn't Change

- The ViewModel (`onDeleteSelectedBookmarks`, `onConfirmBatchDeletion`, `onCancelBatchDeletion`) is untouched.
- The snackbar + undo flow is untouched.
- Single-bookmark swipe-to-delete is untouched.
- Label deletion logic (`onDeleteLabel`) is untouched — only the confirmation dialog UI changes.
