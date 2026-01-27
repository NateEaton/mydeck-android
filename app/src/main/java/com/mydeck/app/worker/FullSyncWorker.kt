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
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.datetime.Clock
import timber.log.Timber

@HiltWorker
class FullSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    val bookmarkRepository: BookmarkRepository,
    val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Timber.d("Start Work")
            val lastSyncTimestamp = settingsDataStore.getLastSyncTimestamp()

            // Use delta sync if we have a previous timestamp, otherwise full sync
            val syncResult = if (lastSyncTimestamp != null) {
                Timber.d("Performing delta sync since=$lastSyncTimestamp")
                bookmarkRepository.performDeltaSync(lastSyncTimestamp)
            } else {
                Timber.d("Performing full sync (no previous timestamp)")
                bookmarkRepository.performFullSync()
            }

            val workResult = when (syncResult) {
                is SyncResult.Error -> Result.failure()
                is SyncResult.NetworkError -> Result.retry()
                is SyncResult.Success -> {
                    // Save the current timestamp after successful sync
                    settingsDataStore.saveLastSyncTimestamp(Clock.System.now())
                    Result.success(
                        Data.Builder().putInt(OUTPUT_DATA_COUNT, syncResult.countDeleted).build()
                    )
                }
            }
            showNotification(syncResult)
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
