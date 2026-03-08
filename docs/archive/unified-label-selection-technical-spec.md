# Unified Label Selection — Technical Specification

Status: Draft for implementation  
Related functional spec: `docs/specs/unified-label-selection.md`

## 1. Purpose

Define the concrete Android/Compose implementation plan for unified label selection across:

- Label browsing (single-select)
- Filter label selection (single-select)
- Add Bookmark label assignment (multi-select)
- Bookmark Details label assignment (multi-select)

This technical spec supersedes any functional-spec assumptions that do not match the current codebase.

## 2. Decisions Confirmed

The following decisions are confirmed for this implementation:

1. Single-select label sheet keeps long-press label management (rename/delete) where that behavior already exists.
2. Multi-select label picker does not expose rename/delete actions.
3. Label dedup/normalization behavior follows current code semantics (case-sensitive equality).
4. Multi-select exit behavior:
- Back = cancel (discard temporary changes in picker session)
- Done = commit selected labels

## 3. Current Code Baseline (Relevant)

- Existing single-select bottom sheet:
  - `app/src/main/java/com/mydeck/app/ui/list/LabelsBottomSheet.kt`
- Filter sheet label field and picker usage:
  - `app/src/main/java/com/mydeck/app/ui/components/FilterBottomSheet.kt`
- Add Bookmark labels UI (inline autocomplete):
  - `app/src/main/java/com/mydeck/app/ui/list/AddBookmarkSheet.kt`
- Bookmark Details labels UI (inline autocomplete):
  - `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailsDialog.kt`
- Current autocomplete component:
  - `app/src/main/java/com/mydeck/app/ui/components/LabelAutocompleteTextField.kt`

## 4. Target Architecture

## 4.1 Reusable Picker Surface

Create one reusable picker composable in place of the current single-mode sheet:

- Name: `LabelPickerBottomSheet` (rename from `LabelsBottomSheet`)
- Modes:
  - `SingleSelect`
  - `MultiSelect`
- Shared UI shell:
  - Modal bottom sheet
  - Top app bar
  - Search text field
  - Scrollable label list

## 4.2 Mode Contracts

### SingleSelect

- Input:
  - `selectedLabel: String?`
- Behavior:
  - Tap row selects immediately and dismisses
  - Selected row visually highlighted
- Optional management:
  - If rename/delete callbacks are non-null, enable long-press context menu
  - If callbacks are null, no long-press menu

### MultiSelect

- Input:
  - `selectedLabels: Set<String>`
- Behavior:
  - Tap row toggles membership
  - Checkmark shown for selected rows
  - Top app bar trailing action `Done` commits changes
  - Back dismisses without commit (cancel)
- Create-label affordance:
  - If query is non-empty and no exact match exists, show `Create label "query"` at top
  - Tapping create adds that exact query to temp selection
  - No case-insensitive normalization or dedupe beyond exact string equality

## 4.3 Suggested Composable API

```kotlin
@Composable
fun LabelPickerBottomSheet(
    labels: Map<String, Int>,
    mode: LabelPickerMode,
    onDismiss: () -> Unit,
)

sealed interface LabelPickerMode {
    data class SingleSelect(
        val selectedLabel: String?,
        val onLabelSelected: (String) -> Unit,
        val onRenameLabel: ((old: String, new: String) -> Unit)? = null,
        val onDeleteLabel: ((label: String) -> Unit)? = null,
    ) : LabelPickerMode

    data class MultiSelect(
        val initialSelection: Set<String>,
        val onDone: (Set<String>) -> Unit,
    ) : LabelPickerMode
}
```

Notes:

- Keep state local inside picker for search and multi-selection temp set.
- Multi-select state resets to `initialSelection` each time the picker opens.

## 5. Context Integrations

## 5.1 Label Browser Sheet (existing label mode)

Location: `BookmarkListScreen`

- Continue opening picker in `SingleSelect`.
- Preserve long-press rename/delete in this context.
- Keep immediate apply behavior when a label is tapped.

## 5.2 Filter Bottom Sheet

Location: `FilterBottomSheet`

- Open picker in `SingleSelect`.
- Pass rename/delete callbacks as null (selection-only context).
- Keep current filter form behavior:
  - choosing label updates local filter field
  - filter applies only on Search button

## 5.3 Add Bookmark Sheet

Location: `AddBookmarkSheet`

Replace inline autocomplete input with:

- Header `Labels`
- Existing removable chips
- Tappable row:
  - `Add labels` when none selected
  - `Edit labels` when >=1 selected
  - Subtitle with selected count (for >=1)
  - Trailing chevron

Interaction:

- Row opens picker in `MultiSelect` with current labels as initial set.
- `Done` updates `labels` via `onLabelsChange`.
- Back dismiss leaves `labels` unchanged.
- Chip remove still updates immediately.

## 5.4 Bookmark Details Dialog

Location: `BookmarkDetailsDialog`

Apply same assignment pattern as Add Bookmark:

- Chips + `Add labels`/`Edit labels` row
- Multi-select picker integration
- `Done` commits once via `onLabelsUpdate`
- Back dismiss cancels picker edits
- Chip remove still updates immediately via `onLabelsUpdate`

## 6. Material 3 and Accessibility

## 6.1 MD3 Alignment

- Use `TopAppBar` inside sheet content.
- Use MD3 list rows (`ListItem`) with consistent spacing.
- Search field includes:
  - leading search icon
  - trailing clear icon when non-empty

## 6.2 Accessibility

- Ensure rows remain at least 48dp touch height.
- Set semantics for selected state in list rows.
- Ensure create-label row and checkmark are screen-reader discoverable.

## 7. Data and Domain Impact

- No backend or DB schema changes required.
- Label creation for assignment remains implicit by including new label strings in bookmark label lists.
- Existing repository update path remains unchanged:
  - `BookmarkRepository.updateLabels(bookmarkId, labels)`

## 8. Strings / Localization

New strings expected (names indicative):

- `select_labels`
- `label_picker_done`
- `add_labels`
- `edit_labels`
- `labels_selected_count`
- `create_label_action`

Per project rule, add English placeholders to all locale `strings.xml` files.

## 9. User Guide Updates

Update English guide docs to reflect new label assignment flow:

- `app/src/main/assets/guide/en/organizing.md`
- `app/src/main/assets/guide/en/your-bookmarks.md`

## 10. Test Plan

## 10.1 UI Behavior Checks

1. Single-select:
- Selected label highlighted
- Tap selects and dismisses
- Long-press menu only appears when management callbacks exist

2. Multi-select:
- Initial selection pre-populated
- Toggle selection works
- Back cancels
- Done commits
- Create-label row appears only when query has no exact match

3. Integration:
- Add Bookmark labels update correctly before save
- Bookmark Details label updates propagate via `onLabelsUpdate`
- Filter label selection still requires Search to apply

## 10.2 Build/Quality Commands

Run after implementation:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`

## 11. Implementation Sequence

1. Refactor/replace existing `LabelsBottomSheet` into mode-based picker.
2. Integrate picker into `FilterBottomSheet` (single-select, no management).
3. Integrate picker into `BookmarkListScreen` labels sheet (single-select, management on).
4. Replace Add Bookmark inline label input with chips + picker row + multi-select flow.
5. Replace Bookmark Details inline label input with chips + picker row + multi-select flow.
6. Add/propagate string resources in all locales.
7. Update English user guide docs.
8. Run required Gradle verification tasks.
