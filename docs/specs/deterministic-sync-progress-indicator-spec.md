# Spec: Deterministic Sync Progress Indicator

## Overview

Replace the indefinite `CircularProgressIndicator` shown during initial bookmark and highlights sync with a `LinearProgressIndicator` displayed at the bottom edge of the top app bar. For bookmark sync, the indicator is deterministic (actual fraction). For highlights sync, the indicator is indeterminate (continuous sweep) due to a lack of a pre-fetch total count from the API.

---

## Background

During first launch after authentication, the app runs two sequential syncs:

1. **Bookmark sync** — fetches all bookmarks in pages of 50 via `performFullSync()` in `BookmarkRepositoryImpl`
2. **Highlights sync** — fetches all annotations in pages of 50 via `reconcileAllAnnotations()` in `HighlightsRepository`

Currently both show a `CircularProgressIndicator`. This spec replaces both with a `LinearProgressIndicator` placed at the bottom of the top app bar, matching Material 3 convention.

---

## API Header Availability

### Bookmarks

`performFullSync()` already reads `Total-Count`, `Total-Pages`, and `Current-Page` from the first page response headers (lines ~722–733 in `BookmarkRepositoryImpl.kt`). The total page count is available after the first round-trip (~150ms). A deterministic fraction (`pagesCompleted / totalPages`) is feasible from page 2 onward.

### Highlights

`reconcileAllAnnotations()` uses offset-based pagination and terminates when `page.size < HIGHLIGHTS_PAGE_SIZE`. It does not read any total-count headers. The Readeck server may return `Total-Count` on the `/bookmarks/annotations` endpoint — this has not been verified. Until verified against a live server, the highlights progress indicator must be **indeterminate**.

> **Verification step (before Phase 2):** Add a temporary log of `response.headers()["Total-Count"]` in `reconcileAllAnnotations()` and confirm against a live Readeck server. If the header is present, Phase 2 can upgrade to a deterministic fraction.

---

## Design

### Placement

A `LinearProgressIndicator` spanning full width, immediately below the `TopAppBar`. `TopAppBar` has no built-in progress slot; the standard approach is a `Column` wrapping the bar area:

```kotlin
Column {
    TopAppBar(title = { ... }, ...)
    if (showProgress) {
        LinearProgressIndicator(
            progress = progressFraction,  // null → indeterminate
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

Use `LinearProgressIndicator(modifier = ...)` (no `progress` lambda) for indeterminate mode; use `LinearProgressIndicator(progress = { fraction }, modifier = ...)` for determinate mode.

The indicator is visible only while a sync is actively running. It disappears on completion or failure. There is no animation to hide/show it — it simply composes in and out.

---

## Implementation Plan

### Phase 1 — Bookmarks (deterministic)

**1. Progress model**

Add to `BookmarkRepositoryImpl` (or a shared sync state holder):

```kotlin
sealed interface BookmarkSyncProgress {
    data object Idle : BookmarkSyncProgress
    data class Running(val page: Int, val totalPages: Int) : BookmarkSyncProgress
    data object Done : BookmarkSyncProgress
}

private val _syncProgress = MutableStateFlow<BookmarkSyncProgress>(BookmarkSyncProgress.Idle)
val syncProgress: StateFlow<BookmarkSyncProgress> = _syncProgress.asStateFlow()
```

Emit `Running(currentPage, totalPages)` after each page is stored inside `performFullSync()`. Emit `Idle` when complete or on failure.

**2. ViewModel**

Expose `syncProgress` from whichever ViewModel drives the loading indicator (confirm the observer chain — likely `BookmarkListViewModel`). Map `Running(page, total)` to a `Float` fraction: `page / total.toFloat()`.

**3. UI**

In the composable hosting the top app bar for the bookmark list screen, replace or supplement the existing `CircularProgressIndicator` with:

```kotlin
val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
Column {
    TopAppBar(...)
    when (val p = progress) {
        is Running -> LinearProgressIndicator(
            progress = { p.page / p.totalPages.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        else -> Unit
    }
}
```

### Phase 2 — Highlights (indeterminate → potentially deterministic)

**Before coding:** verify `Total-Count` header availability (see verification step above).

**If header is absent (default assumption):** replace the existing `CircularProgressIndicator` with an indeterminate `LinearProgressIndicator` shown while `HighlightsSyncState.Running`. No fraction is needed.

**If header is present:** mirror the Phase 1 pattern using `loadedCount / totalCount.toFloat()` as the fraction. `HighlightsSyncState.Running` already carries `loadedCount`; add `totalCount: Int` to it.

---

## Out of Scope

- Progress persistence across process death
- Cancellation UI
- Per-bookmark content download progress (multipart sync)
- Tablet / adaptive layout adjustments

---

## Files Likely Touched

| File | Change |
|---|---|
| `BookmarkRepositoryImpl.kt` | Emit `BookmarkSyncProgress` state from `performFullSync()` |
| `BookmarkListViewModel.kt` | Expose `syncProgress` flow (or relay from repository) |
| `BookmarkListScreen.kt` | Replace `CircularProgressIndicator` with `LinearProgressIndicator` |
| `HighlightsRepository.kt` | Replace `CircularProgressIndicator` trigger; possibly add total-count header read |
| Highlights screen/ViewModel | Wire indeterminate (or deterministic) linear indicator |
