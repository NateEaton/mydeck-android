package com.mydeck.app.domain.sync

import com.mydeck.app.io.prefs.SettingsDataStore
import javax.inject.Inject

class ContentSyncPolicyEvaluator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectivityMonitor: ConnectivityMonitor
) {
    data class Decision(
        val allowed: Boolean,
        val blockedReason: String? = null
    )

    suspend fun canFetchContent(): Decision {
        val constraints = settingsDataStore.getContentSyncConstraints()

        if (constraints.wifiOnly && !connectivityMonitor.isOnWifi()) {
            return Decision(false, "Wi-Fi required")
        }

        if (!constraints.allowOnBatterySaver && connectivityMonitor.isBatterySaverOn()) {
            return Decision(false, "Battery saver active")
        }

        if (!connectivityMonitor.isNetworkAvailable()) {
            return Decision(false, "No network")
        }

        return Decision(true)
    }

    suspend fun shouldAutoFetchContent(): Boolean {
        return settingsDataStore.getContentSyncMode() == ContentSyncMode.AUTOMATIC
            && canFetchContent().allowed
    }
}
