# Spec: Image Storage Limit Refinements

## Status

In progress.

## Background

The `feature/sync-multipart-v012` branch introduced an image storage cap enforced by
`BatchArticleLoadWorker.enforceImageStorageLimit()`. During review and testing, five
issues were identified:

1. The storage cap is enforced against total offline directory size rather than image
   resources only.
2. Sync Status shows a single combined storage figure rather than text vs image breakdown.
3. A regression causes content to be auto-downloaded when a bookmark is added even
   when offline reading is disabled.
4. Multiple concurrent `BatchArticleLoadWorker` instances run simultaneously, causing
   uncoordinated downloading and pruning.
5. The storage enforcement algorithm has no hysteresis — it prunes to exactly the limit,
   causing a seesaw oscillation as each new batch immediately re-exceeds the cap.

Issues 4 and 5 are confirmed by log analysis (`debug/2026-04-01/MyDeckAppLog.0` and
`MyDeckAppLog.1`).

---

## Issues

### 1. Storage limit applies to total storage, not images only

**File:** `BatchArticleLoadWorker.kt` — `enforceImageStorageLimit()`

`contentPackageManager.calculateTotalSize()` walks the entire `offlineContentDir` and
sums all files, including `index.html` text files. The spec states the cap should apply
to image resources only, with text HTML preserved.

`ContentResourceEntity` already has a `byteSize: Long` field for every downloaded
resource. A DAO query over `content_resource` can sum image bytes directly without any
filesystem walk.

**Fix:**

- Add a DAO query to `BookmarkDao` summing `content_resource.byteSize` for image
  resources (filter by `mimeType LIKE 'image/%'` or `group IN ('image', 'thumbnail',
  'embedded')`).
- Add a `calculateImageSize(): Long` method to `ContentPackageManager` backed by that
  query.
- Replace `calculateTotalSize()` in the enforcement logic with `calculateImageSize()`.

---

### 2. Sync Status shows combined storage, not text vs image breakdown

**Files:** `SyncSettingsViewModel.kt`, `SyncSettingsScreen.kt`, `SyncStatusUiState`

The current single `offlineStorageSize: String?` field and one-line display do not
distinguish text from image storage.

**Fix:**

- Reuse the image-size DAO query from issue 1.
- Text size = `calculateTotalSize() - calculateImageSize()`.
- Replace `offlineStorageSize: String?` in `SyncStatusUiState` with two fields:
  `textStorageSize: String?` and `imageStorageSize: String?`.
- Update `SyncSettingsViewModel` to load and format both values.
- Update `SyncSettingsScreen` to display two lines, e.g.:
  - `Text: 4.2 MB`
  - `Images: 187 MB`
- Add two new string resources across all 10 language files.

---

### 3. Regression: content auto-downloaded when offline reading is disabled

**File:** `BookmarkRepositoryImpl.kt`

`createBookmark()` calls `enqueueArticleDownload(bookmarkId)` unconditionally in two
places:

- directly, if the bookmark is already in LOADED state when first fetched
- inside `pollForBookmarkReady()`, when the bookmark transitions to LOADED

Neither call site checks `isOfflineReadingEnabled()`. Adding a bookmark while offline
reading is disabled still triggers a `LoadArticleWorker` job and downloads text content.

When offline reading is disabled, the correct behaviour is:

- no content is downloaded on bookmark creation
- content is fetched on demand the first time the user opens the bookmark

**Fix:**

Guard both `enqueueArticleDownload` call sites:

```kotlin
if (settingsDataStore.isOfflineReadingEnabled()) {
    enqueueArticleDownload(bookmarkId)
}
```

---

### 4. Multiple concurrent BatchArticleLoadWorker instances

**Root cause confirmed by logs.**

`ExistingWorkPolicy.KEEP` only prevents a new job from being enqueued while one is
still *queued and waiting*. Once a worker transitions to RUNNING, WorkManager considers
the slot open — a new KEEP enqueue succeeds immediately. Since each worker run can take
minutes (1,600+ bookmarks), every subsequent metadata sync completion that calls
`enqueueContentSyncIfNeeded()` launches a fresh concurrent instance. Workers cancelled
by constraint changes (Wi-Fi drop, battery saver) and retried by WorkManager add
further instances.

Log evidence: PIDs 344, 345, 346, 353, 354, 358, 360, 361, 391 all observed running
`BatchArticleLoadWorker` within the same session. Each independently downloads and
prunes with no coordination.

**Fix:**

- Change `ExistingWorkPolicy.KEEP` to `ExistingWorkPolicy.REPLACE` so that a new
  enqueue cancels any in-flight worker and starts fresh. This is safe because the worker
  is designed to resume from the current pending set on each run.
- Additionally, add a pre-flight check at the start of `doWork()` using a companion
  object `AtomicBoolean` or Hilt-injected singleton flag so that if two instances do
  somehow overlap (e.g. due to WorkManager retry), the second bails out immediately
  with `Result.success()`.

Note: switching to REPLACE means a settings change (e.g. toggling image download) will
cancel the current run and restart — which is actually the correct behaviour since the
new run will pick up the new settings.

---

### 5. Storage enforcement has no hysteresis — causes seesaw oscillation

**Root cause confirmed by logs.**

The current enforcement loop prunes until `currentSize <= limit.bytes` exactly, leaving
zero headroom. The sequence from the log:

1. Prune completes, size lands just under limit (~99.9 MB at 100 MB cap)
2. Next batch of 10 bookmarks downloads images, pushing to ~113 MB
3. Enforcement kicks in, strips images from those same bookmarks just downloaded
4. Size returns to ~98 MB
5. Repeat indefinitely

This is a classic cache oscillation caused by the lack of a hysteresis band. Additionally,
with a batch size of 10, each batch can represent a significant and unpredictable spike
in image storage (observed: 32, 12, 10, 6, 5 resources per bookmark in a single batch).

The overshoot problem (1.3 GB against a 100 MB cap) occurs when the library is large
enough that the worker runs many batches before enforcement fires — each batch adds
unchecked image storage before the post-batch check runs.

**Fix — two-watermark hysteresis with adaptive batch sizing:**

Define two thresholds as fractions of the configured limit:

- **High watermark** (95% of limit): trigger pruning
- **Low watermark** (80% of limit): target to prune down to — do not stop at the
  limit, stop here
- **Download gate** (90% of limit): before starting each batch, check current image
  size; if at or above this threshold, reduce batch size from 10 to 1-3 bookmarks
  to maintain fine-grained control near the boundary

The enforcement algorithm becomes:

```
before each batch:
    imageSize = calculateImageSize()
    if imageSize >= limit * 0.90:
        batchSize = 1  (or 2-3)
    else:
        batchSize = BATCH_SIZE (10)

after each batch:
    imageSize = calculateImageSize()
    if imageSize >= limit * 0.95:
        pruneUntil(limit * 0.80)
```

The specific percentages (80/90/95) should be tuned based on observed bookmark image
sizes. With very large images per bookmark, wider margins may be needed.

A pre-run check should also enforce the low watermark before the first batch begins,
to handle the case where a prior run left storage above the gate threshold.

---

## Research Note

Before finalising the algorithm, review established cache eviction strategies for
mobile storage quota management. Relevant areas: two-level hysteresis in cache
management, Android `StorageManager` quota APIs, LRU vs recency-weighted eviction.
The current oldest-first eviction order (by `created ASC`) may benefit from
reconsideration — evicting the oldest bookmarks means newly-downloaded images are
sometimes stripped immediately if those bookmarks happen to be the oldest in the
library.

---

## Open Questions / Future Work

### Picture bookmark images and the "Download Images off" case

Picture bookmarks are treated specially: their primary image is downloaded even when
"Download images" is disabled, because without it there is nothing to show. This means
`calculateImageSize()` (and the storage cap) can report non-zero image storage even
when the user believes image download is off. It is not yet decided how to handle this:

- Exempt picture bookmark images from the cap entirely?
- Count them but exclude them from eviction candidates?
- Show a separate "Picture images" line in Sync Status?

TODO: work through the right approach before the storage limit feature is considered
complete. Not a blocker for current testing.

---

## Out of Scope

- Changing `LoadArticleWorker` / `LoadArticleUseCase` from the legacy article endpoint
  to multipart (tracked in `managed-offline-reading-spec.md`).
- Per-bookmark storage accounting.
- Storage limit enforcement for the single-bookmark add path.

---

## Acceptance Criteria

- Adding a bookmark while offline reading is disabled does not download any content.
- The image storage cap is evaluated against image resource bytes only.
- Storage never meaningfully exceeds the configured cap during a normal batch run.
- Image storage does not oscillate around the cap boundary on consecutive batch runs.
- Only one `BatchArticleLoadWorker` instance runs at a time.
- Sync Status shows separate lines for text and image storage.
- All existing unit tests pass; new tests cover the guard and the enforcement algorithm.
