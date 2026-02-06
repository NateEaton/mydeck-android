package com.mydeck.app.domain.sync

import kotlinx.coroutines.flow.Flow

interface ConnectivityMonitor {
    fun isNetworkAvailable(): Boolean
    fun isOnWifi(): Boolean
    fun isBatterySaverOn(): Boolean
    fun observeConnectivity(): Flow<Boolean>
}
