# Port checklist — "With errors" filter + error/no-content badges

**SOURCE = Readeck for Android · TARGET = MyDeck (`com.mydeck.app`).**
Harness: [`mydeck-readeck-port.md`](./mydeck-readeck-port.md) — read it; this is the §8 per-feature checklist that plugs into it. Implementation detail & rationale (only if you need the "why"): the SOURCE repo's [`docs/specs/with-errors-sync-fix-spec.md`](../specs/with-errors-sync-fix-spec.md).

## Source change

One squash commit on Readeck `main`: **`c91cc3f`** — "Fix: \"With errors\" filter + error/no-content card badges (#13)". `git show c91cc3f` in the Readeck checkout is the authoritative diff. Files touched (all under the shared `com.mydeck.app` root; none branding-related):

- **Logic/data:** `domain/BookmarkRepository.kt`, `domain/BookmarkRepositoryImpl.kt`, `domain/usecase/LoadBookmarksUseCase.kt`, `domain/model/BookmarkListItem.kt`, `io/db/dao/BookmarkDao.kt`, `io/db/model/BookmarkListItemEntity.kt`
- **UI:** `ui/list/BookmarkCard.kt` (badge in the download-icon slot + removal of two dead indicator composables)
- **Strings:** `res/values/strings.xml` + 9 locale folders — keys `bookmark_card_has_error`, `bookmark_card_no_content`
- **Guide:** `assets/guide/en/your-bookmarks.md` (Download Status badge note)
- **Tests:** `domain/BookmarkRepositoryImplTest.kt`, `io/db/BookmarkDaoTest.kt`, `domain/usecase/LoadBookmarksUseCaseTest.kt`

What it does, in one line: reconcile `hasServerErrors` on **full** sync (it previously ran only on the incremental path) by centralizing the `has_errors=true` scan in `BookmarkRepository.performFullSync()`; project the error / no-content signals onto the list item; show an error / no-content badge in the card's download-icon slot. `BookmarkDto`, `BookmarkMapper`, and `ReadeckApi` are **not** touched.

## Approach — cherry-pick / patch, not re-development (harness §4)

The two apps share the codebase (identical `com.mydeck.app` package root; only `applicationId`, branding, some config, and CI diverge). Every file in `c91cc3f` **except the strings and the guide** is shared, branding-free code that ports **directly** — this is a file-level patch, not a re-implementation:

1. Determine cherry-pick viability from shared history (`git merge-base` against the Readeck remote, or by comparing the files). If history is shared, **cherry-pick / `git apply` `c91cc3f`**; otherwise copy the change **hunk-by-hunk** per file.
2. `git -C <readeck> diff c91cc3f^ c91cc3f -- <file>` gives the exact hunks. Before any wholesale file copy, diff SOURCE against MyDeck for that file to confirm it's otherwise identical, so a copy can't clobber a MyDeck-only line. (For these files they should differ only by this change.)

## Divergence handling for this port (harness §3/§6) — only two items

1. **Strings** — insert `bookmark_card_has_error` / `bookmark_card_no_content` (English placeholder text) **surgically** into MyDeck's `values/strings.xml` and each MyDeck locale folder. Confirm MyDeck's locale set (SOURCE has 9: `de-rDE, es-rES, fr, gl-rES, pl, pt-rPT, ru, uk, zh-rCN`). **Never** whole-file copy strings.xml (clobbers independently-added keys — harness §7).
2. **Guide** — port the `your-bookmarks.md` badge note into MyDeck's guide, in MyDeck's wording.

No other divergence-map axis is exercised: no migration (below), no notification channel ids, no flavor/variant names, no manifest changes, no branding strings/assets.

## No migration (harness §5 — N/A, confirm)

`hasServerErrors` and `state` are added to the **list projection only** (the SELECT builders + projection POJO); both are **pre-existing columns** on the `bookmarks` table (`BookmarkEntity`). Confirm MyDeck's `BookmarkEntity` already has both — if so, there is no schema/migration work. (If a column were genuinely absent, that would be a real migration → follow harness §5: renumber, regenerate schema JSON, register, migration test.)

## Verify (harness §7)

- MyDeck's gate green: `./scripts/ci-verify.sh` (or `:app:assembleDebugAll` / `:app:testDebugUnitTestAll` / `:app:lintDebugAll` — task names are identical to SOURCE per the divergence map); lint within baseline.
- On device (Pixel 9 via `scripts/install-phone.sh`): fresh sign-in with offline reading **off** → **Filter → With errors → Yes** lists the errored bookmarks straight after metadata sync; errored / no-content cards show the badge; no restart.

## Pre-flight

- [ ] MyDeck on up-to-date `main`; Readeck checkout reachable as SOURCE with `c91cc3f` present.
- [ ] Cherry-pick vs hunk-by-hunk decided from a shared-history check (§4).
- [ ] MyDeck doesn't already carry `c91cc3f`'s change (don't double-apply).
- [ ] `BookmarkEntity` has `hasServerErrors` + `state` columns (→ no migration).
- [ ] MyDeck locale set confirmed.
