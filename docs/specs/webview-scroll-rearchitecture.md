


# Spec: Bookmark Detail Screen Re-Architecture

## 1. Objective
To resolve persistent bugs related to scroll state restoration, text reflow, and memory usage in the `BookmarkDetailScreen` by transitioning from a Compose-managed scroll architecture to a WebView-native scroll architecture, while preserving the seamless integration of native Compose header and footer components.

## 2. Background & Problem Statement
Currently, the `WebView` used to display article content is wrapped in a Compose `Column` using `Modifier.verticalScroll`. 
*   **The Anti-pattern:** This forces the `WebView` to measure and render its entire HTML height at once (often tens of thousands of pixels), defeating the browser engine's internal memory optimizations, lazy-loading, and viewport rendering.
*   **The Bug:** Because Compose manages the scroll state, any asynchronous changes in the HTML (such as the recent text-reflow fix, lazy-loaded images, or font initializations) change the layout bounds *after* Compose has attempted to restore the scroll percentage. This causes unpredictable jumps and aggressively saves incorrect read-progress percentages back to the database.

## 3. Proposed Solution
1.  **Unwrap the WebView:** Allow the WebView to constrain to the screen bounds and handle its own vertical scrolling natively.
2.  **JavaScript State Management:** Shift read-progress calculation and scroll restoration to a JavaScript Bridge, leveraging `ResizeObserver` to automatically maintain scroll percentage during text reflows.
3.  **Nested Scrolling UI:** Utilize Compose's `NestedScrollConnection` to listen to the WebView's native scroll events, collapsing the Compose Header when scrolling down, and pulling the Compose Footer up when reaching the end of the article.

---

## 4. Implementation Steps

### Phase 1: JavaScript Bridge (Scroll & Progress Tracking)
Before altering the UI layout, migrate the read-progress logic to JS.

1.  **Create the JS Interface:**
    Define a Kotlin interface to receive progress updates.
    ```kotlin
    class BookmarkScrollInterface(private val onProgressChanged: (Float) -> Unit) {
        @JavascriptInterface
        fun reportProgress(percentage: Float) {
            onProgressChanged(percentage)
        }
    }
    ```
2.  **Inject JS Scroll Manager:**
    Inject a script on page load that handles restoring the scroll position, observing DOM reflows, and reporting user scrolls.
    ```javascript
    let targetPercentage = %f; // Passed from Kotlin
    let isUserScrolling = false;

    function getScrollMax() {
        return document.documentElement.scrollHeight - window.innerHeight;
    }

    function maintainScrollPosition() {
        if (!isUserScrolling && targetPercentage > 0) {
            let max = getScrollMax();
            if (max > 0) window.scrollTo(0, max * targetPercentage);
        }
    }

    // 1. Initial restore
    maintainScrollPosition();

    // 2. Handle async reflows (fonts, images, text-wrap)
    new ResizeObserver(() => {
        maintainScrollPosition();
    }).observe(document.documentElement);

    // 3. Track user scrolling
    window.addEventListener('scroll', () => {
        isUserScrolling = true;
        let max = getScrollMax();
        let percentage = max > 0 ? window.scrollY / max : 0;
        AndroidScroll.reportProgress(percentage);
    });
    ```

### Phase 2: Unwrap the WebView
Remove the Compose scroll wrapper so the WebView behaves natively.

1.  Remove `Modifier.verticalScroll(scrollState)` from the container wrapping the `AndroidView`.
2.  Assign `Modifier.weight(1f)` or `Modifier.fillMaxSize()` to the `AndroidView` so it fills the available screen space.
3.  Enable nested scrolling on the WebView instance:
    ```kotlin
    factory = { context ->
        WebView(context).apply {
            isNestedScrollingEnabled = true
            addJavascriptInterface(BookmarkScrollInterface { ... }, "AndroidScroll")
            // existing configurations...
        }
    }
    ```

### Phase 3: Header Migration (Collapsing TopAppBar)
Migrate the existing header (`BookmarkDetailHeader`—containing title, metadata, and description) to a Material 3 Collapsing Top App Bar standard.

1.  Initialize a scroll behavior:
    ```kotlin
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ```
2.  Attach the behavior to the parent `Scaffold`:
    ```kotlin
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            // Refactor BookmarkDetailHeader to sit inside this TopAppBar block
        }
    )
    ```

#### Phase 3 Status (2026-05-07)

Phase 3 was implemented as a collapsing `LargeTopAppBar` with title-in-bar, then reverted to a small `TopAppBar` with the title rendered inline in WebView HTML (Option B) to match the v0.12.6 visual style.

Hide/show-on-scroll was attempted via a custom WebView↔Compose nested-scroll bridge: touch events were intercepted in `HighlightActionWebView`, guarded by a touch-slop threshold, and re-dispatched into the Compose scroll system via `MotionEvent.offsetLocation` coordinate shifting. The code compiles and dispatches events correctly, but produces visible jitter/flicker during hesitant or slow gestures — an oscillation around the slop threshold ("starts to hide, doesn't, then finally does").

**Decision:** Top bar pinned to always-visible via `TopAppBarDefaults.pinnedScrollBehavior()` pending future work. The `nestedScroll` wiring and `scrollBehavior` argument are left in place — `pinnedScrollBehavior` satisfies the API contract as a no-op for collapse.

The bridge code in `HighlightActionWebView` (touch-slop guard, `offsetLocation`, cumulative offset) is retained because it still drives `fullscreenTopBarRevealConnection` on the same Scaffold modifier.

**Future options to revisit:**
- (a) Wrap the WebView in a dedicated `NestedScrollingParent2`/`3` `ViewGroup` that forwards scroll offsets via a Compose interop helper — higher fidelity, more complex.
- (b) Accept the JS-derived (post-hoc) scroll bridge as lower fidelity but stable.
- (c) Reconsider whether hide/show is worth the complexity, given the content-inline title already scrolls naturally out of view.

### Phase 4: Footer Migration (End-of-Content Reveal)
Preserve the Readeck UX of action buttons (Favorite, Archive, Delete) appearing at the end of the article by using an unconsumed scroll connection.

1.  **Create the custom NestedScrollConnection:**
    ```kotlin
    val footerHeightPx = with(LocalDensity.current) { 80.dp.toPx() }
    var footerOffsetPx by remember { mutableFloatStateOf(0f) }

    val footerScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Hide footer when scrolling UP
                val delta = available.y
                if (delta > 0 && footerOffsetPx < 0f) {
                    val oldOffset = footerOffsetPx
                    footerOffsetPx = (footerOffsetPx + delta).coerceAtMost(0f)
                    return Offset(0f, footerOffsetPx - oldOffset)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Reveal footer when scrolling DOWN hits WebView bottom limit
                val delta = available.y
                if (delta < 0) {
                    val oldOffset = footerOffsetPx
                    footerOffsetPx = (footerOffsetPx + delta).coerceAtLeast(-footerHeightPx)
                    return Offset(0f, footerOffsetPx - oldOffset)
                }
                return Offset.Zero
            }
        }
    }
    ```
2.  **Apply to the layout:**
    Chain this connection to the container and apply the translation offset.
    ```kotlin
    Column(
        modifier = Modifier
            .nestedScroll(footerScrollConnection)
            .offset { IntOffset(0, footerOffsetPx.roundToInt()) }
    ) {
        AndroidView(modifier = Modifier.weight(1f), ...) // WebView
        BookmarkDetailFooter(modifier = Modifier.height(80.dp)) // Action buttons
    }
    ```

### Phase 5: Search & Annotation Navigation
Refactor features that navigate to specific areas of the text.
*   **Old Behavior:** Calculating pixel offset and calling Compose's `scrollState.scrollTo()`.
*   **New Behavior:** Passing the target pixel or element ID to JS via `webView.evaluateJavascript()`.
    *   *Example:* `webView.evaluateJavascript("window.scrollTo({top: $targetY, behavior: 'smooth'});", null)`

#### 5a. In-article search match navigation
On selecting a search result, call:
```kotlin
webView.evaluateJavascript("window.scrollTo({top: $targetY, behavior: 'smooth'});", null)
```

#### 5b. In-reader Highlights bottom-sheet — tap a row
Currently calls `viewModel.scrollToAnnotation(id)` which sets `_pendingAnnotationScrollId`. After rearch, replace with a direct JS call from the screen's tap handler:
```kotlin
webView.evaluateJavascript(
    "(function(){ var el=document.getElementById('annotation-${id}'); " +
        "if(el) el.scrollIntoView({block:'center', behavior:'smooth'}); })();",
    null
)
```
The `pendingAnnotationScrollId` StateFlow, `scrollToAnnotation()`, `onAnnotationScrollHandled()`, the scroll-driver `LaunchedEffect`, and `WebViewAnnotationBridge.getAnnotationViewportInfo()` can all be removed once both 5b and 5c are implemented.

#### 5c. Highlights screen deep-link (`BookmarkDetailRoute(annotationId=…)`)
The Highlights nav-drawer screen passes `annotationId` through the route into the detail screen. After rearch, the deep-link target is folded into the Phase 1 JS scroll manager so it runs once the DOM is ready and is not interrupted by the percentage-based restore.

Add a second placeholder to the Phase 1 injection:
```javascript
let targetPercentage = %f;
let targetAnnotationId = "%s";   // empty string if none
let isUserScrolling = false;
let initialPositionApplied = false;

function applyInitialPosition() {
    if (initialPositionApplied || isUserScrolling) return;
    if (targetAnnotationId) {
        const el = document.getElementById('annotation-' + targetAnnotationId);
        if (el) {
            el.scrollIntoView({block: 'center', behavior: 'instant'});
            initialPositionApplied = true;
            return;
        }
        // element not in DOM yet — let the ResizeObserver retry
        return;
    }
    // fall through to percentage restore
    if (targetPercentage > 0) {
        const max = getScrollMax();
        if (max > 0) {
            window.scrollTo(0, max * targetPercentage);
            initialPositionApplied = true;
        }
    }
}

applyInitialPosition();
new ResizeObserver(applyInitialPosition).observe(document.documentElement);
```

Notes:
*   Only one of the two placeholders is non-empty at any time. If `annotationId` is present on the route, Kotlin should pass `targetPercentage = 0`. The percentage restore is suppressed in that case so a saved progress at e.g. 100% does not race with the highlight scroll target.
*   Use `behavior: 'instant'` for the initial position; the entry transition (scale-in from `perf-and-transition-u-enhance-spec.md` if landed) masks the jump.
*   Once `isUserScrolling` becomes true, both targets are abandoned — manual scrolling takes precedence over any pending restore or deep-link target.
*   `ResizeObserver` calls `applyInitialPosition` whenever the document height changes (lazy images, font swap). Because `initialPositionApplied` flips to `true` on first success, subsequent reflows do not re-scroll and the user's manual position is preserved.

#### 5d. Migration cleanup (after 5b and 5c land)
Remove from `BookmarkDetailViewModel`:
*   `_pendingAnnotationScrollId` StateFlow + `pendingAnnotationScrollId` accessor
*   `scrollToAnnotation()` and `onAnnotationScrollHandled()` methods

Remove from `BookmarkDetailScreen.kt`:
*   The `LaunchedEffect(annotationId)` in the entry composable that calls `viewModel.scrollToAnnotation(annotationId)`
*   The scroll-driver `LaunchedEffect` keyed on `pendingAnnotationScrollId` and the surrounding `articleTopOffset` / `viewportHeight` `remember`s used only for that math
*   The `pendingAnnotationScrollId` parameter threaded through `BookmarkDetailHost` → `BookmarkDetailScreen` (overload 2) → `BookmarkDetailContent`
*   The restore-skip guard `if (pendingAnnotationScrollId != null) ... return` in the saved-position restore loop (the loop itself is removed in Phase 1, so this becomes moot)

Remove from `WebViewAnnotationBridge.kt`:
*   `getAnnotationViewportInfo()` and `AnnotationViewportInfo` — superseded by JS-side `scrollIntoView`

`BookmarkDetailScreen` keeps `annotationId` as a parameter, but its only consumer is the `loadDataWithBaseURL` injection — passed straight into the JS placeholder at Phase 1.

---

## 5. Risk Assessment & Testing
*   **Testing JS Execution Timing:** Ensure the JavaScript injected for scroll tracking only runs *after* the initial reader content (DOM) is fully loaded to prevent JS exceptions.
*   **Overscroll Glow:** The native Android overscroll effect (the stretch at the top/bottom of lists) on the WebView might visually clash with the Compose footer reveal. Consider disabling the WebView overscroll (`webView.overScrollMode = View.OVER_SCROLL_NEVER`) to make the Compose footer pull-up feel more seamless. 
*   **Loss of unified scrollbar:** Because the header and footer are now detached from the main content viewport, the scrollbar will only represent the WebView's content, not the absolute top of the header or bottom of the footer. This is standard Android behavior and acceptable.