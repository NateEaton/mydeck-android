package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
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
                val result = loadArticleUseCase.execute(bookmarkId)
                when (result) {
                    is LoadArticleUseCase.Result.Success,
                    is LoadArticleUseCase.Result.AlreadyDownloaded,
                    is LoadArticleUseCase.Result.PermanentFailure -> Result.success()
                    is LoadArticleUseCase.Result.TransientFailure -> Result.retry()
                }
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
    }
}
