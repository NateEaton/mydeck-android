# Port checklist — Offline Content Rework + Multi-select "Available offline"

**Uses:** the methodology in [`mydeck-readeck-port.md`](mydeck-readeck-port.md) (roles, divergence map, gotchas). Read that first.
**Source change set:** MyDeck branch `feat/multi-select-offline` (built on `feat/offline-content-rework`), **18 commits**, 62 files, vs `main`.
**Status — PROVISIONAL.** Written against `feat/multi-select-offline @ 7896d65a` *before* the combined PR merged and *before* Stefan's snapshot testing returned. **Reconcile against the final squash-merged commit + any test-driven fixups before porting.** Re-run `git diff <target-of-squash>~1..<squash-commit>` to confirm the final surface.

> **NOTE (post-merge):** the work later pivoted to Pin/Unpin and squash-merged to MyDeck `main` as commit `27a5fb37` (#203). This provisional checklist predates that — see the refreshed assessment for the final state. Anchor the actual port on `27a5fb37`.

> Direction here = SOURCE MyDeck → TARGET Readeck for Android. If a future change flows the other way, the *logic* is symmetric; the divergence transforms below just invert.

---

## 1. Change set at a glance (logical units, in dependency order)

Apply roughly in this order — later units depend on earlier ones (W2 needs W1's form; multi-select needs W2; W9 needs W2's `source`).

| # | Commit | Unit | Essence |
|---|---|---|---|
| 1 | `bab17290` | **W1** completeness model | new `OfflineContentForm`; guards key off completeness not bare `DOWNLOADED`; **removes** per-add `LoadArticleWorker` legacy stamp |
| 2 | `bd40b00e` | W1 §4.3 | partial-package → DIRTY in `executeBatch` too |
| 3 | `7de9f701` | **W2** part 1 | `ContentSource` (AUTOMATIC/MANUAL) + `content_package.source` column + **migration 16→17** + prune excludes MANUAL |
| 4 | `67c1a7e8` | **W6** counts | "Available offline" count split by provenance (+ strings) |
| 5 | `008faf2c` | **W8** | debug export exposes package/provenance/form facts |
| 6 | `b1819173` | **W6** window | restrict newest-N window to eligible rows (both subqueries) |
| 7 | `c85918e6` | **W4** | Clear-All clears every content form (+ `settings.md`) |
| 8 | `8509b87f` | **W5** | user-initiated sync → REPLACE; stalled-guard reschedule |
| 9 | `6f1e9553` | **W2** cap | absolute storage-cap eviction across both pools (LRU by `lastRefreshed`) |
| 10 | `bfc1848b` | **W9** | MANUAL icon tint (+ string) |
| 11 | `acdae59a` | W2/W9 docs | guide updates |
| 12 | `47bc34fd` | **W3** | idempotent create — reconcile-on-retry + unique work |
| 13 | `f14e0436` | **W7** | foreground-service visibility for offline-content sync |
| 14 | `8ea1a4a0` | multi-select | `updateContentPackageSource` DAO + manager wrapper |
| 15 | `d97806d7` | multi-select | bulk "Available offline" action (+ strings, tests) |
| 16–18 | `2042f0e5` `7896d65a` `349ec3a5` | docs | guide + specs (the specs themselves are MyDeck-pathed; port the *behavior*, adapt wording) |

> Post-pivot additions (not in the table above; see refreshed assessment): `3a94f928` (Pin/Unpin pivot) and `387f2ea2` (open-path commits AUTOMATIC). The final merged source is the squash `27a5fb37`.

## 2. New production files to create in TARGET
- `domain/content/ContentSource.kt` (enum + `resolveOnCommit` sticky-MANUAL)
- `domain/content/OfflineContentForm.kt` (`derive` / `needsContentFetch` / `isPartialPackage`)
- `worker/OfflineContentForegroundInfo.kt` (W7 `ForegroundInfo` helper)
- Tests: `OfflineContentFormTest`, `WorkManagerSyncSchedulerTest`, `SyncSettingsViewModelTest`, plus the multi-select VM tests and DAO/worker test additions.

## 3. Files to REMOVE in TARGET
- `worker/LoadArticleWorker.kt` (deleted in W1) and its `SyncScheduler.scheduleArticleDownload` enqueue path — the per-add legacy stamp. **Confirm the TARGET's add path then routes through the batch worker** (as MyDeck's `createBookmark`/poll now does).

## 4. Divergence transforms (the must-change points)

1. **DB migration — renumber.** MyDeck adds `content_package.source` as **v16→17** (`MIGRATION_16_17`: `ALTER TABLE content_package ADD COLUMN source TEXT NOT NULL DEFAULT 'AUTOMATIC'`). In TARGET this is almost certainly a **different version pair** — bump the TARGET's `@Database(version = …)` by one, write `MIGRATION_<n>_<n+1>`, **regenerate the exported schema JSON** for the TARGET's new version (do **not** copy MyDeck's `17.json`), register it in the TARGET's DB class + DI module, and port the migration test. Also add `source` to the TARGET's `ContentPackageEntity` (`@ColumnInfo(defaultValue = "AUTOMATIC")`).
2. **New strings.** Add to the TARGET's base `strings.xml` **and its locale set** (confirm the TARGET's languages — may differ from MyDeck's 9). **Surgical insert only**, never a whole-file copy. (Final string set changed in the pivot — pin vs "available offline"; take the list from the final diff / refreshed assessment.)
3. **Notification (W7).** Reuse the TARGET's existing sync notification **channel constant** (MyDeck: `SYNC_NOTIFICATION_CHANNEL_ID = "FullSyncNotificationChannelId"`). `OFFLINE_CONTENT_NOTIFICATION_ID = 3` must not collide with the TARGET's metadata-sync notification IDs (MyDeck uses 1, 2). Confirm the TARGET's manifest already declares the `dataSync` FGS type + permission before assuming reuse.
4. **Package/imports.** Rewrite `com.mydeck.app.*` → the TARGET package root across every new/changed file.
5. **Verification + run.** Use the TARGET's build/test/lint task names and device-install path — **not** MyDeck's `assembleDebugAll`/`testDebugUnitTestAll`/`lintDebugAll` or `scripts/install-phone.sh` (confirm the TARGET's equivalents).

## 5. Design decisions a porter MUST preserve (don't "fix" to the literal spec)
- **W1 churn-avoidance (subtle):** eligibility/`needsContentFetch` keys off **committed-package presence + DIRTY, NOT bare `hasResources`** — keying off `hasResources` re-downloads genuinely image-less articles every run. `hasResources` only drives FULL-vs-TEXT *form/icon*.
- **W3:** **newest-record-only** reconcile (`getBookmarks(limit=1, sort=-created)`, adopt iff `url` matches and `created >= attemptTs − ~2min`), because **the server allows duplicate URLs by design** — a deep URL scan would wrongly adopt a prior intentional re-add. Reconcile-query failure **throws → retry** (never blind POST). The bug is **cellular-only**; the **unit tests are the proof** (Wi-Fi can't repro).
- **W5:** `userInitiated` → `REPLACE` for user triggers (add-bookmark, pull-to-refresh, Sync-Now, re-enable-offline), `KEEP` for passive; stalled-guard `retry()` bounded by `MAX_RUN_ATTEMPTS=5`; HTTP-blocked failures stay on the failure path.
- **W7:** `setForeground(getForegroundInfo())` **inside `doWork`**, wrapped in try/catch that **rethrows `CancellationException`** and **logs+continues** otherwise. **NO `setExpedited`** — it throws at enqueue and silently kills downloads (this broke MyDeck once). The download must never depend on notification success.
- **W6:** the eligible-row filter (`hasArticle=1 OR type='photo'`, `contentState!=3`) goes on **both** inner newest-N subqueries (eligibility + prune-overflow).
- **W2:** `resolveOnCommit` never downgrades MANUAL; the policy/window prune is **AUTOMATIC-only**; the **absolute cap** prune spans **both pools**, evicting oldest by `lastRefreshed`, no-op when UNLIMITED.
- **Pin/Unpin (post-pivot — see refreshed assessment):** promote-on-open commits **AUTOMATIC**; `LoadContentPackageUseCase.execute()` default `source` is **AUTOMATIC**; only an explicit Pin commits MANUAL (via the worker, never `execute()`). `BookmarkDao` list `@RawQuery` `observedEntities` must include `ContentPackageEntity`; `isPinned` is reactive via `observePackageSource`. `selectedAllPinned` = any-eligible && all-eligible-pinned (ignores ineligible). Image-less committed package shows the text-only icon (accepted).

## 6. On-device verifications to repeat in TARGET
The ones that caught real issues: (a) **downloads still work** after W7 (Sync-Now → packages land — the regression that broke MyDeck's first W7 attempt); (b) **cap evicts MANUAL** oldest-first across both pools, stops at cap, no over-evict; (c) **Clear-All** zeroes packages + resources + article_content; (d) pin/unpin lands MANUAL + flips AUTOMATIC↔MANUAL without re-fetch, list+reader icons update; (e) opening a picture stays unpinned (AUTOMATIC); (f) W3 normal/double-add yields one bookmark (cellular lost-response is unit-test-proven, not device-reproducible).

## 7. Final reconciliation (before declaring the port done)
- Diff the TARGET against this checklist; confirm every unit landed and every §4 transform applied.
- Re-pull the **final** MyDeck squash-merge commit (`27a5fb37`) and diff it to catch the pivot + any post-snapshot fixups.
- TARGET build/test/lint green; the §6 on-device checks pass.
