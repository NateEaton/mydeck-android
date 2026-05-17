package com.mydeck.app.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.mydeck.app.MainActivity
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkAnnotationSyncReason
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.SyncPriority
import com.mydeck.app.domain.BookmarkRepository.SyncResult
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.domain.usecase.FreshnessMarkerUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.datetime.Clock
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

@HiltWorker
class FullSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    val bookmarkRepository: BookmarkRepository,
    val settingsDataStore: SettingsDataStore,
    val loadBookmarksUseCase: LoadBookmarksUseCase,
    val freshnessMarkerUseCase: FreshnessMarkerUseCase,
    val highlightsRepository: HighlightsRepository,
    val bookmarkMetadataSyncCoordinator: BookmarkMetadataSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to promote FullSyncWorker to foreground")
        }
        try {
            Timber.d("Start Work")
            val forceFullSync = inputData.getBoolean(INPUT_FORCE_FULL_SYNC, false)
            val isManualSync = inputData.getBoolean(INPUT_IS_MANUAL_SYNC, false)
            val isOrphanRepair = inputData.getBoolean(INPUT_IS_ORPHAN_REPAIR, false)
            val globalBackstopReason = if (isManualSync) {
                HighlightsRefreshReason.MANUAL_SYNC
            } else if (isOrphanRepair) {
                HighlightsRefreshReason.ORPHAN_REPAIR
            } else {
                HighlightsRefreshReason.PERIODIC_BACKSTOP
            }

            val outcome = bookmarkMetadataSyncCoordinator.withExclusiveMetadataSync(
                reason = when {
                    isManualSync -> "FullSyncWorker.MANUAL"
                    isOrphanRepair -> "FullSyncWorker.ORPHAN_REPAIR"
                    else -> "FullSyncWorker.PERIODIC"
                }
            ) {
                runMetadataSync(forceFullSync || isOrphanRepair)
            }

            if (outcome.freshnessMarkerIds.isNotEmpty()) {
                try {
                    freshnessMarkerUseCase.markDirtyForBookmarks(outcome.freshnessMarkerIds)
                } catch (e: Exception) {
                    Timber.w(e, "Freshness marking failed after metadata sync")
                }
            }
            if (outcome.enqueueContentSync) {
                loadBookmarksUseCase.enqueueContentSyncIfNeeded()
            }
            if (outcome.bookmarkAnnotationCheckIds.isNotEmpty()) {
                requestBookmarkAnnotationChecksForDeltaHints(outcome.bookmarkAnnotationCheckIds)
            }
            if (outcome.requestGlobalHighlightsBackstop) {
                requestGlobalHighlightsBackstop(globalBackstopReason)
            }

            return outcome.result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error performing sync")
            return Result.failure()
        }
    }

    private suspend fun runMetadataSync(forceFullSync: Boolean): MetadataSyncOutcome {
        // Drain the action queue before starting sync to ensure local changes are sent first
        bookmarkRepository.syncPendingActions()
        val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()
        val lastFullSyncTimestamp = settingsDataStore.getLastFullSyncTimestamp()

        // Step 1: Handle deletions via delta sync or periodic full sync
        // N9: lastSyncTimestamp==null (e.g. after DataStore migration) does NOT independently
        // trigger a full sync; only lastFullSyncTimestamp age determines that.
        val needsFullSync = forceFullSync || lastFullSyncTimestamp == null ||
            Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL

        val syncResult = if (needsFullSync) {
            Timber.d("Performing full sync for deletion detection")
            val result = bookmarkRepository.performFullSync()
            if (result is SyncResult.Success) {
                settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
            }
            result
        } else {
            // Prefer delta sync for efficiency
            Timber.d("Performing delta sync since=$lastSyncTimestamp")
            val deltaResult = bookmarkRepository.performDeltaSync(lastSyncTimestamp)

            // If delta sync fails, fall back to full sync
            if (deltaResult is SyncResult.Error) {
                Timber.w("Delta sync failed (code=${deltaResult.code}), falling back to full sync")
                val result = bookmarkRepository.performFullSync()
                if (result is SyncResult.Success) {
                    settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
                }
                result
            } else {
                deltaResult
            }
        }

        // Check if deletion sync failed
        when (syncResult) {
            is SyncResult.Error -> {
                return MetadataSyncOutcome(Result.failure())
            }
            is SyncResult.NetworkError -> {
                return MetadataSyncOutcome(Result.retry())
            }
            is SyncResult.Success -> {
                Timber.d("Deletion sync successful: ${syncResult.countDeleted} deleted")
            }
        }

        val syncSuccess = syncResult as SyncResult.Success

        if (!needsFullSync && syncSuccess.countUpdated == 0) {
            val syncTime = syncSuccess.maxServerTime ?: Clock.System.now()
            settingsDataStore.saveLastSyncTimestamp(syncTime)
            Timber.d("Skipping bookmark reload; delta sync reported no metadata updates")
            Timber.d("Bookmark delta annotation checks skipped: no updatedIds [path=FullSyncWorker]")
            return MetadataSyncOutcome(
                result = Result.success(
                    Data.Builder().putInt(OUTPUT_DATA_COUNT, syncSuccess.countDeleted).build()
                ),
                enqueueContentSync = true,
                requestGlobalHighlightsBackstop = true
            )
        }

        // Step 2: Fetch updated/new bookmarks
        return if (needsFullSync) {
            // Full sync already persisted metadata in performFullSync(); just save cursor and finish
            val syncTime = Clock.System.now()
            settingsDataStore.saveLastSyncTimestamp(syncTime)
            Timber.d("Full sync path: metadata already persisted (${syncSuccess.countUpdated} bookmarks)")
            Timber.d("Bookmark annotation checks skipped: path was full sync [path=FullSyncWorker]")
            MetadataSyncOutcome(
                result = Result.success(
                    Data.Builder().putInt(OUTPUT_DATA_COUNT, syncSuccess.countDeleted).build()
                ),
                enqueueContentSync = true,
                requestGlobalHighlightsBackstop = true
            )
        } else {
            // Delta path: fetch metadata for updated IDs via multipart POST
            val updatedIds = syncSuccess.updatedIds
            Timber.d("Fetching updated bookmarks via multipart [count=${updatedIds.size}]")
            val loadResult = loadBookmarksUseCase.execute(
                updatedIds = updatedIds,
                enqueueContentSyncAfterLoad = false
            )

            when (loadResult) {
                is LoadBookmarksUseCase.UseCaseResult.Error -> {
                    Timber.e(loadResult.exception, "Failed to load updated bookmarks")
                    Timber.d(
                        "Bookmark delta annotation checks skipped: metadata reload failed [updatedIds=%d path=FullSyncWorker]",
                        updatedIds.size
                    )
                    MetadataSyncOutcome(Result.failure())
                }
                is LoadBookmarksUseCase.UseCaseResult.Success -> {
                    // Prefer server event time from delta sync to avoid clock skew issues
                    val syncTime = syncSuccess.maxServerTime ?: Clock.System.now()
                    settingsDataStore.saveLastSyncTimestamp(syncTime)
                    MetadataSyncOutcome(
                        result = Result.success(
                            Data.Builder().putInt(OUTPUT_DATA_COUNT, syncSuccess.countDeleted).build()
                        ),
                        enqueueContentSync = true,
                        freshnessMarkerIds = updatedIds,
                        bookmarkAnnotationCheckIds = updatedIds,
                        requestGlobalHighlightsBackstop = true
                    )
                }
            }
        }
    }

    private suspend fun requestGlobalHighlightsBackstop(reason: HighlightsRefreshReason) {
        Timber.d(
            "Global highlights reconciliation requested from sync worker: reason=%s manual=%s",
            reason,
            reason == HighlightsRefreshReason.MANUAL_SYNC
        )
        try {
            highlightsRepository.requestRefresh(reason)
                .onSuccess {
                    Timber.d(
                        "Global highlights reconciliation request accepted: reason=%s path=FullSyncWorker",
                        reason
                    )
                }
                .onFailure { throwable ->
                    Timber.w(
                        throwable,
                        "Global highlights reconciliation request failed: reason=%s path=FullSyncWorker",
                        reason
                    )
                }
        } catch (throwable: Exception) {
            Timber.w(
                throwable,
                "Global highlights reconciliation request failed: reason=%s path=FullSyncWorker",
                reason
            )
        }
    }

    private suspend fun requestBookmarkAnnotationChecksForDeltaHints(updatedIds: List<String>) {
        if (updatedIds.isEmpty()) {
            Timber.d("Bookmark delta annotation checks skipped: no updatedIds [path=FullSyncWorker]")
            return
        }

        Timber.d(
            "Bookmark delta sync annotation hints: updatedIds=%d path=FullSyncWorker",
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
                    "Bookmark delta annotation check enqueue count: requested=%d path=FullSyncWorker",
                    updatedIds.size
                )
            }.onFailure { throwable ->
                Timber.w(
                    throwable,
                    "Bookmark delta annotation check enqueue failed [requested=%d path=FullSyncWorker]",
                    updatedIds.size
                )
            }
        } catch (throwable: Exception) {
            Timber.w(
                throwable,
                "Bookmark delta annotation check enqueue failed [requested=%d path=FullSyncWorker]",
                updatedIds.size
            )
        }
    }

    private data class MetadataSyncOutcome(
        val result: Result,
        val enqueueContentSync: Boolean = false,
        val freshnessMarkerIds: List<String> = emptyList(),
        val bookmarkAnnotationCheckIds: List<String> = emptyList(),
        val requestGlobalHighlightsBackstop: Boolean = false
    )

    private val notificationChannelId = "FullSyncNotificationChannelId"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val mainActivityIntent = Intent(
            applicationContext,
            MainActivity::class.java
        )

        val mainActivityPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            notificationChannelId
        )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.auto_sync_notification_running))
            .setContentIntent(mainActivityPendingIntent)
            .setAutoCancel(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }


    companion object {
        const val UNIQUE_NAME_AUTO = "auto_full_sync_work"
        const val UNIQUE_NAME_MANUAL = "manual_full_sync_work"
        const val TAG = "full_sync"
        const val OUTPUT_DATA_COUNT = "count"
        const val NOTIFICATION_ID = 2
        const val INPUT_IS_MANUAL_SYNC = "is_manual_sync"
        const val INPUT_FORCE_FULL_SYNC = "force_full_sync"
        const val INPUT_IS_ORPHAN_REPAIR = "is_orphan_repair"
        const val UNIQUE_NAME_ORPHAN_REPAIR = "orphan_repair_full_sync_work"
        val FULL_SYNC_INTERVAL = 168.hours  // Weekly safety fallback (delta sync handles deletions otherwise)
    }
}
