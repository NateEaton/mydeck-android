# Implementation Plan: Unified Offline Content Architecture

## Overview

This plan implements the architecture defined in `unified-offline-content-spec.md`.
It replaces the two-tier (text-only / full) managed offline design from
`feature/sync-multipart-v012` with a single-tier model where a bookmark either has a
full offline package (text + images) or does not.

Each slice is scoped to be independently mergeable. Later slices depend on earlier ones
where noted.

**Model guidance key:**
- **Haiku** — Mechanical, well-bounded changes; pattern-following edits; string/resource
  additions; simple UI removals.
- **Sonnet** — Moderate complexity; targeted refactors touching multiple files; Compose
  UI work; state transition logic; worker/ViewModel coordination with clear direction.
- **Opus** — High complexity; new subsystems; multiple interacting policies with edge
  cases; architecture-level decisions embedded in implementation; annotation-aware
  package replacement.

---

## Slice 1: Formalize Content State Semantics

**Goal:** Document and enforce the updated meaning of `DOWNLOADED + hasResources=false`
(on-demand text cache) vs. `DOWNLOADED + hasResources=true` (full offline package).
No schema change required.

**Scope:**
- Add KDoc to `BookmarkEntity.ContentState`, `ContentPackageEntity`, and
  `BookmarkListItemEntity` clarifying the two `DOWNLOADED` sub-states and their
  acquisition paths.
- Add a top-level comment block in `ContentPackageManager` clarifying that
  `hasResources=false` packages are on-demand text caches, not managed offline content.
- Audit any code that reads `contentState == DOWNLOADED` and does not also check
  `hasResources` — flag any places where the distinction matters for correctness.
  (This is a read-only audit; fixes happen in later slices.)

**Files touched:** `BookmarkEntity.kt`, `ContentPackageEntity.kt`,
`BookmarkListItemEntity.kt`, `ContentPackageManager.kt`

**Does not change:** Schema, DB migrations, any behavior.

**Recommended model: Haiku.** Pure documentation and annotation work on already-read
files. Pattern is clear.

---

## Slice 2: Update Bookmark Card Icon Logic

**Goal:** Shift icon semantics so that the outline icon means "on-demand text cache"
and the filled icon means "full offline package." The actual icon assets and conditions
are largely the same; only the label/description and any `hasResources`-based branching
in list item rendering need updating.

**Scope:**
- Update `BookmarkCard` (and any mosaic/grid/compact layout variants) icon derivation
  logic: `hasResources=false → outline`, `hasResources=true → filled`, `null → no icon`.
- Update any content descriptions or accessibility strings for these icons to reflect
  the new semantics ("Text available" vs. "Available offline").
- Add the new string resources to all language files (English text as placeholder per
  CLAUDE.md localization requirements).
- Remove any code that previously distinguished "text-only managed offline" from
  "on-demand cache" in the icon layer (if such branching exists beyond `hasResources`).

**Files touched:** Bookmark card Composable(s), string resources in all language files.

**Depends on:** Slice 1 (semantic clarity).

**Recommended model: Haiku.** Bounded UI logic change with mechanical string resource
additions.

---

## Slice 3: Remove Per-Article Image Toggle

**Goal:** Remove the cycling image toggle from `BookmarkDetailDialog` and all backing
logic. This is a removal slice; no new behavior is added.

**Scope:**
- Remove the image toggle row from `BookmarkDetailDialog` metadata section.
- Remove the backing ViewModel action and state that drove the toggle.
- Remove the `downloadImagesWithArticles` preference key from `SettingsDataStore`.
- Remove any settings migration or default value for `downloadImagesWithArticles`.
- Do NOT remove `ContentPackageManager.deleteResources()` yet — that is Slice 4.
  Just remove the call sites that were driven by the per-article toggle.
- Verify that "Remove downloaded content" in the overflow menu still works correctly
  (it calls `deleteContentForBookmark`, not `deleteResources` — confirm and retain).

**Files touched:** `BookmarkDetailDialog.kt` (or equivalent Composable),
`BookmarkDetailViewModel.kt`, `SettingsDataStore.kt`, possibly `SyncSettingsViewModel.kt`.

**Depends on:** Slice 1.

**Recommended model: Haiku.** Pure removal. The toggle is self-contained and its call
sites are limited.

---

## Slice 4: Remove Two-Tier Managed Offline Infrastructure

**Goal:** Remove `ContentPackageManager.deleteResources()`, the HTML URL-rewriting
downgrade path, the hysteresis watermark pruning, and the text-vs-image batch sizing
from `BatchArticleLoadWorker`. These are all load-bearing components of the two-tier
design that has no role in the unified architecture.

**Scope:**
- Remove `ContentPackageManager.deleteResources()` and the safety check that aborts
  when relative image URLs are detected.
- Remove `LoadContentPackageUseCase` logic that routes to `fetchTextOnly` based on
  `downloadImagesWithArticles`. Workers now always call the full-package fetch path.
  Note: retain `MultipartSyncClient.fetchTextOnly()` itself — it is still used by
  the annotation HTML refresh path (`refreshHtmlForAnnotations`).
- Remove hysteresis high/low watermark fields and the image-only pruning loop from
  `BatchArticleLoadWorker`.
- Remove the `restoreDownloadedState` call and the DB query that tracked image bytes
  separately from text bytes.
- Remove adaptive batch sizing logic that scaled batch size based on whether images
  were included.
- Update `BatchArticleLoadWorker` to always request full packages.
- Remove or simplify `ContentSyncPolicyEvaluator` if its remaining logic only checks
  whether automatic content sync is enabled (no longer needs to gate on image mode).

**Files touched:** `ContentPackageManager.kt`, `LoadContentPackageUseCase.kt`,
`BatchArticleLoadWorker.kt`, `ContentSyncPolicyEvaluator.kt`.

**Depends on:** Slice 3 (callers of `deleteResources` removed first).

**Recommended model: Sonnet.** Multiple files, non-trivial interdependencies between
worker and use case. Requires understanding what to keep vs. remove without breaking
the annotation refresh path that shares `fetchTextOnly` at the transport layer.

---

## Slice 5: Simplify Annotation Refresh URL Rewriting

**Goal:** Remove the `hasResources`-based branch in `refreshHtmlForAnnotations` that
decided whether to rewrite image URLs. Full offline packages always have resources;
the URL rewriting path is always "use relative URLs."

**Scope:**
- In `LoadContentPackageUseCase.refreshHtmlForAnnotations`, remove the conditional
  that checked `hasResources` before applying relative URL rewriting.
- Full offline packages always use the relative URL path. On-demand text-cached
  bookmarks (`hasResources=false`) still use the legacy article endpoint refresh path
  via `LoadArticleUseCase` — confirm this branching is still correct after Slice 4.
- Update tests for `refreshHtmlForAnnotations` accordingly.

**Files touched:** `LoadContentPackageUseCase.kt`, related unit tests.

**Depends on:** Slice 4.

**Recommended model: Sonnet.** Targeted annotation path change requiring understanding
of the split between content-package and legacy article refresh flows.

---

## Slice 6: On-Demand Open Queues Full Package When Offline Reading Is Enabled

**Goal:** When offline reading is on and the user opens a bookmark that has no full
offline package, the reader fetches text immediately for fast open (existing behavior)
and queues a priority full-package download (text + images).

**Scope:**
- In `BookmarkDetailViewModel`, after a successful on-demand text fetch, check
  `offlineReadingEnabled`. If true and the bookmark is within the current policy's
  eligible scope, enqueue a high-priority `BatchArticleLoadWorker` job for that
  specific bookmark (single-item batch, REPLACE policy to avoid duplicates).
- The priority enqueue bypasses the normal batch ordering (newest-first) because the
  user has expressed explicit interest in this bookmark. WiFi/battery constraints
  still apply (the WorkManager request carries them).
- The bookmark transitions: `NOT_ATTEMPTED → DOWNLOADED(hasResources=false) →
  DOWNLOADED(hasResources=true)` as text is cached then the full package arrives.
- The bookmark card icon updates accordingly (outline → filled) when the package
  commits.

**Files touched:** `BookmarkDetailViewModel.kt`, `BatchArticleLoadWorker.kt` (if a
single-bookmark enqueue path is not already present).

**Depends on:** Slice 4.

**Recommended model: Sonnet.** State transition logic requiring coordination between
ViewModel, WorkManager enqueue, and existing policy evaluator.

---

## Slice 7: Offline Management Policy Engine

**Goal:** Replace the single image-only storage cap with a three-policy selector
(Storage Limit, Newest N, Date Range rolling window), a secondary storage cap for
Policies B and C, and the eligibility/pruning logic that each policy requires.

**Scope:**

### Data layer
- Add to `SettingsDataStore`:
  - `offlineReadingEnabled: Boolean`
  - `offlinePolicy: Enum { STORAGE_LIMIT, NEWEST_N, DATE_RANGE }`
  - `offlinePolicyStorageLimit: Long`
  - `offlinePolicyNewestN: Int`
  - `offlinePolicyDateRangeWindow: Duration`
  - `offlineMaxStorageCap: Long`
- Rename `includeArchivedContentInSync` → `includeArchivedInOfflineScope`.
- Keep `clearContentOnArchive`, `wifiOnly`, `allowOnBatterySaver`.
- Remove `downloadImagesWithArticles` (already removed in Slice 3),
  `contentSyncMode` enum.

### Policy engine
- Create `OfflinePolicyEvaluator` (replaces `ContentSyncPolicyEvaluator`):
  - `isEligible(bookmark): Boolean` — applies the active policy's eligibility
    predicate plus scope (include archived toggle).
  - `shouldPrune(): Boolean` — checks if current usage exceeds the active policy
    threshold.
  - `selectForPruning(n: Int): List<String>` — returns oldest-first bookmark IDs
    to evict until under threshold.
- Policy A (Storage Limit): eligibility = always true within scope; prune when
  total package bytes > limit; evict oldest-first by `created`.
- Policy B (Newest N): eligibility = rank by `created` desc, top N within scope;
  prune when count > N; evict oldest-first.
- Policy C (Date Range): eligibility = `created >= now - window`; prune when any
  package's bookmark has aged out of the window; evict aged-out packages.
- Secondary cap check runs before policy-based pruning for Policies B and C.

### Worker
- Update `BatchArticleLoadWorker` to use `OfflinePolicyEvaluator` for eligibility
  checks and pruning decisions.
- Pruning loop: query eligible packages sorted by `created` asc, call
  `ContentPackageManager.deleteContentForBookmark()` on oldest until under threshold.

**Files touched:** `SettingsDataStore.kt`, new `OfflinePolicyEvaluator.kt`,
`BatchArticleLoadWorker.kt`, DB migration if preference schema is stored in Room
(otherwise DataStore migration), `ContentPackageManager.kt` (prune call sites).

**Depends on:** Slices 4 and 5.

**Recommended model: Opus.** New subsystem with multiple interacting policies and
edge cases: concurrent pruning and download, policy transitions (user changes policy
mid-session), eligibility changes when scope setting changes, interaction between
secondary cap and primary policy. Requires careful design of the evaluator interface
to keep each policy implementation testable in isolation.

---

## Slice 8: Settings Screen Redesign

**Goal:** Replace the existing content sync section with the offline reading section
as specified in `unified-offline-content-spec.md`. This is primarily a Compose UI
slice; the data layer is in place after Slice 7.

**Scope:**
- Remove: content sync mode radio group, date-range scheduling section, download images
  toggle, accordions/collapsible sections.
- Add: offline reading master toggle; policy selector (radio group); storage limit
  picker; newest-N picker; date-range window picker; secondary storage cap picker
  (conditional on policy); include archived toggle; clear-on-archive toggle;
  Wi-Fi only and battery-saver toggles (retained); one-time manual date-range
  download button (see Slice 9).
- Settings screen collapses substantially when offline reading is off.
- Sync status section updates per spec (different fields shown when offline on vs. off).
- String resources for all new UI labels added to all language files.
- Update user guide (`settings.md`) to reflect the new layout.

**Files touched:** Sync settings Composable(s), `SyncSettingsViewModel.kt`,
string resource files (all languages), `app/src/main/assets/guide/en/settings.md`.

**Depends on:** Slice 7.

**Recommended model: Sonnet.** Large Compose UI change but well-specified. The main
risk is getting conditional visibility right for the collapsed state; the ViewModel
logic is straightforward read/write of preference keys.

---

## Slice 9: One-Time Manual Date-Range Download

**Goal:** Add a "Download content for date range" action that triggers a one-time
batch download for bookmarks added within a user-specified from/to date range.
This is distinct from the ongoing rolling-window Policy C.

**Scope:**
- Add a date range picker dialog triggered from the settings screen button.
- On confirm, enqueue a `BatchArticleLoadWorker` with date-range input parameters
  (from/to Instant), bypassing the active management policy's eligibility predicate
  but still respecting WiFi/battery constraints. Show a constraint-override dialog
  if constraints would block the download (matching existing date-range sync behavior).
- Content downloaded by a one-time sync is subject to normal eviction by the active
  management policy on subsequent maintenance runs.
- This replaces the old date-range scheduling path from the prior branch.

**Files touched:** Settings Composable (date picker dialog), `BatchArticleLoadWorker.kt`
(accept optional explicit date range override), `SyncSettingsViewModel.kt`.

**Depends on:** Slice 8.

**Recommended model: Sonnet.** Moderate complexity; the worker change is a parameter
addition, the UI is a standard date picker + constraint dialog pattern already present
in the codebase.

---

## Slice 10: Offline Reading Enable/Disable Lifecycle

**Goal:** Implement the full lifecycle: enabling offline reading starts the background
worker, disabling offline reading immediately purges all managed offline content
(`hasResources=true` packages) and cancels any pending work.

**Scope:**
- When `offlineReadingEnabled` transitions to `true`: enqueue `BatchArticleLoadWorker`
  with current policy constraints.
- When `offlineReadingEnabled` transitions to `false`:
  - Cancel any pending `BatchArticleLoadWorker` work.
  - Query all `DOWNLOADED` bookmarks with `hasResources=true`.
  - Call `ContentPackageManager.deleteContentForBookmark()` for each.
  - On-demand text caches (`hasResources=false`) are NOT purged.
- Purge runs on a `CoroutineScope` tied to the settings ViewModel, not a worker
  (it is synchronous from the user's perspective — settings screen should show progress
  if the library is large).
- After purge, update sync status display.

**Files touched:** `SyncSettingsViewModel.kt`, `ContentPackageManager.kt`,
`BookmarkDao.kt` (query for hasResources=true packages).

**Depends on:** Slices 7 and 8.

**Recommended model: Sonnet.** Lifecycle transitions with clear rules; the main concern
is that the purge does not race with an in-progress download worker.

---

## Slice 11: Bookmark Detail Dialog Offline Status

**Goal:** Update the Bookmark Detail dialog to show explicit offline status text per
spec, and confirm the "Remove downloaded content" overflow menu item works correctly
for both `hasResources=true` and `hasResources=false` packages.

**Scope:**
- Replace icon-only offline indicator in the detail dialog with explicit status text:
  "Offline content: Not kept / Text cached / Available / Refresh pending."
- Confirm `deleteContentForBookmark()` is called (not `deleteResources()`) by the
  overflow menu item — both `hasResources` states should be fully purged by this action.
- The overflow menu item should be visible whenever `contentState == DOWNLOADED`,
  regardless of `hasResources` value.
- Add/update string resources for all status labels in all language files.
- Update user guide (`your-bookmarks.md`) if the detail dialog section describes
  offline state.

**Files touched:** Bookmark detail dialog Composable(s), `BookmarkDetailViewModel.kt`,
string resources (all languages), `app/src/main/assets/guide/en/your-bookmarks.md`.

**Depends on:** Slice 3 (image toggle removed), Slice 10 (lifecycle stable).

**Recommended model: Haiku.** Mostly a UI text change and menu item audit with string
resource additions.

---

## Slice 12: Migration from Prior Settings

**Goal:** Ensure users upgrading from `feature/sync-multipart-v012` or earlier get a
sensible default state.

**Scope:**
- If prior `contentSyncMode == AUTOMATIC`: migrate to `offlineReadingEnabled = true`,
  `offlinePolicy = STORAGE_LIMIT`, `offlinePolicyStorageLimit = 500 MB` (sensible default).
- If prior `contentSyncMode != AUTOMATIC`: migrate to `offlineReadingEnabled = false`.
- `includeArchivedContentInSync` value migrates to `includeArchivedInOfflineScope`.
- Existing `DOWNLOADED + hasResources=true` packages require no migration; they are
  valid full offline packages.
- Existing `DOWNLOADED + hasResources=false` packages become on-demand text caches;
  no migration required.
- Log migration in a one-time DataStore preferences migration block.

**Files touched:** `SettingsDataStore.kt` (migration logic), possibly a `MigrationHelper`
class if the codebase has a pattern for this.

**Depends on:** Slice 7 (new preference keys exist).

**Recommended model: Haiku.** Mechanical mapping of old preference values to new ones.
Edge cases are limited; the migration table is fully specified above.

---

## Slice 13: Unit Test Coverage

**Goal:** Add unit tests for the new `OfflinePolicyEvaluator` and the updated
`BatchArticleLoadWorker` pruning and eligibility logic. Update tests invalidated by
Slices 4 and 5.

**Scope:**
- `OfflinePolicyEvaluator` unit tests: each policy's `isEligible`, `shouldPrune`,
  and `selectForPruning` with boundary conditions (count at N, count at N+1; storage
  at limit, storage over limit; bookmark just within window, bookmark just outside
  window).
- Secondary cap interaction tests for Policies B and C.
- `BatchArticleLoadWorker` integration tests: policy check → download → prune cycle.
- Remove or update tests that exercised `deleteResources`, the hysteresis pruning loop,
  or the `downloadImagesWithArticles` conditional.
- Annotation refresh tests: confirm `refreshHtmlForAnnotations` no longer branches on
  `hasResources`.

**Files touched:** Test files under `app/src/test/` and `app/src/androidTest/`.

**Depends on:** Slices 4, 5, 7.

**Recommended model: Opus.** Policy boundary condition tests require careful thought
about exactly what each policy guarantees. Getting the pruning selection logic right
under concurrent modifications (new bookmarks arriving while pruning) needs thorough
test design. Annotation refresh test update requires understanding the full refresh path.

---

## Dependency Graph

```
Slice 1 (semantics doc)
  └─ Slice 2 (card icons)
  └─ Slice 3 (remove image toggle)
       └─ Slice 4 (remove two-tier infra)
            └─ Slice 5 (annotation refresh simplification)
            └─ Slice 6 (on-demand → queue full package)
            └─ Slice 7 (policy engine)
                 └─ Slice 8 (settings UI)
                      └─ Slice 9 (one-time date-range action)
                      └─ Slice 10 (enable/disable lifecycle)
                           └─ Slice 11 (detail dialog status)
                 └─ Slice 12 (settings migration)
  Slice 13 (tests) — depends on 4, 5, 7; can run after each
```

Slices 2 and 3 are independent of each other and can be done in parallel.
Slices 5 and 6 are independent of each other after Slice 4.
Slices 9, 10, and 12 are independent of each other after Slice 7/8.
Slice 13 work can begin after Slice 4 and continue incrementally through Slice 7.

---

## Build Verification (per CLAUDE.md)

After each slice:
```
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

All three must pass before committing a slice.
