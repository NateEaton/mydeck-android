### Design Specification: "Responsive Grid & Drawer"

#### 1. Layout Strategy
We will define layouts based on **Window Width Size Classes**:
*   **Compact (Phone):** Current behavior. Modal Drawer + Single Column List.
*   **Medium (Tablet Portrait / Foldable):** Modal Drawer (Hidden by default) + **2-Column** Staggered Grid.
*   **Expanded (Tablet Landscape):** **Permanent Navigation Drawer** (Always visible on left) + **3 or 4-Column** Staggered Grid.

#### 2. Navigation Changes
*   **Landscape:** The hamburger menu disappears. The Drawer contents (My List, Archive, Favorites, Labels) are pinned to the left edge.
*   **Portrait:** Remains identical to mobile (Hamburger menu). This adheres to your preference for keeping the mobile experience intact and maximizes screen width for the grid columns.

#### 3. List View Transformation
*   **Grid/Mosaic Mode:** We switch from `LazyColumn` to `LazyVerticalStaggeredGrid`. This is the "Masonry" layout used by Readeck web.
*   **Compact Mode:** We force this to remain a `LazyColumn` (1 column wide) regardless of screen size, preserving the "list" aesthetic you noted.

#### 4. Detail "Info" Panel (Metadata)
*   **Phone:** Continues to act as a full-screen or bottom-sheet overlay.
*   **Tablet:** We will implement a **Modal Side Sheet**. When you click the "Info" (i) icon in the toolbar, a panel slides in from the *right* edge (covering ~360dp), overlaying the content slightly or pushing it, containing the tags/metadata. This matches your request for a "slide-out panel."

---

### Implementation Plan

#### Phase 1: Dependencies & Foundation
We need the official window size class library to detect the form factor reliably.

**1. Add Dependency:**
In `build.gradle.kts`:
```kotlin
implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
```

**2. Pass WindowSizeClass:**
In `MainActivity.kt`, calculate the size class and pass it into `MyDeckNavHost`.
```kotlin
val windowSizeClass = calculateWindowSizeClass(this)
MyDeckTheme {
    MyDeckNavHost(navController, settingsDataStore, windowSizeClass)
}
```

#### Phase 2: Refactoring BookmarkListScreen (The Heavy Lifting)

We need to break `BookmarkListScreen` apart so the content can sit inside different Scaffolds depending on the device rotation.

**1. Refactor `BookmarkListScreen.kt`:**
We will separate the *Navigation Logic* from the *Content Layout*.

*   Create `BookmarkListContent`: A composable that takes the list of bookmarks and the specific `LayoutMode`.
*   Implement Adaptive Logic:

```kotlin
// Pseudo-code concept
@Composable
fun BookmarkListScreen(windowSizeClass: WindowSizeClass, ...) {
    // ... ViewModel state collection ...

    // Determine Navigation Type
    val navigationType = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
        NavigationType.PERMANENT_DRAWER
    } else {
        NavigationType.MODAL_DRAWER
    }

    if (navigationType == PERMANENT_DRAWER) {
        PermanentNavigationDrawer(
            drawerContent = { /* Your existing drawer content reused */ },
            content = {
                // Main Content with NO Hamburger icon in TopBar
                BookmarkListContent(...)
            }
        )
    } else {
        ModalNavigationDrawer(
            drawerContent = { /* Your existing drawer content reused */ },
            content = {
                // Main Content WITH Hamburger icon in TopBar
                BookmarkListContent(...)
            }
        )
    }
}
```

**2. Implement `LazyVerticalStaggeredGrid`:**
In `BookmarkListView`, we will switch based on the layout mode and screen width.

```kotlin
@Composable
fun BookmarkListView(layoutMode: LayoutMode, windowSizeClass: WindowSizeClass, ...) {
    val columnCount = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 4 // or Adaptive(300.dp)
        WindowWidthSizeClass.Medium -> 2
        else -> 1
    }

    if (layoutMode == LayoutMode.COMPACT) {
        // Keep existing LazyColumn
        LazyColumn { ... }
    } else {
        // New Grid implementation
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columnCount),
            // ...
        ) {
            items(bookmarks) { bookmark ->
                BookmarkCard(...)
            }
        }
    }
}
```

#### Phase 3: The Side Sheet (Detail Info)

We need to modify how the "Bookmark Details" (Info) screen is presented. Currently, it's likely a standard Dialog or a separate Navigation Route.

**1. Refactor `BookmarkDetailsDialog`:**
Extract the internal content of `BookmarkDetailsDialog` into a `BookmarkMetadataForm` composable. This allows us to wrap it in different containers.

**2. Update `BookmarkDetailScreen`:**
When the user clicks the "Info" (i) icon:
*   **Phone:** Show `ModalBottomSheet` containing `BookmarkMetadataForm`.
*   **Tablet:** Show `ModalSideSheet` (if available in your Material3 version, otherwise a custom `AnimatedVisibility` Box aligned to the right) containing `BookmarkMetadataForm`.

#### Phase 4: Refinement (The "Polish")

**1. CSS Margins:**
In `BookmarkDetailViewModel`, or wherever you inject the CSS:
*   Inject a class into the `<body>` tag based on the device type.
*   If Tablet: reduce `padding: 13px;` to something cleaner, or rely on the `max-width: 38em` to do the work. (Actually, keeping the padding is usually safer, the `max-width` will handle the centering perfectly).

**2. TopAppBar Consistency:**
Ensure the `TopAppBar` in the `BookmarkListScreen` hides the "Menu" (Hamburger) icon when in Landscape mode, as the Permanent Drawer is already visible.

### Summary of Changes required in Codebase

1.  **`MainActivity.kt`**: Calculate `WindowSizeClass`.
2.  **`BookmarkListScreen.kt`**:
    *   Split into adaptive layouts (Permanent Drawer vs Modal Drawer).
    *   Hide Hamburger icon in Expanded mode.
3.  **`BookmarkListView.kt`**:
    *   Add `LazyVerticalStaggeredGrid` logic.
    *   Logic to determine column count based on `widthSizeClass`.
4.  **`BookmarkDetailScreen.kt`**:
    *   Logic to switch between BottomSheet (Phone) and SideSheet (Tablet) for the Metadata/Info view.

