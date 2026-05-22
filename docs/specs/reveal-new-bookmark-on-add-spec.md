# Reveal Newly Added/Synced Bookmark On List

**Status:** Deferred (documented 2026-05-22)
**Origin:** UX regression noticed alongside the premature-open content poisoning fix.

---

## 1. Problem

Before [#154 — Add swipe actions for bookmark cards](https://github.com/NateEaton/MyDeck/pull/154), a bookmark added via the FAB (or arriving during initial sync) was visible on-screen immediately. After #154, the lazy list keeps its scroll position across emissions (`key(filterKey) { rememberLazyListState() }`), so a new bookmark inserted at index 0 in default `ADDED_NEWEST` sort is off-screen unless the user is already near the top.

[#160 — sync progress](https://github.com/NateEaton/MyDeck/pull/160) added a partial workaround: when `isInitialLoading` transitions `true → false`, the screen increments `scrollToTopTrigger`. This covers initial sync but not the FAB-add or share-intent paths.

`docs/specs/premature-open-content-poisoning-fix-spec.md` originally proposed a second one-line trigger on `CreateBookmarkUiState.Success` to extend "scroll to top" to the FAB case. That is rejected here — it solves the symptom for one sort/view combination and misbehaves for others.

---

## 2. Reframe: "scroll to top" → "reveal the new bookmark"

`scrollToTopTrigger` is a happy-path proxy that only works when sort = `ADDED_NEWEST` *and* the new bookmark belongs to the current view. The real primitive is **scroll until the new bookmark is on-screen**. Behaviour matrix:

| Scenario | Behaviour |
|---|---|
| Initial sync completes | Scroll to top. No single new bookmark to focus on; default sort puts newest at top; affordance = "you have new content." Existing behaviour, keep. |
| FAB-add, default sort, current view matches | New bookmark is at index 0 → scroll to top (equivalent to scroll-to-its-index). |
| FAB-add, non-default sort, current view matches | New bookmark lands at its sorted position → scroll to **that index**, not to top. |
| FAB-add, current view excludes the bookmark (Archive view while saving non-archived, label filter that doesn't match, type filter that doesn't match, search filter that doesn't match) | Bookmark never appears in the list → do nothing. |
| Background sync brings in another client's add | No user-initiated reveal → no scroll. Yanking the list while the user is reading is worse than letting the new card slide in off-screen. |
| Save & View (FAB → VIEW action) | User navigates straight to detail. On return, the bookmark is in place. **Do not** reveal on return — the user explicitly chose to leave the list; arriving back at their previous scroll position is correct. |

---

## 3. Architecture

A new "pending reveal" channel from ViewModel to Screen, parallel to the existing `scrollToTopTrigger`. The `scrollToTopTrigger` stays for title-click and initial-load (those are explicit "go to top" intents, not "reveal a specific item").

### 3.1 ViewModel

```kotlin
data class PendingReveal(
    val bookmarkId: String,
    val expiresAt: Long  // epoch millis; ~30s after creation
)

private val _pendingReveal = MutableStateFlow<PendingReveal?>(null)
val pendingReveal: StateFlow<PendingReveal?> = _pendingReveal.asStateFlow()

fun onPendingRevealConsumed() {
    _pendingReveal.value = null
}
```

Set whenever a save flow knows (or learns) the new bookmark's id. The Screen calls `onPendingRevealConsumed()` after scrolling.

### 3.2 Screen

```kotlin
// In BookmarkListView, alongside the existing scrollToTopTrigger LaunchedEffect:
LaunchedEffect(pendingReveal, bookmarks) {
    val target = pendingReveal ?: return@LaunchedEffect
    if (System.currentTimeMillis() > target.expiresAt) {
        onPendingRevealConsumed()
        return@LaunchedEffect
    }
    val index = bookmarks.indexOfFirst { it.id == target.bookmarkId }
    if (index >= 0) {
        lazyListState.animateScrollToItem(index)  // or lazyGridState
        onPendingRevealConsumed()
    }
}
```

Notes:
- `animateScrollToItem` may want a small leading offset so the card lands below the top app bar / FilterBar rather than flush with the screen edge.
- Wire identically for `LazyVerticalGrid` and `LazyColumn` branches in `BookmarkListView`.

### 3.3 TTL

```kotlin
// When _pendingReveal is set, also launch:
viewModelScope.launch {
    delay(30_000)
    if (_pendingReveal.value?.expiresAt == expiresAt) {
        _pendingReveal.value = null
    }
}
```

The TTL is the silent handler for "current view excludes the bookmark" — the list never produces an item with that id, the LaunchedEffect never fires, the reveal expires and clears. No error UI; user just doesn't see a scroll.

---

## 4. Sources of the bookmark id

Three save actions exist in [`handleCreateBookmarkAction`](app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt#L664-L715):

### 4.1 `VIEW` — already synchronous

`bookmarkRepository.createBookmark(...)` returns the id. After `waitForBookmarkReady`, set `_pendingReveal = PendingReveal(id, now+30s)` and send the navigation event. On return to the list, the LaunchedEffect resolves it. (See §2 — but per the matrix we *don't* want to reveal on return for the VIEW action. So either skip setting it here, or set it but consume-without-scroll on first list-emission-after-return. Cleanest: don't set it.)

### 4.2 `ADD` / `ARCHIVE` — async via worker

`CreateBookmarkWorker.enqueue(...)` returns control immediately; the id is known only after the worker hits the server and the next sync brings the row in. Three options to bridge the gap.

#### Option 1 — WorkManager output Data (recommended)

`CreateBookmarkWorker` writes the new id into `Result.success(workDataOf("bookmarkId" to id))`. VM holds the `WorkRequest.id`, observes its `WorkInfo.outputData` flow via `WorkManager.getWorkInfoByIdFlow(...)`, sets `_pendingReveal` when the id arrives.

- ✅ Idiomatic, no URL-uniqueness assumption.
- ✅ Survives a worker retry — output data is set on terminal `SUCCEEDED` state.
- ⚠ Requires touching the worker (small change in its `doWork()` return path).
- ⚠ The VM needs to track in-flight WorkRequest ids it should observe.

#### Option 2 — URL-match heuristic

VM stashes the URL at enqueue: `_pendingReveal = PendingReveal(url=url, ...)`. The reveal record carries a URL instead of an id. Screen scans `bookmarks` for one with matching URL.

- ✅ No worker change.
- ❌ Breaks down on duplicate-URL saves. Conflicts with the duplicate-URL spec already in flight (`docs/specs/duplicate-url-detection-and-review-spec.md`) which acknowledges dups happen.
- ❌ `BookmarkListItem` may not expose `url` cheaply at the list-render layer; need to verify.

#### Option 3 — Synchronous create for ADD/ARCHIVE too

Switch ADD/ARCHIVE to the same `bookmarkRepository.createBookmark()` path that VIEW uses (which returns the id), with the worker only as an offline fallback.

- ❌ Out of scope for this work. Changes retry/offline semantics for the primary save path.

**Recommended: Option 1.**

---

## 5. Implementation Plan

1. `CreateBookmarkWorker.doWork()`: on success, return `Result.success(workDataOf(KEY_BOOKMARK_ID to id))`. Define `KEY_BOOKMARK_ID` as a `const val` companion-object member.
2. `BookmarkListViewModel`:
   - Add `PendingReveal` data class, `_pendingReveal` MutableStateFlow, `pendingReveal` exposed StateFlow, `onPendingRevealConsumed()`.
   - `handleCreateBookmarkAction(ADD/ARCHIVE)`: after `CreateBookmarkWorker.enqueue`, observe the returned `WorkRequest.id`'s `WorkInfo` flow; when state = `SUCCEEDED` and output has `KEY_BOOKMARK_ID`, set `_pendingReveal`. Launch the TTL coroutine in the same scope.
   - `handleCreateBookmarkAction(VIEW)`: leave alone (per §4.1, don't reveal on return).
3. `BookmarkListView`:
   - Accept `pendingReveal: PendingReveal?` and `onPendingRevealConsumed: () -> Unit` parameters.
   - Add the `LaunchedEffect(pendingReveal, bookmarks)` block from §3.2 to both `LazyVerticalGrid` and `LazyColumn` branches.
   - Pick a sensible scroll offset (probably `scrollOffset = -<top-app-bar height + FilterBar height + 1 row of spacing>` so the revealed card sits a comfortable margin below the chrome).
4. `BookmarkListScreen`: wire `viewModel.pendingReveal.collectAsState()` and pass it plus `viewModel::onPendingRevealConsumed` into `BookmarkListView`.
5. **Keep** `scrollToTopTrigger` for title-click and initial-load — those are different intents.
6. Tests:
   - Unit test the VM: enqueue ADD → simulate worker WorkInfo SUCCEEDED with output → `_pendingReveal` populated; TTL clears after delay.
   - Compose test for `BookmarkListView`: set `pendingReveal` with id present in `bookmarks` → list scrolls to that index; with id absent → no scroll, eventual consume on TTL.

---

## 6. Out of Scope / Follow-up Questions

- **Multiple rapid FAB saves.** With the recommended design, only the most recent `pendingReveal` survives because each new save overwrites `_pendingReveal.value`. Acceptable. If rapid-fire reveals become a real use case, switch to a queue.
- **Share-intent saves.** Flow through the same `handleCreateBookmarkAction` path; covered automatically.
- **Unarchive / restore actions.** A bookmark moving back into the current view could plausibly want the same reveal treatment. Not addressed here — the user-facing trigger and surface are different (snackbar UNDO, not FAB), and a different spec should decide.
- **Tablet expanded layout.** `BookmarkListView` is shared; the scroll-to-item logic should work identically on tablet. Verify the offset calculation doesn't break when the FilterBar isn't visible (single-column phone landscape, etc.).
- **Scroll offset tuning.** May want to expose this as a constant in `Dimens.kt` rather than hardcoding inside `BookmarkListView`.

---

## 7. Files Likely Touched

| File | Change |
|------|--------|
| `worker/CreateBookmarkWorker.kt` | Emit `bookmarkId` in `Result.success` output Data. Add `KEY_BOOKMARK_ID` constant. |
| `ui/list/BookmarkListViewModel.kt` | `PendingReveal` data class; `_pendingReveal` flow; observe worker output for ADD/ARCHIVE; TTL job. |
| `ui/list/BookmarkListScreen.kt` | Collect `pendingReveal`; pass into `BookmarkListView`. |
| `ui/list/BookmarkListScreen.kt` (`BookmarkListView`) | New params + `LaunchedEffect` blocks for both LazyColumn and LazyVerticalGrid branches. |
| `ui/theme/Dimens.kt` (maybe) | Reveal scroll offset constant. |
