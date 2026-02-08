# Mini-Spec: Unified "Quick Save" Experience

## 1. Overview
This feature replaces the current `AlertDialog`-based "Add Bookmark" experience with a **Modal Bottom Sheet**. This modernizes the UI, solves keyboard occlusion issues, and unifies the code used for both in-app adding and external sharing (Intents).

### Implementation Phases

This spec is split into two phases with a clear dependency boundary:

| Phase | Scope | Dependency |
|-------|-------|------------|
| **Phase 1** | Replace in-app AlertDialog with ModalBottomSheet | None — can be implemented immediately |
| **Phase 2** | ShareActivity, intent handling, auto-save timer, CreateBookmarkWorker | Requires Offline Action Queue (for fire-and-forget worker pattern) |

---

## Phase 1: In-App Bottom Sheet

### What Changes

Replace the `CreateBookmarkDialog` (`AlertDialog` in `BookmarkListScreen.kt`) with a `ModalBottomSheet` wrapping the new `AddBookmarkSheet` composable. This is a UI-layer swap only — no new activities, no workers, no architectural changes.

### 1.1 New Composable: `AddBookmarkSheet`

```kotlin
@Composable
fun AddBookmarkSheet(
    url: String,
    title: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit,
    onArchiveBookmark: () -> Unit,
    onDismiss: () -> Unit
)
```

### 1.2 Visual Layout

* **Drag Handle:** Standard Material 3 top pill.
* **Header:** Text "Add Link".
* **Form (Vertical Column):**
    * **URL:** Editable `OutlinedTextField` with error support (preserves existing validation).
    * **Title:** Editable `OutlinedTextField`.
    * **Labels:** `FlowRow` of Chips + "Add Label" input field.
        * Tapping "Add Label" shows the keyboard. The Sheet pushes up via `Modifier.imePadding()` so the input remains visible.
* **Action Bar (Bottom Row):**
    * **Left:** \[Archive\] (Outlined Button) — saves with `isArchived=true`, closes sheet.
    * **Right:** \[Add\] (Filled/Primary Button) — saves normally, closes sheet.

### 1.3 Why Include Archive in Phase 1

In many cases users add bookmarks for content they have already read. The Archive button enables moving a bookmark directly to the archive at creation time, keeping "My List" focused on unread content. This is a small addition (one extra callback routed through the existing ViewModel) with meaningful UX value.

### 1.4 Behavioral Notes (In-App Mode)

| Aspect | Behavior |
|--------|----------|
| **Auto-Save Timer** | Disabled. Waits for user input. |
| **Dismissal** | Swiping down cancels (sheet closes, no save). |
| **"Add" Action** | Saves via existing ViewModel path → closes sheet → shows Snackbar. |
| **"Archive" Action** | Saves with `isArchived=true` → closes sheet → shows Snackbar. |
| **Clipboard** | FAB still pre-fills URL from clipboard (existing behavior preserved). |

### 1.5 Keyboard Handling

* Apply `Modifier.imePadding()` to the sheet content.
* Ensure `WindowCompat.setDecorFitsSystemWindows(window, false)` is set (check if already configured).
* The sheet must be scrollable if content exceeds visible area (e.g., landscape mode with keyboard open).

### 1.6 Implementation Steps (Phase 1)

**Step 1:** Create `AddBookmarkSheet.kt` in `ui/list/`.

**Step 2:** In `BookmarkListScreen.kt`:
  * Remove the `CreateBookmarkDialog` composable and its call site.
  * Add `ModalBottomSheet` wrapping `AddBookmarkSheet`.
  * Wire existing ViewModel state (`createBookmarkUrl`, `createBookmarkTitle`, `createBookmarkLabels`, etc.) to the new sheet.

**Step 3:** Add the archive-on-create path:
  * Add `onArchiveBookmark` callback to `BookmarkListViewModel` that sets `isArchived=true` before calling the create API.
  * Surface the callback through to the sheet's Archive button.

**Step 4:** Test keyboard behavior across screen sizes and orientations. Verify `imePadding()` works correctly with the label input field.

### 1.7 String Resources (Phase 1)

```xml
<string name="add_link">Add Link</string>
<string name="action_archive_bookmark">Archive</string>
```

Note: `add_link` replaces the dialog title "Add New Bookmark" with a shorter header appropriate for the sheet context. The existing `add_bookmark` string is retained for the FAB content description.

---

## Phase 2: Share Intent & Background Processing

> **Prerequisite:** Offline Action Queue must be implemented first. The `CreateBookmarkWorker` pattern depends on the queue infrastructure for reliable fire-and-forget behavior.

### 2.1 New Component: `ShareActivity`
A dedicated entry point for `ACTION_SEND` intents to create the "overlay" effect over other apps (e.g., Chrome).

*   **Manifest:**
    *   `theme`: `Theme.Translucent.NoTitleBar` (Custom theme required in `themes.xml`).
    *   `launchMode`: `standard` (Ephemeral instance).
    *   `windowSoftInputMode`: `adjustResize` (Critical for keyboard handling).
*   **Lifecycle:**
    1.  Extracts URL/Title from Intent.
    2.  Displays `AddBookmarkSheet` (Composable) — reuses the Phase 1 component with `mode = SHARE_INTENT`.
    3.  On Action (Add/Archive/Timeout) -> Enqueues Worker -> Calls `finish()`.
    4.  On Action (View) -> Enqueues Worker -> Launches `MainActivity` -> Calls `finish()`.

### 2.2 AddBookmarkSheet Mode Extension

The Phase 1 composable is extended with a `mode` parameter:

```kotlin
enum class SheetMode { IN_APP, SHARE_INTENT }
```

Additional parameters for Share mode:

```kotlin
    mode: SheetMode,
    onAction: (SaveAction) -> Unit,  // ADD, ARCHIVE, VIEW
    onInteraction: () -> Unit        // Cancels auto-save timer
```

### 2.3 Behavioral Differences (Phase 2)

| Feature | Share Intent Mode (`ShareActivity`) | In-App Mode (`MainActivity`) |
| :--- | :--- | :--- |
| **Context** | Overlay on top of Browser/Other App | Inside MyDeck |
| **Header** | Small App Icon + "Save to MyDeck" | "Add Link" |
| **URL Field** | Read-only (greyed out) | Editable |
| **Auto-Save Timer** | **Active (5s).** Counts down immediately. | **Disabled.** |
| **Interruption** | Tapping *any* field cancels timer permanently. | N/A |
| **Dismissal** | Swiping down cancels (Activity finishes). | Swiping down cancels (Sheet closes). |
| **"Add" Action** | Saves via Worker -> Closes Activity. | Saves via ViewModel -> Closes Sheet -> Snackbar. |
| **"Archive" Action**| Saves (isArchived=true) via Worker -> Closes Activity. | Saves (isArchived=true) -> Closes Sheet -> Snackbar. |
| **"View" Action** | Saves -> Opens `MainActivity` (SingleTop). | Saves -> Navigates to `BookmarkDetailScreen`. |

### 2.4 `CreateBookmarkWorker`

*   **Input:** URL, Title, Labels, IsArchived (Boolean).
*   **Constraint:** `NetworkType.CONNECTED`.
*   **Logic:**
    1.  Call API to create bookmark.
    2.  Get ID from response.
    3.  Fetch metadata for ID.
    4.  **Insert into Local DB (`BookmarkDao`).**
    5.  *Crucial:* Do **not** trigger a full `LoadBookmarksUseCase`. Just insert this one item.

**Relationship to Offline Action Queue:** Once the queue supports `CREATE` actions (Queue Phase 2), the `CreateBookmarkWorker` should be migrated to use the queue's `ActionSyncWorker` instead of being a standalone worker. Until then, it operates independently with the same `NetworkType.CONNECTED` constraint pattern.

### 2.5 Handling "View" (Immediate Read)

The "View" action cannot purely use a background worker because the user wants to see the content *now*.

*   **Logic:**
    1.  Show loading spinner on the Sheet.
    2.  Perform API call immediately in Coroutine Scope (ViewModel).
    3.  **Success:**
        *   Insert to DB.
        *   *Share Mode:* `startActivity(Intent(MainActivity...))` with `bookmarkId` extra.
        *   *In-App:* `navController.navigate(DetailRoute(bookmarkId))`.
    4.  **Failure:** Show Error on Sheet (allow retry).

### 2.6 Implementation Steps (Phase 2)

**Step 1: Fix Sync & DB (Prerequisite)**
*   Implement Insert Ignore + Update in `BookmarkDao` to prevent race conditions between the worker and other sync operations.

**Step 2: Extend `AddBookmarkSheet`**
*   Add `SheetMode` parameter and conditional rendering (header, URL editability, timer).
*   Implement 5-second auto-save timer (`LaunchedEffect` that cancels on state change).

**Step 3: Create `ShareActivity`**
*   Create Activity and translucent theme.
*   Wire up Intent handling.
*   Implement `CreateBookmarkWorker`.

**Step 4: Manifest & Intent Filter**
*   Register `ShareActivity` with `ACTION_SEND` intent filter for `text/plain`.

### 2.7 String Resources (Phase 2)

```xml
<string name="save_to_mydeck">Save to MyDeck</string>
<string name="action_view_bookmark">View</string>
```

---

## 7. UX Edge Cases

*   **Offline (Share Mode):** If user clicks "Add" while offline, the Worker enqueues. It will sync when online. *Risk:* User won't know if it fails later (unless we add notifications in Phase 2).
*   **Invalid URL (Share Mode):**
    *   *Timer:* If URL is invalid, **Pause Timer** immediately. Show error. Force user to fix or cancel.
*   **Keyboard:** When typing labels, the "Add" button might be pushed up. Ensure the Sheet is scrollable if the screen is short (landscape mode).
