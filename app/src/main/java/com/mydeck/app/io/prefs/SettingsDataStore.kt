package com.mydeck.app.io.prefs

import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.model.CachedServerInfo
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangeParams
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

interface SettingsDataStore {
    val tokenFlow: StateFlow<String?>
    val usernameFlow: StateFlow<String?>
    val urlFlow: StateFlow<String?>
    val initialSyncPerformedFlow: StateFlow<Boolean>
    val themeFlow: StateFlow<String?>
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
    suspend fun saveCachedAnnotationSnapshot(bookmarkId: String, snapshot: String)
    suspend fun getCachedAnnotationSnapshot(bookmarkId: String): String?
    suspend fun clearCachedAnnotationSnapshot(bookmarkId: String)
    suspend fun setInitialSyncPerformed(performed: Boolean)
    suspend fun isInitialSyncPerformed(): Boolean
    suspend fun clearCredentials()
    suspend fun saveCredentials(url: String, username: String, token: String)
    suspend fun setAutoSyncEnabled(isEnabled: Boolean)
    suspend fun isAutoSyncEnabled(): Boolean
    suspend fun saveAutoSyncTimeframe(autoSyncTimeframe: AutoSyncTimeframe)
    suspend fun getAutoSyncTimeframe(): AutoSyncTimeframe
    suspend fun saveTheme(theme: Theme)
    suspend fun getTheme(): Theme
    suspend fun saveLightAppearance(appearance: LightAppearance)
    suspend fun getLightAppearance(): LightAppearance
    suspend fun saveDarkAppearance(appearance: DarkAppearance)
    suspend fun getDarkAppearance(): DarkAppearance
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

    // Typography settings
    val typographySettingsFlow: StateFlow<TypographySettings>
    suspend fun saveTypographySettings(settings: TypographySettings)

    // Sepia preference retained only for migration from older builds.
    @Deprecated("Migration-only. Use lightAppearanceFlow instead.")
    val sepiaEnabledFlow: StateFlow<Boolean>
    @Deprecated("Migration-only. Use saveLightAppearance instead.")
    suspend fun saveSepiaEnabled(enabled: Boolean)
    @Deprecated("Migration-only. Use getLightAppearance instead.")
    suspend fun isSepiaEnabled(): Boolean

    // Reader appearance preferences
    val lightAppearanceFlow: StateFlow<LightAppearance>
    val darkAppearanceFlow: StateFlow<DarkAppearance>
    val bookmarkShareFormatFlow: StateFlow<BookmarkShareFormat>
    suspend fun saveBookmarkShareFormat(format: BookmarkShareFormat)
    suspend fun getBookmarkShareFormat(): BookmarkShareFormat

    // Keep screen on while reading preference
    val keepScreenOnWhileReadingFlow: StateFlow<Boolean>
    suspend fun saveKeepScreenOnWhileReading(enabled: Boolean)
    suspend fun isKeepScreenOnWhileReading(): Boolean

    // Fullscreen while reading preference
    val fullscreenWhileReadingFlow: StateFlow<Boolean>
    suspend fun saveFullscreenWhileReading(enabled: Boolean)
    suspend fun isFullscreenWhileReading(): Boolean

    // Server info caching
    suspend fun saveServerInfo(info: CachedServerInfo)
    suspend fun getServerInfo(): CachedServerInfo?
    suspend fun clearServerInfo()
}
