# Fix: Text Disappearing in Reader View During Typography Changes

**Status:** Diagnosed, not yet implemented
**Reported:** 2026-03-23
**Affects:** v0.11.1 and current (`feature/sync-multipart-v012`)

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

1. **`textZoom` in the `update` block** (line ~593) fires on every Compose recomposition, not just on actual changes. Each assignment triggers an internal WebView relayout.

2. **Typography JS runs after a 150ms delay** (LaunchedEffect at line ~215). This creates a window where `textZoom` has changed but CSS properties (line-height, hyphens, font-family) haven't caught up — the WebView is in an inconsistent layout state.

3. **No `requestLayout()` after JS typography changes.** After `evaluateJavascript` modifies `body.style.*`, the WebView reflows internally but doesn't tell Compose it needs re-measurement.

4. **`LAYER_TYPE_HARDWARE`** (line ~351) can cause the WebView to skip repainting after content reflow, a known Android WebView issue especially pre-API 30.

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

### 2. Force re-layout after typography JS (primary fix)

After `evaluateJavascript` in the typography LaunchedEffect, force the WebView to re-measure:

```kotlin
LaunchedEffect(uiState.typographySettings) {
    webViewRef.value?.let { webView ->
        webView.settings.textZoom = uiState.typographySettings.fontSizePercent
        delay(50)  // reduced from 150ms
        withContext(Dispatchers.Main) {
            val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
            webView.evaluateJavascript(js) {
                // Force Compose to re-measure after WebView reflows
                webView.requestLayout()
            }
        }
    }
}
```

### 3. Consider `postVisualStateCallback` for reliable timing

Instead of arbitrary delays, use the WebView's own signal that it has finished painting:

```kotlin
webView.evaluateJavascript(js) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        webView.postVisualStateCallback(System.currentTimeMillis(),
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    webView.requestLayout()
                }
            })
    } else {
        webView.postDelayed({ webView.requestLayout() }, 100)
    }
}
```

### 4. Evaluate `LAYER_TYPE_SOFTWARE` fallback (if above insufficient)

If hardware layer continues to cause stale rendering, consider software rendering as a targeted fallback during typography transitions.

## Testing Plan

- Reproduce with the debug article (New Lines Magazine piece in `debug/reflow-issue/`)
- Test all combinations: each font × each width × each spacing × hyphenation on/off
- Rapidly toggle settings to stress the race condition
- Test on both phone and emulator (different screen densities)
- Verify no regression in scroll-position restoration or search highlighting
