# Highlight Rendering Fix: Eliminate WebView Repaint on Annotation Changes

## Status

Complete — implemented and verified on `feature/sync-multipart-v012`.

## Problem

Three separate issues with annotation/highlight rendering in the reader view:

### 1. Full WebView repaint on every annotation change

When a user created, recolored, or deleted an annotation, the entire article content
visibly repainted. The chain was:

```
refreshArticleContent()
  → loadContentPackageUseCase.executeForceRefresh()
    → multipartSyncClient.fetchContentPackages()   (full HTML + resources)
    → contentPackageManager.commitPackage()         (write to disk)
    → bookmarkDao.updateContentState(DOWNLOADED)    (Room update)
      → Compose recomposition
        → WebView loadDataWithBaseURL()             (full page reload)
```

Every annotation CRUD operation triggered a full multipart download, a Room write
(which emitted a new value from the `observeBookmark` flow), Compose recomposition,
and a WebView `loadDataWithBaseURL` call — a visible flash/repaint of the entire page.

### 2. Annotations from other clients not syncing to reader HTML

When annotations were added via another client (e.g., the Readeck web UI), syncing
in MyDeck (app start, pull-down, sync settings button) updated the annotation database
but never refreshed the on-disk HTML content package. Annotations appeared in the
Highlights bottom sheet (which reads from the REST API / cached DB) but were absent
from the rendered article.

Root cause: for DOWNLOADED content-package bookmarks, the detail screen init code
only called `syncAnnotationsIfNeeded()` when the content directory was *missing* —
the opposite of the normal case. And even that legacy path used
`LoadArticleUseCase.refreshCachedArticleIfAnnotationsChanged()`, which reads from
Room's `articleContent` column — always empty for content-package bookmarks (HTML
lives on disk).

### 3. Annotations with notes hidden entirely

Any annotation that had a note attached was completely invisible — the highlighted
text disappeared from the article. The CSS rule:

```css
rd-annotation[data-annotation-note] {
    display: none;
}
```

was hiding the entire `<rd-annotation>` custom element (including its text content)
whenever `AnnotationHtmlEnricher` added the `data-annotation-note="true"` attribute.
This rule was likely a leftover from an earlier design where notes were rendered as
separate block elements rather than inline highlights.

## Solution

### Three-tier annotation update strategy

Annotation changes now use the lightest-weight update path possible, avoiding full
WebView reloads in all cases:

**Tier 1 — Color changes (instant, no network after the API PATCH):**

1. Send `AnnotationRefreshEvent.ColorUpdate` via a `Channel`-based event flow
2. The `BookmarkDetailScreen` `LaunchedEffect` collector executes JS:
   ```javascript
   document.querySelectorAll('rd-annotation[data-annotation-id-value="ID"]')
     .forEach(el => el.setAttribute('data-annotation-color', 'COLOR'));
   ```
3. Persist the color change to the on-disk `index.html` via regex replacement
4. Update the annotation snapshot in preferences

No network fetch, no Room write, no recomposition, no page reload.

**Tier 2 — Create/Delete (lightweight HTML-only fetch):**

1. `refreshAnnotationHtml()` calls `loadContentPackageUseCase.refreshHtmlForAnnotations()`
2. This uses `multipartSyncClient.fetchHtmlOnly()` — a new method that requests
   `withJson=false, withHtml=true, withResources=false` (no resource download)
3. The HTML is enriched with annotation attributes via `AnnotationHtmlEnricher`
4. Updated HTML is written to disk via `contentPackageManager.updateHtml()`
   (no staging/swap/commit — just overwrites `index.html`)
5. Sends `AnnotationRefreshEvent.HtmlRefresh` → JS replaces `.container` innerHTML:
   ```javascript
   document.querySelector('.container').innerHTML = decodeURIComponent(escape(atob('BASE64')));
   ```
6. Re-injects image interceptor and annotation interaction JS handlers
7. Falls back to Tier 3 if the HTML-only fetch fails

Images remain on disk from the original content package. Relative paths resolve
identically via `WebViewAssetLoader`. No visible repaint.

**Tier 3 — Full refresh (fallback only):**

Full `executeForceRefresh()` multipart download. Only used as fallback when the
lightweight HTML path fails, or when the user explicitly taps "Refresh content."

### Annotation sync from other clients

New `syncAnnotationsForContentPackage()` method in `BookmarkDetailViewModel`, called
when opening a DOWNLOADED content-package bookmark (for Article, Picture, and Video types):

1. Check network availability; skip if offline
2. Fetch current annotations from `GET /bookmarks/{id}/annotations`
3. Serialize to a snapshot string and compare against the cached snapshot in preferences
4. If changed: call `refreshAnnotationHtml()` (Tier 2) — fetches updated HTML from
   server, enriches bare annotation tags, writes to disk, pushes to WebView via JS
5. If unchanged with no cached snapshot: save snapshot to avoid re-checking

This replaces the legacy `syncAnnotationsIfNeeded()` path for content-package bookmarks.
The legacy path is retained for Room-based article content (no content directory on disk).

### Note indicator CSS fix

Replaced `display: none` with a `::after` pseudo-element in all four HTML templates
(light, dark, black, sepia):

```css
rd-annotation[data-annotation-note]::after {
    content: " \270E";
    font-size: 0.75em;
    opacity: 0.6;
}
```

Annotations with notes now display normally with their highlight color, plus a small
pencil icon (✎) at the end — matching the Readeck web UI's approach.

## Files Changed

### New code paths

- **`MultipartSyncClient.fetchHtmlOnly()`** — HTML-only multipart request
  (`withJson=false, withHtml=true, withResources=false`)
- **`ContentPackageManager.updateHtml()`** — writes just `index.html` without
  full staging/swap/commit cycle
- **`LoadContentPackageUseCase.refreshHtmlForAnnotations()`** — lightweight HTML-only
  fetch + enrichment + disk write; returns enriched HTML string
- **`BookmarkDetailViewModel.AnnotationRefreshEvent`** — sealed class with
  `ColorUpdate` and `HtmlRefresh` variants
- **`BookmarkDetailViewModel.syncAnnotationsForContentPackage()`** — snapshot-based
  annotation change detection for content-package bookmarks
- **`BookmarkDetailViewModel.refreshAnnotationHtml()`** — Tier 2 HTML refresh with
  Tier 3 fallback
- **`BookmarkDetailViewModel.updateAnnotationColorOnDisk()`** — regex-based color
  attribute update in on-disk HTML

### Modified code paths

- **`BookmarkDetailViewModel.createAnnotation()`** — uses `refreshAnnotationHtml()`
  instead of `refreshArticleContent()`
- **`BookmarkDetailViewModel.updateAnnotationColors()`** — uses Tier 1 JS + disk
  update instead of full refresh
- **`BookmarkDetailViewModel.deleteAnnotations()`** — uses `refreshAnnotationHtml()`
  instead of `refreshArticleContent()`
- **`BookmarkDetailViewModel.forceRefreshContent()`** — inlines full multipart refresh
  directly (removed `refreshArticleContent()`)
- **`BookmarkDetailViewModel` init** — DOWNLOADED branch now calls
  `syncAnnotationsForContentPackage()` when content directory exists

### CSS fixes

- **`html_template_light.html`** — note indicator fix
- **`html_template_dark.html`** — note indicator fix
- **`html_template_black.html`** — note indicator fix
- **`html_template_sepia.html`** — note indicator fix

### UI integration

- **`BookmarkDetailScreen.kt`** — new `LaunchedEffect(Unit)` collecting
  `annotationRefreshEvent` and executing JS on the WebView

### Test updates

- **`BookmarkDetailViewModelTest.kt`** — updated 3 tests to verify new code paths:
  - Create annotation → verifies `refreshHtmlForAnnotations` called
  - Color update → verifies `executeForceRefresh` NOT called
  - Delete annotation → verifies `refreshHtmlForAnnotations` called

## Inline image safety

The Tier 2 HTML-only refresh explicitly does not fetch or modify resource files.
`fetchHtmlOnly()` sets `withResources=false`. `updateHtml()` only overwrites
`index.html`. Image files remain on disk under `offline_content/{bookmarkId}/`
and relative paths (e.g., `./resources/image.jpg`) resolve identically via
`WebViewAssetLoader` after the HTML update.

The JS innerHTML replacement also preserves image rendering: the browser re-resolves
relative URLs against the same base URL, and previously loaded images are served
from the WebView cache.
