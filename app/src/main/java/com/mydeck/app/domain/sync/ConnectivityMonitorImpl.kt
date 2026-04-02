package com.mydeck.app.domain.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectivityMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        // NET_CAPABILITY_VALIDATED means Android has verified actual connectivity,
        // not just that the network claims to have internet (NET_CAPABILITY_INTERNET)
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun isOnWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun isBatterySaverOn(): Boolean {
        return powerManager.isPowerSaveMode
    }

    override fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        // Emit initial state
        trySend(isNetworkAvailable())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
