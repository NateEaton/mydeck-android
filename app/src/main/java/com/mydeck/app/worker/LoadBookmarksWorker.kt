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
import java.io.IOException
import java.util.UUID

@HiltWorker
class LoadBookmarksWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val loadBookmarksUseCase: LoadBookmarksUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    enum class Trigger {
        INITIAL,
        APP_OPEN,
        PULL_TO_REFRESH
    }

    override suspend fun doWork(): Result {
        val isInitialLoad = inputData.getBoolean(PARAM_IS_INITIAL_LOAD, false)
        val trigger = inputData.getString(PARAM_TRIGGER)
            ?.let { runCatching { Trigger.valueOf(it) }.getOrNull() }
            ?: if (isInitialLoad) Trigger.INITIAL else Trigger.PULL_TO_REFRESH

        Timber.d("LoadBookmarksWorker start [trigger=$trigger, isInitialLoad=$isInitialLoad]")

        if (isInitialLoad && isAnotherWorkerRunning()) {
            Timber.i("Another LoadBookmarksWorker is running, exiting early.")
            return Result.success() // Or Result.failure() if you want to signal an error
        }

        // Run delta sync to catch deletions (lightweight, only fetches changed IDs)
        var pendingSyncCursor: Instant? = null
        if (!isInitialLoad) {
            // Prefer lastSyncTimestamp (advances after every delta sync) over lastBookmarkTimestamp
            // (only advances when bookmark metadata changes, not on deletions)
            val syncSince = resolveSyncSince(
                lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp(),
                lastBookmarkTimestamp = settingsDataStore.getLastBookmarkTimestamp()
            )
            if (syncSince != null) {
                when (val result = bookmarkRepository.performDeltaSync(syncSince)) {
                    is BookmarkRepository.SyncResult.Success -> {
                        pendingSyncCursor = result.maxServerTime ?: Clock.System.now()
                        if (result.countDeleted > 0) {
                            Timber.i("Delta sync removed ${result.countDeleted} deleted bookmarks")
                        }
                        if (result.countUpdated == 0) {
                            Timber.i("Delta sync reported no metadata updates; skipping bookmark reload")
                            settingsDataStore.saveLastSyncTimestamp(pendingSyncCursor!!)
                            if (!settingsDataStore.isInitialSyncPerformed()) {
                                settingsDataStore.setInitialSyncPerformed(true)
                            }
                            return Result.success()
                        }
                    }
                    else -> Timber.w("Delta sync failed during pull-to-refresh, continuing with incremental load")
                }
            }
        }

        return when (val result = loadBookmarksUseCase.execute()) {
            is LoadBookmarksUseCase.UseCaseResult.Success -> {
                pendingSyncCursor?.let { settingsDataStore.saveLastSyncTimestamp(it) }
                if (!settingsDataStore.isInitialSyncPerformed()) {
                    settingsDataStore.setInitialSyncPerformed(true)
                }
                Result.success()
            }
            is LoadBookmarksUseCase.UseCaseResult.Error -> {
                Timber.e(result.exception, "Error loading bookmarks")
                if (result.exception is IOException) {
                    Result.retry()
                } else {
                    Result.failure()
                }
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
        const val PARAM_TRIGGER = "trigger"
        const val UNIQUE_WORK_NAME = "LoadBookmarksSync"
        const val TAG_TRIGGER_APP_OPEN = "load_bookmarks_app_open"
        const val TAG_TRIGGER_PULL_TO_REFRESH = "load_bookmarks_pull_to_refresh"
        const val TAG_TRIGGER_INITIAL = "load_bookmarks_initial"

        internal fun resolveSyncSince(
            lastSyncTimestamp: Instant?,
            lastBookmarkTimestamp: Instant?
        ): Instant? {
            return lastSyncTimestamp
                ?.takeIf { it.epochSeconds > 0 }
                ?: lastBookmarkTimestamp?.takeIf { it.epochSeconds > 0 }
        }

        fun enqueue(context: Context, isInitialLoad: Boolean = false): UUID {
            val trigger = if (isInitialLoad) Trigger.INITIAL else Trigger.PULL_TO_REFRESH
            return enqueue(context, trigger, isInitialLoad)
        }

        fun enqueue(context: Context, trigger: Trigger): UUID {
            return enqueue(context, trigger, trigger == Trigger.INITIAL)
        }

        private fun enqueue(context: Context, trigger: Trigger, isInitialLoad: Boolean): UUID {
            val data = Data.Builder()
                .putBoolean(PARAM_IS_INITIAL_LOAD, isInitialLoad)
                .putString(PARAM_TRIGGER, trigger.name)
                .build()
            val policy = if (isInitialLoad) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            val request = OneTimeWorkRequestBuilder<LoadBookmarksWorker>()
                .setInputData(data)
                .addTag(
                    when (trigger) {
                        Trigger.INITIAL -> TAG_TRIGGER_INITIAL
                        Trigger.APP_OPEN -> TAG_TRIGGER_APP_OPEN
                        Trigger.PULL_TO_REFRESH -> TAG_TRIGGER_PULL_TO_REFRESH
                    }
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)

            return request.id
        }
    }
}
