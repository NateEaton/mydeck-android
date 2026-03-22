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

## Code Review Findings & Required Repairs

The v2 annotation spec made design decisions that were implemented in code, but the code evolved through implementation discoveries not fully captured in that spec. The following is based on a direct review of the current codebase as of this spec's date.

### Critical: Annotations gated to ARTICLE type only

Three hard gates in `BookmarkDetailScreen.kt` restrict all annotation UI to article bookmarks in reader mode:

1. **`BookmarkDetailMenu.kt:66-67`** — The "Highlights" overflow menu item is only shown when `uiState.bookmark.type == ARTICLE && contentMode == READER`
2. **`BookmarkDetailScreen.kt:648-650`** — `AnnotationsBottomSheet` only renders for `ARTICLE` + `READER`
3. **`BookmarkDetailScreen.kt:662`** — `AnnotationEditSheet` only renders for `ARTICLE` + `READER`

However, the underlying infrastructure already works for all types:
- `HighlightActionWebView` (with its text-selection "Highlight" action and annotation tap handling) is used for **all** bookmark types — articles, videos, and photos
- `injectAnnotationInteractions()` is injected after page load for all content types that have a `.container` element
- Readeck supports annotations on photos and videos at the server level

**Impact on this spec**: A user could tap a highlight from the global Highlights list that belongs to a photo or video bookmark, navigate to the reading view, see the highlighted text — but the overflow menu "Highlights" item wouldn't appear, tapping the highlight wouldn't open the edit sheet, and text selection wouldn't show the Highlight action.

**Required fix**: Relax the `ARTICLE` type gates to `ARTICLE || VIDEO || PHOTO` (with `READER` mode still required). Specifically:
- `BookmarkDetailMenu.kt`: Show "Highlights" menu item for all content types in reader mode
- `BookmarkDetailScreen.kt`: Show `AnnotationsBottomSheet` and `AnnotationEditSheet` for all content types in reader mode

### Critical: Video annotation CRUD broken by content refresh

`BookmarkDetailViewModel.refreshArticleContent()` calls `LoadContentPackageUseCase.executeForceRefresh()`, which explicitly rejects videos:

```kotlin
if (bookmark.type is Bookmark.Type.Video) {
    return Result.PermanentFailure("Video bookmarks are not eligible for offline content packages")
}
```

This means after creating, updating, or deleting an annotation on a video bookmark, the content refresh throws an exception, causing the operation to report failure even though the API mutation itself succeeded. The annotation CRUD methods (`createAnnotation`, `updateAnnotationColors`, `deleteAnnotations`) all call `refreshArticleContent` after the API call.

**Required fix**: For video bookmarks, skip the multipart content refresh after annotation mutations and instead:
1. Re-fetch annotations from the per-bookmark API (`GET /bookmarks/{id}/annotations`)
2. Update the cached annotation entities locally
3. Inject updated annotation attributes into the existing HTML via the enricher, or simply re-evaluate the JS to update the DOM

This is a pragmatic approach — video bookmark HTML is typically loaded via the original content URL or embed, and the `<rd-annotation>` elements are already in the DOM. A full content package re-download is unnecessary; the annotation data just needs to be refreshed.

### Note: Existing note support is read-only

The current code already handles notes partially:
- `AnnotationDto` has `note: String = ""` (correct)
- `CachedAnnotationEntity` has `note: String?` (persists notes locally)
- `Annotation` domain model has `note: String?`
- `AnnotationsBottomSheet` shows a note icon when `note.isNotBlank()`
- `AnnotationEditSheet` displays the note text in a **read-only** `OutlinedTextField` with `readOnly = true` and `enabled = false`
- `onNoteClicked` shows a toast: `R.string.highlight_note_not_supported`

The note is visible but not editable. `CreateAnnotationDto` and `UpdateAnnotationDto` both lack a `note` field.

### Note: AnnotationHtmlEnricher handles notes in enrichment

The enricher (`AnnotationHtmlEnricher.kt`) already creates `data-annotation-note="true"` and `title="..."` attributes when enriching bare `<rd-annotation>` tags from multipart content. This means offline-cached content will have note data in the HTML. The bridge's `getRenderedAnnotations()` and `getRenderedAnnotation()` JS functions already extract notes from the HTML via the `title` attribute on `data-annotation-note` elements.

### Note: CachedAnnotationSnapshot omits color and note

`AnnotationCache.kt`'s `CachedAnnotationSnapshot` used for cache-freshness comparison does not include `color` or `note`. This means if only the color or note of an annotation changes (but not its text or selectors), the cache comparison would consider it unchanged. This is a minor issue for cache invalidation but means color/note updates won't trigger a content re-download during sync.

### Note: PATCH response type mismatch

The `ReadeckApi.updateAnnotation()` returns `Response<Unit>` but the actual PATCH response includes the full updated annotations list. This response data is discarded. Once we need to update notes (which requires reading the response to confirm the change), the return type should be changed to `Response<UpdateAnnotationResponseDto>` or similar.

---

## Annotation Notes: Full Support

### Background

The previous annotation spec (`annotations-highlights-design-v2.md`) explicitly deferred notes:

> *"Viewing/editing notes can be added in a future phase — the API spec (`annotationUpdate`) currently only allows color changes"*

This was based on the outdated OpenAPI spec, which shows `annotationCreate` requiring only `start_selector`, `start_offset`, `end_selector`, `end_offset`, and `color`, and `annotationUpdate` accepting only `color`. In reality, the actual API accepts a `note` field on the update endpoint, and returns it in all annotation responses (as an empty string when no note exists). This spec adds full notes support.

### API: Confirmed capabilities

**IMPORTANT**: The OpenAPI spec is unreliable. Every endpoint's actual request/response shape should be confirmed with the user via live API documentation UI examples before implementation. The following are confirmed so far:

#### `GET /bookmarks/annotations` (global list) — CONFIRMED
Response includes `color` and `note` fields (see live example data in this spec above).

#### `PATCH /bookmarks/{id}/annotations/{annotationId}` — CONFIRMED accepts `note`
The update endpoint accepts `note` alongside `color`. The user confirmed this via the API doc UI "fill example" feature.

#### `POST /bookmarks/{id}/annotations` — NEEDS CONFIRMATION
The OpenAPI spec shows only `start_selector`, `start_offset`, `end_selector`, `end_offset`, and `color` as accepted fields. It is **not yet confirmed** whether the POST endpoint also accepts `note`. If it does not, adding a note during creation requires a two-step flow:
1. `POST` to create the highlight (color only)
2. `PATCH` to add the note

The implementation should handle both cases:
- Try sending `note` in the POST body
- If the note doesn't persist (check the response), follow up with a PATCH
- Or, if confirmed that POST doesn't accept notes, always use the two-step flow for highlights with notes

**Action item**: Before implementing, ask the user to test the POST endpoint via the Readeck API doc UI with a body that includes `note` and report whether the note persists.

#### `GET /bookmarks/{id}/annotations` (per-bookmark) — NEEDS CONFIRMATION
The response schema shows `annotationInfo` with `id`, `start_selector`, `start_offset`, `end_selector`, `end_offset`, `created`, `text`. The current code already has `color` and `note` fields with defaults (`color: String = "yellow"`, `note: String = ""`). Confirm that the actual response includes these fields.
```

### Updated DTOs (supersedes current code)

The current codebase has `CreateAnnotationDto` without `note` and `UpdateAnnotationDto` without `note`. Both need updating:

```kotlin
// io/rest/model/CreateAnnotationDto.kt
data class CreateAnnotationDto(
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val color: String,
    val note: String = "",     // NEEDS CONFIRMATION: may not be accepted by POST
)

// io/rest/model/UpdateAnnotationDto.kt
data class UpdateAnnotationDto(
    val color: String,
    val note: String = "",     // CONFIRMED: accepted by PATCH
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

The current `AnnotationEditSheet` already displays the note in a read-only `OutlinedTextField` with an `onNoteClicked` callback that shows a "not supported" toast. The fix is to make the text field editable and wire the save flow to include the note in the PATCH call. The updated sheet design:

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

If the POST endpoint accepts `note`, the call includes it directly. If not, the flow is:
1. `POST` with `color` to create the highlight
2. If the user entered a note, immediately `PATCH` the new annotation with the `note`

The UI should not show two loading states — the two-step flow should appear as a single operation to the user.

### Note Display: Global Highlights List (This Spec)

Already covered above — the highlight card shows the note below the highlighted text when `note.isNotEmpty()`, styled as `bodySmall` in `onSurfaceVariant` with a small note icon.

### Note Display: Per-Bookmark Highlights Panel (V2 Spec Phase 2)

The `AnnotationsBottomSheet` (per-bookmark highlights list in the reader overflow menu) should also show note indicators and note text. Each highlight item in the bottom sheet displays:

- Color indicator
- Highlighted text snippet
- Note text (if non-empty), truncated to ~1 line, with a note icon
- Tap → dismiss sheet + scroll to highlight in WebView

### ViewModel Updates (changes to existing code)

The current `AnnotationEditState` already has `noteText: String? = null` but it's read-only. Changes needed:

1. **Make `noteText` editable**: Add `onAnnotationEditNoteChanged(note: String)` method (parallel to existing `onAnnotationEditColorSelected`)
2. **Update `saveAnnotationEdit()`**: Currently calls `updateAnnotationColors(annotationIds, color)` which sends `UpdateAnnotationDto(color = color)`. Must send `UpdateAnnotationDto(color = color, note = note)` instead.
3. **Update `createAnnotation()`**: Currently sends `CreateAnnotationDto` without `note`. Add `note` parameter and include in request (or follow up with PATCH if POST doesn't support notes).
4. **Update `updateAnnotationColors()`**: Rename to `updateAnnotations()` and add `note` parameter. Send `UpdateAnnotationDto(color, note)`.
5. **Update `ReadeckApi.updateAnnotation()`**: Change return type from `Response<Unit>` to parse the response body, so the updated annotation data (including confirmed note) can be used.

```kotlin
// New method
fun onAnnotationEditNoteChanged(note: String) {
    _annotationEditState.update { state ->
        state?.copy(noteText = note)
    }
}

// Updated signature
fun updateAnnotations(annotationIds: List<String>, color: String, note: String) { ... }

// Updated create — may need two-step flow
fun createAnnotation(
    startSelector: String, startOffset: Int,
    endSelector: String, endOffset: Int,
    color: String,
    note: String,
) { ... }
```

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

## Relationship to Existing Code and Specs

### Already implemented (from v2 spec, now in codebase)
- Phase 1 CSS: `<rd-annotation>` styling in HTML templates (highlight colors rendering) — **done**
- Phase 2 API: `ReadeckApi` annotation endpoints (GET/POST/PATCH/DELETE per bookmark) — **done**
- Phase 2 domain model: `Annotation`, `SelectionData` — **done**
- Phase 2 WebView bridges: `WebViewAnnotationBridge`, `WebViewAnnotationTapBridge` — **done**
- Phase 2 per-bookmark highlights: `AnnotationsBottomSheet` — **done** (articles only)
- Phase 3 create/edit/delete: `AnnotationEditSheet`, `HighlightActionWebView`, ActionMode — **done** (articles only, notes read-only)
- Annotation enrichment: `AnnotationHtmlEnricher`, `AnnotationHtmlParser` — **done**
- Local caching: `CachedAnnotationEntity`, `CachedAnnotationDao` — **done**

### What this spec adds
- Bug fix: extend annotation support from articles-only to all bookmark types (photo, video)
- Bug fix: handle video content refresh gracefully after annotation mutations
- Feature: make notes editable (currently read-only with "not supported" toast)
- Feature: add `note` to `UpdateAnnotationDto` and `CreateAnnotationDto`
- Feature: global highlights list via `GET /bookmarks/annotations` endpoint
- Feature: nav drawer Highlights item → full-screen `HighlightsScreen`
- Feature: navigate from highlight to bookmark reading view with scroll-to-annotation

### Items from v2 spec now superseded by this spec
- `UpdateAnnotationDto` — needs `note` field (was color-only)
- `CreateAnnotationDto` — needs `note` field (pending API confirmation)
- `AnnotationEditSheet` note handling — editable text field replaces read-only display
- `updateAnnotationColors` ViewModel method — replaced by `updateAnnotations` with color + note
- "What This Plan Does NOT Include" section's notes exclusion — notes are now fully included
- Article-only type gate — annotations now supported for all bookmark types

### V2 spec items NOT yet implemented and NOT covered here
- Note indicator CSS in the reader (showing an icon next to highlights that have notes) — specified above but deferred to implementation judgment
- Offline annotation creation — not planned (requires network)

---

## Future: Collections Reuse

The pattern established here — "drawer item → navigate to full-screen list → tap item → navigate to detail" — is the same pattern Collections will need:

- Drawer has a "Collections" item
- Tapping it navigates to a `CollectionsScreen` listing saved collections
- Tapping a collection applies its filter to the bookmark list

The only difference is that Collections navigates *back* to the bookmark list with a filter applied, while Highlights navigates *forward* to a bookmark detail with a scroll target. But the scaffolding — navigation route, dedicated ViewModel, dedicated screen composable, drawer integration — is identical. A shared pattern or base class is premature at this point, but the structural consistency makes future refactoring straightforward.

---

## Implementation Sequence

### Phase A: Bug fixes (prerequisite for Highlights feature)

1. **Fix type gates**: Remove `ARTICLE`-only restriction from `BookmarkDetailMenu.kt`, `BookmarkDetailScreen.kt` (AnnotationsBottomSheet and AnnotationEditSheet conditionals). Allow `ARTICLE || VIDEO || PHOTO` in reader mode.
2. **Fix video content refresh**: In `BookmarkDetailViewModel.refreshArticleContent()`, handle video bookmarks gracefully — skip multipart refresh, instead re-fetch annotations from API and update cached entities + re-inject into DOM.
3. **Confirm API payloads**: Before proceeding, ask the user to test these via the Readeck API doc UI:
   - Does `POST /bookmarks/{id}/annotations` accept a `note` field?
   - Does `GET /bookmarks/{id}/annotations` return `color` and `note` fields?
   - Does `PATCH` response actually return the full annotations list (current code ignores it with `Response<Unit>`)?

### Phase B: Notes support

4. **Update DTOs**: Add `note` to `UpdateAnnotationDto`. Conditionally add to `CreateAnnotationDto` based on API confirmation.
5. **Update `ReadeckApi.updateAnnotation()`**: Change return type to capture response body.
6. **Make note editable**: In `AnnotationEditSheet`, change `OutlinedTextField` from read-only to editable. Add `onNoteChanged` callback. Remove the "not supported" toast.
7. **Update ViewModel**: Add `onAnnotationEditNoteChanged()`. Update `saveAnnotationEdit()` and `createAnnotation()` to include note. Handle two-step create flow if POST doesn't accept notes.
8. **Note indicator CSS**: Add CSS for note indicator icon on highlights with notes in all 3 HTML templates.

### Phase C: Global highlights list

9. **DTO**: Create `AnnotationSummaryDto` in `io/rest/model/`
10. **API**: Add `getAnnotationSummaries()` to `ReadeckApi`
11. **Domain model**: Create `HighlightSummary` and `BookmarkHighlightGroup` data classes
12. **Repository**: Create `HighlightsRepository` interface and implementation
13. **DI**: Add Hilt bindings for `HighlightsRepository`
14. **ViewModel**: Create `HighlightsViewModel` with bookmark grouping logic
15. **Screen**: Create `HighlightsScreen` composable with colored cards, bookmark grouping, and note display
16. **Navigation**: Add `highlights` route to the nav graph; add `annotationId` optional parameter to the bookmark detail route
17. **Scroll-to-annotation**: Implement scroll-to JS in `WebViewAnnotationBridge` (or inline in `BookmarkDetailScreen`), triggered by `annotationId` after `onPageFinished`
18. **Drawer**: Add `onClickHighlights` to `AppDrawerContent` and `AppNavigationRailContent`; wire in `AppShell`

### Phase D: Polish

19. **String resources**: Add all new strings (including note-related) to all 10 locale files
20. **User guide**: Update `app/src/main/assets/guide/en/your-bookmarks.md` with Highlights section; update `reading.md` with note editing instructions

---

## Verification

### Phase A: Bug fixes — video/photo annotation support
- Open a **photo** bookmark with existing highlights → overflow menu shows "Highlights" item
- Tap "Highlights" → per-bookmark highlights bottom sheet opens with highlight list
- Tap a highlight in the bottom sheet → scrolls to it in the reader
- Tap a highlighted annotation in the photo reader → annotation edit sheet opens
- Select text in photo reader → "Highlight" action appears in native selection menu
- Create a highlight on a photo → highlight is created, no crash, highlight appears
- Delete a highlight on a photo → highlight is removed
- Repeat all of the above for a **video** bookmark with HTML content that has highlights
- Video annotation CRUD does not crash (the content refresh for videos is handled gracefully)

### Phase B: Notes
- Editing an existing highlight: note field is **editable** (not read-only, no "not supported" toast)
- Editing: current note text is pre-populated in the text field
- Editing: changing the note and saving persists the update server-side
- Editing: clearing the note (empty field) and saving removes the note server-side
- Creating a new highlight: note text field is available
- Creating a new highlight: submitting with a note persists it server-side (one-step or two-step)
- Creating a new highlight: leaving note empty works
- Note indicator icon appears next to highlights with notes in the article reader
- Notes display in the per-bookmark highlights bottom sheet
- Verify round-trip: create highlight with note → note appears in Readeck web UI → edit note in web UI → updated note appears in MyDeck

### Phase C: Global highlights list
- Open nav drawer → "Highlights" item is visible below Labels
- Tap Highlights → navigates to Highlights screen showing all highlights across bookmarks
- Highlights are grouped by bookmark, with bookmark title below each group
- Highlight cards have colored backgrounds matching annotation color
- Each highlight shows the text snippet, date, and note (if present)
- Tapping a highlight card opens the bookmark in reading view and scrolls to that highlight
- Tapping a highlight for a **photo** or **video** bookmark works correctly (opens reading view, annotation features available)
- Tapping the bookmark title line opens the bookmark at top/last reading position
- Pressing back from reading view returns to the Highlights screen (not to My List)
- Empty state shows when user has no highlights
- Error state with retry shown on network failure
