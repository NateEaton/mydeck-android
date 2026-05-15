# Reader Top Bar — Intended Scroll Behavior

Hand-off spec describing how the bookmark detail / reader view top app bar is **supposed** to behave. Captured after a circular attempt at fixing slow-scroll content "ghosting" so the next pass can start from intent rather than from the failed attempts.

## In-scope screens

- `BookmarkDetailScreen` (reader for **article**, **video**, **picture** bookmark types)
- Both reader and original/embedded content modes

Out of scope: highlights screen, list screen, fullscreen reader mode (it has its own state machine).

## Required behaviors

1. **Title and description must be fully visible at scrollY=0 with the bar shown.** When a user opens any bookmark with read progress 0, the article header (`<h1 class="mydeck-title">`, `<p class="mydeck-description">`) sits below the top bar — not partially under it, not below an empty 80–130 dp gap.
2. **Bar hides on scroll-down, reveals on scroll-up.** Tracks finger naturally (current rate feels right per Codex's implementation).
3. **No content ghosting at any scroll velocity.** The bar transition must not produce a visible per-frame phase shift between the WebView's content position and the bar's position.
4. **Bar auto-reveals when user reaches near the bottom.** Existing threshold ≈ 95% scroll progress.
5. **Bar snaps to fully visible when scrolled to top.** `scrollY <= 0` ⇒ `heightOffset = 0`.
6. **Hiding the bar must give the user more reading area.** A solution that always reserves the bar's slot (so hiding doesn't reclaim pixels) is not acceptable.
7. **Non-scrollable content (Picture, Video) must still let the user see the title.** Today there is no way to access the title when the bar is showing and the article body is empty / shorter than the viewport — this is a *pre-existing* bug, but any solution must fix it, not paper over it.
8. **Tap-on-bar scrolls to top.** Already wired; must keep working for all content types including non-scrollable ones (currently scrolls a non-scrollable WebView to a no-op position).

## Why the last two attempts failed

### Attempt A — `Scaffold` + `enterAlwaysScrollBehavior` (the pre-existing implementation)

Bar lives in the Scaffold's `topBar` slot. As `heightOffset` shrinks, the slot's measured height shrinks, so the Scaffold remeasures and the WebView container's bounds shrink/grow by the same amount. The WebView resize races against the WebView's own scroll on RenderThread → at sub-pixel-per-frame scroll velocities the user sees content "ghost" / double-image.

Tried mitigating with: pixel snapping of `heightOffset`, a sub-2-px scroll dead zone. Neither helped because both still resize the WebView container per frame.

### Attempt B — Overlay bar, WebView fills screen, CSS `padding-top` for clearance

`Scaffold` removed; the bar is overlaid in a `Box` on top of a full-screen WebView. WebView bounds become constant, so the cross-thread race disappears.

To prevent the bar from covering the title, the HTML body got a CSS `padding-top` clear-zone (~130 dp on Pixel 9, set dynamically via `evaluateJavascript` from the measured bar height).

Why this broke:

1. **Non-scrollable content (Picture/Video)** has no body to scroll past, so the CSS top pad **permanently** consumes the area where the title would be — the user can never see the title even by scrolling. Hiding the bar leaves an equivalent empty band at the top because we now require the WebView to expose its first 130 dp via scroll.
2. The CSS-padding approach is fundamentally a "title is part of the scrollable body" model. Picture/Video views don't fit that model.
3. The WebView didn't reliably re-paint after `body.style.padding-top` was set — title only appeared after a slow scroll triggered a layout pass. Forcing reflow (`offsetHeight`) and `postInvalidate()` helped but did not fully fix it across the cold-load path.
4. With the bar overlaid and the title behind the CSS clear-zone, the "tap bar to scroll to top" was broken: the bar's tap target *is* in the area the user wants to reveal.

## Design questions for the next pass

These are the decisions a successor solution has to make. Listed not because they have a known answer, but because every workable design picks among them.

1. **Where does the title/description live — inside the WebView (HTML) or outside (Compose)?**
   - Inside HTML (current): one render surface, but title is bound to the article's scroll context. Bad fit for Picture/Video.
   - Outside (a Compose header above the WebView): clean separation, title always reachable, but introduces a second scrollable surface to coordinate with the bar.
2. **Is the bar opaque or scrim/translucent?**
   - Opaque (current): hard requirement that content not be hidden under it.
   - Translucent with content extending under: title is partially visible through the bar at scrollY=0 even without a clear-zone; standard iOS-style pattern.
3. **For Picture/Video views (no scrollable body), does the bar auto-hide on display?**
   - Show bar briefly on entry, then auto-hide; user taps to bring it back.
   - Or: bar is always visible but content is overlaid such that the bar doesn't cover key elements.
4. **What is the source of truth for the bar's "should be visible" state?**
   - WebView scroll deltas (current — fragile because of WebView ↔ Compose timing).
   - User gesture velocity captured in Compose ahead of WebView (cleaner).
   - Simple binary toggle on tap (loses the "tracks finger" feel users liked).
5. **Should the bar resize the available content area, or always overlay it?**
   - Resize: ghosting risk returns.
   - Always overlay: requires solving the title-visibility problem some other way.

## Suggested starting point for the next session

Move the header (title + description + site name) **out of the HTML** and into a Compose-rendered header above the WebView. Then:

- The Compose header is a real Compose node that participates in nested scroll naturally.
- The WebView only carries article body (or is hidden entirely for Picture/Video views).
- The top bar can overlay the Compose header without the WebView-resize race because the Compose header *does* resize (it is a Compose node, all on the UI thread).
- Picture/Video views become trivial: just render the image/embed below the Compose header, no WebView clear-zone trick required.

This is a larger change (it splits the article's "metadata header" away from the reader template) but it removes the WebView coordinate space from the bar visibility problem entirely.
