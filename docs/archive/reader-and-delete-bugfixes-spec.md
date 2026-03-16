# Spec: Reader & Delete Bugfixes (v0.11.1, revised)

## Summary

This spec covers six scoped reader/list improvements identified during review:

1. rapid successive deletes losing identity
2. text-size shifts when reader width changes
3. follow-up investigation for the reproducible layout glitch
4. video fullscreen overlay control polish only
5. narrower minimum margin for the Wide width preset
6. theme-switch flash/reflow reduction

The original draft has been revised to match the actual Compose Material 3
snackbar behavior and the current WebView fullscreen implementation.

---

## 1. Rapid Successive Deletes — Preserve Per-Item Identity

### Problem

`BookmarkListViewModel` currently stores a single pending delete ID. If the
user stages delete for Card A and then Card B before Card A's snackbar flow has
resolved, Card B overwrites the shared pending state. That can cause the wrong
card to remain greyed out and can cause later snackbar results to confirm or
cancel the wrong item.

### Root Cause

Two implementation details combine to create the bug:

* `BookmarkListViewModel` tracks only one pending delete via
  `MutableStateFlow<String?>`.
* `BookmarkListScreen.stageDeleteWithSnackbar()` launches one snackbar coroutine
  per delete, but the snackbar result handlers call parameterless
  `onConfirmDeleteBookmark()` / `onCancelDeleteBookmark()`, so the result is
  applied to "whatever ID is currently pending" instead of the ID that snackbar
  was created for.

Important nuance: `SnackbarHostState.showSnackbar()` in Compose Material 3
queues snackbar requests. It does not replace the currently shown snackbar.
The fix should therefore preserve snackbar queueing and repair the identity
tracking, rather than dismissing the current snackbar to emulate replacement.

### Proposed Fix

Make staged deletion explicitly item-scoped.

1. Replace the single pending delete state with an ordered collection, e.g.
   `MutableStateFlow<List<String>>` or equivalent.
2. Expose a collection that the UI can use to grey out every pending card while
   its snackbar is queued or visible.
3. Change the delete callbacks to take an explicit bookmark ID:
   `onConfirmDeleteBookmark(bookmarkId: String)` and
   `onCancelDeleteBookmark(bookmarkId: String)`.
4. In `stageDeleteWithSnackbar(bookmarkId)`, stage that ID, then await
   `showSnackbar(...)`, then confirm or cancel that same ID based on the
   returned `SnackbarResult`.
5. Do not dismiss the currently visible snackbar when another delete is staged.
   Let `SnackbarHostState` queue naturally.

This keeps the existing user model of "delete now, undo from snackbar" while
making the bookkeeping safe for multiple quick deletes.

### Notes

* The UI may still expose only one visible snackbar at a time; the change is
  about correctly tracking multiple pending items behind that UI.
* Existing explicit dismiss paths remain valid. If the current snackbar is
  dismissed because the user navigates away or performs another action, only the
  item associated with that snackbar should be confirmed.

### Files

* `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`
* `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt`

---

## 2. Text Size Changes When Switching Width Setting

### Problem

Changing the reader width from Wide to Medium or Narrow changes the apparent
body text size. The width control is acting like a hidden font-size control.

### Root Cause

The reader HTML templates still contain Sakura-derived responsive font-size
media queries:

```css
@media (max-width: 684px) { body { font-size: 1.75rem; } }
@media (max-width: 382px) { body { font-size: 1.65rem; } }
```

The reader width presets change the WebView's effective content width. When the
layout crosses one of those breakpoints, the template changes the base CSS font
size. `WebView.settings.textZoom` is then applied on top of that, so the visual
change is compounded.

### Proposed Fix

Remove the breakpoint-based body font-size overrides from all four reader HTML
templates. Font size should be controlled by `textZoom`, not by width breakpoints.

### Required Collateral Change

Update the template unit test so it no longer expects the removed responsive
font-size blocks and instead asserts that those breakpoints are absent.

### Files

* `app/src/main/assets/html_template_light.html`
* `app/src/main/assets/html_template_dark.html`
* `app/src/main/assets/html_template_sepia.html`
* `app/src/main/assets/html_template_black.html`
* `app/src/test/java/com/mydeck/app/ui/detail/ReaderHtmlTemplateTypographyTest.kt`

---

## 3. Reproducible Layout/Rendering Glitch (Issue #11)

### Problem

Some articles can break visually after typography changes. The issue appears to
depend on a specific combination of settings rather than on a single control.

### Current Conclusion

The most likely first-order cause is item 2: the hidden breakpoint-driven font
size change can interact badly with article CSS that relies on relative units.
That is the first fix to apply.

If the issue remains after item 2, the next place to investigate is the
typography application sequence in `BookmarkDetailWebViews`:

* typography is applied in a `LaunchedEffect` keyed on typography settings
* typography is also applied again from page lifecycle callbacks
* the `LaunchedEffect` currently waits 150 ms before injecting JS

That combination may be causing a timing issue inside the document.

### Proposed Fix

This item stays as a validation checkpoint, not a committed code change beyond
item 2.

1. Apply item 2 and re-test the known failing article(s).
2. If the glitch is still reproducible, open a focused follow-up to inspect the
   typography injection order.
3. Candidate follow-ups are:
   * consolidating duplicate typography injection paths
   * reducing or removing the 150 ms delay after confirming it is safe
4. Do not add `requestLayout()` as part of this spec. Current evidence points
   to DOM/CSS timing rather than Android view measurement.

### Files

* Same as item 2 for the immediate fix
* Potential follow-up: `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`

---

## 4. Video Fullscreen Overlay Control Polish

### Scope

Only the fullscreen overlay control placement/styling issue is in scope for
this spec revision. The following ideas are explicitly deferred:

* app-controlled fullscreen entry
* iframe sandbox/navigation changes
* sensor-based auto-rotation changes

### Current State

Provider-driven fullscreen already works through the existing
`WebChromeClient.onShowCustomView` path. The remaining UX problem in scope is
that MyDeck's own close/rotate controls overlap provider controls when the
fullscreen overlay is visible.

### Proposed Fix

Redesign the `VideoFullscreenOverlay` controls only.

1. Move the close and rotate controls away from the top corners and into a
   vertically centered overlay row so they do not collide with provider chrome.
2. Use larger pill-shaped semi-transparent or outlined containers instead of
   the current small circular buttons.
3. Keep the existing 3-second auto-hide behavior.
4. Keep the existing close action, rotate action, and fullscreen plumbing.
5. Do not change WebView embed handling or fullscreen entry behavior in this
   item.

### Files

* `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`

---

## 5. Narrower Minimum Margin (Wider Layout)

### Problem

The current `WIDE` width preset still leaves more side margin than users want.

### Proposed Fix

Change `TextWidth.WIDE` from `0.95f` to `0.98f`.

This remains safely short of true edge-to-edge rendering because the article
still has runtime body padding and container spacing. The goal is to make the
widest preset feel clearly wider without introducing clipping risk.

### Required Collateral Change

Update the English reader guide so the documented Wide width percentage matches
the new setting.

### Files

* `app/src/main/java/com/mydeck/app/domain/model/TypographySettings.kt`
* `app/src/main/assets/guide/en/reading.md`

---

## 6. Theme Switch Causes Full Content Repaint/Reflow

### Problem

Changing the reading theme (Paper, Sepia, Dark, Black) currently causes a full
HTML reload in the reader WebView, producing a visible flash and content reflow.

### Root Cause

Appearance currently changes the template content itself. When appearance
changes:

1. the ViewModel emits a different template
2. the rendered HTML string changes
3. the `AndroidView` update path sees different content and calls
   `loadDataWithBaseURL()`

That forces a full document reload even though the article body itself has not
changed.

### Proposed Fix

Refactor reader theming so appearance changes update CSS variables in the
existing document instead of replacing the whole document.

1. Move all appearance-specific colors, not just accent colors, into CSS custom
   properties.
2. Introduce a theme-neutral reader template whose structure does not vary by
   appearance.
3. Add a `WebViewThemeBridge` that updates those CSS variables in-place when
   the appearance changes.
4. In `BookmarkDetailWebViews`, treat appearance changes separately from
   content changes. Only real content changes should call `loadDataWithBaseURL()`.
5. Keep initial theme application on first load so the first render matches the
   current appearance.

Expected outcome: theme switching should avoid a full document reload and
substantially reduce flash/reflow. A minor repaint may still occur, but the
document should no longer be torn down and rebuilt on every theme change.

### Migration Path

1. Convert hardcoded theme colors in the templates to shared CSS variable names.
2. Create the runtime theme bridge.
3. Separate content reload triggers from appearance update triggers in
   `BookmarkDetailWebViews`.
4. After behavior is verified, optionally consolidate any remaining duplicated
   template assets.

### Files

* `app/src/main/assets/html_template_*.html`
* New: `app/src/main/java/com/mydeck/app/ui/detail/WebViewThemeBridge.kt`
* `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`
* `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
* `app/src/main/java/com/mydeck/app/ui/theme/ReaderThemeCss.kt`

---

## Priority Order

| # | Item | Severity | Effort |
|---|------|----------|--------|
| 1 | Rapid successive deletes | Bug - correctness/data loss risk | Medium |
| 2 | Text size shifts with width | Bug - visual | Low |
| 3 | Layout glitch (issue #11) | Validation after item 2 | Low |
| 5 | Narrower minimum margin | Enhancement | Trivial |
| 4 | Video overlay control polish | Enhancement | Low |
| 6 | Theme switch reflow | Enhancement | Medium-High |

Items 1 and 2 are the most important correctness fixes. Item 3 should be
re-tested immediately after item 2. Item 6 is still worthwhile, but it is the
largest and riskiest change in the set.

---

## Verification

Because these changes touch UI/assets, run the standard aggregate debug tasks
serially after implementation:

* `./gradlew :app:assembleDebugAll`
* `./gradlew :app:testDebugUnitTestAll`
* `./gradlew :app:lintDebugAll`
