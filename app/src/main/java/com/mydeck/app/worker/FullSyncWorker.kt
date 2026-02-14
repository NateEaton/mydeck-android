package com.mydeck.app.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.BookmarkRepository.SyncResult
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.datetime.Clock
import timber.log.Timber
import kotlin.time.Duration.Companion.hours

@HiltWorker
class FullSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    val bookmarkRepository: BookmarkRepository,
    val settingsDataStore: SettingsDataStore,
    val loadBookmarksUseCase: LoadBookmarksUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Timber.d("Start Work")
            // Drain the action queue before starting sync to ensure local changes are sent first
            bookmarkRepository.syncPendingActions()
            val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()
            val lastFullSyncTimestamp = settingsDataStore.getLastFullSyncTimestamp()

            // Step 1: Determine if we need a full sync for deletion detection
            val forceFullSync = inputData.getBoolean(INPUT_FORCE_FULL_SYNC, false)
            val needsFullSync = forceFullSync || lastFullSyncTimestamp == null ||
                Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL

            var syncResult = if (needsFullSync) {
                Timber.d("Performing full sync for deletion detection")
                val result = bookmarkRepository.performFullSync()
                if (result is SyncResult.Success) {
                    settingsDataStore.saveLastFullSyncTimestamp(Clock.System.now())
                }
                result
            } else {
                // Skip deletion detection - we did a full sync recently
                Timber.d("Skipping deletion check (last full sync was recent)")
                SyncResult.Success(countDeleted = 0)
            }

            // Check if deletion sync failed
            when (syncResult) {
                is SyncResult.Error -> {
                    return Result.failure()
                }
                is SyncResult.NetworkError -> {
                    return Result.retry()
                }
                is SyncResult.Success -> {
                    Timber.d("Deletion sync successful: ${syncResult.countDeleted} deleted")
                }
            }

            // Step 2: Fetch updated/new bookmarks (this also triggers article content loading)
            Timber.d("Fetching updated bookmarks")
            val loadResult = loadBookmarksUseCase.execute()

            val workResult = when (loadResult) {
                is LoadBookmarksUseCase.UseCaseResult.Error -> {
                    Timber.e(loadResult.exception, "Failed to load updated bookmarks")
                    Result.failure()
                }
                is LoadBookmarksUseCase.UseCaseResult.Success -> {
                    // Save the current timestamp after successful sync
                    settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                    Result.success(
                        Data.Builder().putInt(OUTPUT_DATA_COUNT, (syncResult as SyncResult.Success).countDeleted).build()
                    )
                }
            }
            return workResult
        } catch (e: Exception) {
            Timber.e(e, "Error performing sync")
            return Result.failure()
        }
    }

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

        return ForegroundInfo(0, notification)
    }


    companion object {
        const val UNIQUE_NAME_AUTO = "auto_full_sync_work"
        const val UNIQUE_NAME_MANUAL = "manual_full_sync_work"
        const val TAG = "full_sync"
        const val OUTPUT_DATA_COUNT = "count"
        const val NOTIFICATION_ID = 0
        const val INPUT_IS_MANUAL_SYNC = "is_manual_sync"
        const val INPUT_FORCE_FULL_SYNC = "force_full_sync"
        val FULL_SYNC_INTERVAL = 24.hours  // Run full sync once per day
    }
}
