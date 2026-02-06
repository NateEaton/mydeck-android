package com.mydeck.app.domain.policy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.mydeck.app.io.prefs.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Content sync policy modes
 */
enum class ContentSyncMode {
    AUTOMATIC,
    MANUAL,
    DATE_RANGE
}

/**
 * Result of policy evaluation
 */
data class PolicyResult(
    val allowed: Boolean,
    val reason: PolicyReason
)

/**
 * Reasons for policy decisions
 */
enum class PolicyReason {
    ALLOWED,
    NO_WIFI,
    BATTERY_SAVER_ACTIVE,
    OFFLINE,
    MANUAL_MODE,
    DATE_RANGE_NOT_SET,
    POLICY_NOT_SET
}

/**
 * Date range parameters for content sync
 */
data class DateRangeParams(
    val fromDate: kotlinx.datetime.LocalDate?,
    val toDate: kotlinx.datetime.LocalDate?
)

/**
 * Evaluates content sync policy based on user settings and device state
 */
@Singleton
class ContentSyncPolicyEvaluator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    
    /**
     * Evaluate if content sync is allowed under current conditions
     */
    suspend fun evaluatePolicy(): PolicyResult {
        val mode = getContentSyncMode()
        
        return when (mode) {
            ContentSyncMode.AUTOMATIC -> evaluateAutomaticMode()
            ContentSyncMode.MANUAL -> PolicyResult(false, PolicyReason.MANUAL_MODE)
            ContentSyncMode.DATE_RANGE -> evaluateDateRangeMode()
        }
    }
    
    /**
     * Get current content sync mode
     */
    suspend fun getContentSyncMode(): ContentSyncMode {
        return settingsDataStore.getContentSyncMode()
    }
    
    /**
     * Get date range parameters for date range mode
     */
    suspend fun getDateRangeParams(): DateRangeParams {
        return settingsDataStore.getContentSyncDateRange()
    }
    
    /**
     * Set content sync mode
     */
    suspend fun setContentSyncMode(mode: ContentSyncMode) {
        settingsDataStore.setContentSyncMode(mode)
        Timber.d("Content sync mode set to: $mode")
    }
    
    /**
     * Set date range parameters
     */
    suspend fun setDateRangeParams(params: DateRangeParams) {
        settingsDataStore.setContentSyncDateRange(params)
        Timber.d("Content sync date range set to: $params")
    }
    
    /**
     * Check if Wi-Fi only constraint is enabled
     */
    suspend fun isWifiOnlyEnabled(): Boolean {
        return settingsDataStore.isWifiOnlyEnabled()
    }
    
    /**
     * Set Wi-Fi only constraint
     */
    suspend fun setWifiOnlyEnabled(enabled: Boolean) {
        settingsDataStore.setWifiOnlyEnabled(enabled)
        Timber.d("Wi-Fi only constraint set to: $enabled")
    }
    
    /**
     * Check if battery saver constraint is enabled
     */
    suspend fun isBatterySaverAllowed(): Boolean {
        return settingsDataStore.isBatterySaverAllowed()
    }
    
    /**
     * Set battery saver constraint
     */
    suspend fun setBatterySaverAllowed(allowed: Boolean) {
        settingsDataStore.setBatterySaverAllowed(allowed)
        Timber.d("Battery saver allowed set to: $allowed")
    }
    
    private fun evaluateAutomaticMode(): PolicyResult {
        // Check connectivity constraints
        val connectivityResult = checkConnectivityConstraints()
        if (!connectivityResult.allowed) {
            return connectivityResult
        }
        
        // Check battery constraints
        val batteryResult = checkBatteryConstraints()
        if (!batteryResult.allowed) {
            return batteryResult
        }
        
        return PolicyResult(true, PolicyReason.ALLOWED)
    }
    
    private fun evaluateDateRangeMode(): PolicyResult {
        val dateRange = runCatching { 
            kotlinx.coroutines.runBlocking { getDateRangeParams() } 
        }.getOrNull()
        
        if (dateRange?.fromDate == null || dateRange.toDate == null) {
            return PolicyResult(false, PolicyReason.DATE_RANGE_NOT_SET)
        }
        
        // Apply same connectivity and battery constraints as automatic mode
        val connectivityResult = checkConnectivityConstraints()
        if (!connectivityResult.allowed) {
            return connectivityResult
        }
        
        val batteryResult = checkBatteryConstraints()
        if (!batteryResult.allowed) {
            return batteryResult
        }
        
        return PolicyResult(true, PolicyReason.ALLOWED)
    }
    
    private fun checkConnectivityConstraints(): PolicyResult {
        val connectivityManager = ContextCompat.getSystemService(
            context, 
            ConnectivityManager::class.java
        )
        
        if (connectivityManager == null) {
            Timber.w("ConnectivityManager not available")
            return PolicyResult(false, PolicyReason.OFFLINE)
        }
        
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            return PolicyResult(false, PolicyReason.OFFLINE)
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return PolicyResult(false, PolicyReason.OFFLINE)
        }
        
        // Check Wi-Fi constraint
        val wifiOnly = runCatching { 
            kotlinx.coroutines.runBlocking { isWifiOnlyEnabled() } 
        }.getOrNull() ?: false
        
        if (wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return PolicyResult(false, PolicyReason.NO_WIFI)
        }
        
        return PolicyResult(true, PolicyReason.ALLOWED)
    }
    
    private fun checkBatteryConstraints(): PolicyResult {
        val batterySaverAllowed = runCatching { 
            kotlinx.coroutines.runBlocking { isBatterySaverAllowed() } 
        }.getOrNull() ?: true
        
        if (batterySaverAllowed) {
            return PolicyResult(true, PolicyReason.ALLOWED)
        }
        
        val powerManager = ContextCompat.getSystemService(
            context,
            PowerManager::class.java
        )
        
        if (powerManager?.isPowerSaveMode == true) {
            return PolicyResult(false, PolicyReason.BATTERY_SAVER_ACTIVE)
        }
        
        return PolicyResult(true, PolicyReason.ALLOWED)
    }
}
