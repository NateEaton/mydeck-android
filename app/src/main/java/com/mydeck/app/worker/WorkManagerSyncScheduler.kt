package com.mydeck.app.worker

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mydeck.app.domain.sync.SyncScheduler
import timber.log.Timber
import javax.inject.Inject

class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager
) : SyncScheduler {

    override fun scheduleActionSync() {
        ActionSyncWorker.enqueue(workManager)
    }

    override fun scheduleArticleDownload(bookmarkId: String) {
        val request = OneTimeWorkRequestBuilder<LoadArticleWorker>()
            .setInputData(
                Data.Builder()
                    .putString(LoadArticleWorker.PARAM_BOOKMARK_ID, bookmarkId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueue(request)
        Timber.d("Article download enqueued for bookmark: $bookmarkId")
    }

    override fun scheduleBatchArticleLoad(wifiOnly: Boolean, allowBatterySaver: Boolean) {
        try {
            val constraintsBuilder = Constraints.Builder()
            if (wifiOnly) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            }
            if (!allowBatterySaver) {
                constraintsBuilder.setRequiresBatteryNotLow(true)
            }
            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setConstraints(constraintsBuilder.build())
                .addTag(BatchArticleLoadWorker.WORK_TAG_OFFLINE_CONTENT)
                .build()
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.d("Batch article loader enqueued (wifiOnly=$wifiOnly, allowBatterySaver=$allowBatterySaver)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue batch article loader")
        }
    }

    override fun scheduleBatchArticleLoadOverridingConstraints() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(BatchArticleLoadWorker.KEY_OVERRIDE_CONSTRAINTS to true))
                .addTag(BatchArticleLoadWorker.WORK_TAG_OFFLINE_CONTENT)
                .build()
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.d("Batch article loader enqueued with constraint override")
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue constraint-override batch article loader")
        }
    }
}
