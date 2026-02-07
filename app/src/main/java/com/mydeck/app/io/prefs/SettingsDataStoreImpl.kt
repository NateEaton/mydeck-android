package com.mydeck.app.io.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangeParams
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStoreImpl @Inject constructor(@ApplicationContext private val context: Context) :
    SettingsDataStore {

    private val encryptedSharedPreferences = EncryptionHelper.getEncryptedSharedPreferences(context)

    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_URL = stringPreferencesKey("url")
    private val KEY_PASSWORD = stringPreferencesKey("password")
    private val KEY_LAST_BOOKMARK_TIMESTAMP = stringPreferencesKey("lastBookmarkTimestamp")
    private val KEY_LAST_SYNC_TIMESTAMP = stringPreferencesKey("lastSyncTimestamp")
    private val KEY_LAST_CONTENT_SYNC_TIMESTAMP = stringPreferencesKey("lastContentSyncTimestamp")
    private val KEY_LAST_FULL_SYNC_TIMESTAMP = stringPreferencesKey("last_full_sync_timestamp")
    private val KEY_INITIAL_SYNC_PERFORMED = "initial_sync_performed"
    private val KEY_AUTOSYNC_ENABLED = booleanPreferencesKey("autosync_enabled")
    private val KEY_AUTOSYNC_TIMEFRAME = stringPreferencesKey("autosync_timeframe")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_ZOOM_FACTOR = intPreferencesKey("zoom_factor")
    private val KEY_SYNC_ON_APP_OPEN = booleanPreferencesKey("sync_on_app_open")
    private val KEY_SYNC_NOTIFICATIONS_ENABLED = booleanPreferencesKey("sync_notifications_enabled")
    private val KEY_LAYOUT_MODE = stringPreferencesKey("layout_mode")
    private val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
    private val KEY_CONTENT_SYNC_MODE = stringPreferencesKey("content_sync_mode")
    private val KEY_WIFI_ONLY = booleanPreferencesKey("content_sync_wifi_only")
    private val KEY_ALLOW_BATTERY_SAVER = booleanPreferencesKey("content_sync_allow_battery_saver")
    private val KEY_DATE_RANGE_FROM = stringPreferencesKey("date_range_from")
    private val KEY_DATE_RANGE_TO = stringPreferencesKey("date_range_to")

    override fun saveUsername(username: String) {
        Timber.d("saveUsername")
        encryptedSharedPreferences.edit {
            putString(KEY_USERNAME.name, username)
        }
    }

    override fun savePassword(password: String) {
        Timber.d("savePassword")
        encryptedSharedPreferences.edit {
            putString(KEY_PASSWORD.name, password)
        }
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

    override suspend fun setInitialSyncPerformed(performed: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_INITIAL_SYNC_PERFORMED, performed)
        }
    }

    override suspend fun isInitialSyncPerformed(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_INITIAL_SYNC_PERFORMED, false)
    }

    override suspend fun isAutoSyncEnabled(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_AUTOSYNC_ENABLED.name, false)
    }

    override suspend fun setAutoSyncEnabled(isEnabled: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_AUTOSYNC_ENABLED.name, isEnabled)
        }
    }

    override suspend fun getAutoSyncTimeframe(): AutoSyncTimeframe {
        return encryptedSharedPreferences.getString(KEY_AUTOSYNC_TIMEFRAME.name, AutoSyncTimeframe.MANUAL.name)?.let {
            AutoSyncTimeframe.valueOf(it)
        } ?: AutoSyncTimeframe.MANUAL
    }

    override suspend fun saveAutoSyncTimeframe(autoSyncTimeframe: AutoSyncTimeframe) {
        Timber.d("saveAutoSyncTimeframe")
        encryptedSharedPreferences.edit {
            putString(KEY_AUTOSYNC_TIMEFRAME.name, autoSyncTimeframe.name)
        }
    }

    override suspend fun getTheme(): Theme {
        return encryptedSharedPreferences.getString(KEY_THEME.name, Theme.SYSTEM.name)?.let {
            Theme.valueOf(it)
        } ?: Theme.SYSTEM
    }

    override suspend fun saveTheme(theme: Theme) {
        encryptedSharedPreferences.edit {
            putString(KEY_THEME.name, theme.name)
        }
    }

    override suspend fun getZoomFactor(): Int {
        return encryptedSharedPreferences.getInt(KEY_ZOOM_FACTOR.name, 100)
    }

    override suspend fun saveZoomFactor(zoomFactor: Int) {
        encryptedSharedPreferences.edit {
            putInt(KEY_ZOOM_FACTOR.name, zoomFactor.coerceIn(25, 400))
        }
    }

    override suspend fun setSyncOnAppOpenEnabled(isEnabled: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_SYNC_ON_APP_OPEN.name, isEnabled)
        }
    }

    override suspend fun isSyncOnAppOpenEnabled(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_SYNC_ON_APP_OPEN.name, true)
    }

    override suspend fun setSyncNotificationsEnabled(isEnabled: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_SYNC_NOTIFICATIONS_ENABLED.name, isEnabled)
        }
    }

    override suspend fun isSyncNotificationsEnabled(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_SYNC_NOTIFICATIONS_ENABLED.name, true)
    }

    override val tokenFlow = getStringFlow(KEY_TOKEN.name, null)
    override val usernameFlow = getStringFlow(KEY_USERNAME.name, null)
    override val urlFlow = getStringFlow(KEY_URL.name, null)
    override val passwordFlow = getStringFlow(KEY_PASSWORD.name, null)
    override val themeFlow = getStringFlow(KEY_THEME.name, Theme.SYSTEM.name)
    override val zoomFactorFlow = getIntFlow(KEY_ZOOM_FACTOR.name, 100)
    override suspend fun clearCredentials() {
        Timber.d("clearCredentials")
        encryptedSharedPreferences.edit(commit = true) {
            remove(KEY_USERNAME.name)
            remove(KEY_PASSWORD.name)
            remove(KEY_TOKEN.name)
            remove(KEY_URL.name)
        }
    }

    override suspend fun saveCredentials(
        url: String,
        username: String,
        password: String,
        token: String
    ) {
        Timber.d("saveCredentials")
        encryptedSharedPreferences.edit {
            putString(KEY_URL.name, url)
            putString(KEY_USERNAME.name, username)
            putString(KEY_PASSWORD.name, password)
            putString(KEY_TOKEN.name, token)
        }
    }

    private fun getStringFlow(key: String, defaultValue: String? = null): StateFlow<String?> =
        preferenceFlow(key) { encryptedSharedPreferences.getString(key, defaultValue) }

    private fun getIntFlow(key: String, defaultValue: Int = 100): StateFlow<Int> =
        preferenceFlow(key) { encryptedSharedPreferences.getInt(key, defaultValue) }

    private fun <T> preferenceFlow(key: String, getValue: () -> T): StateFlow<T> { // Create our flow using callbackflow
        // Emit initial value when we start collecting from this flow (if it exists) or use default one from params in function call above!  This is important so consumers know initial state!  Can skip this and just send updates if you do not need initial state emission on subscribe time!  That could be fine too depending on your use case - remember that!  Also you can send null as the "initial" value as well if you want!
        val state = MutableStateFlow(getValue())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {  // Only send updates for this specific key
                Timber.d("pref changed key=$key")
                val value = getValue()
                state.value = value
            }
        }

        encryptedSharedPreferences.registerOnSharedPreferenceChangeListener(listener) // Register the listener
        return state.asStateFlow()
    }

    override suspend fun saveLayoutMode(layoutMode: String) {
        encryptedSharedPreferences.edit {
            putString(KEY_LAYOUT_MODE.name, layoutMode)
        }
    }

    override suspend fun getLayoutMode(): String? {
        return encryptedSharedPreferences.getString(KEY_LAYOUT_MODE.name, null)
    }

    override suspend fun saveSortOption(sortOption: String) {
        encryptedSharedPreferences.edit {
            putString(KEY_SORT_OPTION.name, sortOption)
        }
    }

    override suspend fun getSortOption(): String? {
        return encryptedSharedPreferences.getString(KEY_SORT_OPTION.name, null)
    }

    override suspend fun getContentSyncMode(): ContentSyncMode {
        return encryptedSharedPreferences.getString(KEY_CONTENT_SYNC_MODE.name, ContentSyncMode.AUTOMATIC.name)?.let {
            try { ContentSyncMode.valueOf(it) } catch (_: Exception) { ContentSyncMode.AUTOMATIC }
        } ?: ContentSyncMode.AUTOMATIC
    }

    override suspend fun saveContentSyncMode(mode: ContentSyncMode) {
        encryptedSharedPreferences.edit {
            putString(KEY_CONTENT_SYNC_MODE.name, mode.name)
        }
    }

    override suspend fun getContentSyncConstraints(): ContentSyncConstraints {
        return ContentSyncConstraints(
            wifiOnly = encryptedSharedPreferences.getBoolean(KEY_WIFI_ONLY.name, true),
            allowOnBatterySaver = encryptedSharedPreferences.getBoolean(KEY_ALLOW_BATTERY_SAVER.name, false)
        )
    }

    override suspend fun saveWifiOnly(enabled: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_WIFI_ONLY.name, enabled)
        }
    }

    override suspend fun saveAllowBatterySaver(enabled: Boolean) {
        encryptedSharedPreferences.edit {
            putBoolean(KEY_ALLOW_BATTERY_SAVER.name, enabled)
        }
    }

    override suspend fun getDateRangeParams(): DateRangeParams? {
        val from = encryptedSharedPreferences.getString(KEY_DATE_RANGE_FROM.name, null)
        val to = encryptedSharedPreferences.getString(KEY_DATE_RANGE_TO.name, null)
        return if (from != null && to != null) {
            try {
                DateRangeParams(from = LocalDate.parse(from), to = LocalDate.parse(to))
            } catch (_: Exception) { null }
        } else null
    }

    override suspend fun saveDateRangeParams(params: DateRangeParams) {
        encryptedSharedPreferences.edit {
            putString(KEY_DATE_RANGE_FROM.name, params.from.toString())
            putString(KEY_DATE_RANGE_TO.name, params.to.toString())
        }
    }
}
