# Reading Time Filter — Spec

## Overview

Add a reading time range filter to the Filter bottom sheet. Users can specify a minimum and/or maximum reading time in minutes, optionally including bookmarks that have no reading time estimate. The filter is applied locally against the `readingTime` column already stored in the Room database.

---

## UI — Filter Bottom Sheet

### Placement

Insert the new row immediately after the existing From Date / To Date row and before the Type chips section. The visual structure mirrors the date row: an external section label above a single `Row`.

```
Reading Time Est.
┌──────────┐  ┌──────────┐  ☐ Null
│   Min    │  │   Max    │
└──────────┘  └──────────┘
Min must be less than or equal to max   ← shown only on error
```

### Section Label

`Text("Reading Time Est.", style = MaterialTheme.typography.labelLarge)`

Preceded by `Spacer(12.dp)` (matching the spacer used between the date row and Type section, which is currently `16.dp` — adjust the split to `12.dp` above the label and `8.dp` below, consistent with the Type/Progress sections).

### Row Layout

A single `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp))`:

| Element | Weight / Size | Notes |
|---|---|---|
| **Min** `OutlinedTextField` | `weight(1f)` | `label = "Min"`, `keyboardType = Number`, `imeAction = Next`, digits only, no negatives |
| **Max** `OutlinedTextField` | `weight(1f)` | `label = "Max"`, `keyboardType = Number`, `imeAction = Search` → triggers apply |
| **Null** checkbox + label | fixed, `wrapContentWidth` | `Checkbox` + `Text("Null")` in a nested `Row(verticalAlignment = CenterVertically)` |

Both text fields accept integers ≥ 0 with no enforced upper bound. Empty string means "not set" (treated as `null`).

### Validation

Evaluated whenever either numeric field changes:

- **Error condition:** both Min and Max are non-empty and `min.toInt() > max.toInt()`
- **Effect:** error text `"Min must be less than or equal to max"` appears below the row in `MaterialTheme.colorScheme.error` / `typography.bodySmall`; the **Search** button is disabled
- **No error:** one or both fields empty, or `min <= max`

### Null Checkbox Behavior

| Min | Max | Null checked | SQL effect |
|---|---|---|---|
| — | — | false | No reading time filter |
| set | set | false | `b.readingTime BETWEEN min AND max` |
| set | — | false | `b.readingTime >= min` |
| — | set | false | `b.readingTime <= max` |
| — | — | true | `b.readingTime IS NULL` |
| set | set | true | `(b.readingTime BETWEEN min AND max OR b.readingTime IS NULL)` |
| set | — | true | `(b.readingTime >= min OR b.readingTime IS NULL)` |
| — | set | true | `(b.readingTime <= max OR b.readingTime IS NULL)` |

Bookmarks with `readingTime IS NULL` are excluded from range results unless the Null checkbox is checked.

### Clear / Reset Behavior

- Tapping the existing **Reset** button clears all three fields (Min, Max, Null checkbox) along with all other filters.
- There is no per-field clear icon on the numeric fields (they are free-text and easily cleared by the user). The Null checkbox unchecks to clear itself.

---

## UI — Filter Bar (active filter chips)

A single chip is emitted for the reading time filter whenever any of the three values is active. The chip label is built as follows:

| State | Chip label |
|---|---|
| Min only | `Read time: ≥{min} min` |
| Max only | `Read time: ≤{max} min` |
| Min + Max | `Read time: {min}–{max} min` |
| Null only | `Read time: Null` |
| Range + Null | `Read time: {min}–{max} min + Null` |
| Min + Null (no max) | `Read time: ≥{min} min + Null` |
| Max + Null (no min) | `Read time: ≤{max} min + Null` |

Dismissing the chip clears all three values (`minReadingTime = null`, `maxReadingTime = null`, `includeNullReadingTime = false`).

---

## Model — `FilterFormState`

Add three fields:

```kotlin
val minReadingTime: Int? = null,
val maxReadingTime: Int? = null,
val includeNullReadingTime: Boolean = false,
```

Update `hasActiveFilters()`:

```kotlin
minReadingTime != null || maxReadingTime != null || includeNullReadingTime ||
```

No change needed to `fromPreset()` — all three default to "inactive".

---

## Data Layer — `BookmarkDao`

### `getBookmarkListItemsByFilters`

Add parameters:

```kotlin
minReadingTime: Int? = null,
maxReadingTime: Int? = null,
includeNullReadingTime: Boolean = false,
```

Append to the dynamic query builder after the existing `label` clause:

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

Apply the same pattern to `getBookmarksByFilters` (the `BookmarkEntity` variant used by other callers) for consistency.

---

## Data Layer — `BookmarkRepositoryImpl`

Add the three parameters to `observeBookmarkListItems(...)` and thread them through to the DAO call.

---

## String Resources

Add to `app/src/main/res/values/strings.xml` and all nine language files (English placeholder in each):

```xml
<string name="filter_reading_time_est">Reading Time Est.</string>
<string name="filter_reading_time_min">Min</string>
<string name="filter_reading_time_max">Max</string>
<string name="filter_reading_time_null">Null</string>
<string name="filter_reading_time_error">Min must be less than or equal to max</string>
<string name="filter_reading_time">Read time</string>
```

Language files to update:
- `values-de-rDE`, `values-es-rES`, `values-fr`, `values-gl-rES`, `values-pl`, `values-pt-rPT`, `values-ru`, `values-uk`, `values-zh-rCN`

---

## User Guide

Update `app/src/main/assets/guide/en/your-bookmarks.md` to document the new filter under the Filter section. Describe:

- The Min / Max fields accept whole numbers (minutes); either or both may be left blank
- Checking Null includes bookmarks that have no reading time estimate; checking Null with both fields blank returns only those bookmarks
- The filter chip in the bar shows the active range and dismisses all three values when tapped

---

## Out of Scope

- No server-side filter parameter is sent to Readeck; filtering is local only (consistent with all other filters except the initial search field).
- No change to sort options — duration sort already exists.
- Tablet layout requires no special treatment; the row will reflow the same as the date row on wider screens.
