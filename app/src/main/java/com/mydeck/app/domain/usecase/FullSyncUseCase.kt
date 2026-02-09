package com.mydeck.app.domain.usecase

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.worker.FullSyncWorker
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class FullSyncUseCase @Inject constructor(
    private val workManager: WorkManager
) {
    val workInfoFlow = workManager.getWorkInfosForUniqueWorkFlow(FullSyncWorker.UNIQUE_NAME_AUTO)
    val syncIsRunning = workManager.getWorkInfosByTagFlow(FullSyncWorker.TAG)
        .map { it.any { it.state == WorkInfo.State.RUNNING } }

    fun performFullSync() {
        Timber.d("Start Full Sync Worker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putBoolean(FullSyncWorker.INPUT_IS_MANUAL_SYNC, true)
            .build()

        val request = OneTimeWorkRequestBuilder<FullSyncWorker>()
            .setConstraints(constraints)
            .addTag(FullSyncWorker.TAG)
            .setInputData(inputData)
            .build()
        workManager.enqueueUniqueWork(
            FullSyncWorker.UNIQUE_NAME_MANUAL,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun performForcedFullSync() {
        Timber.d("Start Forced Full Sync Worker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putBoolean(FullSyncWorker.INPUT_IS_MANUAL_SYNC, true)
            .putBoolean(FullSyncWorker.INPUT_FORCE_FULL_SYNC, true)
            .build()

        val request = OneTimeWorkRequestBuilder<FullSyncWorker>()
            .setConstraints(constraints)
            .addTag(FullSyncWorker.TAG)
            .setInputData(inputData)
            .build()
        workManager.enqueueUniqueWork(
            FullSyncWorker.UNIQUE_NAME_MANUAL,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleFullSyncWorker(autoSyncTimeframe: AutoSyncTimeframe) {
        Timber.i("Schedule Full Sync Worker [autoSyncTimeframe=$autoSyncTimeframe]")
        if (autoSyncTimeframe == AutoSyncTimeframe.MANUAL) {
            cancelFullSyncWorker()
        } else {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<FullSyncWorker>(
                autoSyncTimeframe.repeatInterval,
                autoSyncTimeframe.repeatIntervalTimeUnit
            )
                .setConstraints(constraints)
                .addTag(FullSyncWorker.TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                FullSyncWorker.UNIQUE_NAME_AUTO,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    fun cancelFullSyncWorker() {
        Timber.i("Cancel Full Sync Worker")
        workManager.cancelUniqueWork(FullSyncWorker.UNIQUE_NAME_AUTO)
    }
}
