# Reader Top-Bar Clearance: Measured Height + Cushion

## Goal

When the Bookmark Detail Reading view is at the top of the article and the top app bar is shown, the article title (`.mydeck-title`) must always sit fully below the bar's bottom edge on every device — including the Pixel Tablet, where the previous formula-based clearance produced a title that visually crept under the bar.

## Background

The Reading view is rendered edge-to-edge: the WebView fills the entire window and the top app bar is overlaid on top of it. To keep the article title from being hidden behind the bar, the reader HTML prepends a transparent spacer:

```html
<div class="mydeck-top-clearance" style="height:{N}px" aria-hidden="true"></div>
```

The CSS-px value `N` was derived in [BookmarkDetailScreen.kt](../../app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt) from compile-time constants:

```kotlin
TopAppBarDefaults.TopAppBarExpandedHeight + WindowInsets.statusBars.getTop(this).toDp()
```

This yielded a clearance equal to the bar's *exact* occupied height. With the typography bridge overwriting the template's `body { padding: 13px }` to `body.style.padding = '0 8px'`, there is **no** padding above the clearance div — the title element sits flush with the bar's bottom edge, relying entirely on the ~3 px of line-box half-leading inside `.mydeck-title` (`font-size: 24px`, `line-height: 1.25`) to visually clear the bar.

On Pixel 9 (density ~2.625) that ~3 px lands on-pixel and the cap-height clears the bar. On Pixel Tablet (density ~2.0, potentially different `fontScale`), sub-pixel rounding in the status-bar inset, CSS-px ↔ dp conversion, or font metrics under `textZoom` is enough to push the cap-height *behind* the bar.

## Change

Replace the formula with a **measured** bar height and add a small cushion.

### Implementation

In [BookmarkDetailScreen.kt](../../app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt) inside the reader content composable:

```kotlin
val density = LocalDensity.current
var measuredTopBarHeightPx by remember { mutableIntStateOf(0) }
val topBarClearance = with(density) {
    val baseDp = if (measuredTopBarHeightPx > 0) {
        measuredTopBarHeightPx.toDp()
    } else {
        TopAppBarDefaults.TopAppBarExpandedHeight + WindowInsets.statusBars.getTop(this).toDp()
    }
    baseDp + 4.dp
}
val readerTopClearanceCssPx = topBarClearance.value.roundToInt().coerceAtLeast(0)
```

And attach `onSizeChanged` to the `Box` wrapping `BookmarkDetailTopBar` so the measurement updates whenever the bar is laid out:

```kotlin
Box(
    modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .onSizeChanged { measuredTopBarHeightPx = it.height }
) {
    BookmarkDetailTopBar(...)
}
```

### Behavior

- **First frame**: `measuredTopBarHeightPx == 0`, so the fallback formula seeds `topBarClearance`. The Reading view renders with approximately the right clearance immediately — no zero-height flash.
- **After first layout**: `onSizeChanged` reports the actual rendered bar height in physical pixels. `topBarClearance` recomputes, and because the reader HTML composable is keyed on `readerTopClearanceCssPx` (see [BookmarkDetailWebViews.kt](../../app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt) and [BookmarkDetailViewModel.kt#buildReaderHeaderHtml](../../app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt)), the HTML is re-emitted with the corrected spacer height. In practice the two values match closely enough that this is a no-op unless the device's actual bar height differs from the formula (the Pixel Tablet case).
- **4 dp cushion**: applied unconditionally on top of either source. This absorbs sub-pixel rounding when converting between Compose dp, physical px, and CSS px, and ensures the visible glyph never touches the bar's bottom edge.
- **Reused elsewhere**: `topBarClearance` is already consumed by the Original WebView padding ([BookmarkDetailScreen.kt:1449](../../app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt#L1449)) and the determinate progress bar ([BookmarkDetailScreen.kt:1534](../../app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt#L1534)). The new value flows through to both with no further changes; both gain the same 4 dp cushion, which is visually negligible.

## Why measured + cushion (rather than just a larger formula)

| Approach | Robustness | Notes |
|---|---|---|
| Formula only | Brittle | Breaks anytime the rendered bar height diverges from `TopAppBarExpandedHeight + statusBars.top` — display cutouts, caption bars (desktop/freeform/split-screen on tablets), waterfall edges, future M3 height changes. |
| Formula + cushion | Better | Still bound to a specific inset source. A 4 dp cushion may not be enough if the discrepancy is larger than rounding. |
| `safeDrawing` + cushion | Better | Captures cutouts but still re-derives from constants. |
| **Measured + cushion (chosen)** | **Robust** | Whatever Compose actually lays the bar as, the clearance follows. Cushion absorbs the tiny font/rounding slop independent of any inset bug. |

## Non-Goals

- No CSS template changes. The four `html_template_*.html` files are untouched.
- No change to the typography bridge's `body.style.padding = '0 8px'` override.
- No change to how the bar collapses/reveals (`enterAlwaysScrollBehavior`) or the fullscreen-reader top-bar visibility logic.

## Verification

1. Open a long article in Reading view on the Pixel 9 — title clears the bar with no visible regression versus the prior behavior (clearance is 4 dp larger, well within the existing visual margin).
2. Open the same article on the Pixel Tablet — title now clears the bar.
3. Toggle the typography panel to change `textZoom` — title remains clear of the bar at all sizes (the cushion absorbs any font-metric variance).
4. Toggle between Article and Original modes — both modes' top padding (`Modifier.padding(top = topBarClearance)`) gets the same measured value.
5. Confirm no zero-clearance flash on first open (the formula fallback seeds the initial frame).
