# Mini-Spec: "No Content" Fallback & UI Consistency

## 1. Problem Statement
Currently, when opening a bookmark that failed to extract content (e.g., server returned no article HTML), the app defaults to **Reader Mode**, showing an empty screen or error message. The user must manually switch to **Original Mode** (WebView). Additionally, the menu option to switch back to Reader Mode remains active even when no content exists.

## 2. Requirements
1.  **Auto-Switch:** If a bookmark has no offline content, the Detail Screen must default to **Original Mode** (WebView) immediately upon opening.
2.  **Centralized Logic:** The definition of "Has Content" must be moved from the UI layer to the View Model/Domain object to ensure consistency.
3.  **Menu Guard:** In the overflow menu, the option to switch to "View Article/Video/Photo" (Reader Mode) must be disabled (greyed out) if no offline content exists.

## 3. Implementation Details

### 3.1. Domain Model Update
Move the content existence logic into the `BookmarkDetailViewModel.Bookmark` data class.

**File:** `src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`

Update the `Bookmark` data class to include a `hasContent` property and calculate it during mapping:

```kotlin
data class Bookmark(
    // ... existing fields ...
    val hasContent: Boolean
)

// In the combine block where UiState is created:
UiState.Success(
    bookmark = Bookmark(
        // ... mappings ...
        hasContent = when (bookmark.type) {
            // Article needs actual text content
            is com.mydeck.app.domain.model.Bookmark.Type.Article -> !bookmark.articleContent.isNullOrBlank()
            // Video needs text content OR an embed code
            is com.mydeck.app.domain.model.Bookmark.Type.Video -> !bookmark.articleContent.isNullOrBlank() || !bookmark.embed.isNullOrBlank()
            // Photos are treated as always having content (the image URL)
            is com.mydeck.app.domain.model.Bookmark.Type.Picture -> true
        }
    )
)
```

### 3.2. Screen Initialization Logic
Update the `BookmarkDetailScreen` to check `hasContent` when initializing the state.

**File:** `src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`

```kotlin
@Composable
fun BookmarkDetailScreen(...) {
    // ... setup ...

    // Fix: Default to ORIGINAL if showOriginal is true OR if content is missing
    var contentMode by remember(uiState.bookmark.bookmarkId) { 
        mutableStateOf(
            if (showOriginal || !uiState.bookmark.hasContent) ContentMode.ORIGINAL 
            else ContentMode.READER
        ) 
    }

    // ...
}
```

### 3.3. Menu Item Disabling
Disable the menu item that switches to Reader Mode if content is missing.

**File:** `src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt` (inside `BookmarkDetailMenu`)

```kotlin
// Inside BookmarkDetailMenu composable
val isReaderMode = contentMode == ContentMode.READER

// Calculate the label based on type (Article/Photo/Video)
val (labelRes, icon) = when {
    isReaderMode -> Pair(R.string.action_view_original, Icons.AutoMirrored.Filled.OpenInNew)
    uiState.bookmark.type == Bookmark.Type.ARTICLE -> Pair(R.string.action_view_article, Icons.Outlined.Description)
    uiState.bookmark.type == Bookmark.Type.PHOTO -> Pair(R.string.action_view_photo, Icons.Filled.Image)
    else -> Pair(R.string.action_view_video, Icons.Filled.Movie)
}

DropdownMenuItem(
    text = { Text(stringResource(labelRes)) },
    // Fix: Disable if we are in Original Mode AND there is no content to switch back to
    enabled = if (isReaderMode) true else uiState.bookmark.hasContent,
    onClick = {
        // Toggle logic
        val newMode = if (isReaderMode) ContentMode.ORIGINAL else ContentMode.READER
        onContentModeChange(newMode)
        expanded = false
    },
    leadingIcon = {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            // Optional: Visually dim icon if disabled (Material3 usually handles this via enabled prop)
            tint = if (!isReaderMode && !uiState.bookmark.hasContent) 
                   MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) 
                   else LocalContentColor.current
        )
    }
)
```

## 4. Verification Steps
1.  **Test "No Content" Bookmark:**
    *   Tap a bookmark known to have no extracted content (e.g., the "newest normal" mentioned in the issue).
    *   **Verify:** It opens directly in the WebView (Original Mode).
    *   **Verify:** Open the menu. The "View Article" option is visible but greyed out/unclickable.
2.  **Test "Normal" Bookmark:**
    *   Tap a bookmark with content.
    *   **Verify:** It opens in Reader Mode.
    *   **Verify:** Menu allows switching to "View Original".
    *   **Verify:** Once in Original, Menu allows switching back to "View Article".