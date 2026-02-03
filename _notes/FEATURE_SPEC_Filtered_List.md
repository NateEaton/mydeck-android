# MyDeck Android — Filtered List (Revised Spec)

## 1. Conceptual Model (Key Shift)

Filtering is **not a mode layered onto every list**.

Instead:

* Filtering is a **distinct list view**
* Entered explicitly via navigation
* With its own title, state, and visual affordances

This mirrors Readeck’s mental model more closely and avoids overloading the existing list screens.

---

## 2. Navigation & Entry Point

### Side Menu

Add a new menu item:

```
My List
Archive
Favorites
────────────
Filtered List
```

### Behavior

* Selecting **Filtered List**:

  * Navigates to the standard bookmark list screen
  * Applies *no filters initially*
  * Opens the **filter panel automatically**
  * Header title becomes: **“Filtered List”**
  * Side menu highlights **Filtered List**

This view is *always* filter-capable; other list views are not.

---

## 3. Header & Top-Level UI

### Header

* Title: **Filtered List**
* No magnifying glass search
* No filter icon in the app bar

Filtering controls live **below the header**, not inside it.

---

## 4. Filter Panel Interaction

### Initial Entry

* Filter panel is fully expanded on first entry
* No results are shown until filters are applied
  *(optional but recommended to avoid “why is this empty?” confusion)*

### Apply Flow

1. User selects filter options
2. Taps **Apply**
3. Filter panel collapses into a **single summary bar**
4. Bookmark list appears below

### Collapsed State (Summary Bar)

The collapsed bar:

* Lives directly under the header
* Shows a concise summary, e.g.:

```
Filters: Unread · Has Content · Has Errors
```

* Is tappable
* Tapping expands the full filter panel again

This replaces the need for a filter icon entirely.

---

## 5. Filter Semantics

### Logical Model

* **All selected filters are ANDed**
* A bookmark must match *every active filter* to appear

No OR logic in Phase 1.

---

## 6. Initial Filter Set (Updated)

### Phase 1 Fields

| Filter      | Control           | Notes                             |
| ----------- | ----------------- | --------------------------------- |
| Read status | Chips / segmented | Unread / Read                     |
| Archived    | Toggle            | Archived only                     |
| Favorites   | Toggle            | Favorites only                    |
| Has content | Toggle            | Offline/article content exists    |
| Has errors  | Toggle            | Content fetch or processing error |

### Explicitly Excluded

* Sort order
* Title / label / site search
* Collections
* Scheduling / “read later”

---

## 7. Filter Panel Layout (Expanded)

```
---------------------------------
| Filter bookmarks               |
|--------------------------------|
| Read status: [Unread][Read]
| Archived:    [ toggle ]
| Favorites:   [ toggle ]
| Has content: [ toggle ]
| Has errors:  [ toggle ]
|--------------------------------|
| [Clear]            [Apply]     |
---------------------------------
```

### Clear

* Resets all fields to defaults
* Keeps panel open
* Clears results if already applied

---

## 8. State & Architecture

### Filter State Model

```kotlin
data class BookmarkFilter(
    val readStatus: ReadStatus? = null,
    val archivedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val hasContentOnly: Boolean = false,
    val hasErrorsOnly: Boolean = false
)
```

> `null` readStatus = no constraint

### ViewModel Responsibilities

* Owns:

  * `filterState`
  * `isApplied`
  * `isExpanded`
* Applies filters **only when Apply is pressed**
* Collapsing the panel does *not* clear filters

---

## 9. List Pipeline

Filtering is performed in the ViewModel:

```
Repository Flow<List<Bookmark>>
 → applyFilters(filterState)
 → UI
```

Suggested mappings:

* **Has content** → `bookmark.article != null`
* **Has errors** → `bookmark.state == ERROR` (or equivalent)
* **Unread** → `bookmark.isRead == false`
* **Favorites** → `bookmark.isFavorite == true`

---

## 10. UI Implementation Notes (Compose)

### Panel Mechanics

Recommended:

* `AnimatedVisibility` with height animation
* Summary bar is a separate composable rendered when collapsed

Avoid:

* Modal bottom sheets
* Scrollable filter content

### Configuration Changes

* Filter state survives rotation
* Expanded/collapsed state survives rotation
* Navigating away clears filters (intentional, since this is a dedicated view)

---

## 11. Non-Goals (Restated)

* No filter persistence across app restarts
* No interaction with existing search
* No partial-match / OR logic
* No server-side filtering

---

## 12. Future Expansion (Still Clean)

This structure allows:

* Adding fields without changing navigation
* Replacing summary bar text with icons later
* Converting applied filters into a saved collection
* Adding “Search within filtered list” later if desired

---

### Why this direction works

* Keeps **My List / Archive / Favorites** conceptually pure
* Makes filtering intentional instead of accidental
* Avoids UI clutter in the app bar
* Scales cleanly as filters grow

