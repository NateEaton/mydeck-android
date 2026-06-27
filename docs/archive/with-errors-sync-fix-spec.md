# "With errors" filter + error/no-content badges — issue & fix spec

**Status:** ✅ **Implemented** on `fix/with-errors-sync` (branched off `main`). Verified by the reporter:
a known no-content/errored bookmark shows the error badge, and a from-scratch sign-in with offline
reading disabled returns the correct rows under **Filter → With errors → Yes** straight after the
metadata sync — no content download or restart required.

> **Note:** parts of the analysis below were written before the fix and turned out to be stale —
> the incremental path already populated the flag, and `BookmarkListItem` had been refactored. See
> [Resolution — what actually shipped](#resolution--what-actually-shipped) for the authoritative
> account of the change; the sections above it are kept as the original investigation record.

**NOT** part of `feature/rc3-enhancements`. This file was created untracked on the rc3 working tree and
committed on the fix branch alongside the change.

**Reported by:** project owner, confirmed by reproduction (see below).

---

## Symptom

- Filtering bookmarks by **With errors → Yes** returns **nothing**, even when the server has errored
  bookmarks (repro account: ~200 bookmarks, 29 errored, mostly older).
- Opening one of those bookmarks shows **no extraction-error box** in Details.
- **Long-standing** — reproduces back to MyDeck v0.14.2 across two server versions. It is **not** a
  regression in `feature/rc3-enhancements`: every file in the error path
  (`BookmarkDao`, `BookmarkMapper`, `Bookmark`, `BookmarkDto`, `BookmarkEntity`, `BookmarkRepositoryImpl`
  write/merge, `FilterFormState`, `FilterBottomSheet`, `FilterBar`, the Room DB) is byte-identical to
  the rc2 tag (`2cc8f97`), and the `withErrors` DAO test passes.

## Root cause (confirmed)

- The Readeck **list** endpoint `GET /bookmarks` returns `bookmarkSummary` objects. Per
  `docs/openapi-spec.json`, `bookmarkSummary` has `state`, `loaded`, `has_article`, … but **no `errors`
  array and no `has_errors` flag**.
- `io/rest/model/BookmarkDto.kt` declares `errors: List<String> = emptyList()`, so on list sync it
  always deserializes empty. `domain/mapper/BookmarkMapper.kt` (~line 66) sets
  `hasServerErrors = errors.isNotEmpty()` → **always `false` from the metadata sync**.
- Readeck encodes a **loaded-but-extraction-failed** bookmark as **`state = 0` (LOADED) with a
  non-empty `errors` array** — *not* `state = 1`. The filter query in `io/db/dao/BookmarkDao.kt` is
  `AND (b.state = 1 OR b.hasServerErrors = 1)`, so such bookmarks match **neither** clause → 0 results,
  and Details has no error box (the local row carries no `errors`).
- The `errors` array only arrives via the **content/detail fetch** (`GET /bookmarks/{id}` and the
  offline content sync), which populates `hasServerErrors`. That path is **offline-policy-limited**
  (newest-first), so older errored bookmarks are never flagged → the inconsistency the reporter saw.
- **Evidence:** a debug export of one such bookmark shows the live server `apiResponse` as
  `state = 0`, `errors = ["could not extract content"]`, `has_article = false`, while the stored
  `localState`/`bookmarkMetadata` has `state = LOADED` and no error flag. `BookmarkDebugExporter` makes
  a *direct* `getBookmarkById` API call for `apiResponse`; the repo's `getBookmarkById` reads the
  **local DB**, which is why opening a bookmark shows no error.

## Reproduction (confirmed by reporter)

Account with ~200 bookmarks, 29 errored (mostly older), offline reading **off**:

1. Clear app data / fresh install → sign in → let the bookmark (metadata) sync finish.
2. **Filter → With errors → Yes** → ~0 results. *(the broken state)*
3. Open a known-errored bookmark → Details shows **no** error box.
4. **Enable offline reading** with a policy covering all ~200 (Storage limit high, or Most recent ≥ 200)
   → wait for content sync. **Filter → With errors → Yes** → now shows the 29; Details shows the box.
   - A *narrow* offline policy only flags the errored bookmarks inside its window (reproducing
     "some but not all"; older errors stay hidden).

**Reactivity gap (important):** after the content download populated the flag, the **filter and Details
still did not reflect it until the app was closed and reopened.** The fix must ensure the list query and
Details refresh on the flag write (Room `@Query` Flows should invalidate on the table write — verify the
content-sync write goes through normal invalidation / that the filtered query observes it).

Captured artifacts available from the reporter: a debug JSON export and app logs from the repro.

## Content vs error: the model + badge decision

Two **orthogonal** list-level axes:

- **Content** — readable extracted content present? (`has_article` / local `contentState`, where
  `PERMANENT_NO_CONTENT` already means "nothing to read"). *Already in the list projection.*
- **Error** — server reported a problem? (`errors` non-empty → `hasServerErrors`, or `state = 1`).
  *Not in the list projection today.*

| Content | Error | Meaning |
|---|---|---|
| has article | none | normal |
| has article | **error** | extracted with non-fatal issues (content exists *and* has an error) |
| **no content** | **error** | extraction failed — the common case; **both** axes true |
| no content | none | no article, nothing flagged (rare for articles) |

**Decision (agreed):** a **single** indicator in the download-icon slot, chosen by priority — not two
competing badges:

1. **Error** (`hasServerErrors` or `state = 1`) → **error badge** (wins; covers failed-extraction, which
   is also no-content, and the content-with-errors row).
2. else **no content** (`has_article = false` / `PERMANENT_NO_CONTENT`, no error) → **no-content badge**
   (circle-slash; signals "opens the original page"). A no-content bookmark has nothing to download
   offline, so replacing the download glyph here is appropriate.
3. else → the normal **download / offline-state** icon.

**Out of scope (agreed):** distinguishing error *kinds* at the list level (e.g. "could not extract"
vs HTTP 4xx/5xx). The list summary can't convey it; the kind lives only in the `errors` **message
strings**, which Details already surfaces when the bookmark is opened. Do not try to surface error type
on cards.

## Proposed solution

1. **Populate `hasServerErrors` from the metadata sync** using the existing `has_errors=true` list query
   (`ReadeckApi.getBookmarks(hasErrors = …)` already exists). During sync, fetch the errored IDs and set
   `hasServerErrors = true` on those / `false` otherwise. No content fetch required. There are already
   `UPDATE bookmarks SET hasServerErrors = …` statements in `BookmarkDao.kt` to build on.
2. **Add `hasServerErrors` to the list projection** — `BookmarkListItemEntity`, the projection SQL in
   `BookmarkDao` (the hand-written `SELECT b.…` builders), the `toBookmarkListItem()` mapper, and
   `BookmarkListItem`. (`hasArticle` / `contentState` are already projected, so the **no-content** signal
   needs no new field.)
3. **Reactivity** — ensure the filtered list and Details reflect the flag without an app restart
   (the observed gap above).
4. **Card badge** — implement the error > no-content > download priority in the download-icon slot in
   `BookmarkCard.kt`. **Sequence this after `feature/rc3-enhancements` merges** — that branch rewrote
   `BookmarkCard.kt`, so the badge UI is the *only* part of this work that would conflict. The
   data/sync work (items 1–3) does **not** touch `BookmarkCard.kt`.
5. **Details** — error messages on open already work once the flag/row is correct; no list-level error
   type. (The reporter is fine with detailed error info + any extract-log link appearing only on open.)

## Validation

- `has_errors=true` list query returns reliably across server versions (tested by reporter: `0.22.3`
  and `0.22.3-117-g3b0e80b1`).
- Incremental sync (`updated_since`): the flag must be **cleared** when an error resolves, not just set.
- `state = 1` hard errors are still caught.
- Filter + Details update **without** an app restart.
- Fresh-install repro returns to a clean, fully-flagged state.

## Scope / file overlap

**Data/sync fix (items 1–3): zero overlap with `feature/rc3-enhancements`.** Files —
`io/rest/model/BookmarkDto.kt`, `domain/mapper/BookmarkMapper.kt`, `io/db/dao/BookmarkDao.kt`,
`io/db/model/BookmarkListItemEntity.kt`, `io/rest/ReadeckApi.kt` (param already present),
`domain/BookmarkRepositoryImpl.kt`, `domain/model/BookmarkListItem.kt`, and the sync use-cases/workers
(`FullSyncUseCase`, `LoadBookmarksUseCase`, `io/rest/sync/*`). None are modified by the rc3 branch.

**Card badge (item 4): overlaps `BookmarkCard.kt`** (rewritten in rc3) → do after rc3 merges.

Recommended sequence: rc3 merges → `fix/with-errors-sync` (data/sync, items 1–3) → card badges (item 4).

---

## Resolution — what actually shipped

Implemented on `fix/with-errors-sync` in four commits (spec → data/sync → badge → tests). The codebase
had moved on since this spec was drafted, so several assumptions above were stale. The corrections:

### 1. The real Item 1 bug was the **full-sync** path, not "never populated"

`refreshServerErrorFlags()` (the `GET /bookmarks?has_errors=true` scan) **already existed** and already
ran — but only on the **incremental / multipart** path (`LoadBookmarksUseCase.executeMultipart`). The
**full sync** that bootstraps a fresh install (`BookmarkRepositoryImpl.performFullSync()`, driven by the
workers) **never** reconciled the flag. So the repro (clear data → sign in → full sync) left every row
`hasServerErrors = false` and the filter matched nothing — exactly the symptom.

**Fix:** the reconciliation was **centralized** as `BookmarkRepository.refreshServerErrorFlags()` and is
now called at the end of `performFullSync()` (success path). That single call site covers **every**
full-sync entry point — fresh install, periodic full sync, and the delta-sync→full-sync fallback inside
`FullSyncWorker` (a worker-level call would have missed that fallback, since it returns no `updatedIds`).
`LoadBookmarksUseCase` now **delegates** to the repository method instead of owning a private copy; its
now-unused `readeckApi` dependency was removed.

> The "clear when an error resolves" requirement is satisfied by `replaceServerErrorFlags()` (clear-all
> then set), which both full and incremental paths funnel through. Note `insertBookmarks()` *preserves*
> an existing flag (`existing.hasServerErrors || incoming…`), so `replaceServerErrorFlags` is the only
> authoritative reset — which is why running it after full sync is correct.

### 2. `BookmarkListItem` had been refactored — the projected fields are different

The spec assumed `BookmarkMapper.toBookmarkListItem()` set `hasServerErrors` and that `hasArticle` /
`contentState` were on `BookmarkListItem`. In reality:

- The list mapper is `BookmarkRepositoryImpl.toBookmarkListItem()` (not `BookmarkMapper`), and
  `BookmarkListItem` had been reworked to an `offlineState` enum + `offlineEligible` — it no longer
  carried `hasArticle` / `contentState`.
- So Item 2 surfaced **new** signals: `hasServerErrors` **and** `state` were added to
  `BookmarkListItemEntity` and to the **three** hand-written `SELECT b.…` projection builders in
  `BookmarkDao` (there were three, not two). The mapper folds them into two domain booleans:
  - **`hasError`** = `hasServerErrors || state == State.ERROR` — the combined "with errors" bucket, matching
    the filter's `b.state = 1 OR b.hasServerErrors = 1` (the enum is `State.ERROR(1)`; there is no
    `LOADED_WITH_ERRORS`).
  - **`hasNoContent`** = `contentState == PERMANENT_NO_CONTENT`.

  `BookmarkDto.errors` / `BookmarkMapper` were **not** touched — they were already correct.

### 3. Item 3 (reactivity) needed no code change

The filtered-list `@RawQuery` already declares `observedEntities = [BookmarkEntity, ContentPackageEntity]`,
so the `UPDATE bookmarks SET hasServerErrors …` write invalidates it; Details already reads via
`observeBookmark()` (a Room-backed Flow). Both update without a restart — verified, not modified.

### 4. Item 4 badge — one shared indicator

`OfflineStateIndicator` / `CompactOfflineStateIndicator` were **dead code** (no call sites) and have been
**deleted**; all card variants (Grid, Compact, Mosaic) render through the single
`BookmarkDownloadStatusIndicator`. It now selects one icon by priority: `hasError` → error badge (error
tint) > `hasNoContent` → `Icons.Outlined.Block` circle-slash > existing download/offline-state icon. New content-description strings `bookmark_card_has_error` /
`bookmark_card_no_content` were added (English placeholders in all nine translated files), and both badges
are documented in `guide/en/your-bookmarks.md`.

### Files actually changed

`domain/BookmarkRepository.kt`, `domain/BookmarkRepositoryImpl.kt`, `domain/usecase/LoadBookmarksUseCase.kt`,
`domain/model/BookmarkListItem.kt`, `io/db/dao/BookmarkDao.kt`, `io/db/model/BookmarkListItemEntity.kt`,
`ui/list/BookmarkCard.kt`, `res/values*/strings.xml`, `guide/en/your-bookmarks.md`, and the three test
files (`BookmarkRepositoryImplTest`, `BookmarkDaoTest`, `LoadBookmarksUseCaseTest`). `BookmarkDto.kt`,
`BookmarkMapper.kt`, and `ReadeckApi.kt` were **not** modified. Verified with `./scripts/ci-verify.sh`
(assemble-all + unit tests + lint, all green).
