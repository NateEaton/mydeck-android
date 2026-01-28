package com.mydeck.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
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
            val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()

            // Step 1: Handle deletions via delta sync or full sync
            var syncResult = if (lastSyncTimestamp != null) {
                Timber.d("Performing delta sync since=$lastSyncTimestamp")
                bookmarkRepository.performDeltaSync(lastSyncTimestamp)
            } else {
                Timber.d("Performing full sync (no previous timestamp)")
                bookmarkRepository.performFullSync()
            }

            // If delta sync failed with an error (e.g., HTTP 500), fall back to full sync
            if (syncResult is SyncResult.Error && lastSyncTimestamp != null) {
                Timber.w("Delta sync failed, falling back to full sync")
                syncResult = bookmarkRepository.performFullSync()
            }

            // Check if deletion sync failed
            when (syncResult) {
                is SyncResult.Error -> {
                    showNotification(syncResult)
                    return Result.failure()
                }
                is SyncResult.NetworkError -> {
                    showNotification(syncResult)
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
                    showNotification(SyncResult.Error("Failed to load bookmarks", ex = loadResult.exception as? Exception))
                    Result.failure()
                }
                is LoadBookmarksUseCase.UseCaseResult.Success -> {
                    // Save the current timestamp after successful sync
                    settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                    showNotification(syncResult)
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


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationChannel = NotificationChannel(
                notificationChannelId,
                applicationContext.getString(R.string.auto_sync_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )

            val notificationManager: NotificationManager? =
                getSystemService(
                    applicationContext,
                    NotificationManager::class.java
                )

            notificationManager?.createNotificationChannel(
                notificationChannel
            )
        }
    }

    private fun showNotification(syncResult: SyncResult) {
        createNotificationChannel()

        val contentText = when (syncResult) {
            is SyncResult.Success -> {
                applicationContext.getString(R.string.auto_sync_notification_success, syncResult.countDeleted)
            }
            else -> {
                applicationContext.getString(R.string.auto_sync_notification_failure)
            }
        }

        val notification = NotificationCompat.Builder(
            applicationContext,
            notificationChannelId
        )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(contentText)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(NOTIFICATION_ID, notification)
            }
        } else {
            Timber.w("No permission to show notification")
        }
    }


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
    }
}
