package com.mydeck.app.io.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.CachedServerInfo
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.DateRangeParams
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflineImageStorageLimit
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class SettingsDataStoreImpl @Inject constructor(@ApplicationContext private val context: Context) :
    SettingsDataStore {

    private val encryptedSharedPreferences = EncryptionHelper.getEncryptedSharedPreferences(context)
    private val userPreferences = context.getSharedPreferences(USER_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    private val preferenceChangeListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_URL = stringPreferencesKey("url")
    private val KEY_LAST_BOOKMARK_TIMESTAMP = stringPreferencesKey("lastBookmarkTimestamp")
    private val KEY_LAST_SYNC_TIMESTAMP = stringPreferencesKey("lastSyncTimestamp")
    private val KEY_LAST_CONTENT_SYNC_TIMESTAMP = stringPreferencesKey("lastContentSyncTimestamp")
    private val KEY_LAST_FULL_SYNC_TIMESTAMP = stringPreferencesKey("last_full_sync_timestamp")
    private val KEY_INITIAL_SYNC_PERFORMED = "initial_sync_performed"
    private val KEY_AUTOSYNC_ENABLED = booleanPreferencesKey("autosync_enabled")
    private val KEY_AUTOSYNC_TIMEFRAME = stringPreferencesKey("autosync_timeframe")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_SYNC_ON_APP_OPEN = booleanPreferencesKey("sync_on_app_open")
    private val KEY_SYNC_NOTIFICATIONS_ENABLED = booleanPreferencesKey("sync_notifications_enabled")
    private val KEY_LAYOUT_MODE = stringPreferencesKey("layout_mode")
    private val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
    private val KEY_LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
    private val KEY_CONTENT_SYNC_MODE = stringPreferencesKey("content_sync_mode")
    private val KEY_WIFI_ONLY = booleanPreferencesKey("content_sync_wifi_only")
    private val KEY_ALLOW_BATTERY_SAVER = booleanPreferencesKey("content_sync_allow_battery_saver")
    private val KEY_DATE_RANGE_FROM = stringPreferencesKey("date_range_from")
    private val KEY_DATE_RANGE_TO = stringPreferencesKey("date_range_to")
    private val KEY_SEPIA_ENABLED = booleanPreferencesKey("sepia_enabled")
    private val KEY_LIGHT_APPEARANCE = stringPreferencesKey("light_appearance")
    private val KEY_DARK_APPEARANCE = stringPreferencesKey("dark_appearance")
    private val KEY_BOOKMARK_SHARE_FORMAT = stringPreferencesKey("bookmark_share_format")
    private val KEY_KEEP_SCREEN_ON_READING = booleanPreferencesKey("keep_screen_on_reading")
    private val KEY_FULLSCREEN_WHILE_READING = booleanPreferencesKey("fullscreen_while_reading")
    private val KEY_INCLUDE_ARCHIVED_CONTENT = booleanPreferencesKey("include_archived_content_in_sync")
    private val KEY_OFFLINE_READING_ENABLED = booleanPreferencesKey("offline_reading_enabled")
    private val KEY_OFFLINE_POLICY = stringPreferencesKey("offline_policy")
    private val KEY_OFFLINE_POLICY_STORAGE_LIMIT = longPreferencesKey("offline_policy_storage_limit")
    private val KEY_OFFLINE_POLICY_NEWEST_N = intPreferencesKey("offline_policy_newest_n")
    private val KEY_OFFLINE_POLICY_DATE_RANGE_WINDOW_MS =
        longPreferencesKey("offline_policy_date_range_window_ms")
    private val KEY_OFFLINE_MAX_STORAGE_CAP = longPreferencesKey("offline_max_storage_cap")
    private val KEY_OFFLINE_CONTENT_SCOPE = stringPreferencesKey("offline_content_scope")
    private val KEY_INCLUDE_ARCHIVED_IN_OFFLINE_SCOPE =
        booleanPreferencesKey("include_archived_in_offline_scope")
    private val KEY_OFFLINE_IMAGE_STORAGE_LIMIT = stringPreferencesKey("offline_image_storage_limit")

    private val KEY_TYPO_FONT_SIZE = intPreferencesKey("typography_font_size_percent")
    private val KEY_TYPO_FONT_FAMILY = stringPreferencesKey("typography_font_family")
    private val KEY_TYPO_LINE_SPACING = stringPreferencesKey("typography_line_spacing")
    private val KEY_TYPO_LINE_SPACING_PERCENT = intPreferencesKey("typography_line_spacing_percent")
    private val KEY_TYPO_TEXT_WIDTH = stringPreferencesKey("typography_text_width")
    private val KEY_TYPO_JUSTIFIED = booleanPreferencesKey("typography_justified")
    private val KEY_TYPO_HYPHENATION = booleanPreferencesKey("typography_hyphenation")

    private val KEY_SERVER_INFO_CANONICAL = stringPreferencesKey("server_info_canonical")
    private val KEY_SERVER_INFO_RELEASE = stringPreferencesKey("server_info_release")
    private val KEY_SERVER_INFO_BUILD = stringPreferencesKey("server_info_build")
    private val KEY_SERVER_INFO_FEATURES = stringPreferencesKey("server_info_features")

    init {
        migrateLegacySepiaSetting(encryptedSharedPreferences)
        migrateNonSensitivePreferencesIfNeeded()
        migrateLegacySepiaSetting(userPreferences)
        migrateAppearancePreferencesIfNeeded()
    }

    override fun saveToken(token: String) {
        Timber.d("saveToken")
        encryptedSharedPreferences.edit {
            putString(KEY_TOKEN.name, token)
        }
    }

    override fun saveUrl(url: String) {
        Timber.d("saveUrl")
        encryptedSharedPreferences.edit {
            putString(KEY_URL.name, url)
        }
    }

    override suspend fun saveLastBookmarkTimestamp(timestamp: Instant) {
        encryptedSharedPreferences.edit {
            putString(KEY_LAST_BOOKMARK_TIMESTAMP.name, timestamp.toString())
        }
    }

    override suspend fun getLastBookmarkTimestamp(): Instant? {
        return encryptedSharedPreferences.getString(KEY_LAST_BOOKMARK_TIMESTAMP.name, null)?.let {
            Instant.parse(it)
        }
    }

    override suspend fun saveLastSyncTimestamp(timestamp: Instant) {
        encryptedSharedPreferences.edit {
            putString(KEY_LAST_SYNC_TIMESTAMP.name, timestamp.toString())
        }
    }

    override suspend fun getLastSyncTimestamp(): Instant? {
        return encryptedSharedPreferences.getString(KEY_LAST_SYNC_TIMESTAMP.name, null)?.let {
            Instant.parse(it)
        }
    }

    override suspend fun saveLastContentSyncTimestamp(timestamp: Instant) {
        encryptedSharedPreferences.edit {
            putString(KEY_LAST_CONTENT_SYNC_TIMESTAMP.name, timestamp.toString())
        }
    }

    override suspend fun getLastContentSyncTimestamp(): Instant? {
        return encryptedSharedPreferences.getString(KEY_LAST_CONTENT_SYNC_TIMESTAMP.name, null)?.let {
            Instant.parse(it)
        }
    }

    override suspend fun saveLastFullSyncTimestamp(timestamp: Instant) {
        encryptedSharedPreferences.edit {
            putString(KEY_LAST_FULL_SYNC_TIMESTAMP.name, timestamp.toString())
        }
    }

    override suspend fun getLastFullSyncTimestamp(): Instant? {
        return encryptedSharedPreferences.getString(KEY_LAST_FULL_SYNC_TIMESTAMP.name, null)?.let {
            Instant.parse(it)
        }
    }

    override suspend fun saveCachedAnnotationSnapshot(bookmarkId: String, snapshot: String) {
        encryptedSharedPreferences.edit {
            putString(annotationSnapshotKey(bookmarkId), snapshot)
        }
    }

    override suspend fun getCachedAnnotationSnapshot(bookmarkId: String): String? {
        return encryptedSharedPreferences.getString(annotationSnapshotKey(bookmarkId), null)
    }

    override suspend fun clearCachedAnnotationSnapshot(bookmarkId: String) {
        encryptedSharedPreferences.edit {
            remove(annotationSnapshotKey(bookmarkId))
        }
    }

    override suspend fun setInitialSyncPerformed(performed: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_INITIAL_SYNC_PERFORMED, performed)
        }
    }

    override suspend fun isInitialSyncPerformed(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_INITIAL_SYNC_PERFORMED, false)
    }

    override suspend fun isAutoSyncEnabled(): Boolean {
        return userPreferences.getBoolean(KEY_AUTOSYNC_ENABLED.name, false)
    }

    override suspend fun setAutoSyncEnabled(isEnabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_AUTOSYNC_ENABLED.name, isEnabled)
        }
    }

    override suspend fun getAutoSyncTimeframe(): AutoSyncTimeframe {
        return userPreferences.getString(KEY_AUTOSYNC_TIMEFRAME.name, AutoSyncTimeframe.MANUAL.name)?.let {
            AutoSyncTimeframe.valueOf(it)
        } ?: AutoSyncTimeframe.MANUAL
    }

    override suspend fun saveAutoSyncTimeframe(autoSyncTimeframe: AutoSyncTimeframe) {
        Timber.d("saveAutoSyncTimeframe")
        userPreferences.edit {
            putString(KEY_AUTOSYNC_TIMEFRAME.name, autoSyncTimeframe.name)
        }
    }

    override suspend fun getTheme(): Theme {
        return userPreferences.getString(KEY_THEME.name, Theme.SYSTEM.name)?.let {
            try {
                Theme.valueOf(it)
            } catch (_: IllegalArgumentException) {
                Theme.LIGHT
            }
        } ?: Theme.SYSTEM
    }

    override suspend fun saveTheme(theme: Theme) {
        userPreferences.edit {
            putString(KEY_THEME.name, theme.name)
        }
    }

    override suspend fun saveLightAppearance(appearance: LightAppearance) {
        userPreferences.edit {
            putString(KEY_LIGHT_APPEARANCE.name, appearance.name)
        }
    }

    override suspend fun getLightAppearance(): LightAppearance {
        return userPreferences.getString(
            KEY_LIGHT_APPEARANCE.name,
            LightAppearance.PAPER.name
        )?.let {
            try {
                LightAppearance.valueOf(it)
            } catch (_: IllegalArgumentException) {
                LightAppearance.PAPER
            }
        } ?: LightAppearance.PAPER
    }

    override suspend fun saveDarkAppearance(appearance: DarkAppearance) {
        userPreferences.edit {
            putString(KEY_DARK_APPEARANCE.name, appearance.name)
        }
    }

    override suspend fun getDarkAppearance(): DarkAppearance {
        return userPreferences.getString(
            KEY_DARK_APPEARANCE.name,
            DarkAppearance.DARK.name
        )?.let {
            try {
                DarkAppearance.valueOf(it)
            } catch (_: IllegalArgumentException) {
                DarkAppearance.DARK
            }
        } ?: DarkAppearance.DARK
    }

    override suspend fun saveBookmarkShareFormat(format: BookmarkShareFormat) {
        userPreferences.edit {
            putString(KEY_BOOKMARK_SHARE_FORMAT.name, format.name)
        }
    }

    override suspend fun getBookmarkShareFormat(): BookmarkShareFormat {
        return userPreferences.getString(
            KEY_BOOKMARK_SHARE_FORMAT.name,
            BookmarkShareFormat.URL_ONLY.name
        )?.let {
            try {
                BookmarkShareFormat.valueOf(it)
            } catch (_: IllegalArgumentException) {
                BookmarkShareFormat.URL_ONLY
            }
        } ?: BookmarkShareFormat.URL_ONLY
    }

    @Deprecated("Migration-only. Use saveLightAppearance instead.")
    override suspend fun saveSepiaEnabled(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_SEPIA_ENABLED.name, enabled)
        }
    }

    @Deprecated("Migration-only. Use getLightAppearance instead.")
    override suspend fun isSepiaEnabled(): Boolean {
        return userPreferences.getBoolean(KEY_SEPIA_ENABLED.name, false)
    }

    override suspend fun saveKeepScreenOnWhileReading(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_KEEP_SCREEN_ON_READING.name, enabled)
        }
    }

    override suspend fun isKeepScreenOnWhileReading(): Boolean {
        return userPreferences.getBoolean(KEY_KEEP_SCREEN_ON_READING.name, true)
    }

    override suspend fun saveFullscreenWhileReading(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_FULLSCREEN_WHILE_READING.name, enabled)
        }
    }

    override suspend fun isFullscreenWhileReading(): Boolean {
        return userPreferences.getBoolean(KEY_FULLSCREEN_WHILE_READING.name, false)
    }

    override suspend fun setSyncOnAppOpenEnabled(isEnabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_SYNC_ON_APP_OPEN.name, isEnabled)
        }
    }

    override suspend fun isSyncOnAppOpenEnabled(): Boolean {
        return userPreferences.getBoolean(KEY_SYNC_ON_APP_OPEN.name, true)
    }

    override suspend fun setSyncNotificationsEnabled(isEnabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_SYNC_NOTIFICATIONS_ENABLED.name, isEnabled)
        }
    }

    override suspend fun isSyncNotificationsEnabled(): Boolean {
        return userPreferences.getBoolean(KEY_SYNC_NOTIFICATIONS_ENABLED.name, true)
    }

    override val tokenFlow = getStringFlow(encryptedSharedPreferences, KEY_TOKEN.name, null)
    override val usernameFlow = getStringFlow(encryptedSharedPreferences, KEY_USERNAME.name, null)
    override val urlFlow = getStringFlow(encryptedSharedPreferences, KEY_URL.name, null)
    override val initialSyncPerformedFlow =
        getBooleanFlow(encryptedSharedPreferences, KEY_INITIAL_SYNC_PERFORMED, false)
    override val themeFlow = getStringFlow(userPreferences, KEY_THEME.name, Theme.SYSTEM.name)
    override val lastSyncTimestampFlow =
        getStringFlow(encryptedSharedPreferences, KEY_LAST_SYNC_TIMESTAMP.name, null)
    override val lastContentSyncTimestampFlow =
        getStringFlow(encryptedSharedPreferences, KEY_LAST_CONTENT_SYNC_TIMESTAMP.name, null)
    @Deprecated("Migration-only. Use lightAppearanceFlow instead.")
    override val sepiaEnabledFlow = getBooleanFlow(userPreferences, KEY_SEPIA_ENABLED.name, false)
    override val lightAppearanceFlow = getEnumFlow(
        preferences = userPreferences,
        key = KEY_LIGHT_APPEARANCE.name,
        defaultValue = LightAppearance.PAPER,
        parse = { value -> LightAppearance.valueOf(value) }
    )
    override val darkAppearanceFlow = getEnumFlow(
        preferences = userPreferences,
        key = KEY_DARK_APPEARANCE.name,
        defaultValue = DarkAppearance.DARK,
        parse = { value -> DarkAppearance.valueOf(value) }
    )
    override val bookmarkShareFormatFlow = getEnumFlow(
        preferences = userPreferences,
        key = KEY_BOOKMARK_SHARE_FORMAT.name,
        defaultValue = BookmarkShareFormat.URL_ONLY,
        parse = { value -> BookmarkShareFormat.valueOf(value) }
    )
    override val keepScreenOnWhileReadingFlow =
        getBooleanFlow(userPreferences, KEY_KEEP_SCREEN_ON_READING.name, true)
    override val fullscreenWhileReadingFlow =
        getBooleanFlow(userPreferences, KEY_FULLSCREEN_WHILE_READING.name, false)

    override suspend fun clearCredentials() {
        Timber.d("clearCredentials")
        encryptedSharedPreferences.edit(commit = true) {
            remove(KEY_USERNAME.name)
            remove(KEY_TOKEN.name)
            remove(KEY_URL.name)
            remove(KEY_INITIAL_SYNC_PERFORMED)
        }
        clearServerInfo()
    }

    override suspend fun saveCredentials(
        url: String,
        username: String,
        token: String
    ) {
        Timber.d("saveCredentials")
        encryptedSharedPreferences.edit {
            putString(KEY_URL.name, url)
            putString(KEY_USERNAME.name, username)
            putString(KEY_TOKEN.name, token)
        }
    }

    private fun getStringFlow(
        preferences: SharedPreferences,
        key: String,
        defaultValue: String? = null
    ): StateFlow<String?> = preferenceFlow(preferences, key) {
        preferences.getString(key, defaultValue)
    }

    private fun getIntFlow(
        preferences: SharedPreferences,
        key: String,
        defaultValue: Int
    ): StateFlow<Int> = preferenceFlow(preferences, key) {
        preferences.getInt(key, defaultValue)
    }

    private fun getBooleanFlow(
        preferences: SharedPreferences,
        key: String,
        defaultValue: Boolean
    ): StateFlow<Boolean> = preferenceFlow(preferences, key) {
        preferences.getBoolean(key, defaultValue)
    }

    private fun <T> getEnumFlow(
        preferences: SharedPreferences,
        key: String,
        defaultValue: T,
        parse: (String) -> T
    ): StateFlow<T> = preferenceFlow(preferences, key) {
        preferences.getString(key, null)?.let { value ->
            try {
                parse(value)
            } catch (_: IllegalArgumentException) {
                defaultValue
            }
        } ?: defaultValue
    }

    private fun <T> preferenceFlow(
        preferences: SharedPreferences,
        key: String,
        getValue: () -> T
    ): StateFlow<T> {
        val state = MutableStateFlow(getValue())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                Timber.d("pref changed key=$key")
                state.value = getValue()
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        preferenceChangeListeners += listener
        return state.asStateFlow()
    }

    override suspend fun saveLayoutMode(layoutMode: String) {
        userPreferences.edit {
            putString(KEY_LAYOUT_MODE.name, layoutMode)
        }
    }

    override suspend fun getLayoutMode(): String? {
        return userPreferences.getString(KEY_LAYOUT_MODE.name, null)
    }

    override suspend fun saveSortOption(sortOption: String) {
        userPreferences.edit {
            putString(KEY_SORT_OPTION.name, sortOption)
        }
    }

    override suspend fun getSortOption(): String? {
        return userPreferences.getString(KEY_SORT_OPTION.name, null)
    }

    override suspend fun saveLogRetentionDays(days: Int) {
        userPreferences.edit {
            putInt(KEY_LOG_RETENTION_DAYS.name, days)
        }
    }

    override suspend fun getLogRetentionDays(): Int {
        return userPreferences.getInt(KEY_LOG_RETENTION_DAYS.name, defaultLogRetentionDays())
    }

    override fun getLogRetentionDaysFlow(): StateFlow<Int> =
        getIntFlow(userPreferences, KEY_LOG_RETENTION_DAYS.name, defaultLogRetentionDays())

    override suspend fun getContentSyncConstraints(): ContentSyncConstraints {
        return ContentSyncConstraints(
            wifiOnly = userPreferences.getBoolean(KEY_WIFI_ONLY.name, true),
            allowOnBatterySaver = userPreferences.getBoolean(KEY_ALLOW_BATTERY_SAVER.name, false)
        )
    }

    override suspend fun saveWifiOnly(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_WIFI_ONLY.name, enabled)
        }
    }

    override suspend fun saveAllowBatterySaver(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_ALLOW_BATTERY_SAVER.name, enabled)
        }
    }

    override suspend fun getDateRangeParams(): DateRangeParams? {
        val from = userPreferences.getString(KEY_DATE_RANGE_FROM.name, null)
        val to = userPreferences.getString(KEY_DATE_RANGE_TO.name, null)
        return if (from != null && to != null) {
            try {
                DateRangeParams(from = LocalDate.parse(from), to = LocalDate.parse(to))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    override suspend fun saveDateRangeParams(params: DateRangeParams) {
        userPreferences.edit {
            putString(KEY_DATE_RANGE_FROM.name, params.from.toString())
            putString(KEY_DATE_RANGE_TO.name, params.to.toString())
        }
    }

    override suspend fun isOfflineReadingEnabled(): Boolean {
        if (userPreferences.contains(KEY_OFFLINE_READING_ENABLED.name)) {
            return userPreferences.getBoolean(KEY_OFFLINE_READING_ENABLED.name, false)
        }
        return false
    }

    override suspend fun saveOfflineReadingEnabled(enabled: Boolean) {
        userPreferences.edit {
            putBoolean(KEY_OFFLINE_READING_ENABLED.name, enabled)
            putString(
                KEY_CONTENT_SYNC_MODE.name,
                if (enabled) LEGACY_CONTENT_SYNC_MODE_AUTOMATIC else LEGACY_CONTENT_SYNC_MODE_MANUAL
            )
        }
    }

    override suspend fun getOfflinePolicy(): OfflinePolicy {
        return userPreferences.getString(KEY_OFFLINE_POLICY.name, OfflinePolicy.STORAGE_LIMIT.name)?.let {
            try {
                OfflinePolicy.valueOf(it)
            } catch (_: IllegalArgumentException) {
                OfflinePolicy.STORAGE_LIMIT
            }
        } ?: OfflinePolicy.STORAGE_LIMIT
    }

    override suspend fun saveOfflinePolicy(policy: OfflinePolicy) {
        userPreferences.edit {
            putString(KEY_OFFLINE_POLICY.name, policy.name)
        }
    }

    override suspend fun getOfflinePolicyStorageLimit(): Long {
        if (userPreferences.contains(KEY_OFFLINE_POLICY_STORAGE_LIMIT.name)) {
            return userPreferences.getLong(
                KEY_OFFLINE_POLICY_STORAGE_LIMIT.name,
                OfflinePolicyDefaults.STORAGE_LIMIT_BYTES
            )
        }

        return legacyOfflineImageStorageLimit()?.bytes ?: OfflinePolicyDefaults.STORAGE_LIMIT_BYTES
    }

    override suspend fun saveOfflinePolicyStorageLimit(limitBytes: Long) {
        userPreferences.edit {
            putLong(KEY_OFFLINE_POLICY_STORAGE_LIMIT.name, limitBytes)
            OfflineImageStorageLimit.entries.firstOrNull { it.bytes == limitBytes }?.let { matchedLimit ->
                putString(KEY_OFFLINE_IMAGE_STORAGE_LIMIT.name, matchedLimit.name)
            } ?: remove(KEY_OFFLINE_IMAGE_STORAGE_LIMIT.name)
        }
    }

    override suspend fun getOfflinePolicyNewestN(): Int {
        return userPreferences.getInt(
            KEY_OFFLINE_POLICY_NEWEST_N.name,
            OfflinePolicyDefaults.NEWEST_N
        ).coerceAtLeast(1)
    }

    override suspend fun saveOfflinePolicyNewestN(newestN: Int) {
        userPreferences.edit {
            putInt(KEY_OFFLINE_POLICY_NEWEST_N.name, newestN.coerceAtLeast(1))
        }
    }

    override suspend fun getOfflinePolicyDateRangeWindow(): Duration {
        return if (userPreferences.contains(KEY_OFFLINE_POLICY_DATE_RANGE_WINDOW_MS.name)) {
            userPreferences.getLong(
                KEY_OFFLINE_POLICY_DATE_RANGE_WINDOW_MS.name,
                OfflinePolicyDefaults.DATE_RANGE_WINDOW.inWholeMilliseconds
            ).milliseconds
        } else {
            OfflinePolicyDefaults.DATE_RANGE_WINDOW
        }
    }

    override suspend fun saveOfflinePolicyDateRangeWindow(window: Duration) {
        userPreferences.edit {
            putLong(
                KEY_OFFLINE_POLICY_DATE_RANGE_WINDOW_MS.name,
                window.inWholeMilliseconds
            )
        }
    }

    override suspend fun getOfflineMaxStorageCap(): Long {
        return userPreferences.getLong(
            KEY_OFFLINE_MAX_STORAGE_CAP.name,
            OfflinePolicyDefaults.MAX_STORAGE_CAP_BYTES
        )
    }

    override suspend fun saveOfflineMaxStorageCap(limitBytes: Long) {
        userPreferences.edit {
            putLong(KEY_OFFLINE_MAX_STORAGE_CAP.name, limitBytes)
        }
    }

    override suspend fun getOfflineContentScope(): OfflineContentScope {
        val stored = userPreferences.getString(KEY_OFFLINE_CONTENT_SCOPE.name, null)
        if (stored != null) {
            return try {
                OfflineContentScope.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                OfflineContentScope.MY_LIST
            }
        }

        val includeArchived = when {
            userPreferences.contains(KEY_INCLUDE_ARCHIVED_IN_OFFLINE_SCOPE.name) ->
                userPreferences.getBoolean(KEY_INCLUDE_ARCHIVED_IN_OFFLINE_SCOPE.name, false)
            userPreferences.contains(KEY_INCLUDE_ARCHIVED_CONTENT.name) ->
                userPreferences.getBoolean(KEY_INCLUDE_ARCHIVED_CONTENT.name, false)
            else -> false
        }

        return if (includeArchived) {
            OfflineContentScope.MY_LIST_AND_ARCHIVED
        } else {
            OfflineContentScope.MY_LIST
        }
    }

    override suspend fun saveOfflineContentScope(scope: OfflineContentScope) {
        userPreferences.edit {
            putString(KEY_OFFLINE_CONTENT_SCOPE.name, scope.name)
            putBoolean(KEY_INCLUDE_ARCHIVED_IN_OFFLINE_SCOPE.name, scope.includesArchived)
            putBoolean(KEY_INCLUDE_ARCHIVED_CONTENT.name, scope.includesArchived)
        }
    }

    override suspend fun getOfflineImageStorageLimit(): OfflineImageStorageLimit {
        legacyOfflineImageStorageLimit()?.let { return it }

        val bytes = getOfflinePolicyStorageLimit()
        return OfflineImageStorageLimit.entries.firstOrNull { it.bytes == bytes }
            ?: OfflineImageStorageLimit.MB_500
    }

    override suspend fun saveOfflineImageStorageLimit(limit: OfflineImageStorageLimit) {
        userPreferences.edit {
            putString(KEY_OFFLINE_IMAGE_STORAGE_LIMIT.name, limit.name)
            putLong(KEY_OFFLINE_POLICY_STORAGE_LIMIT.name, limit.bytes)
        }
    }

    private fun defaultLogRetentionDays(): Int {
        return if (isDebugBuild()) 7 else 30
    }

    private fun isDebugBuild(): Boolean {
        return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE.contains("debug", ignoreCase = true)
    }

    private fun legacyContentSyncMode(): String? {
        return userPreferences.getString(KEY_CONTENT_SYNC_MODE.name, null)
    }

    private fun legacyOfflineImageStorageLimit(): OfflineImageStorageLimit? {
        val stored = userPreferences.getString(KEY_OFFLINE_IMAGE_STORAGE_LIMIT.name, null) ?: return null
        return try {
            OfflineImageStorageLimit.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private val _typographySettingsFlow = MutableStateFlow(readTypographySettings())

    override val typographySettingsFlow: StateFlow<TypographySettings> =
        _typographySettingsFlow.asStateFlow()

    private fun readTypographySettings(): TypographySettings {
        val fontFamilyStr = userPreferences.getString(
            KEY_TYPO_FONT_FAMILY.name,
            ReaderFontFamily.SYSTEM_DEFAULT.name
        ) ?: ReaderFontFamily.SYSTEM_DEFAULT.name
        val textWidthStr = userPreferences.getString(
            KEY_TYPO_TEXT_WIDTH.name,
            TextWidth.MEDIUM.name
        ) ?: TextWidth.MEDIUM.name
        val clampedFontSizePercent = TypographySettings.clampFontSizePercent(
            userPreferences.getInt(KEY_TYPO_FONT_SIZE.name, 100)
        )
        val clampedLineSpacingPercent = readLineSpacingPercent()

        return TypographySettings(
            fontSizePercent = clampedFontSizePercent,
            fontFamily = try {
                ReaderFontFamily.valueOf(fontFamilyStr)
            } catch (_: IllegalArgumentException) {
                ReaderFontFamily.SYSTEM_DEFAULT
            },
            lineSpacingPercent = clampedLineSpacingPercent,
            textWidth = try {
                TextWidth.valueOf(textWidthStr)
            } catch (_: IllegalArgumentException) {
                TextWidth.MEDIUM
            },
            justified = userPreferences.getBoolean(KEY_TYPO_JUSTIFIED.name, false),
            hyphenation = userPreferences.getBoolean(KEY_TYPO_HYPHENATION.name, false)
        )
    }

    override suspend fun saveTypographySettings(settings: TypographySettings) {
        val sanitizedSettings = settings.copy(
            fontSizePercent = TypographySettings.clampFontSizePercent(settings.fontSizePercent),
            lineSpacingPercent = TypographySettings.clampLineSpacingPercent(settings.lineSpacingPercent)
        )
        userPreferences.edit {
            putInt(KEY_TYPO_FONT_SIZE.name, sanitizedSettings.fontSizePercent)
            putString(KEY_TYPO_FONT_FAMILY.name, sanitizedSettings.fontFamily.name)
            putInt(KEY_TYPO_LINE_SPACING_PERCENT.name, sanitizedSettings.lineSpacingPercent)
            remove(KEY_TYPO_LINE_SPACING.name)
            putString(KEY_TYPO_TEXT_WIDTH.name, sanitizedSettings.textWidth.name)
            putBoolean(KEY_TYPO_JUSTIFIED.name, sanitizedSettings.justified)
            putBoolean(KEY_TYPO_HYPHENATION.name, sanitizedSettings.hyphenation)
        }
        _typographySettingsFlow.value = sanitizedSettings
    }

    private fun readLineSpacingPercent(): Int {
        if (userPreferences.contains(KEY_TYPO_LINE_SPACING_PERCENT.name)) {
            return TypographySettings.clampLineSpacingPercent(
                userPreferences.getInt(
                    KEY_TYPO_LINE_SPACING_PERCENT.name,
                    TypographySettings.DEFAULT_LINE_SPACING_PERCENT
                )
            )
        }

        val legacyLineSpacing = userPreferences.getString(KEY_TYPO_LINE_SPACING.name, null)
        return when (legacyLineSpacing) {
            "LOOSE" -> TypographySettings.MAX_LINE_SPACING_PERCENT
            "TIGHT", null -> TypographySettings.DEFAULT_LINE_SPACING_PERCENT
            else -> TypographySettings.DEFAULT_LINE_SPACING_PERCENT
        }
    }

    override suspend fun saveServerInfo(info: CachedServerInfo) {
        encryptedSharedPreferences.edit {
            putString(KEY_SERVER_INFO_CANONICAL.name, info.canonical)
            putString(KEY_SERVER_INFO_RELEASE.name, info.release)
            putString(KEY_SERVER_INFO_BUILD.name, info.build)
            putString(KEY_SERVER_INFO_FEATURES.name, info.features.joinToString(","))
        }
    }

    override suspend fun getServerInfo(): CachedServerInfo? {
        val canonical = encryptedSharedPreferences.getString(KEY_SERVER_INFO_CANONICAL.name, null)
            ?: return null

        val release = encryptedSharedPreferences.getString(KEY_SERVER_INFO_RELEASE.name, "")!!
        val build = encryptedSharedPreferences.getString(KEY_SERVER_INFO_BUILD.name, "")!!
        val featuresStr = encryptedSharedPreferences.getString(KEY_SERVER_INFO_FEATURES.name, "") ?: ""
        val features = if (featuresStr.isEmpty()) emptyList() else featuresStr.split(",")

        return CachedServerInfo(
            canonical = canonical,
            release = release,
            build = build,
            features = features
        )
    }

    override suspend fun clearServerInfo() {
        encryptedSharedPreferences.edit(commit = true) {
            remove(KEY_SERVER_INFO_CANONICAL.name)
            remove(KEY_SERVER_INFO_RELEASE.name)
            remove(KEY_SERVER_INFO_BUILD.name)
            remove(KEY_SERVER_INFO_FEATURES.name)
        }
    }

    private fun annotationSnapshotKey(bookmarkId: String): String {
        return "cached_annotation_snapshot_$bookmarkId"
    }

    private fun migrateLegacySepiaSetting(preferences: SharedPreferences) {
        val storedTheme = preferences.getString(KEY_THEME.name, null)
        if (storedTheme == "SEPIA") {
            preferences.edit(commit = true) {
                putString(KEY_THEME.name, Theme.LIGHT.name)
                putBoolean(KEY_SEPIA_ENABLED.name, true)
            }
        }
    }

    private fun migrateAppearancePreferencesIfNeeded() {
        val hasLightAppearance = userPreferences.contains(KEY_LIGHT_APPEARANCE.name)
        val hasDarkAppearance = userPreferences.contains(KEY_DARK_APPEARANCE.name)
        if (hasLightAppearance && hasDarkAppearance) {
            return
        }

        val legacySepiaEnabled = userPreferences.getBoolean(KEY_SEPIA_ENABLED.name, false)
        userPreferences.edit(commit = true) {
            if (!hasLightAppearance) {
                putString(
                    KEY_LIGHT_APPEARANCE.name,
                    if (legacySepiaEnabled) LightAppearance.SEPIA.name else LightAppearance.PAPER.name
                )
            }
            if (!hasDarkAppearance) {
                putString(KEY_DARK_APPEARANCE.name, DarkAppearance.DARK.name)
            }
        }
    }

    private fun migrateNonSensitivePreferencesIfNeeded() {
        if (userPreferences.getBoolean(KEY_UI_PREFS_MIGRATED, false)) {
            return
        }

        userPreferences.edit(commit = true) {
            migrateBooleanPreference(this, KEY_AUTOSYNC_ENABLED.name)
            migrateStringPreference(this, KEY_AUTOSYNC_TIMEFRAME.name)
            migrateStringPreference(this, KEY_THEME.name)
            migrateBooleanPreference(this, KEY_SYNC_ON_APP_OPEN.name)
            migrateBooleanPreference(this, KEY_SYNC_NOTIFICATIONS_ENABLED.name)
            migrateStringPreference(this, KEY_LAYOUT_MODE.name)
            migrateStringPreference(this, KEY_SORT_OPTION.name)
            migrateIntPreference(this, KEY_LOG_RETENTION_DAYS.name)
            migrateStringPreference(this, KEY_CONTENT_SYNC_MODE.name)
            migrateBooleanPreference(this, KEY_WIFI_ONLY.name)
            migrateBooleanPreference(this, KEY_ALLOW_BATTERY_SAVER.name)
            migrateStringPreference(this, KEY_DATE_RANGE_FROM.name)
            migrateStringPreference(this, KEY_DATE_RANGE_TO.name)
            migrateBooleanPreference(this, KEY_SEPIA_ENABLED.name)
            migrateStringPreference(this, KEY_LIGHT_APPEARANCE.name)
            migrateStringPreference(this, KEY_DARK_APPEARANCE.name)
            migrateBooleanPreference(this, KEY_KEEP_SCREEN_ON_READING.name)
            migrateBooleanPreference(this, KEY_FULLSCREEN_WHILE_READING.name)
            migrateIntPreference(this, KEY_TYPO_FONT_SIZE.name)
            migrateStringPreference(this, KEY_TYPO_FONT_FAMILY.name)
            migrateStringPreference(this, KEY_TYPO_LINE_SPACING.name)
            migrateIntPreference(this, KEY_TYPO_LINE_SPACING_PERCENT.name)
            migrateStringPreference(this, KEY_TYPO_TEXT_WIDTH.name)
            migrateBooleanPreference(this, KEY_TYPO_JUSTIFIED.name)
            migrateBooleanPreference(this, KEY_TYPO_HYPHENATION.name)
            putBoolean(KEY_UI_PREFS_MIGRATED, true)
        }
    }

    private fun migrateStringPreference(editor: SharedPreferences.Editor, key: String) {
        if (!userPreferences.contains(key) && encryptedSharedPreferences.contains(key)) {
            editor.putString(key, encryptedSharedPreferences.getString(key, null))
        }
    }

    private fun migrateBooleanPreference(editor: SharedPreferences.Editor, key: String) {
        if (!userPreferences.contains(key) && encryptedSharedPreferences.contains(key)) {
            editor.putBoolean(key, encryptedSharedPreferences.getBoolean(key, false))
        }
    }

    private fun migrateIntPreference(editor: SharedPreferences.Editor, key: String) {
        if (!userPreferences.contains(key) && encryptedSharedPreferences.contains(key)) {
            editor.putInt(key, encryptedSharedPreferences.getInt(key, 0))
        }
    }

    companion object {
        private const val USER_PREFERENCES_FILE_NAME = "user_preferences"
        private const val KEY_UI_PREFS_MIGRATED = "ui_prefs_migrated"
        private const val LEGACY_CONTENT_SYNC_MODE_AUTOMATIC = "AUTOMATIC"
        private const val LEGACY_CONTENT_SYNC_MODE_MANUAL = "MANUAL"
    }
}
