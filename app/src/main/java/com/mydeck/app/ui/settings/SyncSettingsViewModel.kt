package com.mydeck.app.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.sync.DateRangeParams
import com.mydeck.app.domain.sync.DateRangePreset
import com.mydeck.app.domain.sync.toDateRange
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.DateRangeContentSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val settingsDataStore: SettingsDataStore,
    private val fullSyncUseCase: FullSyncUseCase,
    private val contentPackageManager: ContentPackageManager,
    private val contentSyncPolicyEvaluator: ContentSyncPolicyEvaluator,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val dateFormat = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.MEDIUM
    )
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    // Bookmark sync
    private val bookmarkSyncFrequency = MutableStateFlow(AutoSyncTimeframe.HOURS_01)

    // Content sync
    private val contentSyncMode = MutableStateFlow(ContentSyncMode.AUTOMATIC)
    private val dateRangePreset = MutableStateFlow(DateRangePreset.PAST_MONTH)
    private val dateRangeFrom = MutableStateFlow<LocalDate?>(null)
    private val dateRangeTo = MutableStateFlow<LocalDate?>(null)
    private val isDateRangeDownloading = MutableStateFlow(false)

    // Constraints
    private val wifiOnly = MutableStateFlow(false)
    private val allowBatterySaver = MutableStateFlow(true)

    // Content policy
    private val downloadImages = MutableStateFlow(false)
    private val includeArchivedContent = MutableStateFlow(false)
    private val clearContentOnArchive = MutableStateFlow(false)

    // Dialog
    private val showDialog = MutableStateFlow<SyncSettingsDialog?>(null)

    // Constraint override state
    private var savedWifiOnly = false
    private var savedAllowBatterySaver = true
    private var constraintBlockingDownload: String? = null  // Description of which constraint is blocking
    private var blockedByWifiConstraint = false  // Whether wifi constraint would block
    private var blockedByBatterySaverConstraint = false  // Whether battery saver constraint would block
    private var constraintOverrideTrigger: ConstraintOverrideTrigger = ConstraintOverrideTrigger.DATE_RANGE

    private enum class ConstraintOverrideTrigger { DATE_RANGE, SYNC_NOW }

    // Content sync constraint status (computed)
    private val contentSyncStatusRes = MutableStateFlow<Int?>(null)

    // Storage
    private val offlineStorageSize = MutableStateFlow<String?>(null)

    // Last sync timestamps
    private val lastSyncTimestamp = MutableStateFlow<String?>(null)
    private val lastContentSyncTimestamp = MutableStateFlow<String?>(null)

    // Sync status from DB — also refreshes storage size when content counts change
    private val detailedSyncStatus = bookmarkDao.observeDetailedSyncStatus()
        .map { counts ->
            val result = counts ?: BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0, 0)
            // Refresh storage size whenever content download counts change
            refreshStorageSize()
            result
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0, 0)
        )

    // Work info for next scheduled run
    private val workInfoNext = fullSyncUseCase.workInfoFlow.map { workInfoList ->
        workInfoList.firstOrNull()?.let {
            if (it.state == WorkInfo.State.ENQUEUED) it.nextScheduleTimeMillis else null
        }
    }
    private val bookmarkSyncRunning = fullSyncUseCase.syncIsRunning

    // Date range download work status
    private val dateRangeWorkStatus = workManager
        .getWorkInfosForUniqueWorkFlow(DateRangeContentSyncWorker.UNIQUE_WORK_NAME)
        .map { workInfoList ->
            workInfoList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

    init {
        viewModelScope.launch {
            // Load all settings
            bookmarkSyncFrequency.value = settingsDataStore.getAutoSyncTimeframe().let { timeframe ->
                // If MANUAL, default to HOURS_01 since bookmark sync is always on
                if (timeframe == AutoSyncTimeframe.MANUAL) AutoSyncTimeframe.HOURS_01 else timeframe
            }
            contentSyncMode.value = settingsDataStore.getContentSyncMode()
            val constraints = settingsDataStore.getContentSyncConstraints()
            wifiOnly.value = constraints.wifiOnly
            allowBatterySaver.value = constraints.allowOnBatterySaver
            downloadImages.value = settingsDataStore.isDownloadImagesEnabled()
            includeArchivedContent.value = settingsDataStore.isIncludeArchivedContentInSyncEnabled()
            clearContentOnArchive.value = settingsDataStore.isClearContentOnArchiveEnabled()
            settingsDataStore.getDateRangeParams()?.let {
                dateRangePreset.value = it.preset
                dateRangeFrom.value = it.from
                dateRangeTo.value = it.to
            }
            settingsDataStore.getLastSyncTimestamp()?.let {
                lastSyncTimestamp.value = dateFormat.format(Date(it.toEpochMilliseconds()))
            }
            settingsDataStore.getLastContentSyncTimestamp()?.let {
                lastContentSyncTimestamp.value = dateFormat.format(Date(it.toEpochMilliseconds()))
            }

            // Calculate storage usage and constraint status
            refreshStorageSize()
            refreshContentSyncStatus()

            // Perform settings migration for existing users
            performSettingsMigration()
        }

        // Observe date range work status and refresh storage size when work completes
        viewModelScope.launch {
            var wasRunning = false
            dateRangeWorkStatus.collect { running ->
                isDateRangeDownloading.value = running
                if (wasRunning && !running) {
                    refreshStorageSize()
                }
                wasRunning = running
            }
        }
    }

    private suspend fun performSettingsMigration() {
        val migrationKey = "sync_settings_v3_migrated"
        val prefs = context.getSharedPreferences("sync_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean(migrationKey, false)) return

        // Migrate: if autoSyncEnabled was false, set content mode to MANUAL
        val wasAutoSyncEnabled = settingsDataStore.isAutoSyncEnabled()
        if (!wasAutoSyncEnabled) {
            settingsDataStore.saveContentSyncMode(ContentSyncMode.MANUAL)
            contentSyncMode.value = ContentSyncMode.MANUAL
        }

        // Ensure bookmark sync is always scheduled (was previously toggle-able)
        val timeframe = settingsDataStore.getAutoSyncTimeframe()
        if (timeframe != AutoSyncTimeframe.MANUAL) {
            fullSyncUseCase.scheduleFullSyncWorker(timeframe)
        } else {
            // Default to hourly if it was manual
            settingsDataStore.saveAutoSyncTimeframe(AutoSyncTimeframe.HOURS_01)
            fullSyncUseCase.scheduleFullSyncWorker(AutoSyncTimeframe.HOURS_01)
            bookmarkSyncFrequency.value = AutoSyncTimeframe.HOURS_01
        }

        // Mark migration done
        prefs.edit().putBoolean(migrationKey, true).apply()
        Timber.i("Sync settings v3 migration completed")
    }

    val uiState: StateFlow<SyncSettingsUiState> = combine(
        bookmarkSyncFrequency,
        contentSyncMode,
        showDialog,
        workInfoNext,
        wifiOnly,
        bookmarkSyncRunning
    ) { args: Array<Any?> ->
        val freq = args[0] as AutoSyncTimeframe
        val mode = args[1] as ContentSyncMode
        val dialog = args[2] as SyncSettingsDialog?
        val next = args[3] as Long?
        val wifi = args[4] as Boolean
        val syncRunning = args[5] as Boolean
        SyncSettingsPartial1(freq, mode, dialog, next, wifi, syncRunning)
    }.combine(
        combine(
            allowBatterySaver,
            dateRangePreset,
            dateRangeFrom,
            dateRangeTo,
            isDateRangeDownloading,
            detailedSyncStatus
        ) { args: Array<Any?> ->
            val battery = args[0] as Boolean
            val preset = args[1] as DateRangePreset
            val from = args[2] as LocalDate?
            val to = args[3] as LocalDate?
            val downloading = args[4] as Boolean
            val status = args[5] as BookmarkDao.DetailedSyncStatusCounts
            SyncSettingsPartial2(battery, preset, from, to, downloading, status)
        }
    ) { p1, p2 ->
        val myListCount = (p2.status.total - p2.status.archived).coerceAtLeast(0)
        SyncSettingsUiState(
            bookmarkSyncFrequency = p1.freq,
            bookmarkSyncFrequencyOptions = getBookmarkSyncOptions(p1.freq),
            nextAutoSyncRun = p1.next?.let { dateFormat.format(Date(it)) },
            contentSyncMode = p1.mode,
            dateRangePreset = p2.preset,
            dateRangeFrom = p2.from,
            dateRangeTo = p2.to,
            isDateRangeDownloading = p2.downloading,
            isBookmarkSyncRunning = p1.syncRunning,
            wifiOnly = p1.wifi,
            allowBatterySaver = p2.battery,
            syncStatus = SyncStatus(
                totalBookmarks = p2.status.total,
                unread = myListCount,
                archived = p2.status.archived,
                favorites = p2.status.favorites,
                contentDownloaded = p2.status.contentDownloaded,
                contentAvailable = p2.status.contentAvailable,
                contentDirty = p2.status.contentDirty,
                permanentNoContent = p2.status.permanentNoContent
            ),
            showDialog = p1.dialog
        )
    }.combine(lastSyncTimestamp) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastBookmarkSyncTimestamp = ts))
    }.combine(lastContentSyncTimestamp) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastContentSyncTimestamp = ts))
    }.combine(offlineStorageSize) { state, size ->
        state.copy(syncStatus = state.syncStatus.copy(offlineStorageSize = size))
    }.combine(includeArchivedContent) { state, include ->
        state.copy(includeArchivedContent = include)
    }.combine(clearContentOnArchive) { state, clear ->
        state.copy(clearContentOnArchive = clear)
    }.combine(downloadImages) { state, images ->
        state.copy(downloadImages = images)
    }.combine(contentSyncStatusRes) { state, statusRes ->
        state.copy(contentSyncStatusRes = statusRes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncSettingsUiState()
    )

    // --- Bookmark Sync ---

    fun onClickBookmarkSyncFrequency() {
        showDialog.value = SyncSettingsDialog.BookmarkSyncFrequencyDialog
    }

    fun onBookmarkSyncFrequencySelected(selected: AutoSyncTimeframe) {
        // Don't allow MANUAL - bookmark sync is always on
        val effective = if (selected == AutoSyncTimeframe.MANUAL) AutoSyncTimeframe.HOURS_01 else selected
        fullSyncUseCase.scheduleFullSyncWorker(effective)
        viewModelScope.launch {
            settingsDataStore.saveAutoSyncTimeframe(effective)
            bookmarkSyncFrequency.value = effective
        }
    }

    fun onClickSyncBookmarksNow() {
        fullSyncUseCase.performForcedFullSync()

        // After triggering the bookmark sync, check if content sync constraints would block
        // and show the override dialog if the user has auto content sync enabled
        viewModelScope.launch {
            if (contentSyncMode.value != ContentSyncMode.AUTOMATIC) return@launch
            val decision = contentSyncPolicyEvaluator.canFetchContent()
            if (!decision.allowed && decision.blockedReason != "No network") {
                constraintBlockingDownload = decision.blockedReason
                blockedByWifiConstraint = decision.blockedReason == "Wi-Fi required"
                blockedByBatterySaverConstraint = decision.blockedReason == "Battery saver active"
                constraintOverrideTrigger = ConstraintOverrideTrigger.SYNC_NOW
                showDialog.value = SyncSettingsDialog.ConstraintOverrideDialog
            }
        }
    }

    // --- Content Sync ---

    fun onContentSyncModeSelected(mode: ContentSyncMode) {
        viewModelScope.launch {
            settingsDataStore.saveContentSyncMode(mode)
            contentSyncMode.value = mode
            refreshContentSyncStatus()
        }
    }


    fun onDateRangePresetSelected(preset: DateRangePreset) {
        dateRangePreset.value = preset

        // If preset is not CUSTOM, calculate and save the dates
        if (preset != DateRangePreset.CUSTOM) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val (from, to) = preset.toDateRange(today)
            dateRangeFrom.value = from
            dateRangeTo.value = to
        }

        // Save the preset and current dates
        viewModelScope.launch {
            settingsDataStore.saveDateRangeParams(
                DateRangeParams(
                    preset = preset,
                    from = dateRangeFrom.value,
                    to = dateRangeTo.value
                )
            )
        }
    }

    fun onDateRangeFromSelected(date: LocalDate) {
        dateRangeFrom.value = date
        saveDateRangeIfBothSet()
    }

    fun onDateRangeToSelected(date: LocalDate) {
        dateRangeTo.value = date
        saveDateRangeIfBothSet()
    }

    private fun saveDateRangeIfBothSet() {
        val from = dateRangeFrom.value ?: return
        val to = dateRangeTo.value ?: return
        viewModelScope.launch {
            settingsDataStore.saveDateRangeParams(
                DateRangeParams(
                    preset = dateRangePreset.value,
                    from = from,
                    to = to
                )
            )
        }
    }

    fun onClickDateRangeDownload() {
        val from = dateRangeFrom.value ?: return
        val to = dateRangeTo.value ?: return

        Timber.d("DateRangeDownload: Checking constraints - wifiOnly=$wifiOnly.value, allowBatterySaver=$allowBatterySaver.value")

        // Check if constraints would actually block the download
        blockedByWifiConstraint = wifiOnly.value && !isWifiConnected()
        blockedByBatterySaverConstraint = !allowBatterySaver.value && isBatterySaverActive()

        Timber.d("DateRangeDownload: blockedByWifi=$blockedByWifiConstraint, blockedByBattery=$blockedByBatterySaverConstraint")

        if (blockedByWifiConstraint || blockedByBatterySaverConstraint) {
            // Show dialog asking to override only if constraints would actually block
            val blockingConstraints = mutableListOf<String>()
            if (blockedByWifiConstraint) blockingConstraints.add("Wi-Fi only")
            if (blockedByBatterySaverConstraint) blockingConstraints.add("battery saver active")

            constraintBlockingDownload = blockingConstraints.joinToString(" and ")
            constraintOverrideTrigger = ConstraintOverrideTrigger.DATE_RANGE
            Timber.d("DateRangeDownload: Showing override dialog - $constraintBlockingDownload")
            showDialog.value = SyncSettingsDialog.ConstraintOverrideDialog
            return
        }

        // No constraints blocking, proceed with download
        Timber.d("DateRangeDownload: No constraints blocking, proceeding with download")
        performDateRangeDownload(from, to)
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isBatterySaverActive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false  // Power save mode not available before Android 5.0
        }
    }

    fun onConstraintOverrideConfirmed() {
        showDialog.value = null

        when (constraintOverrideTrigger) {
            ConstraintOverrideTrigger.DATE_RANGE -> {
                val from = dateRangeFrom.value ?: return
                val to = dateRangeTo.value ?: return
                Timber.d("DateRangeDownload: Override confirmed - applying overrides: wifi=$blockedByWifiConstraint, battery=$blockedByBatterySaverConstraint")
                performDateRangeDownload(
                    from = from,
                    to = to,
                    overrideWifiOnly = blockedByWifiConstraint,
                    overrideBatterySaver = blockedByBatterySaverConstraint
                )
            }
            ConstraintOverrideTrigger.SYNC_NOW -> {
                Timber.d("SyncNow: Override confirmed - enqueueing content sync without constraints")
                enqueueContentSyncWithoutConstraints()
            }
        }
    }

    fun onConstraintOverrideCancelled() {
        constraintBlockingDownload = null
        showDialog.value = null
    }

    private fun performDateRangeDownload(
        from: LocalDate,
        to: LocalDate,
        overrideWifiOnly: Boolean = false,
        overrideBatterySaver: Boolean = false
    ) {
        // Calculate override flag
        val isOverriding = overrideWifiOnly || overrideBatterySaver

        // Convert LocalDate to epoch millis for the worker
        val fromEpoch = from.toEpochDays().toLong() * 86400L * 1000L
        val toEpoch = (to.toEpochDays().toLong() + 1) * 86400L * 1000L // End of day

        val inputData = Data.Builder()
            .putLong(DateRangeContentSyncWorker.PARAM_FROM_EPOCH, fromEpoch)
            .putLong(DateRangeContentSyncWorker.PARAM_TO_EPOCH, toEpoch)
            .putBoolean(DateRangeContentSyncWorker.PARAM_OVERRIDE, isOverriding)
            .build()

        // Build constraints based on current settings and overrides
        // When user confirms override, we disable ALL constraints to allow the download to proceed

        val constraintsBuilder = Constraints.Builder()

        // Determine network type constraint
        val networkType = if (isOverriding || !wifiOnly.value) {
            // If overriding ANY constraint OR WiFi constraint not enabled, allow any network
            NetworkType.CONNECTED
        } else {
            // WiFi-only constraint is enabled and NO constraints being overridden
            NetworkType.UNMETERED
        }
        constraintsBuilder.setRequiredNetworkType(networkType)

        Timber.d("DateRangeDownload: Setting network constraint - type=$networkType (wifiOnly=$wifiOnly.value, overridingAny=$isOverriding)")

        // Determine battery constraint
        // If ANY constraint is being overridden, disable ALL constraints
        val shouldApplyBatteryConstraint = !isOverriding && !allowBatterySaver.value
        if (shouldApplyBatteryConstraint) {
            // Battery saver constraint is enabled and NO constraints being overridden
            constraintsBuilder.setRequiresBatteryNotLow(true)
            Timber.d("DateRangeDownload: Setting battery constraint - requireBatteryNotLow=true")
        } else {
            Timber.d("DateRangeDownload: No battery constraint (overridingAny=$isOverriding, allow=$allowBatterySaver.value)")
        }

        val constraints = constraintsBuilder.build()

        val request = OneTimeWorkRequestBuilder<DateRangeContentSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        Timber.i("DateRangeDownload: Enqueueing work - from=$from, to=$to, epochs=[$fromEpoch, $toEpoch], networkType=$networkType, overridingAllConstraints=$isOverriding")

        workManager.enqueueUniqueWork(
            DateRangeContentSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Timber.i("DateRangeDownload: Work enqueued successfully")

        // Switch back to MANUAL mode after successful download
        viewModelScope.launch {
            delay(1000)  // Wait a moment for work to be enqueued
            settingsDataStore.saveContentSyncMode(ContentSyncMode.MANUAL)
            contentSyncMode.value = ContentSyncMode.MANUAL
        }
    }

    private fun enqueueContentSyncWithoutConstraints() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<com.mydeck.app.worker.BatchArticleLoadWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            com.mydeck.app.worker.BatchArticleLoadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // --- Storage ---

    fun onClickClearOfflineContent() {
        showDialog.value = SyncSettingsDialog.ClearOfflineContentDialog
    }

    fun onConfirmClearOfflineContent() {
        showDialog.value = null
        viewModelScope.launch {
            contentPackageManager.deleteAllContent()
            refreshStorageSize()
            Timber.i("All offline content cleared by user")
        }
    }

    private suspend fun refreshContentSyncStatus() {
        val mode = contentSyncMode.value
        if (mode != ContentSyncMode.AUTOMATIC) {
            contentSyncStatusRes.value = R.string.sync_content_status_manual
            return
        }
        val decision = contentSyncPolicyEvaluator.canFetchContent()
        contentSyncStatusRes.value = if (decision.allowed) {
            R.string.sync_content_status_up_to_date
        } else {
            when (decision.blockedReason) {
                "Wi-Fi required" -> R.string.sync_content_waiting_wifi
                "Battery saver active" -> R.string.sync_content_waiting_battery
                else -> null
            }
        }
    }

    private fun refreshStorageSize() {
        val bytes = contentPackageManager.calculateTotalSize()
        offlineStorageSize.value = formatFileSize(bytes)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    // --- Content Policy ---

    fun onDownloadImagesChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDownloadImagesEnabled(enabled)
            downloadImages.value = enabled
        }
    }

    fun onIncludeArchivedContentChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveIncludeArchivedContentInSyncEnabled(enabled)
            includeArchivedContent.value = enabled
        }
    }

    fun onClearContentOnArchiveChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveClearContentOnArchiveEnabled(enabled)
            clearContentOnArchive.value = enabled
        }
    }

    // --- Constraints ---

    fun onWifiOnlyChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveWifiOnly(enabled)
            wifiOnly.value = enabled
            refreshContentSyncStatus()
        }
    }

    fun onAllowBatterySaverChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveAllowBatterySaver(enabled)
            allowBatterySaver.value = enabled
            refreshContentSyncStatus()
        }
    }

    // --- Dialog ---

    fun onShowDialog(dialog: SyncSettingsDialog) {
        showDialog.value = dialog
    }

    fun onDismissDialog() {
        showDialog.value = null
    }

    // --- Navigation ---

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null }
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }

    // --- Helpers ---

    private fun getBookmarkSyncOptions(selected: AutoSyncTimeframe): List<AutoSyncTimeframeOption> {
        // Exclude MANUAL since bookmark sync is always on
        return AutoSyncTimeframe.entries.filter { it != AutoSyncTimeframe.MANUAL }.map {
            AutoSyncTimeframeOption(
                autoSyncTimeframe = it,
                label = it.toLabelResource(),
                selected = it == selected
            )
        }
    }

    // Internal data classes for combine
    private data class SyncSettingsPartial1(
        val freq: AutoSyncTimeframe,
        val mode: ContentSyncMode,
        val dialog: SyncSettingsDialog?,
        val next: Long?,
        val wifi: Boolean,
        val syncRunning: Boolean
    )

    private data class SyncSettingsPartial2(
        val battery: Boolean,
        val preset: DateRangePreset,
        val from: LocalDate?,
        val to: LocalDate?,
        val downloading: Boolean,
        val status: BookmarkDao.DetailedSyncStatusCounts
    )
}

@Immutable
data class SyncSettingsUiState(
    // Bookmark sync
    val bookmarkSyncFrequency: AutoSyncTimeframe = AutoSyncTimeframe.HOURS_01,
    val bookmarkSyncFrequencyOptions: List<AutoSyncTimeframeOption> = emptyList(),
    val nextAutoSyncRun: String? = null,
    val isBookmarkSyncRunning: Boolean = false,

    // Content sync
    val contentSyncMode: ContentSyncMode = ContentSyncMode.AUTOMATIC,
    val dateRangePreset: DateRangePreset = DateRangePreset.PAST_MONTH,
    val dateRangeFrom: LocalDate? = null,
    val dateRangeTo: LocalDate? = null,
    val isDateRangeDownloading: Boolean = false,

    // Constraints
    val wifiOnly: Boolean = false,
    val allowBatterySaver: Boolean = true,

    // Content policy
    val downloadImages: Boolean = false,
    val includeArchivedContent: Boolean = false,
    val clearContentOnArchive: Boolean = false,

    // Content sync status message (constraint feedback)
    val contentSyncStatusRes: Int? = null,

    // Sync status
    val syncStatus: SyncStatus = SyncStatus(),

    // Dialog state
    val showDialog: SyncSettingsDialog? = null
)

@Immutable
data class SyncStatus(
    val totalBookmarks: Int = 0,
    val unread: Int = 0,
    val archived: Int = 0,
    val favorites: Int = 0,
    val contentDownloaded: Int = 0,
    val contentAvailable: Int = 0,
    val contentDirty: Int = 0,
    val permanentNoContent: Int = 0,
    val lastBookmarkSyncTimestamp: String? = null,
    val lastContentSyncTimestamp: String? = null,
    val offlineStorageSize: String? = null
)

enum class SyncSettingsDialog {
    BookmarkSyncFrequencyDialog,
    BackgroundRationaleDialog,
    PermissionRequest,
    DateFromPicker,
    DateToPicker,
    ConstraintOverrideDialog,
    ClearOfflineContentDialog
}

data class AutoSyncTimeframeOption(
    val autoSyncTimeframe: AutoSyncTimeframe,
    @StringRes
    val label: Int,
    val selected: Boolean
)

@StringRes
fun AutoSyncTimeframe.toLabelResource(): Int {
    return when (this) {
        AutoSyncTimeframe.MANUAL -> R.string.auto_sync_timeframe_manual
        AutoSyncTimeframe.MINUTES_15 -> R.string.auto_sync_timeframe_15_minutes
        AutoSyncTimeframe.HOURS_01 -> R.string.auto_sync_timeframe_01_hours
        AutoSyncTimeframe.HOURS_06 -> R.string.auto_sync_timeframe_06_hours
        AutoSyncTimeframe.HOURS_12 -> R.string.auto_sync_timeframe_12_hours
        AutoSyncTimeframe.DAYS_01 -> R.string.auto_sync_timeframe_01_days
        AutoSyncTimeframe.DAYS_07 -> R.string.auto_sync_timeframe_07_days
        AutoSyncTimeframe.DAYS_14 -> R.string.auto_sync_timeframe_14_days
        AutoSyncTimeframe.DAYS_30 -> R.string.auto_sync_timeframe_30_days
    }
}
