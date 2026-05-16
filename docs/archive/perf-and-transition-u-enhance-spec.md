# Mini-Spec: Performance & Transition UX Enhancements

## 1. Overview
This specification details two targeted enhancements for MyDeck to improve the perceived performance and visual hierarchy when opening articles:
1. **Chromium Engine Warm-up:** Eliminate the "first-click" loading penalty by pre-loading the WebView engine into memory during the app's idle startup phase.
2. **Refined M3 Z-Axis Transition:** Replace the current slide/scale hybrid transition with a true Material 3 depth transition, masking the WebView layout and scroll-restoration calculations while reinforcing the "card expanding over the list" mental model.

---

## 2. Chromium Engine Warm-up

### The Problem
Instantiating the first `WebView` in an Android app requires the OS to load the Chromium engine into the app's memory space. This can block the main thread for 100-200ms, causing noticeable jank or a delay the first time a user taps a bookmark card.

### The Solution
We will instantiate a dummy `WebView` silently in the background immediately *after* the initial Compose UI has finished drawing. 

### Implementation Details
**Target File:** `app/src/main/java/com/mydeck/app/MainActivity.kt`

Modify `onCreate` to inject an `IdleHandler`. The `IdleHandler` executes only when the main thread has finished all pending UI rendering tasks, ensuring it does not negatively impact the app's cold-start metrics.

```kotlin
import android.os.Looper
import android.webkit.WebView
// ... existing imports ...

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // 1. Add the Engine Warm-up IdleHandler
    Looper.myQueue().addIdleHandler {
        try {
            // Instantiate a dummy WebView using application context.
            // This forces the OS to load Chromium into memory now.
            WebView(applicationContext)
            Timber.d("WebView engine pre-warmed successfully")
        } catch (e: Exception) {
            Timber.w(e, "Failed to pre-warm WebView engine")
        }
        false // Return false to remove the handler after it runs once
    }

    setContent { 
        // ... existing setup ...
    }
}
```

---

## 3. Refined M3 Z-Axis / Depth Transition

### The Problem
Currently, `AppShell.kt` sets a default `slideInHorizontally` for the `NavHost`, but overrides it with a `scaleIn(0.92f)` on the `BookmarkDetailRoute`. Because the list view underneath either slides away or snaps out of existence, it breaks the illusion of depth.

### The Solution
Implement the standard Material 3 **Shared Z-Axis** choreography. 
*   **Opening an Article:** The detail screen fades in and scales up from `0.85f` (coming forward). The list screen simultaneously fades out and scales down slightly to `0.95f` (receding backward).
*   **Closing an Article:** The detail screen fades out and scales down to `0.85f` (dropping away). The list screen fades in and scales up from `0.95f` to `1.0f` (coming forward).

### Implementation Details
**Target File:** `app/src/main/java/com/mydeck/app/ui/shell/AppShell.kt`

We need to update the `NavHost` transitions in `CompactAppShell`, `MediumAppShell`, and `ExpandedAppShell` to dynamically check the routes and apply the depth effect specifically when transitioning to/from `BookmarkDetailRoute`.

*Replace the `enterTransition`, `exitTransition`, `popEnterTransition`, and `popExitTransition` blocks on the `NavHost` with the following dynamic logic:*

```kotlin
// Define a standard duration for the depth transition to give WebView time to layout
val depthAnimationSpec = tween<Float>(durationMillis = 350)

NavHost(
    navController = navController,
    startDestination = startDestination,
    // When a new screen is opening
    enterTransition = {
        if (targetState.destination.matchesRoute<BookmarkDetailRoute>()) {
            // Detail Screen comes forward
            scaleIn(initialScale = 0.85f, animationSpec = depthAnimationSpec) + 
            fadeIn(animationSpec = depthAnimationSpec)
        } else {
            // Default for Settings, About, etc.
            slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300))
        }
    },
    // When the current screen is being covered
    exitTransition = {
        if (targetState.destination.matchesRoute<BookmarkDetailRoute>()) {
            // List screen recedes backward
            scaleOut(targetScale = 0.95f, animationSpec = depthAnimationSpec) + 
            fadeOut(animationSpec = depthAnimationSpec)
        } else {
            slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300))
        }
    },
    // When the covered screen is returning to the foreground
    popEnterTransition = {
        if (initialState.destination.matchesRoute<BookmarkDetailRoute>()) {
            // List screen comes back forward
            scaleIn(initialScale = 0.95f, animationSpec = depthAnimationSpec) + 
            fadeIn(animationSpec = depthAnimationSpec)
        } else {
            slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300))
        }
    },
    // When the top screen is being closed/popped
    popExitTransition = {
        if (initialState.destination.matchesRoute<BookmarkDetailRoute>()) {
            // Detail Screen drops backward
            scaleOut(targetScale = 0.85f, animationSpec = depthAnimationSpec) + 
            fadeOut(animationSpec = depthAnimationSpec)
        } else {
            slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300))
        }
    }
) {
    // ... composable destinations ...
    
    composable<BookmarkDetailRoute> { backStackEntry -> 
        // REMOVE the custom transition overrides from this specific composable block,
        // as they are now handled globally by the NavHost logic above.
        val route = backStackEntry.toRoute<BookmarkDetailRoute>()
        BookmarkDetailScreen(...)
    }
}
```

*Note: Ensure this change is replicated across `CompactAppShell`, `MediumAppShell`, and `ExpandedAppShell` within `AppShell.kt`.*

---

## 4. Verification & Testing

1. **Test the Warm-up:**
   *   Kill the app completely. Open it. Tap the first article.
   *   *Expected Result:* The transition should begin immediately without dropping frames. `Timber` logs should show `WebView engine pre-warmed successfully` shortly after the initial UI renders.
2. **Test the Transition (Visual):**
   *   Tap an article.
   *   *Expected Result:* The article should scale up toward the user. The list behind it should dim and shrink slightly backward.
   *   Tap the hardware back button or top-left back arrow.
   *   *Expected Result:* The article scales down and fades out, revealing the list scaling back up to its normal size.
3. **Test the Transition (Scroll Masking):**
   *   Open an article, scroll halfway down, and hit back (to save progress).
   *   Re-open the same article.
   *   *Expected Result:* During the 350ms scale-in animation, the WebView should load the content and apply the scroll position. When the animation completes and the article is fully opaque, it should already be at the 50% scroll mark with no visible "jump".