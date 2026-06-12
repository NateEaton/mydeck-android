# Offline Download Icon: Cached-Content State & Text-Cache Retention

**Status:** Part 1 (cached-content state fix) implemented on `claude/offline-reading-icon-analysis-bsbr5p` (commit `11146cb`). Part 2 (demote-to-text on purge/prune) proposed / deferred. (documented 2026-06-12)
**Origin:** Investigation of why opened-but-not-batch-downloaded bookmarks showed no offline download icon when "offline reading" was disabled, and why content opened with offline reading off was lost entirely when the setting was toggled off.

---

## 1. Background — the offline download icon

Each bookmark card shows a small (14dp) download glyph next to the reading-time estimate, driven by `BookmarkListItem.OfflineState`:

| State | Icon | Meaning |
|---|---|---|
| `NOT_DOWNLOADED` | (none) | No local content |
| `DOWNLOADED_TEXT_ONLY` | outline `DownloadForOffline` | Article HTML cached on-demand (no image package) |
| `DOWNLOADED_FULL` | filled `DownloadForOffline` | Full managed package (text + images) |

The state is derived purely from persisted data, with **no dependency on the offline-reading setting** (`BookmarkRepositoryImpl.deriveOfflineState`):

```kotlin
contentState != DOWNLOADED      -> NOT_DOWNLOADED
hasResources == true            -> DOWNLOADED_FULL      // content_package row, images present
else (DOWNLOADED + null/false)  -> DOWNLOADED_TEXT_ONLY  // text cache, no package
```

`contentState` lives on `bookmarks`; `hasResources` comes from a `LEFT JOIN content_package`. The list query (`getBookmarkListItemsByFilters*`) is a reactive Room `@RawQuery(observedEntities = [BookmarkEntity::class])`, so any write to the `bookmarks` table re-emits the list.

Two ways content is cached:

- **On-demand open** — `BookmarkDetailViewModel.fetchContentOnDemand` → `LoadArticleUseCase.execute` fetches `/article` HTML and is *intended* to stamp `contentState = DOWNLOADED` with **no** package (→ outline). Setting-independent.
- **Managed / batch** — workers and `enqueuePriorityPackageDownload` (only when offline reading is **on**) → `LoadContentPackageUseCase` → `ContentPackageManager.commitPackage`, which writes `hasResources = true` (→ filled).

The three-state icon, `deriveOfflineState`, and `enqueuePriorityPackageDownload` were all introduced together in commit `a422e37` ("v0.13.0 Slice 6").

---

## 2. Issue — `DOWNLOADED` state silently dropped on cached articles

### 2.1 Symptoms

- Open an article (offline reading **off**), return to the list → **no outline icon**, even though the content is genuinely cached and renders offline.
- The Details/debug panel shows the tell-tale combination: **Content State: `NOT_ATTEMPTED`** *and* **Has Local Article Content: `true` (N chars)**.
- A *previously opened* bookmark still opens offline (cached HTML in Room); a *never-opened* one shows an offline error. So the icon is wrong even though offline availability is real.
- With offline reading **on**, opened bookmarks *do* show an icon (filled) — masking the bug.

### 2.2 Root cause

`LoadArticleUseCase.storeDownloadedArticle` persists via
`bookmarkRepository.insertBookmarks(...)` → `insertBookmarksWithArticleContent` → `insertBookmarkWithArticleContent` → **`upsertBookmark`**.

For an already-synced row, `upsertBookmark`'s `INSERT OR IGNORE` is a no-op and it falls through to `updateBookmarkMetadata`, whose `SET` clause **deliberately omits `contentState`** (so metadata-only full syncs cannot clobber local download status). The `article_content` row is written separately and unconditionally — so the HTML persists but the explicit `DOWNLOADED` state is discarded, leaving the row at `NOT_ATTEMPTED`.

Result: `deriveOfflineState(NOT_ATTEMPTED, …)` → `NOT_DOWNLOADED` → no icon; and every reopen re-enters `fetchContentOnDemand` (redundant re-fetch online; cached render offline). The `DOWNLOADED_TEXT_ONLY` branch was effectively **unreachable** via the article path. Managed packages were unaffected because `commitPackage` writes state through a direct `updateContentState` UPDATE.

### 2.3 Fix (implemented)

In `insertBookmarkWithArticleContent`, after `upsertBookmark(bookmarkToInsert)`, write the resolved state directly:

```kotlin
upsertBookmark(bookmarkToInsert)
updateContentState(
    bookmarkToInsert.id,
    bookmarkToInsert.contentState.value,
    bookmarkToInsert.contentFailureReason
)
```

`bookmarkToInsert` already reflects the existing preserve/heal logic, so preserved rows are re-written with the same value — metadata-only behavior is unchanged, and `updateBookmarkMetadata` is intentionally left alone (adding `contentState` there would reintroduce the sync-clobber bug). The chokepoint covers `LoadArticleUseCase` and the annotation-refresh callers.

Coverage: `BookmarkDaoTest.ContentBearingUpsertTest` — (a) content-bearing insert persists an explicit `DOWNLOADED` on an existing row (fails pre-fix); (b) an incoming default `NOT_ATTEMPTED` still preserves an existing `DOWNLOADED`.

Effect: opened articles show the outline icon regardless of the offline-reading setting, and reopening short-circuits on the `DOWNLOADED` guard (instant cached render, no spurious offline-error path).

---

## 3. Follow-up — demote-to-text on purge / prune (proposed)

With Part 1 in place, `DOWNLOADED_TEXT_ONLY` is reachable for the first time. That exposes a second divergence from user expectation around the lifecycle of *managed* content.

### 3.1 Current behavior

- **Disable offline reading** → `SyncSettingsViewModel.runManagedContentPurge` → `purgeManagedOfflineContent` → for each `getBookmarkIdsWithOfflinePackages()` (rows with `hasResources = 1`) → `ContentPackageManager.deleteContentForBookmark` → **full wipe**: deletes files, `content_resource`, `content_package`, `article_content`, cached annotations, and resets `contentState = NOT_ATTEMPTED`.
- **Policy prune** (`OfflinePolicyEvaluator.selectForPruning`, e.g. a bookmark ages out of the date-range / Newest-N window) → same `deleteContentForBookmark` full wipe.
- Opening a bookmark **while offline reading is on** promotes it to a full package (`enqueuePriorityPackageDownload`). So a bookmark you merely *read* becomes *managed* — and is therefore swept up by both purge paths.

### 3.2 Why it diverges from expectation

Expectation: *"content I've downloaded/read stays readable offline, and shows at least an outline icon, regardless of the offline-reading toggle."*

Reality: managed teardown is binary — full package → nothing. There is no "degrade to text" step, so:

- Turning offline reading off deletes the **text** of articles you'd actually read, not just their images.
- Pruning an aged-out article you'd read removes it entirely instead of leaving a lightweight text cache.
- Only pure text caches created **while offline reading was off** (no package row) survive — which is why behavior looked inconsistent across the toggle.

The heavy part of offline storage is images (`content_resource`); article text is comparatively tiny.

### 3.3 Proposed solution

Introduce a **demote-to-text** operation (strip resources, keep text) and use it where managed content is currently *deleted*:

- Delete image files, `content_resource` rows, and the `content_package` row (or set `hasResources = false`).
- **Keep** `article_content` and leave `contentState = DOWNLOADED`.

Apply it in:
- `purgeManagedOfflineContent` (offline reading disabled), and
- the prune path (`selectForPruning` consumers).

Result: every previously-downloaded/read article remains readable offline and shows the **outline** icon; only images are reclaimed. Airplane mode: all such articles still open (text); images simply don't load.

### 3.4 Design decisions to confirm

1. **Scope of retention.** Demote *all* managed packages to text on disable, or only ones the user actually opened (e.g. `readProgress > 0`)? Retaining text for hundreds of never-read batch articles is cheap but means "disable" no longer frees that text. Recommendation: gate on "opened" to match the user's framing ("content I've opened").
2. **`hasResources` representation after demote.** Leave an explicit `content_package` row with `hasResources = false`, or drop the row (→ `hasResources = null`)? Both render outline. Note the storage/size queries (`getManagedOfflineStorageSize`, `getBookmarkIdsWithOfflinePackages`, `calculateImageSize`) filter on `hasResources = 1`, so either is self-consistent — but pick one deliberately so a demoted row isn't re-counted or re-pruned.
3. **Re-promotion.** A demoted (text-only) bookmark that re-enters the managed window (or is reopened with offline reading on) should re-acquire images via the normal package path. Confirm `LoadContentPackageUseCase`'s `DOWNLOADED` guard doesn't block re-fetch for a text-only row (it currently early-returns on `DOWNLOADED`; demotion may need to mark such rows `DIRTY`, or the re-promotion path must force-refresh).
4. **Annotations.** `deleteContentForBookmark` also clears cached annotations; demote should retain them alongside the text.

### 3.5 Touch points

- `domain/content/ContentPackageManager.kt` — new `demoteToTextCache(bookmarkId)` beside `deletePackage`.
- `ui/settings/SyncSettingsViewModel.kt` — `purgeManagedOfflineContent` calls demote instead of delete (subject to 3.4.1).
- prune consumers of `OfflinePolicyEvaluator.selectForPruning`.
- DAO size/selection queries — verify behavior for demoted rows (3.4.2).

---

## 4. References

- `BookmarkRepositoryImpl.deriveOfflineState`, `insertBookmarks`
- `BookmarkDao.insertBookmarkWithArticleContent`, `upsertBookmark`, `updateBookmarkMetadata`, `updateContentState`
- `LoadArticleUseCase.storeDownloadedArticle`, `LoadContentPackageUseCase`, `ContentPackageManager`
- `OfflinePolicyEvaluator`, `SyncSettingsViewModel` (purge), `BookmarkDetailViewModel.fetchContentOnDemand` / `enqueuePriorityPackageDownload`
- Introducing commit for the three-state icon + promote-on-open: `a422e37`
