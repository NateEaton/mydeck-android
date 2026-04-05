# Pre-Release Cleanup Tracker

Items to address before formal production release. This is a living document — add items as they are identified during code review and testing.

Last scouted: 2026-04-04 on `feature/sync-multipart-v012`.

---

## Required

These items must be addressed before production release.

### Error Handling / Resilience

- [x] **Bookmark sync resilience** — `LoadBookmarksUseCase.execute()` now wraps each per-bookmark `dto.toDomain()` call in a `try/catch` inside `mapNotNull` ([LoadBookmarksUseCase.kt:71-83](app/src/main/java/com/mydeck/app/domain/usecase/LoadBookmarksUseCase.kt#L71-L83)). Malformed bookmarks are logged via `Timber.w` and skipped; the rest of the page continues.

### Architecture / Code Quality

- [ ] **Navigation event pattern — migrate remaining ViewModels** — `BookmarkListViewModel` has been migrated to `Channel<NavigationEvent>(BUFFERED)` + `receiveAsFlow()`, but seven other ViewModels still use `MutableStateFlow<NavigationEvent?>` with manual `onNavigationEventConsumed()` reset: `SyncSettingsViewModel`, `AboutViewModel`, `LogViewViewModel`, `SettingsViewModel`, `OpenSourceLibrariesViewModel`, `AccountSettingsViewModel`, `UiSettingsViewModel`. Two coexisting patterns is worse than one — complete the migration for consistency and to eliminate the lost-event / double-navigation risk on rapid taps.
- [ ] **Compose state collection** — `BookmarkListScreen` still accesses `.value` directly on `collectAsState()` at three call sites ([BookmarkListScreen.kt:137](app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt#L137), [:138](app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt#L138), [:717](app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt#L717)). None use `collectAsStateWithLifecycle`, so there is no lifecycle-aware cancellation either. Migrate to `by collectAsStateWithLifecycle()` delegation.
- [ ] **Remove remaining direct-WorkManager paths** — `BookmarkRepositoryImpl` is mostly abstracted behind `SyncScheduler`, but a private `enqueueArticleDownload(bookmarkId)` method still schedules a `LoadArticleWorker` inline, distinct from the scheduler path. Route it through `SyncScheduler` (or a dedicated method on it) so the abstraction is airtight.
- [ ] **`LoadBookmarksUseCase` bypasses `SyncScheduler`** — `LoadBookmarksUseCase` takes `WorkManager` as a direct constructor parameter and enqueues `BatchArticleLoadWorker` itself, contradicting the repository-layer abstraction. Inject `SyncScheduler` and add a `scheduleBatchArticleLoad(...)` method (or equivalent) to keep the scheduling boundary consistent across layers.
- [x] **Comma-separated label storage** — `Converters.stringListToString()` now writes JSON via `json.encodeToString(list)` and `fromStringList()` decodes JSON as the primary path ([Converters.kt:24-42](app/src/main/java/com/mydeck/app/io/db/Converters.kt#L24-L42)). The `split(",")` path is retained only as a migration fallback for legacy v6 rows; new data is commas-safe.

### Testing

- [ ] **BookmarkRepositoryImpl — remaining test coverage gaps** — Test suite has grown to 26 methods and now covers `createBookmark`, `updateLabels`, `updateReadProgress`, delta/full sync, embed preservation, omit-description handling, and `syncPendingActions` 404/transient paths. Still uncovered: `renameLabel`, `deleteLabel`, `refreshBookmarkMetadata`, all `observe*` / `search*` flow methods, `updateTitle`, `getBookmarkById`, `deleteAllBookmarks`, `replaceServerErrorFlags`, `fetchRawBookmarkJson`, and `syncPendingActions` success / 400 / 422 / delete-action paths.
- [x] **CI test execution** — All three gradle tasks (`assembleDebugAll`, `lintDebugAll`, `testDebugUnitTestAll`) are active in `.github/workflows/checks.yml`. Gating on PR / `main` pushes / `workflow_dispatch` only (excluding feature branch pushes) is intentional: local workflow runs full test + snapshot tasks before material commits, so duplicating on every feature branch push creates noise on long-lived refactor branches.

### Pending Verification

- [x] **ImageResource deserialization crash (Stefan's issue #143)** — `width`/`height` fields in `BookmarkDto.ImageResource` were required with no defaults, causing `MissingFieldException` when Readeck omits them. Fixed by adding `= 0` defaults. Awaiting Stefan's confirmation.
- [x] **Welcome screen icon** — Was showing legacy ReadeckApp vector drawable; changed to `R.mipmap.ic_launcher_foreground` (MyDeck three-card icon).

---

## Recommended

These items improve quality but are not blockers for release.

### Code Quality

- [ ] **Unused `ImageResourceDTO.kt`** — `app/src/main/java/com/mydeck/app/io/rest/model/ImageResourceDTO.kt` is referenced only by `ResourcesDTO` (as `image: ImageResourceDTO? = null`). The active code path uses a separate `ImageResource` class defined in `BookmarkDto`. Verify whether `ResourcesDTO` itself is still used anywhere; if not, delete both.
- [ ] **Dead `BookmarkCounts.unread` field** — The DAO computes `unread_count` via `WHERE readProgress < 100 AND state = 0 AND isLocalDeleted = 0` ([BookmarkDao.kt:369](app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt#L369), [:656](app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt#L656)) and exposes it on `BookmarkCounts` / `DetailedSyncStatusCounts`, but no references exist on `bookmarkCounts.unread` in `BookmarkListScreen` or the list ViewModel's UiState. Either surface it in the UI or drop the field and the column from the DAO queries.
- [ ] **Update deprecated `LibrariesContainer` API** — [OpenSourceLibrariesScreen.kt:57](app/src/main/java/com/mydeck/app/ui/settings/OpenSourceLibrariesScreen.kt#L57) still calls `LibrariesContainer(modifier = ...)` directly. Migrate to the `rememberLibraries()` + `Libs` composable pattern.
- [ ] **`CustomExceptionHandler` Timber-failure fallback** — Primary crash logging already goes through `Timber.e(throwable, "CRASH: ...")` ([MyDeckApplication.kt:80-95](app/src/main/java/com/mydeck/app/MyDeckApplication.kt#L80-L95)); the `e.printStackTrace()` call lives only in the inner catch that fires if Timber itself throws. Replace that fallback with `android.util.Log.w(...)` so even the logging-failure path uses Logcat rather than stderr.

### Process

- [ ] **OAuth migration branch merge** — Awaiting Stefan's testing confirmation before merging `feature/OAuth-migration` into main.
- [ ] **Full code review pass** — Systematic review of all changes since fork from ReadeckApp for naming, style, and correctness.

---

## Superseded

Items that no longer apply as originally written. Kept here briefly for history.

- **~~Legacy ReadeckApp drawable vector~~** — Original item described `res/drawable/ic_launcher_foreground.xml` as an old two-shape vector. That XML file no longer exists; the launcher foreground is now a webp bitmap set under `mipmap-*dpi/ic_launcher_foreground.webp`, referenced from `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`. Whether the webp depicts the MyDeck three-card icon or a residual asset requires a visual check, but the XML-vector concern is obsolete.
- **~~Rename misleading `unread` field~~** — Original item claimed "My List" (non-archived) was being threaded through a field named `unread`. Scouting shows `BookmarkCounts.unread` and `DetailedSyncStatusCounts.unread` are defined by `WHERE readProgress < 100 AND state = 0 AND isLocalDeleted = 0` — a genuine read-progress count, not a "My List" count. The name matches the query. The real issue (field is computed but never consumed by the UI) is now tracked under **Dead `BookmarkCounts.unread` field** above.
