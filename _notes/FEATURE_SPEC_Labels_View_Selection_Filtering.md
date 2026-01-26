# Feature Spec: Labels View with Selection and Filtering

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

A dedicated view that displays all labels (tags) used across bookmarks with their bookmark counts. Users can browse all labels and tap any label to filter the bookmark list to show only bookmarks with that label.

---

## User-Facing Behavior

### Access Points

**Navigation Drawer Item:**
- **Icon:** Label icon (`Icons.Outlined.Label`)
- **Label:** "Labels" (string resource: `R.string.labels`)
- **Badge:** Count of total labels (if labels exist)
- **Selected State:** Highlighted when viewing labels list OR when a specific label is filtered

### Labels View Layout

**TopAppBar:**
- **Title:** "Bookmark labels" (string resource: `R.string.bookmark_labels`)
- **Navigation Icon:** Menu icon (☰) - opens navigation drawer
- **No Action Icons** (no search when viewing labels list)

**Content Area:**

**Description Text (Header):**
- Text: "Select a label to view bookmarks" (string resource: `R.string.labels_description`)
- Typography: `MaterialTheme.typography.bodyMedium`
- Color: `MaterialTheme.colorScheme.onSurfaceVariant`
- Padding: 16dp horizontal, 12dp vertical

**Labels List:**
- Each label displayed as a NavigationDrawerItem
- Labels sorted alphabetically
- Each item shows:
  - Label name (left-aligned)
  - Badge with bookmark count (right-aligned)
- Items have border outline for visual separation
- Clicking any label navigates to filtered bookmark list

**Empty State:**
- Message: "Nothing to see" (string resource: `R.string.list_view_empty_nothing_to_see`)
- Centered in view
- Shown when no labels exist

### Visual Design

**Label Item Structure:**
```
+--------------------------------------------------+
| Label Name                            [Badge: 5] |
+--------------------------------------------------+
```

**Item Styling:**
- Component: `NavigationDrawerItem`
- Border: 1dp outline with `MaterialTheme.colorScheme.outlineVariant`
- Shape: `MaterialTheme.shapes.medium` (rounded corners)
- Padding: 8dp horizontal, 4dp vertical (outer)
- Inner padding: 16dp horizontal
- Badge background: `MaterialTheme.colorScheme.surfaceVariant`

### Interaction Flow

1. **User clicks "Labels" in navigation drawer**
   - Navigation drawer closes
   - Labels view replaces bookmark list
   - TopAppBar title changes to "Bookmark labels"

2. **User browses labels**
   - Scrollable list shows all labels alphabetically
   - Each label shows count of bookmarks using it

3. **User clicks a label**
   - View switches to bookmark list
   - List filtered to show only bookmarks with that label
   - TopAppBar title changes to "Labels / {label name}"

---

## Implementation Details

### File Locations

**UI Component:**
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListScreen.kt`
  - `LabelsListView()` composable (lines 973-1040)
  - Navigation drawer Labels item (lines 376-396)
  - TopAppBar title handling (lines 446-448)

**ViewModel:**
- `/app/src/main/java/de/readeckapp/ui/list/BookmarkListViewModel.kt`
  - `FilterState` data class with `viewingLabelsList` flag
  - `labelsWithCounts` StateFlow
  - `onClickLabelsView()` function

### State Management

**FilterState Data Class:**
```kotlin
data class FilterState(
    val type: Bookmark.Type? = null,
    val unread: Boolean? = null,
    val archived: Boolean? = null,
    val favorite: Boolean? = null,
    val label: String? = null,
    val viewingLabelsList: Boolean = false
)

private val _filterState = MutableStateFlow(FilterState(unread = true))
val filterState: StateFlow<FilterState> = _filterState.asStateFlow()
```

**Labels with Counts Flow:**
```kotlin
val labelsWithCounts: StateFlow<Map<String, Int>> =
    bookmarkRepository.observeAllLabelsWithCounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
```

**Key Functions:**
```kotlin
fun onClickLabelsView() {
    Timber.d("onClickLabelsView")
    setLabelsListView()
}

private fun setLabelsListView() {
    // Show the labels list view
    _filterState.value = FilterState(viewingLabelsList = true)
}

fun onClickLabel(label: String) {
    Timber.d("onClickLabel: $label")
    setLabelFilter(label)
}

private fun setLabelFilter(label: String?) {
    // Clear all other filters when selecting a label filter
    _filterState.value = FilterState(label = label)
}
```

### LabelsListView Implementation

```kotlin
@Composable
fun LabelsListView(
    modifier: Modifier = Modifier,
    labels: Map<String, Int>,
    onLabelSelected: (String) -> Unit
) {
    if (labels.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.list_view_empty_nothing_to_see),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth()
        ) {
            item {
                Text(
                    text = stringResource(R.string.labels_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(
                items = labels.entries.sortedBy { it.key }.toList(),
                key = { it.key }
            ) { (label, count) ->
                NavigationDrawerItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.medium
                        ),
                    label = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label)
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(count.toString())
                            }
                        }
                    },
                    selected = false,
                    onClick = {
                        onLabelSelected(label)
                    }
                )
            }
        }
    }
}
```

### Navigation Drawer Integration

```kotlin
NavigationDrawerItem(
    label = { Text(
        style = Typography.labelLarge,
        text = stringResource(id = R.string.labels)
    ) },
    icon = { Icon(Icons.Outlined.Label, contentDescription = null) },
    badge = {
        if (labelsWithCounts.value.isNotEmpty()) {
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = labelsWithCounts.value.size.toString()
                )
            }
        }
    },
    selected = filterState.value.viewingLabelsList || filterState.value.label != null,
    onClick = {
        viewModel.onClickLabelsView()
        scope.launch { drawerState.close() }
    }
)
```

### TopAppBar Title Logic

```kotlin
topBar = {
    TopAppBar(
        title = {
            if (isSearchActive.value) {
                // Search TextField
            } else {
                if (filterState.value.viewingLabelsList) {
                    Text(stringResource(id = R.string.bookmark_labels))
                } else if (filterState.value.label != null) {
                    // Show "Labels / {label name}"
                } else {
                    // Show filter-based title (All, Unread, Archive, etc.)
                }
            }
        },
        // ...
    )
}
```

### Main Content Switching

```kotlin
Scaffold(/*...*/) { padding ->
    Column(modifier = Modifier.padding(padding)) {
        // Show labels list if viewing labels, otherwise show bookmarks list
        if (filterState.value.viewingLabelsList) {
            LabelsListView(
                labels = labelsWithCounts.value,
                onLabelSelected = { label ->
                    onClickLabel(label)
                }
            )
        } else {
            PullToRefreshBox(/*...*/) {
                BookmarkListView(/*...*/)
            }
        }
    }
}
```

---

## Repository Implementation

**BookmarkRepository.kt Interface:**
```kotlin
fun observeAllLabelsWithCounts(): Flow<Map<String, Int>>
```

**BookmarkRepositoryImpl.kt:**
```kotlin
override fun observeAllLabelsWithCounts(): Flow<Map<String, Int>> =
    bookmarkDao.observeAllLabels().map { labelsStringList ->
        val labelCounts = mutableMapOf<String, Int>()

        // Parse each labels string and count occurrences
        for (labelsString in labelsStringList) {
            if (labelsString.isNotEmpty()) {
                // Split by comma to get individual labels
                val labels = labelsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (label in labels) {
                    labelCounts[label] = (labelCounts[label] ?: 0) + 1
                }
            }
        }

        labelCounts.toMap()
    }
```

**BookmarkDao.kt:**
```kotlin
@Query("""
    SELECT labels FROM bookmarks WHERE state = 0 AND labels != '' AND labels IS NOT NULL
""")
fun observeAllLabels(): Flow<List<String>>
```

---

## Data Flow

1. **DAO** observes all bookmark labels (comma-separated strings)
2. **Repository** parses label strings and counts occurrences
   - Splits by comma
   - Trims whitespace
   - Counts each label across all bookmarks
3. **ViewModel** exposes as StateFlow via `stateIn` operator
4. **UI** observes via `collectAsState()` and renders list
5. **User clicks label** → ViewModel updates FilterState
6. **UI reacts** to FilterState change and switches view

---

## String Resources Required

| String ID | English Text | Context |
|-----------|--------------|---------|
| `labels` | "Labels" | Navigation drawer item, TopAppBar prefix |
| `bookmark_labels` | "Bookmark labels" | TopAppBar title for labels view |
| `labels_description` | "Select a label to view bookmarks" | Description at top of labels list |
| `list_view_empty_nothing_to_see` | "Nothing to see" | Empty state message |

---

## Key Behaviors

1. **Alphabetical Sorting:** Labels sorted by name (case-sensitive)
2. **Live Updates:** Label counts update automatically as bookmarks are labeled/unlabeled
3. **State Filtering:** Only LOADED bookmarks counted (state = 0)
4. **Empty Label Filtering:** Bookmarks with empty or null labels not included
5. **Navigation Drawer Badge:** Shows total number of unique labels
6. **Selected State:** Drawer item highlighted when viewing labels OR filtering by a label
7. **No Search:** Search icon hidden when viewing labels list
8. **View Switching:** Labels view and bookmarks view mutually exclusive based on FilterState

---

## Testing Considerations

**Test Cases:**
1. Open labels view from navigation drawer
2. Verify labels sorted alphabetically
3. Verify label counts match bookmark totals
4. Click label and verify bookmark list filters correctly
5. Verify empty state when no labels exist
6. Add/remove labels and verify counts update
7. Verify navigation drawer badge shows correct count
8. Verify navigation drawer item selected state
9. Delete all labels and verify empty state appears
10. Verify labels with special characters display correctly

---

## Implementation Notes for Refactor

1. **FilterState Flag:** The `viewingLabelsList` boolean controls view switching, independent of `label` filter
2. **Map Structure:** Labels stored as `Map<String, Int>` where key is label name, value is bookmark count
3. **Label Parsing:** Labels stored as comma-separated strings in database, parsed in repository layer
4. **Flow Transformation:** `observeAllLabels()` returns raw strings, transformed to counts in repository
5. **LazyColumn Items:** Use `items()` with sorted entries list, not the map directly
6. **State Clearing:** Selecting labels view clears all other filters (type, unread, etc.)
7. **No Selected State:** Label items in list always show `selected = false` (only drawer item shows selection)
8. **Border Styling:** NavigationDrawerItem requires manual border modifier, not a built-in property
