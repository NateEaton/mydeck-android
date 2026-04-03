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
        DateFormat.MEDIUM, DateFormat.MEDIUM
    )

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    private val bookmarkSyncFrequency = MutableStateFlow(AutoSyncTimeframe.HOURS_01)
    private val offlineReadingEnabled = MutableStateFlow(false)
    private val offlineContentScope = MutableStateFlow(OfflineContentScope.MY_LIST)
    private val offlineImageStorageLimit = MutableStateFlow(com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_500)
    private val wifiOnly = MutableStateFlow(false)
    private val allowBatterySaver = MutableStateFlow(true)
    private val showDialog = MutableStateFlow<SyncSettingsDialog?>(null)
    private val textStorageSize = MutableStateFlow<String?>(null)
    private val imageStorageSize = MutableStateFlow<String?>(null)

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
        wifiOnly,
        allowBatterySaver,
        batchContentSyncWorkInfos,
        connectivityMonitor.observeConnectivity().onStart {
            emit(connectivityMonitor.isNetworkAvailable())
        }
    ) { args: Array<Any?> ->
        val offlineEnabled = args[0] as Boolean
        val wifiEnabled = args[1] as Boolean
        val batterySaverEnabled = args[2] as Boolean
        val workInfos = args[3] as List<WorkInfo>
        resolveContentSyncStatus(offlineEnabled, workInfos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            bookmarkSyncFrequency.value = settingsDataStore.getAutoSyncTimeframe().let { timeframe ->
                if (timeframe == AutoSyncTimeframe.MANUAL) AutoSyncTimeframe.HOURS_01 else timeframe
            }
            offlineReadingEnabled.value = settingsDataStore.isOfflineReadingEnabled()
            offlineContentScope.value = settingsDataStore.getOfflineContentScope()
            offlineImageStorageLimit.value = settingsDataStore.getOfflineImageStorageLimit()

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
        wifiOnly,
        bookmarkSyncRunning,
        offlineReadingEnabled
    ) { args: Array<Any?> ->
        val frequency = args[0] as AutoSyncTimeframe
        val dialog = args[1] as SyncSettingsDialog?
        val next = args[2] as Long?
        val wifi = args[3] as Boolean
        val syncRunning = args[4] as Boolean
        val offlineEnabled = args[5] as Boolean
        SyncSettingsPartial1(
            bookmarkSyncFrequency = frequency,
            showDialog = dialog,
            nextAutoSyncRun = next,
            wifiOnly = wifi,
            isBookmarkSyncRunning = syncRunning,
            offlineReadingEnabled = offlineEnabled
        )
    }.combine(
        combine(
            allowBatterySaver,
            offlineContentScope,
            offlineImageStorageLimit,
            detailedSyncStatus,
            contentSyncStatusRes
        ) { args: Array<Any?> ->
            val battery = args[0] as Boolean
            val scope = args[1] as OfflineContentScope
            val limit = args[2] as com.mydeck.app.domain.sync.OfflineImageStorageLimit
            val status = args[3] as BookmarkDao.DetailedSyncStatusCounts
            val contentStatus = args[4] as Int?
            SyncSettingsPartial2(
                allowBatterySaver = battery,
                offlineContentScope = scope,
                offlineImageStorageLimit = limit,
                detailedSyncStatus = status,
                contentSyncStatusRes = contentStatus
            )
        }
    ) { p1, p2 ->
        val myListCount = (p2.detailedSyncStatus.total - p2.detailedSyncStatus.archived).coerceAtLeast(0)
        SyncSettingsUiState(
            bookmarkSyncFrequency = p1.bookmarkSyncFrequency,
            bookmarkSyncFrequencyOptions = getBookmarkSyncOptions(p1.bookmarkSyncFrequency),
            nextAutoSyncRun = p1.nextAutoSyncRun?.let { dateFormat.format(Date(it)) },
            isBookmarkSyncRunning = p1.isBookmarkSyncRunning,
            offlineReadingEnabled = p1.offlineReadingEnabled,
            offlineContentScope = p2.offlineContentScope,
            offlineImageStorageLimit = p2.offlineImageStorageLimit,
            wifiOnly = p1.wifiOnly,
            allowBatterySaver = p2.allowBatterySaver,
            contentSyncStatusRes = p2.contentSyncStatusRes,
            syncStatus = SyncStatus(
                totalBookmarks = p2.detailedSyncStatus.total,
                unread = myListCount,
                archived = p2.detailedSyncStatus.archived,
                favorites = p2.detailedSyncStatus.favorites,
                contentDownloaded = p2.detailedSyncStatus.contentDownloaded,
                contentAvailable = p2.detailedSyncStatus.contentAvailable,
                contentDirty = p2.detailedSyncStatus.contentDirty,
                permanentNoContent = p2.detailedSyncStatus.permanentNoContent
            ),
            showDialog = p1.showDialog
        )
    }.combine(lastSyncTimestampText) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastBookmarkSyncTimestamp = ts))
    }.combine(lastContentSyncTimestampText) { state, ts ->
        state.copy(syncStatus = state.syncStatus.copy(lastContentSyncTimestamp = ts))
    }.combine(textStorageSize) { state, size ->
        state.copy(syncStatus = state.syncStatus.copy(textStorageSize = size))
    }.combine(imageStorageSize) { state, size ->
        state.copy(syncStatus = state.syncStatus.copy(imageStorageSize = size))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncSettingsUiState()
    )

    fun onClickBookmarkSyncFrequency() {
        showDialog.value = SyncSettingsDialog.BookmarkSyncFrequencyDialog
    }

    fun onClickOfflineContentScope() {
        showDialog.value = SyncSettingsDialog.OfflineContentScopeDialog
    }

    fun onClickOfflineImageStorageLimit() {
        showDialog.value = SyncSettingsDialog.OfflineImageStorageLimitDialog
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
            settingsDataStore.saveOfflineReadingEnabled(enabled)
            offlineReadingEnabled.value = enabled

            if (enabled) {
                val includeArchived = offlineContentScope.value.includesArchived
                bookmarkDao.markLegacyCachedContentDirtyWithoutPackage(includeArchived)
                restartManagedContentSyncIfNeeded()
                Timber.i("Managed offline reading enabled (includeArchived=$includeArchived)")
            } else {
                workManager.cancelUniqueWork(BatchArticleLoadWorker.UNIQUE_WORK_NAME)
                purgeManagedOfflineContent()
                refreshStorageSize()
                Timber.i("Managed offline reading disabled and offline content purged")
            }
        }
    }

    fun onOfflineContentScopeSelected(scope: OfflineContentScope) {
        viewModelScope.launch {
            settingsDataStore.saveOfflineContentScope(scope)
            offlineContentScope.value = scope

            if (!scope.includesArchived) {
                purgeArchivedOfflineContent()
            }

            if (offlineReadingEnabled.value) {
                bookmarkDao.markLegacyCachedContentDirtyWithoutPackage(scope.includesArchived)
                restartManagedContentSyncIfNeeded()
            }

            refreshStorageSize()
        }
    }

    fun onOfflineImageStorageLimitSelected(limit: com.mydeck.app.domain.sync.OfflineImageStorageLimit) {
        viewModelScope.launch {
            settingsDataStore.saveOfflineImageStorageLimit(limit)
            offlineImageStorageLimit.value = limit
            restartManagedContentSyncIfNeeded()
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
            purgeManagedOfflineContent()
            refreshStorageSize()
            Timber.i("All offline content cleared by user")
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

    private suspend fun restartManagedContentSyncIfNeeded() {
        if (!offlineReadingEnabled.value) {
            return
        }
        workManager.cancelUniqueWork(BatchArticleLoadWorker.UNIQUE_WORK_NAME)
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
            val textBytes = contentPackageManager.calculateManagedOfflineTextSize()
            val imageBytes = contentPackageManager.calculateImageSize()
            imageStorageSize.value = formatFileSize(imageBytes)
            textStorageSize.value = formatFileSize(textBytes)
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

    private data class SyncSettingsPartial1(
        val bookmarkSyncFrequency: AutoSyncTimeframe,
        val showDialog: SyncSettingsDialog?,
        val nextAutoSyncRun: Long?,
        val wifiOnly: Boolean,
        val isBookmarkSyncRunning: Boolean,
        val offlineReadingEnabled: Boolean
    )

    private data class SyncSettingsPartial2(
        val allowBatterySaver: Boolean,
        val offlineContentScope: OfflineContentScope,
        val offlineImageStorageLimit: com.mydeck.app.domain.sync.OfflineImageStorageLimit,
        val detailedSyncStatus: BookmarkDao.DetailedSyncStatusCounts,
        val contentSyncStatusRes: Int?
    )

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
    val offlineContentScope: OfflineContentScope = OfflineContentScope.MY_LIST,
    val offlineImageStorageLimit: com.mydeck.app.domain.sync.OfflineImageStorageLimit = com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_500,
    val wifiOnly: Boolean = false,
    val allowBatterySaver: Boolean = true,
    val contentSyncStatusRes: Int? = null,
    val syncStatus: SyncStatus = SyncStatus(),
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
    val textStorageSize: String? = null,
    val imageStorageSize: String? = null
)

enum class SyncSettingsDialog {
    BookmarkSyncFrequencyDialog,
    OfflineContentScopeDialog,
    OfflineImageStorageLimitDialog,
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

@StringRes
fun com.mydeck.app.domain.sync.OfflineImageStorageLimit.toLabelResource(): Int {
    return when (this) {
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_5 -> R.string.sync_offline_image_limit_5_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_10 -> R.string.sync_offline_image_limit_10_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_20 -> R.string.sync_offline_image_limit_20_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_100 -> R.string.sync_offline_image_limit_100_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_250 -> R.string.sync_offline_image_limit_250_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.MB_500 -> R.string.sync_offline_image_limit_500_mb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.GB_1 -> R.string.sync_offline_image_limit_1_gb
        com.mydeck.app.domain.sync.OfflineImageStorageLimit.UNLIMITED -> R.string.sync_offline_image_limit_unlimited
    }
}
