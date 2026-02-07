package com.mydeck.app.ui.settings

import android.content.Context
import android.os.Build
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangeParams
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.DateRangeContentSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalPermissionsApi::class)
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val settingsDataStore: SettingsDataStore,
    private val fullSyncUseCase: FullSyncUseCase,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var _permissionState: PermissionState? = null
    private val dateFormat = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.MEDIUM
    )
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    // Bookmark sync
    private val bookmarkSyncFrequency = MutableStateFlow(AutoSyncTimeframe.HOURS_01)

    // Content sync
    private val contentSyncMode = MutableStateFlow(ContentSyncMode.AUTOMATIC)
    private val dateRangeFrom = MutableStateFlow<LocalDate?>(null)
    private val dateRangeTo = MutableStateFlow<LocalDate?>(null)
    private val isDateRangeDownloading = MutableStateFlow(false)

    // Constraints
    private val wifiOnly = MutableStateFlow(false)
    private val allowBatterySaver = MutableStateFlow(true)

    // Dialog
    private val showDialog = MutableStateFlow<SyncSettingsDialog?>(null)

    // Last sync timestamps
    private val lastSyncTimestamp = MutableStateFlow<String?>(null)
    private val lastContentSyncTimestamp = MutableStateFlow<String?>(null)

    // Sync status from DB
    private val detailedSyncStatus = bookmarkDao.observeDetailedSyncStatus()
        .map { it ?: BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0, 0) }
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
            settingsDataStore.getDateRangeParams()?.let {
                dateRangeFrom.value = it.from
                dateRangeTo.value = it.to
            }
            settingsDataStore.getLastSyncTimestamp()?.let {
                lastSyncTimestamp.value = dateFormat.format(Date(it.toEpochMilliseconds()))
            }
            settingsDataStore.getLastContentSyncTimestamp()?.let {
                lastContentSyncTimestamp.value = dateFormat.format(Date(it.toEpochMilliseconds()))
            }

            // Perform settings migration for existing users
            performSettingsMigration()
        }

        // Observe date range work status
        viewModelScope.launch {
            dateRangeWorkStatus.collect { running ->
                isDateRangeDownloading.value = running
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
    ) { freq, mode, dialog, next, wifi ->
        SyncSettingsPartial1(freq, mode, dialog, next, wifi)
    }.combine(
        combine(
            allowBatterySaver,
            dateRangeFrom,
            dateRangeTo,
            isDateRangeDownloading,
            detailedSyncStatus
        ) { battery, from, to, downloading, status ->
            SyncSettingsPartial2(battery, from, to, downloading, status)
        }
    ) { p1, p2 ->
        SyncSettingsUiState(
            bookmarkSyncFrequency = p1.freq,
            bookmarkSyncFrequencyOptions = getBookmarkSyncOptions(p1.freq),
            nextAutoSyncRun = p1.next?.let { dateFormat.format(Date(it)) },
            contentSyncMode = p1.mode,
            dateRangeFrom = p2.from,
            dateRangeTo = p2.to,
            isDateRangeDownloading = p2.downloading,
            wifiOnly = p1.wifi,
            allowBatterySaver = p2.battery,
            syncStatus = SyncStatus(
                totalBookmarks = p2.status.total,
                unread = p2.status.unread,
                archived = p2.status.archived,
                favorites = p2.status.favorites,
                contentDownloaded = p2.status.contentDownloaded,
                contentAvailable = p2.status.contentAvailable,
                contentDirty = p2.status.contentDirty,
                permanentNoContent = p2.status.permanentNoContent,
                lastSyncTimestamp = null // set below
            ),
            showDialog = p1.dialog
        )
    }.combine(lastSyncTimestamp) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastBookmarkSyncTimestamp = ts))
    }.combine(lastContentSyncTimestamp) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastContentSyncTimestamp = ts))
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

    // --- Content Sync ---

    fun onContentSyncModeSelected(mode: ContentSyncMode) {
        viewModelScope.launch {
            settingsDataStore.saveContentSyncMode(mode)
            contentSyncMode.value = mode

            // If switching to AUTOMATIC, may need notification permission
            if (mode == ContentSyncMode.AUTOMATIC) {
                requestBackgroundPermissionIfNeeded()
            }
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
            settingsDataStore.saveDateRangeParams(DateRangeParams(from, to))
        }
    }

    fun onClickDateRangeDownload() {
        val from = dateRangeFrom.value ?: return
        val to = dateRangeTo.value ?: return

        // Request permission if needed
        requestBackgroundPermissionIfNeeded()

        // Convert LocalDate to epoch millis for the worker
        val fromEpoch = from.toEpochDays().toLong() * 86400L * 1000L
        val toEpoch = (to.toEpochDays().toLong() + 1) * 86400L * 1000L // End of day

        val inputData = Data.Builder()
            .putLong(DateRangeContentSyncWorker.PARAM_FROM_EPOCH, fromEpoch)
            .putLong(DateRangeContentSyncWorker.PARAM_TO_EPOCH, toEpoch)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DateRangeContentSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            DateRangeContentSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Timber.i("Enqueued DateRangeContentSyncWorker [from=$from, to=$to]")
    }

    // --- Constraints ---

    fun onWifiOnlyChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveWifiOnly(enabled)
            wifiOnly.value = enabled
        }
    }

    fun onAllowBatterySaverChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveAllowBatterySaver(enabled)
            allowBatterySaver.value = enabled
        }
    }

    // --- Permission ---

    private fun requestBackgroundPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = _permissionState ?: return
        if (perm.status.isGranted) return

        if (perm.status.shouldShowRationale) {
            showDialog.value = SyncSettingsDialog.BackgroundRationaleDialog
        } else {
            showDialog.value = SyncSettingsDialog.PermissionRequest
        }
    }

    fun onRationaleDialogConfirm() {
        showDialog.value = SyncSettingsDialog.PermissionRequest
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

    @OptIn(ExperimentalPermissionsApi::class)
    fun setPermissionState(permissionState: PermissionState) {
        _permissionState = permissionState
    }

    // Internal data classes for combine
    private data class SyncSettingsPartial1(
        val freq: AutoSyncTimeframe,
        val mode: ContentSyncMode,
        val dialog: SyncSettingsDialog?,
        val next: Long?,
        val wifi: Boolean
    )

    private data class SyncSettingsPartial2(
        val battery: Boolean,
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

    // Content sync
    val contentSyncMode: ContentSyncMode = ContentSyncMode.AUTOMATIC,
    val dateRangeFrom: LocalDate? = null,
    val dateRangeTo: LocalDate? = null,
    val isDateRangeDownloading: Boolean = false,

    // Constraints
    val wifiOnly: Boolean = false,
    val allowBatterySaver: Boolean = true,

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
    val lastContentSyncTimestamp: String? = null
)

enum class SyncSettingsDialog {
    BookmarkSyncFrequencyDialog,
    BackgroundRationaleDialog,
    PermissionRequest,
    DateFromPicker,
    DateToPicker
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
        AutoSyncTimeframe.HOURS_01 -> R.string.auto_sync_timeframe_01_hours
        AutoSyncTimeframe.HOURS_06 -> R.string.auto_sync_timeframe_06_hours
        AutoSyncTimeframe.HOURS_12 -> R.string.auto_sync_timeframe_12_hours
        AutoSyncTimeframe.DAYS_01 -> R.string.auto_sync_timeframe_01_days
        AutoSyncTimeframe.DAYS_07 -> R.string.auto_sync_timeframe_07_days
        AutoSyncTimeframe.DAYS_14 -> R.string.auto_sync_timeframe_14_days
        AutoSyncTimeframe.DAYS_30 -> R.string.auto_sync_timeframe_30_days
    }
}
