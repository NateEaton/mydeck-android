package com.mydeck.app.io.prefs

import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.model.CachedServerInfo
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.HighlightsSyncMetadata
import com.mydeck.app.domain.model.LabelSearchMatching
import com.mydeck.app.domain.model.LabelSearchSort
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.OpenWebPagesIn
import com.mydeck.app.domain.model.SwipeAction
import com.mydeck.app.domain.model.SwipeConfig
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.DateRangeParams
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflinePolicy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration

interface SettingsDataStore {
    val tokenFlow: StateFlow<String?>
    val usernameFlow: StateFlow<String?>
    val urlFlow: StateFlow<String?>
    val initialSyncPerformedFlow: StateFlow<Boolean>
    val themeFlow: StateFlow<String?>
    val lastSyncTimestampFlow: StateFlow<String?>
    val lastContentSyncTimestampFlow: StateFlow<String?>
    fun saveToken(token: String)
    /**
     * Synchronous, read-after-write-consistent token read for the auth interceptor.
     * Unlike collecting [tokenFlow], this reflects a [saveToken] write immediately, so a
     * request fired right after login (e.g. GET /profile in completeLogin) carries the new token.
     */
    fun getTokenSync(): String?
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
    suspend fun saveHighlightsSyncMetadata(metadata: HighlightsSyncMetadata)
    suspend fun getHighlightsSyncMetadata(): HighlightsSyncMetadata
    suspend fun clearHighlightsSyncMetadata()
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

    // Offline reading policy
    val offlineReadingEnabledFlow: StateFlow<Boolean>
    suspend fun getContentSyncConstraints(): ContentSyncConstraints
    suspend fun saveWifiOnly(enabled: Boolean)
    suspend fun saveAllowBatterySaver(enabled: Boolean)
    suspend fun getDateRangeParams(): DateRangeParams?
    suspend fun saveDateRangeParams(params: DateRangeParams)
    suspend fun isOfflineReadingEnabled(): Boolean
    suspend fun saveOfflineReadingEnabled(enabled: Boolean)
    suspend fun getOfflinePolicy(): OfflinePolicy
    suspend fun saveOfflinePolicy(policy: OfflinePolicy)
    suspend fun getOfflinePolicyStorageLimit(): Long
    suspend fun saveOfflinePolicyStorageLimit(limitBytes: Long)
    suspend fun getOfflinePolicyNewestN(): Int
    suspend fun saveOfflinePolicyNewestN(newestN: Int)
    suspend fun getOfflinePolicyDateRangeWindow(): Duration
    suspend fun saveOfflinePolicyDateRangeWindow(window: Duration)
    suspend fun getOfflineMaxStorageCap(): Long
    suspend fun saveOfflineMaxStorageCap(limitBytes: Long)
    suspend fun getOfflineContentScope(): OfflineContentScope
    suspend fun saveOfflineContentScope(scope: OfflineContentScope)
    suspend fun getOfflineImageStorageLimit(): com.mydeck.app.domain.sync.OfflineImageStorageLimit
    suspend fun saveOfflineImageStorageLimit(limit: com.mydeck.app.domain.sync.OfflineImageStorageLimit)

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

    // "What's New" on-update sheet: last app version whose notes were shown (or
    // silently recorded on fresh install), and whether the first-launch guide
    // nudge has already been shown.
    suspend fun getLastSeenWhatsNewVersion(): String?
    suspend fun saveLastSeenWhatsNewVersion(version: String)
    suspend fun isWelcomeGuidePromptShown(): Boolean
    suspend fun saveWelcomeGuidePromptShown(shown: Boolean)

    // Bookmark list display preferences
    val showCompactFaviconsFlow: StateFlow<Boolean>
    suspend fun saveShowCompactFavicons(enabled: Boolean)
    suspend fun isShowCompactFavicons(): Boolean

    val showAddBookmarkFabFlow: StateFlow<Boolean>
    suspend fun saveShowAddBookmarkFab(enabled: Boolean)
    suspend fun isShowAddBookmarkFab(): Boolean

    // Where a bookmark's original web page opens (Original View vs external browser)
    val openWebPagesInFlow: StateFlow<OpenWebPagesIn>
    suspend fun saveOpenWebPagesIn(value: OpenWebPagesIn)
    suspend fun getOpenWebPagesIn(): OpenWebPagesIn

    // Label search ranking preferences
    val labelSearchMatchingFlow: StateFlow<LabelSearchMatching>
    suspend fun saveLabelSearchMatching(matching: LabelSearchMatching)
    val labelSearchSortFlow: StateFlow<LabelSearchSort>
    suspend fun saveLabelSearchSort(sort: LabelSearchSort)

    // Swipe action preferences
    val swipeConfigFlow: StateFlow<SwipeConfig>
    suspend fun saveSwipeEnabled(enabled: Boolean)
    suspend fun saveSwipeLeftAction(action: SwipeAction)
    suspend fun saveSwipeRightAction(action: SwipeAction)

    // Server info caching
    suspend fun saveServerInfo(info: CachedServerInfo)
    suspend fun getServerInfo(): CachedServerInfo?
    suspend fun clearServerInfo()
}
