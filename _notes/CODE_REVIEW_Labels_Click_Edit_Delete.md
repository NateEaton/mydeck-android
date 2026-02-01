# Code Review: Click Label to Filter + Edit/Delete Label

**Branch:** `claude/implement-labels-filtering-MAKIY`
**File with build errors:** `BookmarkListScreen.kt`
**Reviewer note:** Two distinct issues are causing the build failures. Both are straightforward to fix.

---

## Issue 1: Wrong import for `RectangleShape`

**Lines affected:** 21 (import), 603 (usage)

The import `androidx.compose.foundation.shape.RectangleShape` does not exist in the Compose version this project uses. `RectangleShape` lives in `androidx.compose.ui.graphics`.

**Fix:** Change line 21 from:
```kotlin
import androidx.compose.foundation.shape.RectangleShape
```
to:
```kotlin
import androidx.compose.ui.graphics.RectangleShape
```

That's a one-line change; nothing else needed for this issue.

---

## Issue 2: `viewModel` referenced inside `BookmarkListView` which doesn't have access to it

**Lines affected:** 1027, 1037, 1047

This is the more important issue and reflects a misunderstanding of how Compose composables access data.

### What went wrong

`BookmarkListView` is a standalone `@Composable` function (defined at line 1004) that receives all its data and callbacks through parameters. It does **not** have access to `viewModel` — that variable only exists inside `BookmarkListScreen` (line 125). You added `onClickLabel` calls that directly reference `viewModel`:

```kotlin
onClickLabel = { label -> viewModel.onClickLabel(label) }  // ← viewModel doesn't exist here
```

This happens three times (once per layout mode: GRID, COMPACT, MOSAIC).

### How to fix it

Follow the same pattern used for every other callback in `BookmarkListView` (`onClickDelete`, `onClickFavorite`, etc.):

1. **Add an `onClickLabel` parameter** to the `BookmarkListView` function signature:
   ```kotlin
   fun BookmarkListView(
       modifier: Modifier = Modifier,
       layoutMode: LayoutMode = LayoutMode.GRID,
       bookmarks: List<BookmarkListItem>,
       onClickBookmark: (String) -> Unit,
       onClickDelete: (String) -> Unit,
       onClickFavorite: (String, Boolean) -> Unit,
       onClickArchive: (String, Boolean) -> Unit,
       onClickOpenInBrowser: (String) -> Unit,
       onClickShareBookmark: (String) -> Unit,
       onClickLabel: (String) -> Unit          // ← add this
   )
   ```

2. **Use the parameter** inside the function body instead of `viewModel`:
   ```kotlin
   onClickLabel = { label -> onClickLabel(label) }
   // or simply:
   onClickLabel = onClickLabel
   ```

3. **Pass the callback from the call site** in `BookmarkListScreen` (~line 655) where `viewModel` *is* in scope:
   ```kotlin
   BookmarkListView(
       layoutMode = layoutMode.value,
       bookmarks = uiState.bookmarks,
       onClickBookmark = onClickBookmark,
       onClickDelete = onClickDelete,
       onClickArchive = onClickArchive,
       onClickFavorite = onClickFavorite,
       onClickOpenInBrowser = onClickOpenInBrowser,
       onClickShareBookmark = onClickShareBookmark,
       onClickLabel = { label -> viewModel.onClickLabel(label) }   // ← add this
   )
   ```

4. **Update the preview** (`BookmarkListViewPreview`, ~line 1062) to pass a no-op for the new parameter:
   ```kotlin
   onClickLabel = {}
   ```

---

## Summary

| # | Problem | Root cause | Fix |
|---|---------|-----------|-----|
| 1 | `RectangleShape` unresolved | Wrong import package | Change import to `androidx.compose.ui.graphics.RectangleShape` |
| 2 | `viewModel` unresolved (×3) | Referencing a variable that's not in scope inside a standalone composable | Thread `onClickLabel` as a lambda parameter through `BookmarkListView`, same pattern as the other callbacks |

Both fixes are mechanical. No logic changes needed — the feature implementation itself is correct.
