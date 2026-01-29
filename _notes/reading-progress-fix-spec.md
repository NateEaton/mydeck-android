# Spec: Align Reading Progress Tracking with Native Readeck Behavior

## Problem Statement

The app's reading progress tracking does not match the behavior of the native Readeck web client. There are two specific issues:

1. **Opening an article immediately sets progress to 1%** — even if the user doesn't scroll. The native Readeck client does not update progress until the user actually scrolls.
2. **Progress is not reliably saved on exit** — `onCleared()` launches a coroutine in `viewModelScope`, which is already cancelled at that point. The save may silently fail.

Additionally, the current implementation is missing several behaviors from the native client: completion locking, proper mark-as-unread reset, and automatic `is_read` flagging at 100%.

## Reference: Native Readeck Behavior

The native Readeck web client (v0.17+, refined in v0.21) implements these rules:

### Data Model
- **`read_progress`** (float, 0.0–1.0): Scroll position as a fraction. The app uses integers 0–100 internally and converts at the API boundary.
- **`is_read`** (boolean): Whether the article is marked as completed. Derived from `readProgress == 100` via `Bookmark.isRead()`.

### Scroll Tracking Rules
1. **Calculate progress on scroll:** `P = scrollTop / (scrollHeight - clientHeight)`, clamped to [0, 100].
2. **Completion threshold:** When P >= 100 (user reaches bottom), set `read_progress = 100` and `is_read = true`.
3. **Lock on completion:** Once `is_read` is true, **stop updating progress from scroll events**. Scrolling back up after reaching the bottom must NOT reduce progress from 100.
4. **No update without scroll:** Opening an article must NOT change progress. Only actual scroll events update it.
5. **Sync on exit only:** Send the PATCH to the server only when the user exits the article, not on every scroll.

### Mark as Read/Unread Rules
- **Mark as Read (manual):** Set `read_progress = 100`, `is_read = true`, disable scroll tracking.
- **Mark as Unread:** Set `read_progress = 0`, `is_read = false`, **re-enable scroll tracking**.

## Current Code Analysis

All changes are in `BookmarkDetailViewModel.kt` (`app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`).

### Issue 1: Progress set on open (line 98-100)

```kotlin
// init block, lines 95-104
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    if (bookmark.readProgress == 0) {
        currentScrollProgress = 1  // ← BUG: sets progress before user scrolls
    } else {
        currentScrollProgress = bookmark.readProgress
    }
}
```

When a fresh article (0% progress) is opened, `currentScrollProgress` is immediately set to 1. On exit, `saveCurrentProgress()` persists this value. The article now shows as "in progress" even though the user never scrolled.

### Issue 2: Save fails silently on ViewModel teardown (lines 113-131)

```kotlin
override fun onCleared() {
    super.onCleared()
    saveCurrentProgress()  // launches coroutine in viewModelScope
}

private fun saveCurrentProgress() {
    if (bookmarkId != null && currentScrollProgress > 0) {
        viewModelScope.launch {  // ← viewModelScope is cancelled during onCleared()
            bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
        }
    }
}
```

`viewModelScope` is cancelled when `onCleared()` runs. Any coroutine launched in it may be cancelled before the network/DB call completes. This means progress saves on app kill or system-initiated destruction are unreliable.

### Issue 3: No completion lock (line 229-232)

```kotlin
fun onScrollProgressChanged(progress: Int) {
    currentScrollProgress = progress.coerceIn(0, 100)
}
```

This unconditionally accepts any progress value. If the user scrolls to the bottom (100%) and then scrolls back up, progress drops below 100. The native client locks progress at 100 once the bottom is reached.

### Issue 4: Mark as Unread doesn't reset progress correctly (lines 234-244)

```kotlin
fun onToggleRead(bookmarkId: String, isRead: Boolean) {
    viewModelScope.launch {
        val newProgress = if (isRead) 100 else 0
        bookmarkRepository.updateReadProgress(bookmarkId, newProgress)
        currentScrollProgress = newProgress
    }
}
```

This sets progress to 0 when marking unread, which is correct. But it doesn't re-enable scroll tracking if it was previously locked (once we add the lock).

## Proposed Changes

All changes are in `BookmarkDetailViewModel.kt`. No other files need modification.

### Change 1: Add `isReadLocked` state and remove premature progress initialization

Add a new field to track whether scroll updates should be ignored (completion lock):

```kotlin
// CURRENT (lines 72-75)
private var currentScrollProgress = 0
private var initialReadProgress = 0
private var bookmarkType: com.mydeck.app.domain.model.Bookmark.Type? = null

// PROPOSED
private var currentScrollProgress = 0
private var initialReadProgress = 0
private var bookmarkType: com.mydeck.app.domain.model.Bookmark.Type? = null
private var isReadLocked = false  // true when article has been completed; disables scroll tracking
```

### Change 2: Fix init block — don't set progress on open, initialize lock state

```kotlin
// CURRENT (lines 95-104)
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    if (bookmark.readProgress == 0) {
        currentScrollProgress = 1
    } else {
        currentScrollProgress = bookmark.readProgress
    }
}

// PROPOSED
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    currentScrollProgress = bookmark.readProgress
    // If article was already completed, lock scroll tracking
    // so scrolling back up doesn't reduce progress from 100
    isReadLocked = bookmark.isRead()  // isRead() returns readProgress == 100
}
```

This ensures:
- A fresh article (0%) stays at 0% until the user scrolls.
- A completed article (100%) has scroll tracking locked immediately.

### Change 3: Add completion lock to `onScrollProgressChanged`

```kotlin
// CURRENT (lines 229-232)
fun onScrollProgressChanged(progress: Int) {
    currentScrollProgress = progress.coerceIn(0, 100)
}

// PROPOSED
fun onScrollProgressChanged(progress: Int) {
    // Once article is marked read, ignore further scroll updates
    // (matches native Readeck behavior: lock on completion)
    if (isReadLocked) return

    val clamped = progress.coerceIn(0, 100)
    currentScrollProgress = clamped

    // Auto-complete: when user reaches the bottom, lock tracking
    if (clamped >= 100) {
        isReadLocked = true
    }
}
```

### Change 4: Fix `onToggleRead` to manage lock state

```kotlin
// CURRENT (lines 234-244)
fun onToggleRead(bookmarkId: String, isRead: Boolean) {
    viewModelScope.launch {
        val newProgress = if (isRead) 100 else 0
        bookmarkRepository.updateReadProgress(bookmarkId, newProgress)
        currentScrollProgress = newProgress
    }
}

// PROPOSED
fun onToggleRead(bookmarkId: String, isRead: Boolean) {
    viewModelScope.launch {
        val newProgress = if (isRead) 100 else 0
        bookmarkRepository.updateReadProgress(bookmarkId, newProgress)
        currentScrollProgress = newProgress
        isReadLocked = isRead  // Lock on "mark read", unlock on "mark unread"
    }
}
```

### Change 5: Fix save reliability — use a non-cancellable scope in `onCleared()`

```kotlin
// CURRENT (lines 113-131)
override fun onCleared() {
    super.onCleared()
    saveCurrentProgress()
}

private fun saveCurrentProgress() {
    if (bookmarkId != null && currentScrollProgress > 0) {
        viewModelScope.launch {
            try {
                bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
                Timber.d("Saved final read progress: $currentScrollProgress%")
            } catch (e: Exception) {
                Timber.e(e, "Error saving final progress: ${e.message}")
            }
        }
    }
}

// PROPOSED
override fun onCleared() {
    super.onCleared()
    // viewModelScope is cancelled during onCleared(), so use an independent scope
    // to ensure the save completes even during ViewModel teardown
    kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    ).launch {
        saveCurrentProgress()
    }
}

private suspend fun saveCurrentProgress() {
    if (bookmarkId != null && currentScrollProgress > 0) {
        try {
            bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
            Timber.d("Saved final read progress: $currentScrollProgress%")
        } catch (e: Exception) {
            Timber.e(e, "Error saving final progress: ${e.message}")
        }
    }
}
```

Note: The `> 0` guard is intentionally kept — this matches native Readeck behavior where opening an article without scrolling does not update progress.

### Change 6: Make `onClickBack()` await the save before navigating

```kotlin
// CURRENT (lines 291-295)
fun onClickBack() {
    saveCurrentProgress()
    _navigationEvent.update { NavigationEvent.NavigateBack }
}

// PROPOSED
fun onClickBack() {
    viewModelScope.launch {
        saveCurrentProgress()  // now suspend — completes before navigation
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }
}
```

## Summary of All Changes

| # | Change | Lines | Purpose |
|---|--------|-------|---------|
| 1 | Add `isReadLocked` field | 75 | Tracks whether scroll updates should be ignored |
| 2 | Fix init — remove `= 1` special case, initialize lock | 95-104 | Don't set progress on open; lock if already read |
| 3 | Add completion lock to `onScrollProgressChanged` | 229-232 | Ignore scroll after reaching 100%; auto-lock at bottom |
| 4 | Fix `onToggleRead` to manage lock | 234-244 | Lock on "mark read", unlock + reset on "mark unread" |
| 5 | Use non-cancellable scope in `onCleared()` | 113-131 | Ensure save completes during teardown |
| 6 | Await save in `onClickBack()` | 291-295 | Prevent race between save and navigation |

## What This Does NOT Change

- **Scroll position restoration** — `initialReadProgress` is loaded from DB in init and passed to `BookmarkDetailContent`. The composable's `LaunchedEffect` restores position correctly as-is.
- **The scroll calculation in `BookmarkDetailContent`** — the `LaunchedEffect` at lines 285-298 continues to compute `(scrollValue / maxValue) * 100` and report it. No changes needed.
- **Photos/videos** — auto-marked as 100% on open (lines 87-94), unaffected.
- **The `> 0` guard in `saveCurrentProgress()`** — intentionally kept. Matches native behavior: opening without scrolling does not create a server update.
- **API contract** — `PATCH /bookmarks/{id}` with `read_progress` (integer 0-100). Note: if the API expects float 0.0-1.0, a conversion would be needed in `BookmarkRepositoryImpl.updateReadProgress()`, but current code sends integers and it works.

## Behavior Matrix (After Changes)

| Scenario | Progress Saved | is_read | Lock State |
|----------|---------------|---------|------------|
| Open article, don't scroll, exit | No save (stays at DB value) | Unchanged | Unchanged |
| Scroll to 50%, exit | 50 | false | Unlocked |
| Scroll to 100% (bottom), exit | 100 | true (via isRead()) | Locked |
| Scroll to 100%, scroll back to 30%, exit | 100 (locked) | true | Locked |
| Mark as Read manually | 100 | true | Locked |
| Mark as Unread | 0 | false | Unlocked |
| Mark as Unread, scroll to 40%, exit | 40 | false | Unlocked |

## Testing Plan

1. **Fresh article:** Open → don't scroll → exit → reopen → verify starts at top, progress still 0%
2. **Partial read:** Scroll to ~50% → exit → reopen → verify restores to ~50%
3. **Completion lock:** Scroll to bottom → scroll back to top → exit → reopen → verify restores to 100%, shown as "read"
4. **Mark as Read:** Tap "mark read" at 30% → verify progress jumps to 100% → scroll up → exit → reopen → verify still 100%
5. **Mark as Unread:** Mark a completed article unread → verify progress resets to 0% → scroll to 40% → exit → verify saves 40%
6. **App kill:** Scroll to ~30% → force-kill app → reopen → verify progress is ~30% (tests non-cancellable scope)
7. **Photos/videos:** Open photo bookmark → verify auto-marked 100% → unaffected by any changes
