package com.mydeck.app.ui.detail

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.AssetLoader
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.io.encoding.ExperimentalEncodingApi

@HiltViewModel
class BookmarkDetailViewModel @Inject constructor(
    private val updateBookmarkUseCase: UpdateBookmarkUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val assetLoader: AssetLoader,
    private val settingsDataStore: SettingsDataStore,
    private val loadArticleUseCase: LoadArticleUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    private val _openUrlEvent = MutableStateFlow<String>("")
    val openUrlEvent = _openUrlEvent.asStateFlow()

    private val _shareIntent = MutableStateFlow<Intent?>(null)
    val shareIntent: StateFlow<Intent?> = _shareIntent.asStateFlow()

    private val bookmarkId: String? = savedStateHandle["bookmarkId"]
    private val template: Flow<Template?> = settingsDataStore.themeFlow.map {
        it?.let {
            Theme.valueOf(it)
        } ?: Theme.SYSTEM
    }.map {
        when (it) {
            Theme.DARK -> assetLoader.loadAsset(Template.DARK_TEMPLATE_FILE)?.let { Template.SimpleTemplate(it) }
            Theme.LIGHT -> assetLoader.loadAsset(Template.LIGHT_TEMPLATE_FILE)?.let { Template.SimpleTemplate(it) }
            Theme.SEPIA -> assetLoader.loadAsset(Template.SEPIA_TEMPLATE_FILE)?.let { Template.SimpleTemplate(it) }
            Theme.SYSTEM -> {
                val light = assetLoader.loadAsset(Template.LIGHT_TEMPLATE_FILE)
                val dark = assetLoader.loadAsset(Template.DARK_TEMPLATE_FILE)
                if (!light.isNullOrBlank() && !dark.isNullOrBlank()) {
                    Template.DynamicTemplate(light = light, dark = dark)
                } else null
            }
        }
    }
    private val zoomFactor: Flow<Int> = settingsDataStore.zoomFactorFlow
    private val updateState = MutableStateFlow<UpdateBookmarkState?>(null)

    // Local tracking of scroll progress (not immediately persisted)
    private var currentScrollProgress = 0
    private var initialReadProgress = 0
    private var bookmarkType: com.mydeck.app.domain.model.Bookmark.Type? = null
    private var isReadLocked = false // true when article has been completed; disables scroll tracking

    // Content loading state for on-demand fetch
    private val _contentLoadState = MutableStateFlow<ContentLoadState>(ContentLoadState.Idle)
    val contentLoadState: StateFlow<ContentLoadState> = _contentLoadState.asStateFlow()

    // Pending deletion state (for undo functionality)
    // Uses a separate scope so deletion survives ViewModel clearing (e.g. user navigates back)
    private val deletionScope = CoroutineScope(Dispatchers.IO)
    private var pendingDeletionJob: Job? = null
    private val _pendingDeletion = MutableStateFlow(false)
    val pendingDeletion: StateFlow<Boolean> = _pendingDeletion.asStateFlow()

    // Article search state
    private val _articleSearchState = MutableStateFlow(ArticleSearchState())
    val articleSearchState: StateFlow<ArticleSearchState> = _articleSearchState.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        // Load initial progress and handle type-specific behavior
        if (bookmarkId != null) {
            viewModelScope.launch {
                try {
                    val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
                    initialReadProgress = bookmark.readProgress
                    bookmarkType = bookmark.type

                    // For photos and videos, auto-mark as 100% when opened
                    // and refresh from API to ensure embed data is available
                    when (bookmark.type) {
                        is com.mydeck.app.domain.model.Bookmark.Type.Picture,
                        is com.mydeck.app.domain.model.Bookmark.Type.Video -> {
                            if (bookmark.readProgress < 100) {
                                bookmarkRepository.updateReadProgress(bookmarkId, 100)
                                currentScrollProgress = 100
                            }
                            // Refresh from API to get embed and other fields
                            // that may not have been present during initial sync
                            bookmarkRepository.refreshBookmarkFromApi(bookmarkId)
                        }
                        is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
                            currentScrollProgress = bookmark.readProgress
                            // If article was already completed, lock scroll tracking
                            // so scrolling back up doesn't reduce progress from 100
                            isReadLocked = bookmark.isRead()

                            // Handle content availability
                            when (bookmark.contentState) {
                                ContentState.DOWNLOADED -> { /* Content available, nothing to do */ }
                                ContentState.PERMANENT_NO_CONTENT -> {
                                    // Signal to UI that content is permanently unavailable
                                    _contentLoadState.value = ContentLoadState.Failed(
                                        reason = bookmark.contentFailureReason ?: "No content available",
                                        canRetry = false
                                    )
                                }
                                else -> {
                                    // NOT_ATTEMPTED or DIRTY — attempt on-demand fetch
                                    fetchContentOnDemand(bookmarkId)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error initializing bookmark progress: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Save final progress when leaving the detail view
        // viewModelScope is cancelled during onCleared(), so use an independent scope
        // to ensure the save completes even during ViewModel teardown
        CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        ).launch {
            saveCurrentProgress()
        }
    }

    fun getInitialReadProgress(): Int = currentScrollProgress.takeIf { it > 0 } ?: initialReadProgress

    private suspend fun saveCurrentProgress() {
        if (bookmarkId != null && currentScrollProgress > 0) {
            try {
                bookmarkRepository.updateReadProgress(bookmarkId, currentScrollProgress)
                Timber.d("Saved final read progress: $currentScrollProgress%")
            } catch (e: Exception) {
                Timber.e(e, "Error saving final progress: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    val uiState = combine(
        bookmarkRepository.observeBookmark(bookmarkId!!),
        updateState,
        template,
        zoomFactor
    ) { bookmark, updateState, template, zoomFactor ->
        if (bookmark == null) {
            Timber.e("Error loading bookmark [bookmarkId=$bookmarkId]")
            UiState.Error
        } else if (template == null) {
            Timber.e("Error loading template(s)")
            UiState.Error
        } else {
            UiState.Success(
                bookmark = Bookmark(
                    url = bookmark.url,
                    title = bookmark.title,
                    authors = bookmark.authors,
                    createdDate = formatLocalDateTimeWithDateFormat(bookmark.created),
                    bookmarkId = bookmarkId,
                    siteName = bookmark.siteName,
                    imgSrc = bookmark.image.src,
                    isFavorite = bookmark.isMarked,
                    isArchived = bookmark.isArchived,
                    isRead = bookmark.isRead(),
                    type = when (bookmark.type) {
                        is com.mydeck.app.domain.model.Bookmark.Type.Article -> Bookmark.Type.ARTICLE
                        is com.mydeck.app.domain.model.Bookmark.Type.Picture -> Bookmark.Type.PHOTO
                        is com.mydeck.app.domain.model.Bookmark.Type.Video -> Bookmark.Type.VIDEO
                    },
                    articleContent = bookmark.articleContent,
                    embed = bookmark.embed,
                    lang = bookmark.lang,
                    wordCount = bookmark.wordCount,
                    readingTime = bookmark.readingTime,
                    description = bookmark.description,
                    labels = bookmark.labels,
                    readProgress = bookmark.readProgress,
                    debugInfo = buildDebugInfo(bookmark),
                    hasContent = when (bookmark.type) {
                        is com.mydeck.app.domain.model.Bookmark.Type.Article -> !bookmark.articleContent.isNullOrBlank()
                        is com.mydeck.app.domain.model.Bookmark.Type.Video -> !bookmark.articleContent.isNullOrBlank() || !bookmark.embed.isNullOrBlank()
                        is com.mydeck.app.domain.model.Bookmark.Type.Picture -> true
                    }
                ),
                updateBookmarkState = updateState,
                template = template,
                zoomFactor = zoomFactor
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    fun onToggleFavorite(bookmarkId: String, isFavorite: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsFavorite(
                bookmarkId = bookmarkId,
                isFavorite = isFavorite
            )
        }
    }

    fun onUpdateBookmarkStateConsumed() {
        updateState.value = null
    }

    fun onToggleArchive(bookmarkId: String, isArchived: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsArchived(
                bookmarkId = bookmarkId,
                isArchived = isArchived
            )
        }
    }

    fun onToggleMarkRead(bookmarkId: String, isRead: Boolean) {
        updateBookmark {
            updateBookmarkUseCase.updateIsRead(
                bookmarkId = bookmarkId,
                isRead = isRead
            )
        }
    }

    fun onUpdateLabels(bookmarkId: String, labels: List<String>) {
        updateBookmark {
            // For now, we'll handle labels through the repository directly
            // This should be implemented in UpdateBookmarkUseCase.updateLabels()
            val result = bookmarkRepository.updateLabels(bookmarkId, labels)
            when (result) {
                is BookmarkRepository.UpdateResult.Success -> UpdateBookmarkUseCase.Result.Success
                is BookmarkRepository.UpdateResult.Error -> UpdateBookmarkUseCase.Result.GenericError(result.errorMessage)
                is BookmarkRepository.UpdateResult.NetworkError -> UpdateBookmarkUseCase.Result.NetworkError(result.errorMessage)
            }
        }
    }

    fun onScrollProgressChanged(progress: Int) {
        // Once article is marked read, ignore further scroll updates
        // (matches native Readeck behavior: lock on completion)
        if (isReadLocked) return

        val clamped = progress.coerceIn(0, 100)
        currentScrollProgress = clamped

        // Auto-complete: when user reaches the bottom, lock tracking
        if (clamped >= 100) {
            isReadLocked = true
        }
    }

    fun onToggleRead(bookmarkId: String, isRead: Boolean) {
        viewModelScope.launch {
            try {
                val newProgress = if (isRead) 100 else 0
                bookmarkRepository.updateReadProgress(bookmarkId, newProgress)
                currentScrollProgress = newProgress
                isReadLocked = isRead // Lock on "mark read", unlock on "mark unread"
                Timber.d("Manually set read progress to $newProgress%")
            } catch (e: Exception) {
                Timber.e(e, "Error updating read state: ${e.message}")
            }
        }
    }

    private fun updateBookmark(update: suspend () -> UpdateBookmarkUseCase.Result) {
        viewModelScope.launch {
            val state = when (val result = update()) {
                is UpdateBookmarkUseCase.Result.Success -> UpdateBookmarkState.Success
                is UpdateBookmarkUseCase.Result.GenericError -> UpdateBookmarkState.Error(result.message)
                is UpdateBookmarkUseCase.Result.NetworkError -> UpdateBookmarkState.Error(result.message)
            }
            updateState.value = state
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

    fun deleteBookmark(bookmarkId: String) {
        // Cancel any existing pending deletion
        pendingDeletionJob?.cancel()

        _pendingDeletion.value = true

        pendingDeletionJob = deletionScope.launch {
            try {
                delay(10000)

                val state = when (val result = updateBookmarkUseCase.deleteBookmark(bookmarkId)) {
                    is UpdateBookmarkUseCase.Result.Success -> UpdateBookmarkState.Success
                    is UpdateBookmarkUseCase.Result.GenericError -> UpdateBookmarkState.Error(result.message)
                    is UpdateBookmarkUseCase.Result.NetworkError -> UpdateBookmarkState.Error(result.message)
                }
                if (state is UpdateBookmarkState.Success) {
                    _navigationEvent.update { NavigationEvent.NavigateBack }
                }
                updateState.value = state
                _pendingDeletion.value = false
            } catch (e: CancellationException) {
                Timber.d("Deletion cancelled by user")
                _pendingDeletion.value = false
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error deleting bookmark: ${e.message}")
                _pendingDeletion.value = false
            }
        }
    }

    fun onCancelDeleteBookmark() {
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
        _pendingDeletion.value = false
        Timber.d("Delete bookmark cancelled")
    }

    fun onClickOpenUrl(url: String){
         _openUrlEvent.value = url
    }

    fun onClickBack() {
        // Save progress before navigating back
        viewModelScope.launch {
            saveCurrentProgress()
            _navigationEvent.update { NavigationEvent.NavigateBack }
        }
    }

    fun onClickChangeZoomFactor(value: Int) {
        viewModelScope.launch {
            val currentZoom = settingsDataStore.zoomFactorFlow
                .stateIn(viewModelScope)
                .value
            val newZoom = (currentZoom + value).coerceAtMost(400).coerceAtLeast(25)
            settingsDataStore.saveZoomFactor(newZoom)
        }
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null } // Reset the event
    }

    fun onOpenUrlEventConsumed() {
        _openUrlEvent.value = ""
    }

    private fun formatLocalDateTimeWithDateFormat(localDateTime: LocalDateTime): String {
        val dateFormat = DateFormat.getDateInstance(
            DateFormat.MEDIUM
        )
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis = localDateTime.toInstant(timeZone).toEpochMilliseconds()
        return dateFormat.format(Date(epochMillis))
    }

    private fun fetchContentOnDemand(bookmarkId: String) {
        viewModelScope.launch {
            _contentLoadState.value = ContentLoadState.Loading
            val result = loadArticleUseCase.execute(bookmarkId)
            _contentLoadState.value = when (result) {
                is LoadArticleUseCase.Result.Success -> ContentLoadState.Loaded
                is LoadArticleUseCase.Result.AlreadyDownloaded -> ContentLoadState.Loaded
                is LoadArticleUseCase.Result.TransientFailure -> ContentLoadState.Failed(
                    reason = result.reason,
                    canRetry = true
                )
                is LoadArticleUseCase.Result.PermanentFailure -> ContentLoadState.Failed(
                    reason = result.reason,
                    canRetry = false
                )
            }
        }
    }

    fun retryContentFetch() {
        bookmarkId?.let { fetchContentOnDemand(it) }
    }

    sealed class ContentLoadState {
        data object Idle : ContentLoadState()
        data object Loading : ContentLoadState()
        data object Loaded : ContentLoadState()
        data class Failed(val reason: String, val canRetry: Boolean) : ContentLoadState()
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }

    sealed class UiState {
        data class Success(val bookmark: Bookmark, val updateBookmarkState: UpdateBookmarkState?, val template: Template, val zoomFactor: Int) :
            UiState()

        data object Loading : UiState()
        data object Error : UiState()
    }

    data class Bookmark(
        val url: String,
        val title: String,
        val authors: List<String>,
        val createdDate: String,
        val bookmarkId: String,
        val siteName: String,
        val imgSrc: String,
        val isFavorite: Boolean,
        val isArchived: Boolean,
        val isRead: Boolean,
        val type: Type,
        val articleContent: String?,
        val embed: String?,
        val lang: String,
        val wordCount: Int?,
        val readingTime: Int?,
        val description: String,
        val labels: List<String>,
        val readProgress: Int,
        val debugInfo: String = "",
        val hasContent: Boolean
    ) {
        enum class Type {
            ARTICLE, PHOTO, VIDEO
        }
        fun getContent(template: Template, isDark: Boolean): String? {
            val htmlTemplate = when (template) {
                is Template.SimpleTemplate -> template.template
                is Template.DynamicTemplate -> {
                    if (isDark) {
                        template.dark
                    } else {
                        template.light
                    }
                }
            }
            return when (type) {
                Type.PHOTO -> {
                    val textPart = articleContent ?: description.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" } ?: ""
                    val imagePart = """<img src="$imgSrc"/>"""
                    htmlTemplate.replace("%s", textPart + imagePart)
                }

                Type.VIDEO -> {
                    val textPart = articleContent ?: description.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" } ?: ""
                    val rawEmbedPart = embed ?: ""
                    val isYouTubeEmbed = rawEmbedPart.contains("youtube", ignoreCase = true)
                    val embedPart = if (isYouTubeEmbed) {
                        rawEmbedPart
                            .replace(Regex("\\s+sandbox=\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("\\s+csp=\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("\\s+credentialless=\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                    } else {
                        rawEmbedPart
                    }
                    val wrappedEmbedPart = if (embedPart.contains("<iframe", ignoreCase = true)) {
                        """<div class="video-embed">$embedPart</div>"""
                    } else {
                        embedPart
                    }
                    val content = textPart + wrappedEmbedPart
                    val embedLength = embedPart.length
                    val iframeSrc = Regex("src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                        .find(embedPart)
                        ?.groupValues
                        ?.getOrNull(1)
                    val iframeStyle = Regex("style=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                        .find(embedPart)
                        ?.groupValues
                        ?.getOrNull(1)
                    Timber.d(
                        "Video embed details: embedLength=%s iframeSrc=%s iframeStyle=%s descriptionLength=%s articleLength=%s",
                        embedLength,
                        iframeSrc,
                        iframeStyle,
                        description.length,
                        articleContent?.length
                    )
                    Timber.d(
                        "Video HTML template length=%s combinedContentLength=%s",
                        htmlTemplate.length,
                        content.length
                    )
                    if (content.isNotEmpty()) htmlTemplate.replace("%s", content) else null
                }

                Type.ARTICLE -> {
                    articleContent?.let {
                        htmlTemplate.replace("%s", it)
                    }
                }
            }
        }
    }

    sealed class UpdateBookmarkState {
        data object Success : UpdateBookmarkState()
        data class Error(val message: String) : UpdateBookmarkState()
    }

    private fun buildDebugInfo(bookmark: com.mydeck.app.domain.model.Bookmark): String {
        return buildString {
            appendLine("=== BOOKMARK DEBUG INFO ===")
            appendLine()
            appendLine("ID: ${bookmark.id}")
            appendLine("State: ${bookmark.state}")
            appendLine("Loaded: ${bookmark.loaded}")
            appendLine("Has Article: ${bookmark.hasArticle}")
            appendLine("Is Deleted: ${bookmark.isDeleted}")
            appendLine()
            appendLine("Timestamps:")
            appendLine("  Created: ${bookmark.created}")
            appendLine("  Updated: ${bookmark.updated}")
            appendLine("  Published: ${bookmark.published ?: "N/A"}")
            appendLine()
            appendLine("URLs & Resources:")
            appendLine("  URL: ${bookmark.url}")
            appendLine("  HREF: ${bookmark.href}")
            appendLine("  Site: ${bookmark.site}")
            appendLine("  Site Name: ${bookmark.siteName}")
            appendLine("  Article Resource: ${bookmark.article.src}")
            appendLine("  Icon: ${bookmark.icon.src} (${bookmark.icon.width}x${bookmark.icon.height})")
            appendLine("  Image: ${bookmark.image.src} (${bookmark.image.width}x${bookmark.image.height})")
            appendLine("  Thumbnail: ${bookmark.thumbnail.src} (${bookmark.thumbnail.width}x${bookmark.thumbnail.height})")
            appendLine("  Log: ${bookmark.log.src}")
            appendLine("  Props: ${bookmark.props.src}")
            appendLine()
            appendLine("Content Info:")
            appendLine("  Type: ${bookmark.type}")
            appendLine("  Document Type: ${bookmark.documentTpe}")
            appendLine("  Language: ${bookmark.lang}")
            appendLine("  Text Direction: ${bookmark.textDirection}")
            appendLine("  Word Count: ${bookmark.wordCount ?: "N/A"}")
            appendLine("  Reading Time: ${bookmark.readingTime ?: "N/A"} min")
            appendLine("  Read Progress: ${bookmark.readProgress}%")
            appendLine("  Embed: ${bookmark.embed ?: "N/A"}")
            appendLine("  Embed Hostname: ${bookmark.embedHostname ?: "N/A"}")
            appendLine("  Has Article Content: ${bookmark.articleContent != null}")
            if (bookmark.articleContent != null) {
                appendLine("  Article Content Length: ${bookmark.articleContent.length} chars")
            }
            appendLine()
            appendLine("Metadata:")
            appendLine("  Authors: ${if (bookmark.authors.isEmpty()) "None" else bookmark.authors.joinToString(", ")}")
            appendLine("  Labels: ${if (bookmark.labels.isEmpty()) "None" else bookmark.labels.joinToString(", ")}")
            appendLine("  Is Marked: ${bookmark.isMarked}")
            appendLine("  Is Archived: ${bookmark.isArchived}")
            appendLine()
            if (bookmark.description.isNotBlank()) {
                appendLine("Description:")
                appendLine(bookmark.description)
            }
        }
    }

    // Article search functions
    fun onArticleSearchActivate() {
        _articleSearchState.update { it.copy(isActive = true) }
    }

    fun onArticleSearchDeactivate() {
        searchDebounceJob?.cancel()
        _articleSearchState.update { ArticleSearchState() }
    }

    fun onArticleSearchQueryChange(query: String) {
        _articleSearchState.update { it.copy(query = query) }

        // Debounce search execution
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            // Signal that search should be executed
            // The actual search is performed in the UI layer via WebViewSearchBridge
        }
    }

    fun onArticleSearchUpdateResults(totalMatches: Int) {
        _articleSearchState.update { state ->
            val newCurrent = if (totalMatches > 0 && state.currentMatch == 0) 1 else 0
            state.copy(
                totalMatches = totalMatches,
                currentMatch = newCurrent
            )
        }
    }

    fun onArticleSearchNext() {
        _articleSearchState.update { state ->
            if (state.totalMatches > 0) {
                val next = if (state.currentMatch >= state.totalMatches) 1 else state.currentMatch + 1
                state.copy(currentMatch = next)
            } else {
                state
            }
        }
    }

    fun onArticleSearchPrevious() {
        _articleSearchState.update { state ->
            if (state.totalMatches > 0) {
                val prev = if (state.currentMatch <= 1) state.totalMatches else state.currentMatch - 1
                state.copy(currentMatch = prev)
            } else {
                state
            }
        }
    }

    data class ArticleSearchState(
        val isActive: Boolean = false,
        val query: String = "",
        val totalMatches: Int = 0,
        val currentMatch: Int = 0  // 1-based, 0 when no matches
    )
}
