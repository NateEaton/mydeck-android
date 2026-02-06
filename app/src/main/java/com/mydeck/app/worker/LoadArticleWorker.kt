package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import timber.log.Timber

@HiltWorker
class LoadArticleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    val loadArticleUseCase: LoadArticleUseCase
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Timber.d("Start Work with params=$workerParams")
            val bookmarkId = workerParams.inputData.getString(PARAM_BOOKMARK_ID)
            return if (bookmarkId != null) {
                Timber.i("Start loading article [bookmarkId=$bookmarkId]")
                loadArticleUseCase.execute(bookmarkId)
                Result.success()
            } else {
                Timber.w("No bookmarkId provided")
                Result.failure()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading article")
            return Result.failure()
        }
    }
    companion object {
        const val PARAM_BOOKMARK_ID = "bookmarkId"
        
        fun enqueue(context: Context, bookmarkId: String) {
            val data = Data.Builder()
                .putString(PARAM_BOOKMARK_ID, bookmarkId)
                .build()
            
            val request = OneTimeWorkRequestBuilder<LoadArticleWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "load_article_$bookmarkId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            
            Timber.d("LoadArticleWorker enqueued for bookmark: $bookmarkId")
        }
    }
}
