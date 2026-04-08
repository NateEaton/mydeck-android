package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator.Companion.avgArticleBytes
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

@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadContentPackageUseCase: LoadContentPackageUseCase,
    private val policyEvaluator: OfflinePolicyEvaluator,
    private val settingsDataStore: SettingsDataStore,
    private val contentPackageManager: ContentPackageManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.isOfflineReadingEnabled()) {
            Timber.i("BatchArticleLoadWorker: offline reading disabled, skipping work")
            return Result.success()
        }

        // Priority single-bookmark path: skip the normal batch logic.
        val priorityBookmarkId = inputData.getString(KEY_PRIORITY_BOOKMARK_ID)
        if (priorityBookmarkId != null) {
            return processPriorityBookmark(priorityBookmarkId)
        }

        val overrideConstraints = inputData.getBoolean(KEY_OVERRIDE_CONSTRAINTS, false)

        try {
            Timber.d("BatchArticleLoadWorker starting")

            val includeArchived = settingsDataStore.getOfflineContentScope().includesArchived

            // Mark bookmarks that have no extractable content (e.g. search results)
            // as PERMANENT_NO_CONTENT so they appear in the skipped stats.
            val markedNoContent = bookmarkDao.markNoContentBookmarksPermanent()
            if (markedNoContent > 0) {
                Timber.i("Marked $markedNoContent bookmarks as permanent-no-content (no extractable article)")
            }

            pruneManagedContentIfNeeded(includeArchived)

            var batchIndex = 0
            var previousPendingCount = -1
            var stalledRetries = 0
            while (true) {
                // Check constraints before each batch (skip if user overrode)
                if (!overrideConstraints && !policyEvaluator.canFetchContent().allowed) {
                    Timber.i("Content fetch blocked by constraints, stopping batch")
                    break
                }

                // Re-evaluate eligible bookmarks each iteration so we pick up
                // changes from previous batches.
                val offlinePolicyBookmarks = bookmarkDao.getOfflinePolicyBookmarks(includeArchived)
                val downloadedCount = offlinePolicyBookmarks.count { it.hasOfflinePackage }

                // Hysteresis download-threshold check: stop when usage reaches
                // the download threshold of the one-sided hysteresis band.
                val usageBeforeBatch = contentPackageManager.calculateManagedOfflineSize()

                if (policyEvaluator.shouldStopDownloading(usageBeforeBatch, downloadedCount)) {
                    val avg = avgArticleBytes(usageBeforeBatch, downloadedCount)
                    Timber.i(
                        "Download threshold reached (usage=${usageBeforeBatch / 1024}KB, " +
                            "avg=${avg / 1024}KB), stopping batch loop"
                    )
                    break
                }

                val pendingBookmarkIds = policyEvaluator
                    .let {
                        when (settingsDataStore.getOfflinePolicy()) {
                            OfflinePolicy.NEWEST_N -> {
                                bookmarkDao.getBookmarkIdsEligibleForNewestNContentFetch(
                                    settingsDataStore.getOfflinePolicyNewestN(),
                                    includeArchived
                                )
                            }

                            else -> {
                                it.selectEligibleBookmarks(offlinePolicyBookmarks)
                                    .filter { bookmark -> it.needsOfflinePackage(bookmark) }
                                    .map { bookmark -> bookmark.id }
                            }
                        }
                    }

                if (pendingBookmarkIds.isEmpty()) {
                    Timber.i("No more eligible bookmarks to fetch")
                    break
                }

                // Stalled-progress guard: if the pending count hasn't decreased
                // after several consecutive batches, no real progress is being made.
                if (pendingBookmarkIds.size == previousPendingCount) {
                    stalledRetries++
                    if (stalledRetries >= MAX_STALLED_RETRIES) {
                        Timber.i(
                            "Stalled: pending count unchanged at ${pendingBookmarkIds.size} " +
                                "after $stalledRetries retries (batch $batchIndex), stopping batch loop"
                        )
                        break
                    }
                } else {
                    stalledRetries = 0
                }
                previousPendingCount = pendingBookmarkIds.size

                if (batchIndex == 0) {
                    Timber.i(
                        "Found ${pendingBookmarkIds.size} offline policy candidates to fetch (includeArchived=$includeArchived)"
                    )
                }

                // Adaptive batch size: fit as many articles as headroom allows.
                val headroom = policyEvaluator.downloadHeadroomBytes(usageBeforeBatch, downloadedCount)
                val avg = avgArticleBytes(usageBeforeBatch, downloadedCount)
                val batchSize = adaptiveBatchSize(headroom, avg, pendingBookmarkIds.size)

                val batch = pendingBookmarkIds.take(batchSize)
                batchIndex++

                Timber.d(
                    "Processing batch $batchIndex, size=${batch.size} " +
                        "(headroom=${headroom / 1024}KB, avg=${avg / 1024}KB, " +
                        "pending=${pendingBookmarkIds.size})"
                )

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

                pruneManagedContentIfNeeded(includeArchived)

                delay(BATCH_DELAY_MS)
            }

            settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())
            Timber.i("BatchArticleLoadWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker failed")
            return Result.retry()
        }
    }

    private suspend fun processPriorityBookmark(bookmarkId: String): Result {
        return try {
            Timber.d("BatchArticleLoadWorker: priority download starting for $bookmarkId")
            if (!settingsDataStore.isOfflineReadingEnabled()) {
                Timber.i("Priority content fetch skipped because offline reading is disabled for $bookmarkId")
                return Result.success()
            }
            if (!settingsDataStore.getOfflineContentScope().includesArchived && bookmarkDao.getIsArchived(bookmarkId)) {
                Timber.i("Priority content fetch skipped for archived bookmark outside offline scope: $bookmarkId")
                return Result.success()
            }
            if (!policyEvaluator.canFetchContent().allowed) {
                Timber.i("Priority content fetch blocked by constraints for $bookmarkId")
                return Result.success()
            }
            loadContentPackageUseCase.executeBatch(listOf(bookmarkId))
            settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())
            Timber.i("BatchArticleLoadWorker: priority download completed for $bookmarkId")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker: priority download failed for $bookmarkId")
            Result.retry()
        }
    }

    /**
     * Prune managed offline content using one-sided hysteresis thresholds.
     *
     * Entry condition  : [OfflinePolicyEvaluator.shouldPrune] — usage above
     *                     hard ceiling (`target`).
     * Continue condition: [OfflinePolicyEvaluator.isPrunedEnough] — keep
     *                     removing until usage is at or below the soft ceiling
     *                     (`target − avg`).
     */
    private suspend fun pruneManagedContentIfNeeded(includeArchived: Boolean) {
        if (settingsDataStore.getOfflinePolicy() == OfflinePolicy.NEWEST_N) {
            val newestN = settingsDataStore.getOfflinePolicyNewestN()
            val outsideWindow = bookmarkDao.getDownloadedBookmarkIdsOutsideNewestN(newestN, includeArchived)
            if (outsideWindow.isNotEmpty()) {
                Timber.i(
                    "Prune cycle starting for NEWEST_N window overflow " +
                        "(outsideWindow=${outsideWindow.size}, newestN=$newestN)"
                )
                outsideWindow.forEach { pruneId ->
                    contentPackageManager.deleteContentForBookmark(pruneId)
                    Timber.i("Pruned managed offline content for $pruneId")
                }
                val remainingOutsideWindow =
                    bookmarkDao.getDownloadedBookmarkIdsOutsideNewestN(newestN, includeArchived).size
                val usageAfterWindowPrune = contentPackageManager.calculateManagedOfflineSize()
                Timber.i(
                    "Prune cycle complete for NEWEST_N window overflow " +
                        "(remainingOutsideWindow=$remainingOutsideWindow, usage=${usageAfterWindowPrune / 1024}KB)"
                )
            }
        }

        // --- Entry check ---
        val initialBookmarks = bookmarkDao.getOfflinePolicyBookmarks(includeArchived)
            .filter { it.hasOfflinePackage }
        if (initialBookmarks.isEmpty()) return

        val initialUsage = contentPackageManager.calculateManagedOfflineSize()
        if (!policyEvaluator.shouldPrune(initialBookmarks, initialUsage)) return

        Timber.i(
            "Prune cycle starting (usage=${initialUsage / 1024}KB, " +
                "downloaded=${initialBookmarks.size})"
        )

        // --- Prune loop: remove one at a time until soft ceiling ---
        while (true) {
            val downloadedBookmarks = bookmarkDao.getOfflinePolicyBookmarks(includeArchived)
                .filter { it.hasOfflinePackage }
            if (downloadedBookmarks.isEmpty()) return

            val totalUsageBytes = contentPackageManager.calculateManagedOfflineSize()
            if (policyEvaluator.isPrunedEnough(downloadedBookmarks, totalUsageBytes)) {
                Timber.i(
                    "Prune cycle complete (usage=${totalUsageBytes / 1024}KB, " +
                        "downloaded=${downloadedBookmarks.size})"
                )
                return
            }

            val pruneId = policyEvaluator.selectForPruning(downloadedBookmarks, totalUsageBytes)
                .firstOrNull() ?: return
            contentPackageManager.deleteContentForBookmark(pruneId)
            Timber.i("Pruned managed offline content for $pruneId")
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        const val WORK_TAG_OFFLINE_CONTENT = "offline_content_work"
        const val KEY_PRIORITY_BOOKMARK_ID = "priority_bookmark_id"
        const val KEY_OVERRIDE_CONSTRAINTS = "override_constraints"
        private const val PRIORITY_WORK_NAME_PREFIX = "content_priority_"
        private const val BATCH_DELAY_MS = 500L
        private const val MAX_STALLED_RETRIES = 3

        internal const val MAX_BATCH_SIZE = 10

        /**
         * How many articles we can likely fit in the remaining headroom.
         * Returns at least 1 and at most [MAX_BATCH_SIZE].
         */
        internal fun adaptiveBatchSize(
            headroomBytes: Long,
            avgArticleBytes: Long,
            pendingCount: Int
        ): Int {
            if (avgArticleBytes <= 0) return 1
            val fits = (headroomBytes / avgArticleBytes).toInt()
            return fits.coerceIn(1, MAX_BATCH_SIZE).coerceAtMost(pendingCount)
        }

        /**
         * Enqueue a one-off full-package download for a single bookmark, bypassing the
         * normal batch queue. Uses REPLACE policy so a second open of the same bookmark
         * while a download is already pending simply resets the job.
         */
        fun enqueuePriorityDownload(
            workManager: WorkManager,
            bookmarkId: String,
            constraints: Constraints
        ) {
            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setInputData(workDataOf(KEY_PRIORITY_BOOKMARK_ID to bookmarkId))
                .setConstraints(constraints)
                .addTag(WORK_TAG_OFFLINE_CONTENT)
                .build()
            workManager.enqueueUniqueWork(
                "$PRIORITY_WORK_NAME_PREFIX$bookmarkId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.d("Priority offline package enqueued for $bookmarkId")
        }
    }
}
