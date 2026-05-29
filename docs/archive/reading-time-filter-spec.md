# Length Filters — Reading Time & Word Count

## Overview

Adds two local filters to the bookmark list:

- **Reading time** — the per-bookmark `readingTime` estimate (minutes) from Readeck.
- **Word count** — the per-bookmark `wordCount` value from Readeck.

Each filter accepts a **Min** and/or **Max** integer bound and an **Include unknown** option that captures bookmarks where the field is `NULL` (typical for very short articles, videos, and pictures — Readeck does not estimate reading time for sub-minute content). Filtering is applied locally against the Room database; no parameter is sent to the Readeck server (consistent with other filters except the initial search field).

Both filters share a single UI surface — a **Length** picker on the filter sheet that opens an `AlertDialog`. This consolidation keeps the bottom sheet uncluttered and sidesteps a Material3 `ModalBottomSheet` IME-handling quirk (see *Implementation Notes* at the bottom).

---

## UI — Filter Bottom Sheet

### Length Picker

A single read-only `OutlinedTextField` placed immediately after the From Date / To Date row and before the Type chips section. Visual idiom mirrors the existing Date and Label pickers in this sheet:

| Element | Treatment |
|---|---|
| **Label** | `"Length"` |
| **Value** (empty state) | `value = ""` + `placeholder = "Any reading time / any word count"` (muted `onSurfaceVariant`) |
| **Value** (active state) | Summary string built from the two range filters (see *Summary String* below) |
| **Trailing icon** (active state only) | `Icons.Filled.Clear` (X) — one tap clears all six values without opening the dialog |
| **Modifier** | `enabled = false` + `pickerColors` so the field reads as enabled, and `Modifier.clickable { showLengthFilterDialog = true }` |

#### Summary String

The picker value is built from the dialog's six current values:

```
Read time: ≥{min} min            // min only
Read time: ≤{max} min            // max only
Read time: {min}–{max} min       // min + max
Read time: unknown               // include unknown only
Read time: {range} + unknown     // range + include unknown
```

Same format for word count, prefix `Words:`, no unit suffix:

```
Words: ≥{min}
Words: 100–500
Words: 100–500 + unknown
```

When both filters are active the parts are joined with ` / `, e.g.:

```
Read time: 5–15 min / Words: ≥500
```

When no length filter is active, the summary is `null` and the placeholder text is shown instead.

### Length Filter Dialog

Tapping the Length picker opens an `AlertDialog` with the following structure:

```
┌──────────────────────────────────────┐
│ Length filters                       │
│                                      │
│ Reading Time Est.                    │
│ ┌──────┐ ┌──────┐                    │
│ │ Min  │ │ Max  │                    │
│ └──────┘ └──────┘                    │
│ ☐ Include unknown                    │
│ Min must be less than or equal to max│ ← shown only on error
│                                      │
│ Word Count                           │
│ ┌──────┐ ┌──────┐                    │
│ │ Min  │ │ Max  │                    │
│ └──────┘ └──────┘                    │
│ ☐ Include unknown                    │
│                                      │
│ Clear                                │ ← disabled when nothing to clear
│                                      │
│            [ Cancel ]  [ OK ]        │
└──────────────────────────────────────┘
```

#### Dialog Structure

- `title`: `"Length filters"`.
- `text`: A scrollable `Column(verticalArrangement = Arrangement.spacedBy(16.dp))` containing:
  1. Reading Time `LengthRangeSection`.
  2. Word Count `LengthRangeSection`.
  3. `TextButton(onClick = clearDialog, enabled = hasAnyValue, modifier = Modifier.align(Alignment.Start))` labelled `"Clear"`.
- `confirmButton`: `TextButton` labelled `"OK"`, `enabled = !hasValidationError`. Calls `onApply(...)` with the six current values.
- `dismissButton`: `TextButton` labelled `"Cancel"`. Calls `onDismiss`.
- `modifier = Modifier.imePadding()` so the dialog content respects the IME inset.

#### `LengthRangeSection`

Each section is a private composable rendering:

- Section title (`labelLarge`, e.g. `"Reading Time Est."` / `"Word Count"`).
- Row with two `OutlinedTextField`s (`Modifier.weight(1f)` each):
  - `keyboardType = KeyboardType.Number`, digits-only filter on `onValueChange`.
  - **Min** field: `imeAction = ImeAction.Next`.
  - **Max** field: `imeAction = ImeAction.Next` (Reading Time, focus moves to Word Count) or `ImeAction.Done` (Word Count, triggers apply via `onDone`).
  - `isError = hasError` (this section's error flag only).
  - Each field gets a `Modifier.bringFocusedFieldIntoView()` modifier — a `BringIntoViewRequester` driven by an `onFocusEvent` listener that calls `bringIntoView()` after a `FOCUSED_FIELD_BRING_INTO_VIEW_DELAY_MS` (250 ms) delay, allowing the IME animation to complete before scroll.
- Toggleable row containing the **Include unknown** Checkbox + label:
  ```kotlin
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
          .fillMaxWidth()
          .toggleable(value = includeNull, onValueChange = onIncludeNullChange, role = Role.Checkbox)
  ) {
      Checkbox(checked = includeNull, onCheckedChange = null)
      Text(stringResource(R.string.filter_length_include_unknown))
  }
  ```
  The whole row toggles the checkbox; screen readers announce a single checkbox control.
- Error text below the row when the section is invalid:
  ```
  Min must be less than or equal to max
  ```

#### Validation

Per section, evaluated whenever Min or Max changes:

- **Error condition:** both Min and Max are non-empty *and* `min.toInt() > max.toInt()`.
- **Effect:** error text appears in `MaterialTheme.colorScheme.error` / `typography.bodySmall`; the section's `OutlinedTextField`s show `isError = true`; the dialog's **OK** button is disabled (gate is `readingTimeError || wordCountError`).
- **No error:** one or both fields empty, or `min <= max`.

#### Clear / Reset Behavior

The dialog has its own **Clear** button (resets only the six length values inside the dialog without dismissing). Outside the dialog, two other resets exist:

- The Length picker's trailing **X** icon — clears all six values immediately, without opening the dialog.
- The filter sheet's bottom **Reset** button — clears all filters in the sheet (length included).

#### Truth Table — `Include unknown`

The same truth table applies independently to Reading Time and Word Count. Listed here for the `readingTime` column; word count is identical with `wordCount` substituted.

| Min | Max | Include unknown | SQL effect |
|---|---|---|---|
| — | — | false | No filter |
| set | set | false | `b.readingTime BETWEEN min AND max` |
| set | — | false | `b.readingTime >= min` |
| — | set | false | `b.readingTime <= max` |
| — | — | true | `b.readingTime IS NULL` |
| set | set | true | `(b.readingTime BETWEEN min AND max OR b.readingTime IS NULL)` |
| set | — | true | `(b.readingTime >= min OR b.readingTime IS NULL)` |
| — | set | true | `(b.readingTime <= max OR b.readingTime IS NULL)` |

Bookmarks with `IS NULL` are excluded from range results unless **Include unknown** is checked.

---

## UI — Filter Bar (active filter chips)

Two independent chips are emitted — one for reading time, one for word count — whenever any of that filter's three values is active. Each chip can be dismissed without affecting the other filter.

### Reading time chip

| State | Chip label |
|---|---|
| Min only | `Read time: ≥{min} min` |
| Max only | `Read time: ≤{max} min` |
| Min + Max | `Read time: {min}–{max} min` |
| Include unknown only | `Read time: unknown` |
| Range + unknown | `Read time: {range} + unknown` |

Dismissing clears `minReadingTime`, `maxReadingTime`, `includeNullReadingTime`.

### Word count chip

Same format, prefix `Words:`, no unit suffix:

| State | Chip label |
|---|---|
| Min + Max | `Words: {min}–{max}` |
| Range + unknown | `Words: {range} + unknown` |

Dismissing clears `minWordCount`, `maxWordCount`, `includeNullWordCount`.

---

## Model — `FilterFormState`

Add six fields:

```kotlin
val minReadingTime: Int? = null,
val maxReadingTime: Int? = null,
val includeNullReadingTime: Boolean = false,
val minWordCount: Int? = null,
val maxWordCount: Int? = null,
val includeNullWordCount: Boolean = false,
```

Update `hasActiveFilters()`:

```kotlin
minReadingTime != null || maxReadingTime != null || includeNullReadingTime ||
minWordCount != null || maxWordCount != null || includeNullWordCount
```

No change to `fromPreset()` — all six default to inactive.

---

## Data Layer — `BookmarkDao`

The SQL clause is applied at three call sites for parity: `getFilteredBookmarkListItems` (primary path used by the ViewModel), `getBookmarkListItemsByFilters`, and `getBookmarksByFilters`.

Add six parameters to each function:

```kotlin
minReadingTime: Int? = null,
maxReadingTime: Int? = null,
includeNullReadingTime: Boolean = false,
minWordCount: Int? = null,
maxWordCount: Int? = null,
includeNullWordCount: Boolean = false,
```

Append two parallel clauses to each dynamic query builder (after existing clauses, before `ORDER BY`). The pattern below shows the reading-time clause for the `b.readingTime` aliased query; the word-count clause is the same with `wordCount` substituted, and the simpler `getBookmarksByFilters` query uses the unaliased column name (`readingTime` / `wordCount`):

```kotlin
val hasRange = minReadingTime != null || maxReadingTime != null
if (hasRange || includeNullReadingTime) {
    val rangeParts = buildList {
        if (hasRange) {
            val rangeSql = buildString {
                if (minReadingTime != null) { append("b.readingTime >= ?"); args.add(minReadingTime) }
                if (minReadingTime != null && maxReadingTime != null) append(" AND ")
                if (maxReadingTime != null) { append("b.readingTime <= ?"); args.add(maxReadingTime) }
            }
            add(rangeSql)
        }
        if (includeNullReadingTime) add("b.readingTime IS NULL")
    }
    append(" AND (${rangeParts.joinToString(" OR ")})")
}
```

---

## Data Layer — `BookmarkRepositoryImpl`

Add the six parameters to both `observeBookmarkListItems(...)` and `observeFilteredBookmarkListItems(...)` and thread them through to the DAO calls. The `BookmarkListViewModel` passes them in directly from `FilterFormState`.

---

## String Resources

Add to `app/src/main/res/values/strings.xml` and all nine language files (English placeholder in each):

```xml
<!-- Length picker + dialog -->
<string name="filter_length">Length</string>
<string name="filter_length_dialog_title">Length filters</string>
<string name="filter_length_summary_none">Any reading time / any word count</string>
<string name="filter_length_include_unknown">Include unknown</string>
<string name="filter_length_unknown">unknown</string>
<string name="filter_length_clear">Clear</string>

<!-- Reading time section + chip -->
<string name="filter_reading_time_est">Reading Time Est.</string>
<string name="filter_reading_time_min">Min</string>
<string name="filter_reading_time_max">Max</string>
<string name="filter_reading_time_null">Null</string>          <!-- retained, no longer surfaced in UI -->
<string name="filter_reading_time_error">Min must be less than or equal to max</string>
<string name="filter_reading_time">Read time</string>

<!-- Word count section + chip -->
<string name="filter_word_count_est">Word Count</string>
<string name="filter_word_count">Words</string>
```

Language files to update:
- `values-de-rDE`, `values-es-rES`, `values-fr`, `values-gl-rES`, `values-pl`, `values-pt-rPT`, `values-ru`, `values-uk`, `values-zh-rCN`

---

## User Guide

`app/src/main/assets/guide/en/your-bookmarks.md` documents a **Length** section under Filter, describing the picker, the dialog, the Min / Max / Include unknown semantics, and chip dismissal behavior.

---

## Implementation Notes

### Why a dialog instead of inline rows in the sheet

The first attempt placed the Min/Max fields directly in the bottom sheet (one row each for Reading Time and Word Count). That ran into a Material3 `ModalBottomSheet` behavior where the sheet auto-collapses to its `PartiallyExpanded` anchor when the IME opens for a low-positioned field — leaving the field hidden under the keyboard. Each workaround had a trade-off:

| Workaround | Problem |
|---|---|
| `skipPartiallyExpanded = true` | Sheet opens fully expanded; drag handle becomes hard to notice. |
| `confirmValueChange` to lock `PartiallyExpanded` | Visible "drop then re-expand" flash because the anchor recompute happens before the override can fire. |
| Explicit `sheetState.expand()` on focus | Same flash — `expand()` animates ~300ms, the partial-anchor snap happens earlier. |
| `sheetState.snapTo(Expanded)` | `snapTo` is `internal` in Material3 BOM 2025.04.01; not callable. |

Hoisting the editing UI into an `AlertDialog` dissolves the problem entirely: the dialog hosts its own `Window` with native soft-input handling, the bottom sheet keeps its partial-anchor opening (drag handle visible), and the Length picker is the same idiom as the existing Date and Label pickers in this sheet.

### Why "unknown" rather than "null"

`null` is precise but technical. End-users see bookmarks without a reading-time estimate or word count as "unknown" or "missing" — "Include unknown" reads naturally without explaining what null means. The internal API still uses `includeNull*` for the boolean.

### Chip independence

The two chips are independent so a user can clear "Read time: 5–15 min" without losing "Words: ≥500" — useful when iterating on a search. The dialog itself edits both at once because they're conceptually peer "length" measures.

---

## Out of Scope

- No server-side filter parameter is sent to Readeck; filtering is local only.
- No new sort option — sort-by-duration already exists.
- Tablet/landscape layout — both the picker (full-width OutlinedTextField) and the dialog reflow naturally.
