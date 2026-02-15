package com.mydeck.app.worker

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
}
