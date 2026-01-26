# Feature Spec: Click Label to Filter + Edit/Delete Label

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

Users can click on label chips in bookmark cards to filter the list to that label. When viewing a label-filtered list, users can rename or delete the label via TopAppBar actions.

---

## User-Facing Behavior

### Feature Components

1. **Click Label in Bookmark Card** - Filter list to show only bookmarks with that label
2. **Edit Label Name** - Rename a label across all bookmarks
3. **Delete Label** - Remove a label from all bookmarks with undo support

---

## Part 1: Click Label to Filter

### Label Chip in Bookmark Card

**Visual Design:**
- Component: `SuggestionChip` from Material3
- Typography: `MaterialTheme.typography.labelMedium`
- Layout: FlowRow (wraps to multiple lines if needed)
- Icon: Label icon (`R.drawable.ic_label_24px`) displayed to left of chips

**Location in Card:**
- Positioned after site info row, before action icons row
- Only shown if bookmark has labels (conditional rendering)
- Multiple labels displayed horizontally with wrapping

**Interaction:**
- Tap any label chip
- Bookmark list immediately filters to show only bookmarks with that label
- TopAppBar title changes to "Labels / {label name}"
- TopAppBar actions change to show Edit icon

**Code Implementation:**
```kotlin
if (bookmark.labels.isNotEmpty()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_label_24px),
            contentDescription = "labels",
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.width(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            bookmark.labels.forEach { label ->
                SuggestionChip(
                    onClick = { onClickLabel(label) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}
```

---

## Part 2: Edit Label Name

### TopAppBar Edit Mode

**Entry State:**
- User is viewing bookmarks filtered by a specific label
- FilterState: `label = "some-label"`

**TopAppBar Changes:**

**Title Area (Normal State):**
```
Labels / my-label
```
- Format: "Labels / {label name}"
- Left side of title area

**Title Area (Edit State):**
```
[my-label____________]  (editable TextField)
```
- Full-width TextField
- Pre-populated with current label name
- Single line input
- Focus automatically (implicit with TextField)

**Action Icons:**

**Normal State:**
- Edit icon button (pencil icon: `Icons.Filled.Edit`)
- Content description: "Edit label" (string resource: `R.string.edit_label`)
- Click to enter edit mode

**Edit State:**
- Check icon button (checkmark icon: `Icons.Filled.Check`)
- Content description: "Save"
- Click to save changes

### Edit Flow

1. **User clicks Edit icon**
   - Local state `isEditingLabel` set to `true`
   - Local state `editedLabelName` set to current label name
   - TopAppBar title changes to TextField
   - Action icon changes to Check

2. **User modifies label name**
   - TextField value updates in local state
   - No validation (any non-empty string allowed)

3. **User clicks Check icon**
   - If label name changed AND not blank:
     - Call `viewModel.onRenameLabel(oldLabel, newLabel)`
   - Set `isEditingLabel` to `false`
   - TopAppBar returns to normal state
   - UI updates automatically when repository updates

4. **Rename Operation:**
   - ViewModel calls `bookmarkRepository.renameLabel(oldLabel, newLabel)`
   - Repository finds all bookmarks with old label
   - Each bookmark updated locally and via API
   - API: PATCH /bookmarks/{id} with `addLabels: [newLabel]` and `removeLabels: [oldLabel]`
   - If user is still viewing the label, filter updates to new name
   - Labels list auto-refreshes via Flow

### Implementation Details

**Local State in BookmarkListScreen:**
```kotlin
var isEditingLabel by remember { mutableStateOf(false) }
var editedLabelName by remember { mutableStateOf("") }
```

**TopAppBar Title (Edit Mode):**
```kotlin
if (filterState.value.label != null) {
    if (isEditingLabel) {
        TextField(
            value = editedLabelName,
            onValueChange = { editedLabelName = it },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${stringResource(id = R.string.labels)} / ${filterState.value.label}")
        }
    }
}
```

**TopAppBar Actions (Edit/Check Toggle):**
```kotlin
if (filterState.value.label != null && !isSearchActive.value) {
    // Show edit/check icon when a label is selected
    if (isEditingLabel) {
        IconButton(
            onClick = {
                // Save the edited label
                if (editedLabelName.isNotBlank() && editedLabelName != filterState.value.label) {
                    viewModel.onRenameLabel(filterState.value.label!!, editedLabelName)
                }
                isEditingLabel = false
            }
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Save"
            )
        }
    } else {
        IconButton(
            onClick = {
                editedLabelName = filterState.value.label ?: ""
                isEditingLabel = true
            }
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(id = R.string.edit_label)
            )
        }
    }
}
```

**ViewModel Handler:**
```kotlin
fun onRenameLabel(oldLabel: String, newLabel: String) {
    viewModelScope.launch {
        try {
            when (bookmarkRepository.renameLabel(oldLabel, newLabel)) {
                is BookmarkRepository.UpdateResult.Success -> {
                    // Update the filter state with the new label name
                    if (_filterState.value.label == oldLabel) {
                        setLabelFilter(newLabel)
                    }
                    // Labels will auto-refresh via Flow
                }
                is BookmarkRepository.UpdateResult.Error,
                is BookmarkRepository.UpdateResult.NetworkError -> {
                    Timber.e("Failed to rename label")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error renaming label")
        }
    }
}
```

---

## Part 3: Delete Label

### Delete Button

**Location:**
- Below TopAppBar, above bookmark list
- Only visible when viewing label-filtered list (not when editing)

**Button Design:**
```
[🗑️ Delete Label]
```
- Component: OutlinedButton
- Border: 1dp with `MaterialTheme.colorScheme.error` (red)
- Shape: RectangleShape (square corners)
- Content color: `MaterialTheme.colorScheme.error` (red text/icon)
- Icon: `Icons.Filled.Delete`
- Text: "Delete label" (string resource: `R.string.delete_label`)
- Icon padding: 4dp right
- Container padding: 16dp horizontal, 8dp vertical

### Delete Flow with Undo

1. **User clicks "Delete Label" button**
   - Local state `pendingDeleteLabel` set to current label name
   - Cancel any existing delete operation
   - Snackbar shown immediately:
     - Message: "Label {label name} deleted" (string resource: `R.string.label_deleted` with format)
     - Action: "UNDO" button
     - Duration: Long (10 seconds)
   - Schedule actual deletion after 10 seconds

2. **During 10-second window:**
   - User can click "UNDO" to cancel deletion
   - If undo clicked:
     - Cancel scheduled deletion job
     - Clear `pendingDeleteLabel`
     - Snackbar dismisses
     - Label remains unchanged

3. **After 10 seconds (if not undone):**
   - Check if `pendingDeleteLabel` still matches current label
   - Call `viewModel.onDeleteLabel(label)`
   - Clear `pendingDeleteLabel`

4. **Delete Operation:**
   - ViewModel calls `bookmarkRepository.deleteLabel(label)`
   - Repository finds all bookmarks with the label
   - Each bookmark updated to remove label (locally and via API)
   - API: PATCH /bookmarks/{id} with `removeLabels: [label]`
   - After success, navigate to labels list view
   - Labels list auto-refreshes (deleted label removed)

### Implementation Details

**Local State in BookmarkListScreen:**
```kotlin
var pendingDeleteLabel by remember { mutableStateOf<String?>(null) }
var deleteLabelJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
```

**Delete Button UI:**
```kotlin
if (filterState.value.label != null && !isEditingLabel) {
    val labelDeletedMessageFormat = stringResource(R.string.label_deleted)
    val currentLabel = filterState.value.label!!

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        OutlinedButton(
            onClick = {
                // Cancel any existing delete operation
                deleteLabelJob?.cancel()

                // Set pending delete
                pendingDeleteLabel = currentLabel

                // Show snackbar with undo option
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = labelDeletedMessageFormat.format(currentLabel),
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Long
                    )

                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        // User clicked undo, cancel the deletion
                        deleteLabelJob?.cancel()
                        pendingDeleteLabel = null
                    }
                }

                // Schedule the actual deletion after 10 seconds
                deleteLabelJob = scope.launch {
                    kotlinx.coroutines.delay(10000)
                    if (pendingDeleteLabel == currentLabel) {
                        viewModel.onDeleteLabel(currentLabel)
                        pendingDeleteLabel = null
                    }
                }
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            shape = RectangleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(stringResource(id = R.string.delete_label))
        }
    }
}
```

**ViewModel Handler:**
```kotlin
fun onDeleteLabel(label: String) {
    viewModelScope.launch {
        try {
            when (bookmarkRepository.deleteLabel(label)) {
                is BookmarkRepository.UpdateResult.Success -> {
                    // Navigate back to labels list page
                    _filterState.value = FilterState(viewingLabelsList = true)
                    // Labels will auto-refresh via Flow
                }
                is BookmarkRepository.UpdateResult.Error,
                is BookmarkRepository.UpdateResult.NetworkError -> {
                    Timber.e("Failed to delete label")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting label")
        }
    }
}
```

---

## Repository Implementation

### Rename Label

**BookmarkRepositoryImpl.kt:**
```kotlin
override suspend fun renameLabel(oldLabel: String, newLabel: String): BookmarkRepository.UpdateResult =
    withContext(dispatcher) {
        try {
            // Get all bookmarks with the old label
            val bookmarksWithLabel = bookmarkDao.getAllBookmarksWithContent()
                .filter { bookmark ->
                    bookmark.bookmark.labels.contains(oldLabel)
                }

            // Update each bookmark by replacing the old label with the new one
            for (bookmarkWithContent in bookmarksWithLabel) {
                val bookmark = bookmarkWithContent.bookmark
                val updatedLabels = bookmark.labels.map { label ->
                    if (label == oldLabel) newLabel else label
                }

                // Update locally
                val updatedBookmark = bookmark.copy(labels = updatedLabels)
                bookmarkDao.insertBookmark(updatedBookmark)

                // Update on server - use addLabels and removeLabels
                val response = readeckApi.editBookmark(
                    id = bookmark.id,
                    body = EditBookmarkDto(
                        addLabels = listOf(newLabel),
                        removeLabels = listOf(oldLabel)
                    )
                )

                if (!response.isSuccessful) {
                    return@withContext BookmarkRepository.UpdateResult.Error(
                        errorMessage = "Failed to rename label on server",
                        code = response.code()
                    )
                }
            }

            BookmarkRepository.UpdateResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Error renaming label")
            BookmarkRepository.UpdateResult.NetworkError(
                errorMessage = "Network error while renaming label",
                ex = e
            )
        }
    }
```

### Delete Label

**BookmarkRepositoryImpl.kt:**
```kotlin
override suspend fun deleteLabel(label: String): BookmarkRepository.UpdateResult =
    withContext(dispatcher) {
        try {
            // Get all bookmarks with the label
            val bookmarksWithLabel = bookmarkDao.getAllBookmarksWithContent()
                .filter { bookmark ->
                    bookmark.bookmark.labels.contains(label)
                }

            // Update each bookmark by removing the label
            for (bookmarkWithContent in bookmarksWithLabel) {
                val bookmark = bookmarkWithContent.bookmark
                val updatedLabels = bookmark.labels.filter { it != label }

                // Update locally
                val updatedBookmark = bookmark.copy(labels = updatedLabels)
                bookmarkDao.insertBookmark(updatedBookmark)

                // Update on server - use removeLabels
                val response = readeckApi.editBookmark(
                    id = bookmark.id,
                    body = EditBookmarkDto(
                        removeLabels = listOf(label)
                    )
                )

                if (!response.isSuccessful) {
                    return@withContext BookmarkRepository.UpdateResult.Error(
                        errorMessage = "Failed to delete label on server",
                        code = response.code()
                    )
                }
            }

            BookmarkRepository.UpdateResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Error deleting label")
            BookmarkRepository.UpdateResult.NetworkError(
                errorMessage = "Network error while deleting label",
                ex = e
            )
        }
    }
```

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `labels` | "Labels" | TopAppBar title prefix |
| `edit_label` | "Edit label" | Edit icon content description |
| `delete_label` | "Delete label" | Delete button text |
| `label_deleted` | "Label %s deleted" | Snackbar message (format string) |

---

## Data Flow

### Filter by Label
1. User clicks label chip in bookmark card
2. `onClickLabel(label)` called
3. ViewModel sets `FilterState(label = label)`
4. Repository filters bookmarks by label
5. UI updates to show filtered list
6. TopAppBar shows "Labels / {label}"

### Rename Label
1. User clicks Edit icon → enters edit mode
2. User modifies label name in TextField
3. User clicks Check icon
4. If changed: `onRenameLabel(oldLabel, newLabel)` called
5. Repository updates all bookmarks with old label
6. Each bookmark updated locally and remotely
7. If still viewing the label, filter updates to new name
8. Labels Flow updates, UI reflects changes

### Delete Label
1. User clicks "Delete Label" button
2. Snackbar shown with UNDO (10-second timer starts)
3. User can cancel by clicking UNDO
4. After 10 seconds (if not undone): `onDeleteLabel(label)` called
5. Repository updates all bookmarks to remove label
6. Each bookmark updated locally and remotely
7. Navigate to labels list view
8. Labels Flow updates, deleted label removed

---

## Key Behaviors

1. **Clickable Chips:** All label chips in bookmark cards are clickable, filtering to that label
2. **Edit Mode Toggle:** Edit icon and Check icon toggle state locally (not in ViewModel)
3. **Rename Validation:** Only renames if name changed AND not blank
4. **Filter Update:** After rename, if viewing the label, filter automatically updates to new name
5. **Delete Timer:** 10-second undo window implemented with coroutine delay
6. **Job Cancellation:** Clicking delete again cancels previous delete operation
7. **Navigation After Delete:** Always navigates to labels list view after successful deletion
8. **Atomic Updates:** Each bookmark updated individually (no batch operation)
9. **Optimistic Local:** Local database updated immediately, API called after
10. **Error Handling:** Errors logged but not shown to user (silent failure)

---

## Testing Considerations

**Test Cases:**
1. Click label chip in bookmark card, verify list filters
2. Enter edit mode, change label name, save
3. Verify all bookmarks with old label now show new label
4. Enter edit mode, cancel without saving (click back/menu)
5. Click delete, then undo before 10 seconds
6. Click delete, wait 10 seconds, verify label removed
7. Click delete, then delete again (verify previous job cancelled)
8. Rename label to same name (should be no-op)
9. Rename label to empty string (should not save)
10. Delete label, verify navigation to labels list
11. Verify API calls for rename (addLabels + removeLabels)
12. Verify API calls for delete (removeLabels only)

---

## Implementation Notes for Refactor

1. **Local UI State:** Edit mode and delete timer state managed locally in composable, not ViewModel
2. **Coroutine Jobs:** Delete operation uses `Job` reference for cancellation capability
3. **Format String:** Delete message uses string format with `%s` placeholder
4. **Filter State Update:** After rename, ViewModel checks if current filter matches old label
5. **Navigation Side Effect:** Delete success causes automatic navigation to labels view
6. **Error Display:** Current implementation logs errors but doesn't show them to user
7. **Sequential Updates:** Bookmarks updated one-by-one in loop, not batched
8. **No Confirmation Dialog:** Delete uses Snackbar + undo instead of AlertDialog
