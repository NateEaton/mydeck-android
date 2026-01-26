# Feature Spec: Adding Labels When Adding a URL

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

Users can add one or more labels (tags) to a bookmark directly within the "Add New Bookmark" dialog. Labels are entered via a text field that supports comma-separated input, with each label displayed as a removable chip.

---

## User-Facing Behavior

### Dialog Access
- **Entry Point:** Floating Action Button (+) on BookmarkListScreen
- **Dialog Type:** AlertDialog with form fields
- **Dialog Title:** "Add new bookmark" (string resource: `R.string.add_new_bookmark`)

### Dialog Layout (Top to Bottom)
1. **URL Field** - Required, validated, shows error if invalid
2. **Title Field** - Optional
3. **Labels Section** - Optional, expandable with chips

### Labels Section UI

**Section Header:**
- Text: "Labels" (string resource: `R.string.detail_labels`)
- Typography: `MaterialTheme.typography.labelMedium`
- Color: `MaterialTheme.colorScheme.onSurfaceVariant`

**Existing Labels Display:**
- FlowRow of label chips (if any labels added)
- Each chip shows label text + remove (X) icon
- Chips wrap to multiple rows if needed
- Spacing: 8dp horizontal, 8dp vertical between chips

**New Label Input Field:**
- OutlinedTextField
- Placeholder: "Add labels (comma-separated)" (string resource: `R.string.detail_label_placeholder`)
- Single line input
- IME Action: Done
- On Done action: Adds labels from input

**Label Chip Design:**
```
+---------------------------+
| Label Name    [X]         |
+---------------------------+
```
- Card with rounded corners (16dp)
- Padding: 12dp horizontal, 8dp vertical
- Label text: `MaterialTheme.typography.labelSmall`
- Close icon: 16dp × 16dp
- Icon button: 20dp × 20dp

### User Interactions

**Adding Labels:**
1. User types label(s) in input field
2. Can enter single label or multiple comma-separated labels
3. Press "Done" on keyboard or click "Create" button
4. Labels are parsed (split by comma, trimmed, duplicates ignored)
5. Valid labels added as chips above input field
6. Input field cleared

**Removing Labels:**
1. Click X icon on any label chip
2. Label immediately removed from list
3. No confirmation required

**Creating Bookmark:**
1. Click "Create" button
2. If input field has unparsed labels, they are processed first
3. Bookmark created with URL, title, and all labels
4. Dialog closes on success

### Label Validation Rules
- Labels are trimmed of whitespace
- Empty labels (blank or whitespace-only) are ignored
- Duplicate labels are prevented (case-sensitive comparison)
- No character limit enforced
- Commas serve as delimiters and are stripped

---

## Implementation Details

### File Locations

**UI Components:**
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListScreen.kt`
  - `CreateBookmarkDialog()` composable (lines 743-835)
  - `CreateBookmarkLabelsSection()` composable (lines 837-888)
  - `LabelChip()` composable (lines 890-922)

**ViewModel:**
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListViewModel.kt`
  - `CreateBookmarkUiState` sealed class
  - Label state management functions

### State Management

**ViewModel State:**
```kotlin
sealed class CreateBookmarkUiState {
    data object Closed : CreateBookmarkUiState()

    data class Open(
        val title: String,
        val url: String,
        val urlError: Int?,        // Resource ID for error message
        val isCreateEnabled: Boolean,
        val labels: List<String>   // List of labels
    ) : CreateBookmarkUiState()

    data object Loading : CreateBookmarkUiState()
    data object Success : CreateBookmarkUiState()
    data class Error(val message: String) : CreateBookmarkUiState()
}

private val _createBookmarkUiState = MutableStateFlow<CreateBookmarkUiState>(CreateBookmarkUiState.Closed)
val createBookmarkUiState: StateFlow<CreateBookmarkUiState> = _createBookmarkUiState.asStateFlow()
```

**Key Functions:**
```kotlin
fun updateCreateBookmarkLabels(labels: List<String>) {
    _createBookmarkUiState.update {
        (it as? CreateBookmarkUiState.Open)?.copy(
            labels = labels
        ) ?: it
    }
}

fun createBookmark() {
    viewModelScope.launch {
        val url = (_createBookmarkUiState.value as CreateBookmarkUiState.Open).url
        val title = (_createBookmarkUiState.value as CreateBookmarkUiState.Open).title
        val labels = (_createBookmarkUiState.value as CreateBookmarkUiState.Open).labels

        _createBookmarkUiState.value = CreateBookmarkUiState.Loading
        try {
            bookmarkRepository.createBookmark(title = title, url = url, labels = labels)
            _createBookmarkUiState.value = CreateBookmarkUiState.Success
        } catch (e: Exception) {
            _createBookmarkUiState.value = CreateBookmarkUiState.Error(e.message ?: "Unknown error")
        }
    }
}
```

### CreateBookmarkDialog Implementation

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBookmarkDialog(
    onDismiss: () -> Unit,
    title: String,
    url: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit
) {
    var newLabelInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.add_new_bookmark)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // URL field
                OutlinedTextField(
                    value = url,
                    onValueChange = { onUrlChange(it) },
                    isError = urlError != null,
                    label = { Text(stringResource(id = R.string.url)) },
                    supportingText = {
                        urlError?.let { Text(text = stringResource(it)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it) },
                    label = { Text(stringResource(id = R.string.title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Labels Section
                CreateBookmarkLabelsSection(
                    labels = labels,
                    newLabelInput = newLabelInput,
                    onNewLabelChange = { newLabelInput = it },
                    onAddLabel = {
                        if (newLabelInput.isNotBlank()) {
                            // Split on commas and trim each label
                            val newLabels = newLabelInput.split(',')
                                .map { it.trim() }
                                .filter { it.isNotBlank() && !labels.contains(it) }

                            if (newLabels.isNotEmpty()) {
                                onLabelsChange(labels + newLabels)
                            }
                            newLabelInput = ""
                            keyboardController?.hide()
                        }
                    },
                    onRemoveLabel = { label ->
                        onLabelsChange(labels.filter { it != label })
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Process any pending label input before creating
                    if (newLabelInput.isNotBlank()) {
                        val newLabels = newLabelInput.split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() && !labels.contains(it) }

                        if (newLabels.isNotEmpty()) {
                            onLabelsChange(labels + newLabels)
                        }
                    }
                    onCreateBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
```

### CreateBookmarkLabelsSection Implementation

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBookmarkLabelsSection(
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

### LabelChip Implementation

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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

## API Integration

**Repository Method:**
```kotlin
suspend fun createBookmark(
    title: String,
    url: String,
    labels: List<String>
): String
```

**REST API Endpoint:**
- Method: `POST /bookmarks`
- Request Body (CreateBookmarkDto):
  ```json
  {
    "url": "https://example.com",
    "title": "Example Article",
    "labels": ["tech", "tutorial", "kotlin"]
  }
  ```
- Response: Returns bookmark ID in header `X-Bookmark-Id`

**Implementation in BookmarkRepositoryImpl:**
```kotlin
override suspend fun createBookmark(title: String, url: String, labels: List<String>): String {
    val createBookmarkDto = CreateBookmarkDto(labels = labels, title = title, url = url)
    val response = readeckApi.createBookmark(createBookmarkDto)
    if (response.isSuccessful) {
        return response.headers()[ReadeckApi.Header.BOOKMARK_ID]!!
    } else {
        throw Exception("Failed to create bookmark")
    }
}
```

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `add_new_bookmark` | "Add new bookmark" | Dialog title |
| `url` | "URL" | URL field label |
| `title` | "Title" | Title field label |
| `detail_labels` | "Labels" | Labels section header |
| `detail_label_placeholder` | "Add labels (comma-separated)" | Input field placeholder |
| `create` | "Create" | Confirm button |
| `cancel` | "Cancel" | Dismiss button |
| `account_settings_url_error` | "Invalid URL" | URL validation error |

---

## Data Flow

1. **User opens dialog:** ViewModel sets `_createBookmarkUiState` to `Open` with empty fields
2. **User enters URL:** `updateCreateBookmarkUrl()` validates and updates state
3. **User enters title:** `updateCreateBookmarkTitle()` updates state
4. **User types labels:** Local state in `CreateBookmarkDialog` tracks input
5. **User presses Done/comma:** Labels parsed and added via `updateCreateBookmarkLabels()`
6. **User removes label:** `updateCreateBookmarkLabels()` with filtered list
7. **User clicks Create:**
   - Pending labels in input field are processed first
   - `createBookmark()` called with URL, title, labels
   - State transitions: `Open` → `Loading` → `Success` or `Error`
8. **On success:** Dialog auto-closes after 1 second delay
9. **On error:** Shows error dialog with message

---

## Key Behaviors

1. **Comma-Separated Input:** Users can enter multiple labels at once: "tech, kotlin, android"
2. **Auto-Trim:** Leading/trailing whitespace automatically removed from each label
3. **Duplicate Prevention:** Same label can't be added twice (case-sensitive)
4. **Pending Input Handling:** When user clicks Create, any unparsed input is processed
5. **Keyboard Hiding:** Keyboard auto-hides when labels are added via Done action
6. **No Empty Labels:** Blank labels filtered out during parsing
7. **Visual Feedback:** Labels immediately appear as chips when added

---

## Testing Considerations

**Test Cases:**
1. Add single label via Done button
2. Add multiple comma-separated labels at once
3. Add label via Create button (with pending input)
4. Remove label via X button
5. Attempt to add duplicate label (should be ignored)
6. Add label with leading/trailing spaces (should be trimmed)
7. Add blank label (should be ignored)
8. Create bookmark with no labels
9. Create bookmark with multiple labels
10. Verify labels sent to API correctly

---

## Implementation Notes for Refactor

1. **Local UI State:** The `newLabelInput` is managed locally in the dialog composable, not in ViewModel
2. **Label Parsing:** Split by comma, trim, filter blanks, check duplicates - happens in dialog, not ViewModel
3. **Keyboard Controller:** Uses `LocalSoftwareKeyboardController` to hide keyboard after adding labels
4. **FlowRow Layout:** Requires `@OptIn(ExperimentalLayoutApi::class)` annotation
5. **Pending Input:** Create button processes any unparsed input before calling ViewModel
6. **State Updates:** Use `_createBookmarkUiState.update { }` with smart casting for type safety
7. **Success Delay:** 1-second delay before closing dialog on success (shows loading state briefly)
