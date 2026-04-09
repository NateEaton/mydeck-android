# Fix: Text Disappearing in Reader View During Typography Changes

**Status:** Fix applied, confirmation pending
**Reported:** 2026-03-23
**Affects:** v0.11.1 and current (`main`)
**Branch:** `fix/reader-text-disappearing`

## Problem

Text in the reader view appears to disappear with certain combinations of typography settings. On closer inspection, the text is not gone — the **WebView's content height is calculated too short**, truncating the article mid-sentence with blank space below. Scrolling up reveals the beginning of the article but it cuts off partway through.

The trigger varies by device but consistently involves:

- **Wide** body width
- **Literata** font (bundled via `@font-face`)
- Font size ~105%, spacing ~90%
- Toggling **hyphenation** on/off

The user reports that with hyphenation on, text always truncates/disappears. With hyphenation off, quickly switching font sizes also causes it, but scrolling can make some text reappear.

**Important:** The truncation is **persistent** — it survives navigating away from the bookmark and re-opening it. This rules out a purely transient race condition and suggests a stale height value is cached somewhere (WebView internal state, Compose measurement, or both).

The issue is article-dependent — not all articles trigger it. The debug article is a ~3100-word New Lines Magazine piece.

## Debug Artifacts

See `debug/reflow-issue/` for screenshots and exported bookmark JSON.

## Architecture Context

The reader is a WebView embedded in a Compose `verticalScroll` Column:

```
Column (verticalScroll)
  └─ Box (fillMaxWidth(textWidth.widthFraction))   ← Compose controls width
       └─ AndroidView(Modifier.padding(0.dp))       ← no height constraint
            └─ WebView
                 ├─ settings.textZoom = fontSizePercent   ← native API
                 └─ evaluateJavascript(body.style.*)      ← JS bridge
```

**Text width** is a Compose `fillMaxWidth` fraction (0.75/0.85/0.98), not CSS.
**Font size** uses `WebView.settings.textZoom`.
**Other typography** (font-family, line-height, hyphens, justify) is applied via JS in `WebViewTypographyBridge.applyTypography()`.

Key files:
- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt` — WebView setup, update block, LaunchedEffects
- `app/src/main/java/com/mydeck/app/ui/detail/WebViewTypographyBridge.kt` — JS generation
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt` — Compose layout (width fraction)
- `app/src/main/assets/html_template_light.html` — base CSS (`font-size: 62.5%` on html, `1.95rem` on body)

## Root Cause Analysis

The WebView's measured content height gets calculated too short and then "sticks" — the article renders but is clipped partway through. Contributing factors:

1. **`textZoom` in the `update` block** (line ~610) fires on every Compose recomposition, not just on actual changes. Each assignment triggers an internal WebView relayout.

2. **Typography JS runs after a 150ms delay** (LaunchedEffect at lines ~227-239). This creates a window where `textZoom` has changed but CSS properties (line-height, hyphens, font-family) haven't caught up — the WebView is in an inconsistent layout state.

3. **No `requestLayout()` after JS typography changes.** After `evaluateJavascript` modifies `body.style.*`, the WebView reflows internally but doesn't tell Compose it needs re-measurement. The `postVisualStateCallback` pattern is already used successfully elsewhere in the file (lines ~491-502 for content ready detection).

4. **`LAYER_TYPE_HARDWARE`** (line ~363) can cause the WebView to skip repainting after content reflow, a known Android WebView issue especially pre-API 30.

5. **Bundled font loading** (`@font-face` from `file:///android_asset/`) is asynchronous. With `font-display: swap`, the WebView may measure with a fallback font, then reflow when the real font loads — another window for height miscalculation.

### Why it's device/article dependent

- Different screen widths produce different line-break points, so which articles trigger zero-height depends on how text reflows at a specific width
- Wide body (98% fill) leaves minimal margin, making reflow timing more sensitive
- Hyphenation adds an extra layout pass in WebView rendering
- Literata requires async font loading from assets

### Why scrolling partially helps

Scrolling up reveals the beginning of the article, but the content is still truncated — the full article is not accessible. Scrolling triggers Compose recomposition → `update` block re-applies `textZoom` → WebView may partially re-measure, but not enough to recover the full content height.

### Why the truncation persists across navigation

The stale height survives leaving the reader and re-opening the same bookmark. This suggests:
- The WebView's internal content height calculation is cached and not invalidated on re-entry, OR
- The Compose layout captures the truncated measurement and the `AndroidView` `update` block doesn't force a fresh measure pass, OR
- `textZoom` is re-applied in the `update` block before the content has fully loaded, locking in a short height again each time

## Proposed Fix

### 1. Guard `textZoom` in the update block (low risk)

Only assign `textZoom` when it actually changed to avoid spurious relayouts:

```kotlin
update = {
    // ... existing content loading ...
    if (it.settings.textZoom != uiState.typographySettings.fontSizePercent) {
        it.settings.textZoom = uiState.typographySettings.fontSizePercent
    }
}
```

### 2. Force re-layout after typography JS using `postVisualStateCallback` (primary fix)

After `evaluateJavascript` in the typography LaunchedEffect, use the WebView's own signal that it has finished painting to trigger re-measurement. This is the same pattern already used successfully in `reportReadyIfNeeded()` (lines ~491-502).

```kotlin
LaunchedEffect(uiState.typographySettings) {
    webViewRef.value?.let { webView ->
        // Guard textZoom to avoid spurious relayouts
        if (webView.settings.textZoom != uiState.typographySettings.fontSizePercent) {
            webView.settings.textZoom = uiState.typographySettings.fontSizePercent
        }
        // Reduced delay from 150ms - just enough for textZoom to take effect
        delay(50)
        withContext(Dispatchers.Main) {
            val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
            webView.evaluateJavascript(js) {
                // Use postVisualStateCallback for reliable timing (API 23+)
                // Fallback to postDelayed for older devices
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val requestId = System.currentTimeMillis()
                    webView.postVisualStateCallback(requestId,
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                webView.requestLayout()
                            }
                        })
                } else {
                    webView.postDelayed({ webView.requestLayout() }, 100)
                }
            }
        }
    }
}
```

### 3. Remove forced hardware layer type (critical fix)

The WebView is inside a Compose `verticalScroll` Column, so it renders its entire content height at once. `LAYER_TYPE_HARDWARE` forces the entire View into a single GPU texture. Most mobile GPUs have a maximum texture dimension of 8,192–16,384 CSS pixels. When a long article with large font and wide width exceeds that limit, rendering stops abruptly mid-content — producing exactly the "text cut off with blank space below" symptom.

**Fix:** Change `setLayerType(View.LAYER_TYPE_HARDWARE, null)` to `setLayerType(View.LAYER_TYPE_NONE, null)` in both WebView factory blocks (article and embed). `LAYER_TYPE_NONE` uses standard hardware-accelerated display list rendering with automatic tiling — no single-texture ceiling.

Do **not** use `LAYER_TYPE_SOFTWARE` — that would allocate a massive bitmap in RAM and likely OOM on long articles.

## Implementation Status

| Fix | Status | Location | Notes |
|-----|--------|----------|-------|
| 1. Guard `textZoom` | ✅ Implemented | `BookmarkDetailWebViews.kt` ~line 633 | Prevents spurious relayouts on every recomposition |
| 2. `postVisualStateCallback` | ✅ Implemented | `BookmarkDetailWebViews.kt` ~lines 243-258 | Uses proven pattern from content-ready detection |
| 3. `LAYER_TYPE_NONE` | ✅ Implemented | `BookmarkDetailWebViews.kt` lines 391, 868 | Most likely primary fix — eliminates GPU texture size limit |

## Diagnostic Results (2026-04-08)

After applying all three fixes, diagnostic logging was added to compare JS `document.documentElement.scrollHeight` against the Android View's `height` and `measuredHeight` after each typography change.

Over 130 logged typography changes across multiple articles:
- **Zero measurement desyncs** — JS height and View height maintained a consistent ratio (~2.875x, matching device density) in every single entry
- View heights reached up to **797,829 device pixels** (277,506 CSS pixels) without truncation — far exceeding the ~8,192–16,384 CSS pixel GPU texture limit that would have applied under `LAYER_TYPE_HARDWARE`
- Rapid typography toggling (10+ changes in 2 seconds) showed no stale measurements

**Conclusion:** The GPU texture limit from `LAYER_TYPE_HARDWARE` was almost certainly the primary cause. Fixes #1 and #2 are good defensive improvements but were not sufficient on their own. Developer testing shows the issue is resolved; confirmation from the original reporter is pending.

## Testing Plan

- Reproduce with the debug article (New Lines Magazine piece in `debug/reflow-issue/`)
- Test all combinations: each font × each width × each spacing × hyphenation on/off
- Rapidly toggle settings to stress the race condition
- Test on both phone and emulator (different screen densities)
- Verify no regression in scroll-position restoration or search highlighting
