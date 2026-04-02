package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import timber.log.Timber

@HiltWorker
class DateRangeContentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fromEpoch = inputData.getLong(PARAM_FROM_EPOCH, 0)
        val toEpoch = inputData.getLong(PARAM_TO_EPOCH, 0)
        val isOverride = inputData.getBoolean(PARAM_OVERRIDE, false)

        if (fromEpoch == 0L && toEpoch == 0L) {
            Timber.w("DateRangeContentSyncWorker: both fromEpoch and toEpoch are 0 — likely missing input params")
            return Result.success()
        }

        Timber.d("DateRangeContentSyncWorker starting [from=$fromEpoch, to=$toEpoch, override=$isOverride]")

        val eligibleIds = bookmarkDao.getBookmarkIdsForDateRangeContentFetch(
            fromEpoch = fromEpoch, toEpoch = toEpoch
        )

        Timber.i("Found ${eligibleIds.size} bookmarks eligible for date range content fetch")

        for (id in eligibleIds) {
            // If this is an override request, skip the constraint check
            if (!isOverride && !policyEvaluator.canFetchContent().allowed) {
                Timber.i("Constraints no longer met, stopping date range sync")
                return Result.success()
            }
            try {
                loadArticleUseCase.execute(id)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load article $id in date range sync")
            }
        }

        // Record the content sync timestamp
        settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "date_range_content_sync"
        const val PARAM_FROM_EPOCH = "from_epoch"
        const val PARAM_TO_EPOCH = "to_epoch"
        const val PARAM_OVERRIDE = "override"
    }
}
