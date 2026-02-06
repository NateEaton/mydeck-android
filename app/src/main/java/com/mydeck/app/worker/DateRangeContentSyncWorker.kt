package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class DateRangeContentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fromEpoch = inputData.getLong(PARAM_FROM_EPOCH, 0)
        val toEpoch = inputData.getLong(PARAM_TO_EPOCH, 0)

        Timber.d("DateRangeContentSyncWorker starting [from=$fromEpoch, to=$toEpoch]")

        val eligibleIds = bookmarkDao.getBookmarkIdsForDateRangeContentFetch(
            fromEpoch = fromEpoch, toEpoch = toEpoch
        )

        Timber.i("Found ${eligibleIds.size} bookmarks eligible for date range content fetch")

        for (id in eligibleIds) {
            if (!policyEvaluator.canFetchContent().allowed) {
                Timber.i("Constraints no longer met, stopping date range sync")
                return Result.success()
            }
            try {
                loadArticleUseCase.execute(id)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load article $id in date range sync")
            }
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "date_range_content_sync"
        const val PARAM_FROM_EPOCH = "from_epoch"
        const val PARAM_TO_EPOCH = "to_epoch"
    }
}
