package com.mydeck.app.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mydeck.app.R
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflineImageStorageLimit
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyDefaults
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.BatchArticleLoadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val settingsDataStore: SettingsDataStore,
    private val fullSyncUseCase: FullSyncUseCase,
    private val loadBookmarksUseCase: LoadBookmarksUseCase,
    private val contentPackageManager: ContentPackageManager,
    private val contentSyncPolicyEvaluator: OfflinePolicyEvaluator,
    private val connectivityMonitor: ConnectivityMonitor,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val dateFormat = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT
    )

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    private val bookmarkSyncFrequency = MutableStateFlow(AutoSyncTimeframe.HOURS_01)
    private val offlineReadingEnabled = MutableStateFlow(false)
    private val offlinePolicy = MutableStateFlow(OfflinePolicy.STORAGE_LIMIT)
    private val offlinePolicyStorageLimit = MutableStateFlow(OfflineImageStorageLimit.MB_100)
    private val offlinePolicyNewestN = MutableStateFlow(OfflinePolicyDefaults.NEWEST_N)
    private val offlinePolicyDateRangeWindow = MutableStateFlow(OfflinePolicyDefaults.DATE_RANGE_WINDOW)
    private val offlineMaxStorageCap = MutableStateFlow(OfflineImageStorageLimit.UNLIMITED)
    private val offlineContentScope = MutableStateFlow(OfflineContentScope.MY_LIST)
    private val wifiOnly = MutableStateFlow(false)
    private val allowBatterySaver = MutableStateFlow(true)
    private val showDialog = MutableStateFlow<SyncSettingsDialog?>(null)
    private val offlineStorageSize = MutableStateFlow<String?>(null)
    private val isPurgingOfflineContent = MutableStateFlow(false)

    private val detailedSyncStatus = bookmarkDao.observeDetailedSyncStatus()
        .map { counts ->
            val result = counts ?: BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0, 0)
            refreshStorageSize()
            result
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0, 0)
        )

    private val workInfoNext = fullSyncUseCase.workInfoFlow.map { workInfoList ->
        workInfoList.firstOrNull()?.let {
            if (it.state == WorkInfo.State.ENQUEUED) it.nextScheduleTimeMillis else null
        }
    }
    private val bookmarkSyncRunning = fullSyncUseCase.syncIsRunning
    private val batchContentSyncWorkInfos = workManager
        .getWorkInfosForUniqueWorkFlow(BatchArticleLoadWorker.UNIQUE_WORK_NAME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val lastSyncTimestampText = settingsDataStore.lastSyncTimestampFlow
        .map { formatStoredTimestamp(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val lastContentSyncTimestampText = settingsDataStore.lastContentSyncTimestampFlow
        .map { formatStoredTimestamp(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val contentSyncStatusRes = combine(
        offlineReadingEnabled,
        batchContentSyncWorkInfos,
        connectivityMonitor.observeConnectivity().onStart {
            emit(connectivityMonitor.isNetworkAvailable())
        }
    ) { args: Array<Any?> ->
        val offlineEnabled = args[0] as Boolean
        val workInfos = args[1] as List<WorkInfo>
        resolveContentSyncStatus(offlineEnabled, workInfos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            bookmarkSyncFrequency.value = settingsDataStore.getAutoSyncTimeframe().let { timeframe ->
                if (timeframe == AutoSyncTimeframe.MANUAL) AutoSyncTimeframe.HOURS_01 else timeframe
            }
            offlineReadingEnabled.value = settingsDataStore.isOfflineReadingEnabled()
            offlinePolicy.value = settingsDataStore.getOfflinePolicy()
            offlinePolicyStorageLimit.value =
                settingsDataStore.getOfflinePolicyStorageLimit().toStorageLimit()
            offlinePolicyNewestN.value = settingsDataStore.getOfflinePolicyNewestN()
            offlinePolicyDateRangeWindow.value = settingsDataStore.getOfflinePolicyDateRangeWindow()
            offlineMaxStorageCap.value = settingsDataStore.getOfflineMaxStorageCap().toStorageLimit()
            offlineContentScope.value = settingsDataStore.getOfflineContentScope()

            val constraints = settingsDataStore.getContentSyncConstraints()
            wifiOnly.value = constraints.wifiOnly
            allowBatterySaver.value = constraints.allowOnBatterySaver

            refreshStorageSize()
            performSettingsMigration()
        }
    }

    private suspend fun performSettingsMigration() {
        val migrationKey = "sync_settings_v3_migrated"
        val prefs = context.getSharedPreferences("sync_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean(migrationKey, false)) return

        val wasAutoSyncEnabled = settingsDataStore.isAutoSyncEnabled()
        if (!wasAutoSyncEnabled) {
            settingsDataStore.saveOfflineReadingEnabled(false)
            offlineReadingEnabled.value = false
        }

        val timeframe = settingsDataStore.getAutoSyncTimeframe()
        if (timeframe != AutoSyncTimeframe.MANUAL) {
            fullSyncUseCase.scheduleFullSyncWorker(timeframe)
        } else {
            settingsDataStore.saveAutoSyncTimeframe(AutoSyncTimeframe.HOURS_01)
            fullSyncUseCase.scheduleFullSyncWorker(AutoSyncTimeframe.HOURS_01)
            bookmarkSyncFrequency.value = AutoSyncTimeframe.HOURS_01
        }

        prefs.edit().putBoolean(migrationKey, true).apply()
        Timber.i("Sync settings v3 migration completed")
    }

    val uiState: StateFlow<SyncSettingsUiState> = combine(
        bookmarkSyncFrequency,
        showDialog,
        workInfoNext,
        bookmarkSyncRunning,
        offlineReadingEnabled,
        offlinePolicy,
        offlinePolicyStorageLimit,
        offlinePolicyNewestN,
        offlinePolicyDateRangeWindow,
        offlineMaxStorageCap,
        offlineContentScope,
        wifiOnly,
        allowBatterySaver,
        detailedSyncStatus,
        contentSyncStatusRes,
        lastSyncTimestampText,
        lastContentSyncTimestampText,
        offlineStorageSize,
        isPurgingOfflineContent
    ) { args: Array<Any?> ->
        val detailedSyncStatusCounts = args[13] as BookmarkDao.DetailedSyncStatusCounts
        val totalBookmarks = detailedSyncStatusCounts.total
        val archivedBookmarks = detailedSyncStatusCounts.archived
        val myListBookmarks = (totalBookmarks - archivedBookmarks).coerceAtLeast(0)
        val favorites = detailedSyncStatusCounts.favorites

        SyncSettingsUiState(
            bookmarkSyncFrequency = args[0] as AutoSyncTimeframe,
            bookmarkSyncFrequencyOptions = getBookmarkSyncOptions(args[0] as AutoSyncTimeframe),
            nextAutoSyncRun = (args[2] as Long?)?.let { dateFormat.format(Date(it)) },
            isBookmarkSyncRunning = args[3] as Boolean,
            offlineReadingEnabled = args[4] as Boolean,
            offlinePolicy = args[5] as OfflinePolicy,
            offlinePolicyStorageLimit = args[6] as OfflineImageStorageLimit,
            offlinePolicyNewestN = args[7] as Int,
            offlinePolicyDateRangeWindow = args[8] as Duration,
            offlineMaxStorageCap = args[9] as OfflineImageStorageLimit,
            includeArchivedBookmarks = (args[10] as OfflineContentScope).includesArchived,
            wifiOnly = args[11] as Boolean,
            allowBatterySaver = args[12] as Boolean,
            contentSyncStatusRes = args[14] as Int?,
            syncStatus = SyncStatus(
                totalBookmarks = totalBookmarks,
                myListBookmarks = myListBookmarks,
                archivedBookmarks = archivedBookmarks,
                favorites = favorites,
                fullOfflineAvailable = detailedSyncStatusCounts.contentDownloaded,
                lastBookmarkSyncTimestamp = args[15] as String?,
                lastOfflineMaintenanceTimestamp = args[16] as String?,
                offlineStorageSize = args[17] as String?
            ),
            showDialog = args[1] as SyncSettingsDialog?,
            isPurgingOfflineContent = args[18] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncSettingsUiState()
    )

    fun onClickBookmarkSyncFrequency() {
        showDialog.value = SyncSettingsDialog.BookmarkSyncFrequencyDialog
    }

    fun onClickOfflinePolicyStorageLimit() {
        showDialog.value = SyncSettingsDialog.OfflineStorageLimitDialog
    }

    fun onClickOfflinePolicyNewestN() {
        showDialog.value = SyncSettingsDialog.OfflineNewestNDialog
    }

    fun onClickOfflinePolicyDateRangeWindow() {
        showDialog.value = SyncSettingsDialog.OfflineDateRangeWindowDialog
    }

    fun onClickOfflineMaxStorageCap() {
        showDialog.value = SyncSettingsDialog.OfflineMaxStorageCapDialog
    }

    fun onBookmarkSyncFrequencySelected(selected: AutoSyncTimeframe) {
        val effective = if (selected == AutoSyncTimeframe.MANUAL) AutoSyncTimeframe.HOURS_01 else selected
        fullSyncUseCase.scheduleFullSyncWorker(effective)
        viewModelScope.launch {
            settingsDataStore.saveAutoSyncTimeframe(effective)
            bookmarkSyncFrequency.value = effective
        }
    }

    fun onClickSyncBookmarksNow() {
        fullSyncUseCase.performForcedFullSync()
    }

    fun onOfflineReadingChanged(enabled: Boolean) {
        viewModelScope.launch {
            if (isPurgingOfflineContent.value) {
                return@launch
            }
            settingsDataStore.saveOfflineReadingEnabled(enabled)
            offlineReadingEnabled.value = enabled

            if (enabled) {
                val includeArchived = offlineContentScope.value.includesArchived
                bookmarkDao.markLegacyCachedContentDirtyWithoutPackage(includeArchived)
                restartManagedContentSyncIfNeeded()
                Timber.i("Managed offline reading enabled (includeArchived=$includeArchived)")
            } else {
                runManagedContentPurge("Managed offline reading disabled and offline content purged")
            }
        }
    }

    fun onOfflinePolicySelected(policy: OfflinePolicy) {
        viewModelScope.launch {
            settingsDataStore.saveOfflinePolicy(policy)
            offlinePolicy.value = policy
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onOfflinePolicyStorageLimitSelected(limit: OfflineImageStorageLimit) {
        viewModelScope.launch {
            settingsDataStore.saveOfflinePolicyStorageLimit(limit.bytes)
            settingsDataStore.saveOfflineImageStorageLimit(limit)
            offlinePolicyStorageLimit.value = limit
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onOfflinePolicyNewestNSelected(newestN: Int) {
        viewModelScope.launch {
            settingsDataStore.saveOfflinePolicyNewestN(newestN)
            offlinePolicyNewestN.value = newestN
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onOfflinePolicyDateRangeWindowSelected(window: Duration) {
        viewModelScope.launch {
            settingsDataStore.saveOfflinePolicyDateRangeWindow(window)
            offlinePolicyDateRangeWindow.value = window
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onOfflineMaxStorageCapSelected(limit: OfflineImageStorageLimit) {
        viewModelScope.launch {
            settingsDataStore.saveOfflineMaxStorageCap(limit.bytes)
            offlineMaxStorageCap.value = limit
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onIncludeArchivedChanged(enabled: Boolean) {
        viewModelScope.launch {
            val scope = if (enabled) {
                OfflineContentScope.MY_LIST_AND_ARCHIVED
            } else {
                OfflineContentScope.MY_LIST
            }
            settingsDataStore.saveOfflineContentScope(scope)
            offlineContentScope.value = scope

            if (!enabled) {
                purgeArchivedOfflineContent()
            }

            if (offlineReadingEnabled.value) {
                bookmarkDao.markLegacyCachedContentDirtyWithoutPackage(enabled)
                restartManagedContentSyncIfNeeded()
            }

            refreshStorageSize()
        }
    }

    fun onWifiOnlyChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveWifiOnly(enabled)
            wifiOnly.value = enabled
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onAllowBatterySaverChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveAllowBatterySaver(enabled)
            allowBatterySaver.value = enabled
            restartManagedContentSyncIfNeeded()
        }
    }

    fun onClickClearOfflineContent() {
        showDialog.value = SyncSettingsDialog.ClearOfflineContentDialog
    }

    fun onConfirmClearOfflineContent() {
        showDialog.value = null
        viewModelScope.launch {
            runManagedContentPurge("All offline content cleared by user")
        }
    }

    fun onDismissDialog() {
        showDialog.value = null
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null }
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    private suspend fun purgeArchivedOfflineContent() {
        val archivedIds = bookmarkDao.getArchivedBookmarkIdsWithOfflinePackage()
        archivedIds.forEach { bookmarkId ->
            contentPackageManager.deleteContentForBookmark(bookmarkId)
        }
        if (archivedIds.isNotEmpty()) {
            Timber.i("Purged offline content for ${archivedIds.size} archived bookmarks")
        }
    }

    private suspend fun purgeManagedOfflineContent() {
        val offlinePackageIds = bookmarkDao.getBookmarkIdsWithOfflinePackages()
        offlinePackageIds.forEach { bookmarkId ->
            contentPackageManager.deleteContentForBookmark(bookmarkId)
        }
    }

    private suspend fun runManagedContentPurge(successLog: String) {
        isPurgingOfflineContent.value = true
        try {
            workManager.cancelUniqueWork(BatchArticleLoadWorker.UNIQUE_WORK_NAME)
            workManager.cancelAllWorkByTag(BatchArticleLoadWorker.WORK_TAG_OFFLINE_CONTENT)
            purgeManagedOfflineContent()
            refreshStorageSize()
            Timber.i(successLog)
        } finally {
            isPurgingOfflineContent.value = false
        }
    }

    private suspend fun restartManagedContentSyncIfNeeded() {
        if (!offlineReadingEnabled.value) {
            return
        }
        workManager.cancelUniqueWork(BatchArticleLoadWorker.UNIQUE_WORK_NAME)
        workManager.cancelAllWorkByTag(BatchArticleLoadWorker.WORK_TAG_OFFLINE_CONTENT)
        loadBookmarksUseCase.enqueueContentSyncIfNeeded()
    }

    private suspend fun resolveContentSyncStatus(
        offlineEnabled: Boolean,
        workInfos: List<WorkInfo>
    ): Int? {
        if (!offlineEnabled) {
            return null
        }
        val decision = contentSyncPolicyEvaluator.canFetchContent()
        if (!decision.allowed) {
            return when (decision.blockedReason) {
                "Wi-Fi required" -> R.string.sync_content_waiting_wifi
                "Battery saver active" -> R.string.sync_content_waiting_battery
                else -> null
            }
        }

        val hasActiveContentSync = workInfos.any {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }

        return if (hasActiveContentSync) {
            R.string.sync_content_status_downloading_text
        } else {
            R.string.sync_content_status_up_to_date
        }
    }

    private fun refreshStorageSize() {
        viewModelScope.launch {
            val totalBytes = contentPackageManager.calculateManagedOfflineSize()
            offlineStorageSize.value = formatFileSize(totalBytes)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun formatStoredTimestamp(timestamp: String?): String? {
        return timestamp?.let {
            runCatching {
                dateFormat.format(Date(kotlinx.datetime.Instant.parse(it).toEpochMilliseconds()))
            }.getOrNull()
        }
    }

    private fun getBookmarkSyncOptions(selected: AutoSyncTimeframe): List<AutoSyncTimeframeOption> {
        return AutoSyncTimeframe.entries.filter { it != AutoSyncTimeframe.MANUAL }.map {
            AutoSyncTimeframeOption(
                autoSyncTimeframe = it,
                label = it.toLabelResource(),
                selected = it == selected
            )
        }
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }
}

@Immutable
data class SyncSettingsUiState(
    val bookmarkSyncFrequency: AutoSyncTimeframe = AutoSyncTimeframe.HOURS_01,
    val bookmarkSyncFrequencyOptions: List<AutoSyncTimeframeOption> = emptyList(),
    val nextAutoSyncRun: String? = null,
    val isBookmarkSyncRunning: Boolean = false,
    val offlineReadingEnabled: Boolean = false,
    val offlinePolicy: OfflinePolicy = OfflinePolicy.STORAGE_LIMIT,
    val offlinePolicyStorageLimit: OfflineImageStorageLimit = OfflineImageStorageLimit.MB_100,
    val offlinePolicyNewestN: Int = OfflinePolicyDefaults.NEWEST_N,
    val offlinePolicyDateRangeWindow: Duration = OfflinePolicyDefaults.DATE_RANGE_WINDOW,
    val offlineMaxStorageCap: OfflineImageStorageLimit = OfflineImageStorageLimit.UNLIMITED,
    val includeArchivedBookmarks: Boolean = false,
    val wifiOnly: Boolean = false,
    val allowBatterySaver: Boolean = true,
    val contentSyncStatusRes: Int? = null,
    val syncStatus: SyncStatus = SyncStatus(),
    val showDialog: SyncSettingsDialog? = null,
    val isPurgingOfflineContent: Boolean = false
)

@Immutable
data class SyncStatus(
    val totalBookmarks: Int = 0,
    val myListBookmarks: Int = 0,
    val archivedBookmarks: Int = 0,
    val favorites: Int = 0,
    val fullOfflineAvailable: Int = 0,
    val lastBookmarkSyncTimestamp: String? = null,
    val lastOfflineMaintenanceTimestamp: String? = null,
    val offlineStorageSize: String? = null
)

enum class SyncSettingsDialog {
    BookmarkSyncFrequencyDialog,
    OfflineStorageLimitDialog,
    OfflineNewestNDialog,
    OfflineDateRangeWindowDialog,
    OfflineMaxStorageCapDialog,
    ClearOfflineContentDialog
}

data class AutoSyncTimeframeOption(
    val autoSyncTimeframe: AutoSyncTimeframe,
    @StringRes
    val label: Int,
    val selected: Boolean
)

data class IntSelectionOption(
    val value: Int,
    @StringRes
    val label: Int
)

data class DurationSelectionOption(
    val value: Duration,
    @StringRes
    val label: Int
)

fun offlineNewestNOptions(): List<IntSelectionOption> {
    return listOf(
        IntSelectionOption(25, R.string.sync_offline_newest_n_25),
        IntSelectionOption(50, R.string.sync_offline_newest_n_50),
        IntSelectionOption(100, R.string.sync_offline_newest_n_100),
        IntSelectionOption(200, R.string.sync_offline_newest_n_200),
        IntSelectionOption(500, R.string.sync_offline_newest_n_500)
    )
}

fun offlineDateRangeWindowOptions(): List<DurationSelectionOption> {
    return listOf(
        DurationSelectionOption(7.days, R.string.sync_offline_date_range_1_week),
        DurationSelectionOption(30.days, R.string.sync_offline_date_range_1_month),
        DurationSelectionOption(90.days, R.string.sync_offline_date_range_3_months),
        DurationSelectionOption(180.days, R.string.sync_offline_date_range_6_months),
        DurationSelectionOption(365.days, R.string.sync_offline_date_range_1_year)
    )
}

private fun Long.toStorageLimit(): OfflineImageStorageLimit {
    return OfflineImageStorageLimit.entries.firstOrNull { it.bytes == this }
        ?: OfflineImageStorageLimit.UNLIMITED
}

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

@StringRes
fun OfflineImageStorageLimit.toLabelResource(): Int {
    return when (this) {
        OfflineImageStorageLimit.MB_100 -> R.string.sync_offline_image_limit_100_mb
        OfflineImageStorageLimit.MB_250 -> R.string.sync_offline_image_limit_250_mb
        OfflineImageStorageLimit.MB_500 -> R.string.sync_offline_image_limit_500_mb
        OfflineImageStorageLimit.GB_1 -> R.string.sync_offline_image_limit_1_gb
        OfflineImageStorageLimit.UNLIMITED -> R.string.sync_offline_image_limit_unlimited
    }
}
