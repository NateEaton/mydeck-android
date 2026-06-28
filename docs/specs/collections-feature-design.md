# Collections Feature: Design and Implementation Plan

**Date:** 2026-02-19
**Branch:** `claude/bookmark-features-design-C1m9G`
**Status:** Draft

---

## Overview

Collections allow users to save named sets of filter criteria as persistent, server-side entities. Users browse and create them on a dedicated **Collections screen** (reached from a "Collections" entry in the navigation drawer's Tools group), and selecting one opens the bookmark list as an **active-collection view** showing only bookmarks matching those saved filters. A single collection may also be surfaced as a custom view selector in the drawer/rail (see the Navigation Settings spec). Collections are managed server-side via the Readeck API and cached locally in Room for offline availability and reactive UI updates.

> **UI note (2026-06-28):** This document's original UI sketch placed collections as an inline list inside the navigation drawer with per-item actions (including Duplicate). That was superseded by the screen-based design below, agreed with the maintainer. The data / repository / ViewModel layers (slice C1) are unaffected; only the UI sections changed. See `collections-nav-rollout-plan.md` §11.

---

## Current State

The existing `FilterFormState` domain model (`domain/model/FilterFormState.kt`) holds all active filter parameters — search, title, author, site, label, date range, type, progress, archive/favourite state — and is managed by `BookmarkListViewModel`. The `AppDrawerContent` composable (`ui/shell/AppDrawerContent.kt`) displays navigation presets (My List, Archive, Favourites, Articles, Videos, Pictures) and labels as tappable items. There is no current mechanism for saving or restoring custom filter sets.

---

## Design Decisions

### 1. FilterFormState is Not Modified

The original spec proposed adding `collectionId: String?` to `FilterFormState`. This is rejected. `FilterFormState` is a pure filter-parameter value object; mixing in an identity reference couples two concerns. Instead, `BookmarkListViewModel` will hold a separate `selectedCollectionId: String?` state alongside the filter state. A filter applied from a collection is indistinguishable from a manually-entered filter once applied — collections are just a save/load mechanism for filter state.

### 2. Deletion Via PATCH, Not a DELETE Endpoint

The Readeck API does not expose a `DELETE /bookmarks/collections/{id}` endpoint. Deletion is soft — send `PATCH /bookmarks/collections/{id}` with `{ "is_deleted": true }`. The local Room entity will be removed from the cache upon a successful response.

### 3. Local-Only Filter Fields Are Dropped on Persistence

Three `FilterFormState` fields have no API equivalent: `isLoaded`, `withLabels`, and `withErrors`. These are silently omitted when creating or updating a collection. When loading a collection from the API these fields default to `null`.

### 4. Separate `CollectionRepository`

Collection logic goes into a dedicated `CollectionRepository` rather than extending the already-large `BookmarkRepository`. The ViewModel injects both.

### 5. Pagination

The `GET /bookmarks/collections` endpoint supports pagination via `limit`/`offset`. The refresh implementation should page through all results (e.g. 100 per page) and replace the local cache atomically.

### 6. UI: dedicated Collections screen, not an inline drawer list

Collections are browsed and created on a dedicated **Collections screen** modeled on the Highlights screen, reached from a single **"Collections"** entry in the drawer's Tools group (below Highlights). The selected-collection state on the main list mirrors the **active-label list view** (collection name as the app-bar title + a leading icon, no chips for the collection's own criteria). There is **no** inline drawer list of collections and **no Duplicate action**. Create and edit share one unified **collection editor sheet**. See "UI Components".

---

## Data Models

### Domain Model

```kotlin
// domain/model/Collection.kt
data class Collection(
    val id: String,
    val name: String,
    val isPinned: Boolean,
    val filter: FilterFormState,
    val created: kotlinx.datetime.Instant,
    val updated: kotlinx.datetime.Instant,
)
```

### FilterFormState ↔ API Field Mapping

`collectionCreate` and `collectionInfo` use flat JSON fields. A two-way adapter converts between `FilterFormState` and these fields.

| `FilterFormState` field | API field        | Conversion notes                                                    |
|-------------------------|------------------|---------------------------------------------------------------------|
| `search`                | `search`         | String passthrough, nullable                                        |
| `title`                 | `title`          | String passthrough, nullable                                        |
| `author`                | `author`         | String passthrough, nullable                                        |
| `site`                  | `site`           | String passthrough, nullable                                        |
| `label`                 | `labels`         | Single string (API accepts comma-separated; use first label only)   |
| `fromDate`              | `range_start`    | `Instant.toString()` (ISO 8601) ↔ `Instant.parse()`                |
| `toDate`                | `range_end`      | `Instant.toString()` (ISO 8601) ↔ `Instant.parse()`                |
| `types` (`Set<Bookmark.Type>`) | `type` (`List<String>`) | `Bookmark.Type.Article` → `"article"`, `Picture` → `"photo"`, `Video` → `"video"` |
| `progress` (`Set<ProgressFilter>`) | `read_status` (`List<String>`) | `ProgressFilter` enum → `"unread"` / `"reading"` / `"read"` |
| `isFavorite`            | `is_marked`      | Nullable Boolean passthrough                                        |
| `isArchived`            | `is_archived`    | Nullable Boolean passthrough                                        |
| `isLoaded`              | _(no equivalent)_ | **Omit when persisting; default `null` when loading**              |
| `withLabels`            | _(no equivalent)_ | **Omit when persisting; default `null` when loading**              |
| `withErrors`            | _(no equivalent)_ | **Omit when persisting; default `null` when loading**              |

Add the adapter as extension functions in a new file `domain/model/CollectionFilterAdapter.kt`:

```kotlin
fun FilterFormState.toCollectionRequest(): CreateCollectionDto { ... }
fun CollectionDto.toFilterFormState(): FilterFormState { ... }
```

### Room Entity

```kotlin
// io/db/model/CollectionEntity.kt
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPinned: Boolean,
    val search: String?,
    val title: String?,
    val author: String?,
    val site: String?,
    val labels: String?,
    val type: List<String>,       // stored via existing TypeConverter as JSON array
    val readStatus: List<String>, // stored via existing TypeConverter as JSON array
    val isMarked: Boolean?,
    val isArchived: Boolean?,
    val rangeStart: String?,
    val rangeEnd: String?,
    val created: Long,            // epoch milliseconds
    val updated: Long,            // epoch milliseconds
)
```

### API DTOs

```kotlin
// io/rest/model/CollectionDto.kt
data class CollectionDto(
    @SerializedName("id")           val id: String,
    @SerializedName("href")         val href: String,
    @SerializedName("created")      val created: String,
    @SerializedName("updated")      val updated: String,
    @SerializedName("name")         val name: String,
    @SerializedName("is_pinned")    val isPinned: Boolean?,
    @SerializedName("is_deleted")   val isDeleted: Boolean?,
    @SerializedName("search")       val search: String?,
    @SerializedName("title")        val title: String?,
    @SerializedName("author")       val author: String?,
    @SerializedName("site")         val site: String?,
    @SerializedName("type")         val type: List<String>?,
    @SerializedName("labels")       val labels: String?,
    @SerializedName("read_status")  val readStatus: List<String>?,
    @SerializedName("is_marked")    val isMarked: Boolean?,
    @SerializedName("is_archived")  val isArchived: Boolean?,
    @SerializedName("range_start")  val rangeStart: String?,
    @SerializedName("range_end")    val rangeEnd: String?,
)

// io/rest/model/CreateCollectionDto.kt
data class CreateCollectionDto(
    @SerializedName("name")         val name: String,
    @SerializedName("is_pinned")    val isPinned: Boolean? = null,
    @SerializedName("is_deleted")   val isDeleted: Boolean? = null,
    @SerializedName("search")       val search: String? = null,
    @SerializedName("title")        val title: String? = null,
    @SerializedName("author")       val author: String? = null,
    @SerializedName("site")         val site: String? = null,
    @SerializedName("type")         val type: List<String>? = null,
    @SerializedName("labels")       val labels: String? = null,
    @SerializedName("read_status")  val readStatus: List<String>? = null,
    @SerializedName("is_marked")    val isMarked: Boolean? = null,
    @SerializedName("is_archived")  val isArchived: Boolean? = null,
    @SerializedName("range_start")  val rangeStart: String? = null,
    @SerializedName("range_end")    val rangeEnd: String? = null,
)

// PATCH body reuses the same shape (all fields optional)
typealias UpdateCollectionDto = CreateCollectionDto
```

---

## Repository Layer

### New Interface

```kotlin
// domain/CollectionRepository.kt
interface CollectionRepository {
    /** Emits the local cached list of non-deleted collections. */
    fun observeCollections(): Flow<List<Collection>>

    /** Fetches all collections from the server and replaces local cache. */
    suspend fun refreshCollections(): Result<Unit>

    /** Creates a new collection server-side and caches it locally. */
    suspend fun createCollection(name: String, filter: FilterFormState): Result<Collection>

    /** Updates an existing collection's name and/or filter server-side. */
    suspend fun updateCollection(id: String, name: String, filter: FilterFormState): Result<Collection>

    /** Soft-deletes a collection via PATCH is_deleted=true, then removes from local cache. */
    suspend fun deleteCollection(id: String): Result<Unit>
}
```

### ReadeckApi Additions

```kotlin
// Add to io/rest/ReadeckApi.kt

@GET("bookmarks/collections")
suspend fun getCollections(
    @Query("limit") limit: Int = 100,
    @Query("offset") offset: Int = 0
): Response<List<CollectionDto>>

@GET("bookmarks/collections/{id}")
suspend fun getCollectionById(
    @Path("id") id: String
): Response<CollectionDto>

@POST("bookmarks/collections")
suspend fun createCollection(
    @Body body: CreateCollectionDto
): Response<StatusMessageDto>

@Headers("Accept: application/json")
@PATCH("bookmarks/collections/{id}")
suspend fun updateCollection(
    @Path("id") id: String,
    @Body body: UpdateCollectionDto
): Response<CollectionDto>
```

---

## Database Changes

- Bump `MyDeckDatabase` to **version 8**.
- Add `CollectionEntity::class` to the `@Database` `entities` list.
- Add `MIGRATION_7_8` to create the `collections` table.
- Create `CollectionDao`.

```kotlin
// Migration
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `collections` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `isPinned` INTEGER NOT NULL DEFAULT 0,
                `search` TEXT,
                `title` TEXT,
                `author` TEXT,
                `site` TEXT,
                `labels` TEXT,
                `type` TEXT NOT NULL DEFAULT '[]',
                `readStatus` TEXT NOT NULL DEFAULT '[]',
                `isMarked` INTEGER,
                `isArchived` INTEGER,
                `rangeStart` TEXT,
                `rangeEnd` TEXT,
                `created` INTEGER NOT NULL,
                `updated` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}
```

```kotlin
// io/db/dao/CollectionDao.kt
@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY isPinned DESC, name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollections(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM collections")
    suspend fun deleteAll()

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?
}
```

---

## ViewModel Changes

### `BookmarkListViewModel`

Inject `CollectionRepository` via Hilt constructor injection.

```kotlin
// New state
private val _selectedCollectionId = MutableStateFlow<String?>(null)
val selectedCollectionId: StateFlow<String?> = _selectedCollectionId.asStateFlow()

val collections: StateFlow<List<Collection>> = collectionRepository
    .observeCollections()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// New functions

fun onSelectCollection(collectionId: String) {
    val collection = collections.value.find { it.id == collectionId } ?: return
    _selectedCollectionId.value = collectionId   // discriminator; drives the active-collection view
    _filterFormState.value = collection.filter
    // navigate to the main list; app bar shows the collection name + leading icon
    // (no _drawerPreset = null — _drawerPreset is non-nullable; see rollout-plan §11. As built in C1.)
}

fun onClearCollection() {
    _selectedCollectionId.value = null
}

fun refreshCollections() {
    viewModelScope.launch { collectionRepository.refreshCollections() }
}

fun onSaveCurrentFilterAsCollection(name: String) {
    viewModelScope.launch {
        collectionRepository.createCollection(name, filterFormState.value)
            .onSuccess { collection -> _selectedCollectionId.value = collection.id }
            .onFailure { /* emit UI error event via channel */ }
    }
}

fun onUpdateActiveCollection(newName: String) {
    val id = _selectedCollectionId.value ?: return
    viewModelScope.launch {
        collectionRepository.updateCollection(id, newName, filterFormState.value)
    }
}

fun onDeleteCollection(id: String) {
    viewModelScope.launch {
        collectionRepository.deleteCollection(id)
            .onSuccess { if (_selectedCollectionId.value == id) onClearCollection() }
            .onFailure { /* emit UI error event via channel */ }
    }
}
```

---

## UI Components

The collections UI reuses two existing app patterns: the **Highlights screen** (browsing/creating) and the **active-label list view** (the selected-collection state). Collections are **not** rendered inline in the navigation drawer.

### Navigation drawer / rail — "Collections" entry

Add a single **"Collections"** item to the drawer's **Tools** group, directly below **Highlights** (a single entry, like Highlights and Labels — not an expanded list). Selecting it navigates to the Collections screen. The same entry appears in the wide-layout navigation rail. (For this branch the entry is added to the existing static drawer/rail; the Navigation Settings feature later makes the drawer/rail data-driven.)

### Collections screen

A dedicated screen (route + screen + state, modeled on the Highlights screen):

- Each collection renders as a **card** showing the **name** and **created date/time** only — no per-card action icons (parity with Highlights cards).
- **Tap a card** → `onSelectCollection(id)`; navigate to the main bookmark list as the active-collection view (below).
- A **FAB** ("New Collection") opens the **collection editor sheet** (empty) to compose and save a new collection.
- **Empty state** (M3 pattern) when there are no collections, inviting creation via the FAB.
- Future / out of scope for v1: sort/filter controls in the app bar — the screen reserves app-bar space for these, which is why create is a FAB rather than an app-bar action.

### Active-collection view (main bookmark list)

When a collection is selected, the main list mirrors the **active-label list view**:

- The **top app bar** shows the **collection name as the title** with a **leading icon** to its left (same treatment as a label-filtered list). All other app-bar actions and overflow items mirror the normal list.
- **No filter chips** are shown for the collection's own criteria — an active collection reads as an ordinary list view.
- If the user **applies an additional filter** on top of the active collection, those **added criteria render as chips** (the collection stays the active view; the app-bar title remains the collection name). The saved collection is unchanged until explicitly saved.
- While a collection is active, the **overflow menu** gains **Edit collection** and **Delete collection**:
  - **Edit collection** → opens the editor sheet pre-filled with the **combined** filter (the collection's saved criteria + any currently-applied additional filter) and the existing name.
  - **Delete collection** → confirmation dialog → `onDeleteCollection(id)`.

### Collection editor sheet (unified create + edit)

A single reusable sheet = the filter-criteria controls (reusing `FilterBottomSheet`'s controls) **plus a name `TextField` at the top**. Three entry points:

| Entry point | Pre-fill | Buttons |
|---|---|---|
| FAB (Collections screen) | empty criteria, blank name | Save / Cancel |
| Main-list overflow "Save as Collection" (filter active, no collection selected) | current filter, blank name | Save / Cancel |
| Overflow "Edit collection" (collection active) | combined filter + existing name | Save / Delete (+ Cancel) |

- **Save**, no active collection → `onSaveCurrentFilterAsCollection(name)` (create).
- **Save**, editing → `onUpdateActiveCollection(name)` (persists the combined filter under the name; **rename is supported** via the name field).
- **Delete** (edit mode) → confirmation → `onDeleteCollection(id)`.
- Save is disabled until the name is non-blank.

### Main-list overflow — create entry

The main-list overflow is a small state machine:
- **no collection + active filter** → show **"Save as Collection"** (opens the editor sheet pre-filled with the current filter).
- **no collection + no filter** → nothing (create lives on the Collections screen FAB).
- **collection active** → show **Edit collection** + **Delete collection** (never "Save as Collection").

---

## String Resources

Add to `app/src/main/res/values/strings.xml` and all language files per `CLAUDE.md`:

```xml
<string name="collections_drawer_item">Collections</string>
<string name="collections_screen_title">Collections</string>
<string name="collection_create_fab">New Collection</string>
<string name="collection_name_hint">Collection name</string>
<string name="collection_editor_save">Save</string>
<string name="collection_save_as_action">Save as Collection</string>
<string name="collection_edit_action">Edit collection</string>
<string name="collection_delete_action">Delete collection</string>
<string name="collection_delete_confirm_title">Delete collection?</string>
<string name="collection_delete_confirm_message">"%s" will be permanently deleted.</string>
<string name="collections_empty_title">No collections yet</string>
<string name="collections_empty_message">Save a filter as a collection to see it here.</string>
<string name="collection_save_error">Failed to save collection</string>
<string name="collection_update_error">Failed to update collection</string>
<string name="collection_delete_error">Failed to delete collection</string>
<string name="collection_load_error">Failed to load collections</string>
```

---

## Error Handling

| Scenario                               | Handling                                                              |
|----------------------------------------|-----------------------------------------------------------------------|
| Network unavailable on refresh         | Serve stale local data; show `Snackbar` warning if list is empty      |
| `createCollection` API error           | Show error `Snackbar`; do not update local state                      |
| `deleteCollection` API error           | Show error `Snackbar`; restore item in local cache                    |
| `updateCollection` API error           | Show error `Snackbar`; revert filter state                            |
| Paginated results (> 100 collections)  | Loop with `offset` increments until `Total-Count` is reached          |

---

## Implementation Sequence

1. **DTOs** — Add `CollectionDto`, `CreateCollectionDto` in `io/rest/model/`.
2. **API** — Add four collection methods to `ReadeckApi`.
3. **Domain model** — Add `Collection` data class.
4. **Adapter** — Add `CollectionFilterAdapter.kt` with bi-directional conversion functions.
5. **Room** — Add `CollectionEntity`, `CollectionDao`; bump DB to version 8; write `MIGRATION_7_8`.
6. **Repository interface** — Create `CollectionRepository` interface.
7. **Repository impl** — Implement `CollectionRepositoryImpl` using DAO + API.
8. **DI** — Add Hilt bindings for `CollectionRepository` in the appropriate `@Module`.
9. **ViewModel** — Inject `CollectionRepository` into `BookmarkListViewModel`; add state and functions.
10. **Drawer/rail "Collections" entry + Collections screen** *(Slice C2)* — add the Tools-group "Collections" item (below Highlights) to `AppDrawerContent` (Compact + Expanded) and `AppNavigationRailContent`; build the Collections screen (route + screen + state) with name/created cards, FAB, and empty state; tap → `onSelectCollection` → active-collection view.
11. **Active-collection view** *(Slice C3)* — main-list app-bar title + leading icon mirroring the active-label view; chips for filters added on top of a collection; overflow **Edit collection** / **Delete collection**.
12. **Collection editor sheet + main-list "Save as Collection"** *(Slice C3)* — unified create/edit sheet (filter controls + name field; Save / Delete); main-list overflow "Save as Collection" when a filter is active and no collection is selected.
13. **String resources** — add all new strings to all 10 locale files (distributed into C2/C3 as each is needed).
14. **Tests** — adapter, DAO queries, `CollectionRepositoryImpl`, `BookmarkListViewModel` collection logic, plus the editor-sheet / active-collection state logic. (No Duplicate.)
