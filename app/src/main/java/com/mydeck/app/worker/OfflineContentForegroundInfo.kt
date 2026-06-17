package com.mydeck.app.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.mydeck.app.MainActivity
import com.mydeck.app.R
import com.mydeck.app.SYNC_NOTIFICATION_CHANNEL_ID

/**
 * Notification ID for offline-content foreground sync. Distinct from the metadata-sync workers
 * (LoadBookmarksWorker = 1, FullSyncWorker = 2) so a content-sync notification never clobbers a
 * metadata-sync one. The two offline-content workers share this ID intentionally — only one
 * "downloading offline content" notification slot is ever wanted.
 */
const val OFFLINE_CONTENT_NOTIFICATION_ID = 3

/**
 * Builds the [ForegroundInfo] used to promote the offline-content sync workers
 * ([BatchArticleLoadWorker], [DateRangeContentSyncWorker]) to a foreground (dataSync) service.
 *
 * This mirrors the metadata-sync workers' notification exactly so offline-content sync has the
 * same OS-level visibility, reusing the shared [SYNC_NOTIFICATION_CHANNEL_ID] channel. Callers
 * promote via `setForeground(getForegroundInfo())` wrapped in a try/catch that logs and continues
 * when promotion is disallowed — the download must never depend on notification success.
 */
fun offlineContentForegroundInfo(context: Context): ForegroundInfo {
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, SYNC_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.offline_content_notification_running))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    return ForegroundInfo(
        OFFLINE_CONTENT_NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )
}
