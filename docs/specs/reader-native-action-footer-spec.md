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
- The footer is hidden by default and animates in only when the user has
  scrolled to the end of the article content.
- The article content never sits underneath the footer when it is visible.
- Reader theme (light / sepia / dark) is honored without JS state injection.

## Non-goals

- Changing how favorite/archive state is persisted, synced, or queued offline.
- Changing the top-app-bar pattern. This spec mirrors it; it does not modify it.
- Adding new actions to the footer beyond favorite and archive. Other actions,
  if any, are a follow-up.
- Touching the original-page WebView, which has no bridge today.

## Design

### Signal: IntersectionObserver sentinel, not scroll percentage

Add a zero-height sentinel element at the end of the article body in the reader
HTML template:

```html
<div id="mydeck-end-sentinel" aria-hidden="true"></div>
```

Attach an `IntersectionObserver` in the reader JS that watches this sentinel and
reports state changes through a new read-only bridge:

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
- **Read-only callback.** No state mutation; same shape as `WebViewScrollBridge`
  today. The bridge-removal goal applies to mutating bridges; event-report
  bridges are kept.

### Shared signal with auto-mark-as-read

Auto-mark-as-read at end-of-content already exists. The end-sentinel-visible
signal should also feed that logic, so footer-visibility and read-completion
are derived from the same source of truth and cannot drift. Practical
implication: the existing scroll-bridge logic that decides "the user has
finished the article" should either be replaced by, or call into, the same
end-visible signal.

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

## Edge cases

1. **Bookmark type variance.** Picture and video reader templates differ from
   article. The sentinel must render at the visual end of each template's
   content (not the end of the DOM, which for video may be the embed iframe).
   For picture bookmarks the sentinel goes after the image caption; for video,
   after the embed. Alternatively, scope the footer to article-type bookmarks
   for v1 and let picture/video continue surfacing favorite/archive via the
   existing metadata view. **Recommendation: scope to article in v1; revisit
   picture/video in a follow-up.**
2. **Wide layouts (tablet landscape).** Bookmark details may already be
   persistent on the side with favorite/archive controls available there.
   Verify against the current AppShell layout. If duplicate controls would
   result, suppress the floating footer in those layouts
   (`showFooter = false`).
3. **Original-page WebView.** Do not render the footer there. The
   favorite/archive actions still apply to the underlying bookmark, but mixing
   them with non-extracted content is confusing UX, and the original-page
   WebView has no sentinel injected anyway.
4. **Annotation / selection toolbars.** If text-selection or
   annotation-creation surfaces appear at the bottom of the reader, the footer
   must yield. Gate `showFooter` on "no active selection toolbar" and "no
   active annotation editor."
5. **In-article search bar.** Currently at the top, so no collision. Document
   the assumption and revisit if search ever moves to the bottom.
6. **Embed-heavy pages with delayed layout.** Some video/embed pages finish
   layout slowly. The sentinel's first `IntersectionObserver` callback may
   fire late. Acceptable — the footer simply does not appear until the page
   is genuinely scrolled to end. Better than a percentage-based heuristic
   firing too early on slow-loading pages.
7. **Very short articles where the sentinel is visible immediately on load.**
   The footer appears right away, which is fine — the user has effectively
   read the whole article. Optionally debounce by ~150 ms so the footer does
   not flash during initial page paint.
8. **Theme changes mid-read.** The CSS clearance variable does not need to
   change with theme. The footer rerenders on palette change automatically.
   No special handling needed.

## Implementation outline

1. Add sentinel element to article (and picture/video, if scoping wider)
   reader HTML templates.
2. Add reader JS: `IntersectionObserver` wiring, calls
   `MyDeckEndSentinel.onEndVisible(visible)`.
3. Add `WebViewEndSentinelBridge` Kotlin class. Register on the reader WebView
   in `BookmarkDetailWebViews.kt`.
4. Hoist `endVisible: Boolean` into `BookmarkDetailViewModel.UiState`.
5. Add `--mydeck-bottom-clearance-px` to the article CSS and its injection
   point in Kotlin (mirror `readerTopClearanceCssPx`).
6. Build `ReaderActionFooter` composable.
7. Wrap the existing WebView in the Compose `Box` + `AnimatedVisibility`
   overlay.
8. Wire auto-mark-as-read to consume `endVisible` (or share the signal).
   Remove the old percentage-based trigger if no longer needed.
9. Remove the in-template footer HTML, CSS, and the bridge wiring.
10. Delete `WebViewActionsBridge.kt`, `WebViewActionsInjector.kt` (or trim if
    other code uses parts of it).
11. Update `SECURITY.md` reader-bridge enumeration to reflect the new set.

## Code to remove

- `app/src/main/java/com/mydeck/app/ui/detail/WebViewActionsBridge.kt`
- The action-button HTML/CSS sections of the article/picture/video reader
  templates under `app/src/main/assets/`.
- `WebViewActionsInjector.setFavoriteStateScript` and `setArchiveStateScript`
  (or the entire file, if these are its only uses).
- The bridge registration block for `WebViewActionsBridge` in
  `BookmarkDetailWebViews.kt`.
- The JS state-sync calls that fire when favorite/archive state changes
  outside the WebView.

## Test plan

- Unit tests for `endVisible` state transitions in
  `BookmarkDetailViewModel`.
- Robolectric test for `ReaderActionFooter` rendering with each palette
  variant.
- Manual test matrix:
  - Article / picture / video bookmark types
  - Phone portrait / phone landscape / tablet portrait / tablet landscape
  - Light / dark / sepia themes
  - Short article (sentinel visible at load) vs. long article
  - Scroll to end, then scroll back up — footer hides
  - Toggle favorite/archive — state persists, no JS reload of WebView
  - Offline state — toggle queues to pending actions, footer reflects pending
    state via the existing UiState path
  - Original-page WebView — footer never shown
  - Wide layout — footer suppressed if duplicate controls present in side
    panel

## Out of scope

- Adding more actions to the footer (share, delete, add label). v1 is
  favorite + archive only.
- Changing the top app bar.
- Picture/video footer support if v1 is scoped to article only.
- Auto-mark-as-read behavior changes beyond sharing the signal source.

## Open questions

1. **Article-only vs. all reader types in v1?** Scoping to article is simpler
   and lower risk. Picture/video can adopt the pattern later. Recommendation:
   article only in v1.
2. **Animation duration and easing.** Match the top-bar animation, or pick
   independently? Recommendation: match top-bar for consistency.
3. **Should the footer auto-hide after N seconds of no interaction?** Probably
   not, but worth a UX call. Default recommendation: no auto-hide; visibility
   is purely driven by the sentinel.
