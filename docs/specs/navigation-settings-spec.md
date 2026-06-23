# Navigation Settings: Configurable Nav Drawer Views

**Date:** 2026-06-23
**Branch:** `claude/readeck-collections-nav-kq1wy2`
**Status:** Draft
**Depends on:** `collections-feature-design.md` (Collections must ship first)

---

## Overview

Today the navigation drawer shows a fixed set of views in a fixed order. This
feature adds a **Navigation** settings screen that lets the user:

1. Toggle which **standard views** appear in the drawer (My List, Archive,
   Favourites, Articles, Videos, Pictures).
2. Enable a single **custom view** backed by a user-selected Collection.
3. Enable a predefined **Offline Content** view that lists every bookmark with
   downloaded offline content, regardless of type or archive state.
4. **Reorder** views within their group.

The drawer stops being a hand-written static layout and becomes **data-driven**:
it renders from an ordered, filtered list of nav-item descriptors derived from
persisted settings. The same descriptor list also drives the wide-layout
navigation rail.

This spec assumes the Collections feature (separate spec) is already
implemented — specifically `CollectionRepository.observeCollections()`,
`BookmarkListViewModel.onSelectCollection(id)`, and the `Collection` domain
model.

---

## Rollout / Versioning

- **MyDeck v0.15.0** — first release of this feature (and Collections).
- **Readeck for Android v1.0.0** — rebrand release; ships once the production
  Play Store listing is live. Collections + Navigation Settings are **not** in
  v1.0.0.
- **Readeck for Android v1.1** — Collections and Navigation Settings are ported
  over from MyDeck.

No code in this spec may hard-code the "MyDeck" brand string in a way that
blocks the port; all user-facing labels go through string resources as usual.

---

## Current State

- **`DrawerPreset`** (`domain/model/DrawerPreset.kt`) is a 7-value enum:
  `MY_LIST, ARCHIVE, FAVORITES, ARTICLES, VIDEOS, PICTURES, HIGHLIGHTS`.
- **`FilterFormState.fromPreset(preset)`** maps each preset to a filter
  (`MY_LIST → isArchived=false`, `ARCHIVE → isArchived=true`,
  `ARTICLES → types={Article}`, …). `HIGHLIGHTS` is a special non-filter view.
- **`BookmarkListViewModel.onSelectDrawerPreset(preset)`** sets `_drawerPreset`
  and `_filterFormState`. Convenience methods `onClickMyList()` … exist per
  preset.
- **`AppDrawerContent.kt`** is fully static: every `NavigationDrawerItem` and
  every `HorizontalDivider` is hand-written, and each item takes its own
  `onClickX` lambda (10 lambdas threaded through `AppShell`).
- **`AppNavigationRailContent.kt`** mirrors the drawer for the wide layout with
  the same per-item lambdas.
- Settings persistence goes through **`SettingsDataStore`**
  (`io/prefs/SettingsDataStore.kt` + `SettingsDataStoreImpl.kt`); complex values
  are serialized to JSON-backed preference keys. Sub-screens follow the
  `UiSettingsRoute` / `UiSettingsScreen` / `UiSettingsViewModel` pattern, routed
  from `SettingsScreen` via a `ListItem` + nav event.
- Offline content is a **local-only** concept: a bookmark "has offline content"
  when a committed `content_package` row exists for it. There is **no** API
  filter or `FilterFormState` field for this. The
  `OfflineContentScope` enum (`domain/sync/ContentSyncPolicy.kt`) has
  `includesArchived: Boolean`; `MY_LIST_AND_ARCHIVED` is the value that downloads
  offline content for archived bookmarks.

---

## Design Decisions

### 1. Fixed groups, reorder-within-group only (v1)

The drawer keeps its three logical groups; dividers between non-empty groups
stay **automatic**. The user may reorder items **within** a group but not move
items across groups, and there is no user-placed divider entity in v1.

| Group        | Reorderable / toggleable items                              | Fixed items (always shown, not in this feature) |
|--------------|-------------------------------------------------------------|--------------------------------------------------|
| **Status**   | My List, Archive, Favourites                                | —                                                |
| **Type**     | Articles, Videos, Pictures, *Custom view*, *Offline Content*| —                                                |
| **Tools**    | —                                                           | Labels, Highlights                               |
| **Footer**   | —                                                           | Settings, User Guide, About                      |

Custom view and Offline Content live in the **Type** group and are reorderable
within it once enabled. Labels, Highlights, and the footer items are out of
scope for v1 toggling/reordering (noted as a possible later extension).

This is deliberately simpler than a flat user-ordered list with draggable
dividers. The persisted shape (ordered token lists) still generalizes to that
model later, so we are not painting ourselves into a corner.

### 2. Drawer becomes data-driven

`AppDrawerContent` and `AppNavigationRailContent` render from a single
`List<NavItem>` produced by the ViewModel. Each `NavItem` carries everything the
row needs (token, label, icon, badge count, selected flag, click action). The
10 individual `onClickX` lambdas collapse into one `onSelect(token)` callback.
This refactor is the largest and riskiest part of the work and should land
first, behaving identically to today before any settings are wired in.

### 3. Custom view reuses Collections selection plumbing

A custom view *is* a saved Collection. Selecting it calls the existing
`onSelectCollection(collectionId)` from the Collections spec — no new list/query
path. Only **one** custom view is supported in v1 (multiple is a future
extension).

### 4. Offline Content is a new local-only view

Because offline content cannot be expressed as a `FilterFormState`/API filter,
the Offline Content view gets a **dedicated local query path**, not a preset
filter. See "Offline Content View" below.

### 5. Constraint: at least one standard navigable view must stay visible

The toggle list must never become empty. At least one item across the **Status +
Type** standard views (My List, Archive, Favourites, Articles, Videos, Pictures)
must remain checked. Custom view and Offline Content do **not** count toward this
minimum (they are opt-in extras). Enforced by disabling the toggle on the last
remaining checked standard view.

Archive is **not** mandatory. If the user hides Archive, archived bookmarks are
still reachable by (a) re-enabling Archive in Navigation settings, or (b)
applying an `isArchived = true` filter.

---

## Data Models

### Domain model

```kotlin
// domain/model/NavView.kt

/** Stable identity for every configurable nav destination. */
enum class NavView {
    MY_LIST, ARCHIVE, FAVORITES,        // Status group
    ARTICLES, VIDEOS, PICTURES,         // Type group (standard)
    CUSTOM, OFFLINE,                    // Type group (opt-in)
}

/** Persisted navigation configuration. */
data class NavigationSettings(
    /** Visibility per standard view. Defaults all true. */
    val visible: Map<NavView, Boolean>,
    /** Order of items within the Status group. */
    val statusOrder: List<NavView>,
    /** Order of items within the Type group (includes CUSTOM/OFFLINE when enabled). */
    val typeOrder: List<NavView>,
    val customViewEnabled: Boolean,
    val customViewCollectionId: String?,
    val offlineViewEnabled: Boolean,
) {
    companion object {
        val DEFAULT = NavigationSettings(
            visible = NavView.entries.associateWith { true },
            statusOrder = listOf(NavView.MY_LIST, NavView.ARCHIVE, NavView.FAVORITES),
            typeOrder = listOf(NavView.ARTICLES, NavView.VIDEOS, NavView.PICTURES,
                               NavView.CUSTOM, NavView.OFFLINE),
            customViewEnabled = false,
            customViewCollectionId = null,
            offlineViewEnabled = false,
        )
    }
}
```

The **default** value reproduces today's drawer exactly (all standard views on,
current order, no custom/offline). Users who never open the screen see no
change.

### Render-time descriptor

```kotlin
// domain/model/NavItem.kt (UI-facing, built in the ViewModel)
data class NavItem(
    val view: NavView,
    val labelText: String,      // resolved; for CUSTOM this is the collection name
    val selected: Boolean,
    val badgeCount: Int?,       // null = no badge
)
```

`DrawerPreset` gains a value for the offline view:

```kotlin
enum class DrawerPreset { MY_LIST, ARCHIVE, FAVORITES, ARTICLES, VIDEOS, PICTURES, HIGHLIGHTS, OFFLINE }
```

`FilterFormState.fromPreset()` treats `OFFLINE` like `HIGHLIGHTS` — a special
view with no plain filter mapping (returns an empty/neutral `FilterFormState`;
the list query path is selected separately, see below).

---

## Persistence (`SettingsDataStore`)

Add to the interface (mirrors existing `get`/`save` + flow conventions):

```kotlin
val navigationSettingsFlow: StateFlow<NavigationSettings>
suspend fun getNavigationSettings(): NavigationSettings
suspend fun saveNavigationSettings(settings: NavigationSettings)
```

Store the whole `NavigationSettings` as a single JSON-serialized preference key
(consistent with how other composite settings are persisted). Deserialization
must be **forward/backward tolerant**: unknown enum names are dropped, missing
views fall back to `DEFAULT` (visible = true, appended to their group order in
canonical position). This protects the v1.1 Readeck port and any future
`NavView` additions.

---

## Offline Content View

A bookmark belongs to the Offline Content view when a committed
`content_package` row exists for it (HTML on disk), **independent of type and
archive state**. This naturally satisfies the requirement:

- Archived bookmarks appear **iff** they actually have offline content — which
  happens when the offline scope is `MY_LIST_AND_ARCHIVED`, or when the bookmark
  was manually pinned/downloaded. No extra toggle or branch is required; it falls
  out of the data.

### DAO

Add a paged, observable query alongside the existing list queries in
`BookmarkDao` (reuse the existing `LEFT JOIN content_package cp` shape and the
projection used by the standard list query so the same `BookmarkListItemEntity`
mapping applies):

```kotlin
// Selects bookmarks that have any committed content package, newest first.
// Archive-agnostic by design (no isArchived predicate).
@Query("""
    SELECT <same projection as the standard list query>
    FROM bookmarks b
    INNER JOIN content_package cp ON cp.bookmarkId = b.id
    WHERE b.isLocalDeleted = 0 AND b.state = 0
    ORDER BY b.created DESC
""")
fun observeOfflineContent(/* paging params to match existing list source */): ...
```

> Implementation note: match whatever paging/source type the standard list query
> uses (e.g. `PagingSource` or `Flow<List<…>>`) so the repository and ViewModel
> consume it through the same code path. If the standard list source is built
> via `@RawQuery` with `FilterFormState`, add a sibling raw-query builder that
> ignores filters and applies only the `content_package` join + the predicates
> above.

### ViewModel wiring

`BookmarkListViewModel` selects the list source based on the active view:

```kotlin
fun onSelectOfflineView() {
    _drawerPreset.value = DrawerPreset.OFFLINE
    _selectedCollectionId.value = null
    _filterFormState.value = FilterFormState() // neutral; not used for query
    // list source switches to bookmarkDao.observeOfflineContent()
}
```

The list-source selection is the only place that branches on `OFFLINE`:
`OFFLINE → offline query`, `selectedCollectionId != null → collection filter`,
else → `fromPreset(preset)` filter (current behaviour).

### Badge count

Reuse the existing detailed-count plumbing: there is already a count of
bookmarks with committed packages available via the sync-status counts
(`content_package` joins in `BookmarkDao`). Surface an "offline content count"
through `BookmarkCounts` for the drawer badge, or omit the badge in v1 if the
existing aggregate doesn't already expose an archive-inclusive total (decide
during implementation; a missing badge is acceptable for v1).

---

## Long Collection Name Handling

Collection names can be long (the server allows names far wider than a drawer
row or a dropdown). Names are **never truncated in storage** — only in display.

1. **Settings custom-view dropdown** (`ExposedDropdownMenu`):
   - Each menu item `Text`: `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
   - The collapsed anchor field that shows the current selection: same single
     line + ellipsis, constrained to the field width.
2. **Drawer / rail custom-view row:** the collection name is the row label;
   apply `maxLines = 1` + `TextOverflow.Ellipsis`. The modal drawer is 0.75×
   screen width, so end-ellipsis is appropriate.
3. **Accessibility:** set the full, untruncated name as the `contentDescription`
   on the dropdown item and the drawer row so screen readers announce it in full.
4. **No new length limit is imposed by the client.** We rely on the server's
   existing name constraints; the client only handles overflow visually.

Use end-ellipsis (not middle) for consistency with how labels are rendered
elsewhere in the app.

---

## UI Components

### Navigation settings screen

New route + screen + ViewModel following the `UiSettings*` pattern:

- `NavigationSettingsRoute` in `ui/navigation/Routes.kt`.
- `NavigationSettingsScreen` + `NavigationSettingsViewModel` in `ui/settings/`.
- Entry point: a `ListItem` in `SettingsScreen` ("Navigation") that emits a
  `NavigateToNavigationSettings` event handled in the `SettingsScreen`
  `LaunchedEffect` block.

Screen layout (top to bottom):

1. **Standard views** — section of checkable rows for the 6 standard views,
   grouped visually as Status / Type. Each row: checkbox + label + drag handle
   (reorder within group). The last checked standard view's checkbox is
   **disabled** (enforces the minimum-one rule) with a helper caption.
2. **Custom view** — a switch ("Show a custom view"). When on:
   - If collections exist: an `ExposedDropdownMenu` of collection names; the
     selection persists to `customViewCollectionId`.
   - If **no** collections exist: the dropdown contains a single
     **"Add a collection…"** entry that navigates to the Collections screen.
     The Collections screen is opened with a **return target** of the Navigation
     settings screen (see Back-navigation below). On return, if a collection was
     created, it becomes the selected custom view.
3. **Offline Content** — a switch ("Show Offline Content view"), default off.
   Helper caption explaining it lists all downloaded bookmarks, and that
   archived items appear when offline downloads include archived bookmarks.
4. **Reordering** — within Status and within Type, via drag handles (or
   up/down affordances if drag is too costly for v1; drag preferred for parity
   with platform conventions).

Reorder of Type group must keep Custom/Offline in `typeOrder` only while they are
enabled; when disabled they are simply filtered out at render time (their slot in
`typeOrder` is preserved so re-enabling restores position).

### Back-navigation from "Add a collection…"

When the user jumps from Navigation settings → Collections to create one, Back
must return to Navigation settings (not the bookmark list). Implement with the
nav graph: navigate to the Collections destination with `launchSingleTop`, and
on the Collections screen handle Back with `popUpTo(NavigationSettingsRoute)`
(or pass a return-route argument the Collections screen pops to). Keep the
mechanism explicit so the v1.1 Readeck port carries it over.

### Data-driven drawer / rail

- `AppDrawerContent(navItems: List<NavItem>, onSelect: (NavView) -> Unit, …)`
  renders the Status group, a divider, the Type group, a divider, then the fixed
  Tools + Footer rows (which keep their current dedicated callbacks, or are also
  expressed as fixed `NavItem`s — implementer's choice, but Labels/Highlights
  behaviour is unchanged).
- Dividers render only between **non-empty** groups.
- `AppNavigationRailContent` consumes the same `navItems`.
- `AppShell` builds/collects `navItems` from the ViewModel and passes a single
  `onSelect` that dispatches: standard view → `onSelectDrawerPreset`, `CUSTOM`
  → `onSelectCollection(customViewCollectionId)`, `OFFLINE` →
  `onSelectOfflineView`.

---

## ViewModel Changes

### `BookmarkListViewModel`

- Inject `SettingsDataStore` navigation settings (already injected for other
  prefs) and `CollectionRepository` (from Collections spec).
- Expose:

```kotlin
val navItems: StateFlow<List<NavItem>>   // built from NavigationSettings + counts + collections + selection
```

  Built by combining `navigationSettingsFlow`, `bookmarkCounts`,
  `collections`, `drawerPreset`, and `selectedCollectionId`. Filters out
  hidden/disabled views, applies per-group order, resolves the custom view's
  label to the selected collection name (ellipsised at the UI layer), and marks
  the selected item.

- Add `onSelectOfflineView()` (above) and route `CUSTOM` selection through the
  existing `onSelectCollection(id)`.
- If the configured `customViewCollectionId` no longer exists (collection
  deleted), the custom view is omitted from `navItems` and, if it was selected,
  fall back to `MY_LIST`.

### `NavigationSettingsViewModel`

- Reads/writes `NavigationSettings` via `SettingsDataStore`.
- Observes `collections` to populate the dropdown and detect the empty state.
- Enforces the minimum-one-standard-view rule (computes which toggle to disable).
- Emits a navigation event for "Add a collection…".

---

## String Resources

Add to `values/strings.xml` **and all 9 locale files** (English placeholder text)
per `CLAUDE.md`:

```xml
<string name="settings_navigation_title">Navigation</string>
<string name="settings_navigation_summary">Choose, reorder, and customise drawer views</string>
<string name="nav_settings_standard_views_header">Standard views</string>
<string name="nav_settings_min_one_view_caption">At least one view must stay visible</string>
<string name="nav_settings_custom_view_switch">Show a custom view</string>
<string name="nav_settings_custom_view_pick">Choose a collection</string>
<string name="nav_settings_custom_view_add">Add a collection…</string>
<string name="nav_settings_offline_view_switch">Show Offline Content</string>
<string name="nav_settings_offline_view_caption">Lists all bookmarks with downloaded offline content</string>
<string name="nav_view_offline_content">Offline Content</string>
<string name="nav_settings_reorder_hint">Drag to reorder</string>
```

(Final wording to be reviewed; keep names stable for the port.)

---

## Documentation

Per `CLAUDE.md`, update English user-guide files in `app/src/main/assets/guide/en/`:

- `settings.md` — new **Navigation** settings section: toggling views, the
  minimum-one rule, custom view, Offline Content, reordering.
- `your-bookmarks.md` — note that the drawer's views are configurable and that an
  Offline Content view and a custom (collection-backed) view can be added.

---

## Implementation Sequence

1. **Drawer/rail refactor to data-driven** — introduce `NavItem`, convert
   `AppDrawerContent` + `AppNavigationRailContent` + `AppShell` to render from a
   `List<NavItem>` with one `onSelect`. No behaviour change yet (build the list
   from a hardcoded default). Verify visual parity.
2. **`NavView` + `NavigationSettings` model** and `SettingsDataStore`
   persistence (JSON, tolerant deserialization).
3. **Offline Content view** — `DrawerPreset.OFFLINE`, DAO query,
   `onSelectOfflineView`, list-source branch.
4. **`navItems` builder** in `BookmarkListViewModel` (order, visibility, custom
   label resolution, selection, offline/custom dispatch, missing-collection
   fallback).
5. **Navigation settings screen + ViewModel** — toggles, reorder, custom-view
   dropdown + empty-state, offline switch, min-one enforcement.
6. **Back-navigation** from "Add a collection…" to Collections and back.
7. **Long-name handling** — ellipsis + contentDescription in dropdown and drawer.
8. **Strings** (10 locale files) + **user-guide docs**.
9. **Tests** — see below.

---

## Testing

- `NavigationSettings` (de)serialization, including dropping unknown/ missing
  `NavView` values (port-safety).
- Min-one-standard-view enforcement: cannot disable the last checked standard
  view; Custom/Offline don't count toward the minimum.
- `navItems` builder: ordering within groups, hidden views excluded,
  custom-view label = collection name, deleted-collection fallback to MY_LIST,
  selection marking.
- Offline DAO query: includes archived bookmarks that have a package, excludes
  those without, archive state ignored.
- Offline scope interaction (integration): with `MY_LIST_AND_ARCHIVED`, archived
  downloads appear in the Offline Content view; with the my-list-only scope,
  only manually pinned archived bookmarks appear.
- Back-navigation: "Add a collection…" → create → Back returns to Navigation
  settings with the new collection selectable.

---

## Out of Scope (future)

- Multiple custom views.
- Toggling/reordering Labels, Highlights, or footer items.
- User-placed dividers / fully flat ordered list.
- Cross-group reordering.
