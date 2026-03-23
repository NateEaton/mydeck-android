# Filter UI — "Filtered List" Title & Synthetic Chips

**Status:** Active
**Target:** v0.11.x (current development branch)

## Summary

This spec defines the behaviour of the bookmark list top-bar title and the filter
chip bar when the active filter state deviates from the drawer-preset defaults.
The goal is to ensure users always understand that they are viewing a filtered
result set and always have an obvious path back to a standard view.

## Problems Addressed

1. **Misleading title.** Starting from "My List" and broadening filters (e.g.
   setting *Is archived* to N/A) shows all bookmarks, but the title still said
   "My List".
2. **Chipless filtered state.** Some deviations from the preset (clearing a
   boolean constraint to N/A, or deselecting all types) produce no visible chip,
   leaving the user in "Filtered List" with no explanation and no chip-based
   escape hatch.
3. **Chip removal ambiguity.** Removing the last explicit type chip from a
   type-preset view (e.g. Articles) left the list in an all-types state with no
   chip, rather than returning to the preset.

## Design Principles

- **Title reflects current state, not navigation history.** The title is derived
  from the current `FilterFormState` vs. the current `DrawerPreset`.
- **Every deviation is visible as a chip.** If `Filtered List` is showing, at
  least one chip must explain why.
- **Normal chip removal is literal.** Dismissing a chip clears only that one
  filter dimension. No implicit side-effects.
- **Synthetic chips restore the preset dimension.** Dismissing a synthetic
  "broadened scope" chip restores the preset value for that one dimension,
  keeping all other filters intact.
- **Reset restores the full preset.** The bottom-sheet Reset button returns
  the entire filter form to the drawer-preset defaults.
- **Drawer selection is authoritative.** Selecting a drawer preset always
  reinitialises the filter form to that preset's defaults.

## Title Rule

```
if (isLabelMode)       → show label name
else if (filterFormState == FilterFormState.fromPreset(drawerPreset))
                       → show preset title  (My List, Archive, etc.)
else                   → show "Filtered List"
```

No sticky history. Title is purely a function of current state.

## Chip Rules

### Existing explicit chips (unchanged)

These appear when a filter field has a non-default value and are already
implemented:

| Condition | Chip label | Dismiss action |
|---|---|---|
| `search != null` | Search: *value* | Clear search |
| `title != null` | Title: *value* | Clear title |
| `author != null` | Author: *value* | Clear author |
| `site != null` | Site: *value* | Clear site |
| `label != null` | Label: *value* | Clear label |
| `fromDate != null` | From date: *value* | Clear fromDate |
| `toDate != null` | To date: *value* | Clear toDate |
| Type added beyond preset | Type: Article/Video/Picture | Remove that type |
| Progress selected | Progress: Unviewed/In progress/Completed | Remove that progress |
| `isFavorite` differs from preset **and is non-null** | Is favorite: Yes/No | Restore preset isFavorite |
| `isArchived` differs from preset **and is non-null** | Is archived: Yes/No | Restore preset isArchived |
| `isLoaded != null` | Is downloaded: Yes/No | Clear isLoaded |
| `withLabels != null` | With labels: Yes/No | Clear withLabels |
| `withErrors != null` | With errors: Yes/No | Clear withErrors |

### New synthetic chips

These appear when a preset constraint has been **broadened** (cleared to N/A or
emptied), creating a deviation from the preset that no existing chip represents.

| Condition | Chip label | Dismiss action |
|---|---|---|
| `isArchived == null` AND preset.isArchived is non-null | Is archived: N/A | Restore `isArchived` to preset value |
| `isFavorite == null` AND preset.isFavorite is non-null | Is favorite: N/A | Restore `isFavorite` to preset value |
| `types` is empty AND preset.types is non-empty | Type: Any | Restore `types` to preset value |

### Mutual exclusion

- **isArchived**: At most one chip shows — either the explicit Yes/No chip
  (when non-null and different from preset) or the synthetic N/A chip (when
  null and preset is non-null). Never both.
- **isFavorite**: Same rule as isArchived.
- **Type: Any** vs explicit type chips: `Type: Any` only appears when
  `types` is empty. If the user selects any specific types, explicit type
  chips appear instead.

## Chip Removal Walkthrough

### My List (preset: isArchived = false)

| User action | Filter state | Title | Chips |
|---|---|---|---|
| Start | isArchived=false | My List | (none) |
| Set archived → N/A | isArchived=null | Filtered List | Is archived: N/A |
| Add label "Work" | isArchived=null, label=Work | Filtered List | Is archived: N/A · Label: Work |
| Dismiss Label: Work | isArchived=null | Filtered List | Is archived: N/A |
| Dismiss Is archived: N/A | isArchived=false | My List | (none) |

### Articles (preset: types = {Article})

| User action | Filter state | Title | Chips |
|---|---|---|---|
| Start | types={Article} | Articles | (none) |
| Add Video | types={Article,Video} | Filtered List | Type: Video |
| Dismiss Type: Video | types={Article} | Articles | (none) |

| User action | Filter state | Title | Chips |
|---|---|---|---|
| Start | types={Article} | Articles | (none) |
| Deselect Article | types={} | Filtered List | Type: Any |
| Select Video | types={Video} | Filtered List | Type: Video |
| Dismiss Type: Video | types={} | Filtered List | Type: Any |
| Dismiss Type: Any | types={Article} | Articles | (none) |

### Favorites (preset: isFavorite = true)

| User action | Filter state | Title | Chips |
|---|---|---|---|
| Start | isFavorite=true | Favorites | (none) |
| Set favorite → N/A | isFavorite=null | Filtered List | Is favorite: N/A |
| Add With errors: Yes | isFavorite=null, withErrors=true | Filtered List | Is favorite: N/A · With errors: Yes |
| Dismiss Is favorite: N/A | isFavorite=true, withErrors=true | Favorites (**no** — still differs) | With errors: Yes |
| Dismiss With errors: Yes | isFavorite=true | Favorites | (none) |

Note: In the Favorites example, after dismissing the synthetic chip, the title
may or may not revert depending on whether other chips remain. The title is
always derived from state, not from chip count.

## Removed preset types (informational)

From Articles, if the user changes types from {Article} to {Video, Picture},
the chip bar shows "Type: Video" and "Type: Picture" (added beyond preset).
The fact that Article was *removed* from the preset set is not shown as a
separate chip; the title "Filtered List" plus the visible type chips are
sufficient. Removing both type chips would leave types empty, surfacing the
"Type: Any" synthetic chip, from which the user can return to the preset.

## String Resources

### New strings (all locales)

| Key | Default (en) | Notes |
|---|---|---|
| `filtered_list` | Filtered List | Already added |
| `filter_type_any` | Any | Used in "Type: Any" chip |

The synthetic boolean chips reuse existing strings:
- `filter_is_archived` + `tri_state_any` → "Is archived: N/A"
- `filter_is_favorite` + `tri_state_any` → "Is favorite: N/A"

### Renamed strings (already applied)

| Key | Old value | New value |
|---|---|---|
| `filter_is_loaded` | Downloaded | Is downloaded |

## Files Changed

| File | Change |
|---|---|
| `FilterFormState.kt` | `differsFromPreset()` helper (already added) |
| `BookmarkListScreen.kt` | Title derivation logic (already added) |
| `FilterBar.kt` | Synthetic chip logic for isArchived N/A, isFavorite N/A, Type: Any |
| `strings.xml` (all locales) | `filtered_list` (done), `filter_type_any` (new) |
| `guide/en/your-bookmarks.md` | Updated filter documentation |

## Out of Scope

- **Label mode.** Label-based views are unaffected by this spec.
- **Sticky Filtered List.** The title is not sticky; it reflects current state.
- **"All bookmarks" chip.** Rejected in favour of dimension-specific synthetic
  chips to avoid contradictions like "All bookmarks" + "Label: Work".
- **Magic chip removal.** Removing an explicit chip does not implicitly jump
  back to the preset. The return path is always via synthetic chip dismissal,
  Reset, or drawer selection.

## Testing

- Manual verification of all walkthrough scenarios above.
- Verify `differsFromPreset()` correctly detects each deviation type.
- Verify title changes reactively as chips are added/removed.
- Verify synthetic chips never co-exist with explicit chips for the same
  dimension (isArchived, isFavorite, types).
- Run `assembleDebugAll`, `testDebugUnitTestAll`, `lintDebugAll`.
