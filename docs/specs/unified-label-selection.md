# MyDeck — Unified Label Selection Functional Specification

## 1. Objectives

### Primary Goals

* Replace inline autocomplete for label assignment.
* Unify label list UI across filtering and assignment.
* Support multi-label assignment.
* Preserve existing filter-builder behavior.
* Eliminate keyboard overlap and focus instability.
* Align with Material Design 3 surface and interaction principles.

### Non-Goals

* Changing filter logic (still single-label filter).
* Introducing hierarchical editing.
* Bulk label management redesign.

---

# 2. Core Architectural Concept

Create a reusable **Label Picker Surface** component configurable in two modes:

1. `SingleSelectMode` (Filter)
2. `MultiSelectMode` (Assignment)

Same layout.
Different interaction rules.

---

# 3. Contexts & Behavior

## 3.1 Advanced Filter Builder Sheet

### Entry Point

User taps the **Label** field in the filter builder sheet.

### Mode

`SingleSelectMode`

### Behavior

* Tap a label.
* Selected label populates the Label field.
* Label picker sheet dismisses.
* Filter not applied yet (user may combine with other criteria).
* Filter applies only when user confirms.

### Selection Rules

* Only one label may be selected.
* Selecting a new label replaces the previous one.

### Visual Indicators

* Selected row visually highlighted.
* Count pills may remain visible.
* No checkboxes or checkmarks.

### Title

`Select label`

---

## 3.2 Quick Filter by Label (if applicable)

### Mode

`SingleSelectMode`

### Behavior

* Tap a label.
* Filter immediately applies.
* Sheet dismisses.

### Visual

Same as Advanced Filter mode.

---

## 3.3 Add Bookmark Sheet

### Current Behavior to Replace

Inline autocomplete input under “Labels.”

### New Entry Pattern

Replace inline input with:

* Section header: `Labels`
* Selected labels shown as removable Assist Chips.
* Below chips: tappable row:

  * Text: `Add labels` (none selected)
  * OR `Edit labels` (if one or more selected)
  * Optional subtitle: “2 selected”
  * Trailing chevron icon.

### Interaction

* Tapping row opens Label Picker in `MultiSelectMode`.

---

## 3.4 Bookmark Details Screen

Identical to Add Bookmark pattern:

* Chips show assigned labels.
* “Edit labels” row opens Label Picker in `MultiSelectMode`.
* After dismissal, chips update immediately.

---

# 4. Label Picker Surface Specification

## 4.1 Surface Type

* Full-height modal bottom sheet OR full-screen dialog.
* Must scroll internally.
* Must handle IME insets correctly.
* Must not use dropdown overlays.

---

## 4.2 Top App Bar

### MultiSelectMode

* Leading: Back arrow.
* Title: `Select labels`
* Trailing: `Done` button.

### SingleSelectMode

* Leading: Back arrow.
* Title: `Select label`
* No Done button (optional).
* Selection dismisses sheet.

---

## 4.3 Search Field

Located below top app bar.

* Filled TextField.
* Placeholder: `Search labels`
* Leading search icon.
* Clear icon when text present.
* Real-time filtering.
* Case-insensitive substring match.

Keyboard behavior:

* Remains open during search.
* Does not auto-dismiss on selection (MultiSelectMode).

---

## 4.4 Label List

Scrollable vertical list.

Each row contains:

* Leading label icon.
* Label name.
* Optional trailing metadata (count pill, de-emphasized).

---

# 5. Mode-Specific Behavior

## 5.1 SingleSelectMode (Filter)

* Tap row selects label.
* Sheet dismisses.
* Selected row visually highlighted.
* No checkmarks.
* No multi-selection.

Used in:

* Advanced filter builder.
* Quick filter mode.

---

## 5.2 MultiSelectMode (Assignment)

* Tap row toggles selection.
* Multiple labels allowed.
* Selection indicated with trailing checkmark (preferred) OR checkbox.
* Explicit Done button required.

Used in:

* Add Bookmark.
* Bookmark Details.

---

# 6. Label Creation (MultiSelectMode Only)

When search query:

* Has no exact match, OR
* Is non-empty and not already present

Show at top of list:

`Create label "query"`

Behavior:

* Tapping creates new label.
* New label automatically selected.
* Sheet remains open.
* Search may clear or remain (implementation choice).
* No duplicate labels allowed (case-insensitive).

---

# 7. State Handling

## Entry

* Picker receives initial selected label set.
* Selected items pre-marked in MultiSelectMode.

## Exit

### MultiSelectMode

* Done commits selected labels.
* Back behaves same as Done (recommended) OR cancels (choose one and remain consistent).

### SingleSelectMode

* Tap immediately returns selection.

---

# 8. Chips Outside Picker

## Add Bookmark

* Chips reflect selected labels.
* Removing a chip updates selection state.
* Next time picker opens, state is preserved.

## Bookmark Details

* Same behavior.

---

# 9. Visual Consistency Rules

Across all modes:

* Same spacing.
* Same typography.
* Same list layout.
* Same search field style.
* Same surface elevation.

Differentiation comes from:

* Title text.
* Selection model.
* Presence/absence of Done button.
* Checkmarks vs highlight-only.

---

# 10. Interaction Differences Summary

| Context          | Mode   | Selection | Commit    | Indicator |
| ---------------- | ------ | --------- | --------- | --------- |
| Advanced Filter  | Single | One       | On Apply  | Highlight |
| Quick Filter     | Single | One       | Immediate | Highlight |
| Add Bookmark     | Multi  | Many      | Done      | Checkmark |
| Bookmark Details | Multi  | Many      | Done      | Checkmark |

---

# 11. Accessibility

* 48dp minimum touch targets.
* Screen reader announces selection state.
* Checkmarks described as “selected.”
* Search field properly labeled.

---

# 12. Benefits of This Model

* Eliminates fragile inline autocomplete.
* Prevents IME overlap issues.
* Maintains composable filter logic.
* Supports multi-label editing cleanly.
* Reuses existing label list UI.
* Aligns with Material 3 surface stability principles.
* Keeps mental models clean: filter vs edit.

---

# 13. Optional Enhancements (Future)

* Recently used labels grouping.
* Selected section pinned at top.
* Label color indicators.
* Multi-label filtering support.

