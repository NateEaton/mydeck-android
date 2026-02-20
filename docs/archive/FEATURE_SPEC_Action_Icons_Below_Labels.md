# Feature Spec: Action Icons Below Labels in List Views

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

Each bookmark card in the list view displays action icons (Favorite, Archive, Delete) in a row below the labels section. These icons allow quick actions on bookmarks without opening the detail view.

---

## User-Facing Behavior

### Visual Layout (Top to Bottom)
1. **Image** - Full-width thumbnail (150dp height)
2. **Title** - 2-line max, ellipsized
3. **Horizontal Divider**
4. **Site Info Row** - Favicon + site name + external link icon (clickable to open URL)
5. **Labels Row** - Icon + FlowRow of clickable label chips (if labels exist)
6. **Action Icons Row** - Favorite + Archive buttons (left), Delete button (right)

### Action Icons Row Layout
```
+--------------------------------------------------------+
| [★ Favorite] [📦 Archive]          [🗑️ Delete]         |
+--------------------------------------------------------+
```

**Left Side (Start):**
- Favorite button (star icon)
- Archive button (box icon)

**Right Side (End):**
- Delete button (trash icon)

### Icon States

**Favorite Icon:**
- Unfavorited: Outlined star (`Icons.Outlined.Grade`)
- Favorited: Filled star (`Icons.Filled.Grade`)
- Content Description: "Favorite" (string resource: `R.string.action_favorite`)

**Archive Icon:**
- Not Archived: Outlined box (`Icons.Outlined.Inventory2`)
- Archived: Filled box (`Icons.Filled.Inventory2`)
- Content Description: "Archive" (string resource: `R.string.action_archive`)

**Delete Icon:**
- Always filled trash icon (`Icons.Filled.Delete`)
- Content Description: "Delete" (string resource: `R.string.action_delete`)

### Interaction Behavior

**Favorite:**
- Click toggles favorite state
- Visual feedback: Icon changes from outlined to filled (or vice versa)
- No confirmation dialog
- Updates immediately (optimistic update)

**Archive:**
- Click toggles archive state
- Visual feedback: Icon changes from outlined to filled (or vice versa)
- No confirmation dialog
- Updates immediately (optimistic update)

**Delete:**
- Click marks bookmark for deletion
- Shows Snackbar with "Bookmark deleted" message
- Snackbar includes "UNDO" action button
- 10-second undo window (SnackbarDuration.Long)
- If not undone, bookmark permanently deleted after 10 seconds
- Bookmark removed from list immediately

---

## Implementation Details

### File Location
`/app/src/main/java/de/readeckapp/ui/list/BookmarkCard.kt`

### Component Structure

**BookmarkCard Composable:**
```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickMarkRead: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickLabel: (String) -> Unit = {}
)
```

### Action Icons Row Implementation

```kotlin
// Action Icons Row
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Row(horizontalArrangement = Arrangement.Start) {
        // Favorite Button
        IconButton(
            onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
            modifier = Modifier.width(48.dp).height(48.dp)
        ) {
            Icon(
                imageVector = if (bookmark.isMarked) Icons.Filled.Grade else Icons.Outlined.Grade,
                contentDescription = stringResource(R.string.action_favorite)
            )
        }

        // Archive Button
        IconButton(
            onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) },
            modifier = Modifier.width(48.dp).height(48.dp)
        ) {
            Icon(
                imageVector = if (bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                contentDescription = stringResource(R.string.action_archive)
            )
        }
    }

    // Delete Button (right side)
    IconButton(
        onClick = { onClickDelete(bookmark.id) },
        modifier = Modifier.width(48.dp).height(48.dp)
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.action_delete)
        )
    }
}
```

### Integration with BookmarkListScreen

**BookmarkListView Usage:**
```kotlin
@Composable
fun BookmarkListView(
    bookmarks: List<BookmarkListItem>,
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickMarkRead: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenInBrowser: (String) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit = {}
) {
    LazyColumn {
        items(bookmarks) { bookmark ->
            BookmarkCard(
                bookmark = bookmark,
                onClickCard = onClickBookmark,
                onClickDelete = onClickDelete,
                onClickArchive = onClickArchive,
                onClickFavorite = onClickFavorite,
                onClickMarkRead = onClickMarkRead,
                onClickOpenUrl = onClickOpenInBrowser,
                onClickShareBookmark = onClickShareBookmark,
                onClickLabel = onClickLabel
            )
        }
    }
}
```

### ViewModel Handler Functions

**BookmarkListScreen.kt Event Handlers:**
```kotlin
val onClickDelete: (String) -> Unit = { bookmarkId ->
    viewModel.onDeleteBookmark(bookmarkId)
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = "Bookmark deleted",
            actionLabel = "UNDO",
            duration = SnackbarDuration.Long // 10 seconds
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.onCancelDeleteBookmark()
        }
    }
}

val onClickMarkRead: (String, Boolean) -> Unit = { bookmarkId, isRead ->
    viewModel.onToggleMarkReadBookmark(bookmarkId, isRead)
}

val onClickFavorite: (String, Boolean) -> Unit = { bookmarkId, isFavorite ->
    viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite)
}

val onClickArchive: (String, Boolean) -> Unit = { bookmarkId, isArchived ->
    viewModel.onToggleArchiveBookmark(bookmarkId, isArchived)
}
```

**BookmarkListViewModel.kt Implementation:**
```kotlin
fun onToggleFavoriteBookmark(bookmarkId: String, isFavorite: Boolean) {
    viewModelScope.launch {
        val result = bookmarkRepository.updateBookmark(
            bookmarkId = bookmarkId,
            isFavorite = isFavorite
        )
        // Handle result (success/error)
    }
}

fun onToggleArchiveBookmark(bookmarkId: String, isArchived: Boolean) {
    viewModelScope.launch {
        val result = bookmarkRepository.updateBookmark(
            bookmarkId = bookmarkId,
            isArchived = isArchived
        )
        // Handle result (success/error)
    }
}

fun onDeleteBookmark(bookmarkId: String) {
    viewModelScope.launch {
        // Mark for deletion but don't delete immediately
        // Actual deletion happens after 10 seconds if not canceled
    }
}

fun onCancelDeleteBookmark() {
    // Cancel pending deletion
}
```

---

## Layout Specifications

### Row Layout
- **Outer Row:** `Modifier.fillMaxWidth().padding(top = 8.dp)`
- **Horizontal Arrangement:** `SpaceBetween` (pushes left and right groups apart)

### Button Sizing
- **IconButton Size:** 48dp x 48dp (standard Material3 touch target)
- **Icon Default Size:** 24dp x 24dp (Material Icons default)

### Spacing
- **Top Padding:** 8dp from labels row
- **Inner Row:** No explicit spacing (IconButtons have built-in padding)

---

## Material Icons Required

### Filled Icons
```kotlin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Inventory2
```

### Outlined Icons
```kotlin
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Inventory2
```

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `action_favorite` | "Favorite" | Favorite icon content description |
| `action_archive` | "Archive" | Archive icon content description |
| `action_delete` | "Delete" | Delete icon content description |

---

## Data Model Requirements

**BookmarkListItem:**
```kotlin
data class BookmarkListItem(
    val id: String,
    val isMarked: Boolean,      // Favorite state
    val isArchived: Boolean,    // Archive state
    // ... other fields
)
```

---

## API Integration

**Repository Updates:**
All actions call `bookmarkRepository.updateBookmark()`:

```kotlin
suspend fun updateBookmark(
    bookmarkId: String,
    isFavorite: Boolean? = null,
    isArchived: Boolean? = null,
    isRead: Boolean? = null
): BookmarkRepository.UpdateResult
```

**REST API Endpoint:**
- Method: `PATCH /bookmarks/{id}`
- Request Body (EditBookmarkDto):
  ```json
  {
    "isMarked": true/false,      // For favorite
    "isArchived": true/false,    // For archive
    "readProgress": 0 or 100     // For mark read
  }
  ```

**Delete Endpoint:**
- Method: `DELETE /bookmarks/{id}`
- Handled by `bookmarkRepository.deleteBookmark(id)`

---

## Key Behaviors

1. **Optimistic Updates:** UI updates immediately before API call completes
2. **Toggle Actions:** Favorite and Archive toggle between states on each click
3. **Delete Confirmation:** Uses Snackbar with UNDO instead of confirmation dialog
4. **Touch Targets:** All buttons are 48dp minimum for accessibility
5. **Visual Feedback:** Icons change between outlined/filled to indicate state
6. **Positioning:** Action icons always appear below labels (or below site info if no labels)

---

## Testing Considerations

**Test Cases:**
1. Toggle favorite from unmarked to marked
2. Toggle favorite from marked to unmarked
3. Toggle archive from unarchived to archived
4. Toggle archive from archived to unarchived
5. Delete bookmark and confirm removal from list
6. Delete bookmark and undo within 10 seconds
7. Delete bookmark and verify permanent deletion after 10 seconds
8. Verify touch target sizes (48dp minimum)
9. Verify icon state changes (outlined ↔ filled)
10. Test with and without labels present

---

## Implementation Notes for Refactor

1. **Layout Order:** Action icons row must be placed after labels row in Column hierarchy
2. **Icon State Logic:** Use conditional `if` expression to switch between filled/outlined icons based on bookmark state
3. **Delete Flow:** Requires SnackbarHostState from BookmarkListScreen scaffold
4. **Spacing:** `Arrangement.SpaceBetween` on outer Row pushes delete button to right automatically
5. **Button Size:** Explicit 48dp size ensures consistent touch targets across devices
6. **State Updates:** ViewModel handles all state changes; card is stateless and purely presentational
