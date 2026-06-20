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

    override fun scheduleBatchArticleLoad(wifiOnly: Boolean, allowBatterySaver: Boolean, userInitiated: Boolean) {
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
            // User-initiated triggers must actually run: REPLACE displaces a backed-off/lingering
            // ENQUEUED instance that KEEP would otherwise silently leave in place (bug 4.6).
            val policy = if (userInitiated) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                policy,
                request
            )
            Timber.d(
                "Batch article loader enqueued (wifiOnly=$wifiOnly, allowBatterySaver=$allowBatterySaver, " +
                    "userInitiated=$userInitiated, policy=$policy)"
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue batch article loader")
        }
    }

    override fun scheduleBookmarkOrphanRepairFullSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val inputData = Data.Builder()
                .putBoolean(FullSyncWorker.INPUT_FORCE_FULL_SYNC, true)
                .putBoolean(FullSyncWorker.INPUT_IS_ORPHAN_REPAIR, true)
                .build()
            val request = OneTimeWorkRequestBuilder<FullSyncWorker>()
                .setConstraints(constraints)
                .addTag(FullSyncWorker.TAG)
                .setInputData(inputData)
                .build()
            workManager.enqueueUniqueWork(
                FullSyncWorker.UNIQUE_NAME_ORPHAN_REPAIR,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("Bookmark orphan repair full sync enqueued")
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue bookmark orphan repair full sync")
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
