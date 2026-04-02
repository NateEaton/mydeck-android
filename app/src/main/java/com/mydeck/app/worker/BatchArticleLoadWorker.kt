package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.sync.OfflineImageStorageLimit
import com.mydeck.app.domain.usecase.LoadContentPackageUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadContentPackageUseCase: LoadContentPackageUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator,
    private val settingsDataStore: SettingsDataStore,
    private val contentPackageManager: com.mydeck.app.domain.content.ContentPackageManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Guard against concurrent instances (e.g. from WorkManager retries overlapping
        // with a new enqueue triggered by a subsequent metadata sync).
        if (!isRunning.compareAndSet(false, true)) {
            Timber.w("BatchArticleLoadWorker: another instance is already running, bailing out")
            return Result.success()
        }

        try {
            Timber.d("BatchArticleLoadWorker starting")

            val includeArchived = settingsDataStore.getOfflineContentScope().includesArchived
            val pendingBookmarkIds = bookmarkDao.getBookmarkIdsEligibleForContentFetch(includeArchived)
            Timber.i("Found ${pendingBookmarkIds.size} bookmarks without content (includeArchived=$includeArchived)")

            if (pendingBookmarkIds.isEmpty()) {
                return Result.success()
            }

            // Enforce limit before the first batch in case a prior run left us over the gate
            var limitHitDuringRun = enforceImageStorageLimitIfNeeded(trigger = "pre_run")

            var batchIndex = 0
            var offset = 0
            while (offset < pendingBookmarkIds.size) {
                // Check constraints before each batch
                if (!policyEvaluator.canFetchContent().allowed) {
                    Timber.i("Content fetch blocked by constraints, stopping batch")
                    return Result.success()
                }

                // Adaptive batch size: shrink near the storage gate threshold
                val currentBatchSize = adaptiveBatchSize()
                val batch = pendingBookmarkIds.subList(offset, minOf(offset + currentBatchSize, pendingBookmarkIds.size))
                offset += batch.size
                batchIndex++

                if (limitHitDuringRun) {
                    // Image cap already reached this run. Restore bookmarks that already have
                    // text content to DOWNLOADED without re-downloading. Any that genuinely
                    // have no content yet (state 0 / no package) remain dirty for the next run.
                    Timber.d("Processing batch $batchIndex, size=${batch.size} — restoring existing text (limit reached)")
                    bookmarkDao.restoreDownloadedStateIfHasContent(batch)
                } else {
                    Timber.d("Processing batch $batchIndex, size=${batch.size}")

                    coroutineScope {
                        val perIdResults = loadContentPackageUseCase.executeBatch(batch)
                        perIdResults.entries.map { (id, res) ->
                            async {
                                if (res is LoadContentPackageUseCase.Result.TransientFailure) {
                                    Timber.d("Multipart content fetch transient failure for $id: ${res.reason}")
                                } else if (res is LoadContentPackageUseCase.Result.PermanentFailure) {
                                    Timber.d("Multipart content fetch permanent failure for $id: ${res.reason}")
                                }
                            }
                        }.awaitAll()
                    }

                    // Brief pause between image-download batches to reduce resource contention
                    if (offset < pendingBookmarkIds.size) {
                        delay(BATCH_DELAY_MS)
                    }

                    if (enforceImageStorageLimitIfNeeded(trigger = "post_batch")) {
                        limitHitDuringRun = true
                    }
                }
            }

            settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())
            Timber.i("BatchArticleLoadWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker failed")
            return Result.retry()
        } finally {
            isRunning.set(false)
        }
    }

    /**
     * Returns the batch size to use for the next download batch.
     * Shrinks to BATCH_SIZE_NEAR_LIMIT when image storage is within the gate threshold,
     * so we have finer control near the boundary.
     */
    private suspend fun adaptiveBatchSize(): Int {
        val limit = settingsDataStore.getOfflineImageStorageLimit()
        if (limit == OfflineImageStorageLimit.UNLIMITED) return BATCH_SIZE
        val imageSize = contentPackageManager.calculateImageSize()
        return if (imageSize >= limit.bytes * GATE_FRACTION) BATCH_SIZE_NEAR_LIMIT else BATCH_SIZE
    }

    /**
     * Enforces the image storage cap using a two-watermark hysteresis strategy:
     *
     * - High watermark (HIGH_FRACTION of limit): triggers pruning
     * - Low watermark (LOW_FRACTION of limit): target to prune down to
     *
     * Returns true if pruning was triggered, false if storage was within bounds.
     * The caller uses this to switch subsequent batches to text-only mode.
     */
    private suspend fun enforceImageStorageLimitIfNeeded(trigger: String): Boolean {
        if (!policyEvaluator.canFetchContent().allowed) return false

        val limit = settingsDataStore.getOfflineImageStorageLimit()
        if (limit == OfflineImageStorageLimit.UNLIMITED) return false

        var imageSize = contentPackageManager.calculateImageSize()
        val highWatermark = (limit.bytes * HIGH_FRACTION).toLong()
        val lowWatermark = (limit.bytes * LOW_FRACTION).toLong()

        if (imageSize < highWatermark) return false

        Timber.i("Storage limit exceeded (current: $imageSize, limit: ${limit.bytes}, trigger=$trigger), starting prune...")
        val includeArchived = settingsDataStore.getOfflineContentScope().includesArchived
        val evictionCandidates = bookmarkDao.getOldestBookmarkIdsWithResources(includeArchived)

        for (id in evictionCandidates) {
            if (imageSize <= lowWatermark) break

            Timber.d("Pruning images for bookmark $id to free space")
            val result = loadContentPackageUseCase.executeTextOnlyOverwrite(id)
            if (result is LoadContentPackageUseCase.Result.TransientFailure ||
                result is LoadContentPackageUseCase.Result.PermanentFailure) {
                Timber.w("Failed to text-only prune bookmark $id, skipping to next candidate")
            } else {
                imageSize = contentPackageManager.calculateImageSize()
            }
        }

        Timber.i("Finished pruning. New image size: $imageSize (low watermark: $lowWatermark)")
        return true
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        private const val BATCH_SIZE = 10
        private const val BATCH_SIZE_NEAR_LIMIT = 2
        private const val BATCH_DELAY_MS = 500L

        // Hysteresis watermarks as fractions of the configured limit
        private const val HIGH_FRACTION = 0.95   // trigger pruning above 95%
        private const val LOW_FRACTION = 0.80    // prune down to 80%
        private const val GATE_FRACTION = 0.90   // shrink batch size above 90%

        // In-flight guard — prevents concurrent instances from running simultaneously
        private val isRunning = AtomicBoolean(false)
    }
}
