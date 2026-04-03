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
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
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
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Priority single-bookmark path: skip the batch DAO query and isRunning guard.
        val priorityBookmarkId = inputData.getString(KEY_PRIORITY_BOOKMARK_ID)
        if (priorityBookmarkId != null) {
            return processPriorityBookmark(priorityBookmarkId)
        }

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

            var batchIndex = 0
            var offset = 0
            while (offset < pendingBookmarkIds.size) {
                // Check constraints before each batch
                if (!policyEvaluator.canFetchContent().allowed) {
                    Timber.i("Content fetch blocked by constraints, stopping batch")
                    return Result.success()
                }

                val batch = pendingBookmarkIds.subList(offset, minOf(offset + BATCH_SIZE, pendingBookmarkIds.size))
                offset += batch.size
                batchIndex++

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

                if (offset < pendingBookmarkIds.size) {
                    delay(BATCH_DELAY_MS)
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

    private suspend fun processPriorityBookmark(bookmarkId: String): Result {
        return try {
            Timber.d("BatchArticleLoadWorker: priority download starting for $bookmarkId")
            if (!policyEvaluator.canFetchContent().allowed) {
                Timber.i("Priority content fetch blocked by constraints for $bookmarkId")
                return Result.success()
            }
            loadContentPackageUseCase.executeBatch(listOf(bookmarkId))
            Timber.i("BatchArticleLoadWorker: priority download completed for $bookmarkId")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker: priority download failed for $bookmarkId")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        const val KEY_PRIORITY_BOOKMARK_ID = "priority_bookmark_id"
        private const val PRIORITY_WORK_NAME_PREFIX = "content_priority_"
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 500L

        // In-flight guard — prevents concurrent batch instances from running simultaneously
        private val isRunning = AtomicBoolean(false)

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
