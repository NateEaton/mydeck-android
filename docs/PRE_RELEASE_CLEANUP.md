# Pre-Release Cleanup Tracker

Items to address before formal production release. This is a living document — add items as they are identified during code review and testing.

---

## Required

These items must be addressed before production release.

### Error Handling / Resilience

- [ ] **Bookmark sync resilience** — `LoadBookmarksUseCase.execute()` deserializes an entire page of bookmarks at once. If any single bookmark in the page has unexpected data, the whole sync aborts. Consider adding per-bookmark error handling so one malformed bookmark doesn't prevent the rest from loading.

### Architecture / Code Quality

- [ ] **Navigation event pattern** — `BookmarkListViewModel`, `SyncSettingsViewModel`, and other ViewModels use `MutableStateFlow<NavigationEvent?>` with manual `onNavigationEventConsumed()` reset. This pattern is prone to lost events or double navigation under rapid taps. Migrate to `Channel` with `receiveAsFlow()` consumed via `LaunchedEffect`.
- [ ] **Compose state collection** — `BookmarkListScreen` accesses `.value` directly on `collectAsState()` (e.g., `viewModel.uiState.collectAsState().value`), which can cause unnecessary recompositions. Use `by collectAsState()` delegation or pass `State` objects to child composables.
- [ ] **WorkManager logic in Repository** — `BookmarkRepositoryImpl` directly calls `ActionSyncWorker.enqueue(workManager)`. Extract sync scheduling to a dedicated `SyncScheduler` or UseCase to maintain separation of concerns.
- [ ] **Comma-separated label storage** — `Converters.fromStringList()` uses `value?.split(",")` which could corrupt data if labels contain commas. While Readeck currently disallows commas in labels, consider using JSON serialization for robustness.

### Testing

- [ ] **BookmarkRepositoryImpl test coverage** — Tests cover ~40% of functionality. See memory/notes for detailed phase plan covering: `createBookmark()`, `refreshBookmarkFromApi()`, `updateLabels()`, `updateReadProgress()`, `renameLabel()`, `deleteLabel()`, observe/flow methods, and search.
- [ ] **Re-enable CI test execution** — Tests currently disabled in CI due to coverage gaps.

### Pending Verification

- [x] **ImageResource deserialization crash (Stefan's issue #143)** — `width`/`height` fields in `BookmarkDto.ImageResource` were required with no defaults, causing `MissingFieldException` when Readeck omits them. Fixed by adding `= 0` defaults. Awaiting Stefan's confirmation.
- [x] **Welcome screen icon** — Was showing legacy ReadeckApp vector drawable; changed to `R.mipmap.ic_launcher_foreground` (MyDeck three-card icon).

---

## Recommended

These items improve quality but are not blockers for release.

### Code Quality

- [ ] **Unused `ImageResourceDTO.kt`** — `app/src/main/java/com/mydeck/app/io/rest/model/ImageResourceDTO.kt` defines a separate `ImageResourceDTO` with nullable fields, but `BookmarkDto.kt` uses its own `ImageResource` class. Determine if `ImageResourceDTO` is referenced anywhere; if not, remove it.
- [ ] **Legacy ReadeckApp drawable vector** — `res/drawable/ic_launcher_foreground.xml` still contains the old two-shape ReadeckApp icon. Consider replacing it with a vector version of the MyDeck three-card icon or removing it if nothing else references it.
- [ ] **Rename misleading sync status field** — Sync Settings shows "My List" (non-archived) but currently threads that value through a field named `unread` in the sync status model/UI wiring. Rename to something like `myList` to avoid future confusion.
- [ ] **Update deprecated `LibrariesContainer` API** — `OpenSourceLibrariesScreen.kt` uses the deprecated `LibrariesContainer` composable from aboutlibraries. Migrate to the `rememberLibraries` + `Libs`-based variant.
- [ ] **`CustomExceptionHandler.e.printStackTrace()`** — In `MyDeckApplication.kt`, the `CustomExceptionHandler` catch block uses `e.printStackTrace()` instead of a logging framework. This is in the crash handler itself so Timber may not be available, but consider using `Log.w()` as a fallback.

### Process

- [ ] **OAuth migration branch merge** — Awaiting Stefan's testing confirmation before merging `feature/OAuth-migration` into main.
- [ ] **Full code review pass** — Systematic review of all changes since fork from ReadeckApp for naming, style, and correctness.
