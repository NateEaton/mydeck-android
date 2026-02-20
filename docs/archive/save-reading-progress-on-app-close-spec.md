# Mini Spec: Save Reading Progress on App Close/Minimize

## Problem Statement

When a user is reading an article and minimizes or closes the app without navigating back to the list view, the reading progress (scroll position) is lost. This only affects articles; photos and videos are automatically marked as complete when opened.

---

## Current Behavior

### Progress Tracking
- **Location**: `BookmarkDetailViewModel.kt`
- **State variable**: `currentScrollProgress` (line 83) - tracks scroll percentage (0-100)
- **Update method**: `onScrollProgressChanged(progress: Int)` (line 287-299) - called by WebView scroll listener
- **Type filtering**: Only tracks articles; photos/videos auto-complete to 100% on open (lines 114-126)

### Progress Persistence
Reading progress is currently saved in **two scenarios**:

1. **User navigates back to list view**
   - Method: `onClickBack()` (line 383-389)
   - Calls: `saveCurrentProgress()` before navigation
   - **Reliable**: ✅ Always fires when user taps back button

2. **ViewModel is destroyed**
   - Method: `onCleared()` (line 157-167)
   - Uses independent coroutine scope to ensure completion
   - **Unreliable**: ❌ May not fire if Android system kills app process

### The Gap
When a user:
- Minimizes the app while reading (switches to another app)
- Closes the app from recent apps screen
- App is killed by system (low memory, battery optimization)

...the `onCleared()` lifecycle method may not fire, and scroll progress tracked in memory is lost.

---

## Root Cause

**Android Lifecycle Issue**:
- `ViewModel.onCleared()` fires during `Activity.onDestroy()`
- If the system kills the app process at `onStop()` (before `onDestroy()`), `onCleared()` never executes
- Progress saved only in memory (`currentScrollProgress`) is lost

---

## Proposed Solution

Add lifecycle observation in `BookmarkDetailScreen` to save progress when the screen goes to background, ensuring persistence even if the app is killed.

---

## Implementation

### 1. **Add Lifecycle Observer** (`BookmarkDetailScreen.kt`)

**Location**: After ViewModel instantiation (around line 92)

**Required imports**:
```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
```

**Code**:
```kotlin
val lifecycleOwner = LocalLifecycleOwner.current

DisposableEffect(lifecycleOwner, bookmarkId) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            // Screen going to background - save article progress
            val state = viewModel.uiState.value
            if (state is BookmarkDetailViewModel.UiState.Success &&
                state.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE) {
                // Trigger save via ViewModel
                viewModel.saveProgressOnPause()
            }
        }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

**Why `ON_STOP` instead of `ON_PAUSE`?**
- `ON_PAUSE`: Fires when screen partially obscured (e.g., dialog) - too frequent, unnecessary saves
- `ON_STOP`: Fires when screen fully hidden - ideal for state persistence
- `ON_DESTROY`: Unreliable if process killed - same problem as `onCleared()`

---

### 2. **Expose Save Method** (`BookmarkDetailViewModel.kt`)

**Add public method** (near line 180):

```kotlin
/**
 * Save current reading progress. Called when screen goes to background
 * to ensure progress persists even if app is killed by system.
 */
fun saveProgressOnPause() {
    viewModelScope.launch {
        saveCurrentProgress()
    }
}
```

**Note**: The existing private `saveCurrentProgress()` method (line 171-180) already handles:
- Null checks for `bookmarkId`
- Progress validation (> 0)
- Repository call with error handling
- No changes needed to this method

---

## Behavior Changes

### Before
1. User scrolls article to 45%
2. User minimizes app (switches to browser)
3. System kills MyDeck app to free memory
4. User returns to app → article shows 0% progress (or last saved progress from previous session)

### After
1. User scrolls article to 45%
2. User minimizes app → `ON_STOP` fires → progress saved to database
3. System kills MyDeck app to free memory
4. User returns to app → article shows 45% progress ✅

---

## Edge Cases

| Scenario | Behavior | Notes |
|----------|----------|-------|
| User scrolls then immediately closes app | Progress saved on `ON_STOP` before kill | ✅ Works |
| User navigates back normally | Progress saved twice (`ON_STOP` + `onClickBack`) | ✅ Idempotent, harmless |
| User opens photo/video | No save triggered (type check) | ✅ Efficient |
| User scrolls up after reaching 100% | Progress locked at 100% (existing behavior) | ✅ Preserved |
| User switches apps rapidly | Multiple `ON_STOP` events | ✅ Debounced by coroutine scope |
| App crashes before `ON_STOP` | Progress not saved | ⚠️ Unavoidable (instant crash) |

---

## Testing Checklist

- [ ] Open article, scroll to 50%, minimize app, reopen → progress at 50%
- [ ] Open article, scroll to 50%, close from recent apps, reopen → progress at 50%
- [ ] Open article, scroll to 50%, navigate back normally → progress at 50% (double-save harmless)
- [ ] Open article, scroll to 100%, minimize, reopen → progress locked at 100%
- [ ] Open photo → no additional save calls (type check works)
- [ ] Open video → no additional save calls (type check works)
- [ ] Open article with no scroll → no save calls (progress = 0 check works)

---

## Benefits

✅ **User experience**: Reading progress preserved across app sessions
✅ **Reliability**: Survives system-initiated process kills
✅ **Performance**: Only saves on background (not every scroll event)
✅ **Type-safe**: Only applies to articles (photos/videos unaffected)
✅ **Backwards compatible**: Doesn't change existing save behavior
✅ **Low risk**: Additive change, reuses existing save logic

---

## Implementation Estimate

- **Code changes**: ~20 lines total (15 in Screen, 5 in ViewModel)
- **Testing**: 10-15 minutes
- **Risk**: Very low (additive, idempotent operation)
- **Dependencies**: None (uses standard Android/Compose lifecycle APIs)

---

## Future Considerations

If performance becomes a concern (frequent app switching), consider:
- Debouncing saves with a short delay (e.g., 500ms)
- Batching multiple progress updates
- Using WorkManager for guaranteed background persistence

However, the current approach is sufficient for typical usage patterns.
