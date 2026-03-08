# Highlights & Annotations — Implementation Plan

## Context

MyDeck is an Android client for Readeck. The Readeck server already supports highlights/annotations via its API and embeds them directly in article HTML as `<rd-annotation>` custom elements with color attributes. However, MyDeck currently has **no CSS styling for these elements**, so existing highlights are invisible despite being present in the rendered HTML.

The previous design (`docs/specs/annotations-highlights-design.md` and `feature/annotations-highlights` branch) had several significant flaws:

1. **Color storage misconception**: Assumed `GET /annotations` was the only source of color info, requiring a local Room table for colors. In reality, the server embeds colors directly in the article HTML via `<rd-annotation data-annotation-color="red">`. No local color storage is needed.

2. **Wrong rendering approach**: Proposed injecting `<mark>` elements via JavaScript XPath resolution. The server already puts `<rd-annotation>` custom elements in the HTML — we just need CSS to style them. Phase 1 is literally just adding CSS.

3. **Text selection UX**: Proposed a modal "Select Text to Highlight" mode from the highlights panel. This is awkward. Instead, we override `startActionMode()` to add a "Highlight" item to Android's native text selection menu — the standard approach.

4. **Top bar placement**: Put highlights toggle in the top bar. User wants it in the overflow menu near "Details" to keep the top bar clean.

5. **Unnecessary Room database complexity**: Full Room entity/DAO/migration for annotations. Since the server HTML contains all annotation data including colors, and we re-fetch article content after mutations, no local persistence is needed beyond the existing article content cache.

This plan corrects all these issues with a simplified, phased approach.

**Color names**: Readeck uses 5 color values in `data-annotation-color`: `yellow`, `red`, `blue`, `green`, and `none`. The `none` color represents a colorless/transparent highlight (used for annotations where only the note matters, not a visible color).

**Notes in HTML**: Readeck embeds annotation notes as a **second** `<rd-annotation>` element immediately following the highlighted text element. Both share the same `data-annotation-id-value`. The note element has:
- `data-annotation-note=""` attribute (presence indicates it's a note element)
- `title="<note text>"` attribute containing the actual note content
- Empty text content (no visible text)
- No `id` attribute (unlike the first element which has `id="annotation-{id}"`)

Example from real data:
```html
<rd-annotation id="annotation-EWGePt1qtLuzQbEggdaAfT"
    data-annotation-id-value="EWGePt1qtLuzQbEggdaAfT"
    data-annotation-color="yellow">Install</rd-annotation><rd-annotation
    data-annotation-id-value="EWGePt1qtLuzQbEggdaAfT"
    data-annotation-note="" title="test"
    data-annotation-color="yellow"></rd-annotation>
```

## Feasibility Assessment

**Verdict: HIGHLY FEASIBLE** — all required patterns already exist in the codebase.

| Concern | Assessment |
|---------|-----------|
| Display existing highlights | Trivial — just add CSS for `rd-annotation` elements. Already in HTML. |
| Scroll to highlight | Proven — `WebViewSearchBridge` already uses `scrollIntoView()` successfully |
| JavaScript bridge | Proven — 3 bridges exist (`Image`, `Search`, `Typography`) with established patterns |
| Text selection capture | Feasible — override `startActionMode()` on WebView to add "Highlight" action to native selection menu. Android explicitly supports this. |
| Bottom sheet UI | Proven — `ReaderSettingsBottomSheet` provides the exact pattern |
| API integration | Straightforward — Retrofit patterns established; 4 endpoints to add |
| Color management | Simplified — `data-annotation-color` in HTML provides colors; no local Room storage needed for colors |

### Text Selection Approach

**Recommended: Custom ActionMode.Callback2** — Override `startActionMode(callback, type)` on the WebView to wrap the system's ActionMode callback. This adds a "Highlight" menu item to the native text selection popup (alongside Copy, Select All, etc.) instead of fighting the native behavior. When tapped, JavaScript extracts the selection range as XPath selectors and sends them to native code via the JS bridge.

This approach:
- Works WITH the native WebView text selection, not against it
- Preserves Copy/Paste/Select All functionality
- Requires no custom text selection UI
- Is the documented Android approach for extending WebView selection

---

## Phase 1: Display Existing Highlights (CSS-only)

**Goal**: Make highlights that already exist in article HTML visible with correct colors.

### What to do

1. **Add CSS for `rd-annotation` elements** to all 3 HTML templates

The Readeck server embeds highlights as:
```html
<rd-annotation data-annotation-color="red">highlighted text</rd-annotation>
```

Colors used by Readeck: `yellow`, `red`, `blue`, `green`, `none`

### HTML Structure

Readeck uses two kinds of `<rd-annotation>` elements:

1. **Highlight element** — contains the highlighted text:
   ```html
   <rd-annotation id="annotation-{id}" data-annotation-id-value="{id}" data-annotation-color="yellow">highlighted text</rd-annotation>
   ```

2. **Note element** (optional, immediately follows highlight) — empty element carrying the note:
   ```html
   <rd-annotation data-annotation-id-value="{id}" data-annotation-note="" title="note text" data-annotation-color="yellow"></rd-annotation>
   ```

The note element should be hidden (it has no visible text content) but could display a note indicator icon via CSS `::after` pseudo-element in a future phase.

### Files to modify

- `app/src/main/assets/html_template_light.html` — add `rd-annotation` CSS
- `app/src/main/assets/html_template_dark.html` — add `rd-annotation` CSS
- `app/src/main/assets/html_template_sepia.html` — add `rd-annotation` CSS

### CSS to add (in each template's `<style>` block, after the search highlight CSS)

```css
/* Readeck annotation/highlight rendering */
rd-annotation {
    border-radius: 2px;
    padding: 1px 0;
    cursor: pointer;
}
/* Note elements (second rd-annotation with data-annotation-note) should not display */
rd-annotation[data-annotation-note] {
    display: none;
}
rd-annotation[data-annotation-color="yellow"],
rd-annotation:not([data-annotation-color]) {
    background-color: rgba(255, 235, 59, 0.4);
}
rd-annotation[data-annotation-color="red"] { background-color: rgba(239, 83, 80, 0.35); }
rd-annotation[data-annotation-color="blue"] { background-color: rgba(66, 165, 245, 0.35); }
rd-annotation[data-annotation-color="green"] { background-color: rgba(102, 187, 106, 0.35); }
rd-annotation[data-annotation-color="none"] {
    background-color: transparent;
    text-decoration: underline;
    text-decoration-color: rgba(150, 150, 150, 0.6);
    text-underline-offset: 2px;
}
```

For **dark theme**, adjust alpha values for visibility against dark backgrounds:
```css
rd-annotation[data-annotation-color="yellow"],
rd-annotation:not([data-annotation-color]) {
    background-color: rgba(255, 235, 59, 0.3);
}
rd-annotation[data-annotation-color="red"] { background-color: rgba(239, 83, 80, 0.3); }
rd-annotation[data-annotation-color="blue"] { background-color: rgba(66, 165, 245, 0.3); }
rd-annotation[data-annotation-color="green"] { background-color: rgba(102, 187, 106, 0.3); }
rd-annotation[data-annotation-color="none"] {
    background-color: transparent;
    text-decoration: underline;
    text-decoration-color: rgba(200, 200, 200, 0.5);
    text-underline-offset: 2px;
}
```

For **sepia theme**, use warmer-toned variants that harmonize with the background.

### Verification
- Open an article that has highlights created in the Readeck web UI
- Highlights should appear with correct colors
- Text should remain readable through the highlight
- Works across all 3 themes (light, dark, sepia)

---

## Phase 2: Highlights List & Navigation

**Goal**: Users can view all highlights for an article and tap to scroll to them.

### 2A: API Layer — Annotation endpoints + DTOs

#### New files to create

- `app/src/main/java/com/mydeck/app/io/rest/model/AnnotationDto.kt`
  ```kotlin
  // AnnotationDto — maps to annotationInfo from API
  data class AnnotationDto(
      val id: String,
      val start_selector: String,
      val start_offset: Int,
      val end_selector: String,
      val end_offset: Int,
      val created: String,
      val text: String
  )

  // CreateAnnotationDto — request body for POST
  data class CreateAnnotationDto(
      val start_selector: String,
      val start_offset: Int,
      val end_selector: String,
      val end_offset: Int,
      val color: String
  )

  // UpdateAnnotationDto — request body for PATCH
  data class UpdateAnnotationDto(
      val color: String
  )
  ```

#### Files to modify

- `app/src/main/java/com/mydeck/app/io/rest/ReadeckApi.kt` — add 4 endpoints:
  ```kotlin
  @GET("bookmarks/{id}/annotations")
  suspend fun getAnnotations(@Path("id") bookmarkId: String): Response<List<AnnotationDto>>

  @POST("bookmarks/{id}/annotations")
  suspend fun createAnnotation(@Path("id") bookmarkId: String, @Body body: CreateAnnotationDto): Response<AnnotationDto>

  @PATCH("bookmarks/{id}/annotations/{annotationId}")
  suspend fun updateAnnotation(@Path("id") bookmarkId: String, @Path("annotationId") annotationId: String, @Body body: UpdateAnnotationDto): Response<Unit>

  @DELETE("bookmarks/{id}/annotations/{annotationId}")
  suspend fun deleteAnnotation(@Path("id") bookmarkId: String, @Path("annotationId") annotationId: String): Response<Unit>
  ```

### 2B: Domain Model

#### New file to create

- `app/src/main/java/com/mydeck/app/domain/model/Annotation.kt`
  ```kotlin
  data class Annotation(
      val id: String,
      val bookmarkId: String,
      val text: String,
      val color: String,  // from HTML data-annotation-color: yellow, red, blue, green, none
      val note: String?,  // from HTML title attribute on the note rd-annotation element
      val created: String
  )
  ```

### 2C: WebView Annotation Bridge — Scroll & Color Extraction

#### New file to create

- `app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt`

Follow the pattern of `WebViewSearchBridge.kt`. Key JS functions:
- `scrollToAnnotation(annotationId)` — finds `rd-annotation#annotation-{id}`, calls `scrollIntoView({block: 'center', behavior: 'smooth'})`
- `getAnnotationColors()` — iterates all `rd-annotation[id]` elements (excludes note elements which have `data-annotation-note`), returns JSON map of `{annotationId: color}` via callback
- `highlightActiveAnnotation(annotationId)` — adds a CSS class to visually distinguish the selected annotation (e.g., thicker border/outline)

### 2D: ViewModel State + Fetch Annotations

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`:
  - Add `AnnotationsState` data class: `data class AnnotationsState(val annotations: List<Annotation>, val isLoading: Boolean)`
  - Add `_annotationsState: MutableStateFlow<AnnotationsState?>`
  - Add `fetchAnnotations(bookmarkId)` method — calls API, merges colors from `WebViewAnnotationBridge.getAnnotationColors()`
  - Add `scrollToAnnotation(annotationId)` method
  - Add `showAnnotationsSheet: MutableStateFlow<Boolean>`

### 2E: Highlights Bottom Sheet

#### New file to create

- `app/src/main/java/com/mydeck/app/ui/detail/AnnotationsBottomSheet.kt`

Follow the pattern of `ReaderSettingsBottomSheet.kt`. Content:
- Title: "Highlights"
- List of highlights, each showing:
  - Color indicator (small circle/chip matching highlight color; for `none`, use a gray outline circle)
  - Snippet of highlighted text (truncated to ~2 lines)
  - Note indicator (small icon) if the annotation has a note (extracted from `title` attribute)
  - Tap → dismiss sheet + scroll to highlight in WebView
- Empty state message when no highlights exist

### 2F: Overflow Menu Item

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailMenu.kt`:
  - Add `onShowHighlights: () -> Unit = {}` callback parameter
  - Add "Highlights" menu item between "Details" and the divider before "Delete"
  - Use `Icons.AutoMirrored.Outlined.Note` or `Icons.Outlined.FormatColorFill` icon
  - Only show for article type bookmarks in reader mode

- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`:
  - Wire `onShowHighlights` to set `showAnnotationsSheet = true`
  - Add `AnnotationsBottomSheet` composable, shown when `showAnnotationsSheet` is true
  - Pass WebView ref for scroll-to-annotation functionality

### 2G: String Resources

Add to `app/src/main/res/values/strings.xml` and all 9 language variant files:
```xml
<string name="highlights_title">Highlights</string>
<string name="highlights_empty">No highlights yet</string>
<string name="highlights_menu_item">Highlights</string>
```

### Verification
- Open an article with existing highlights (created in Readeck web UI)
- Open overflow menu → tap "Highlights"
- Bottom sheet shows all highlights with colors and text snippets
- Tapping a highlight dismisses the sheet and smoothly scrolls to it in the article
- The scrolled-to highlight briefly flashes or gets a visual indicator

---

## Phase 3: Create, Edit, and Delete Highlights

**Goal**: Users can select text to create highlights, tap existing highlights to change color or delete.

### 3A: Custom ActionMode for Text Selection

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`:
  - Subclass WebView (or override `startActionMode`) to wrap the system ActionMode.Callback
  - Add a "Highlight" menu item (with highlight icon) to the ActionMode
  - When "Highlight" is tapped:
    1. Call JS to get selection as XPath selectors: `window.getSelection()` → compute XPath for start/end containers and offsets
    2. Send data to native via `WebViewAnnotationBridge` JS interface
    3. Dismiss ActionMode
    4. Show highlight creation bottom sheet

### 3B: XPath Selection JavaScript

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt` — add JS functions:
  - `getXPathForElement(element)` — generates XPath from element to `.container` root
  - `getSelectionAsAnnotation()` — uses `window.getSelection().getRangeAt(0)` to get Range, computes XPath selectors and offsets, returns JSON with `{startSelector, startOffset, endSelector, endOffset, text}`
  - `@JavascriptInterface fun onSelectionCaptured(json: String)` — receives selection data from JS
  - `@JavascriptInterface fun onAnnotationClicked(annotationId: String)` — receives click on existing highlight

### 3C: Annotation Click Handler

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/WebViewAnnotationBridge.kt`:
  - Inject JS click listeners on all `rd-annotation` elements
  - On click → call `MyDeckAnnotationBridge.onAnnotationClicked(annotationId)`
  - Native side shows the edit/delete bottom sheet

### 3D: Highlight Creation Bottom Sheet

#### New file to create

- `app/src/main/java/com/mydeck/app/ui/detail/AnnotationEditSheet.kt`

Mirrors Readeck's new dialog UI. Contains:
- Color picker: row of colored circles (yellow, red, blue, green, none/transparent)
- Selected color has a checkmark overlay
- "Save" button → calls POST API to create annotation
- For editing existing: pre-selects current color, adds "Delete" button

### 3E: ViewModel CRUD Methods

#### Files to modify

- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`:
  - Add `createAnnotation(startSelector, startOffset, endSelector, endOffset, color)` — POST to API, refresh article content to get updated HTML with new `rd-annotation` element
  - Add `updateAnnotationColor(annotationId, color)` — PATCH to API, refresh article content
  - Add `deleteAnnotation(annotationId)` — DELETE from API, refresh article content
  - Add state for showing edit sheet: `annotationEditState: MutableStateFlow<AnnotationEditState?>`
  - `AnnotationEditState`: `data class AnnotationEditState(val annotationId: String?, val color: String, val selectionData: SelectionData?)`

### 3F: Content Refresh After Mutation

After creating/updating/deleting an annotation, the article HTML from the server will have updated `rd-annotation` elements. The approach:
1. Call the mutation API endpoint
2. Re-fetch article content via `GET /bookmarks/{id}/article`
3. Update the local article content in Room DB
4. The WebView will reload with the new HTML containing updated annotations

This avoids complex client-side DOM manipulation for mutations — let the server be the source of truth.

### 3G: Additional String Resources

Add to `app/src/main/res/values/strings.xml` and all 9 language variant files:
```xml
<string name="highlight_create">Create Highlight</string>
<string name="highlight_edit">Edit Highlight</string>
<string name="highlight_delete">Delete Highlight</string>
<string name="highlight_color_yellow">Yellow</string>
<string name="highlight_color_red">Red</string>
<string name="highlight_color_blue">Blue</string>
<string name="highlight_color_green">Green</string>
<string name="highlight_color_none">None</string>
<string name="highlight_action">Highlight</string>
<string name="highlight_delete_confirm">Delete this highlight?</string>
```

### 3H: User Guide Documentation

#### Files to modify

- `app/src/main/assets/guide/en/reading.md` — add section on highlights:
  - How to view highlights (overflow menu → Highlights)
  - How to create a highlight (select text → tap Highlight in selection menu → pick color)
  - How to edit/delete a highlight (tap highlighted text → change color or delete)

### Verification
- Select text in an article → native selection menu shows "Highlight" action
- Tap "Highlight" → color picker bottom sheet appears
- Pick color → highlight is created, text shows highlight color
- Tap existing highlight → edit sheet with current color, option to change or delete
- Delete highlight → highlight disappears from text
- All changes sync back to Readeck server (verify in web UI)
- Overflow menu → Highlights shows updated list after create/delete

---

## Key Files Reference

| File | Role |
|------|------|
| `app/src/main/assets/html_template_*.html` (3 files) | HTML templates — add `rd-annotation` CSS |
| `app/src/main/java/.../io/rest/ReadeckApi.kt` | API interface — add annotation endpoints |
| `app/src/main/java/.../io/rest/model/AnnotationDto.kt` | NEW — API DTOs |
| `app/src/main/java/.../domain/model/Annotation.kt` | NEW — domain model |
| `app/src/main/java/.../ui/detail/WebViewAnnotationBridge.kt` | NEW — JS bridge for annotations |
| `app/src/main/java/.../ui/detail/BookmarkDetailViewModel.kt` | ViewModel — add annotation state & methods |
| `app/src/main/java/.../ui/detail/components/BookmarkDetailWebViews.kt` | WebView — ActionMode override |
| `app/src/main/java/.../ui/detail/components/BookmarkDetailMenu.kt` | Overflow menu — add Highlights item |
| `app/src/main/java/.../ui/detail/BookmarkDetailScreen.kt` | Screen — wire up bottom sheets |
| `app/src/main/java/.../ui/detail/AnnotationsBottomSheet.kt` | NEW — highlights list sheet |
| `app/src/main/java/.../ui/detail/AnnotationEditSheet.kt` | NEW — create/edit/delete sheet |
| `app/src/main/res/values/strings.xml` + 9 locale files | String resources |
| `app/src/main/assets/guide/en/reading.md` | User guide |

## Existing Patterns to Reuse

- **JS bridge pattern**: Follow `WebViewSearchBridge.kt` for structure, JS injection, and callbacks
- **Bottom sheet pattern**: Follow `ReaderSettingsBottomSheet.kt` for `ModalBottomSheet` setup
- **Menu item pattern**: Follow existing items in `BookmarkDetailMenu.kt`
- **Scroll-to-element**: Reuse `scrollIntoView({block: 'center', behavior: 'smooth'})` from `WebViewSearchBridge`
- **API endpoint pattern**: Follow existing Retrofit interface patterns in `ReadeckApi.kt`
- **State management**: Follow `ArticleSearchState` pattern in `BookmarkDetailViewModel.kt`

## What This Plan Does NOT Include

- **Room database for annotations**: Not needed. The server HTML contains all highlight data. Colors come from `data-annotation-color`. No offline annotation creation.
- **Notes/comments on highlights**: Readeck embeds notes in the HTML as a second `<rd-annotation>` element with `data-annotation-note` and `title` attributes (see Context section above). Phase 1 hides these with `display: none`. Viewing/editing notes can be added in a future phase — the API spec (`annotationUpdate`) currently only allows color changes, but the HTML already carries note data for display purposes.
- **Global highlights browser**: No cross-bookmark annotation view. Per-bookmark only.
