package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber

@HiltWorker
class LoadBookmarksWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val loadBookmarksUseCase: LoadBookmarksUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val isInitialLoad = inputData.getBoolean(PARAM_IS_INITIAL_LOAD, false)

        if (isInitialLoad && isAnotherWorkerRunning()) {
            Timber.i("Another LoadBookmarksWorker is running, exiting early.")
            return Result.success() // Or Result.failure() if you want to signal an error
        }

        if (isInitialLoad) {
            try {
                Timber.i("Performing initial sync: Deleting all bookmarks.")
                bookmarkRepository.deleteAllBookmarks()
                Timber.i("Performing initial sync: Reset lastBookmarkTimestamp.")
                settingsDataStore.saveLastBookmarkTimestamp(Instant.fromEpochMilliseconds(0))
            } catch (e: Exception) {
                Timber.e(e, "Error preparing for initial loading.")
                return Result.failure()
            }
        }

        // Run delta sync to catch deletions (lightweight, only fetches changed IDs)
        if (!isInitialLoad) {
            // Prefer lastSyncTimestamp (advances after every delta sync) over lastBookmarkTimestamp
            // (only advances when bookmark metadata changes, not on deletions)
            val syncSince = settingsDataStore.getLastSyncTimestamp()
                ?: settingsDataStore.getLastBookmarkTimestamp()
            if (syncSince != null && syncSince.epochSeconds > 0) {
                val deltaResult = bookmarkRepository.performDeltaSync(syncSince)
                when (deltaResult) {
                    is BookmarkRepository.SyncResult.Success -> {
                        settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                        if (deltaResult.countDeleted > 0) {
                            Timber.i("Delta sync removed ${deltaResult.countDeleted} deleted bookmarks")
                        }
                    }
                    else -> Timber.w("Delta sync failed during pull-to-refresh, continuing with incremental load")
                }
            }
        }

        return when (val result = loadBookmarksUseCase.execute()) {
            is LoadBookmarksUseCase.UseCaseResult.Success -> {
                Result.success()
            }
            is LoadBookmarksUseCase.UseCaseResult.Error -> {
                Timber.e(result.exception, "Error loading bookmarks")
                Result.failure() // Or Result.retry() depending on the error
            }
        }
    }

    private suspend fun isAnotherWorkerRunning(): Boolean {
        return withContext(Dispatchers.IO) {
            val workInfos = WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
                .get()

            // Check if there's another worker running with a different ID.
            // This prevents the worker from exiting early if it's briefly interrupted
            // and then rescheduled.
            workInfos.any { it.id != id && it.state == WorkInfo.State.RUNNING }
        }
    }

    companion object {
        const val PARAM_IS_INITIAL_LOAD = "isInitialLoad"
        const val UNIQUE_WORK_NAME = "LoadBookmarksSync"

        fun enqueue(context: Context, isInitialLoad: Boolean = false) {
            val data = Data.Builder().putBoolean(PARAM_IS_INITIAL_LOAD, isInitialLoad).build()

            val request = OneTimeWorkRequestBuilder<LoadBookmarksWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)

        }
    }
}
