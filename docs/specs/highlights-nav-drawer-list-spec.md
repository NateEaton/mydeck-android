# Highlights Nav Drawer & List View

**Date:** 2026-03-22
**Status:** Draft

---

## Overview

Add a "Highlights" item to the navigation drawer that opens a full-screen list of all highlights across all bookmarks, fetched from the `GET /bookmarks/annotations` API endpoint. Tapping a highlight opens the bookmark in reading view and scrolls to the annotation. This establishes a "secondary list screen" pattern that Collections can later reuse.

---

## Background & Motivation

The previous annotation specs (`docs/archive/annotations-highlights-design.md` and `annotations-highlights-design-v2.md`) assumed there was no global annotations endpoint — only per-bookmark `GET /bookmarks/{id}/annotations`. This was incorrect. The Readeck API provides `GET /bookmarks/annotations` (paginated, `limit`/`offset`) which returns all highlights created by the current user, each with the highlighted text, creation date, and parent bookmark metadata (id, title, site name, URL).

The native Readeck web UI already surfaces this as a dedicated "Bookmark Highlights" page accessible from the sidebar. MyDeck should mirror this.

---

## Design Decision: Full-Screen Destination vs. Bottom Sheet

### Why not a bottom sheet?

Labels use a bottom sheet because selecting a label **filters the existing bookmark list** — the user stays within the list context and the drawer preset doesn't change. The action is: pick a label → see matching bookmarks in the same list.

Highlights are fundamentally different. The user is browsing a **cross-bookmark index** and then diving into a specific bookmark's reading view at a specific scroll position. Consider this scenario with a bottom sheet:

1. User is viewing "My List" (unread bookmarks)
2. User opens drawer → taps Highlights → bottom sheet opens
3. User taps a highlight → sheet dismisses → reading view opens for a bookmark that happens to be archived
4. User presses back from reading view → returns to "My List" where that bookmark doesn't exist

This is disorienting. The alternative of routing back from reading view to Archive (or whatever state the bookmark is in) is equally confusing — the user never navigated to Archive.

With the native Readeck UI, backing out of the reading view takes the user back to the Highlights list. This is the natural, expected behavior.

### Why a full-screen destination works

- **Natural back stack**: Drawer → Highlights Screen → Reading View → back → Highlights Screen → back → previous list. Clean and predictable.
- **Context preservation**: The highlights list stays alive in the back stack while the user reads. Returning to it is instantaneous with scroll position preserved.
- **No archive/state confusion**: The highlights screen is a self-contained context. The bookmark's archived/unread state is irrelevant to the highlights list.
- **Consistent with Readeck**: The web UI gives highlights their own page. Matching this reduces friction for users of both.
- **Reusable pattern for Collections**: Collections will need the same "secondary list navigated from drawer" pattern. Building the scaffolding now means Collections can plug in with minimal new infrastructure.

### The pattern

The Highlights screen is conceptually parallel to the bookmark list but with a different data source and simpler item layout. It is **not** a `DrawerPreset` — it is a separate navigation destination, similar to how Settings and About are separate destinations triggered from the drawer. The drawer's Highlights item closes the drawer and navigates to a `HighlightsScreen` route.

---

## API Endpoint

### `GET /bookmarks/annotations`

From the OpenAPI spec:

- **Scope:** `oauth | bookmarks:read`
- **Pagination:** `limit` (integer), `offset` (integer)
- **Response headers:** `Link`, `Current-Page`, `Total-Count`, `Total-Pages`
- **Response body:** `Array<annotationSummary>`

#### `annotationSummary` schema

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` (short-uid) | Highlight ID |
| `href` | `string` (uri) | Link to the highlight |
| `text` | `string` | Highlighted text |
| `created` | `string` (date-time) | Highlight creation date |
| `bookmark_id` | `string` (short-uid) | Parent bookmark ID |
| `bookmark_href` | `string` (uri) | Link to the bookmark info |
| `bookmark_url` | `string` (uri) | Original bookmark URL |
| `bookmark_title` | `string` | Title of the parent bookmark |
| `bookmark_site_name` | `string` | Site name of the parent bookmark |

**Undocumented fields:** The OpenAPI spec is out of date — the actual API response includes `color` and `note` fields not listed in the schema. Confirmed via the Readeck API documentation UI with live data. The actual `annotationSummary` fields are:

| Field | Type | Description |
|-------|------|-------------|
| `color` | `string` | Annotation color: `"yellow"`, `"red"`, `"blue"`, `"green"`, or `"none"` |
| `note` | `string` | Annotation note text (empty string if no note) |

---

## Data Models

### DTO

```kotlin
// io/rest/model/AnnotationSummaryDto.kt
data class AnnotationSummaryDto(
    val id: String,
    val href: String,
    val text: String,
    val color: String,
    val note: String,
    val created: String,
    val bookmark_id: String,
    val bookmark_href: String,
    val bookmark_url: String,
    val bookmark_title: String,
    val bookmark_site_name: String,
)
```

### Domain Model

```kotlin
// domain/model/HighlightSummary.kt
data class HighlightSummary(
    val id: String,
    val text: String,
    val color: String,  // "yellow", "red", "blue", "green", "none"
    val note: String,   // empty string if no note
    val created: kotlinx.datetime.Instant,
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
)
```

### Grouped model (for UI)

```kotlin
// domain/model/BookmarkHighlightGroup.kt
data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val highlights: List<HighlightSummary>,  // ordered by created descending
)
```

The repository returns flat `HighlightSummary` items; the ViewModel groups them by `bookmarkId` and sorts groups by the most recent highlight timestamp (descending).

No Room entity is needed. This is a network-only, non-cached listing (like Labels in single-select mode). The list is fetched fresh each time the user navigates to the Highlights screen. The list is typically small (tens to low hundreds of items) and the endpoint is paginated.

---

## API Layer

### ReadeckApi addition

```kotlin
@GET("bookmarks/annotations")
suspend fun getAnnotationSummaries(
    @Query("limit") limit: Int,
    @Query("offset") offset: Int,
): Response<List<AnnotationSummaryDto>>
```

The `Total-Count` response header should be read from the `Response` to determine if more pages exist. Follow the pagination pattern used elsewhere in the app (e.g., bookmark sync uses `Total-Pages`/`Current-Page` headers).

---

## Repository

A lightweight repository or use-case class that pages through all annotation summaries and maps DTOs to domain objects.

```kotlin
// domain/HighlightsRepository.kt (or a use case)
interface HighlightsRepository {
    suspend fun getAllHighlights(): Result<List<HighlightSummary>>
}
```

The implementation fetches pages in a loop (e.g., 50 per page) until all items are retrieved, similar to how labels or collections might page through results. For a first implementation, fetching all pages before displaying is acceptable given the expected data size. If performance becomes an issue, this can be converted to a `PagingSource` for Paging 3 in a follow-up.

---

## Navigation

### Route

Add a new navigation route:

```kotlin
// In the navigation graph (AppNavigation or equivalent)
composable("highlights") {
    HighlightsScreen(
        onNavigateToBookmark = { bookmarkId, annotationId ->
            // Navigate to BookmarkDetailScreen with annotation scroll target
            navController.navigate("bookmark/$bookmarkId?annotationId=$annotationId")
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### BookmarkDetailScreen: annotation scroll parameter

The `BookmarkDetailScreen` route should accept an optional `annotationId` query parameter. When present, after the article loads (`onPageFinished`), the WebView scrolls to the `<rd-annotation id="annotation-{annotationId}">` element using `scrollIntoView()`. This reuses the scroll-to-element pattern already established by `WebViewSearchBridge`.

---

## Drawer Integration

### AppDrawerContent changes

Add `onClickHighlights: () -> Unit` parameter. Add a "Highlights" `NavigationDrawerItem` in the section below the divider after Pictures, alongside Labels:

```kotlin
NavigationDrawerItem(
    label = { Text(
        style = MaterialTheme.typography.titleMedium,
        text = stringResource(id = R.string.highlights_drawer_item)
    ) },
    icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
    selected = false,  // Highlights is a destination, not a selectable preset
    colors = prominentItemColors,
    onClick = onClickHighlights
)
```

Position: immediately after Labels, before the divider that precedes Settings. This mirrors the placement in the native Readeck sidebar (see screenshots). The drawer section with Labels, Highlights, and (future) Collections represents "cross-cutting views" that are not bookmark-list filter presets.

### AppShell wiring

```kotlin
onClickHighlights = {
    navController.navigate("highlights")
    scope.launch { drawerState.close() }
}
```

---

## UI: HighlightsScreen

### Layout

A `Scaffold` with:
- **Top app bar**: Title "Highlights", navigation icon (back arrow or hamburger depending on entry point)
- **Content**: A `LazyColumn` of highlight items, grouped by date (day)
- **Empty state**: Centered message when no highlights exist
- **Loading state**: `CircularProgressIndicator` during initial fetch
- **Error state**: Error message with retry button

### Grouping: by bookmark, not by date

The Readeck native UI groups highlights **by parent bookmark**, not by date. Multiple highlights from the same bookmark are shown as consecutive cards, with the bookmark title appearing **below** the group (not above it) as a link-styled line. This is the layout visible in the screenshots:

```
┌─────────────────────────────────────────────┐
│  22 March 2026, 03:44                       │  ← date
│  Intergraph                                 │  ← highlighted text
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│  22 March 2026, 03:44                       │  ← date
│  big five                                   │  ← highlighted text
└─────────────────────────────────────────────┘
  Shapr3D - Intergraph - History of CAD         ← bookmark title BELOW the group
```

The overall list is sorted by most-recent-highlight descending (bookmarks with the newest highlight appear first). Within a bookmark group, highlights are ordered by creation date descending.

### Highlight card styling

Each highlight card has a **colored background** matching the annotation color, following the Readeck native UI:

| Annotation color | Card background |
|-----------------|-----------------|
| `yellow` | Warm yellow/amber tint (e.g., `rgba(255, 235, 59, 0.25)`) |
| `red` | Muted red/rose tint (e.g., `rgba(239, 83, 80, 0.20)`) |
| `blue` | Subtle blue tint (e.g., `rgba(66, 165, 245, 0.20)`) |
| `green` | Subtle green tint (e.g., `rgba(102, 187, 106, 0.20)`) |
| `none` | Olive/neutral tint (e.g., `rgba(150, 150, 100, 0.15)`) |

Alpha values should be tuned for both light and dark themes to ensure readability. The Readeck screenshots show distinct but muted background colors that make the list visually scannable without being garish.

Each card displays:
- **Date**: Creation timestamp, styled as `labelMedium` in `onSurfaceVariant`
- **Highlighted text**: The `text` field, truncated to ~2–3 lines, styled as `bodyLarge`
- **Note** (if non-empty): The `note` field below the highlighted text, styled as `bodySmall` in `onSurfaceVariant`, with a small note icon. Most highlights in practice have an empty note, so this line is only rendered when `note.isNotEmpty()`.

### Bookmark title line

The bookmark title + site name line appears **below** the last card in a bookmark group, styled as a link (accent/primary color, `bodyMedium`). In the Readeck UI this is formatted as:

```
Site Name - Bookmark Title
```

Tapping the bookmark title line navigates to the bookmark in reading view without a specific annotation scroll target (opens at the top or last reading position). Tapping a highlight card navigates to the bookmark with scroll-to-annotation.

### Tap actions

Two distinct tap targets:

1. **Tapping a highlight card**: Navigates to `BookmarkDetailScreen` for the parent bookmark, passing the `annotationId` so the reading view scrolls to that specific highlight after page load.

2. **Tapping the bookmark title line**: Navigates to `BookmarkDetailScreen` for the bookmark without a specific annotation scroll target (opens at the top or last reading position). This matches the Readeck web UI behavior where the title is a link to the bookmark itself.

---

## ViewModel: HighlightsViewModel

A dedicated ViewModel for the Highlights screen:

```kotlin
@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HighlightsUiState>(HighlightsUiState.Loading)
    val uiState: StateFlow<HighlightsUiState> = _uiState.asStateFlow()

    init {
        loadHighlights()
    }

    fun loadHighlights() {
        viewModelScope.launch {
            _uiState.value = HighlightsUiState.Loading
            highlightsRepository.getAllHighlights()
                .onSuccess { highlights ->
                    _uiState.value = if (highlights.isEmpty()) {
                        HighlightsUiState.Empty
                    } else {
                        HighlightsUiState.Success(highlights)
                    }
                }
                .onFailure { error ->
                    _uiState.value = HighlightsUiState.Error(error.message ?: "Failed to load highlights")
                }
        }
    }
}

sealed interface HighlightsUiState {
    data object Loading : HighlightsUiState
    data object Empty : HighlightsUiState
    data class Success(val groups: List<BookmarkHighlightGroup>) : HighlightsUiState
    data class Error(val message: String) : HighlightsUiState
}
```

---

## String Resources

Add to `values/strings.xml` and all 9 language-variant files:

```xml
<string name="highlights_drawer_item">Highlights</string>
<string name="highlights_screen_title">Highlights</string>
<string name="highlights_empty">No highlights yet. Highlight text in articles to see them here.</string>
<string name="highlights_load_error">Failed to load highlights</string>
<string name="highlights_retry">Retry</string>
```

---

## Annotation Notes: Full Support

### Background

The previous annotation spec (`annotations-highlights-design-v2.md`) explicitly deferred notes:

> *"Viewing/editing notes can be added in a future phase — the API spec (`annotationUpdate`) currently only allows color changes"*

This was based on the outdated OpenAPI spec, which shows `annotationCreate` requiring only `start_selector`, `start_offset`, `end_selector`, `end_offset`, and `color`, and `annotationUpdate` accepting only `color`. In reality, the actual API accepts a `note` field on both endpoints, and returns it in all annotation responses (as an empty string when no note exists). This spec adds full notes support.

### API: Updated Schemas

The actual request bodies (confirmed via the Readeck API documentation UI) are:

#### `annotationCreate` (POST `/bookmarks/{id}/annotations`)

```json
{
  "start_selector": "string",
  "start_offset": 0,
  "end_selector": "string",
  "end_offset": 0,
  "color": "string",
  "note": "string"         // ← undocumented, optional
}
```

#### `annotationUpdate` (PATCH `/bookmarks/{id}/annotations/{annotationId}`)

```json
{
  "color": "string",
  "note": "string"         // ← undocumented, optional
}
```

### Updated DTOs (supersedes v2 spec)

```kotlin
// io/rest/model/CreateAnnotationDto.kt
data class CreateAnnotationDto(
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val color: String,
    val note: String = "",     // empty string for no note
)

// io/rest/model/UpdateAnnotationDto.kt
data class UpdateAnnotationDto(
    val color: String,
    val note: String,          // empty string to clear note
)
```

### PATCH Response (supersedes v2 spec)

The `PATCH /bookmarks/{id}/annotations/{annotationId}` response returns the full list of annotations for that bookmark, each now including `color` and `note` (also undocumented in the OpenAPI spec). The `annotationInfo` schema in practice is:

```json
{
  "annotations": [
    {
      "id": "string",
      "start_selector": "string",
      "start_offset": 0,
      "end_selector": "string",
      "end_offset": 0,
      "color": "string",
      "created": "date-time",
      "text": "string",
      "note": "string"
    }
  ],
  "updated": "date-time"
}
```

This eliminates the v1 spec's concern about `color` not being in the PATCH response and the need for local-only color storage. Both `color` and `note` round-trip through the API.

```kotlin
// io/rest/model/AnnotationDto.kt (updated)
data class AnnotationDto(
    val id: String,
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val color: String,      // ← now confirmed in response
    val created: String,
    val text: String,
    val note: String,       // ← now confirmed in response
)
```

### Notes in the Article Reader

Readeck embeds notes in the article HTML as a second `<rd-annotation>` element immediately following the highlight element:

```html
<rd-annotation id="annotation-{id}"
    data-annotation-id-value="{id}"
    data-annotation-color="yellow">highlighted text</rd-annotation>
<rd-annotation
    data-annotation-id-value="{id}"
    data-annotation-note=""
    title="This is the note text"
    data-annotation-color="yellow"></rd-annotation>
```

Phase 1 CSS hides the note element with `display: none`. With full notes support, the note element should instead display a **note indicator** — a small icon (e.g., a tiny comment/note icon) rendered via CSS `::after` pseudo-element on the highlight element when a sibling note element exists:

```css
/* Show a note indicator after highlights that have notes */
rd-annotation[id] + rd-annotation[data-annotation-note] {
    display: inline;
    font-size: 0;          /* hide any accidental text content */
}
rd-annotation[id] + rd-annotation[data-annotation-note]::after {
    content: " \01F4DD";   /* or use a background-image SVG icon */
    font-size: 14px;
    vertical-align: super;
    cursor: pointer;
}
```

Alternatively, the JavaScript annotation bridge can detect note elements and add a visual indicator programmatically. The CSS approach is simpler for display; the JS approach is needed anyway for click handling (see below).

### Note Display: Tap-to-View in Reader

When the user taps a highlighted annotation in the reading view (handled by `WebViewAnnotationBridge.onAnnotationClicked`), the **annotation action sheet** (from Phase 3 of the v2 spec) should show:

1. The highlighted text snippet
2. The current note text (if any), or a "Add note" prompt if empty
3. Color picker (existing from v2 spec)
4. An editable text field for the note
5. Save / Delete buttons

This replaces the v2 spec's `AnnotationEditSheet` which only had color picking and delete. The updated sheet design:

```
┌─────────────────────────────────────────────┐
│  Edit Highlight                              │
│                                              │
│  "highlighted text snippet..."               │  ← read-only, truncated
│                                              │
│  Color                                       │
│  ○ yellow  ○ red  ○ blue  ○ green  ○ none   │
│                                              │
│  Note                                        │
│  ┌─────────────────────────────────────────┐ │
│  │ Add a note...                           │ │
│  │                                         │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  [Delete]                        [Save]      │
└─────────────────────────────────────────────┘
```

The "Save" button calls `PATCH /bookmarks/{id}/annotations/{annotationId}` with both `color` and `note`. After a successful PATCH, the article content is re-fetched so the HTML reflects the updated note element.

### Note Display: Highlight Creation

When creating a new highlight (Phase 3 — user selects text, taps "Highlight" in the ActionMode), the creation sheet should also include the note field:

```
┌─────────────────────────────────────────────┐
│  Create Highlight                            │
│                                              │
│  "selected text..."                          │  ← read-only preview
│                                              │
│  Color                                       │
│  ○ yellow  ○ red  ○ blue  ○ green  ○ none   │
│                                              │
│  Note (optional)                             │
│  ┌─────────────────────────────────────────┐ │
│  │ Add a note...                           │ │
│  │                                         │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│                              [Create]        │
└─────────────────────────────────────────────┘
```

The `POST /bookmarks/{id}/annotations` call includes the `note` field (empty string if the user leaves it blank).

### Note Display: Global Highlights List (This Spec)

Already covered above — the highlight card shows the note below the highlighted text when `note.isNotEmpty()`, styled as `bodySmall` in `onSurfaceVariant` with a small note icon.

### Note Display: Per-Bookmark Highlights Panel (V2 Spec Phase 2)

The `AnnotationsBottomSheet` (per-bookmark highlights list in the reader overflow menu) should also show note indicators and note text. Each highlight item in the bottom sheet displays:

- Color indicator
- Highlighted text snippet
- Note text (if non-empty), truncated to ~1 line, with a note icon
- Tap → dismiss sheet + scroll to highlight in WebView

### ViewModel Updates (Supersedes V2 Spec Phase 3E)

The annotation edit state needs to carry the note:

```kotlin
data class AnnotationEditState(
    val annotationId: String?,       // null for new highlight
    val color: String,
    val note: String,                // current note text
    val selectionData: SelectionData?,  // non-null for new highlight
    val highlightText: String,       // the highlighted text (read-only display)
)
```

New/updated ViewModel methods:

```kotlin
fun createAnnotation(
    startSelector: String, startOffset: Int,
    endSelector: String, endOffset: Int,
    color: String,
    note: String,        // ← added
)

fun updateAnnotation(
    annotationId: String,
    color: String,
    note: String,        // ← added (replaces updateAnnotationColor)
)
```

The v2 spec's `updateAnnotationColor` method is replaced by `updateAnnotation` which sends both color and note in the PATCH request.

### String Resources for Notes

Add to `values/strings.xml` and all 9 language-variant files:

```xml
<string name="highlight_note_label">Note</string>
<string name="highlight_note_hint">Add a note…</string>
<string name="highlight_note_optional">Note (optional)</string>
<string name="highlight_save">Save</string>
<string name="highlight_note_save_error">Failed to save note</string>
```

---

## Relationship to Existing Annotation Specs

This spec covers:
- The nav drawer Highlights item and global highlights list view
- Full annotation notes support (creating, editing, displaying) across all surfaces

It does **not** cover:
- Displaying highlights within the article reader (CSS for `<rd-annotation>`) — covered by Phase 1 of `annotations-highlights-design-v2.md`
- Per-bookmark highlights list/panel in the detail screen — covered by Phase 2 of `annotations-highlights-design-v2.md`, with notes additions specified above
- Creating and deleting highlights via text selection — covered by Phase 3 of `annotations-highlights-design-v2.md`, with notes additions specified above

**Superseded items from the v2 spec:**
- `UpdateAnnotationDto` — now includes `note` (was color-only)
- `CreateAnnotationDto` — now includes `note` (was color-only)
- `AnnotationEditSheet` — now includes a note text field (was color picker + delete only)
- `updateAnnotationColor` ViewModel method — replaced by `updateAnnotation` with color + note
- "What This Plan Does NOT Include" section's notes exclusion — notes are now fully included

This spec has a dependency on Phase 1 (CSS rendering) being implemented first, because the scroll-to-annotation behavior requires `<rd-annotation>` elements to be visible in the rendered article. Without the CSS, the scroll target exists in the DOM but is invisible.

Additionally, the `annotationId` parameter on the `BookmarkDetailScreen` route and the scroll-to-annotation JavaScript logic can be shared with Phase 2's per-bookmark highlights panel (which also needs scroll-to-annotation). Implementing this spec first establishes the scroll infrastructure that Phase 2 reuses.

---

## Future: Collections Reuse

The pattern established here — "drawer item → navigate to full-screen list → tap item → navigate to detail" — is the same pattern Collections will need:

- Drawer has a "Collections" item
- Tapping it navigates to a `CollectionsScreen` listing saved collections
- Tapping a collection applies its filter to the bookmark list

The only difference is that Collections navigates *back* to the bookmark list with a filter applied, while Highlights navigates *forward* to a bookmark detail with a scroll target. But the scaffolding — navigation route, dedicated ViewModel, dedicated screen composable, drawer integration — is identical. A shared pattern or base class is premature at this point, but the structural consistency makes future refactoring straightforward.

---

## Implementation Sequence

1. **DTOs**: Create `AnnotationSummaryDto` in `io/rest/model/`; update `CreateAnnotationDto`, `UpdateAnnotationDto`, and `AnnotationDto` to include `note` and `color` fields
2. **API**: Add `getAnnotationSummaries()` to `ReadeckApi`
3. **Domain model**: Create `HighlightSummary` and `BookmarkHighlightGroup` data classes
4. **Repository**: Create `HighlightsRepository` interface and implementation
5. **DI**: Add Hilt bindings for `HighlightsRepository`
6. **ViewModel**: Create `HighlightsViewModel` with bookmark grouping logic
7. **Screen**: Create `HighlightsScreen` composable with colored cards, bookmark grouping, and note display
8. **Navigation**: Add `highlights` route to the nav graph; add `annotationId` optional parameter to the bookmark detail route
9. **Scroll-to-annotation**: Implement scroll-to JS in `WebViewAnnotationBridge` (or inline in `BookmarkDetailScreen`), triggered by `annotationId` after `onPageFinished`
10. **Drawer**: Add `onClickHighlights` to `AppDrawerContent` and `AppNavigationRailContent`; wire in `AppShell`
11. **Note indicator CSS**: Add CSS for note indicator icon on highlights with notes in all 3 HTML templates
12. **Annotation edit sheet**: Update `AnnotationEditSheet` (from v2 spec) to include note text field for both create and edit flows
13. **String resources**: Add all new strings (including note-related) to all 10 locale files
14. **User guide**: Update `app/src/main/assets/guide/en/your-bookmarks.md` with Highlights section; update `reading.md` with note editing instructions

---

## Verification

### Highlights list
- Open nav drawer → "Highlights" item is visible below Labels
- Tap Highlights → navigates to Highlights screen showing all highlights across bookmarks
- Highlights are grouped by bookmark, with bookmark title below each group
- Highlight cards have colored backgrounds matching annotation color
- Each highlight shows the text snippet, date, and note (if present)
- Tapping a highlight card opens the bookmark in reading view and scrolls to that highlight
- Tapping the bookmark title line opens the bookmark at top/last reading position
- Pressing back from reading view returns to the Highlights screen (not to My List)
- Empty state shows when user has no highlights
- Error state with retry shown on network failure

### Notes
- Creating a new highlight: note text field is available, submitting with a note persists it server-side
- Creating a new highlight: leaving note empty works (sends empty string)
- Editing an existing highlight: current note text is pre-populated in the text field
- Editing: changing the note and saving persists the update server-side
- Editing: clearing the note (empty field) and saving removes the note server-side
- Note indicator icon appears next to highlights with notes in the article reader
- Notes display in the global highlights list cards
- Notes display in the per-bookmark highlights panel
- Verify round-trip: create highlight with note → note appears in Readeck web UI → edit note in web UI → updated note appears in MyDeck
