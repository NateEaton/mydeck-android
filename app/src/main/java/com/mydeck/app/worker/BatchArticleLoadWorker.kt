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
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BatchArticleLoadWorker starting")

        try {
            val pendingBookmarkIds = bookmarkDao.getBookmarkIdsEligibleForContentFetch()
            Timber.i("Found ${pendingBookmarkIds.size} bookmarks without content")

            if (pendingBookmarkIds.isEmpty()) {
                return Result.success()
            }

            val batches = pendingBookmarkIds.chunked(BATCH_SIZE)
            batches.forEachIndexed { index, batch ->
                // Check constraints before each batch
                if (!policyEvaluator.canFetchContent().allowed) {
                    Timber.i("Content fetch blocked by constraints, stopping batch")
                    return Result.success()
                }

                Timber.d("Processing batch ${index + 1}, size=${batch.size}")

                coroutineScope {
                    batch.map { id ->
                        async {
                            try {
                                loadArticleUseCase.execute(id)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load article $id")
                            }
                        }
                    }.awaitAll()
                }

                // Brief pause between batches to reduce resource contention
                if (index < batches.size - 1) {
                    delay(BATCH_DELAY_MS)
                }
            }

            Timber.i("BatchArticleLoadWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker failed")
            return Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        private const val BATCH_SIZE = 5
        private const val BATCH_DELAY_MS = 500L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,  // Don't restart if already running
                request
            )
        }
    }
}
