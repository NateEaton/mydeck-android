package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.mydeck.app.domain.policy.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.policy.PolicyReason
import com.mydeck.app.io.db.dao.BookmarkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import timber.log.Timber

/**
 * Worker that downloads content for bookmarks within a specified date range
 */
@HiltWorker
class DateRangeContentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val contentSyncPolicyEvaluator: ContentSyncPolicyEvaluator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Timber.d("Starting date range content sync")
            
            // Check policy first
            val policyResult = contentSyncPolicyEvaluator.evaluatePolicy()
            if (!policyResult.allowed) {
                Timber.w("Date range content sync blocked by policy: ${policyResult.reason}")
                return Result.failure()
            }
            
            // Get date range parameters
            val dateRange = contentSyncPolicyEvaluator.getDateRangeParams()
            val fromDate = dateRange.fromDate
            val toDate = dateRange.toDate
            
            if (fromDate == null || toDate == null) {
                Timber.w("Date range not properly configured")
                return Result.failure()
            }
            
            Timber.d("Syncing content for date range: $fromDate to $toDate")
            
            // Get bookmarks within date range that don't have content
            val bookmarksNeedingContent = getBookmarksInDateRangeWithoutContent(fromDate, toDate)
            
            if (bookmarksNeedingContent.isEmpty()) {
                Timber.d("No bookmarks in date range need content")
                return Result.success()
            }
            
            Timber.d("Found ${bookmarksNeedingContent.size} bookmarks needing content")
            
            // Enqueue individual article load workers for each bookmark
            bookmarksNeedingContent.forEach { bookmarkId ->
                LoadArticleWorker.enqueue(applicationContext, bookmarkId)
            }
            
            return Result.success(
                Data.Builder()
                    .putInt(OUTPUT_BOOKMARKS_COUNT, bookmarksNeedingContent.size)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error in date range content sync")
            return Result.failure()
        }
    }
    
    private suspend fun getBookmarksInDateRangeWithoutContent(
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val fromEpochMillis = fromDate
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            
            val toEpochMillis = toDate
                .atTime(23, 59, 59)
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            
            bookmarkDao.getBookmarkIdsInDateRangeWithoutContent(fromEpochMillis, toEpochMillis)
        }
    }
    
    companion object {
        const val UNIQUE_WORK_NAME = "date_range_content_sync"
        const val TAG = "date_range_content_sync"
        const val OUTPUT_BOOKMARKS_COUNT = "bookmarks_count"
        
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = OneTimeWorkRequestBuilder<DateRangeContentSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            
            Timber.d("Date range content sync worker enqueued")
        }
    }
}
