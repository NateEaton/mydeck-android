# Article Annotations / Highlights: Design and Implementation Plan

**Date:** 2026-02-19
**Branch:** `claude/bookmark-features-design-C1m9G`
**Status:** Draft

---

## Overview

Allow users to highlight passages of text within article-type bookmarks, stored persistently server-side via the Readeck annotations API. Highlights are visually rendered within the in-app WebView reader. Users can change highlight color or delete highlights from inside the reader, and browse all highlights for a bookmark in a list pane within the detail screen.

---

## Current State

- The article reader is a WebView in `BookmarkDetailScreen.kt`, driven by `BookmarkDetailViewModel.kt` (725 lines).
- JavaScript bridges already exist for typography (`WebViewTypographyBridge.kt`) and article search (`WebViewSearchBridge.kt`). These provide the precedent for the annotation bridge.
- The `Bookmark` domain model has no annotation concept. The `ReadeckApi` has no annotation endpoints.
- The `MyDeckDatabase` is at version 7 (bumped to 8 by the Collections feature). Annotations will require a further bump to version 9.

---

## Design Decisions

### 1. Selector Type: XPath, Not CSS

The original spec describes `startSelector`/`endSelector` as "CSS selectors". The Readeck API uses **XPath selectors** (`start_selector` and `end_selector` in `annotationCreate`). The implementation must use XPath. The JavaScript bridge will use `document.evaluate()` to resolve XPath expressions and `Range` objects for selection and rendering.

### 2. Color Is Stored Locally

The `annotationInfo` response schema from the API returns: `id`, `start_selector`, `start_offset`, `end_selector`, `end_offset`, `created`, `text`. **There is no `color` field in the server response.** The API accepts color on `POST` and on `PATCH`, but does not return it in `GET`. The highlight color must therefore be stored in the local Room `annotations` table and treated as local state. On `PATCH /bookmarks/{id}/annotations/{id}` the server returns an updated `annotations` array — if color is not included in the response, the locally stored color takes precedence.

### 3. Bookmark Domain Model Is Not Modified

The original spec suggests adding `List<Highlight>` to `Bookmark`. This couples the heavyweight detail model to annotation data that is only needed in the reader and the annotation list pane. Instead, annotations are a separate concern: fetched on demand when the article reader opens, stored in their own Room table, and observed via a separate Flow in `BookmarkDetailViewModel`.

### 4. Global Highlights Are Summary-Only in the Stable API

The stable API exposes `GET /bookmarks/annotations`, but it returns `annotationSummary` objects only: `id`, `href`, `text`, `created`, and bookmark metadata. It does **not** return `color`, `start_selector`, or `end_selector`. That means the app cannot reliably:

- render a global list with correct per-highlight colors for highlights it has never seen before,
- scroll a bookmark to a highlight from the global list without first hydrating that bookmark's full annotation list, or
- reconstruct missing local color state from the server alone.

The implementation will therefore keep the feature **bookmark-scoped for all color-aware and reader-integrated behavior**. The global endpoint can be considered later for a separate summary-only screen, but it is not sufficient for the reader UX in this spec.

### 5. Highlight Creation Uses an Explicit Reader Action

In practice, auto-opening `AnnotationCreationSheet` from every non-empty WebView selection conflicts with Android's native text-selection toolbar. That makes normal copy/paste selection unstable and clears the selection before the user can finish dragging.

The corrected design uses an explicit "add highlight" step:

- **Browse highlights:** tap the top app bar Highlights button to open the bottom sheet list.
- **Create highlight:** from the Highlights panel, tap **Select Text to Highlight**, then long-press/drag-select text in the article. When the WebView reports that selection, the app opens `AnnotationCreationSheet`.
- **Edit/delete highlight:** tap an existing rendered highlight to open `AnnotationActionSheet`.

Normal text selection outside this explicit highlight flow remains available for the native WebView copy/paste actions.

### 6. Annotation Refresh Is Independent of Bookmark Sync

Highlights are not part of the existing bookmark sync stream. The app should call `GET /bookmarks/{id}/annotations` when opening an article bookmark detail screen, independent of the full bookmark sync endpoints. This gives the reader the latest known server state for that bookmark even when the bookmark body/content itself came from local cache.

Create/update/delete actions are sent to the server immediately via the annotation CRUD endpoints. Local Room state remains the cache and offline fallback, but server writes are not deferred behind the bookmark sync model.

### 7. Pending Colors on PATCH Response

When the server PATCH response returns `annotations` without color, the implementation will merge the server-returned list with the locally stored color values. Any annotation id present in the server response but missing locally will default to yellow (`#FFFF00`).

---

## Data Models

### Domain Model

```kotlin
// domain/model/Annotation.kt
data class Annotation(
    val id: String,
    val bookmarkId: String,
    val startSelector: String,   // XPath expression for start element
    val startOffset: Int,        // character offset within start element's text node
    val endSelector: String,     // XPath expression for end element
    val endOffset: Int,          // character offset within end element's text node
    val color: String,           // hex color string, e.g. "#FFFF00" (stored locally)
    val text: String,            // server-provided highlighted text (read-only)
    val created: kotlinx.datetime.Instant,
)
```

**Available colors** (a small fixed palette is sufficient):

```kotlin
object AnnotationColors {
    const val YELLOW  = "#FFFF00"
    const val GREEN   = "#90EE90"
    const val BLUE    = "#ADD8E6"
    const val PINK    = "#FFB6C1"
    val all = listOf(YELLOW, GREEN, BLUE, PINK)
    val default = YELLOW
}
```

### Room Entity

```kotlin
// io/db/model/AnnotationEntity.kt
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookmarkId")]
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookmarkId: String,
    val startSelector: String,
    val startOffset: Int,
    val endSelector: String,
    val endOffset: Int,
    val color: String,       // locally stored — not in API response
    val text: String,
    val created: Long,       // epoch milliseconds
)
```

### API DTOs

```kotlin
// io/rest/model/AnnotationDto.kt
data class AnnotationDto(
    @SerializedName("id")             val id: String,
    @SerializedName("start_selector") val startSelector: String,
    @SerializedName("start_offset")   val startOffset: Int,
    @SerializedName("end_selector")   val endSelector: String,
    @SerializedName("end_offset")     val endOffset: Int,
    @SerializedName("created")        val created: String,
    @SerializedName("text")           val text: String,
)

// io/rest/model/CreateAnnotationDto.kt
data class CreateAnnotationDto(
    @SerializedName("start_selector") val startSelector: String,
    @SerializedName("start_offset")   val startOffset: Int,
    @SerializedName("end_selector")   val endSelector: String,
    @SerializedName("end_offset")     val endOffset: Int,
    @SerializedName("color")          val color: String,
)

// io/rest/model/UpdateAnnotationDto.kt
data class UpdateAnnotationDto(
    @SerializedName("color") val color: String,
)

// The PATCH response wraps the updated annotation list
data class UpdateAnnotationResponseDto(
    @SerializedName("updated")     val updated: String,
    @SerializedName("annotations") val annotations: List<AnnotationDto>,
)
```

---

## Repository Layer

### New Interface

```kotlin
// domain/AnnotationRepository.kt
interface AnnotationRepository {
    /** Emits the locally cached annotations for a bookmark, ordered by creation date. */
    fun observeAnnotations(bookmarkId: String): Flow<List<Annotation>>

    /** Fetches annotations from the server and replaces local cache for that bookmark. */
    suspend fun refreshAnnotations(bookmarkId: String): Result<Unit>

    /** Creates a new annotation server-side and caches it locally. */
    suspend fun createAnnotation(
        bookmarkId: String,
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        color: String,
    ): Result<Annotation>

    /** Updates annotation color server-side and locally. */
    suspend fun updateAnnotationColor(
        bookmarkId: String,
        annotationId: String,
        color: String,
    ): Result<Unit>

    /** Deletes an annotation server-side and locally. */
    suspend fun deleteAnnotation(bookmarkId: String, annotationId: String): Result<Unit>
}
```

### ReadeckApi Additions

```kotlin
// Add to io/rest/ReadeckApi.kt

@GET("bookmarks/{id}/annotations")
suspend fun getAnnotations(
    @Path("id") bookmarkId: String
): Response<List<AnnotationDto>>

@POST("bookmarks/{id}/annotations")
suspend fun createAnnotation(
    @Path("id") bookmarkId: String,
    @Body body: CreateAnnotationDto
): Response<AnnotationDto>

@Headers("Accept: application/json")
@PATCH("bookmarks/{id}/annotations/{annotation_id}")
suspend fun updateAnnotation(
    @Path("id") bookmarkId: String,
    @Path("annotation_id") annotationId: String,
    @Body body: UpdateAnnotationDto
): Response<UpdateAnnotationResponseDto>

@DELETE("bookmarks/{id}/annotations/{annotation_id}")
suspend fun deleteAnnotation(
    @Path("id") bookmarkId: String,
    @Path("annotation_id") annotationId: String
): Response<Unit>
```

---

## Database Changes

> **Note:** If the Collections feature (DB version 8) has not yet landed, this migration must be chained after it. Coordinate version numbering with the Collections implementation.

- Bump `MyDeckDatabase` to **version 9** (or 8 if Collections is not merged first).
- Add `AnnotationEntity::class` to the `@Database` `entities` list.
- Add `MIGRATION_8_9`.

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `annotations` (
                `id` TEXT NOT NULL,
                `bookmarkId` TEXT NOT NULL,
                `startSelector` TEXT NOT NULL,
                `startOffset` INTEGER NOT NULL,
                `endSelector` TEXT NOT NULL,
                `endOffset` INTEGER NOT NULL,
                `color` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `created` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookmarkId`) REFERENCES `bookmarks`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookmarkId` ON `annotations` (`bookmarkId`)"
        )
    }
}
```

```kotlin
// io/db/dao/AnnotationDao.kt
@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookmarkId = :bookmarkId ORDER BY created ASC")
    fun observeAnnotations(bookmarkId: String): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotations(annotations: List<AnnotationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM annotations WHERE bookmarkId = :bookmarkId")
    suspend fun deleteForBookmark(bookmarkId: String)

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: String): AnnotationEntity?
}
```

---

## ViewModel Changes

### `BookmarkDetailViewModel`

Inject `AnnotationRepository` via Hilt.

```kotlin
// New state
val annotations: StateFlow<List<Annotation>> = annotationRepository
    .observeAnnotations(bookmarkId)  // initialised when loadBookmark() is called
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// Track whether the annotations panel is open
private val _isAnnotationPanelOpen = MutableStateFlow(false)
val isAnnotationPanelOpen: StateFlow<Boolean> = _isAnnotationPanelOpen.asStateFlow()

// New functions

fun loadAnnotations(bookmarkId: String) {
    viewModelScope.launch {
        annotationRepository.refreshAnnotations(bookmarkId)
    }
}

fun onCreateAnnotation(
    startSelector: String,
    startOffset: Int,
    endSelector: String,
    endOffset: Int,
    color: String,
) {
    val bId = currentBookmarkId ?: return
    viewModelScope.launch {
        annotationRepository.createAnnotation(bId, startSelector, startOffset, endSelector, endOffset, color)
            .onFailure { /* emit error snackbar */ }
    }
}

fun onUpdateAnnotationColor(annotationId: String, color: String) {
    val bId = currentBookmarkId ?: return
    viewModelScope.launch {
        annotationRepository.updateAnnotationColor(bId, annotationId, color)
            .onFailure { /* emit error snackbar */ }
    }
}

fun onDeleteAnnotation(annotationId: String) {
    val bId = currentBookmarkId ?: return
    viewModelScope.launch {
        annotationRepository.deleteAnnotation(bId, annotationId)
            .onFailure { /* emit error snackbar */ }
    }
}

fun onToggleAnnotationPanel() {
    _isAnnotationPanelOpen.value = !_isAnnotationPanelOpen.value
}
```

---

## JavaScript Bridge: `WebViewAnnotationBridge.kt`

Create a new bridge file `ui/detail/WebViewAnnotationBridge.kt` following the pattern of `WebViewTypographyBridge.kt` and `WebViewSearchBridge.kt`.

### Android → WebView (JS injection)

**`renderAnnotations(annotations: List<Annotation>): String`**

Generates JavaScript that:
1. Removes any existing highlight spans (`document.querySelectorAll('[data-annotation-id]')`).
2. For each annotation, resolves the XPath selectors using `document.evaluate()`.
3. Creates a `Range`, wraps the selected text in a `<mark>` element with `data-annotation-id`, `data-color`, and inline `background-color` style.
4. Attaches a `click` listener to each `<mark>` that calls `AnnotationInterface.onAnnotationClicked(id)`.

**`injectSelectionObserver(): String`**

Generates JavaScript that:
1. Ensures article text remains selectable in the WebView (`user-select: text` CSS).
2. Registers a debounced `selectionchange` listener that calls `AnnotationInterface.onTextSelected(startXPath, startOffset, endXPath, endOffset, selectedText)` when a non-empty selection exists.
3. Avoids duplicate callbacks for the same selection and clears the native selection after reporting it.

**`scrollToAnnotation(annotationId: String): String`**

Generates JavaScript that scrolls the element with `data-annotation-id == annotationId` into view.

### WebView → Android (`@JavascriptInterface`)

```kotlin
// Registered as "AnnotationInterface" on the WebView

@JavascriptInterface
fun onTextSelected(
    startSelector: String,
    startOffset: Int,
    endSelector: String,
    endOffset: Int,
    text: String
) {
    // Post to main thread via Handler or ViewModel channel
    // Show the annotation creation bottom sheet with color picker
}

@JavascriptInterface
fun onAnnotationClicked(annotationId: String) {
    // Post to main thread
    // Show annotation action sheet (change color / delete)
}
```

### XPath Generation in JavaScript

The JavaScript must compute XPath selectors for the selected `Range` boundaries. A utility function injected into the page computes XPath like:

```javascript
function getXPath(element) {
    if (element.id) return `//*[@id="${element.id}"]`;
    const parts = [];
    while (element && element.nodeType === Node.ELEMENT_NODE) {
        let index = 0;
        let sibling = element.previousSibling;
        while (sibling) {
            if (sibling.nodeType === Node.ELEMENT_NODE && sibling.nodeName === element.nodeName) index++;
            sibling = sibling.previousSibling;
        }
        parts.unshift(`${element.nodeName.toLowerCase()}[${index + 1}]`);
        element = element.parentNode;
    }
    return parts.length ? `/${parts.join('/')}` : '';
}
```

This must be injected into every article page load, before annotation rendering.

---

## UI Components

### `BookmarkDetailScreen.kt` — Annotation Controls

1. **Top bar action:** Add a "Highlights" `IconButton` (e.g. `Icons.Outlined.Highlight`) that toggles `isAnnotationPanelOpen` and calls `onToggleAnnotationPanel()` in the ViewModel.
2. **Text selection:** Long-pressing or drag-selecting text in the reader should trigger the native text-selection affordance and then open `AnnotationCreationSheet` without requiring a dedicated mode button.

### `AnnotationCreationSheet.kt` (new composable)

A `ModalBottomSheet` that appears after the user selects text in the WebView:
- Shows the selected text (truncated to ~100 chars) for confirmation.
- Shows a row of 4 color swatches using `AnnotationColors.all`.
- **Create** button calls `viewModel.onCreateAnnotation(...)`.
- **Cancel** button dismisses the sheet without creating a highlight.

### `AnnotationActionSheet.kt` (new composable)

A `ModalBottomSheet` that appears when the user taps an existing highlight in the WebView:
- Shows the highlighted text.
- Shows color swatches to change the highlight color (calls `viewModel.onUpdateAnnotationColor`).
- A **Delete** button with confirmation dialog (calls `viewModel.onDeleteAnnotation`).

### `AnnotationsPanel.kt` (new composable — in-screen panel)

A side panel or `ModalDrawerSheet` on the right side of `BookmarkDetailScreen`, visible when `isAnnotationPanelOpen` is true. On compact screens it slides up as a bottom sheet.

Contents:
- List header "Highlights" with a close button.
- Each annotation shown as a card:
  - Color swatch
  - Highlighted text (truncated)
  - Created date
  - Trailing actions: change color (opens `AnnotationActionSheet`) and delete.
- Tapping an annotation card calls `scrollToAnnotation(id)` on the WebView and closes the panel on compact screens.

---

## Article HTML Template Considerations

The article HTML loaded into the WebView originates from the Readeck server. The JavaScript bridge scripts must be injected **after** page load, using `WebView.evaluateJavascript()`. The existing pattern from `WebViewTypographyBridge` and `WebViewSearchBridge` should be followed:

- Inject the XPath utility function and annotation rendering script in `WebViewClient.onPageFinished`.
- Re-render annotations whenever the `annotations` StateFlow emits a new list.

---

## String Resources

Add to `values/strings.xml` and all 10 locale files:

```xml
<string name="annotations_panel_title">Highlights</string>
<string name="annotation_create_title">Highlight Text</string>
<string name="annotation_color_label">Highlight color</string>
<string name="annotation_create_button">Add Highlight</string>
<string name="annotation_delete_button">Remove Highlight</string>
<string name="annotation_delete_confirm">Remove this highlight?</string>
<string name="annotation_change_color">Change color</string>
<string name="annotation_load_error">Failed to load highlights</string>
<string name="annotation_save_error">Failed to save highlight</string>
<string name="annotation_delete_error">Failed to delete highlight</string>
<string name="annotation_toggle_button">Highlights</string>
```

---

## Error Handling

| Scenario                         | Handling                                                                |
|----------------------------------|-------------------------------------------------------------------------|
| Network unavailable on load      | Show locally cached annotations; surface `Snackbar` if cache empty      |
| `createAnnotation` failure       | Show error `Snackbar`; do not render the new highlight in the WebView   |
| `updateAnnotationColor` failure  | Show error `Snackbar`; revert color in local cache                      |
| `deleteAnnotation` failure       | Show error `Snackbar`; restore annotation in local cache and WebView    |
| XPath resolution fails in JS     | Log a warning; silently skip the failed annotation in rendering         |
| Page not yet loaded on injection | Queue JS evaluation; execute in `onPageFinished` callback               |

---

## Implementation Sequence

1. **DTOs** — Add `AnnotationDto`, `CreateAnnotationDto`, `UpdateAnnotationDto`, `UpdateAnnotationResponseDto` in `io/rest/model/`.
2. **API** — Add four annotation methods to `ReadeckApi`.
3. **Domain model** — Add `Annotation` data class and `AnnotationColors` object.
4. **Room** — Add `AnnotationEntity`, `AnnotationDao`; bump DB version; write migration.
5. **Repository interface** — Create `AnnotationRepository` interface.
6. **Repository impl** — Implement `AnnotationRepositoryImpl`, handling color merge on PATCH response.
7. **DI** — Add Hilt bindings for `AnnotationRepository`.
8. **`WebViewAnnotationBridge`** — Implement JS injection functions and `@JavascriptInterface` class.
9. **`BookmarkDetailViewModel`** — Inject `AnnotationRepository`; add state and functions; call `loadAnnotations` in `loadBookmark()`.
10. **`BookmarkDetailScreen`** — Wire WebView `onPageFinished` to inject bridge; add Highlights button in top bar; connect annotation flow to JS rendering.
11. **`AnnotationCreationSheet`** — Build color-picker bottom sheet triggered by standard text selection (`onTextSelected`).
12. **`AnnotationActionSheet`** — Build action sheet triggered by `onAnnotationClicked`.
13. **`AnnotationsPanel`** — Build highlights list panel with scroll-to and delete actions.
14. **String resources** — Add all new strings to all 10 locale files.
15. **Tests** — Unit tests for `AnnotationRepositoryImpl` color merge logic; ViewModel tests for CRUD flow; JS bridge manual/integration test in the reader.
