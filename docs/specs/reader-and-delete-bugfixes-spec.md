# Spec: Reader & Delete Bugfixes (v0.11.1)

## Summary

A collection of bugs and small improvements surfaced during user testing.
This spec covers five items: rapid-delete race condition, text-size shift when
margins change, a reproducible layout glitch, video fullscreen gaps, narrower
minimum margin, and a theme-switch reflow investigation.

---

## 1. Rapid Successive Deletes — Only First Registers

### Problem

`pendingDeletionBookmarkId` in `BookmarkListViewModel` is a single `String?`.
When the user deletes Card A and then Card B 3-4 seconds later:

1. Card B's ID overwrites Card A → Card A un-greys.
2. A new snackbar replaces the first; the first coroutine is orphaned.
3. When the second snackbar dismisses, only Card B's delete executes.

### Root Cause

* `BookmarkListViewModel.kt:88-89` — `_pendingDeletionBookmarkId: MutableStateFlow<String?>`
* `BookmarkListScreen.kt:164-181` — `stageDeleteWithSnackbar()` launches a new
  coroutine per delete but only one snackbar can be active at a time.

### Proposed Fix

When a second delete is staged while one is already pending, **immediately
confirm (execute) the first delete** before staging the second. This matches
standard MD3 snackbar behavior: showing a new snackbar dismisses the previous
one, and dismissal without Undo = confirm delete.

Concretely:

1. Change `_pendingDeletionBookmarkId` to `MutableStateFlow<Set<String>>` so
   the UI can grey out multiple cards simultaneously during the brief overlap.
2. In `stageDeleteWithSnackbar`, before launching a new snackbar coroutine,
   dismiss any existing snackbar via `snackbarHostState.currentSnackbarData
   ?.dismiss()`. The existing coroutine's `SnackbarResult.Dismissed` branch
   will fire `onConfirmDeleteBookmark()` for the previous card.
3. `onConfirmDeleteBookmark()` should pop the oldest pending ID from the set
   (not clear the entire set).
4. `onCancelDeleteBookmark()` removes only the currently-snackbar'd ID.

### Files

* `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`
* `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt`

---

## 2. Text Size Changes When Switching Margin/Width Setting

### Problem

Changing the text width from Wide → Narrow (or vice versa) causes an apparent
~14% change in rendered text size, confirmed by screenshot analysis.

### Root Cause

The Sakura-based HTML templates contain responsive CSS media queries:

```css
@media (max-width: 684px) { body { font-size: 1.75rem; } }
@media (max-width: 382px) { body { font-size: 1.65rem; } }
```

The content width fractions (Wide=0.95, Medium=0.85, Narrow=0.75) change the
WebView's pixel width. When the width crosses 684px or 382px, the media query
fires and changes the CSS font-size. Meanwhile `textZoom` is applied
multiplicatively on top, compounding the shift.

### Proposed Fix

Remove both `@media (max-width: ...)` font-size blocks from all four HTML
templates. Font size is already fully controlled by `WebView.settings.textZoom`
from the Android side; these CSS breakpoints are vestigial from the original
Sakura theme and are now counterproductive.

### Files

* `app/src/main/assets/html_template_light.html`
* `app/src/main/assets/html_template_dark.html`
* `app/src/main/assets/html_template_sepia.html`
* `app/src/main/assets/html_template_black.html`

---

## 3. Reproducible Layout/Rendering Glitch (Issue #11)

### Problem

Certain articles (e.g. worksinprogress.co/issue/why-europe-doesnt-have-a-tesla)
break visually when typography settings are changed. The break is intermittent
and appears tied to a specific combination of settings values rather than any
single setting.

### Root Cause (Hypothesis)

Likely related to item #2: the media-query font-size jump interacts badly with
certain article layouts that use relative units. Removing the media queries may
resolve this. If not, the interaction between `textZoom`, the JS typography
bridge (`WebViewTypographyBridge.applyTypography`), and the 150ms delay in the
`LaunchedEffect` (BookmarkDetailWebViews.kt:193) should be investigated — a
race between the WebView's layout pass and the JS injection could cause
transient mis-rendering.

### Proposed Fix

1. Apply fix #2 first and re-test.
2. If the glitch persists, investigate removing the delay or using
   `requestLayout()` after JS evaluation completes.

### Files

* Same as #2, plus `BookmarkDetailWebViews.kt:186-199`

---

## 4. Video Fullscreen Not Working for YouTube/Vimeo Embeds

### Problem

The custom fullscreen button (rotate icon at top-right) only appears for
bookmarks typed as VIDEO. YouTube and Vimeo links saved as articles with
embedded video don't get this treatment — they render inline with no working
fullscreen.

### Root Cause

* `BookmarkDetailWebViews.kt:307-311` — `settings.javaScriptEnabled` and
  `settings.domStorageEnabled` are set based on `bookmark.type == VIDEO`.
* The `onShowCustomView` / `onHideCustomView` callbacks that drive fullscreen
  are gated on `isVideo` (line 342).
* Content type is determined by Readeck server-side extraction, not by the
  app. An article page that happens to contain an embedded YouTube iframe is
  classified as ARTICLE, not VIDEO.

### Proposed Fix

1. Detect embedded iframes in article content at render time (the HTML is
   available in `articleContent`).
2. When an article contains `<iframe` tags with video-hosting domains
   (youtube.com, vimeo.com, dailymotion.com, etc.), enable `domStorageEnabled`
   and register the fullscreen callbacks even for ARTICLE type.
3. This is a targeted enhancement — it doesn't reclassify the bookmark, just
   enables the video player affordances when video embeds are detected.

### Files

* `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`

---

## 5. Narrower Minimum Margin (Wider Layout)

### Problem

The WIDE setting (`widthFraction = 0.95f`) still leaves noticeable margins.
User feedback suggests the ideal narrowest margin is ~50% of the current value.

### Proposed Fix

Change `TextWidth.WIDE` from `0.95f` to `0.98f`. This leaves ~1% margin on
each side (plus the 8px CSS body padding and the 13px template padding),
resulting in content that feels nearly edge-to-edge without going truly
full-bleed. Going to `1.0f` would work too, but 0.98f avoids any clipping
edge cases.

### Files

* `app/src/main/java/com/mydeck/app/domain/model/TypographySettings.kt`

---

## 6. Theme Switch Causes Full Content Repaint/Reflow

### Problem

Changing the reading theme (Paper/Sepia/Dark/Black) from the typography sheet
causes a visible flash and content reflow as the entire article reloads.

### Root Cause

Each theme is a separate HTML file (`html_template_light.html`, etc.). When the
theme changes:

1. `readerAppearanceSelection` emits → `template` flow produces a new
   `Template` object.
2. `content` is recomputed in `remember(... uiState.template ...)` →
   `getContent()` produces a brand-new HTML string.
3. `update` block detects `it.tag != content.value` → calls
   `loadDataWithBaseURL()` → full WebView document reload.

### Analysis

Diffing the four templates shows they are structurally identical — they differ
**only** in ~15 hardcoded color values (body text, background, blockquote bg,
input borders, table borders, annotation highlight opacities). The layout,
fonts, and all structural CSS are shared.

### Proposed Fix

Refactor the templates so all theme-varying colors are expressed as CSS custom
properties (variables) on `:root`. This is already partially done for accent
colors (`--accent-color`, etc.). Extend the pattern to cover all theme-varying
properties:

```css
:root {
  --body-color: #4a4a4a;
  --body-bg: #f9f9f9;
  --surface-color: #f1f1f1;
  --border-color: #f1f1f1;
  --input-color: #4a4a4a;
  /* ... plus existing accent vars ... */
}
```

Then add a new `WebViewThemeBridge.applyTheme(appearance)` JS function
(analogous to `WebViewTypographyBridge.applyTypography`) that updates these
CSS variables at runtime:

```javascript
document.documentElement.style.setProperty('--body-bg', '#222222');
// ... etc.
```

With this approach:
* A single template file suffices (or one light + one dark for system-theme
  transitions, each using the same CSS variable names).
* Theme switches execute a small JS snippet instead of `loadDataWithBaseURL()`.
* No document reload, no reflow, no flash.
* The `content` remember key no longer includes `uiState.template`, so the
  WebView is only reloaded when the actual article content changes.

### Migration Path

1. Consolidate the four templates into one (or two for system-theme).
2. Move color definitions to CSS custom properties.
3. Create `WebViewThemeBridge` with `applyTheme()`.
4. Add a `LaunchedEffect` keyed on appearance that calls the new bridge.
5. Remove `template` from the `content` remember keys.

### Files

* `app/src/main/assets/html_template_*.html` (consolidate)
* New: `app/src/main/java/com/mydeck/app/ui/detail/WebViewThemeBridge.kt`
* `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`
* `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
* `app/src/main/java/com/mydeck/app/ui/theme/ReaderThemeCss.kt`

---

## Priority Order

| # | Item | Severity | Effort |
|---|------|----------|--------|
| 1 | Rapid successive deletes | Bug — data loss | Medium |
| 2 | Text size shifts with margins | Bug — visual | Low |
| 3 | Layout glitch (issue #11) | Bug — visual | Low (if #2 fixes it) |
| 5 | Narrower minimum margin | Enhancement | Trivial |
| 4 | Video fullscreen for articles | Enhancement | Medium |
| 6 | Theme switch reflow | Enhancement | Medium-High |

Items 2, 3, and 5 are quick wins. Item 1 is the most impactful bug.
Item 6 is the most involved but has the biggest UX payoff for readers who
switch themes frequently.
