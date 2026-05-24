# Spec: Native Compose Reader Action Footer

## Status

Proposal — not yet scheduled for implementation.

## Context

### Current state (v0.13.1)

The reader WebView renders favorite and archive buttons at the bottom of the
article HTML template. These are HTML elements, styled in the template's CSS,
and wired to native code through `WebViewActionsBridge`:

- `WebViewActionsBridge.toggleFavorite()` — calls into `BookmarkDetailViewModel`
  to flip the bookmark's favorite state.
- `WebViewActionsBridge.toggleArchive()` — same for archive state.

Bridge registration happens in
`app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`.
The bridge is installed only on the reader WebView (the `WebViewAssetLoader`-backed
instance loading from `https://offline.mydeck.local/...`), not on the
original-page WebView.

The footer buttons live inside the scrolling article content, so they scroll out
of view with the rest of the page. Button state (favorited / unfavorited,
archived / unarchived) is kept in sync via `setFavoriteStateScript` /
`setArchiveStateScript` in `WebViewActionsInjector`, which are evaluated against
the WebView whenever the underlying state changes outside the page.

### Why this needs to change

**Defense-in-depth weakness.** The reader WebView loads server-extracted HTML.
Readeck sanitizes that HTML server-side, but the bridge exposes mutating account
operations to any JavaScript that runs in the reader context. If the sanitizer
ever misses a script tag, regresses in a future Readeck release, or behaves
differently for a new extraction path, the consequence is that page JS can flip
favorite/archive state on the current bookmark. The blast radius is small (no
token access, no arbitrary code, two bounded toggles) but the dependency is
"Readeck's sanitizer never has a bug." That is an unnecessary single point of
failure when the actions do not need to be JS-callable at all.

The asymmetry matters: MyDeck releases on a 2–3 week cadence; Readeck releases
on a slower cadence. A sanitizer regression in a Readeck point release would
expose MyDeck users until the next Readeck release fixes it, and the MyDeck
side has no recourse beyond removing the bridge — which is what this spec
proposes doing pre-emptively.

**UX limitations.** The buttons scroll with content rather than remaining
visible, so they are not always discoverable when the user has read past them.
They do not get Material ripple, cannot easily honor the reader theme palette
across sepia/dark/light reliably, and require parallel JS state updates whenever
favorite/archive state changes elsewhere in the app.

**Architectural cost.** The bridge, the injectors, the JS state-sync calls, and
the HTML/CSS for the footer all exist solely to make this one piece of UI work
inside the WebView. A native Compose footer eliminates that entire stack.

### Why a previous attempt failed

An earlier attempt to render the footer as a Compose overlay did not work,
primarily because the WebView and Compose were competing for scroll ownership
and the footer-show signal was being computed outside the WebView's scroll
context. Since then, the top-app-bar scroll behavior has been solved using the
same overlay shape with the WebView remaining the scroller. The same
architectural pattern applies to a footer.

## Goals

- Remove `WebViewActionsBridge` entirely.
- Render favorite and archive as native Compose buttons.
- The footer is hidden by default and animates in when the user has scrolled to
  the end of the reader content, for all three bookmark types
  (article / picture / video).
- The reader content never sits underneath the footer when it is visible.
- Reader theme (light / sepia / dark) is honored without JS state injection.
- The footer's enter/exit animation matches the top-app-bar animation already
  in place, so the two overlays feel like one system.

## Non-goals

- Changing how favorite/archive state is persisted, synced, or queued offline.
- Changing the top-app-bar pattern. This spec mirrors it; it does not modify it.
- Adding new actions to the footer beyond favorite and archive. Other actions,
  if any, are a follow-up.
- Touching the original-page WebView, which has no bridge today.
- Changing the existing read-progress mechanics. `WebViewScrollBridge` remains
  responsible for intermediate progress, article completion, resume position,
  and the in-list progress indicator.

## Design

### Signal: IntersectionObserver sentinel, not scroll percentage

Add a visually inert 1px sentinel element at the end of the article body in the
reader HTML template:

```html
<div id="mydeck-end-sentinel" aria-hidden="true"></div>
```

The sentinel is styled with a stable, minimal size rather than zero height:

```css
#mydeck-end-sentinel {
  width: 1px;
  height: 1px;
  pointer-events: none;
}
```

Attach an `IntersectionObserver` in the reader JS that watches this sentinel and
reports state changes through a new low-risk event bridge:

```kotlin
class WebViewEndSentinelBridge(
    private val onEndVisibleChanged: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun onEndVisible(visible: Boolean) {
        onEndVisibleChanged(visible)
    }
    companion object { const val BRIDGE_NAME = "MyDeckEndSentinel" }
}
```

Rationale for sentinel over scroll percentage:

- **Reliable across content types.** Picture and video reader views have CSS
  padding/margins and iframe-based embeds that distort percentage math. A
  sentinel reports when the end of content is in viewport, regardless of layout.
- **Naturally bidirectional.** Hides when the user scrolls back up.
- **Cheap.** One observer per page, no `requestAnimationFrame` polling.
- **No bookmark/server mutation.** The callback only updates footer visibility
  state in Compose. It cannot favorite, archive, delete, edit, or otherwise
  mutate bookmark data. The bridge-removal goal applies to mutating bridges;
  event-report bridges are kept.

### Read-progress behavior remains unchanged

Read-state behavior today, confirmed against `BookmarkDetailViewModel.kt`:

- **Picture and Video bookmarks** are auto-marked to `readProgress = 100`
  unconditionally on open, in `initializeBookmark()` at lines 250–257. The
  sentinel does not change this; for these types it drives footer visibility
  only.
- **Article bookmarks** track read progress through
  `onScrollProgressChanged(progress: Int)` (line 561), fed by
  `WebViewScrollBridge`. The article is treated as completed and the tracker
  is locked when `progress.coerceIn(0, 100) >= 100` (line 570), at which point
  `isReadLocked = true` and further scroll updates are ignored. On open,
  `isReadLocked` is initialized to `bookmark.isRead()` (line 314).

After this spec:

- `WebViewScrollBridge` and `onScrollProgressChanged` stay exactly where they
  are. Intermediate progress (`0..99`) and completion at `>= 100` continue to
  feed resume-from-position, auto-mark-as-read, and the in-list progress
  indicator.
- The sentinel does not call `updateReadProgress`, does not set
  `currentScrollProgress`, and does not change `isReadLocked`.
- For all reader types, the sentinel is wired only to footer visibility state.
  If a sanitizer bypass ever allowed page JavaScript to call the sentinel bridge,
  the worst effect would be showing or hiding the native footer, not mutating
  account data or queued server actions.

### Bottom clearance

Mirror the existing top-clearance pattern (`readerTopClearanceCssPx`). Inject a
CSS custom property from Kotlin and apply it as `padding-bottom` on the body:

```css
body {
  padding-top: var(--mydeck-top-clearance-px, 0px);
  padding-bottom: var(--mydeck-bottom-clearance-px, 0px);
}
```

`--mydeck-bottom-clearance-px` is computed from the Compose footer's target
height plus the bottom safe-area inset, set once when the footer's target
height is known (not animated). Setting it to the target value up front —
rather than recomputing during the show/hide animation — prevents content
reflow during the animation.

When the footer is suppressed for a given layout or bookmark type (see edge
cases), the clearance variable stays at 0 so the article uses the full
viewport.

### Compose layer

```
Box(modifier = Modifier.fillMaxSize()) {
    ReaderWebView(...)  // unchanged
    AnimatedVisibility(
        visible = uiState.endVisible && uiState.showFooter,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        ReaderActionFooter(
            isFavorite = uiState.bookmark.isFavorite,
            isArchived = uiState.bookmark.isArchived,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleArchive = viewModel::toggleArchive,
            palette = readerThemePalette
        )
    }
}
```

`ReaderActionFooter` is a `Row` with two `IconButton`s side-by-side, centered,
surface coloured from the reader palette (not the global Material scheme) so
it harmonises with sepia and reader-dark themes. Material ripple is automatic.

`uiState.endVisible` is fed from the new bridge. `uiState.showFooter` gates on
the layout/type conditions in the edge-cases section below.

## Sentinel placement per bookmark type

Each reader type composes its content differently in
`BookmarkDetailViewModel.Bookmark.getContent()` (lines 1688–1766). The
sentinel must render at the visual end of each type's content, not at the end
of the DOM. The recommended placement, expressed against the existing
composition:

- **Article** (`Type.ARTICLE`, line 1760): the body is
  `<div id="rd-article-content">$articleContent</div>` followed by the current
  `footerHtml`. Sentinel goes immediately after the closing
  `</div>` of `rd-article-content` and replaces the footer.
- **Picture** (`Type.PHOTO`, line 1715): the body is an image element plus an
  optional text caption. Sentinel goes after the caption when present, or
  immediately after the `<img>` when not. Replaces the footer.
- **Video** (`Type.VIDEO`, line 1737): the body is the embed (in
  `<div class="video-embed">`) followed by
  `<div id="rd-article-content">$textPart</div>` (transcript or description).
  Sentinel goes after the closing `</div>` of `rd-article-content`. Replaces
  the footer.

The cleanest implementation is to replace `buildReaderFooterHtml(...)` with
`buildReaderEndSentinelHtml()` returning
`<div id="mydeck-end-sentinel" aria-hidden="true"></div>`, and remove the
favorite/archive label parameters from `getContent()`'s signature entirely.
The three type branches keep the same composition shape; only the trailing
element changes.

## Edge cases

1. **Wide layouts (tablet landscape).** Bookmark details may already be
   persistent on the side with favorite/archive controls available there.
   Verify against the current AppShell layout. If duplicate controls would
   result, suppress the floating footer in those layouts
   (`showFooter = false`).
2. **Original-page WebView.** Do not render the footer there. The
   favorite/archive actions still apply to the underlying bookmark, but mixing
   them with non-extracted content is confusing UX, and the original-page
   WebView has no sentinel injected anyway.
3. **Annotation / selection toolbars.** If text-selection or
   annotation-creation surfaces appear at the bottom of the reader, the footer
   must yield. Gate `showFooter` on "no active selection toolbar" and "no
   active annotation editor."
4. **In-article search bar.** Currently at the top, so no collision. Document
   the assumption and revisit if search ever moves to the bottom.
5. **Embed-heavy pages with delayed layout.** Some video/embed pages finish
   layout slowly. The sentinel's first `IntersectionObserver` callback may
   fire late. Acceptable — the footer simply does not appear until the page
   is genuinely scrolled to end. Better than a percentage-based heuristic
   firing too early on slow-loading pages.
6. **Picture and Video bookmarks where content fits without scrolling.** The
   sentinel is visible at load time, so the footer appears immediately. That
   is the right UX — these bookmark types are already auto-marked as read on
   open, so showing the footer immediately matches the user's mental model
   ("I can see the whole thing; here are my actions"). Debounce by ~150 ms so
   the footer does not flash during initial page paint.
7. **Very short articles where the sentinel is visible immediately on load.**
   Same as above — footer appears right away. Same ~150 ms debounce.
8. **Theme changes mid-read.** The CSS clearance variable does not need to
   change with theme. The footer rerenders on palette change automatically.
   No special handling needed.

## Implementation outline

1. Replace `buildReaderFooterHtml(...)` in
   `BookmarkDetailViewModel.Bookmark` with `buildReaderEndSentinelHtml()`
   returning `<div id="mydeck-end-sentinel" aria-hidden="true"></div>`. Drop
   the favorite/archive label parameters from `getContent()`.
2. Update each of the three type branches in `getContent()` (Article,
   Picture, Video) so the sentinel is appended at the correct position per
   the placement section above.
3. Add reader JS: `IntersectionObserver` wiring on `#mydeck-end-sentinel`,
   calls `MyDeckEndSentinel.onEndVisible(visible)`.
4. Add `WebViewEndSentinelBridge` Kotlin class. Register on the reader WebView
   in `BookmarkDetailWebViews.kt`. Remove the `WebViewActionsBridge`
   registration.
5. Hoist `endVisible: Boolean` into `BookmarkDetailViewModel.UiState`. Add a
   `showFooter: Boolean` derived from `endVisible` AND the layout/type/
   selection gates described in Edge Cases.
6. Do not wire the sentinel into read-progress state. Article completion remains
   driven by `WebViewScrollBridge` and `onScrollProgressChanged`; Picture and
   Video remain unchanged and still auto-mark on open.
7. Add `--mydeck-bottom-clearance-px` to the reader CSS in the four template
   files and its injection point in Kotlin (mirror `readerTopClearanceCssPx`).
8. Build `ReaderActionFooter` composable — a `Row` of two `IconButton`s,
   surfaces from the reader palette.
9. Wrap the existing WebView in a Compose `Box` and add `AnimatedVisibility`
   for the footer overlay, using the same enter/exit animation values as the
   top app bar.
10. Remove the in-template footer HTML and CSS from the four
    `html_template_*.html` files.
11. Delete `WebViewActionsBridge.kt` and `WebViewActionsInjector.kt` (or trim
    if any non-footer code uses parts of it; on inspection, the icon
    constants `FAV_ICON_FILLED` / `ARC_ICON_FILLED` etc. are referenced only
    from `buildReaderFooterHtml`, so the whole file should be deletable).
12. Update `SECURITY.md` reader-bridge enumeration to reflect the new set
    (drop Actions, add EndSentinel; the count stays manageable).

## Code to remove

- `app/src/main/java/com/mydeck/app/ui/detail/WebViewActionsBridge.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/WebViewActionsInjector.kt`
  (the icon constants and state-injection scripts in this file are referenced
  only from `buildReaderFooterHtml` and the bridge wiring; remove with the
  rest).
- `buildReaderFooterHtml(...)` in `BookmarkDetailViewModel.Bookmark`, and the
  `favoriteLabel` / `unfavoriteLabel` / `archiveLabel` / `unarchiveLabel`
  parameters from `getContent()`.
- The footer-button CSS rules in the four `html_template_*.html` files under
  `app/src/main/assets/`.
- The bridge registration block for `WebViewActionsBridge` in
  `BookmarkDetailWebViews.kt`.
- The JS state-sync `evaluateJavascript` calls that fire when favorite/archive
  state changes outside the WebView (the
  `WebViewActionsInjector.setFavoriteStateScript` / `setArchiveStateScript`
  call sites).

## Test plan

- Unit tests for `endVisible` and `showFooter` state transitions in
  `BookmarkDetailViewModel`.
- Unit test confirming `endVisible` changes do not alter `currentScrollProgress`,
  `isReadLocked`, or call read-progress persistence.
- Unit test confirming the existing Article completion path still runs through
  `onScrollProgressChanged(progress >= 100)`.
- Unit test confirming the Picture/Video auto-mark-on-open path at lines
  250–257 is unchanged.
- Robolectric test for `ReaderActionFooter` rendering with each palette
  variant.
- Manual test matrix:
  - All three bookmark types: article / picture / video
  - Phone portrait / phone landscape / tablet portrait / tablet landscape
  - Light / dark / sepia themes
  - Short article (sentinel visible at load) vs. long article
  - Scroll to end, then scroll back up — footer visibility follows the sentinel
    for every reader type
  - Toggle favorite/archive — state persists, no JS reload of WebView
  - Offline state — toggle queues to pending actions, footer reflects pending
    state via the existing UiState path
  - Original-page WebView — footer never shown
  - Wide layout — footer suppressed if duplicate controls present in side
    panel
  - Video with iframe embed and long transcript — sentinel after the
    transcript, not after the embed
  - Picture with no caption — sentinel directly after the image
  - Resume from mid-article position (saved `readProgress` < 100) — footer
    hidden until user scrolls to end; intermediate scroll updates still
    flow through `WebViewScrollBridge`

## Out of scope

- Adding more actions to the footer (share, delete, add label). v1 is
  favorite + archive only.
- Changing the top app bar.
- Changing the existing Picture/Video auto-mark-on-open behavior, Article
  intermediate-progress tracking, or Article completion tracking.

## Open questions

1. **Should the footer auto-hide after N seconds of no interaction?** Probably
   not, but worth a UX call. Default recommendation: no auto-hide; visibility
   is purely driven by the sentinel and the gate conditions in Edge Cases.
