package com.mydeck.app.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.mydeck.app.domain.BookmarkAnnotationSyncReason
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.SyncPriority
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.MainActivity
import com.mydeck.app.R
import kotlin.coroutines.cancellation.CancellationException
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
    private val highlightsRepository: HighlightsRepository,
    private val bookmarkMetadataSyncCoordinator: BookmarkMetadataSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    enum class Trigger {
        INITIAL,
        APP_OPEN,
        PULL_TO_REFRESH
    }

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to promote LoadBookmarksWorker to foreground")
        }
        val isInitialLoad = inputData.getBoolean(PARAM_IS_INITIAL_LOAD, false)
        val trigger = inputData.getString(PARAM_TRIGGER)
            ?.let { runCatching { Trigger.valueOf(it) }.getOrNull() }
            ?: if (isInitialLoad) Trigger.INITIAL else Trigger.PULL_TO_REFRESH

        Timber.d("LoadBookmarksWorker start [trigger=$trigger, isInitialLoad=$isInitialLoad]")

        val outcome = bookmarkMetadataSyncCoordinator.withExclusiveMetadataSync(
            reason = "LoadBookmarksWorker.${trigger.name}"
        ) {
            runMetadataSync(trigger, isInitialLoad)
        }

        if (outcome.enqueueContentSync) {
            loadBookmarksUseCase.enqueueContentSyncIfNeeded()
        }
        if (outcome.bookmarkAnnotationCheckIds.isNotEmpty()) {
            requestBookmarkAnnotationChecksForDeltaHints(outcome.bookmarkAnnotationCheckIds)
        }
        outcome.globalHighlightsReason?.let { requestGlobalHighlightsBackstop(it) }

        return outcome.result
    }

    private suspend fun runMetadataSync(
        trigger: Trigger,
        isInitialLoad: Boolean
    ): MetadataSyncOutcome {
        if (isInitialLoad) {
            // Initial load: use full sync which persists metadata from the paging response
            Timber.d("Initial load: performing full sync to bootstrap bookmarks")
            return when (val result = bookmarkRepository.performFullSync()) {
                is BookmarkRepository.SyncResult.Success -> {
                    Timber.i("Initial full sync: inserted ${result.countUpdated} bookmarks, deleted ${result.countDeleted}")
                    Timber.d("Bookmark annotation checks skipped: path was full sync [path=LoadBookmarksWorker.initial]")
                    settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                    settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
                    settingsDataStore.setInitialSyncPerformed(true)
                    MetadataSyncOutcome(
                        result = Result.success(),
                        globalHighlightsReason = trigger.toHighlightsRefreshReason()
                    )
                }
                is BookmarkRepository.SyncResult.NetworkError -> {
                    Timber.e(result.ex, "Network error during initial full sync")
                    MetadataSyncOutcome(Result.retry())
                }
                is BookmarkRepository.SyncResult.Error -> {
                    Timber.e("Initial full sync failed: ${result.errorMessage}")
                    MetadataSyncOutcome(Result.failure())
                }
            }
        }

        // Non-initial: run delta sync then fetch updated metadata via multipart
        val syncSince = resolveSyncSince(
            lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp(),
            lastBookmarkTimestamp = settingsDataStore.getLastBookmarkTimestamp()
        )
        var pendingSyncCursor: Instant? = null
        var deltaUpdatedIds: List<String>? = null

        if (syncSince != null) {
            when (val result = bookmarkRepository.performDeltaSync(syncSince)) {
                is BookmarkRepository.SyncResult.Success -> {
                    pendingSyncCursor = result.maxServerTime ?: Clock.System.now()
                    if (result.countDeleted > 0) {
                        Timber.i("Delta sync removed ${result.countDeleted} deleted bookmarks")
                    }
                    if (result.countUpdated == 0) {
                        Timber.i("Delta sync reported no metadata updates; skipping bookmark reload")
                        Timber.d("Bookmark delta annotation checks skipped: no updatedIds [path=LoadBookmarksWorker]")
                        settingsDataStore.saveLastSyncTimestamp(pendingSyncCursor!!)
                        if (!settingsDataStore.isInitialSyncPerformed()) {
                            settingsDataStore.setInitialSyncPerformed(true)
                        }
                        return MetadataSyncOutcome(
                            result = Result.success(),
                            enqueueContentSync = true,
                            globalHighlightsReason = trigger.toHighlightsRefreshReason()
                        )
                    }
                    deltaUpdatedIds = result.updatedIds
                }
                else -> Timber.w("Delta sync failed during pull-to-refresh, falling back to full sync")
            }
        }

        // If delta sync failed or no cursor was available, deltaUpdatedIds is null.
        // Fall back to a full sync rather than silently returning success with stale data.
        if (deltaUpdatedIds == null) {
            Timber.w("No delta IDs available (delta failed or no cursor) — performing full sync fallback")
            return when (val result = bookmarkRepository.performFullSync()) {
                is BookmarkRepository.SyncResult.Success -> {
                    Timber.i("Full sync fallback: updated ${result.countUpdated}, deleted ${result.countDeleted}")
                    Timber.d("Bookmark annotation checks skipped: path was full sync [path=LoadBookmarksWorker.fallback]")
                    settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                    if (!settingsDataStore.isInitialSyncPerformed()) {
                        settingsDataStore.setInitialSyncPerformed(true)
                    }
                    MetadataSyncOutcome(
                        result = Result.success(),
                        enqueueContentSync = true,
                        globalHighlightsReason = trigger.toHighlightsRefreshReason()
                    )
                }
                is BookmarkRepository.SyncResult.NetworkError -> {
                    Timber.e(result.ex, "Network error during full sync fallback")
                    MetadataSyncOutcome(Result.retry())
                }
                is BookmarkRepository.SyncResult.Error -> {
                    Timber.e("Full sync fallback failed: ${result.errorMessage}")
                    MetadataSyncOutcome(Result.failure())
                }
            }
        }

        return when (val result = loadBookmarksUseCase.execute(
            updatedIds = deltaUpdatedIds,
            enqueueContentSyncAfterLoad = false
        )) {
            is LoadBookmarksUseCase.UseCaseResult.Success -> {
                pendingSyncCursor?.let { settingsDataStore.saveLastSyncTimestamp(it) }
                if (!settingsDataStore.isInitialSyncPerformed()) {
                    settingsDataStore.setInitialSyncPerformed(true)
                }
                MetadataSyncOutcome(
                    result = Result.success(),
                    enqueueContentSync = true,
                    bookmarkAnnotationCheckIds = deltaUpdatedIds.orEmpty(),
                    globalHighlightsReason = trigger.toHighlightsRefreshReason()
                )
            }
            is LoadBookmarksUseCase.UseCaseResult.Error -> {
                Timber.e(result.exception, "Error loading bookmarks")
                Timber.d(
                    "Bookmark delta annotation checks skipped: metadata reload failed [updatedIds=%d path=LoadBookmarksWorker]",
                    deltaUpdatedIds.orEmpty().size
                )
                if (result.exception is IOException) {
                    MetadataSyncOutcome(Result.retry())
                } else {
                    MetadataSyncOutcome(Result.failure())
                }
            }
        }
    }

    private suspend fun requestGlobalHighlightsBackstop(reason: HighlightsRefreshReason) {
        Timber.d(
            "Global highlights reconciliation requested from bookmark sync: reason=%s path=LoadBookmarksWorker",
            reason
        )
        try {
            highlightsRepository.requestRefresh(reason)
                .onSuccess {
                    Timber.d(
                        "Global highlights reconciliation request accepted: reason=%s path=LoadBookmarksWorker",
                        reason
                    )
                }
                .onFailure { throwable ->
                    Timber.w(
                        throwable,
                        "Global highlights reconciliation request failed: reason=%s path=LoadBookmarksWorker",
                        reason
                    )
                }
        } catch (throwable: Exception) {
            Timber.w(
                throwable,
                "Global highlights reconciliation request failed: reason=%s path=LoadBookmarksWorker",
                reason
            )
        }
    }

    private suspend fun requestBookmarkAnnotationChecksForDeltaHints(updatedIds: List<String>) {
        if (updatedIds.isEmpty()) {
            Timber.d("Bookmark delta annotation checks skipped: no updatedIds [path=LoadBookmarksWorker]")
            return
        }

        Timber.d(
            "Bookmark delta sync annotation hints: updatedIds=%d path=LoadBookmarksWorker",
            updatedIds.size
        )
        try {
            val result = highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal,
            )
            result.onSuccess {
                Timber.d(
                    "Bookmark delta annotation check enqueue count: requested=%d path=LoadBookmarksWorker",
                    updatedIds.size
                )
            }.onFailure { throwable ->
                Timber.w(
                    throwable,
                    "Bookmark delta annotation check enqueue failed [requested=%d path=LoadBookmarksWorker]",
                    updatedIds.size
                )
            }
        } catch (throwable: Exception) {
            Timber.w(
                throwable,
                "Bookmark delta annotation check enqueue failed [requested=%d path=LoadBookmarksWorker]",
                updatedIds.size
            )
        }
    }

    private data class MetadataSyncOutcome(
        val result: Result,
        val enqueueContentSync: Boolean = false,
        val bookmarkAnnotationCheckIds: List<String> = emptyList(),
        val globalHighlightsReason: HighlightsRefreshReason? = null
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.auto_sync_notification_running))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "FullSyncNotificationChannelId"
        private const val NOTIFICATION_ID = 1
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

        private fun Trigger.toHighlightsRefreshReason(): HighlightsRefreshReason {
            return when (this) {
                Trigger.APP_OPEN -> HighlightsRefreshReason.APP_OPEN
                Trigger.INITIAL,
                Trigger.PULL_TO_REFRESH -> HighlightsRefreshReason.MANUAL_SYNC
            }
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
