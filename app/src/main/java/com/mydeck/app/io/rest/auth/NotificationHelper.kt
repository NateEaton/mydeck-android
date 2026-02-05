package com.mydeck.app.io.rest.auth

import android.content.Context
import androidx.core.app.NotificationManagerCompat

interface NotificationHelper {
    fun showUnauthorizedNotification()
}

class NotificationHelperImpl(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat
) : NotificationHelper {

    @android.annotation.SuppressLint("MissingPermission")
    override fun showUnauthorizedNotification() {
        NotificationUtil.showUnauthorizedNotification(context, notificationManager)
    }
}
