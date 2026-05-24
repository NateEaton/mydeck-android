# Premature-Open Content Poisoning Fix

**Status:** In progress (2026-05-22)
**Branch:** `fix/premature-open-content-poisoning`

---

## 1. Problem

When a user opens a freshly added bookmark **before** the Readeck server has finished extracting the article (or video transcript), MyDeck permanently marks the bookmark as having no extractable content. Even after the server completes processing — and even though Readeck itself happily opens the same bookmark — MyDeck keeps falling back to "View Original" forever.

User reproduction: add a link → as soon as the bookmark card appears in the list (no title yet, etc.), tap it → the on-demand fetch fires against a still-processing bookmark → bookmark is poisoned.

---

## 2. Root Cause

1. The Readeck DTO carries both `state` (enum: LOADING / LOADED / ERROR) and `loaded` (boolean). While the server is still extracting, the bookmark arrives with `state = LOADING`, `loaded = false`, **and** `hasArticle = false`.

2. `BookmarkDetailViewModel.initializeBookmark()` sees `contentState = NOT_ATTEMPTED` and unconditionally calls `fetchContentOnDemand()`.

3. `LoadArticleUseCase.execute()` interprets `!hasArticle` as terminal: it writes `contentState = PERMANENT_NO_CONTENT` and returns `PermanentFailure`. The fallback `LoadContentPackageUseCase.execute()` has the identical trap.

4. `BookmarkDao.insertBookmarkWithArticleContent()` preserves the existing `contentState` on metadata-only syncs, so when the server later updates the bookmark to `hasArticle = true`, the local row stays `PERMANENT_NO_CONTENT` forever.

---

## 3. Fix

Three layers. All ship together.

### 3.1 Don't stamp permanent while the server is still processing

In both `LoadArticleUseCase.execute()` and `LoadContentPackageUseCase.execute()`, replace the bare `!bookmark.hasArticle → PERMANENT_NO_CONTENT` branch with:

```kotlin
if (!bookmark.hasArticle) {
    if (bookmark.state == Bookmark.State.LOADING) {
        return Result.TransientFailure("Bookmark still being processed by server")
    }
    // Server has finished (LOADED or ERROR) and confirmed no article — stamp permanent
    bookmarkDao.updateContentState(bookmarkId, PERMANENT_NO_CONTENT.value, ...)
    return Result.PermanentFailure(...)
}
```

The `state` enum is the authoritative signal for server-side processing. The DTO's redundant `loaded` boolean isn't used as a gate, because an inconsistent `state == ERROR && loaded == false` would otherwise be misclassified as transient and looped forever in the polling UI.

Defense in depth — even after the UI guard in 3.3, batch workers and other callers go through the same use cases and need the same protection.

### 3.2 Heal already-poisoned rows

**3.2.a — One-shot Room migration.** Reset `contentState = NOT_ATTEMPTED, contentFailureReason = NULL` for every row where `contentState = 3 (PERMANENT_NO_CONTENT) AND hasArticle = 1`. Surgical, runs once, no loop risk. This is the cleanup for current users carrying poisoned rows.

**3.2.b — Per-sync heal guarded by `updated`-advance.** In `BookmarkDao.insertBookmarkWithArticleContent()`, when the existing row is `PERMANENT_NO_CONTENT` and the incoming DTO has `hasArticle = true AND state = LOADED AND updated > existingUpdated`, clear the stamp (reset to `NOT_ATTEMPTED`, null reason). The `updated > existingUpdated` clause is the loop-breaker: Readeck bumps `updated` when extraction completes, so a subsequent genuine re-failure won't re-heal until the server reports another change.

### 3.3 UI: wait through the LOADING state instead of fetching

In `BookmarkDetailViewModel.initializeBookmark()`, **before** running the `contentState` switch, call a new helper `awaitServerProcessingIfNeeded(id, initial)`. If `initial.state == LOADING`, the helper enters a polling loop and returns only once the server transitions to a terminal state.

Polling loop:
- Set `_contentLoadState = ContentLoadState.Loading(0.10f)` immediately so the UI shows the determinate progress bar.
- Sleep on backoff (`2s → 4s → 8s → 15s`, capped at 15s thereafter).
- On each tick, call `bookmarkRepository.refreshBookmarkMetadata(id)` then re-read `getBookmarkById(id)`. Advance the progress bar by +0.05 per tick, capped at 0.50 so the subsequent on-demand fetch (which uses 0.55 → 1.0) can advance smoothly.
- Exit the loop as soon as `state != LOADING` and return the refreshed bookmark.
- No explicit timeout: the user said they prefer the UI to wait. `viewModelScope` cancellation handles back-navigation; the existing detail screen behaviour handles user choice to switch to Original mode independently.

Once the helper returns, the existing `contentState` switch runs against the refreshed bookmark:

| Server signal at exit | Existing behaviour (unchanged) |
|---|---|
| `state = LOADED && hasArticle = true` | Falls into `else` branch → `fetchContentOnDemand()` (now safe — `hasArticle` is true so no stamping risk). |
| `state = LOADED && hasArticle = false` | Falls into `else` branch → `fetchContentOnDemand()` → `LoadArticleUseCase` correctly stamps `PERMANENT_NO_CONTENT` (state != LOADING so the new guard lets it through). |
| `state = ERROR` | Falls into `else` branch → `fetchContentOnDemand()` → use case stamps `PERMANENT_NO_CONTENT` (state != LOADING). Failed UI auto-switches to Original mode. |

---

## 4. Acceptance

- Adding a new link and tapping its card immediately should show a progress indicator and then open in reader mode once the server finishes — no poisoning.
- A bookmark already stamped `PERMANENT_NO_CONTENT` with `hasArticle = 1` (from a prior poisoned session) opens correctly after upgrade (migration heals it).
- A genuine no-content URL (e.g. a static image link tagged as article) still ends in `PERMANENT_NO_CONTENT` after the server finishes processing — only the *timing* of when we stamp changes.
- No re-heal loop: a bookmark where the server keeps reporting `hasArticle = true` but the fetch keeps failing for a genuine reason stays stamped after the second pass (until `updated` advances).

---

## 5. Files Touched

| File | Change |
|------|--------|
| `domain/usecase/LoadArticleUseCase.kt` | Guard `!hasArticle` branch with server-processing check (3.1). |
| `domain/usecase/LoadContentPackageUseCase.kt` | Same guard (3.1). |
| `io/db/MyDeckDatabase.kt` | New Room migration: heal `PERMANENT_NO_CONTENT && hasArticle=1` rows (3.2.a). |
| `io/db/dao/BookmarkDao.kt` | Per-sync heal in `insertBookmarkWithArticleContent`; `ContentStateInfo` now also carries `updated` (3.2.b). |
| `io/db/DatabaseModule.kt` | Register `MIGRATION_15_16`. |
| `ui/detail/BookmarkDetailViewModel.kt` | `awaitServerProcessingIfNeeded` polling helper invoked at the top of `initializeBookmark` (3.3). |

No new user-visible strings: the wait state reuses the existing determinate progress bar; `ContentLoadState.Failed.reason` is internal and never rendered.
