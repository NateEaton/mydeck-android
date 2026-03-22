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

**Color:** The OpenAPI spec does not document a `color` field in `annotationSummary`. However, the native Readeck web UI clearly displays highlight cards with colored backgrounds matching each annotation's color (yellow, red, blue, green, olive/none). This strongly suggests the actual API response includes a `color` field not yet documented in the spec. During implementation, the response should be inspected to confirm. If `color` is present, use it for card background tinting. If not, a fallback approach is to fetch per-bookmark annotations (`GET /bookmarks/{id}/annotations`) which also lacks color in the spec — but the article HTML `<rd-annotation data-annotation-color="...">` is the authoritative source. As a last resort, a neutral highlight style can be used, but the colored card approach matching the Readeck UI is strongly preferred.

---

## Data Models

### DTO

```kotlin
// io/rest/model/AnnotationSummaryDto.kt
data class AnnotationSummaryDto(
    val id: String,
    val href: String,
    val text: String,
    val color: String?,  // Not in OpenAPI spec but likely present (see Color note above)
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
    val color: String,  // "yellow", "red", "blue", "green", "none"; defaults to "yellow" if absent
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

## Relationship to Existing Annotation Specs

This spec covers **only** the nav drawer item and global highlights list view. It does **not** cover:

- Displaying highlights within the article reader (CSS for `<rd-annotation>`) — covered by Phase 1 of `annotations-highlights-design-v2.md`
- Per-bookmark highlights list/panel in the detail screen — covered by Phase 2 of `annotations-highlights-design-v2.md`
- Creating, editing, or deleting highlights — covered by Phase 3 of `annotations-highlights-design-v2.md`

However, this spec has a dependency on Phase 1 (CSS rendering) being implemented first, because the scroll-to-annotation behavior requires `<rd-annotation>` elements to be visible in the rendered article. Without the CSS, the scroll target exists in the DOM but is invisible.

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

1. **DTO**: Create `AnnotationSummaryDto` in `io/rest/model/`
2. **API**: Add `getAnnotationSummaries()` to `ReadeckApi`
3. **Domain model**: Create `HighlightSummary` data class
4. **Repository**: Create `HighlightsRepository` interface and implementation
5. **DI**: Add Hilt bindings for `HighlightsRepository`
6. **ViewModel**: Create `HighlightsViewModel`
7. **Screen**: Create `HighlightsScreen` composable
8. **Navigation**: Add `highlights` route to the nav graph; add `annotationId` optional parameter to the bookmark detail route
9. **Scroll-to-annotation**: Implement scroll-to JS in `WebViewAnnotationBridge` (or inline in `BookmarkDetailScreen`), triggered by `annotationId` after `onPageFinished`
10. **Drawer**: Add `onClickHighlights` to `AppDrawerContent` and `AppNavigationRailContent`; wire in `AppShell`
11. **String resources**: Add all new strings to all 10 locale files
12. **User guide**: Update `app/src/main/assets/guide/en/your-bookmarks.md` with Highlights section

---

## Verification

- Open nav drawer → "Highlights" item is visible below Labels
- Tap Highlights → navigates to Highlights screen showing all highlights across bookmarks
- Highlights are grouped by date, newest first
- Each highlight shows the text snippet, date, and parent bookmark title/site
- Tapping a highlight opens the bookmark in reading view
- The article scrolls to the tapped highlight (requires Phase 1 CSS to be implemented)
- Pressing back from reading view returns to the Highlights screen (not to My List)
- Empty state shows when user has no highlights
- Error state with retry shown on network failure
