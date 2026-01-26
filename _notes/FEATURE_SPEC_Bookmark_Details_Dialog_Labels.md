# Feature Spec: Bookmark Details Dialog with Labels

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

A full-screen dialog displaying bookmark metadata (type, language, word count, reading time, authors, description) with an interactive labels section that allows users to add and remove labels.

---

## User-Facing Behavior

### Access Point
- From BookmarkDetailScreen (reading view)
- Accessed via UI control (button/menu item) in detail screen
- Opens as full-screen dialog overlay

### Dialog Layout

**TopAppBar:**
- **Title:** "Bookmark Details" (string resource: `R.string.detail_dialog_title`)
- **Navigation Icon:** Back arrow (`Icons.AutoMirrored.Filled.ArrowBack`)
- **Content Description:** "Back" (string resource: `R.string.back`)
- **Action:** Dismisses dialog

**Content Area (Scrollable):**
1. **Metadata Fields** - Read-only information
2. **Labels Section** - Interactive label management

**Padding:**
- Content: 24dp all sides (inside scrollable area)
- Vertical spacing: 16dp between sections

### Metadata Fields

Displayed in order (each field shown only if data exists):

| Field | Display Condition | Format |
|-------|------------------|---------|
| Type | Always shown | "Article", "Photo", or "Video" |
| Language | If not null/blank and != "Unknown" | Language name |
| Word Count | If > 0 | "{count} words" |
| Reading Time | If > 0 | "{time} min" (abbreviated) |
| Authors | If list not empty | Comma-separated names |
| Description | If not null/blank | Full description text |

**Field Styling:**
- Label: `MaterialTheme.typography.labelMedium` with `onSurfaceVariant` color
- Value: `MaterialTheme.typography.bodyMedium` with `onSurface` color
- Spacing: 4dp between label and value
- Vertical spacing: 16dp between fields

### Labels Section

**Section Header:**
- Text: "Labels" (string resource: `R.string.detail_labels`)
- Typography: `MaterialTheme.typography.labelMedium`
- Color: `MaterialTheme.colorScheme.onSurfaceVariant`

**Existing Labels Display:**
- FlowRow of label chips (if any labels exist)
- Spacing: 8dp horizontal, 8dp vertical between chips
- Chips wrap to multiple rows if needed

**Label Chip Design:**
```
+---------------------------+
| Label Name    [X]         |
+---------------------------+
```
- Card with rounded corners (16dp)
- Padding: 12dp horizontal, 8dp vertical (inner content)
- Card padding: 4dp (outer)
- Label text: `MaterialTheme.typography.labelSmall`
- Close icon: 16dp × 16dp
- Icon button: 20dp × 20dp

**New Label Input Field:**
- OutlinedTextField
- Placeholder: "Add labels (comma-separated)" (string resource: `R.string.detail_label_placeholder`)
- Single line input
- Typography: `MaterialTheme.typography.bodySmall`
- IME Action: Done
- Full width

### User Interactions

**Adding Labels:**
1. User types label(s) in input field
2. Can enter single label or comma-separated labels
3. Press "Done" on keyboard
4. Labels parsed (split by comma, trimmed, duplicates ignored)
5. Valid labels added as chips above input field
6. Input field cleared
7. Keyboard hides automatically
8. Labels immediately saved to server via callback

**Removing Labels:**
1. Click X icon on any label chip
2. Label removed from list immediately
3. Labels saved to server via callback
4. No confirmation required

**Closing Dialog:**
1. Click back arrow in TopAppBar
2. Dialog dismisses
3. All changes already saved (no "Save" button needed)

### Label Validation Rules
- Labels trimmed of whitespace
- Empty labels (blank or whitespace-only) ignored
- Duplicate labels prevented (case-sensitive)
- No character limit enforced
- Commas serve as delimiters and are stripped

---

## Implementation Details

### File Location
`/app/src/main/java/de/readeckapp/ui/detail/BookmarkDetailsDialog.kt`

### Component Structure

**BookmarkDetailsDialog Composable:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailsDialog(
    bookmark: BookmarkDetailViewModel.Bookmark,
    onDismissRequest: () -> Unit,
    onLabelsUpdate: (List<String>) -> Unit = {}
)
```

**Parameters:**
- `bookmark`: Full bookmark data including metadata and labels
- `onDismissRequest`: Callback to close dialog
- `onLabelsUpdate`: Callback when labels change (called immediately on add/remove)

### Local State Management

```kotlin
var labels by remember { mutableStateOf(bookmark.labels.toMutableList()) }
var newLabelInput by remember { mutableStateOf("") }
val keyboardController = LocalSoftwareKeyboardController.current
```

**State Variables:**
- `labels`: Mutable list of current labels (initialized from bookmark)
- `newLabelInput`: Current text in input field
- `keyboardController`: Controller to hide keyboard after adding labels

### Dialog Implementation

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.detail_dialog_title)) },
            navigationIcon = {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        )
    }
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Metadata fields...

        // Labels Section
        LabelsSection(
            labels = labels,
            newLabelInput = newLabelInput,
            onNewLabelChange = { newLabelInput = it },
            onAddLabel = {
                if (newLabelInput.isNotBlank()) {
                    val newLabels = newLabelInput.split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !labels.contains(it) }

                    labels.addAll(newLabels)
                    newLabelInput = ""
                    keyboardController?.hide()
                    if (newLabels.isNotEmpty()) {
                        onLabelsUpdate(labels)
                    }
                }
            },
            onRemoveLabel = { label ->
                labels.remove(label)
                onLabelsUpdate(labels)
            }
        )
    }
}
```

### MetadataField Component

```kotlin
@Composable
private fun MetadataField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

### LabelsSection Component

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsSection(
    labels: List<String>,
    newLabelInput: String,
    onNewLabelChange: (String) -> Unit,
    onAddLabel: () -> Unit,
    onRemoveLabel: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_labels),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Existing labels
        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEach { label ->
                    LabelChip(
                        label = label,
                        onRemove = { onRemoveLabel(label) }
                    )
                }
            }
        }

        // Input field for new label
        OutlinedTextField(
            value = newLabelInput,
            onValueChange = onNewLabelChange,
            placeholder = { Text(stringResource(R.string.detail_label_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onAddLabel() }
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
```

### LabelChip Component

```kotlin
@Composable
private fun LabelChip(
    label: String,
    onRemove: () -> Unit = {}
) {
    Card(
        modifier = Modifier.padding(4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove label",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
```

---

## Integration with BookmarkDetailScreen

**Opening Dialog:**
```kotlin
var showDetailsDialog by remember { mutableStateOf(false) }

if (showDetailsDialog) {
    BookmarkDetailsDialog(
        bookmark = uiState.bookmark,
        onDismissRequest = { showDetailsDialog = false },
        onLabelsUpdate = { newLabels ->
            onUpdateLabels(uiState.bookmark.bookmarkId, newLabels)
        }
    )
} else {
    // Normal detail view
}
```

**Event Handler in BookmarkDetailScreen:**
```kotlin
// Passed to BookmarkDetailScreen composable
val onUpdateLabels: (String, List<String>) -> Unit
```

---

## ViewModel Integration

**BookmarkDetailViewModel.kt:**

```kotlin
fun onUpdateLabels(bookmarkId: String, labels: List<String>) {
    updateBookmark {
        updateBookmarkUseCase.updateLabels(
            bookmarkId = bookmarkId,
            labels = labels
        )
    }
}

private fun updateBookmark(update: suspend () -> UpdateBookmarkUseCase.Result) {
    viewModelScope.launch {
        val state = when (val result = update()) {
            is UpdateBookmarkUseCase.Result.Success -> UpdateBookmarkState.Success
            is UpdateBookmarkUseCase.Result.Error -> UpdateBookmarkState.Error(result.message)
        }
        _updateBookmarkState.value = state
    }
}
```

**Use Case Integration:**
- `UpdateBookmarkUseCase.updateLabels(bookmarkId, labels)`
- Calls repository method `bookmarkRepository.updateLabels(bookmarkId, labels)`
- Repository implementation calculates added/removed labels (see previous specs)

---

## Data Model

**BookmarkDetailViewModel.Bookmark:**
```kotlin
data class Bookmark(
    val bookmarkId: String,
    val url: String,
    val title: String,
    val type: Type,
    val lang: String?,
    val wordCount: Int?,
    val readingTime: Int?,
    val authors: List<String>,
    val description: String?,
    val labels: List<String>,
    // ... other fields
) {
    enum class Type {
        ARTICLE, PHOTO, VIDEO
    }
}
```

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `detail_dialog_title` | "Bookmark Details" | Dialog TopAppBar title |
| `back` | "Back" | Back button content description |
| `detail_type` | "Type" | Bookmark type field label |
| `detail_language` | "Language" | Language field label |
| `detail_word_count` | "Word Count" | Word count field label |
| `detail_reading_time` | "Reading Time" | Reading time field label |
| `detail_minutes_short` | "min" | Abbreviated minutes |
| `detail_labels` | "Labels" | Labels section header |
| `detail_label_placeholder` | "Add labels (comma-separated)" | Input field placeholder |

---

## Key Behaviors

1. **Full-Screen Dialog:** Uses Scaffold with TopAppBar for full-screen presentation
2. **Immediate Save:** Labels saved immediately on add/remove (no "Save" button)
3. **Local State First:** Local labels list updated first, then callback fires
4. **Mutable List:** Uses `toMutableList()` to create local copy for editing
5. **Keyboard Hiding:** Keyboard auto-hides after adding labels via Done action
6. **Conditional Metadata:** Only shows metadata fields if data exists
7. **Scrollable Content:** Entire content area scrollable for long metadata/many labels
8. **No Empty Check:** Labels update callback called even if newLabels list is empty (important for remove operation)

---

## Data Flow

1. **Dialog Opens:**
   - Bookmark data passed as parameter
   - Local `labels` state initialized from `bookmark.labels`

2. **User Adds Labels:**
   - Input parsed (split, trim, filter)
   - New labels added to local state
   - `onLabelsUpdate(labels)` callback fired with full label list
   - Callback triggers ViewModel → UseCase → Repository → API
   - Keyboard hides

3. **User Removes Label:**
   - Label removed from local state
   - `onLabelsUpdate(labels)` callback fired with updated list
   - Callback triggers same update flow

4. **User Closes Dialog:**
   - Back arrow clicked
   - `onDismissRequest()` callback fires
   - Dialog state variable set to false in parent
   - Dialog dismissed

5. **Update Flow:**
   - ViewModel `onUpdateLabels()` called
   - Use case calculates diff (added/removed labels)
   - Repository updates bookmark locally and remotely
   - Bookmark data refreshes via Flow
   - UI reflects changes

---

## Testing Considerations

**Test Cases:**
1. Open dialog, verify all metadata fields display correctly
2. Add single label via Done button
3. Add multiple comma-separated labels
4. Remove label via X button
5. Verify labels update immediately (no save button needed)
6. Add label with leading/trailing spaces (should trim)
7. Attempt duplicate label (should be ignored)
8. Close dialog via back arrow
9. Verify keyboard hides after adding labels
10. Verify scrolling works with long content
11. Test with bookmark missing optional metadata
12. Test type display names (Article, Photo, Video)

---

## Implementation Notes for Refactor

1. **Full-Screen Dialog:** Not using AlertDialog - uses Scaffold for full-screen presentation
2. **Mutable State:** Uses `toMutableList()` to create local copy, allows `add()`/`remove()` operations
3. **Immediate Callbacks:** Every label change triggers `onLabelsUpdate()` immediately
4. **No Diff Calculation:** Dialog passes full label list; diff calculated in repository layer
5. **Conditional Rendering:** Each metadata field checks if data exists before rendering
6. **Type Display:** Uses extension function for type enum to display name conversion
7. **FlowRow Layout:** Requires `@OptIn(ExperimentalLayoutApi::class)` annotation
8. **Keyboard Controller:** Uses `LocalSoftwareKeyboardController` from Compose UI
9. **Scrollable Column:** Uses `verticalScroll(rememberScrollState())` for scrolling
10. **Label Chip Reuse:** Same LabelChip component as CreateBookmarkDialog (could be shared)
