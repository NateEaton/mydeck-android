package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mydeck.app.domain.BookmarkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Provider
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class ActionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkRepositoryProvider: Provider<BookmarkRepository>
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (bookmarkRepositoryProvider.get().syncPendingActions()) {
            is BookmarkRepository.UpdateResult.Success -> Result.success()
            is BookmarkRepository.UpdateResult.NetworkError -> Result.retry()
            is BookmarkRepository.UpdateResult.Error -> Result.failure()
        }
    }

    private fun isTransientError(e: Exception): Boolean {
        return e is IOException || e.message?.contains("500") == true || e.message?.contains("429") == true || e.message?.contains("408") == true
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "OfflineActionSync"

        fun enqueue(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ActionSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
