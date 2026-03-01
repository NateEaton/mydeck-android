# Reader Image Gallery & Context Menus

**Date:** 2026-02-28
**Branch:** `claude/readeck-data-formatting-research-TQgGb`
**Status:** Draft

---

## Overview

Add a native image gallery (lightbox) to the article reader, with thumbnail navigation, pinch-to-zoom, swipe between images, and a link indicator for hyperlinked images. Add long-press context menus to the reader WebView for images and links, providing feature parity with the list view's context menu system. Intercept and neutralize image-wrapping hyperlinks (e.g., Substack CDN links) so that tapping an image opens the gallery instead of launching an external browser.

---

## Motivation

### Problems Solved

1. **Accidental external navigation:** Many sites (especially Substack) wrap article images in `<a>` tags linking to CDN image URLs. Tapping an image in the current reader triggers `shouldOverrideUrlLoading`, opening Chrome Custom Tabs to show a raw image — a disruptive, useless experience. This is worse on mobile because images fill more of the screen and are easy to accidentally tap.

2. **No image zoom:** Article images are constrained to `max-width: 100%` in the reader template. Users cannot zoom into details, diagrams, or infographics.

3. **No reader context menus:** The list view provides long-press menus for links and images (copy URL, download, share, open in browser). The reader WebView has none — long-pressing does nothing useful.

4. **No gallery navigation:** Image-heavy articles (photo essays, tutorials with screenshots) require the user to scroll back and forth. A gallery with thumbnail navigation allows browsing all images in sequence.

### User Feedback

> "Some Substacks hyperlink their images to direct Substack CDN links, and clicking an image amounts to clicking an external link, so a browser is opened with the full image loaded. Which is OK, except Readeck explicitly archives all images so it's not really useful... This issue is actually worse on mobile since the images are easier to misclick."

### How Readeck Handles This

The Readeck web app uses a Stimulus controller (`modal_images_controller.js`) that:
- Scans article images, filtering out small ones (< 48px or both < 128px)
- Wraps each qualifying image in a `<span>` with an expand button overlay (top-right, visible on hover)
- If the image is the sole child of an `<a>` tag, wraps the link instead (neutralizing it)
- Clicking the expand button opens the image in a `<dialog>` element (modal lightbox)
- Clicking anywhere on the lightbox closes it

MyDeck's approach adapts this for mobile: single-tap on any qualifying image opens a native gallery overlay with swipe navigation and pinch-to-zoom, providing a better mobile experience than Readeck's hover-based expand button.

---

## Current State

- Article HTML is loaded into a `WebView` via `loadDataWithBaseURL` in `BookmarkDetailWebViews.kt`.
- All link clicks go through `shouldOverrideUrlLoading` → `openUrlInCustomTab()`. There is no image click interception.
- JavaScript is enabled for articles (needed for search highlighting). Two JS bridges exist (`WebViewSearchBridge`, `WebViewTypographyBridge`), both using `evaluateJavascript()` (Android → WebView, unidirectional). No `@JavascriptInterface` bridges exist yet.
- The list view uses `combinedClickable` with two-zone `DropdownMenu` context menus (body zone: Copy Link, Copy Link Text, Share Link, Open in Browser; image zone: adds Copy Image URL, Download Image, Share Image).
- Coil 3.x is already a project dependency (`coil-compose`, `coil-network-okhttp`, `coil-svg`).
- `HorizontalPager` is available via the Compose BOM (2025.04.01).
- No pinch-to-zoom library is present; custom gesture handling will be used.

---

## Design Decisions

### 1. Native Gallery, Not WebView Overlay

The image gallery is a native Compose `Dialog` with `HorizontalPager`, not a CSS/JS overlay inside the WebView. Native rendering provides pinch-to-zoom, swipe gestures, and proper back-button handling that a WebView overlay cannot match on mobile.

### 2. Single-Tap Opens Gallery, Not the Link

For any qualifying image (including those wrapped in hyperlinks), a single tap opens the gallery. The original hyperlink, if present, is accessible via a link indicator button in the gallery chrome and via the long-press context menu. This prioritizes the most common user intent (viewing the image) over the rare intent (following the link).

### 3. Gallery With Thumbnail Strip

When an article has multiple qualifying images, the gallery shows a horizontal thumbnail strip at the bottom. The user can swipe the main image or tap a thumbnail to navigate. Tapping the current image toggles visibility of all chrome (thumbnails, close button, link indicator). This matches the standard pattern in Google Photos, iOS Photos, and other gallery apps.

### 4. Link Indicator in Gallery Chrome

When the currently-displayed image has an associated hyperlink (to a page or an image URL), a link icon button appears in the gallery top bar. Tapping it dismisses the gallery and opens the link in Chrome Custom Tabs (matching existing reader link behavior). This makes link discovery self-evident without cluttering the article reading experience.

### 5. Long-Press Via Native WebView Hit Testing

Long-press detection uses `WebView.setOnLongClickListener` + `WebView.hitTestResult`, which feels native and avoids touch-handling conflicts with WebView scrolling. For `SRC_IMAGE_ANCHOR_TYPE` results (where `hitTestResult` only provides the link URL), a JS query enriches the data with the image URL.

### 6. Small Image Filtering

Images are excluded from gallery interception if:
- `naturalWidth < 48` or `naturalHeight < 48` (tiny in either dimension)
- `naturalWidth < 128` AND `naturalHeight < 128` (small in both dimensions)

This matches Readeck's filtering logic and prevents expand buttons / click interception on icons, avatars, and decorative elements.

---

## Image Classification

The JavaScript layer classifies each qualifying `<img>` into one of three categories:

| Category | DOM Pattern | Example | Gallery Behavior |
|---|---|---|---|
| **Plain image** | `<img src="...">` (no wrapping link) | Inline article image | Tap → gallery. No link indicator. |
| **Image-linked image** | `<a href="image.ext"><img src="..."></a>` where `<a>` has exactly one child | Substack CDN links | Tap → gallery (link intercepted). Link indicator shows in gallery (link icon). Long-press shows "Open Link in Browser". |
| **Page-linked image** | `<a href="page.html"><img src="..."></a>` where `<a>` has exactly one child | Image linking to another article | Same as image-linked, but link indicator opens the page URL. |

**Link type detection:** The `href` is classified as "image" if it matches the pattern `\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff|avif)(\?.*)?$` (case-insensitive). Otherwise it is classified as "page".

**Non-qualifying patterns (no interception):**
- `<a href="..."><img src="..."> <span>caption</span></a>` — link has multiple children; image click triggers the link normally.
- `<img>` with dimensions below the small-image threshold — no interception, no context menu.

---

## Tap/Long-Press Behavior Matrix

| Target | Single Tap | Long-Press |
|---|---|---|
| **Qualifying image** (plain) | Open gallery at this image | Context menu: Copy Image URL, Download Image, Share Image |
| **Qualifying image** (linked) | Open gallery at this image (link intercepted) | Context menu: Copy Image URL, Download Image, Share Image, [divider], Copy Link Address, Open Link in Browser |
| **Text link** | Open in Chrome Custom Tab (current behavior, unchanged) | Context menu: Copy Link Address, Copy Link Text, Share Link, Open in Browser |
| **Small image** (below threshold) | No interception (default WebView behavior) | No context menu |
| **Non-link, non-image content** | No interception | No context menu |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ BookmarkDetailScreen                                    │
│                                                         │
│  ┌──────────────────────────────────────┐               │
│  │ BookmarkDetailArticle                │               │
│  │                                      │               │
│  │  ┌─────────────────────────────┐     │               │
│  │  │ WebView                     │     │               │
│  │  │                             │     │               │
│  │  │  JS: ImageInterceptor       │     │               │
│  │  │  (injected on page load)    │     │               │
│  │  │                             │     │               │
│  │  │  ┌─────────────────────┐    │     │               │
│  │  │  │ @JavascriptInterface│    │     │               │
│  │  │  │ MyDeckImageBridge   │────┼─────┼───► ViewModel │
│  │  │  └─────────────────────┘    │     │       │       │
│  │  │                             │     │       │       │
│  │  │  setOnLongClickListener ────┼─────┼───► Context   │
│  │  │                             │     │     Menu      │
│  │  └─────────────────────────────┘     │       │       │
│  └──────────────────────────────────────┘       │       │
│                                                  │       │
│  ┌──────────────────────────────────────────────▼──┐    │
│  │ ImageGalleryOverlay (Compose Dialog)             │    │
│  │                                                  │    │
│  │  HorizontalPager ← swipe between images          │    │
│  │    └─ ZoomableImage ← pinch-to-zoom + double-tap │    │
│  │                                                  │    │
│  │  Top bar: [Close] [1/N counter] [Link icon?]     │    │
│  │  Bottom: [Thumbnail strip]                       │    │
│  └──────────────────────────────────────────────────┘    │
│                                                         │
│  ┌──────────────────────────────────────────────────┐    │
│  │ ReaderContextMenu (DropdownMenu)                 │    │
│  │  - Image actions / Link actions                  │    │
│  └──────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Data Models

```kotlin
// domain/model/GalleryImage.kt

/**
 * Represents a single image in the article gallery.
 * Built by the JavaScript image interceptor and sent via the bridge.
 */
data class GalleryImage(
    val src: String,          // Image URL (from img.src)
    val alt: String,          // Alt text (from img.alt, may be empty)
    val linkHref: String?,    // URL of wrapping <a> tag, or null if plain image
    val linkType: String,     // "none" | "image" | "page"
)

/**
 * Data sent from JavaScript when an image is tapped.
 * Contains ALL qualifying images in the article for gallery navigation,
 * plus the index of the tapped image.
 */
data class ImageGalleryData(
    val images: List<GalleryImage>,
    val currentIndex: Int,
)
```

These are UI-layer data classes. No Room entity, API DTO, or repository change is needed — all data originates from the article HTML in the WebView.

---

## JavaScript Injection Layer

### `WebViewImageBridge.kt` — JS Generation

A new bridge file following the pattern of `WebViewSearchBridge.kt`. Contains a companion object with functions that return JavaScript strings for execution via `evaluateJavascript()`.

#### `injectImageInterceptor(): String`

Returns JavaScript that:

1. **Scans the DOM** for all `img[src]` elements within `.container` (the article content wrapper).

2. **Filters small images** using `naturalWidth` / `naturalHeight` with the thresholds defined in [Image Classification](#image-classification). Uses `img.complete` to check if dimensions are available; for images not yet loaded, attaches a `load` event listener to check dimensions later.

3. **Builds an image registry** — an array of objects: `{ src, alt, linkHref, linkType, naturalWidth, naturalHeight }`.

4. **Attaches click handlers** to each qualifying image (or its parent `<a>` if the image is the sole child of a link). The handler calls `event.preventDefault()`, `event.stopPropagation()`, and invokes the bridge:
   ```javascript
   MyDeckImageBridge.onImageTapped(JSON.stringify({
       images: imageRegistry,
       currentIndex: thisImageIndex
   }));
   ```

5. **Adds a visual cursor** (`cursor: pointer`) to qualifying images on non-touch devices (for consistency, though this is a mobile app).

6. **Does NOT modify the DOM structure** — no wrapper elements or button overlays are injected. Click interception is purely event-based. This avoids layout shifts or conflicts with Readeck's own HTML processing.

#### `getImageUrlAtLink(linkHref: String): String`

Returns JavaScript that finds the `<img>` inside an `<a>` tag matching the given `href` and returns its `src`. Used to enrich `SRC_IMAGE_ANCHOR_TYPE` hit test results during long-press handling.

```javascript
(function() {
    var links = document.querySelectorAll('a[href="ESCAPED_HREF"]');
    for (var i = 0; i < links.length; i++) {
        var img = links[i].querySelector('img');
        if (img) return img.src;
    }
    return '';
})();
```

### Full JavaScript for `injectImageInterceptor()`

```javascript
(function() {
    'use strict';

    var MIN_DIM = 48;
    var MIN_COMBINED = 128;
    var IMAGE_EXT_RE = /\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff|avif)(\?.*)?$/i;

    var registry = [];

    function isSmall(img) {
        var w = img.naturalWidth || img.width;
        var h = img.naturalHeight || img.height;
        if (w < MIN_DIM || h < MIN_DIM) return true;
        if (w < MIN_COMBINED && h < MIN_COMBINED) return true;
        return false;
    }

    function classifyLink(a) {
        if (!a || a.tagName !== 'A' || a.children.length !== 1) {
            return { href: null, type: 'none' };
        }
        var href = a.href;
        if (!href) return { href: null, type: 'none' };
        var type = IMAGE_EXT_RE.test(href) ? 'image' : 'page';
        return { href: href, type: type };
    }

    function processImage(img) {
        if (isSmall(img)) return;

        var parent = img.parentElement;
        var link = classifyLink(parent);
        var index = registry.length;

        registry.push({
            src: img.src,
            alt: img.alt || '',
            linkHref: link.href,
            linkType: link.type
        });

        img.dataset.mydeckGalleryIndex = index;
        img.style.cursor = 'pointer';

        var clickTarget = (link.href && parent.tagName === 'A') ? parent : img;
        clickTarget.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            MyDeckImageBridge.onImageTapped(JSON.stringify({
                images: registry,
                currentIndex: index
            }));
        }, true);
    }

    var container = document.querySelector('.container');
    if (!container) return;

    container.querySelectorAll('img[src]').forEach(function(img) {
        if (img.complete && img.naturalWidth > 0) {
            processImage(img);
        } else {
            img.addEventListener('load', function() {
                processImage(img);
            }, { once: true });
        }
    });
})();
```

---

## JavaScript Bridge Interface

### `WebViewImageBridge.kt` — `@JavascriptInterface` Class

```kotlin
// ui/detail/WebViewImageBridge.kt

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import kotlinx.serialization.json.Json

/**
 * JavaScript-to-native bridge for image interactions in the reader WebView.
 *
 * Registered on the WebView as "MyDeckImageBridge".
 * Called from injected JavaScript when the user taps a qualifying image.
 */
class WebViewImageBridge(
    private val json: Json,
    private val onImageTapped: (ImageGalleryData) -> Unit,
) {
    @JavascriptInterface
    fun onImageTapped(jsonString: String) {
        // @JavascriptInterface methods run on a background thread.
        // Parse JSON here, then post the result to the main thread.
        try {
            val data = json.decodeFromString<ImageGalleryData>(jsonString)
            Handler(Looper.getMainLooper()).post {
                onImageTapped(data)
            }
        } catch (e: Exception) {
            // Malformed JSON from JS — log and ignore
            timber.log.Timber.w(e, "Failed to parse image tap data")
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckImageBridge"

        /** Returns JS to inject into the WebView on page load. */
        fun injectImageInterceptor(): String {
            // Returns the JavaScript shown in the JS Injection Layer section
            return """(function() { ... })();"""
        }

        /** Returns JS to find the image URL inside a link with the given href. */
        fun getImageUrlAtLink(linkHref: String): String {
            val escaped = linkHref
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
            return """
                (function() {
                    var links = document.querySelectorAll('a[href="${escaped}"]');
                    for (var i = 0; i < links.length; i++) {
                        var img = links[i].querySelector('img');
                        if (img) return img.src;
                    }
                    return '';
                })();
            """.trimIndent()
        }
    }
}
```

---

## WebView Integration

### Changes to `BookmarkDetailWebViews.kt`

The `BookmarkDetailArticle` composable's WebView factory block gains three additions:

#### 1. Register the JavaScript bridge

```kotlin
// In WebView factory, after setting webViewClient:
addJavascriptInterface(
    WebViewImageBridge(
        json = json,
        onImageTapped = { data -> onImageTapped(data) }
    ),
    WebViewImageBridge.BRIDGE_NAME
)
```

#### 2. Inject the image interceptor on page load

```kotlin
// In WebViewClient.onPageFinished, after typography injection:
val imageJs = WebViewImageBridge.injectImageInterceptor()
it.evaluateJavascript(imageJs, null)
```

The image interceptor must run **after** `onPageFinished` to ensure all images have loaded and `naturalWidth`/`naturalHeight` are available. For images that load asynchronously (e.g., via Readeck media URLs), the JS uses `img.addEventListener('load', ...)` as a fallback.

#### 3. Add long-press handler

```kotlin
// In WebView factory:
setOnLongClickListener { view ->
    val webView = view as WebView
    val result = webView.hitTestResult

    when (result.type) {
        WebView.HitTestResult.IMAGE_TYPE -> {
            onImageLongPress(
                imageUrl = result.extra ?: "",
                linkUrl = null,
                linkType = "none"
            )
            true
        }
        WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
            onLinkLongPress(
                linkUrl = result.extra ?: ""
            )
            true
        }
        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
            // hitTestResult only gives us the link URL.
            // Query JS for the image URL.
            val linkUrl = result.extra ?: ""
            webView.evaluateJavascript(
                WebViewImageBridge.getImageUrlAtLink(linkUrl)
            ) { imageUrl ->
                val cleanUrl = imageUrl?.trim('"') ?: ""
                val imageExtRe = Regex("""\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff|avif)(\?.*)?$""",
                    RegexOption.IGNORE_CASE)
                val linkType = if (imageExtRe.containsMatchIn(linkUrl)) "image" else "page"
                onImageLongPress(
                    imageUrl = cleanUrl,
                    linkUrl = linkUrl,
                    linkType = linkType
                )
            }
            true
        }
        else -> false
    }
}
```

### New Composable Parameters

`BookmarkDetailArticle` gains these callback parameters:

```kotlin
@Composable
fun BookmarkDetailArticle(
    modifier: Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState = ...,
    onArticleSearchUpdateResults: (Int) -> Unit = {},
    // New parameters:
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String) -> Unit = { _, _, _ -> },
    onLinkLongPress: (linkUrl: String) -> Unit = {},
)
```

---

## Image Gallery Overlay

### `ImageGalleryOverlay.kt` (new file)

A full-screen Compose `Dialog` containing the gallery viewer.

### Structure

```
Dialog (fullscreen, black background)
├── HorizontalPager (swipe between images)
│   └── ZoomableImage (per page)
│       └── AsyncImage (Coil) with gesture modifiers
├── GalleryTopBar (animated visibility)
│   ├── Close button (top-left, Icon: Icons.Default.Close)
│   ├── Image counter "3 of 12" (center)
│   └── Link indicator button (top-right, Icon: Icons.AutoMirrored.Filled.OpenInNew)
│       └── Only visible when currentImage.linkHref != null
└── ThumbnailStrip (animated visibility, bottom)
    └── LazyRow of small AsyncImage thumbnails
        └── Selected thumbnail has a highlight border
```

### Composable Signature

```kotlin
@Composable
fun ImageGalleryOverlay(
    galleryData: ImageGalleryData,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
)
```

### Behavior

| User Action | Result |
|---|---|
| **Swipe left/right** on image | Navigate to previous/next image. Pager animates. Thumbnail highlight updates. |
| **Tap** on image | Toggle chrome visibility (top bar + thumbnail strip). |
| **Double-tap** on image | Toggle between 1x and 2x zoom. If zoomed, reset to 1x; if at 1x, zoom to 2x centered on tap point. |
| **Pinch** on image | Zoom in/out (1x to 5x range). Pan enabled when zoomed > 1x. |
| **Tap** thumbnail | Navigate pager to that image (animated). |
| **Tap** close button | Dismiss gallery. |
| **Tap** link icon | Dismiss gallery, call `onOpenLink(currentImage.linkHref)`. |
| **Back gesture/button** | Dismiss gallery. |

### Chrome Auto-Hide

- Chrome starts **visible** when gallery opens.
- Chrome hides automatically when the user **pinch-zooms** beyond 1x.
- Chrome reappears when zoom returns to 1x.
- Tap on image toggles chrome regardless of zoom level.

### ZoomableImage Composable

```kotlin
@Composable
private fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    onClick: () -> Unit,
    onZoomChanged: (Float) -> Unit,
)
```

Implementation uses:
- `AsyncImage` (Coil) with `ContentScale.Fit`
- `Modifier.pointerInput` with `detectTapGestures` for tap and double-tap
- `Modifier.pointerInput` with `detectTransformGestures` for pinch-to-zoom and pan
- `Modifier.graphicsLayer` for scale and translation transforms
- Scale clamped to `1f..5f`
- Pan clamped to image bounds (prevent panning beyond the image edge)
- Double-tap toggles between 1x and 2x with animated transition (`animateFloatAsState`)

### ThumbnailStrip Composable

```kotlin
@Composable
private fun ThumbnailStrip(
    images: List<GalleryImage>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

Implementation:
- `LazyRow` with `AsyncImage` thumbnails (fixed size, e.g., 56.dp x 56.dp)
- Current thumbnail has a 2.dp border in `MaterialTheme.colorScheme.primary`
- Auto-scrolls to keep the current thumbnail centered (`LaunchedEffect` on `currentIndex` → `lazyListState.animateScrollToItem`)
- Background: semi-transparent black bar (`Color.Black.copy(alpha = 0.6f)`)
- Only rendered when `images.size > 1`

### GalleryTopBar Composable

```kotlin
@Composable
private fun GalleryTopBar(
    currentImage: GalleryImage,
    imageIndex: Int,        // 1-based
    totalImages: Int,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Implementation:
- Semi-transparent black background (`Color.Black.copy(alpha = 0.6f)`)
- `Row` with:
  - `IconButton` close (left): `Icons.Default.Close`, white tint
  - `Text` counter (center): "%d of %d" format, white, `bodyMedium` style
  - `IconButton` link (right): `Icons.AutoMirrored.Filled.OpenInNew`, white tint. Only composed when `currentImage.linkHref != null`. `onClick` calls `onOpenLink(currentImage.linkHref!!)`.
- `WindowInsets.statusBars` padding to avoid overlap with system UI

---

## Long-Press Context Menu

### State in ViewModel

```kotlin
// BookmarkDetailViewModel.kt — new state

data class ReaderContextMenuState(
    val visible: Boolean = false,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val linkType: String = "none",  // "none" | "image" | "page"
)

private val _readerContextMenu = MutableStateFlow(ReaderContextMenuState())
val readerContextMenu: StateFlow<ReaderContextMenuState> = _readerContextMenu.asStateFlow()

fun onShowImageContextMenu(imageUrl: String, linkUrl: String?, linkType: String) {
    _readerContextMenu.value = ReaderContextMenuState(
        visible = true,
        imageUrl = imageUrl,
        linkUrl = linkUrl,
        linkType = linkType,
    )
}

fun onShowLinkContextMenu(linkUrl: String) {
    _readerContextMenu.value = ReaderContextMenuState(
        visible = true,
        imageUrl = null,
        linkUrl = linkUrl,
        linkType = "page",
    )
}

fun onDismissReaderContextMenu() {
    _readerContextMenu.value = ReaderContextMenuState()
}
```

### Gallery State in ViewModel

```kotlin
// BookmarkDetailViewModel.kt — new state

private val _galleryData = MutableStateFlow<ImageGalleryData?>(null)
val galleryData: StateFlow<ImageGalleryData?> = _galleryData.asStateFlow()

fun onImageTapped(data: ImageGalleryData) {
    _galleryData.value = data
}

fun onDismissGallery() {
    _galleryData.value = null
}
```

### Context Menu UI

The context menu is a `DropdownMenu` in `BookmarkDetailScreen`, following the same pattern as the list view's context menus in `BookmarkCard.kt`.

#### Image Context Menu (imageUrl != null)

| Item | Icon | Action |
|---|---|---|
| Copy Image URL | `Icons.Outlined.ContentCopy` | Copy `imageUrl` to clipboard, show snackbar |
| Download Image | `Icons.Outlined.Download` | Enqueue via `DownloadManager`, show snackbar |
| Share Image | `Icons.Outlined.Share` | Download to cache → share via `FileProvider` intent |
| *Divider* | — | Only if `linkUrl != null` |
| Copy Link Address | `Icons.Outlined.ContentCopy` | Copy `linkUrl` to clipboard, show snackbar |
| Open Link in Browser | `Icons.AutoMirrored.Filled.OpenInNew` | `openUrlInCustomTab(linkUrl)` |

#### Link Context Menu (imageUrl == null, linkUrl != null)

| Item | Icon | Action |
|---|---|---|
| Copy Link Address | `Icons.Outlined.ContentCopy` | Copy `linkUrl` to clipboard, show snackbar |
| Share Link | `Icons.Outlined.Share` | Share intent with link URL as text |
| Open in Browser | `Icons.AutoMirrored.Filled.OpenInNew` | `openUrlInCustomTab(linkUrl)` |

### Action Implementations

All action implementations reuse the existing patterns from `BookmarkListScreen.kt`:
- **Clipboard:** `LocalClipboardManager.current.setText(AnnotatedString(url))`
- **Download:** `DownloadManager.Request(Uri.parse(imageUrl))` → `DIRECTORY_DOWNLOADS`
- **Share Image:** Download to `context.cacheDir/images/`, share via `FileProvider.getUriForFile()` with `ACTION_SEND` + `image/*`
- **Share Link:** `ACTION_SEND` with `text/plain`
- **Open in Browser:** `openUrlInCustomTab(context, url)` (existing utility)

Consider extracting these shared action implementations into a common utility (e.g., `BookmarkActions.kt` or extension functions) to avoid duplication between `BookmarkListScreen` and `BookmarkDetailScreen`. This refactor is optional and can be done as a follow-up.

---

## Interaction With shouldOverrideUrlLoading

The existing `shouldOverrideUrlLoading` in the WebView client continues to handle text links (non-image `<a>` tags). It does **not** need modification — the JavaScript click interceptor calls `preventDefault()` and `stopPropagation()` on image clicks **before** they bubble to the WebView's native link handler. This means:

- **Image in link:** JS intercepts → bridge → gallery. `shouldOverrideUrlLoading` never fires.
- **Text link:** No JS interception → WebView fires `shouldOverrideUrlLoading` → Chrome Custom Tab (current behavior preserved).

If a race condition or edge case causes both to fire (JS interception fails but the link click still propagates), the worst case is that Chrome Custom Tabs opens — the same as current behavior. No functional regression.

---

## String Resources

Add to `values/strings.xml` and all 9 language-specific `strings.xml` files:

```xml
<string name="gallery_close">Close</string>
<string name="gallery_open_link">Open link</string>
<string name="gallery_image_counter">%1$d of %2$d</string>
```

The existing strings are reused where possible:
- `action_copy_link` → "Copy Link Address" (existing)
- `action_copy_image` → "Copy Image" (existing)
- `action_download_image` → "Download Image" (existing)
- `action_share_image` → "Share Image" (existing)
- `action_share_link` → "Share Link" (existing)
- `action_open_in_browser` → "Open in Browser" (existing)
- `link_copied` → snackbar (existing)
- `image_url_copied` → snackbar (existing)
- `download_started` → snackbar (existing)

---

## Edge Cases

| Scenario | Handling |
|---|---|
| **Image not yet loaded when JS scans** | `img.addEventListener('load', ...)` defers classification until dimensions available |
| **Image fails to load** | Not added to registry (no `load` event fires). No gallery entry. |
| **Article has zero qualifying images** | No JS click interception occurs. No gallery available. Long-press on non-qualifying images shows no menu. |
| **Article has exactly one qualifying image** | Gallery opens without thumbnail strip. |
| **Gallery image URL fails to load (Coil)** | `AsyncImage` shows placeholder/error state. User can swipe past it. |
| **Very large image (high-res photo)** | Coil handles downsampling. `ContentScale.Fit` ensures it fits the viewport. |
| **SVG image** | Coil SVG decoder (already a dependency) handles rendering. `naturalWidth`/`naturalHeight` may be 0 for SVGs; use `getBoundingClientRect()` as fallback in JS. |
| **Image inside nested links** | Only direct parent `<a>` is checked (`img.parentElement`). Deeper nesting is not intercepted. |
| **Search highlighting active** | Gallery does not interfere with search — search operates on DOM text nodes, gallery intercepts image clicks. |
| **Typography settings change** | Does not affect gallery data (images are URL-based, not DOM-dependent). No re-injection needed. |
| **Content reload (theme change)** | Image interceptor must be re-injected in `onPageFinished` (same as typography and search). |
| **Back press during gallery** | `Dialog`'s `onDismissRequest` handles this. Gallery dismisses, reader remains. |
| **Long-press on image while gallery is open** | Not possible — gallery is a `Dialog` overlay that captures all input. |

---

## New Dependencies

**None.** All components use existing project dependencies:
- `HorizontalPager` — `androidx.compose.foundation.pager` (included via Compose BOM)
- `AsyncImage` — `io.coil-kt.coil3:coil-compose` (already in project)
- `detectTransformGestures` / `detectTapGestures` — `androidx.compose.foundation.gestures` (included via Compose BOM)
- `@JavascriptInterface` — `android.webkit` (Android SDK)

---

## File Inventory

### New Files

| File | Description |
|---|---|
| `ui/detail/ImageGalleryOverlay.kt` | Gallery composable (HorizontalPager, ZoomableImage, ThumbnailStrip, GalleryTopBar) |
| `ui/detail/WebViewImageBridge.kt` | JS injection functions + `@JavascriptInterface` class |

### Modified Files

| File | Changes |
|---|---|
| `ui/detail/components/BookmarkDetailWebViews.kt` | Register JS bridge on WebView, inject image interceptor in `onPageFinished`, add `setOnLongClickListener`, add new composable parameters |
| `ui/detail/BookmarkDetailViewModel.kt` | Add `galleryData` and `readerContextMenu` state flows and associated functions |
| `ui/detail/BookmarkDetailScreen.kt` | Wire gallery overlay (shown when `galleryData != null`), wire context menu (shown when `readerContextMenu.visible`), add action handlers |
| `res/values/strings.xml` | Add 3 new strings |
| `res/values-*/strings.xml` (9 files) | Add same 3 strings as English placeholders |

---

## Implementation Sequence

1. **Data models** — `GalleryImage`, `ImageGalleryData`, `ReaderContextMenuState` in `domain/model/` or inline in ViewModel.
2. **`WebViewImageBridge.kt`** — JS injection function (`injectImageInterceptor`), JS enrichment function (`getImageUrlAtLink`), `@JavascriptInterface` class with `onImageTapped`.
3. **`ImageGalleryOverlay.kt`** — `ZoomableImage`, `ThumbnailStrip`, `GalleryTopBar`, `ImageGalleryOverlay` composables.
4. **`BookmarkDetailViewModel.kt`** — Add `galleryData` and `readerContextMenu` state, add functions (`onImageTapped`, `onDismissGallery`, `onShowImageContextMenu`, `onShowLinkContextMenu`, `onDismissReaderContextMenu`).
5. **`BookmarkDetailWebViews.kt`** — Register JS bridge (`addJavascriptInterface`), inject interceptor in `onPageFinished`, add `setOnLongClickListener`, add new callback parameters.
6. **`BookmarkDetailScreen.kt`** — Wire gallery overlay (observe `galleryData`), wire context menu (observe `readerContextMenu`), implement action handlers (clipboard, download, share, open link).
7. **String resources** — Add 3 new strings to all 10 locale files.
8. **Manual testing** — Verify with debug export bookmarks (Substack-style linked images, plain images, images linking to pages).

---

## Testing Considerations

### Manual Test Cases

1. **Plain image tap** → Gallery opens, image displayed, no link icon.
2. **Substack-style linked image tap** → Gallery opens (NOT Chrome Custom Tabs), link icon visible in top bar.
3. **Link icon tap in gallery** → Gallery dismisses, Chrome Custom Tab opens with the link URL.
4. **Multi-image article** → Gallery opens with thumbnail strip, swipe navigates, thumbnail highlight follows.
5. **Single-image article** → Gallery opens without thumbnail strip.
6. **Small image (icon)** → No interception on tap, no context menu on long-press.
7. **Pinch-to-zoom** → Image zooms smoothly, chrome auto-hides, pan works when zoomed.
8. **Double-tap** → Toggles between 1x and 2x zoom.
9. **Long-press on image** → Context menu with image actions (Copy URL, Download, Share).
10. **Long-press on linked image** → Context menu with image actions + divider + link actions.
11. **Long-press on text link** → Context menu with link actions (Copy, Share, Open in Browser).
12. **Gallery back press** → Gallery dismisses, reader intact.
13. **Theme change during gallery** → Gallery dismisses (content reloads), re-opens on next tap.
14. **Text search + image tap** → Search highlights preserved, gallery opens normally.

### Automated Tests

- **ViewModel unit tests:** `onImageTapped` sets `galleryData`, `onDismissGallery` clears it, `onShowImageContextMenu` / `onShowLinkContextMenu` set menu state, `onDismissReaderContextMenu` clears it.
- **`WebViewImageBridge` unit tests:** `getImageUrlAtLink` escapes special characters correctly. `injectImageInterceptor` returns valid JavaScript (syntax check).

---

## Future Considerations

- **Annotation highlights + gallery:** The planned Annotations feature uses `@JavascriptInterface` for text selection. The image bridge establishes the pattern for WebView → native communication that annotations will follow.
- **Image caching:** Gallery images are loaded from Readeck media URLs (network). If offline reading support is added, these URLs would need to be served from a local cache. No changes needed now — Coil's disk cache provides some benefit.
- **Share image from gallery:** A future enhancement could add share/download buttons to the gallery top bar, in addition to the long-press context menu. This spec does not include it to keep the gallery chrome minimal.
- **Video thumbnails:** Readeck's `modal_videos_controller.js` handles video embeds similarly. If video lightbox is desired, it would follow the same pattern with an iframe or `VideoView` instead of `AsyncImage`.
