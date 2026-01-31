## **Functional & Technical Spec: List View Layout & Sort Enhancement**

### **1\. Overview**

Add three switchable layout modes (Card, Magazine, List) and a Sort menu to the bookmark list screen. Both preferences persist across sessions via SettingsDataStore.  
---

### **2\. Layout Modes**

#### **2.1 Card View (current, modified)**

* Image: Full-width, fills top of card (change ContentScale.FillWidth to ContentScale.Crop, remove fixed 150.dp height constraint — use aspect ratio \~16:9 instead, approximately 200.dp)  
* Title: Overlaid on the bottom of the image with a gradient scrim (dark gradient from bottom), white text, 2 lines max  
* Action row: Favorite, Archive, Open-in-browser icons on left; Delete on right — positioned below the image, outside the scrim  
* No site name row, no labels, no divider — keep it visually clean like Readeck's card view  
* Read progress indicator: Stays in top-right corner of image (as-is)

#### **2.2 Magazine View**

* Layout: Row — thumbnail on left, text content on right  
* Thumbnail: \~100.dp x 80.dp, ContentScale.Crop, rounded corners (8.dp)  
* Right side (Column):  
  * Favicon (16x16) \+ site name \+ " · " \+ read time (e.g., "26 min") in labelMedium  
  * Title: titleSmall, 2 lines max, ellipsis  
  * Action icons row: Favorite, Archive, Open-in-browser (left); Delete (right) — smaller icon buttons (36.dp)  
* Read progress: Small circular indicator overlaid on thumbnail top-right  
* Card padding: 12.dp horizontal, 8.dp vertical; thin divider between items (no Card elevation — use flat list style)

#### **2.3 List View (most compact)**

* Layout: Row — favicon on left, text in center, no image  
* Left: Favicon 24x24dp  
* Center (Column, weight 1f):  
  * Title: bodyMedium, 2 lines max, ellipsis  
  * Site name \+ " · " \+ read time in labelSmall, single line  
* Right: Compact action icons — Favorite \+ overflow (3-dot per-item) or just Favorite \+ Delete, 32.dp touch targets  
* Read progress: Thin colored left-border on the row (accent color, width proportional to progress) or small text "8 of 10 min left" like Instapaper  
* Divider: HorizontalDivider between items, no card elevation

---

### **3\. Data Model Changes**

#### **3.1 BookmarkListItem — add fields**

data class BookmarkListItem(  
    *// ... existing fields ...*  
    val readingTime: Int?,    *// minutes, nullable*  
    val created: LocalDateTime,  
    val wordCount: Int?       *// nullable*  
)

#### **3.2 BookmarkListItemEntity — add matching columns**

Add readingTime, created, wordCount to the Room entity.

#### **3.3 DAO SELECT updates**

All three query functions (getBookmarkListItemsByFilters, searchBookmarkListItems, and any future ones) must add these columns to the SELECT:  
SELECT id, url, title, siteName, isMarked, isArchived, readProgress,  
       icon\_src AS iconSrc, image\_src AS imageSrc, labels,  
       thumbnail\_src AS thumbnailSrc, type,  
       readingTime, created, wordCount  
FROM bookmarks WHERE ...

#### **3.4 Repository mapper**

Update the mapper from BookmarkListItemEntity → BookmarkListItem to include the new fields.  
---

### **4\. Sort Implementation**

#### **4.1 Sort enum**

enum class SortOption(val sqlOrderBy: String) {  
    NEWEST\_ADDED("created DESC"),  
    OLDEST\_ADDED("created ASC"),  
    LONGEST\_ARTICLE("readingTime DESC"),  
    SHORTEST\_ARTICLE("readingTime ASC"),  
    READ\_PROGRESS("readProgress ASC")  
}

Each option has a fixed direction (no separate asc/desc toggle).

#### **4.2 DAO changes**

Replace hardcoded ORDER BY created DESC in all three query-building functions with a parameter:  
fun getBookmarkListItemsByFilters(  
    *// ... existing params ...*  
    orderBy: String \= "created DESC"   *// new param*  
): Flow\<List\<BookmarkListItemEntity\>\> {  
    *// ... existing logic ...*  
    append(" ORDER BY $orderBy")  
}

Same for searchBookmarkListItems and getBookmarksByFilters.

#### **4.3 Repository layer**

Pass SortOption.sqlOrderBy through from ViewModel → Repository → DAO.

#### **4.4 ViewModel**

* New state: private val \_sortOption \= MutableStateFlow(SortOption.NEWEST\_ADDED)  
* On init, read persisted sort preference from SettingsDataStore  
* Include \_sortOption in the combine() flow alongside \_filterState and \_searchQuery  
* Expose fun onSortOptionSelected(option: SortOption) — updates state and persists to DataStore

#### **4.5 Persistence**

Add to SettingsDataStore interface:  
suspend fun saveSortOption(sortOption: String)  
suspend fun getSortOption(): String?

Store as the enum name string. Default: NEWEST\_ADDED.  
---

### **5\. Layout Switching**

#### **5.1 Layout enum**

enum class LayoutMode {  
    CARD, MAGAZINE, LIST  
}

#### **5.2 ViewModel**

* New state: private val \_layoutMode \= MutableStateFlow(LayoutMode.CARD)  
* On init, read persisted layout preference from SettingsDataStore  
* Expose fun onLayoutModeSelected(mode: LayoutMode)

#### **5.3 Persistence**

Add to SettingsDataStore:  
suspend fun saveLayoutMode(layoutMode: String)  
suspend fun getLayoutMode(): String?

Default: CARD.  
---

### **6\. UI: 3-Dot Menu & Dialogs**

#### **6.1 Top App Bar changes**

In BookmarkListScreen, update the actions block (line 329-335):  
actions \= {  
    if (\!isSearchActive.value) {  
        IconButton(onClick \= { viewModel.onSearchActiveChange(true) }) {  
            Icon(Icons.Filled.Search, ...)  
        }  
        IconButton(onClick \= { showOverflowMenu \= true }) {  
            Icon(Icons.Default.MoreVert, contentDescription \= "More options")  
        }  
        DropdownMenu(  
            expanded \= showOverflowMenu,  
            onDismissRequest \= { showOverflowMenu \= false }  
        ) {  
            DropdownMenuItem(  
                text \= { Text("Layout") },  
                onClick \= { showLayoutDialog \= true; showOverflowMenu \= false }  
            )  
            DropdownMenuItem(  
                text \= { Text("Sort") },  
                onClick \= { showSortDialog \= true; showOverflowMenu \= false }  
            )  
        }  
    }  
}

Both search icon and 3-dot menu are hidden when search is active (replaced by the search field).

#### **6.2 Layout Picker Dialog**

A small dialog or bottom sheet with three icons in a SingleChoiceSegmentedButtonRow or a simple row of three IconButtons with selection highlight:

| Icon | Layout |
| :---- | :---- |
| Icons.Default.GridView (or Apps) | Card |
| Icons.AutoMirrored.Default.ViewList | Magazine |
| Icons.Default.ViewHeadline (or Reorder) | List |

Use icons only — no text labels. Highlight the selected one with a filled background/tint. A SegmentedButton row is the cleanest Material 3 approach.

#### **6.3 Sort Picker Dialog**

An AlertDialog or DropdownMenu with radio-button style list:  
Sort by  
──────────────  
○ Newest Saved        (default, selected)  
○ Oldest Saved  
○ Longest Articles  
○ Shortest Articles  
○ Read Progress

Use RadioButton \+ Text rows. Dismiss on selection. Checkmark or filled radio on the active option.  
---

### **7\. BookmarkListView Changes**

Replace the single BookmarkCard call in BookmarkListView with a when on layout mode:  
@Composable  
fun BookmarkListView(  
    layoutMode: LayoutMode,  
    bookmarks: List\<BookmarkListItem\>,  
    *// ... callbacks ...*  
) {  
    LazyColumn(modifier \= modifier) {  
        items(bookmarks) { bookmark \-\>  
            when (layoutMode) {  
                LayoutMode.CARD \-\> BookmarkCardView(bookmark, ...)  
                LayoutMode.MAGAZINE \-\> BookmarkMagazineView(bookmark, ...)  
                LayoutMode.LIST \-\> BookmarkListItemView(bookmark, ...)  
            }  
        }  
    }  
}

Create three composables (can live in BookmarkCard.kt or split into separate files).  
---

### **8\. Files to Modify**

| File | Changes |
| :---- | :---- |
| BookmarkListItem.kt | Add readingTime, created, wordCount |
| BookmarkListItemEntity.kt | Add matching columns |
| BookmarkDao.kt | Add columns to SELECTs, parameterize ORDER BY |
| BookmarkRepository (interface \+ impl) | Pass sort param through |
| SettingsDataStore.kt \+ Impl | Add layout/sort persistence methods |
| BookmarkListViewModel.kt | Add SortOption, LayoutMode state \+ flows, wire into combine |
| BookmarkListScreen.kt | 3-dot menu, layout dialog, sort dialog, pass layoutMode to list |
| BookmarkCard.kt | Refactor current card, add Magazine and List composables |

### **9\. New Files**

| File | Purpose |
| :---- | :---- |
| SortOption.kt | Enum in domain/model/ |
| LayoutMode.kt | Enum in domain/model/ |

---

### **10\. Design Notes**

* The Card view draws inspiration from Readeck's full-bleed card layout. The Magazine and List views are standard patterns found across Readeck, Pocket, and Instapaper. The implementation uses common layout conventions rather than copying any specific app's design verbatim.  
* Read time display format: "X min" (matches Readeck convention). If readingTime is null, omit it.  
* For List view read progress, use the text format "X of Y min left" (like Instapaper) when both readingTime and readProgress are available. Otherwise fall back to no indicator.

