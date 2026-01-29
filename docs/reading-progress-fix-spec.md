# Spec: Fix Reading Progress to Track Current Position, Not Furthest Read

## Problem Statement

The app tracks reading progress as a percentage (0–100) and restores scroll position when the user reopens an article. The current implementation always restores to the **furthest point the user has ever scrolled to**, rather than the **position they were at when they last closed the article**.

**Example:** A user scrolls to 60% of an article, then scrolls back to the top (0%) and exits. When they reopen the article, it jumps to 60% instead of staying at 0%.

## Root Cause

In `BookmarkDetailViewModel`, the `onScrollProgressChanged` callback unconditionally updates `currentScrollProgress` with whatever the current scroll percentage is:

```kotlin
// BookmarkDetailViewModel.kt
fun onScrollProgressChanged(progress: Int) {
    currentScrollProgress = progress.coerceIn(0, 100)
}
```

This value is saved to the database and API when the user exits (`onCleared()` or `onClickBack()`). So the *current position* is saved correctly on exit — **this part is fine.**

The actual bug is in `BookmarkDetailContent` (the composable). The `LaunchedEffect` that tracks scroll position fires for every scroll change, and the ViewModel stores whatever value comes in. This means when the user scrolls back to 0% and exits, `currentScrollProgress` should be 0. Let me re-examine whether the issue is actually upstream.

**After deeper analysis, the real issue is a race condition in `onCleared()`:**

```kotlin
override fun onCleared() {
    super.onCleared()
    saveCurrentProgress()  // launches a coroutine in viewModelScope
}

private fun saveCurrentProgress() {
    if (bookmarkId != null && currentScrollProgress > 0) {  // ← BUG: skips saving 0%
        viewModelScope.launch {
            bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
        }
    }
}
```

**There are two bugs here:**

1. **The `> 0` guard prevents saving 0% progress.** If the user scrolls back to the top (0%), the save is skipped entirely, so the previously-saved furthest-read progress remains in the database.

2. **`viewModelScope.launch` in `onCleared()` may not complete.** When `onCleared()` is called, `viewModelScope` is cancelled, so coroutines launched within it may be cancelled before the network call and DB write finish.

## Affected Files

| File | Role |
|------|------|
| `app/.../ui/detail/BookmarkDetailViewModel.kt` | ViewModel — tracks progress locally, saves on exit |
| `app/.../ui/detail/BookmarkDetailScreen.kt` | Composable — detects scroll position, reports to ViewModel |
| `app/.../domain/BookmarkRepositoryImpl.kt` | Repository — persists progress to API + local DB |
| `app/.../io/db/dao/BookmarkDao.kt` | DAO — local DB update query |
| `app/.../io/rest/ReadeckApi.kt` | API — PATCH endpoint for progress |

## Proposed Solution

### Change 1: Remove the `> 0` guard in `saveCurrentProgress()`

The condition `currentScrollProgress > 0` prevents saving when the user has scrolled back to the top. Change it to allow saving 0%.

**File:** `BookmarkDetailViewModel.kt`

```kotlin
// BEFORE
private fun saveCurrentProgress() {
    if (bookmarkId != null && currentScrollProgress > 0) {
        viewModelScope.launch { ... }
    }
}

// AFTER
private fun saveCurrentProgress() {
    if (bookmarkId != null) {
        // Save even if progress is 0 — user may have scrolled back to top
        viewModelScope.launch { ... }
    }
}
```

### Change 2: Ensure the save completes even when the ViewModel is being cleared

`viewModelScope` is cancelled when `onCleared()` runs, so a coroutine launched there may never finish. Use a scope that outlives the ViewModel. The standard Android pattern is to use `NonCancellable`:

**File:** `BookmarkDetailViewModel.kt`

```kotlin
// BEFORE
override fun onCleared() {
    super.onCleared()
    saveCurrentProgress()
}

private fun saveCurrentProgress() {
    if (bookmarkId != null) {
        viewModelScope.launch {
            bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
        }
    }
}

// AFTER
override fun onCleared() {
    super.onCleared()
    // Use a coroutine scope that won't be cancelled when the ViewModel is cleared
    kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    ).launch {
        saveCurrentProgress()
    }
}

private suspend fun saveCurrentProgress() {
    if (bookmarkId != null) {
        try {
            bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
            Timber.d("Saved final read progress: $currentScrollProgress%")
        } catch (e: Exception) {
            Timber.e(e, "Error saving final progress: ${e.message}")
        }
    }
}
```

Alternatively, if the project uses Hilt and has an application-scoped `CoroutineScope` available for injection, that is the cleaner approach — inject it and use it for fire-and-forget saves.

### Change 3: Also save eagerly in `onClickBack()`

`onClickBack()` already calls `saveCurrentProgress()`, but it then immediately triggers navigation. The save should complete before navigation or use the same `NonCancellable` pattern:

**File:** `BookmarkDetailViewModel.kt`

```kotlin
// BEFORE
fun onClickBack() {
    saveCurrentProgress()
    _navigationEvent.update { NavigationEvent.NavigateBack }
}

// AFTER
fun onClickBack() {
    viewModelScope.launch {
        saveCurrentProgress()  // now a suspend function — completes before navigation
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }
}
```

### Change 4: Handle the init special case for 0% articles

In `init`, there's logic that sets `currentScrollProgress = 1` when the stored progress is 0. This is problematic — if a user reads an article, scrolls back to 0%, and exits, the next open will set `currentScrollProgress = 1` and on exit will save 1% instead of 0%. Remove this special case:

**File:** `BookmarkDetailViewModel.kt`

```kotlin
// BEFORE (in init block)
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    if (bookmark.readProgress == 0) {
        currentScrollProgress = 1  // ← forces non-zero, prevents saving 0 later
    } else {
        currentScrollProgress = bookmark.readProgress
    }
}

// AFTER
is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
    currentScrollProgress = bookmark.readProgress
}
```

## Summary of All Changes

| # | File | Change | Why |
|---|------|--------|-----|
| 1 | `BookmarkDetailViewModel.kt` | Remove `> 0` guard in `saveCurrentProgress()` | Allows saving 0% when user scrolled to top |
| 2 | `BookmarkDetailViewModel.kt` | Use non-cancellable scope in `onCleared()` | Ensures save completes even during teardown |
| 3 | `BookmarkDetailViewModel.kt` | Make `onClickBack()` await save before navigating | Prevents race between save and navigation |
| 4 | `BookmarkDetailViewModel.kt` | Remove `currentScrollProgress = 1` special case in init | Eliminates false non-zero value that masks the bug |

## What This Does NOT Change

- **How progress is tracked during scrolling** — the `LaunchedEffect` in `BookmarkDetailContent` continues to report the current scroll percentage. No changes needed there.
- **How progress is restored on article open** — `initialReadProgress` is loaded from the DB in `init` and passed to the composable. The `LaunchedEffect` that restores scroll position is correct as-is.
- **The API contract** — `PATCH /bookmarks/{id}` with `read_progress` remains unchanged.
- **The "mark as read/unread" feature** — `onToggleRead()` sets progress to 100 or 0 explicitly and is unaffected.

## Edge Cases to Verify

1. **User opens article, doesn't scroll, exits** → progress should remain at whatever it was before (0% for new, or the restored value for previously-read)
2. **User scrolls to 60%, exits** → progress saved as 60%, restored to 60% on reopen
3. **User scrolls to 60%, scrolls back to 0%, exits** → progress saved as 0%, article opens at top on reopen
4. **User scrolls to 100% (bottom)** → progress saved as 100%, article shows as "read" in list
5. **App is killed (process death) while reading** → `onCleared()` fires, save should complete via non-cancellable scope
6. **Photos and videos** → auto-marked as 100%, unaffected by these changes
7. **Network error during save** → caught by try/catch, logged, no crash. Progress is lost for that session but the previous value remains in DB.

## Testing Plan

1. Open an unread article → verify it starts at the top
2. Scroll to ~50%, exit → reopen → verify it restores to ~50%
3. Scroll to ~50%, scroll back to top, exit → reopen → verify it starts at top (0%)
4. Scroll to bottom (100%), exit → verify article shows as "read" in list
5. Open a "read" article, scroll to top, exit → reopen → verify it starts at top
6. Force-kill the app while reading at ~30% → reopen → verify progress is ~30%
