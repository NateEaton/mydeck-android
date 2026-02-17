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
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    private var pendingDeletionJob: Job? = null
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive = _isSearchActive.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState(archived = false))
    val filterState = _filterState.asStateFlow()

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
        // ... (Previous init logic for settings)
         viewModelScope.launch {
            combine(
                _filterState,
                _searchQuery,
                _sortOption
            ) { filter, query, sort ->
                Triple(filter, query, sort)
            }.flatMapLatest { (filter, query, sort) ->
                // In label mode, show ALL bookmarks with that label regardless of archive/favorite status
                val isLabelMode = filter.label != null
                val effectiveArchived = if (isLabelMode) null else filter.archived
                val effectiveFavorite = if (isLabelMode) null else filter.favorite
                if (query.isNotEmpty()) {
                    bookmarkRepository.searchBookmarkListItems(
                        searchQuery = query,
                        type = filter.type,
                        unread = filter.unread,
                        archived = effectiveArchived,
                        favorite = effectiveFavorite,
                        label = filter.label,
                        orderBy = sort.sqlOrderBy
                    )
                } else {
                    bookmarkRepository.observeBookmarkListItems(
                        type = filter.type,
                        unread = filter.unread,
                        archived = effectiveArchived,
                        favorite = effectiveFavorite,
                        label = filter.label,
                        orderBy = sort.sqlOrderBy
                    )
                }
            }.collectLatest { bookmarks ->
                _uiState.update { currentState ->
                    if (currentState is UiState.Success) {
                        currentState.copy(bookmarks = bookmarks)
                    } else {
                        if (bookmarks.isEmpty() && _searchQuery.value.isEmpty() && _filterState.value == FilterState(archived = false)) {
                             UiState.Empty(R.string.list_view_empty_nothing_to_see)
                        } else {
                            UiState.Success(bookmarks, null)
                        }
                    }
                }
            }

        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSearchActiveChange(isActive: Boolean) {
        _isSearchActive.value = isActive
        if (!isActive) {
            onClearSearch()
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun onClickLabel(label: String) {
        _filterState.update {
            if (it.label == label) it.copy(label = null)
            else it.copy(label = label)
        }
    }

    fun onClickMyList() {
        _filterState.update { FilterState(archived = false) }
    }

    fun onClickArchive() {
        _filterState.update { FilterState(archived = true) }
    }

    fun onClickFavorite() {
        _filterState.update { FilterState(favorite = true) }
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
                _filterState.update { 
                    if (it.label == oldLabel) it.copy(label = newLabel) else it 
                }
            } catch (e: Exception) {
                Timber.e(e, "Error renaming label")
                // _uiState.value = UiState.Error(...) ?
            }
        }
    }

    fun onDeleteLabel(label: String) {
        viewModelScope.launch {
            try {
                bookmarkRepository.deleteLabel(label)
                _filterState.update {
                    if (it.label == label) FilterState(archived = false) else it
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting label")
            }
        }
    }


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
        // Cancel any existing pending deletion
        pendingDeletionJob?.cancel()

        // Store the bookmark ID for potential undo
        pendingDeletionBookmarkId = bookmarkId

        // Start a new deletion job with 10-second delay
        pendingDeletionJob = viewModelScope.launch {
            try {
                // Wait 10 seconds before actually deleting
                delay(10000)

                // After delay, perform the actual deletion
                updateBookmark {
                    updateBookmarkUseCase.deleteBookmark(bookmarkId)
                }

                // Clear pending deletion state
                pendingDeletionBookmarkId = null
                pendingDeletionJob = null
            } catch (e: CancellationException) {
                // Job was cancelled (undo was clicked), just rethrow
                Timber.d("Deletion cancelled by user")
                throw e
            } catch (e: Exception) {
                // Some other error occurred
                Timber.e(e, "Error deleting bookmark: ${e.message}")
            }
        }
    }

    fun onCancelDeleteBookmark() {
        // Cancel the pending deletion job
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
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

    fun createBookmark() {
        handleCreateBookmarkAction(SaveAction.ADD)
    }

    fun handleCreateBookmarkAction(action: SaveAction) {
        val state = _createBookmarkUiState.value as? CreateBookmarkUiState.Open ?: return
        val url = state.url
        val title = state.title
        val labels = state.labels

        when (action) {
            SaveAction.ADD -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = false
                )
                _createBookmarkUiState.value = CreateBookmarkUiState.Success
            }
            SaveAction.ARCHIVE -> {
                CreateBookmarkWorker.enqueue(
                    workManager = workManager,
                    url = url,
                    title = title,
                    labels = labels,
                    isArchived = true
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

    data class FilterState(
        val type: Bookmark.Type? = null,
        val unread: Boolean? = null,
        val archived: Boolean? = null,
        val favorite: Boolean? = null,
        val label: String? = null,
    )

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
            val labels: List<String> = emptyList()
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

