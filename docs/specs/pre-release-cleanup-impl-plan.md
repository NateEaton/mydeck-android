
# Pre-Release Cleanup — Implementation Plan

Derived from [docs/PRE_RELEASE_CLEANUP.md](../PRE_RELEASE_CLEANUP.md) after the 2026-04-04 scouting pass. Covers all still-partial, still-needed, and newly-identified items. Process items (OAuth branch merge, full code review pass) are not included — they are not coding tasks.

Eight slices. Ordered to front-load low-risk quick wins, then architecture consistency, then tests, then library migration. Slices are independently shippable — no hard dependencies between them except where noted. Each slice lists a recommended model class (Haiku / Sonnet / Opus) based on scope and judgment required.

---

## Phase 1 — Quick wins (low blast radius)

### Slice 1 — `collectAsStateWithLifecycle` migration in BookmarkListScreen
**Model: Haiku**

- **Goal:** Eliminate `.value` on `collectAsState()` and add lifecycle-aware cancellation.
- **Files:** [BookmarkListScreen.kt:137-138, 717](../../app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt#L137)
- **Approach:** Replace each `val x = viewModel.flow.collectAsState().value` with `val x by viewModel.flow.collectAsStateWithLifecycle()`. Add the `androidx.lifecycle:lifecycle-runtime-compose` import if missing. Verify the dep is already in `libs.versions.toml`; if not, add it.
- **Verify:** `./gradlew :app:assembleDebugAll`; see Phase 1 functional test checklist below.
- **Why Haiku:** Three specific line replacements in one file, no surprises.

### Slice 2 — CustomExceptionHandler Timber-failure fallback
**Model: Haiku**

- **Goal:** The inner catch (runs only if Timber itself throws) writes to Logcat instead of stderr.
- **Files:** [MyDeckApplication.kt:80-95](../../app/src/main/java/com/mydeck/app/MyDeckApplication.kt#L80-L95)
- **Approach:** Replace `e.printStackTrace()` with `android.util.Log.w("CrashHandler", "Timber failed while logging crash", e)`.
- **Verify:** Compile only. Not exercisable in normal runtime — this path requires Timber itself to throw.
- **Why Haiku:** One-line replacement, well-scoped.

### Slice 3 — Remove unused `ImageResourceDTO` (and `ResourcesDTO` if also unused)
**Model: Haiku**

- **Goal:** Delete dead DTO types.
- **Files:** [ImageResourceDTO.kt](../../app/src/main/java/com/mydeck/app/io/rest/model/ImageResourceDTO.kt), [ResourcesDTO.kt](../../app/src/main/java/com/mydeck/app/io/rest/model/ResourcesDTO.kt)
- **Approach:**
  1. Grep for `ResourcesDTO` across Kotlin + build files.
  2. If `ResourcesDTO` has no references outside its own definition, delete both files.
  3. If `ResourcesDTO` is still referenced somewhere, leave it but still delete `ImageResourceDTO` if that reference can be dropped; otherwise escalate the finding.
- **Verify:** `./gradlew :app:assembleDebugAll :app:lintDebugAll`.
- **Why Haiku:** Grep-then-delete with a clear stop condition.

### Phase 1 functional test checklist

Phase 1's only user-observable change is **Slice 1** (BookmarkListScreen state collection). Slices 2 and 3 are compile-time only. After running `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll`, exercise the following on a debug build:

**Slice 1 — BookmarkListScreen paths to exercise** (one smoke pass through each):
1. **Main list render (`uiState`)** — Launch app → bookmark list loads → scroll the list → tap a card → back out. Expect no regressions in layout variant (Mosaic/Grid/Compact) switching from UI settings.
2. **Create-bookmark flow (`createBookmarkUiState`)** — Tap FAB → "Add Link" dialog → enter URL → Save. Expect the new bookmark to appear; expect the dialog state (loading spinner, error surface) to behave as before.
3. **Share intent (`shareIntent`)** — From another app (e.g. browser), invoke Share → "Save to MyDeck" → confirm. Expect the share target to populate, save, and return to the sharing app without crash.
4. **Lifecycle-aware cancellation (the actual behavioral improvement)** — On the list screen, rotate the device and return; background the app for ~30s and foreground it. Expect no stale state, no double-emission glitches, no crash. This is the path that didn't exist before the migration.
5. **Filter / sort / label mode** — Toggle a few filters and a label to ensure state is still observed correctly after the delegation change.

**Slices 2 & 3:** No functional tests. If `assembleDebugAll` passes, they're done.

**Skip / not needed:**
- Full regression sweep — Phase 1 is narrowly scoped.
- Screenshot diffs — no UI changes intended.

---

## Phase 2 — Architecture consistency

### Slice 4 — Unify navigation event pattern across all ViewModels
**Model: Sonnet**

- **Goal:** All ViewModels emit navigation via `Channel<NavigationEvent>(BUFFERED) + receiveAsFlow()`, matching `BookmarkListViewModel`.
- **Files (ViewModels):** `SyncSettingsViewModel`, `AboutViewModel`, `LogViewViewModel`, `SettingsViewModel`, `OpenSourceLibrariesViewModel`, `AccountSettingsViewModel`, `UiSettingsViewModel`.
- **Files (Screens):** Each paired Screen that currently calls `onNavigationEventConsumed()`.
- **Approach:**
  1. Study `BookmarkListViewModel`'s channel setup and the `LaunchedEffect` collection pattern in its Screen as the reference implementation.
  2. For each ViewModel: replace `MutableStateFlow<NavigationEvent?>(null)` with a private `Channel<NavigationEvent>(BUFFERED)` and expose `.receiveAsFlow()`. Replace `.value = event` with `channel.trySend(event)` (or `viewModelScope.launch { channel.send(event) }` if back-pressure matters).
  3. Delete each `onNavigationEventConsumed()` method and its call sites in the Screen.
  4. In the Screen, replace the null-check pattern with `LaunchedEffect(Unit) { viewModel.navigationEvents.collect { … } }`.
- **Verify:** `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll`. Manual smoke test of each settings screen's navigation actions.
- **Why Sonnet:** Seven ViewModels plus their Screens; mechanical but each has slightly different event shapes and collection sites. Pattern adherence, not novel design.

### Slice 5 — Consolidate all WorkManager scheduling behind `SyncScheduler`
**Model: Sonnet**

- **Goal:** No non-`SyncScheduler` path to `WorkManager` outside of `WorkManagerSyncScheduler` itself.
- **Files:** `SyncScheduler.kt`, `WorkManagerSyncScheduler.kt`, [BookmarkRepositoryImpl.kt](../../app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt), `LoadBookmarksUseCase.kt`, Hilt module (`AppModule.kt`).
- **Approach:**
  1. Add methods to `SyncScheduler` as needed: `scheduleSingleArticleDownload(bookmarkId: String)` (to replace the Repository's private `enqueueArticleDownload`) and `scheduleBatchArticleLoad(bookmarkIds: List<String>)` (to replace the `WorkManager` call in `LoadBookmarksUseCase`).
  2. Implement both in `WorkManagerSyncScheduler` — move the existing enqueue code there verbatim.
  3. In `BookmarkRepositoryImpl`: delete the private `enqueueArticleDownload`; route its callers through `syncScheduler.scheduleSingleArticleDownload(...)`. Decide whether to merge it with the existing `scheduleArticleDownload` or keep both — document the difference inline if kept separate.
  4. In `LoadBookmarksUseCase`: remove `WorkManager` constructor parameter; inject `SyncScheduler`; replace inline enqueue with the new method.
  5. Update Hilt bindings.
- **Verify:** `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll`. Grep confirms `import androidx.work.WorkManager` appears only in `WorkManagerSyncScheduler` and `MyDeckApplication` (for `WorkManager.initialize`).
- **Why Sonnet:** Cross-file refactor with an interface change and a subtle decision about whether the Repository's old private path should merge with the existing `scheduleArticleDownload`. Needs judgment on semantic equivalence.

### Slice 6 — Remove dead `BookmarkCounts.unread` field
**Model: Haiku**

- **Goal:** Delete the unused `unread` count field and its DAO query expression.
- **Context (decision made 2026-04-04):** The query `readProgress < 100 AND state = 0 AND isLocalDeleted = 0` is semantically correct for MyDeck's data model — Read/Unread toggles in the reader view literally write `readProgress = 100` or `readProgress = 0`, so `readProgress < 100` *is* the canonical definition of "unread." The name and the query are aligned. What's wrong is that no consumer exists: the simplified sync status dialog intentionally dropped per-view counts, the filter bottom sheet uses finer progress buckets (Unviewed / In progress / Completed) with no count decorations, and the list ViewModel's UiState never references `bookmarkCounts.unread`. The field is vestigial from an earlier sync status layout and should be removed rather than repaired.
- **Files:** [BookmarkDao.kt:369, :656](../../app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt#L369), [BookmarkCounts.kt](../../app/src/main/java/com/mydeck/app/domain/model/BookmarkCounts.kt), `DetailedSyncStatusCounts` (same DAO file), and any call sites the compiler flags.
- **Approach:**
  1. Remove the `unread_count` expression from both DAO `SELECT` projections (lines 369 and 656 per scout).
  2. Remove the `unread` property from `BookmarkCounts` and `DetailedSyncStatusCounts`.
  3. Follow compile errors — delete any remaining references in sync-status mappers, ViewModels, or tests.
  4. Grep for `BookmarkCounts.unread`, `DetailedSyncStatusCounts.unread`, and bare `.unread` on the two types to confirm full removal.
- **Verify:** `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll`.
- **Why Haiku:** Pure mechanical dead-code removal after the design decision was made. No judgment required beyond following the compiler.

### Phase 2 functional test checklist

Phase 2 touches user-observable behavior in navigation and background sync. After `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll`, exercise:

**Slice 4 — Navigation events across migrated ViewModels** (one smoke pass per screen):
1. **Settings root** — Open Settings drawer item → verify list renders → back → re-open. Tap each subsection entry (Account, UI, Sync, Log, About, Open Source Libraries) and verify each opens and returns cleanly.
2. **Account Settings** — Open → trigger any action that emits a nav event (e.g. sign-out, re-authenticate flow landing). Verify the screen navigates back or forward exactly once.
3. **Sync Settings** — Open → change a setting that triggers navigation (if any) → force a manual sync → verify any result-surface navigation fires once.
4. **UI Settings** — Open → toggle theme / card variant → navigate back. No stuck screens, no double-back.
5. **Log Viewer** — Open → scroll logs → back.
6. **About** — Open → tap "Open Source Libraries" → verify it pushes the libraries screen → back to About → back to Settings.
7. **Rapid-tap stress** — On any migrated screen, tap a nav-triggering button 4-5 times in quick succession. Expect exactly one navigation, not zero and not multiple stacked screens. (This is the original failure mode the migration fixes.)
8. **Rotation mid-navigation** — Trigger a nav event, rotate during the transition. Expect the correct destination, no crash.

**Slice 5 — WorkManager consolidation** (sync still works end-to-end):
1. **Create bookmark** — FAB → Add Link → save → verify the bookmark appears and its article content eventually downloads (wait 10-30s on wifi, then open the bookmark and confirm reader view renders from cache, not live-fetch). This exercises the `scheduleSingleArticleDownload` path.
2. **Delta sync with offline reading on** — Enable offline reading in Sync Settings → trigger a sync (pull-to-refresh or manual) → verify newly-synced bookmarks get their articles downloaded in the background. This exercises the `scheduleBatchArticleLoad` path that replaced the direct `WorkManager` injection in `LoadBookmarksUseCase`.
3. **Action sync** — Favorite a bookmark → watch pending-actions count (if visible in Sync Settings debug info) go up then back to zero → verify the server reflects the change. This exercises `scheduleActionSync` which was already abstracted but worth a smoke check given the surrounding refactor.
4. **Airplane mode → back online** — Toggle airplane mode on, favorite/archive a few items (queueing pending actions), toggle airplane mode off, confirm the queue drains and all three action types sync.

**Slice 6 — Dead `unread` field removal:**
- **Sync Settings dialog** — Open Sync Settings, open the sync status dialog. Verify All / MyList / Archive counts render correctly and no placeholder/empty row appeared in their place. No visible change is the expected outcome.
- **Drawer and filter surfaces** — Open the navigation drawer and the filter bottom sheet. Verify no count display is broken and no stray reference to "unread" appears. Again, zero visible change expected — this is a dead-code removal.
- **Compile guard** — `./gradlew :app:assembleDebugAll` must be clean; any reference the compiler flags has to be dealt with during the slice itself, not punted to test time.

---

## Phase 3 — Tests

### Slice 7 — BookmarkRepositoryImpl test coverage expansion
**Model: Sonnet**

- **Goal:** Cover the still-uncovered public methods and `syncPendingActions` paths identified in the scout.
- **Files:** `BookmarkRepositoryImplTest.kt` (and any existing fake/mock fixture files).
- **Scope (target methods):**
  - `renameLabel()` — success + API error
  - `deleteLabel()` — success + API error
  - `refreshBookmarkMetadata()` — success + not-found
  - `updateTitle()` — success + pending-action queued
  - `getBookmarkById()` — found + missing
  - `deleteAllBookmarks()`
  - `replaceServerErrorFlags()`
  - `fetchRawBookmarkJson()` — success + error
  - `observeBookmark()`, `observeBookmarks()`, `observeBookmarkListItems()`, `searchBookmarkListItems()`, `observeFilteredBookmarkListItems()` — verify Flow emission on underlying data change (use `turbine` if already a dep)
  - `observeAllBookmarkCounts()`, `observeAllLabelsWithCounts()`, `observePendingActionCount()` — smoke tests
  - `syncPendingActions()` — success path, 400/422 handling, delete-action success
- **Approach:** Reuse the existing 26-test file's fixture builders and mock patterns; add tests in the same style. If a method needs a helper that doesn't exist, add it alongside.
- **Verify:** `./gradlew :app:testDebugUnitTestAll` passes.
- **Why Sonnet:** Test writing at scale matches an existing style and reuses existing infrastructure. Opus is overkill; Haiku would miss subtleties like Flow testing setup.
- **Sequencing:** Large slice — expect multiple sessions. Batch by method group (`label ops` → `observers` → `syncPendingActions paths`) and commit per group.

### Phase 3 functional test checklist

Phase 3 is test code only — no runtime behavior change. "Functional testing" here reduces to:

1. **Suite passes** — `./gradlew :app:testDebugUnitTestAll` green.
2. **Mutation sanity check (optional but recommended per group)** — After adding tests for a method group, temporarily break one line in the corresponding production method (e.g. flip a boolean, comment out a `pendingActionDao.insert(...)` call) and rerun the targeted test class. At least one new test should fail. Revert. This confirms the new tests actually exercise the paths they claim to.
3. **No flaky Flow tests** — Rerun `:app:testDebugUnitTestAll` twice in a row; all new `observe*` / `search*` tests should be deterministic across runs.

No device-side smoke testing needed for this phase.

---

## Phase 4 — Library migration

### Slice 8 — Migrate `LibrariesContainer` to `rememberLibraries` + `Libs`
**Model: Sonnet**

- **Goal:** Use the non-deprecated aboutlibraries API.
- **Files:** [OpenSourceLibrariesScreen.kt:57](../../app/src/main/java/com/mydeck/app/ui/settings/OpenSourceLibrariesScreen.kt#L57)
- **Approach:**
  1. Confirm current aboutlibraries version in `libs.versions.toml` supports `rememberLibraries`.
  2. Replace the direct `LibrariesContainer(modifier = ...)` call with `val libs by rememberLibraries(R.raw.aboutlibraries)` (or the JSON-loaded variant) and pass `libraries = libs` to the appropriate non-deprecated entry point for the version in use.
  3. Visual parity check (dividers, card backgrounds, typography match app theme).
- **Verify:** `./gradlew :app:assembleDebugAll :app:lintDebugAll`; manual visual check of the Open Source Libraries screen.
- **Why Sonnet:** Needs to look up or recall the current aboutlibraries API and pick the correct replacement — not mechanical enough for Haiku, not complex enough for Opus.

### Phase 4 functional test checklist

Phase 4 is a single-screen visual change. After `./gradlew :app:assembleDebugAll :app:lintDebugAll`:

1. **Navigate to Open Source Libraries** — Settings → About → Open Source Libraries. Verify the screen loads without crash.
2. **Library list renders** — Scroll through the full list. Confirm every library entry has its name, version, and license visible. No missing entries vs the previous build.
3. **Visual parity** — Dividers, card backgrounds, and typography match the rest of the app's Settings screens. No stray padding, clipping, or theme mismatch.
4. **Theme variants** — Switch to dark theme and sepia theme (UI Settings) and re-open the screen. Verify readable contrast and no white-flash on navigation into the screen.
5. **Library detail interaction** — If the new API exposes per-library dialogs / expandable cards / external links, tap one and verify it behaves as expected and returns cleanly.
6. **Back navigation** — Back out to About → back to Settings. No stuck screens.

---

## Summary table

| # | Slice | Model | Phase |
|---|---|---|---|
| 1 | `collectAsStateWithLifecycle` migration | **Haiku** | 1 |
| 2 | CustomExceptionHandler fallback | **Haiku** | 1 |
| 3 | Remove unused `ImageResourceDTO` | **Haiku** | 1 |
| 4 | Nav event pattern migration (7 VMs) | **Sonnet** | 2 |
| 5 | Unify WorkManager scheduling behind `SyncScheduler` | **Sonnet** | 2 |
| 6 | Remove dead `BookmarkCounts.unread` field | **Haiku** | 2 |
| 7 | BookmarkRepositoryImpl test expansion | **Sonnet** | 3 |
| 8 | `LibrariesContainer` → `rememberLibraries` | **Sonnet** | 4 |

**Model-class rationale:** Haiku handles single-file, well-scoped, mechanical changes where the correct edit is unambiguous (Slices 1-3, 6). Sonnet handles multi-file refactors, pattern-matching migrations, test writing, and changes with a small judgment call (Slices 4, 5, 7, 8). No slice here needs Opus — none involve cross-cutting architectural decisions with subtle tradeoffs; Phase 2 comes closest but the direction is already decided.

---

## Out of scope

Items intentionally not planned here:
- **CI feature-branch trigger behavior** — The `verify` job's current gating (PR / main / `workflow_dispatch` only, not feature branch pushes) is by design. Local workflow already runs full test + snapshot gradle tasks before material commits; duplicating that on every feature branch push creates noise when iterating on long-lived refactor branches.
- **Full code review pass** — Manual process, not a coding slice.
