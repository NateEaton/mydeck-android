package com.mydeck.app.io.prefs

import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangeParams
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

interface SettingsDataStore {
    val tokenFlow: StateFlow<String?>
    val usernameFlow: StateFlow<String?>
    val passwordFlow: StateFlow<String?>
    val urlFlow: StateFlow<String?>
    val themeFlow: StateFlow<String?>
    val zoomFactorFlow: StateFlow<Int>
    fun saveUsername(username: String)
    fun savePassword(password: String)
    fun saveToken(token: String)
    fun saveUrl(url: String)
    suspend fun saveLastBookmarkTimestamp(timestamp: Instant)
    suspend fun getLastBookmarkTimestamp(): Instant?
    suspend fun saveLastSyncTimestamp(timestamp: Instant)
    suspend fun getLastSyncTimestamp(): Instant?
    suspend fun saveLastContentSyncTimestamp(timestamp: Instant)
    suspend fun getLastContentSyncTimestamp(): Instant?
    suspend fun saveLastFullSyncTimestamp(timestamp: Instant)
    suspend fun getLastFullSyncTimestamp(): Instant?
    suspend fun setInitialSyncPerformed(performed: Boolean)
    suspend fun isInitialSyncPerformed(): Boolean
    suspend fun clearCredentials()
    suspend fun saveCredentials(url: String, username: String, password: String, token: String)
    suspend fun setAutoSyncEnabled(isEnabled: Boolean)
    suspend fun isAutoSyncEnabled(): Boolean
    suspend fun saveAutoSyncTimeframe(autoSyncTimeframe: AutoSyncTimeframe)
    suspend fun getAutoSyncTimeframe(): AutoSyncTimeframe
    suspend fun saveTheme(theme: Theme)
    suspend fun getTheme(): Theme
    suspend fun  getZoomFactor(): Int
    suspend fun  saveZoomFactor(zoomFactor: Int)
    suspend fun setSyncOnAppOpenEnabled(isEnabled: Boolean)
    suspend fun isSyncOnAppOpenEnabled(): Boolean
    suspend fun setSyncNotificationsEnabled(isEnabled: Boolean)
    suspend fun isSyncNotificationsEnabled(): Boolean
    suspend fun saveLayoutMode(layoutMode: String)
    suspend fun getLayoutMode(): String?
    suspend fun saveSortOption(sortOption: String)
    suspend fun getSortOption(): String?
    suspend fun saveLogRetentionDays(days: Int)
    suspend fun getLogRetentionDays(): Int
    fun getLogRetentionDaysFlow(): StateFlow<Int>

    // Content sync policy
    suspend fun getContentSyncMode(): ContentSyncMode
    suspend fun saveContentSyncMode(mode: ContentSyncMode)
    suspend fun getContentSyncConstraints(): ContentSyncConstraints
    suspend fun saveWifiOnly(enabled: Boolean)
    suspend fun saveAllowBatterySaver(enabled: Boolean)
    suspend fun getDateRangeParams(): DateRangeParams?
    suspend fun saveDateRangeParams(params: DateRangeParams)
}
