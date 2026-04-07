# Pre-Merge Cleanup — feature/sync-multipart-v012

Derived from the code review pass on 2026-04-07. Five concrete changes before merging to main.
Items already covered by the post-merge pre-release plan are excluded.

---

## Fix 1 — `hasOfflinePackage` uses wrong proxy in `getOfflinePolicyBookmarks`

**File:** `BookmarkDao.kt`

**Problem:** The query uses `CASE WHEN b.contentState = 1 THEN 1 ELSE 0 END` to determine whether
a bookmark has a managed offline package. `contentState = 1` (DOWNLOADED) is also true for
text-cached content (no images, no `content_package` row with `hasResources = 1`). This causes:
- The prune loop to count and potentially delete text-cached bookmarks as if they are managed
  offline packages.
- `downloadedCount` in `BatchArticleLoadWorker` to overstate the managed article count, making the
  `STORAGE_LIMIT` hysteresis arithmetic incorrect.

**Fix:** Join on `content_package` and use `cp.bookmarkId IS NOT NULL AND cp.hasResources = 1`
as the `hasOfflinePackage` signal.

---

## Fix 2 — `parseInstantLenient` duplicated across two use-cases

**Files:** `LoadContentPackageUseCase.kt`, `FreshnessMarkerUseCase.kt`

**Problem:** Identical private helper in two classes. Any future change requires two updates.

**Fix:** Extract to a top-level function in `com.mydeck.app.util` (added to `Utils.kt`) and
replace both private copies with the shared one.

---

## Fix 3 — Constraint override dialog body shows blank constraint name

**Files:** `BookmarkListScreen.kt`, `strings.xml`

**Problem:** `stringResource(R.string.sync_constraint_override_body, "")` passes an empty string
for the `%s` placeholder, producing `"…blocked by active constraints ()…"`. The ViewModel has
the blocked reason string available via `canFetchContent().blockedReason`.

**Fix:** Replace the single `%s` format string with a plain string that does not name the
constraint (simpler and accurate), and remove the format arg from the `stringResource` call.
The reason is already communicated via the snackbar that fires before the dialog.

---

## Fix 4 — `ReadPositionLogPrefix` debug logs should be debug-build only

**File:** `BookmarkDetailWebViews.kt`

**Problem:** Three `Timber.d` calls tagged `READPOS` are diagnostic aids that should not appear
in release builds. Timber's debug tree is not planted in release, so `Timber.d` is effectively
silent already — but the calls are pointless in release and clutter the log in debug.

**Fix:** Wrap each call with `if (BuildConfig.DEBUG)` so intent is explicit.

---

## Fix 5 — `DateRangePreset.ALL_TIME` magic number needs a comment

**File:** `DateRangePreset.kt`

**Problem:** `365 * 20` is unexplained. `ALL_TIME` is not currently exposed in any UI dropdown
but is used as a sentinel for "fetch everything" in the date-range content sync worker.

**Fix:** Add an inline comment explaining the value and that this preset is a programmatic
sentinel, not a user-facing option.

---

## Fix 6 — `SyncRequest` comment contradicts actual serialization config

**File:** `SyncRequest.kt`

**Problem:** The comment says "Defaults must be false so Kotlinx serialization (encodeDefaults=false)
actually emits the field when callers pass true." The shared `Json` instance in `AppModule.kt`
has `encodeDefaults = true`, so `false` fields are also always emitted. The comment is misleading.
The behavior is benign (server ignores false flags) but the comment creates confusion.

**Fix:** Replace the comment with an accurate description of what the defaults achieve.

---

## Summary table

| # | File | Type |
|---|---|---|
| 1 | `BookmarkDao.kt` | Bug — prune logic correctness |
| 2 | `LoadContentPackageUseCase.kt`, `FreshnessMarkerUseCase.kt`, `Utils.kt` | Code quality — duplication |
| 3 | `BookmarkListScreen.kt`, `strings.xml` | Bug — blank format arg in dialog |
| 4 | `BookmarkDetailWebViews.kt` | Polish — unnecessary debug logs |
| 5 | `DateRangePreset.kt` | Polish — unexplained magic number |
| 6 | `SyncRequest.kt` | Polish — misleading comment |
