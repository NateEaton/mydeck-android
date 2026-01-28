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
    private val loadArticleUseCase: LoadArticleUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BatchArticleLoadWorker starting")

        try {
            val pendingBookmarkIds = bookmarkDao.getBookmarkIdsWithoutContent()
            Timber.i("Found ${pendingBookmarkIds.size} bookmarks without content")

            if (pendingBookmarkIds.isEmpty()) {
                return Result.success()
            }

            pendingBookmarkIds.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
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
                if (index < pendingBookmarkIds.chunked(BATCH_SIZE).size - 1) {
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
                .setRequiresBatteryNotLow(true)
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
