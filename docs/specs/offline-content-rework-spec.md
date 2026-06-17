# Spec: Offline Content Model Rework + Sync Reliability & Visibility

**Status:** Draft for implementation. Branch: `feat/offline-content-rework`.
**Applies to:** MyDeck (`com.mydeck.app`) and, by port, upstream **Readeck for Android** (`org.readeck.apps.android`) — shared offline/create code. Write changes to be as repo-agnostic as possible.
**Companion docs (read first):**
- `debug/2026-06-14/offline-sync-diagnosis.md` — full evidence-based diagnosis (code refs, repro logs, live DB).
- `docs/archive/unified-offline-content-spec.md` — the authoritative existing design this rework completes.
- `docs/archive/offline-download-icon-and-text-cache-retention-spec.md` — icon model + the *shelved* demote-to-text decision.
- Distinct from `docs/specs/duplicate-url-detection-and-review-spec.md` (user-facing "URL exists" warning) — **not** part of this work.

---

## 1. Problem (one sentence)

`contentState = DOWNLOADED` is overloaded across three physical storage forms (legacy text cache / HTML-only package / full package), and every guard keys off that bare flag instead of off content completeness — which strands added articles as text-only, lets partial packages get stuck, makes "Clear All" incomplete, and (amplified by flaky networks) makes prune/sync unreliable and creates duplicate bookmarks. Offline-content background work also has no OS-level visibility.

See the diagnosis doc for the full evidence chain. This spec is the fix.

## 2. Goals

1. A content model where guards key off **completeness**, not bare `DOWNLOADED`, so incomplete content is always upgradeable and nothing gets stranded.
2. Decide **A2 vs A1** empirically (instrumented), starting with A2 (simplest), with a measured go/no-go gate.
3. Make background **create idempotent** so retries after a network blip never duplicate.
4. Make **"Clear All Offline Content"** actually clear everything; keep policy/disable semantics correct.
5. Make policy **prune/sync reliable** regardless of trigger and resilient to flaky networks.
6. Fix the **NEWEST_N window** to count only offline-eligible bookmarks; surface a **live on-device count** in settings.
7. Give offline-content sync **system-notification / foreground visibility** consistent with metadata sync (compliance-critical for the rebrand attestation).
8. Make offline/package state **inspectable** via the debug export.

## 3. Non-goals

- User-facing duplicate-URL warning (separate spec).
- A managed text-only "demote-to-text" tier (explicitly shelved — do not reintroduce).
- New offline policies (collections, custom rolling windows) — deferred per unified spec.

---

## 4. Target content model (applies to BOTH A1 and A2)

The existing `BookmarkEntity.ContentState` enum (`NOT_ATTEMPTED / DOWNLOADED / DIRTY / PERMANENT_NO_CONTENT`) is retained (no schema migration). The fix is to introduce **one** derived notion of offline completeness and route **all** guards through it.

### 4.1 Single source of truth for "offline form"

Introduce a derived helper (extend the existing `BookmarkRepositoryImpl.deriveOfflineState` / `OfflinePolicyEvaluator`) computing form from `(contentState, content_package presence, hasResources)`:

```
NONE            — no package, no text cache               (contentState NOT_ATTEMPTED, no package)
TEXT_CACHE      — text only, no images                    (DOWNLOADED, no content_package OR hasResources=false)
FULL_PACKAGE    — text + images on disk                   (DOWNLOADED/DIRTY, content_package hasResources=true)
PERMANENT_NONE  — server confirmed no content             (PERMANENT_NO_CONTENT)
```

### 4.2 Guards that MUST switch from `contentState==DOWNLOADED` to completeness

| Site | Today | Change to |
|---|---|---|
| `LoadContentPackageUseCase.execute` early-return (`:71`) | `AlreadyDownloaded` if `DOWNLOADED` | Return `AlreadyDownloaded` only if **FULL_PACKAGE**; otherwise fetch/upgrade |
| Batch eligibility `BookmarkDao.getBookmarkIdsEligibleForNewestNContentFetch` (`:892`) | `contentState IN (0,2)` | Eligible if **not FULL_PACKAGE** (and offline-eligible type) |
| Reader open branch `BookmarkDetailViewModel` (`:328`) | `DOWNLOADED` → display only | If not FULL_PACKAGE and offline reading on → run the upgrade path (A1) / build package (A2) |
| `LoadArticleUseCase.execute` early-return (`:37`) | n/a under A2 | A1: only short-circuit for the text it owns; A2: path removed |

### 4.3 Partial-package rule (kills the "stuck HTML-only" class)

A multipart fetch that yields HTML but **zero** image resources must **not** commit a silent terminal `FULL_PACKAGE`. Options (implementer picks, document choice):
- Commit as `TEXT_CACHE` **and** mark `DIRTY` so the next maintenance run re-attempts the resources, **or**
- Treat "expected resources but got none due to transport failure" as a `TransientFailure` (no commit) so it's retried.

Either way the completeness guards (§4.2) guarantee it is re-attempted rather than stranded.

> **CHOSEN (Phase 1) — option (a), discriminated by `parseWarnings`.** `LoadContentPackageUseCase.fetchAndCommit` commits the HTML (so the text is immediately readable) and then **overrides `contentState` to `DIRTY`** iff the result is a *transport-partial* package, decided by `OfflineContentForm.isPartialPackage(...)`: HTML present **and** zero resources **and** `BookmarkSyncPackage.parseWarnings` non-empty (the parser records a warning for every dropped/short resource part), or a `Picture` with zero resources. A **genuinely image-less** article (HTML, zero resources, **no** warnings) stays `DOWNLOADED` and is treated as complete. This is what makes "re-attempt" terminate instead of churning forever. A full stream abort already returns `MultipartSyncClient.Result.Error/NetworkError` → `DIRTY` (pre-existing), so both partial paths converge on `DIRTY`.

---

## 5. Phase plan & sequencing

> Hard ordering: **Phase 0 → Phase 1 (gate) → Phase 2**. Phase 2 items are independent of each other and can be parallelized across subagents once Phase 1 lands.

### Phase 0 — Instrumentation + A2 spike + assessment (decision gate)

**Objective:** determine whether building a full package on open is fast enough to first-paint to justify dropping the text-first layer (A2).

1. **Instrument open latency** (reuse the existing `READPOS first-visible … tOpenMs` signal in `BookmarkDetailWebViews`). Emit a single structured log line per open:
   `OPENPERF path=<TEXT_FIRST|PACKAGE_DIRECT> tFirstVisibleMs=<n> htmlBytes=<n> imageCount=<n> packageBytes=<n> net=<wifi|cell> warm=<bool> bookmarkId=<id>`
2. **Build the A2 path behind a dev flag** (`PACKAGE_DIRECT` open): on open, fetch+build the package via `LoadContentPackageUseCase` instead of `LoadArticleUseCase`.
   - **Key risk to resolve in the spike:** the multipart fetch currently parses/commits the whole response (HTML + all images) before the reader renders. For A2 to be viable, the reader must paint HTML **as soon as the HTML part is available**, not after all images. The spike must either (a) render HTML-first from the package (stream/commit HTML before resources, images fill in via `OfflineContentPathHandler` as they land), or (b) measure and accept the all-parts latency. Document which.
3. **Run the comparison matrix** (same articles both paths): {short text, long text, image-heavy} × {wifi, cellular} × {cold, warm}. Capture median and p90 `tFirstVisibleMs` per path. Use a Haiku subagent to drive the device and collect/aggregate the `OPENPERF` lines via adb.
4. **Decision criteria (defaults — confirm with maintainer before deciding):**
   - **Keep A2** if `PACKAGE_DIRECT` median first-visible is within **≤ 250 ms** of `TEXT_FIRST` **and** p90 absolute first-visible **≤ ~1.5 s on wifi / ≤ ~2.5 s on cellular**.
   - Otherwise **revert the A2 spike and implement A1**.
5. **Output:** a short `OPENPERF` results table appended to this spec + a recorded decision.

> **RESOLVED 2026-06-15 — Gate decision: A1.** Maintainer signed off on A1; A2 (`PACKAGE_DIRECT`) rejected. HTML-first streaming not pursued.

#### Phase 0 results (OPENPERF)

Method: compile-time `OpenPathConfig.PATH` toggle + a debug-only `OpenPerfTracker` emitting the `OPENPERF` line at the reader's first-visible (READPOS ready-true). Pixel 9, debug snapshot, **Wi-Fi only** — cellular was not measurable because adb runs over Wi-Fi (disabling Wi-Fi drops the device). Cold opens driven via the `navigateToBookmarkDetail` intent (list→detail→BACK, representative + flat stack); error bookmarks (`hasServerErrors=1`) excluded; ~200-bookmark test account; samples self-classified by logged `htmlBytes`/`imageCount`.

| Path | n | median | p90 | max |
|---|---|---|---|---|
| TEXT_FIRST cold | 12 | 272 ms | ~293 ms | 347 ms |
| PACKAGE_DIRECT cold (clean) | 15 | 836 ms | ~1416 ms | 3540 ms |
| warm floor (both paths) | — | ~80–170 ms | — | — |

Gate (both required): median within ≤250 ms of TEXT_FIRST **and** p90 ≤ ~1.5 s Wi-Fi.
- Median delta **564 ms** (> 250 ms) → **fails** (~2×). p90 ~1416 ms → passes marginally (Wi-Fi). Gate is AND → **A2 fails**.

Findings:
- TEXT_FIRST first-paint is **flat (~270 ms)** regardless of size (paints text, images lazy-load). PACKAGE_DIRECT first-paint **scales with `packageBytes`** (whole multipart fetched before paint): the 3.6 MB article took 3540 ms.
- A2 failure mode **degrades to View Original** (live WebView) instead of article text, and a failed/slow A2 fetch **leaves a stuck `DOWNLOADED`/`DIRTY` row with no package** — the §4.3 class reproduced on a healthy network.
- Cellular (unmeasured) only widens the gap (full multipart vs lightweight text fetch).

Consequence: **Phase 1 takes the A1 branch**; the §9.1 "A1 → form-based scoping" resolution applies. The Phase 0 spike scaffolding (`OpenPathConfig`, `OpenPerfTracker`, and their VM/WebView wiring) is removed as Phase 1 begins.

### Phase 1 — Lock the content model

**If A2 (gate passed):**
- Open builds a package directly; **retire the on-demand text-cache caching path** (`LoadArticleUseCase.execute` for caching; per-add `LoadArticleWorker` text stamp). `LoadArticleUseCase` may remain only for annotation-HTML refresh (`refreshHtmlForAnnotations`) if still needed.
- Remove the per-add `enqueueArticleDownload` → for a freshly added bookmark, rely on the batch worker (newest-first) and promote-on-open-equivalent (open builds package).
- **DECIDED (2026-06-15): opening an article always builds a full package, regardless of the offline-reading setting**, so "opened ⇒ offline-readable ⇒ iconed" holds deterministically. The *managed vs user-opened* distinction (whether opened content is protected from prune/disable, and how it is bounded) is the remaining Fork-B question — see §9.1.

**If A1 (gate failed):**
- Keep text-first open. **Open while offline reading is off → text cache** (not a full package); **open while offline reading is on → text-first, then promote** to a full package.
- **Fix promote-on-open** so it fires for already-text-cached articles: the reader's `DOWNLOADED` branch must, when the form is not `FULL_PACKAGE` and offline reading is on, run `enqueuePriorityPackageDownload` (today it only fires from `fetchContentOnDemand`, which a `DOWNLOADED` row skips).
- Remove the per-add `LoadArticleWorker` legacy stamp (or have it set a state that does **not** block the batch/promote upgrade), so added articles aren't stranded.
- **Prune/disable scope:** operate only on full packages (`hasResources=1`); leave on-demand text caches alone (per §9.1). No provenance flag.

**Both:** apply §4.2 guard changes and the §4.3 partial-package rule. Add unit tests proving an incomplete form is selected for upgrade and a full package is not re-fetched.

> **IMPLEMENTED (Phase 1 / W1) — A1 branch.** Single source of truth: `OfflineContentForm` (`domain/content/OfflineContentForm.kt`) with `derive(contentState, hasPackage, hasResources)` for the storage form and `needsContentFetch(contentState, hasPackage)` for the completeness guard. Guard sites routed through it:
> - `LoadContentPackageUseCase.execute` (`:71`) — `AlreadyDownloaded` only when `DOWNLOADED && getContentDir != null`; a legacy text cache (DOWNLOADED, no package) now falls through and upgrades.
> - `BookmarkDao` eligibility queries — `getBookmarkIdsEligibleForNewestNContentFetch`, `getBookmarkIdsEligibleForContentFetch`, `getBookmarkIdsForDateRangeContentFetch` — switched from `contentState IN (0,2)` to `contentState != 3 AND (contentState = 2 OR NOT EXISTS content_package)`. `OfflinePolicyEvaluator.needsOfflinePackage` routes through `needsContentFetch`; `OfflinePolicyBookmark` gained `hasContentPackage` (any committed package) alongside `hasOfflinePackage` (full).
> - `BookmarkDetailViewModel` — the Article `DOWNLOADED` branch now promotes-on-open (`enqueuePriorityPackageDownload`) when offline reading is on and there is no committed package; `fetchContentOnDemand`'s promote gate keys off `getContentDir != null` (committed package) rather than `hasResources`.
> - `LoadArticleUseCase.execute` (`:37`) — short-circuits only when its own text cache is present (`getArticleContent != null` for a `DOWNLOADED` row), so a `DIRTY` row still refreshes.
> - Per-add legacy stamp **removed**: `LoadArticleWorker` + `SyncScheduler.scheduleArticleDownload` deleted; `createBookmark`/`pollForBookmarkReady` now route adds through the batch package pipeline (`scheduleBatchArticleLoad`) so a freshly-added article gets a full package, not a stranding text stamp.
>
> **KEY DESIGN DECISION — eligibility keys off package presence + DIRTY, not bare `hasResources`.** The literal "eligible if not `FULL_PACKAGE` (hasResources)" reading would re-download a *genuinely image-less* committed package on every batch run forever (a new churn bug, ironically the "never settles" symptom). So `needsContentFetch` keys off **(committed package present, DIRTY)**: a committed package that is not DIRTY is complete (full **or** legitimately image-less); `hasResources` only drives the FULL-vs-TEXT *form* label (icons / reader image base URL). The §4.3 transport-partial case is funneled through DIRTY, so it is still re-attempted. (Prune/disable scope is unchanged and still operates on `hasResources=1` full packages only, per §9.1.)
>
> **Tests:** `OfflineContentFormTest` (derive + needsContentFetch + isPartialPackage matrix, incl. the added-article lifecycle ending FULL_PACKAGE); updated `OfflinePolicyEvaluatorTest.needsOfflinePackage` cases (legacy text cache → eligible; committed package → not); updated `BookmarkRepositoryImplTest` create-bookmark assertion (batch package, not legacy worker). No new user-facing strings or user-guide changes in W1.
>
> **Port note (Readeck for Android):** all W1 changes are repo-agnostic — `OfflineContentForm`, the DAO SQL, and the guard refactor carry no MyDeck-specific package name, applicationId, or notification-channel id. Nothing here diverges for the port.

### Phase 2 — Roll in the remaining fixes (parallelizable)

W2–W9 below. Each is independently specified and testable. **W2 (provenance) lands first** — W4/W6/W9 build on it; W3/W5/W7/W8 are independent.

---

## 6. Workstreams (Phase 2)

### W2 — Content provenance: Automatic vs Manual pools (Fork B, explicit) — **supersedes §9.1 A1 resolution**

**Why.** Phase-1 field testing (2026-06-15) showed the §9.1 A1 "form-as-signal, no provenance flag" resolution does not hold: **promote-on-open** turns any article opened while offline reading is on into a full package indistinguishable from a policy download, so the rolling prune — and any add or periodic prune that triggers it — **evicts hand-picked offline reads**. The "open it while offline is *off* to keep it" workaround is backwards and yields text-only. Track provenance explicitly instead (the "explicit pinned vs managed" arm of Fork B, diagnosis §7).

**Model — two values, by how a full package was acquired:**
- **AUTOMATIC** — built by the policy (batch newest-N / date-range). Matches the settings copy *"Downloads content automatically based on your settings below."* (`sync_offline_enable_desc`). Rolling window; **prunable**.
- **MANUAL** — built by an explicit user action while offline reading is **on**: **promote-on-open** (opening a not-yet-downloaded article) **or** the multi-select **"Available offline"** action (see `multi-select-offline-download-spec.md`). **Protected — never auto-pruned by the policy window.** (Open-while-offline-*off* yields only a perf text cache, not a package — it is not in either pool.)

Provenance is **orthogonal** to W1's `OfflineContentForm` (form = *what's on disk*; provenance = *why it's there*). It is meaningful only for committed full packages; **on-demand text caches are inherently user-origin**, so a text cache promoted to a full package becomes **MANUAL** (this is how pre-offline manual opens stay protected once re-opened).

**Storage.** Additive column on `content_package` — `source TEXT NOT NULL DEFAULT 'AUTOMATIC'` — a single forward Room migration (no data loss). Existing rows default to AUTOMATIC (conservative: a previously user-opened package is treated as prunable until re-opened, which re-marks it MANUAL — nothing is wrongly *kept*, only possibly *droppable*, and only until the next open). Set at commit time: `BatchArticleLoadWorker` policy download → `AUTOMATIC`; `enqueuePriorityPackageDownload` / promote-on-open (offline reading on) → `MANUAL`; multi-select "Available offline" → `MANUAL`.

**Pruning & counting.**
- Prune (`OfflinePolicyEvaluator.selectForPruning` + the `getBookmarkIdsWithOfflinePackages` / `getOfflinePolicyBookmarks` `hasResources=1` filters) operates on `source = 'AUTOMATIC'` **only**.
- The policy **target N** (newest-N count / date-range membership) counts **AUTOMATIC only**; MANUAL is outside the window and does not reduce automatic downloads (so "newest 100" + 14 manual = 114 on disk — accepted).
- The **absolute storage cap** (`getOfflineMaxStorageCap`, UI "Maximum storage cap") bounds **AUTOMATIC + MANUAL combined** (maintainer decision). It is the **only** condition under which MANUAL content is auto-removed: when total usage crosses the cap, the secondary-cap prune path evicts oldest-first across **both** pools (LRU). Update `sync_offline_max_storage_cap_desc` ("Applies to Most recent and Last options.") to reflect that it caps total offline storage incl. manual.

**Stats (refines W6 live count).** The Sync-Settings "Available offline" figure becomes a **total** of on-device full packages with two indented sub-counts; reactive query (extend `observeDetailedSyncStatus`), split on `source`:
```
Available offline            114
    Automatic                100
    Manual                    14
```
(Pure text-only caches are not "available offline" — they lack images — so they are not counted here.)

**Teardown.**
- **Disable offline reading → full teardown: clears BOTH pools** (decided 2026-06-15 — revisit, or add a "keep Manual on disable" setting, if users ask). Matches the current observed disable behavior.
- **Clear All** (W4) → clears everything (both pools + text caches), unchanged.
- **Archive auto-clear → gated on the "Include Archive" offline-scope setting (decided 2026-06-15).** If Include Archive (`getOfflineContentScope().includesArchived`) is **on**, archived bookmarks are in offline scope, so **do not** auto-clear their content on archive (either pool). If it is **off**, auto-clear on archive as today. (This makes archive-clear consistent with what the offline policy already considers in-scope.)

**Icons (refines W9).** Presence guarantee unchanged (any content ⇒ icon). MANUAL full packages render the filled "available offline" glyph in a **distinct color/tint** to signal "you picked this"; AUTOMATIC = default filled; outline = text/partial. Exact color → W9 / design.

**Flip-on-touch — RESOLVED (2026-06-15).** An already-downloaded **AUTOMATIC** item flips to the manual pool **only via the explicit multi-select "Available offline" action** (a cheap state write, no re-download). Merely **opening** it does **not** flip it — reading policy content doesn't re-classify it. (This is already the case structurally: promote-on-open only fires for items that are *not* yet a full package, so opening an AUTOMATIC package is a provenance no-op.)

**What creates MANUAL content — RESOLVED (2026-06-15): R1, two-value model.** The "opening is a performance feature" framing applies **only when offline reading is disabled** (open → on-demand text cache, images lazy from network — *not* offline storage). When offline reading is **enabled**:
- **Open a not-yet-downloaded article → promote-on-open → `MANUAL`** (protected from prune). This is the original-complaint resolution, now explicit.
- **Multi-select "Available offline" → `MANUAL`** (protected) — the same pool.
- Opening an already-downloaded **AUTOMATIC** package is a provenance no-op (per Flip-on-touch above); only multi-select flips it.

So provenance is exactly **two values — `AUTOMATIC` (prunable) vs `MANUAL` (protected)**; no third `SELECTED`/`OPENED` value. The accepted consequence: the MANUAL pool grows as the user reads (every offline-on open keeps that article), bounded only by the absolute storage cap (which evicts oldest-first across both pools — §"Pruning & counting"). Open-while-offline-disabled remains a pure perf text cache and never enters either pool.

**Tests.** Prune skips MANUAL; absolute-cap path evicts across both pools; promote-on-open & multi-select set MANUAL while batch sets AUTOMATIC; counts split incl. transient over-target; disable-offline clears both; migration defaults existing rows to AUTOMATIC. New/changed strings → all 10 language files; user guide (`settings.md`) updated for the split count + manual-survives-prune behavior.

**Port note (Readeck for Android).** Repo-agnostic — a `content_package` column, query filters, and a settings count row; no package-name / applicationId / channel-id divergence.

### W3 — Idempotent background create (duplicate fix)

**Root cause:** `BookmarkRepositoryImpl.createBookmark` (`:444`) POSTs with no dedup; `CreateBookmarkWorker` is enqueued non-unique and retries up to 3× — a POST that reached the server but lost its response is re-POSTed → duplicate (with duplicated label).

**Network landscape (confirmed with the reporting user, 2026-06-17):** this is specifically a **cellular / unstable-connectivity** phenomenon — the POST commits server-side and then the connection drops mid-response, so the client never reads the `bookmark-id`. It does **not** reproduce on a stable LAN/Wi-Fi (the response never gets a chance to be lost), so the **unit tests are the proof**; an on-device double-add over Wi-Fi only exercises the enqueue-coalescing path.

**Design (as implemented):**
1. **Coalesce enqueues:** `CreateBookmarkWorker.enqueue` uses `enqueueUniqueWork(uniqueWorkName(url), ExistingWorkPolicy.KEEP, request)`, where `uniqueWorkName(url) = "create_" + UUID.nameUUIDFromBytes(url.toByteArray())`. A double-tap / double-share of the same URL coalesces to one worker (KEEP = the in-flight worker wins; its retry path covers the lost-response case). Distinct URLs get distinct names and never collide.
2. **Reconcile before re-POST — retry only.** The attempt timestamp (`System.currentTimeMillis()`) is stamped into the worker input `Data` at enqueue and is preserved unchanged across retries, so it is a stable baseline. On a retry run (`runAttemptCount > 0`) the worker passes it to `BookmarkRepository.createBookmark(..., attemptTimestampMs)`; on the first attempt it passes `null` and the happy path is a single POST, unchanged.
3. **Adopt rule — the *single most-recent* bookmark only.** The Readeck server allows the same URL to be added multiple times **by design** (intentional re-adds), so "this URL exists somewhere recent" is **not** a valid signal. The repo queries `getBookmarks(limit = 1, sort = -created)` (no `ReadeckApi` change — Readeck's `GET /bookmarks` has only a full-text `search`, unreliable for exact-URL) and adopts that newest bookmark **iff** `newest.url == url` **and** `newest.created >= attemptTimestamp − skewMargin`. The created-recency clause is what distinguishes "our lost-response add actually landed" from "a *previous* add of the same URL that merely happens to still be the newest." If adopted, run the existing local-insert/hydrate path against that id and **skip the POST**; otherwise POST.
4. **Fail-closed on an unconfirmable check.** If the reconcile query itself fails (network still flaky), it throws → the worker returns `Result.retry()` rather than POSTing — so we never re-create until we've confirmed the add did *not* already land.
5. **Labels/favorite/archive against the reconciled id.** Labels ride the create DTO, so an adopted bookmark already carries them → we do **not** re-apply (this is what kills the duplicated-label symptom). Favorite/archive are applied by the worker against the adopted-or-created id as before.

**Decisions / deviations from the earlier draft of this section:**
- **No separate "client request id"** — the WorkManager work UUID already correlates retry runs in logs, and Readeck's create DTO accepts no idempotency key, so a generated id would be dead weight. The attempt timestamp does the real work.
- **`skewMargin ≈ 2 min`** absorbs client/server clock skew (NTP keeps this to seconds). Its only side effect: a deliberate re-add **within ~2 min** of a prior add (when the re-add fails) adopts the prior copy — effectively a rapid double-add, which is benign.
- **Accepted edge case (user-confirmed):** deliberately re-adding a URL that is still your newest bookmark, when the re-add hits a network failure, adopts the existing copy and skips the intended duplicate. In practice this only arises during active testing.
- **Skew-free alternative (documented for future reference, not implemented):** anchor recency to the HTTP `Date` response header (server "now") instead of the client attempt timestamp, eliminating the cross-clock comparison entirely. Deferred — the margin is simpler and more than sufficient.

**Tests (all unit/Robolectric):** (a) lost-response-after-server-create then retry → adopt, no second POST, one bookmark/one label; (b) genuine failure then retry → no match → single POST; (b′) newest matches URL but is stale (created before the attempt) → POST the intended new copy; (c) `enqueue` uses unique work + KEEP with a stable per-URL name; plus worker-wiring (retry passes the timestamp, first attempt passes `null`) and reconcile-query-failure → propagates (worker retries).

### W4 — Purge / Clear-All semantics

**Confirmed defect:** "Clear All Offline Content" (`SyncSettingsViewModel.onConfirmClearOfflineContent` → `runManagedContentPurge` → `purgeManagedOfflineContent` → `getBookmarkIdsWithOfflinePackages`, filtered `hasResources=1`) leaves text caches behind. Disable-offline retention of text caches is **intended** per the unified spec; the **explicit Clear All** is not.

**Design:**
- Add a true clear-all path that removes **every** content form: all `content_package` rows + resources + files, all on-demand `article_content`, cached annotations, and resets `contentState = NOT_ATTEMPTED`. (Extend `ContentPackageManager.deleteAllContent` to also clear `article_content` — verify it does; today it calls `deleteAllArticleContent` + `resetAllContentState`, so wire the Clear-All button to this instead of the managed-only purge.) Scope is content only — it does **not** clear the Coil cover/thumbnail image cache (separate store).
- Keep `runManagedContentPurge` (managed-only) for the **disable-offline** and policy-prune paths.
- Under **A2** with package-on-open: revisit whether disable-offline should also remove user-opened packages (Fork B / §9).

**Tests:** after Clear All → 0 packages, 0 resources, 0 `article_content`, all eligible rows `NOT_ATTEMPTED`. After disable-offline → managed packages gone, intended retained content per the chosen model.

### W5 — Sync reliability

1. **Manual sync must run content sync.** `WorkManagerSyncScheduler.scheduleBatchArticleLoad` uses `ExistingWorkPolicy.KEEP` (`:56`), so a lingering/backed-off instance silently drops a user-initiated content sync. For **user-initiated** triggers (Sync-Now, pull-to-refresh), ensure a run actually happens — use `REPLACE` (or check work state and enqueue if not actively RUNNING). Keep `KEEP` only for passive/background enqueues where a run is already in flight.
2. **Stalled-guard must not silently give up.** `BatchArticleLoadWorker` breaks after `MAX_STALLED_RETRIES` and returns `Result.success()` without rescheduling (`:158-171`), swallowing per-item transient failures. Change: distinguish **"no eligible remain"** (success) from **"eligible remain but couldn't fetch"** (return `Result.retry()` so WorkManager backs off and resumes). Guard against infinite churn with bounded backoff; do not retry on permanent/HTTP-blocked failures.

**Tests:** Robolectric/unit around enqueue policy by trigger; worker returns `retry` when eligible-but-failed vs `success` when truly drained.

### W6 — NEWEST_N window correctness + live on-device count

1. **Window correctness:** the "newest N" subquery counts **all** non-deleted bookmarks (`BookmarkDao.kt:897-903`, `:916-922`), so videos/links/`PERMANENT_NO_CONTENT` consume slots and crowd out eligible articles. Restrict the window to offline-eligible rows (`hasArticle = 1 OR type='photo'`, exclude `contentState = 3`). Apply consistently to both the eligibility and prune-overflow queries.
2. **Live count:** the sync-settings "Available offline" figure must reflect **current** on-device full packages (`COUNT` of `hasResources=1`), a live/observed query — not the policy target. It is expected and acceptable that this temporarily exceeds the target (e.g., 103 vs target 100) between batch prunes; per-item 1-for-1 trimming is **not** required. Wire it to a reactive query (extend `observeDetailedSyncStatus`) so it updates after prune. **Under W2 this becomes a total with AUTOMATIC + MANUAL sub-counts — see W2.**

**Tests:** window excludes non-article/no-content rows; count reflects actual full packages including a transient over-target state.

### W7 — System-notification / foreground visibility for offline-content sync (compliance-critical)

**Gap:** metadata sync surfaces a system notification for every trigger; offline-content workers (`BatchArticleLoadWorker`, priority downloads, `DateRangeContentSyncWorker`) run without OS-level visibility. The rebrand store attestation about foreground visibility of background activity must be accurate. Logs already show `FullSyncWorker: Failed to promote … to foreground` (`ForegroundServiceStartNotAllowedException`, Android 12+), so the strategy must comply with background-start restrictions.

**Design:**
- Run offline-content sync as a **foreground service** with a user-visible notification when it does real work, gated on the same user "sync notifications" preference as metadata sync. Use `setExpedited` / `getForegroundInfoAsync` with the correct `foregroundServiceType` (`dataSync`) and a proper notification channel.
- Respect Android 12+ rules: when started from the background and foreground promotion is disallowed, fall back to expedited work quota rather than crashing/logging-and-continuing-silently; ensure either a notification shows or the work defers to an allowed window — never invisible background network work when the user expects visibility.
- Audit `FullSyncWorker`'s existing foreground-promotion failure and fix it under the same approach so metadata and content sync are consistent.
- New notification strings → add to `values/strings.xml` and **all 10** language files (English placeholder).

**Tests:** instrumented/manual check that each offline-content trigger posts the notification when enabled; no `ForegroundServiceStartNotAllowedException`.

### W8 — Debug export enhancement

`BookmarkDebugExporter` uses `getBookmarkById` (bare entity) so `hasLocalArticleContent`/`localArticleContentLength` are always false. Load the content join and add package facts: `content_package` presence, `hasResources`, resource count + total bytes, on-disk file list, `offlineBaseUrl`. Makes every test-matrix cell verifiable by inspection.

### W9 — Bookmark-card icon semantics

Keep the goal: **any on-device content ⇒ an icon on the card.** Retain `deriveOfflineState` (none / outline=text-or-partial / filled=full). Under **A2**, "outline" becomes rare (only an incomplete package mid-upgrade); decide whether to keep the two-glyph distinction or collapse to a single "downloaded" glyph. Either is acceptable provided the presence guarantee holds. Update `your-bookmarks.md` if semantics change.

---

## 7. Cross-cutting requirements

- **Localization:** every new/changed string added to `values/strings.xml` + all 10 language files (English placeholder) per CLAUDE.md.
- **User guide:** update `app/src/main/assets/guide/en/settings.md` (offline reading, Clear All behavior, live count, sync notifications) and `your-bookmarks.md` (icon semantics) for user-visible changes.
- **Verification before each commit:** `./gradlew :app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll`.
- **Cross-repo:** keep changes repo-agnostic where possible; after MyDeck lands, port to Readeck for Android. Flag any divergence (package name, applicationId, notification channel ids).

---

## 8. Deterministic test matrix (substitute for soak testing)

Run each cell and assert the single expected outcome; verify via the enhanced debug export (W8), not by eyeballing the WebView. No dependence on background timing.

Dimensions:
- **Storage form:** NONE / TEXT_CACHE / FULL_PACKAGE / PERMANENT_NONE
- **Offline reading:** off / on
- **Open path:** online / offline (airplane mode)
- **Lifecycle event:** none / policy prune / archive auto-clear / disable-offline / Clear-All

Representative assertions:
- Add article (offline on) → after maintenance, ends `FULL_PACKAGE` (not stranded TEXT_CACHE). *(W1/Phase1, the core fix.)*
- Open article not in policy (offline on) → becomes `FULL_PACKAGE` (promote-on-open / A2 build).
- Multipart with image parts failing → does **not** end as a terminal full package; re-attempted next run (§4.3).
- Clear All → 0 packages + 0 resources + 0 `article_content`; all eligible `NOT_ATTEMPTED` (W4).
- Disable offline → managed packages gone; intended retained content per chosen model (W4).
- newest-N window with N videos in newest range → still downloads N eligible articles; count reflects reality (W6).
- Create over a dropped response → exactly one bookmark, one label (W3).
- Any offline-content trigger with notifications enabled → system notification shown; no FGS exception (W7).
- Settings count shows transient over-target (e.g., 103) then trims after batch prune (W6).

---

## 9. Open decisions (confirm before implementing the dependent parts)

### 9.1 Fork B — provenance of opened content — RESOLVED (2026-06-15), conditional on the Phase 0 gate

> **SUPERSEDED 2026-06-15 — see §6 W2.** The A1 "form-as-signal, no provenance flag" resolution below was implemented in Phase 1 and **failed field testing**: promote-on-open converts any offline-on open into a prunable full package, so hand-picked offline reads are evicted by the rolling prune. Replaced by explicit **AUTOMATIC vs MANUAL** provenance (W2). The text below is retained for history.

The resolution depends on whether A2 or A1 is chosen, and in **neither case do we add a "pinned" provenance flag** — the content **form** is the signal.

**If A2 (gate passes) → unified pool.** Every open builds a full package (decided). All packages are one pool: policy prune (when offline reading is on) and disable-offline both operate on **all** packages; opening an aged-out article while offline reading is on can get it pruned again later; disabling offline reading wipes all managed packages. This matches prior team direction (shelved demote-to-text doc).
- *Sub-question — DECIDED (2026-06-15): defer.* When offline reading is **off** there is no active policy, so opened packages grow until "Clear All." An LRU/size cap is **deferred** (rely on Clear All for now); revisit as a future enhancement if real-world storage growth warrants it.

**If A1 (gate fails) → form-based scoping, no flag.** Two forms coexist; prune and disable-offline operate **only on full packages** (`hasResources=1`); **on-demand text caches survive** (this is exactly the existing unified-spec purge/prune filter, so less work). Consequence the maintainer wants:
- Open while offline reading **off** → **text cache** (not a full package) → **survives prune**.
- It becomes prunable only once **promoted** to a full package, which happens two ways: (a) opened while offline reading is **on** (promote-on-open), or (b) it falls in the policy window and the batch worker downloads a full package for it. (Batch-promoted items are in-window, so prune won't evict them until they age out — consistent.)
- **Reconciliation:** the "every open builds a full package regardless of setting" decision is therefore **A2-only**. Under A1, open-while-offline-off reverts to a text cache (that is what makes "text caches survive prune" work). Update §5 Phase 1 A1 branch accordingly.

**Clear All (both models) — RESOLVED:** sweeps full packages **and** non-package on-demand text caches (article content + resources + files + cached annotations) and resets `contentState`. It does **not** touch the Coil cover/thumbnail image cache (separate store; card art may re-fetch from network).

### 9.2 Other open items — RESOLVED 2026-06-15

- **A2 first-paint strategy** (§5 Phase 0): moot — A2 rejected (see Phase 0 results under §5). HTML-first streaming not pursued.
- **Decision thresholds** (§5 Phase 0 step 4): applied as written (median ≤250 ms delta from TEXT_FIRST **and** p90 ≤ ~1.5 s Wi-Fi). A2 failed the median criterion (564 ms delta).

---

> **Sequencing:** **W2 lands first in Phase 2** (after Phase 1 W1) — it is the provenance foundation that W4 (Clear-All scope), W6 (split count), and W9 (Manual icon tint) build on. W3/W5/W7/W8 are independent and can proceed in parallel with W2.

## 10. Delegation guidance (Opus orchestrator → subagents)

- **Opus (keep):** content-model design + guard refactor (W1), **provenance model + prune/teardown/cap semantics (W2, design-bearing)**, the A2 spike + reading the `OPENPERF` data + the go/no-go decision (Phase 0/1), idempotent create reconciliation (W3, subtle), notification/foreground-service compliance (W7, subtle), final integration.
- **Sonnet (delegate):** well-specced units — **W2 mechanics once the model is fixed (the `content_package.source` column + migration, prune/count query filters, multi-select "Available offline" action)**, DAO query fixes + live count (W6), Clear-All path (W4), KEEP→REPLACE + stalled-guard reschedule (W5), debug-export enhancement (W8), icon tweak (W9), and the unit/Robolectric tests for each.
- **Haiku (delegate):** mechanical work — driving the device + collecting/aggregating `OPENPERF` logs via adb (Phase 0), adding strings across the 10 language files, running the gradle verification tasks, simple boilerplate/renames.
