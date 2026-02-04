# Mini-Spec: Unified "Quick Save" Experience

## 1. Overview
This feature replaces the current `AlertDialog`-based "Add Bookmark" experience with a **Modal Bottom Sheet**. This modernizes the UI, solves keyboard occlusion issues, and unifies the code used for both in-app adding and external sharing (Intents).

## 2. Architecture

### 2.1 New Component: `ShareActivity`
A dedicated entry point for `ACTION_SEND` intents to create the "overlay" effect over other apps (e.g., Chrome).

*   **Manifest:**
    *   `theme`: `Theme.Translucent.NoTitleBar` (Custom theme required in `themes.xml`).
    *   `launchMode`: `standard` (Ephemeral instance).
    *   `windowSoftInputMode`: `adjustResize` (Critical for keyboard handling).
*   **Lifecycle:**
    1.  Extracts URL/Title from Intent.
    2.  Displays `AddBookmarkSheet` (Composable).
    3.  On Action (Add/Archive/Timeout) -> Enqueues Worker -> Calls `finish()`.
    4.  On Action (View) -> Enqueues Worker -> Launches `MainActivity` -> Calls `finish()`.

### 2.2 Existing Component: `BookmarkListScreen`
*   **Change:** Replace the `AlertDialog` conditional logic with a `ModalBottomSheet`.
*   **State:** The ViewModel continues to manage `CreateBookmarkUiState`, but the UI renders the shared Sheet instead of a Dialog.

---

## 3. Shared UI Component: `AddBookmarkSheet`

A single Composable used by both Activities.

```kotlin
@Composable
fun AddBookmarkSheet(
    url: String,
    title: String,
    initialLabels: List<String>,
    // Configuration
    mode: SheetMode, // Enum: SHARE_INTENT or IN_APP
    // Callbacks
    onUpdateTitle: (String) -> Unit,
    onUpdateLabels: (List<String>) -> Unit,
    onAction: (SaveAction) -> Unit, // ADD, ARCHIVE, VIEW
    onDismiss: () -> Unit,
    onInteraction: () -> Unit // Used to cancel timer
)
```

### 3.1 Visual Layout (Material 3 BottomSheet)
*   **Drag Handle:** Standard top pill.
*   **Header:**
    *   *Share Mode:* Small App Icon + Text "Save to MyDeck".
    *   *In-App Mode:* Text "Add Link".
*   **Form (Vertical Column):**
    *   **URL:** Read-only `OutlinedTextField` (greyed out text).
    *   **Title:** Editable `OutlinedTextField`.
    *   **Labels:** FlowRow of Chips + "Add Label" input field.
        *   *Improvement:* Tapping "Add Label" shows the keyboard. The Sheet pushes up via `Modifier.imePadding()` so the input remains visible.
*   **Action Bar (Bottom Row):**
    *   **Left:** [Archive] (Outlined Button or Icon Button with Text).
    *   **Center:** [Add] (Filled/Primary Button).
        *   *Share Mode:* Displays a progress indicator (Linear or Circular) representing the auto-save timer.
    *   **Right:** [View] (Text Button "View").

---

## 4. Behavioral Differences

| Feature | Share Intent Mode (`ShareActivity`) | In-App Mode (`MainActivity`) |
| :--- | :--- | :--- |
| **Context** | Overlay on top of Browser/Other App | Inside MyDeck |
| **Auto-Save Timer** | **Active (5s).** Counts down immediately. | **Disabled.** Waits for user input. |
| **Interruption** | Tapping *any* field (Title/Label) **cancels** the timer permanently. | N/A |
| **Dismissal** | Swiping down **Cancels** (Activity finishes). | Swiping down **Cancels** (Sheet closes). |
| **"Add" Action** | Saves via Worker -> Closes Activity. | Saves via Worker -> Closes Sheet -> Shows Snackbar. |
| **"Archive" Action**| Saves (isArchived=true) -> Closes Activity. | Saves (isArchived=true) -> Closes Sheet. |
| **"View" Action** | Saves -> Opens `MainActivity` (SingleTop). | Saves -> Navigates to `BookmarkDetailScreen`. |

---

## 5. Data & Worker Logic

To ensure the "Fire and Forget" experience (especially for Share Mode), we must not rely on the UI staying open to finish the network call.

### 5.1 `CreateBookmarkWorker`
*   **Input:** URL, Title, Labels, IsArchived (Boolean).
*   **Constraint:** `NetworkType.CONNECTED`.
*   **Logic:**
    1.  Call API to create bookmark.
    2.  Get ID from response.
    3.  Fetch metadata for ID.
    4.  **Insert into Local DB (`BookmarkDao`).**
    5.  *Crucial:* Do **not** trigger a full `LoadBookmarksUseCase`. Just insert this one item.

### 5.2 Handling "View" (Immediate Read)
The "View" action is the only one that cannot purely use a background worker because the user wants to see the content *now*.

*   **Logic:**
    1.  Show loading spinner on the Sheet.
    2.  Perform API call immediately in Coroutine Scope (ViewModel).
    3.  **Success:**
        *   Insert to DB.
        *   *Share Mode:* `startActivity(Intent(MainActivity...))` with `bookmarkId` extra.
        *   *In-App:* `navController.navigate(DetailRoute(bookmarkId))`.
    4.  **Failure:** Show Error on Sheet (allow retry).

---

## 6. Implementation Plan

### Step 1: Fix Sync & DB (Prerequisite)
*   Implement **Fix #3** (Insert Ignore + Update) in `BookmarkDao`. This ensures that if the background worker runs while the app is doing something else, we don't accidentally wipe content.

### Step 2: UI Component (`AddBookmarkSheet`)
*   Create the Composable.
*   Implement `Modifier.imePadding()` and `WindowCompat.setDecorFitsSystemWindows(window, false)` to ensure the keyboard pushes the sheet up (like the Instapaper screenshots).

### Step 3: `ShareActivity`
*   Create Activity and Theme.
*   Implement the 5-second timer logic (`LaunchedEffect` that cancels on state change).
*   Wire up the Intent handling.

### Step 4: Refactor In-App "Add"
*   In `BookmarkListScreen`, remove `AlertDialog`.
*   Replace with `ModalBottomSheet` wrapping the new `AddBookmarkSheet`.
*   Wire up to `BookmarkListViewModel`.

### Step 5: Bug Squashing
*   Ensure `SavedStateHandle` is cleared in `BookmarkListViewModel` so the dialog doesn't reappear on process death/restoration (Fix #1).

---

## 7. UX Edge Cases

*   **Offline (Share Mode):** If user clicks "Add" while offline, the Worker enqueues. It will sync when online. *Risk:* User won't know if it fails later (unless we add notifications in Phase 2).
*   **Invalid URL (Share Mode):**
    *   *Timer:* If URL is invalid, **Pause Timer** immediately. Show error. Force user to fix or cancel.
*   **Keyboard:** When typing labels, the "Add" button might be pushed up. Ensure the Sheet is scrollable if the screen is short (landscape mode).