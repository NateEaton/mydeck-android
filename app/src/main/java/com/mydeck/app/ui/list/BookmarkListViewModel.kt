package com.mydeck.app.ui.list

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.BuildConfig
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkBatchUpdate
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.DrawerPreset
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.domain.model.SwipeConfig
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.content.ContentSource
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckNetworkPolicy
import com.mydeck.app.util.extractUrlAndTitle
import com.mydeck.app.util.formatBookmarkShareText
import com.mydeck.app.util.isValidUrl
import com.mydeck.app.util.MAX_TITLE_LENGTH
import com.mydeck.app.worker.BatchArticleLoadWorker
import com.mydeck.app.worker.CreateBookmarkWorker
import com.mydeck.app.worker.LoadBookmarksWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

data class MultiSelectState(
    val active: Boolean = false,
    val selectedIds: Set<String> = emptySet()
) {
    val selectedCount: Int get() = selectedIds.size
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
    fun isSelected(bookmarkId: String): Boolean = bookmarkId in selectedIds
}

data class MultiSelectTargets(
    val allVisibleSelected: Boolean = false,
    val selectedAllFavorited: Boolean = false,
    val selectedAllUnfavorited: Boolean = false,
    val selectedAllArchived: Boolean = false,
    val selectedAllUnarchived: Boolean = false,
    // All selected items are already pinned (offline). Drives the contextual Pin↔Unpin label.
    val selectedAllPinned: Boolean = false
)

sealed class BatchActionSnackbarEvent {
    abstract val count: Int

    // Favorite/archive events carry the ids actually changed (skip-no-op filtered out) so
    // Undo can revert exactly those items back to their prior state in a single transaction.
    data class FavoritesAdded(override val count: Int, val changedIds: List<String>) : BatchActionSnackbarEvent()
    data class FavoritesRemoved(override val count: Int, val changedIds: List<String>) : BatchActionSnackbarEvent()
    data class Archived(override val count: Int, val changedIds: List<String>) : BatchActionSnackbarEvent()
    data class Unarchived(override val count: Int, val changedIds: List<String>) : BatchActionSnackbarEvent()

    // Labels are applied optimistically (like favorite/archive); Undo restores each changed
    // bookmark's prior label list, so the event carries those snapshots.
    data class LabelsAdded(
        override val count: Int,
        val priorLabelsByBookmark: Map<String, List<String>>
    ) : BatchActionSnackbarEvent()

    // Delete is staged (not yet written); confirm/cancel act on the batch-pending set held in the ViewModel.
    data class Deleted(override val count: Int) : BatchActionSnackbarEvent()

    // Pin/Unpin (multi-select): informational only (no Undo — the toggle is its own inverse, and
    // selection mode stays open). pinnedCount = eligible items now pinned (downloading / flipped /
    // already pinned); skippedCount = ineligible items (no offline content). The two sum to the
    // selection size. Unpinned carries just the count demoted back to managed.
    data class PinnedOffline(
        val pinnedCount: Int,
        val skippedCount: Int
    ) : BatchActionSnackbarEvent() {
        override val count: Int get() = pinnedCount + skippedCount
    }

    data class Unpinned(override val count: Int) : BatchActionSnackbarEvent()
}

private data class SelectedBookmarkActionState(
    val id: String,
    val isMarked: Boolean,
    val isArchived: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val updateBookmarkUseCase: UpdateBookmarkUseCase,
    private val fullSyncUseCase: FullSyncUseCase,
    private val workManager: WorkManager,
    private val bookmarkRepository: BookmarkRepository,
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val savedStateHandle: SavedStateHandle,
    private val contentSyncPolicyEvaluator: OfflinePolicyEvaluator,
    private val contentPackageManager: ContentPackageManager,
    private val syncScheduler: com.mydeck.app.domain.sync.SyncScheduler,
    connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private enum class LoadTrigger {
        APP_OPEN,
        USER_REFRESH
    }

    val isOnline: StateFlow<Boolean> = connectivityMonitor.observeConnectivity()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent = _openUrlEvent.receiveAsFlow()
    private val _createBookmarkUiState = MutableStateFlow<CreateBookmarkUiState>(CreateBookmarkUiState.Closed)
    val createBookmarkUiState = _createBookmarkUiState.asStateFlow()

    private val _layoutMode = MutableStateFlow(LayoutMode.GRID)
    val layoutMode = _layoutMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.ADDED_NEWEST)
    val sortOption = _sortOption.asStateFlow()

    private val _shareIntent = MutableStateFlow<Intent?>(null)
    val shareIntent = _shareIntent.asStateFlow()

    // Pending deletion IDs tracked per staged snackbar so rapid deletes keep item identity.
    private val _pendingDeletionBookmarkIds = MutableStateFlow<List<String>>(emptyList())
    val pendingDeletionBookmarkIds = _pendingDeletionBookmarkIds.asStateFlow()

    // Batch (multi-select) pending deletion kept separate from single-item pending deletion so the
    // batch confirms/cancels atomically and Undo restores exactly the staged batch set.
    private val _pendingBatchDeletionBookmarkIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingBatchDeletionBookmarkIds = _pendingBatchDeletionBookmarkIds.asStateFlow()

    private val _multiSelectState = MutableStateFlow(MultiSelectState())
    val multiSelectState = _multiSelectState.asStateFlow()

    // Add-labels picker: holds the selection captured when the picker opened, so selection-mode
    // changes while the picker is open do not affect which bookmarks get the labels.
    private val _addLabelsPickerTargetIds = MutableStateFlow<List<String>>(emptyList())
    val addLabelsPickerTargetIds = _addLabelsPickerTargetIds.asStateFlow()

    private val _showAddLabelsPicker = MutableStateFlow(false)
    val showAddLabelsPicker = _showAddLabelsPicker.asStateFlow()

    // Constraint feedback: one-shot snackbar message when content sync is blocked
    private val _constraintSnackbarEvent = Channel<Int>(Channel.BUFFERED)
    val constraintSnackbarEvent: Flow<Int> = _constraintSnackbarEvent.receiveAsFlow()

    // Constraint override dialog for user-initiated refresh.
    // Holds the @StringRes body text describing the specific blocked constraint, or null when dismissed.
    private val _constraintOverrideBodyRes = MutableStateFlow<Int?>(null)
    val constraintOverrideBodyRes = _constraintOverrideBodyRes.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = settingsDataStore.getLayoutMode()
            if (mode != null) {
                try {
                    _layoutMode.value = LayoutMode.valueOf(mode)
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing layout mode")
                }
            }
            val sort = settingsDataStore.getSortOption()
            if (sort != null) {
                 try {
                    _sortOption.value = SortOption.valueOf(sort)
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing sort option")
                }
            }
        }

         // Handle shared text if any
        savedStateHandle.get<String>("sharedText")?.let { sharedText ->
            openCreateBookmarkDialog(sharedText)
        }

        // Sync on app open (delta sync for deletions + incremental load for updates)
        viewModelScope.launch {
            val url = settingsDataStore.urlFlow.first()
            val syncOnAppOpenEnabled = settingsDataStore.isSyncOnAppOpenEnabled()
            if (ReadeckNetworkPolicy.isSavedHttpUrlBlocked(url, BuildConfig.ALLOW_INSECURE_HTTP)) {
                Timber.i("Skipping app-open sync because HTTP is blocked in this build")
                return@launch
            }
            if (!url.isNullOrBlank() && syncOnAppOpenEnabled) {
                loadBookmarks(LoadTrigger.APP_OPEN)
            }
        }
    }

    // --- Filter system state ---

    private val _drawerPreset = MutableStateFlow(DrawerPreset.MY_LIST)
    val drawerPreset = _drawerPreset.asStateFlow()

    private val _filterFormState = MutableStateFlow(FilterFormState.fromPreset(DrawerPreset.MY_LIST))
    val filterFormState = _filterFormState.asStateFlow()

    private val _activeLabel = MutableStateFlow<String?>(null)
    val activeLabel = _activeLabel.asStateFlow()

    private val _isFilterSheetOpen = MutableStateFlow(false)
    val isFilterSheetOpen = _isFilterSheetOpen.asStateFlow()

    val labelsWithCounts: StateFlow<Map<String, Int>> = bookmarkRepository.observeAllLabelsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pendingActionCount: StateFlow<Int> = bookmarkRepository.observePendingActionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bookmarkCounts: StateFlow<BookmarkCounts> = bookmarkRepository.observeAllBookmarkCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarkCounts(0, 0, 0, 0))

    val swipeConfig: StateFlow<SwipeConfig> = settingsDataStore.swipeConfigFlow

    val syncFraction: StateFlow<Float?> = bookmarkRepository.syncProgress
        .map { progress ->
            if (progress is BookmarkRepository.BookmarkSyncProgress.Running) {
                progress.page / progress.totalPages.toFloat()
            } else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val loadBookmarksWorkInfos: StateFlow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val loadBookmarksIsRunning: StateFlow<Boolean> = loadBookmarksWorkInfos
        .map { workInfoList ->
            workInfoList.any {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _userRequestedRefresh = MutableStateFlow(false)

    val isInitialLoading: StateFlow<Boolean> = combine(
        loadBookmarksIsRunning,
        settingsDataStore.initialSyncPerformedFlow
    ) { isRunning, initialSyncPerformed ->
        isRunning && !initialSyncPerformed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isUserRefreshing: StateFlow<Boolean> = combine(
        loadBookmarksIsRunning,
        _userRequestedRefresh
    ) { isRunning, userRequestedRefresh ->
        isRunning && userRequestedRefresh
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val loadBookmarksHasFailed: StateFlow<Boolean> = loadBookmarksWorkInfos
        .map { workInfoList ->
            val hasLoadInProgress = workInfoList.any {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
            !hasLoadInProgress && workInfoList.any {
                it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    val multiSelectTargets: StateFlow<MultiSelectTargets> = combine(
        _multiSelectState,
        _uiState
    ) { selection, ui ->
        val bookmarks = (ui as? UiState.Success)?.bookmarks.orEmpty()
        val allVisibleSelected = bookmarks.isNotEmpty() &&
            bookmarks.all { it.id in selection.selectedIds }
        val selectedItems = bookmarks.filter { it.id in selection.selectedIds }
        val hasSelection = selectedItems.isNotEmpty()
        MultiSelectTargets(
            allVisibleSelected = allVisibleSelected,
            selectedAllFavorited = hasSelection && selectedItems.all { it.isMarked },
            selectedAllUnfavorited = hasSelection && selectedItems.none { it.isMarked },
            selectedAllArchived = hasSelection && selectedItems.all { it.isArchived },
            selectedAllUnarchived = hasSelection && selectedItems.none { it.isArchived },
            // Toggle decision considers only *pinnable* (eligible) items, so a no-content item in
            // the selection doesn't block Unpin once the eligible items are all pinned.
            selectedAllPinned = selectedItems.any { it.offlineEligible } &&
                selectedItems.filter { it.offlineEligible }
                    .all { it.offlineState == BookmarkListItem.OfflineState.PINNED }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MultiSelectTargets())

    private val _batchActionSnackbarEvent = Channel<BatchActionSnackbarEvent>(Channel.BUFFERED)
    val batchActionSnackbarEvent: Flow<BatchActionSnackbarEvent> =
        _batchActionSnackbarEvent.receiveAsFlow()

    // Drives the multi-select "Available offline" overflow item's enabled state: greyed out and
    // inactive when offline reading is disabled (hard line — no prompt-to-enable).
    val offlineReadingEnabled: StateFlow<Boolean> = settingsDataStore.offlineReadingEnabledFlow

    init {
        viewModelScope.launch {
            var wasRunning = false
            var hasCheckedConstraints = false
            loadBookmarksIsRunning.collectLatest { isRunning ->
                if (!isRunning && wasRunning) {
                    _userRequestedRefresh.value = false
                    // Check content sync constraints after app-open sync completes (once)
                    if (!hasCheckedConstraints) {
                        hasCheckedConstraints = true
                        checkContentSyncConstraints()
                    }
                }
                wasRunning = isRunning
            }
        }

        viewModelScope.launch {
            combine(
                _filterFormState,
                _activeLabel,
                _sortOption
            ) { filter, label, sort ->
                Triple(filter, label, sort)
            }.flatMapLatest { (filter, label, sort) ->
                if (label != null) {
                    // Label mode: show ALL bookmarks with that label, ignoring filter form
                    bookmarkRepository.observeFilteredBookmarkListItems(
                        label = label,
                        orderBy = sort.sqlOrderBy
                    )
                } else {
                    // Normal filter mode
                    bookmarkRepository.observeFilteredBookmarkListItems(
                        searchQuery = filter.search,
                        title = filter.title,
                        author = filter.author,
                        site = filter.site,
                        types = filter.types,
                        progressFilters = filter.progress,
                        isArchived = filter.isArchived,
                        isFavorite = filter.isFavorite,
                        label = filter.label,
                        fromDate = filter.fromDate?.toEpochMilliseconds(),
                        toDate = filter.toDate?.toEpochMilliseconds(),
                        isLoaded = filter.isLoaded,
                        withLabels = filter.withLabels,
                        withErrors = filter.withErrors,
                        minReadingTime = filter.minReadingTime,
                        maxReadingTime = filter.maxReadingTime,
                        includeNullReadingTime = filter.includeNullReadingTime,
                        minWordCount = filter.minWordCount,
                        maxWordCount = filter.maxWordCount,
                        includeNullWordCount = filter.includeNullWordCount,
                        orderBy = sort.sqlOrderBy
                    )
                }
            }.combine(settingsDataStore.initialSyncPerformedFlow) { visibleBookmarks, initialSyncPerformed ->
                visibleBookmarks to initialSyncPerformed
            }.combine(loadBookmarksHasFailed) { (visibleBookmarks, initialSyncPerformed), hasLoadFailed ->
                Triple(visibleBookmarks, initialSyncPerformed, hasLoadFailed)
            }.collectLatest { (visibleBookmarks, initialSyncPerformed, hasLoadFailed) ->
                dropSelectedIdsMissingFrom(visibleBookmarks)
                _uiState.update { currentState ->
                    val updateBookmarkState = (currentState as? UiState.Success)?.updateBookmarkState
                    when {
                        visibleBookmarks.isNotEmpty() -> UiState.Success(visibleBookmarks, updateBookmarkState)
                        !initialSyncPerformed && hasLoadFailed ->
                            UiState.Empty(R.string.list_view_empty_error_loading_bookmarks)
                        !initialSyncPerformed -> UiState.Loading
                        _activeLabel.value == null &&
                            _filterFormState.value == FilterFormState.fromPreset(DrawerPreset.MY_LIST) ->
                            UiState.Empty(R.string.list_view_empty_nothing_to_see)
                        else -> UiState.Success(visibleBookmarks, updateBookmarkState)
                    }
                }
            }
        }
    }

    // --- Drawer preset navigation ---

    fun onSelectDrawerPreset(preset: DrawerPreset) {
        clearMultiSelectState()
        _drawerPreset.value = preset
        _filterFormState.value = FilterFormState.fromPreset(preset)
        _activeLabel.value = null
    }

    fun onClickMyList() = onSelectDrawerPreset(DrawerPreset.MY_LIST)
    fun onClickArchive() = onSelectDrawerPreset(DrawerPreset.ARCHIVE)
    fun onClickFavorite() = onSelectDrawerPreset(DrawerPreset.FAVORITES)
    fun onClickArticles() = onSelectDrawerPreset(DrawerPreset.ARTICLES)
    fun onClickVideos() = onSelectDrawerPreset(DrawerPreset.VIDEOS)
    fun onClickPictures() = onSelectDrawerPreset(DrawerPreset.PICTURES)

    // --- Label mode ---

    fun onClickLabel(label: String) {
        clearMultiSelectState()
        if (_activeLabel.value == label) {
            // Toggle off label mode, return to previous drawer preset
            _activeLabel.value = null
        } else {
            _activeLabel.value = label
        }
    }

    private val _isLabelsSheetOpen = MutableStateFlow(false)
    val isLabelsSheetOpen = _isLabelsSheetOpen.asStateFlow()

    fun onOpenLabelsSheet() {
        _isLabelsSheetOpen.value = true
    }

    fun onCloseLabelsSheet() {
        _isLabelsSheetOpen.value = false
    }

    fun onRenameLabel(oldLabel: String, newLabel: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.renameLabel(oldLabel, newLabel)
                if (_activeLabel.value == oldLabel) {
                    _activeLabel.value = newLabel
                }
            } catch (e: Exception) {
                Timber.e(e, "Error renaming label")
            }
        }
    }

    fun onDeleteLabel(label: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.deleteLabel(label)
                if (_activeLabel.value == label) {
                    _activeLabel.value = null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting label")
            }
        }
    }

    // --- Filter sheet ---

    fun onOpenFilterSheet() {
        _isFilterSheetOpen.value = true
    }

    fun onCloseFilterSheet() {
        _isFilterSheetOpen.value = false
    }

    fun onApplyFilter(filterFormState: FilterFormState) {
        clearMultiSelectState()
        _filterFormState.value = filterFormState
        _isFilterSheetOpen.value = false
    }

    fun onResetFilter() {
        clearMultiSelectState()
        _filterFormState.value = FilterFormState.fromPreset(_drawerPreset.value)
        _isFilterSheetOpen.value = false
    }

    // --- Navigation ---

    fun onClickSettings() {
        Timber.d("onClickSettings")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToSettings) }
    }

    fun onClickAbout() {
        Timber.d("onClickAbout")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToAbout) }
    }

    fun onClickUserGuide() {
        Timber.d("onClickUserGuide")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToUserGuide) }
    }

    fun onClickBookmark(bookmarkId: String) {
        Timber.d("onClickBookmark")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToBookmarkDetail(bookmarkId)) }
    }

    fun onClickBookmarkOpenOriginal(bookmarkId: String) {
        Timber.d("onClickBookmarkOpenOriginal")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToBookmarkDetail(bookmarkId, showOriginal = true)) }
    }

    // No need for consume/reset methods with Channel

    private fun loadBookmarks(trigger: LoadTrigger, initialLoad: Boolean = false) {
        if (trigger == LoadTrigger.USER_REFRESH) {
            _userRequestedRefresh.value = true
        }
        viewModelScope.launch {
            try {
                when {
                    initialLoad -> LoadBookmarksWorker.enqueue(context, isInitialLoad = true)
                    trigger == LoadTrigger.APP_OPEN -> LoadBookmarksWorker.enqueue(
                        context,
                        trigger = LoadBookmarksWorker.Trigger.APP_OPEN
                    )
                    else -> LoadBookmarksWorker.enqueue(
                        context,
                        trigger = LoadBookmarksWorker.Trigger.PULL_TO_REFRESH
                    )
                }
            } catch (e: Exception) {
                // Handle errors (e.g., show error message)
                if (trigger == LoadTrigger.USER_REFRESH) {
                    _userRequestedRefresh.value = false
                }
                _uiState.value = UiState.Empty(R.string.list_view_empty_error_loading_bookmarks)
                Timber.e(e, "Error loading bookmarks: ${e.message}")
            }
        }
    }

    fun onClickShareBookmark(title: String, url: String) {
        viewModelScope.launch {
            val shareText = formatBookmarkShareText(
                title = title,
                url = url,
                format = settingsDataStore.getBookmarkShareFormat()
            )
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            _shareIntent.value = intent
        }
    }

    fun onShareIntentConsumed() {
        _shareIntent.value = null
    }

    fun onClickOpenInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error opening URL in browser: $url")
        }
    }

    fun onPullToRefresh() {
        loadBookmarks(LoadTrigger.USER_REFRESH)
        // Check if content sync constraints would block after user-initiated refresh
        viewModelScope.launch {
            if (!contentSyncPolicyEvaluator.shouldAutoFetchContent()) return@launch
            val decision = contentSyncPolicyEvaluator.canFetchContent()
            if (!decision.allowed) {
                val bodyRes = when (decision.blockedReason) {
                    "Wi-Fi required" -> R.string.sync_constraint_override_body_wifi
                    "Battery saver active" -> R.string.sync_constraint_override_body_battery
                    else -> return@launch // No network — don't show override dialog
                }
                _constraintOverrideBodyRes.value = bodyRes
            }
        }
    }

    fun onConstraintOverrideConfirmed() {
        _constraintOverrideBodyRes.value = null
        syncScheduler.scheduleBatchArticleLoadOverridingConstraints()
    }

    fun onConstraintOverrideCancelled() {
        _constraintOverrideBodyRes.value = null
    }

    /**
     * Check if auto content sync is on but constraints would block it,
     * and emit a one-shot snackbar event if so.
     */
    private suspend fun checkContentSyncConstraints() {
        if (!contentSyncPolicyEvaluator.shouldAutoFetchContent()) return
        val decision = contentSyncPolicyEvaluator.canFetchContent()
        if (decision.allowed) return

        val messageRes = when (decision.blockedReason) {
            "Wi-Fi required" -> R.string.sync_content_waiting_wifi
            "Battery saver active" -> R.string.sync_content_waiting_battery
            else -> return // Network unavailable — don't show constraint snackbar for that
        }
        _constraintSnackbarEvent.send(messageRes)
    }

    fun onDeleteBookmark(bookmarkId: String) {
        _pendingDeletionBookmarkIds.update { pendingIds ->
            if (bookmarkId in pendingIds) pendingIds else pendingIds + bookmarkId
        }
    }

    fun onConfirmDeleteBookmark(bookmarkId: String) {
        val wasPending = removePendingDeletion(bookmarkId)
        if (!wasPending) return

        viewModelScope.launch {
            try {
                updateBookmark {
                    updateBookmarkUseCase.deleteBookmark(bookmarkId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting bookmark: ${e.message}")
            }
        }
    }

    fun onCancelDeleteBookmark(bookmarkId: String) {
        removePendingDeletion(bookmarkId)
        Timber.d("Delete bookmark cancelled")
    }

    private fun removePendingDeletion(bookmarkId: String): Boolean {
        var removed = false
        _pendingDeletionBookmarkIds.update { pendingIds ->
            if (bookmarkId !in pendingIds) {
                pendingIds
            } else {
                removed = true
                pendingIds.filterNot { it == bookmarkId }
            }
        }
        return removed
    }

    fun onToggleMarkReadBookmark(bookmarkId: String, isRead: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsRead(
                bookmarkId = bookmarkId,
                isRead = isRead
            )
        }
    }

    fun onToggleFavoriteBookmark(bookmarkId: String, isFavorite: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsFavorite(
                bookmarkId = bookmarkId,
                isFavorite = isFavorite
            )
        }
    }

    fun onToggleArchiveBookmark(bookmarkId: String, isArchived: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsArchived(
                bookmarkId = bookmarkId,
                isArchived = isArchived
            )
        }
    }

    fun onEnterMultiSelectMode() {
        _multiSelectState.value = MultiSelectState(active = true)
    }

    fun onExitMultiSelectMode() {
        clearMultiSelectState()
    }

    fun onToggleBookmarkSelected(bookmarkId: String) {
        _multiSelectState.update { state ->
            if (!state.active) {
                state
            } else {
                val selectedIds = if (bookmarkId in state.selectedIds) {
                    state.selectedIds - bookmarkId
                } else {
                    state.selectedIds + bookmarkId
                }
                state.copy(selectedIds = selectedIds)
            }
        }
    }

    fun onFavoriteSelectedBookmarks() = applyBatchFavorite(targetFavorite = true)

    fun onUnfavoriteSelectedBookmarks() = applyBatchFavorite(targetFavorite = false)

    fun onArchiveSelectedBookmarks() = applyBatchArchive(targetArchived = true)

    fun onUnarchiveSelectedBookmarks() = applyBatchArchive(targetArchived = false)

    // Multi-select "Pin offline": pin every eligible selected bookmark (W2 MANUAL = pinned).
    // Keyed off the authoritative committed-package source (not the list icon), so an image-less
    // committed package pins correctly (offline-pinning spec §9):
    //   - no committed package → enqueue a priority download as MANUAL (the worker commits MANUAL)
    //   - AUTOMATIC package     → flip in place to MANUAL (no re-fetch)
    //   - MANUAL package        → already pinned, no-op
    // Ineligible items (no article/picture content, or PERMANENT_NO_CONTENT) are skipped and
    // surfaced in the snackbar. Selection mode stays open (favorite/archive parity).
    fun onPinSelection() {
        val targetIds = _multiSelectState.value.selectedIds.toList()
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            val constraints = buildContentSyncConstraints()
            var pinned = 0
            var skipped = 0
            for (id in targetIds) {
                val bookmark = runCatching { bookmarkRepository.getBookmarkById(id) }.getOrNull()
                if (bookmark == null || !isOfflineEligible(bookmark)) {
                    skipped++
                    continue
                }
                when (contentPackageManager.getPackageSource(id)) {
                    ContentSource.MANUAL -> { /* already pinned */ }
                    ContentSource.AUTOMATIC -> contentPackageManager.updatePackageSource(id, ContentSource.MANUAL)
                    null -> BatchArticleLoadWorker.enqueuePriorityDownload(
                        workManager, id, constraints, ContentSource.MANUAL
                    )
                }
                pinned++
            }
            _batchActionSnackbarEvent.trySend(
                BatchActionSnackbarEvent.PinnedOffline(pinnedCount = pinned, skippedCount = skipped)
            )
        }
    }

    // Multi-select "Unpin": demote every pinned (MANUAL) selected bookmark back to AUTOMATIC
    // (managed) — non-destructive; the content stays and becomes prunable again. Items that aren't
    // pinned are a no-op. The contextual menu only offers Unpin when all selected are already
    // pinned, so in practice every item demotes. Selection mode stays open.
    fun onUnpinSelection() {
        val targetIds = _multiSelectState.value.selectedIds.toList()
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            var unpinned = 0
            for (id in targetIds) {
                if (contentPackageManager.getPackageSource(id) == ContentSource.MANUAL) {
                    contentPackageManager.updatePackageSource(id, ContentSource.AUTOMATIC)
                    unpinned++
                }
            }
            _batchActionSnackbarEvent.trySend(BatchActionSnackbarEvent.Unpinned(unpinned))
        }
    }

    // Capture the current selection and open the add-labels picker. The captured ids drive the
    // apply, so toggling selection (or auto-exit) while the picker is open has no effect.
    fun onAddLabelsToSelection() {
        val selectedIds = _multiSelectState.value.selectedIds
        if (selectedIds.isEmpty()) return
        _addLabelsPickerTargetIds.value = selectedIds.toList()
        _showAddLabelsPicker.value = true
    }

    fun onDismissAddLabelsPicker() {
        _showAddLabelsPicker.value = false
        _addLabelsPickerTargetIds.value = emptyList()
    }

    fun onLabelsPicked(chosen: Set<String>) {
        val targetIds = _addLabelsPickerTargetIds.value
        onDismissAddLabelsPicker()
        if (chosen.isEmpty() || targetIds.isEmpty()) return

        val selectedCount = targetIds.size
        viewModelScope.launch {
            val priorByBookmark = updateBookmarkUseCase.addLabelsToBookmarks(targetIds, chosen.toList())
            _batchActionSnackbarEvent.trySend(
                BatchActionSnackbarEvent.LabelsAdded(selectedCount, priorByBookmark)
            )
        }
    }

    // Stage all selected bookmarks as batch-pending-delete: grey them out, leave selection mode, and
    // emit a Snackbar. No DB write happens here — it is deferred until the Snackbar is confirmed.
    fun onDeleteSelectedBookmarks() {
        val selectedIds = _multiSelectState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val staged = selectedIds.toSet()
        _pendingBatchDeletionBookmarkIds.value = staged
        clearMultiSelectState()
        _batchActionSnackbarEvent.trySend(BatchActionSnackbarEvent.Deleted(staged.size))
    }

    // Confirm path (Snackbar dismissed/timed out/interaction elsewhere): soft-delete the staged batch
    // locally and enqueue delete pending actions for sync, in one transaction.
    fun onConfirmBatchDeletion() {
        val ids = _pendingBatchDeletionBookmarkIds.value
        if (ids.isEmpty()) return

        _pendingBatchDeletionBookmarkIds.value = emptySet()
        updateBookmark {
            updateBookmarkUseCase.deleteBookmarks(ids.toList())
        }
    }

    // Undo path: restore the staged batch from pending-delete UI state without enqueuing any deletes.
    fun onCancelBatchDeletion() {
        _pendingBatchDeletionBookmarkIds.value = emptySet()
    }

    // Snackbar Undo for every batch action. Favorite/archive were applied optimistically, so Undo
    // reverts only the items actually changed; delete was merely staged, so Undo cancels the stage.
    fun onUndoBatchAction(event: BatchActionSnackbarEvent) {
        when (event) {
            is BatchActionSnackbarEvent.FavoritesAdded -> revertBatchFavorite(event.changedIds, revertTo = false)
            is BatchActionSnackbarEvent.FavoritesRemoved -> revertBatchFavorite(event.changedIds, revertTo = true)
            is BatchActionSnackbarEvent.Archived -> revertBatchArchive(event.changedIds, revertTo = false)
            is BatchActionSnackbarEvent.Unarchived -> revertBatchArchive(event.changedIds, revertTo = true)
            is BatchActionSnackbarEvent.LabelsAdded -> revertBatchLabels(event.priorLabelsByBookmark)
            is BatchActionSnackbarEvent.Deleted -> onCancelBatchDeletion()
            // Pin/Unpin are informational only — their snackbars offer no Undo (the toggle is the inverse).
            is BatchActionSnackbarEvent.PinnedOffline -> Unit
            is BatchActionSnackbarEvent.Unpinned -> Unit
        }
    }

    // Snackbar dismissal for every batch action. Only delete has deferred work to commit on dismiss;
    // favorite/archive are already applied, so dismissing is a no-op for them.
    fun onConfirmBatchAction(event: BatchActionSnackbarEvent) {
        if (event is BatchActionSnackbarEvent.Deleted) onConfirmBatchDeletion()
    }

    private fun revertBatchFavorite(changedIds: List<String>, revertTo: Boolean) {
        if (changedIds.isEmpty()) return
        updateBookmark {
            updateBookmarkUseCase.updateBookmarks(
                changedIds.map { BookmarkBatchUpdate(bookmarkId = it, isFavorite = revertTo) }
            )
        }
    }

    private fun revertBatchArchive(changedIds: List<String>, revertTo: Boolean) {
        if (changedIds.isEmpty()) return
        updateBookmark {
            updateBookmarkUseCase.updateBookmarks(
                changedIds.map { BookmarkBatchUpdate(bookmarkId = it, isArchived = revertTo) }
            )
        }
    }

    private fun revertBatchLabels(priorLabelsByBookmark: Map<String, List<String>>) {
        if (priorLabelsByBookmark.isEmpty()) return
        viewModelScope.launch {
            updateBookmarkUseCase.restoreBookmarkLabels(priorLabelsByBookmark)
        }
    }

    private fun applyBatchFavorite(targetFavorite: Boolean) {
        val snapshots = selectedBookmarkActionSnapshots()
        if (snapshots.isEmpty()) return

        val selectedCount = snapshots.size
        val itemsToUpdate = snapshots.filter { it.isMarked != targetFavorite }

        if (itemsToUpdate.isNotEmpty()) {
            updateBookmark {
                updateBookmarkUseCase.updateBookmarks(
                    itemsToUpdate.map { snapshot ->
                        BookmarkBatchUpdate(
                            bookmarkId = snapshot.id,
                            isFavorite = targetFavorite
                        )
                    }
                )
            }
        }
        val changedIds = itemsToUpdate.map { it.id }
        val event = if (targetFavorite) {
            BatchActionSnackbarEvent.FavoritesAdded(selectedCount, changedIds)
        } else {
            BatchActionSnackbarEvent.FavoritesRemoved(selectedCount, changedIds)
        }
        _batchActionSnackbarEvent.trySend(event)
    }

    private fun applyBatchArchive(targetArchived: Boolean) {
        val snapshots = selectedBookmarkActionSnapshots()
        if (snapshots.isEmpty()) return

        val selectedCount = snapshots.size
        val itemsToUpdate = snapshots.filter { it.isArchived != targetArchived }

        if (itemsToUpdate.isNotEmpty()) {
            updateBookmark {
                updateBookmarkUseCase.updateBookmarks(
                    itemsToUpdate.map { snapshot ->
                        BookmarkBatchUpdate(
                            bookmarkId = snapshot.id,
                            isArchived = targetArchived
                        )
                    }
                )
            }
        }
        val changedIds = itemsToUpdate.map { it.id }
        val event = if (targetArchived) {
            BatchActionSnackbarEvent.Archived(selectedCount, changedIds)
        } else {
            BatchActionSnackbarEvent.Unarchived(selectedCount, changedIds)
        }
        _batchActionSnackbarEvent.trySend(event)
    }

    fun onToggleSelectAllBookmarks() {
        val visibleIds = (_uiState.value as? UiState.Success)
            ?.bookmarks
            ?.mapTo(linkedSetOf<String>()) { it.id }
            ?: linkedSetOf()
        _multiSelectState.update { state ->
            if (!state.active) {
                state
            } else if (visibleIds.isNotEmpty() && state.selectedIds == visibleIds) {
                state.copy(selectedIds = emptySet())
            } else {
                state.copy(selectedIds = visibleIds)
            }
        }
    }

    private fun selectedBookmarkActionSnapshots(): List<SelectedBookmarkActionState> {
        val selectedIds = _multiSelectState.value.selectedIds
        if (selectedIds.isEmpty()) return emptyList()

        val bookmarks = (_uiState.value as? UiState.Success)?.bookmarks.orEmpty()
        return bookmarks
            .asSequence()
            .filter { it.id in selectedIds }
            .map {
                SelectedBookmarkActionState(
                    id = it.id,
                    isMarked = it.isMarked,
                    isArchived = it.isArchived
                )
            }
            .toList()
    }

    // Same eligibility gate as LoadContentPackageUseCase: pictures always carry offline content;
    // articles and videos need extracted article content (hasArticle) — this excludes embed-only
    // videos. PERMANENT_NO_CONTENT is never eligible.
    private fun isOfflineEligible(bookmark: Bookmark): Boolean =
        (bookmark.hasArticle || bookmark.type is Bookmark.Type.Picture) &&
            bookmark.contentState != Bookmark.ContentState.PERMANENT_NO_CONTENT

    // Build WorkManager constraints from the user's content-sync settings (wifi-only / battery
    // saver), mirroring the promote-on-open priority download in BookmarkDetailViewModel.
    private suspend fun buildContentSyncConstraints(): Constraints {
        val syncConstraints = settingsDataStore.getContentSyncConstraints()
        return Constraints.Builder().apply {
            setRequiredNetworkType(if (syncConstraints.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            if (!syncConstraints.allowOnBatterySaver) setRequiresBatteryNotLow(true)
        }.build()
    }

    private fun clearMultiSelectState() {
        _multiSelectState.value = MultiSelectState()
    }

    private fun dropSelectedIdsMissingFrom(visibleBookmarks: List<BookmarkListItem>) {
        if (!_multiSelectState.value.active) return

        val visibleIds = visibleBookmarks.mapTo(mutableSetOf()) { it.id }
        _multiSelectState.update { state ->
            val retainedIds = state.selectedIds.intersect(visibleIds)
            when {
                retainedIds == state.selectedIds -> state
                retainedIds.isEmpty() -> MultiSelectState()
                else -> state.copy(selectedIds = retainedIds)
            }
        }
    }

    private fun updateBookmark(update: suspend () -> UpdateBookmarkUseCase.Result) {
        viewModelScope.launch {
            val result = update()
            _uiState.update {
                if (it is UiState.Success) {
                    it.copy(updateBookmarkState = when (result) {
                        is UpdateBookmarkUseCase.Result.Success -> UpdateBookmarkState.Success
                        is UpdateBookmarkUseCase.Result.GenericError -> UpdateBookmarkState.Error(result.message)
                        is UpdateBookmarkUseCase.Result.NetworkError -> UpdateBookmarkState.Error(result.message)
                    })
                } else it
            }
        }
    }

    // Create Bookmark Dialog
    fun openCreateBookmarkDialog(clipboardText: String? = null) {
        val sharedText = clipboardText?.extractUrlAndTitle()

        _createBookmarkUiState.value = if (sharedText != null) {
            val isValid = sharedText.url.isValidUrl()
            CreateBookmarkUiState.Open(
                title = sharedText.title?.take(MAX_TITLE_LENGTH) ?: "",
                url = sharedText.url,
                urlError = if (!isValid && sharedText.url.isNotEmpty()) R.string.account_settings_url_error else null,
                isCreateEnabled = isValid
            )
        } else {
            CreateBookmarkUiState.Open(
                title = "",
                url = "",
                urlError = null,
                isCreateEnabled = false
            )
        }
    }

    fun closeCreateBookmarkDialog() {
        _createBookmarkUiState.value = CreateBookmarkUiState.Closed
    }

    fun updateCreateBookmarkTitle(title: String) {
        _createBookmarkUiState.update {
            (it as? CreateBookmarkUiState.Open)?.copy(
                title = title,
                isCreateEnabled = it.url.isValidUrl()
            ) ?: it
        }
    }

    fun updateCreateBookmarkUrl(url: String) {
        val isValidUrl = url.isValidUrl()
        val urlError = if (!isValidUrl && url.isNotEmpty()) {
            R.string.account_settings_url_error // Use resource ID
        } else {
            null
        }
        _createBookmarkUiState.update {
            (it as? CreateBookmarkUiState.Open)?.copy(
                url = url,
                urlError = urlError,
                isCreateEnabled = isValidUrl
            ) ?: it
        }
    }

    fun updateCreateBookmarkLabels(labels: List<String>) {
        _createBookmarkUiState.update {
            (it as? CreateBookmarkUiState.Open)?.copy(
                labels = labels
            ) ?: it
        }
    }

    fun updateCreateBookmarkFavorite(isFavorite: Boolean) {
        _createBookmarkUiState.update {
            (it as? CreateBookmarkUiState.Open)?.copy(
                isFavorite = isFavorite
            ) ?: it
        }
    }

    fun createBookmark() {
        handleCreateBookmarkAction(SaveAction.ADD)
    }

    fun handleCreateBookmarkAction(action: SaveAction) {
        val state = _createBookmarkUiState.value as? CreateBookmarkUiState.Open ?: return
        val url = state.url
        val title = state.title
        val labels = state.labels
        val isFavorite = state.isFavorite

        when (action) {
            SaveAction.ADD -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = false,
                    isFavorite = isFavorite
                )
                _createBookmarkUiState.value = CreateBookmarkUiState.Success
            }
            SaveAction.ARCHIVE -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = true,
                    isFavorite = isFavorite
                )
                _createBookmarkUiState.value = CreateBookmarkUiState.Success
            }
            SaveAction.VIEW -> {
                _createBookmarkUiState.value = CreateBookmarkUiState.Loading
                viewModelScope.launch {
                    try {
                        val bookmarkId = bookmarkRepository.createBookmark(
                            title = title,
                            url = url,
                            labels = labels
                        )
                        // Wait for bookmark to reach a terminal state before navigating,
                        // otherwise the detail screen shows Original mode with no content.
                        waitForBookmarkReady(bookmarkId)
                        _createBookmarkUiState.value = CreateBookmarkUiState.Success
                        _navigationEvent.send(NavigationEvent.NavigateToBookmarkDetail(bookmarkId))
                    } catch (e: Exception) {
                        _createBookmarkUiState.value =
                            CreateBookmarkUiState.Error(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    private suspend fun waitForBookmarkReady(bookmarkId: String) {
        val maxAttempts = 30
        val delayMs = 2000L
        for (i in 1..maxAttempts) {
            try {
                val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
                if (bookmark.state != com.mydeck.app.domain.model.Bookmark.State.LOADING) {
                    Timber.d("Bookmark ready after $i polls (state=${bookmark.state})")
                    return
                }
            } catch (e: Exception) {
                Timber.w(e, "Poll attempt $i failed for $bookmarkId")
            }
            delay(delayMs)
        }
        Timber.w("Timed out waiting for bookmark $bookmarkId to be ready")
    }

    fun onLayoutModeSelected(mode: LayoutMode) {
        _layoutMode.value = mode
        viewModelScope.launch {
            settingsDataStore.saveLayoutMode(mode.name)
        }
    }

    fun onSortOptionSelected(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            settingsDataStore.saveSortOption(option.name)
        }
    }

    sealed class NavigationEvent {
        data object NavigateToSettings : NavigationEvent()
        data object NavigateToAbout : NavigationEvent()
        data object NavigateToUserGuide : NavigationEvent()
        data class NavigateToBookmarkDetail(val bookmarkId: String, val showOriginal: Boolean = false) : NavigationEvent()
    }

    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val bookmarks: List<BookmarkListItem>,
            val updateBookmarkState: UpdateBookmarkState?
        ) : UiState()

        data class Empty(
            val messageResource: Int
        ) : UiState()
    }

    sealed class CreateBookmarkUiState {
        data object Closed : CreateBookmarkUiState()
        data class Open(
            val title: String,
            val url: String,
            val urlError: Int?,
            val isCreateEnabled: Boolean,
            val labels: List<String> = emptyList(),
            val isFavorite: Boolean = false
        ) : CreateBookmarkUiState()

        data object Loading : CreateBookmarkUiState()
        data object Success : CreateBookmarkUiState()
        data class Error(val message: String) : CreateBookmarkUiState()
    }

    sealed class UpdateBookmarkState {
        data object Success : UpdateBookmarkState()
        data class Error(val message: String) : UpdateBookmarkState()
    }
}
