package com.mydeck.app.ui.list

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.DrawerPreset
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.util.extractUrlAndTitle
import com.mydeck.app.util.isValidUrl
import com.mydeck.app.util.MAX_TITLE_LENGTH
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

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
    connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

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

    // Pending deletion
    private var pendingDeletionBookmarkId: String? = null

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

    val loadBookmarksIsRunning: StateFlow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME)
        .map { workInfoList ->
            workInfoList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow<UiState>(UiState.Empty(R.string.list_view_empty_not_loaded_yet))
    val uiState = _uiState.asStateFlow()

    init {
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
                        orderBy = sort.sqlOrderBy
                    )
                }
            }.collectLatest { bookmarks ->
                _uiState.update { currentState ->
                    if (currentState is UiState.Success) {
                        currentState.copy(bookmarks = bookmarks)
                    } else {
                        if (bookmarks.isEmpty() && _activeLabel.value == null && _filterFormState.value == FilterFormState.fromPreset(DrawerPreset.MY_LIST)) {
                             UiState.Empty(R.string.list_view_empty_nothing_to_see)
                        } else {
                            UiState.Success(bookmarks, null)
                        }
                    }
                }
            }
        }
    }

    // --- Drawer preset navigation ---

    fun onSelectDrawerPreset(preset: DrawerPreset) {
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
        _filterFormState.value = filterFormState
        _isFilterSheetOpen.value = false
    }

    fun onResetFilter() {
        _filterFormState.value = FilterFormState()
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

    fun onClickBookmark(bookmarkId: String) {
        Timber.d("onClickBookmark")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToBookmarkDetail(bookmarkId)) }
    }

    fun onClickBookmarkOpenOriginal(bookmarkId: String) {
        Timber.d("onClickBookmarkOpenOriginal")
        viewModelScope.launch { _navigationEvent.send(NavigationEvent.NavigateToBookmarkDetail(bookmarkId, showOriginal = true)) }
    }

    // No need for consume/reset methods with Channel

    private fun loadBookmarks(initialLoad: Boolean = false) {
        viewModelScope.launch {
            try {
                LoadBookmarksWorker.enqueue(context, isInitialLoad = initialLoad) // Enqueue for incremental sync
            } catch (e: Exception) {
                // Handle errors (e.g., show error message)
                _uiState.value = UiState.Empty(R.string.list_view_empty_error_loading_bookmarks)
                Timber.e(e, "Error loading bookmarks: ${e.message}")
            }
        }
    }

    fun onClickShareBookmark(url: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }
        _shareIntent.value = intent
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
        loadBookmarks(false)
    }

    fun onDeleteBookmark(bookmarkId: String) {
        // Store bookmark ID for potential undo; actual deletion occurs on snackbar dismissal.
        pendingDeletionBookmarkId = bookmarkId
    }

    fun onConfirmDeleteBookmark() {
        val bookmarkId = pendingDeletionBookmarkId ?: return
        pendingDeletionBookmarkId = null

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

    fun onCancelDeleteBookmark() {
        pendingDeletionBookmarkId = null
        Timber.d("Delete bookmark cancelled")
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
        data class NavigateToBookmarkDetail(val bookmarkId: String, val showOriginal: Boolean = false) : NavigationEvent()
    }

    sealed class UiState {
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
