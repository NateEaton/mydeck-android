package com.mydeck.app.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Annotation
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.EffectiveAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.BookmarkMetadataUpdate
import com.mydeck.app.domain.model.ReaderAppearanceSelection
import com.mydeck.app.domain.model.SelectionData
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.model.toEffectiveAppearance
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.domain.usecase.LoadContentPackageUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.AssetLoader
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.CreateAnnotationDto
import com.mydeck.app.io.rest.model.UpdateAnnotationDto
import com.mydeck.app.io.rest.model.toAnnotationCachePayload
import com.mydeck.app.util.BookmarkDebugExporter
import com.mydeck.app.util.formatBookmarkShareText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.mydeck.app.domain.model.ImageGalleryData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


@HiltViewModel
class BookmarkDetailViewModel @Inject constructor(
    private val updateBookmarkUseCase: UpdateBookmarkUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val assetLoader: AssetLoader,
    private val settingsDataStore: SettingsDataStore,
    private val loadArticleUseCase: LoadArticleUseCase,
    private val loadContentPackageUseCase: LoadContentPackageUseCase,
    private val contentPackageManager: ContentPackageManager,
    private val cachedAnnotationDao: CachedAnnotationDao,
    private val connectivityMonitor: ConnectivityMonitor,
    private val readeckApi: ReadeckApi,
    @ApplicationContext private val context: Context,
    private val json: Json,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent: Flow<String> = _openUrlEvent.receiveAsFlow()

    private val _shareIntent = Channel<Intent>(Channel.BUFFERED)
    val shareIntent: Flow<Intent> = _shareIntent.receiveAsFlow()

    private val _debugExportEvent = Channel<DebugExportEvent>(Channel.BUFFERED)
    val debugExportEvent: Flow<DebugExportEvent> = _debugExportEvent.receiveAsFlow()

    private val _bookmarkId = MutableStateFlow<String?>(savedStateHandle["bookmarkId"])
    private val themeModeFlow = settingsDataStore.themeFlow
        .map { themeStr ->
            themeStr?.let {
                try { Theme.valueOf(it) } catch (_: IllegalArgumentException) { Theme.LIGHT }
            } ?: Theme.SYSTEM
        }
    private val lightAppearance = settingsDataStore.lightAppearanceFlow
    private val darkAppearance = settingsDataStore.darkAppearanceFlow
    private val readerAppearanceSelection: Flow<ReaderAppearanceSelection> = combine(
        themeModeFlow,
        lightAppearance,
        darkAppearance
    ) { themeMode, lightAppearance, darkAppearance ->
        ReaderAppearanceSelection(
            themeMode = themeMode,
            lightAppearance = lightAppearance,
            darkAppearance = darkAppearance
        )
    }
    private val template: Flow<Template?> = readerAppearanceSelection.map { selection ->
        when (selection.themeMode) {
            Theme.DARK -> loadTemplateForAppearance(selection.darkAppearance.toEffectiveAppearance())
            Theme.LIGHT -> loadTemplateForAppearance(selection.lightAppearance.toEffectiveAppearance())
            Theme.SYSTEM -> {
                val lightTemplate = loadTemplateContent(selection.lightAppearance.toEffectiveAppearance())
                val darkTemplate = loadTemplateContent(selection.darkAppearance.toEffectiveAppearance())
                if (!lightTemplate.isNullOrBlank() && !darkTemplate.isNullOrBlank()) {
                    Template.DynamicTemplate(light = lightTemplate, dark = darkTemplate)
                } else null
            }
        }
    }
    private val typographySettings = settingsDataStore.typographySettingsFlow
    val keepScreenOnWhileReading: StateFlow<Boolean> = settingsDataStore.keepScreenOnWhileReadingFlow
    val fullscreenWhileReading: StateFlow<Boolean> = settingsDataStore.fullscreenWhileReadingFlow
    private val updateState = MutableStateFlow<UpdateBookmarkState?>(null)

    val labelsWithCounts: StateFlow<Map<String, Int>> = bookmarkRepository
        .observeAllLabelsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun loadTemplateForAppearance(appearance: EffectiveAppearance): Template? {
        return loadTemplateContent(appearance)?.let { Template.SimpleTemplate(it) }
    }

    private fun loadTemplateContent(appearance: EffectiveAppearance): String? {
        val templateFile = when (appearance) {
            EffectiveAppearance.PAPER -> Template.LIGHT_TEMPLATE_FILE
            EffectiveAppearance.SEPIA -> Template.SEPIA_TEMPLATE_FILE
            EffectiveAppearance.DARK -> Template.DARK_TEMPLATE_FILE
            EffectiveAppearance.BLACK -> Template.BLACK_TEMPLATE_FILE
        }
        return assetLoader.loadAsset(templateFile)
    }

    // Local tracking of scroll progress (not immediately persisted)
    private var currentScrollProgress = 0
    private var initialReadProgress = 0
    private var bookmarkType: com.mydeck.app.domain.model.Bookmark.Type? = null
    private var isReadLocked = false // true when article has been completed; disables scroll tracking

    // Content loading state for on-demand fetch
    private val _contentLoadState = MutableStateFlow<ContentLoadState>(ContentLoadState.Idle)
    val contentLoadState: StateFlow<ContentLoadState> = _contentLoadState.asStateFlow()

    // Article search state
    private val _articleSearchState = MutableStateFlow(ArticleSearchState())
    val articleSearchState: StateFlow<ArticleSearchState> = _articleSearchState.asStateFlow()

    private val _annotationsState = MutableStateFlow(AnnotationsState())
    val annotationsState: StateFlow<AnnotationsState> = _annotationsState.asStateFlow()

    private val _showAnnotationsSheet = MutableStateFlow(false)
    val showAnnotationsSheet: StateFlow<Boolean> = _showAnnotationsSheet.asStateFlow()

    private val _pendingAnnotationScrollId = MutableStateFlow<String?>(null)
    val pendingAnnotationScrollId: StateFlow<String?> = _pendingAnnotationScrollId.asStateFlow()

    private val _annotationEditState = MutableStateFlow<AnnotationEditState?>(null)
    val annotationEditState: StateFlow<AnnotationEditState?> = _annotationEditState.asStateFlow()

    // Gallery state
    private val _galleryData = MutableStateFlow<ImageGalleryData?>(null)
    val galleryData: StateFlow<ImageGalleryData?> = _galleryData.asStateFlow()

    // Reader context menu state
    private val _readerContextMenu = MutableStateFlow(ReaderContextMenuState())
    val readerContextMenu: StateFlow<ReaderContextMenuState> = _readerContextMenu.asStateFlow()

    // Annotation refresh events for in-place WebView updates (no full reload)
    private val _annotationRefreshEvent = Channel<AnnotationRefreshEvent>(Channel.BUFFERED)
    val annotationRefreshEvent: Flow<AnnotationRefreshEvent> = _annotationRefreshEvent.receiveAsFlow()

    private var searchDebounceJob: Job? = null
    private var annotationSyncJob: Job? = null
    private var contentRefreshJob: Job? = null

    init {
        _bookmarkId.value?.let { initializeBookmark(it) }
    }

    /**
     * Load a new bookmark into this ViewModel. Resets local state and triggers
     * observation of the new bookmark. Used by the expanded detail pane where
     * SavedStateHandle is not available.
     */
    fun loadBookmark(bookmarkId: String) {
        // Reset local reading state for the new bookmark
        currentScrollProgress = 0
        initialReadProgress = 0
        bookmarkType = null
        isReadLocked = false
        _contentLoadState.value = ContentLoadState.Idle
        _articleSearchState.value = ArticleSearchState()
        _annotationsState.value = AnnotationsState()
        _showAnnotationsSheet.value = false
        _pendingAnnotationScrollId.value = null
        _annotationEditState.value = null

        _bookmarkId.value = bookmarkId
        initializeBookmark(bookmarkId)
    }

    private fun initializeBookmark(id: String) {
        // Load initial progress and handle type-specific behavior
        viewModelScope.launch {
            try {
                val bookmark = bookmarkRepository.getBookmarkById(id)
                initialReadProgress = bookmark.readProgress
                bookmarkType = bookmark.type

                // For photos and videos, auto-mark as 100% when opened
                when (bookmark.type) {
                    is com.mydeck.app.domain.model.Bookmark.Type.Picture,
                    is com.mydeck.app.domain.model.Bookmark.Type.Video -> {
                        if (bookmark.readProgress < 100) {
                            bookmarkRepository.updateReadProgress(id, 100)
                            currentScrollProgress = 100
                        }
                        // Refresh metadata from API if embed is missing
                        // (list endpoint used by full sync doesn't include embed)
                        if (bookmark.type is com.mydeck.app.domain.model.Bookmark.Type.Video &&
                            bookmark.embed.isNullOrBlank()) {
                            bookmarkRepository.refreshBookmarkMetadata(id)
                        }
                        // Fetch article content on demand if not yet downloaded
                        // (videos and photos can have article content per the API spec)
                        when (bookmark.contentState) {
                            ContentState.DOWNLOADED -> {
                                // Check for annotation changes from other clients
                                if (contentPackageManager.getContentDir(id) != null) {
                                    syncAnnotationsForContentPackage(id)
                                }
                            }
                            ContentState.PERMANENT_NO_CONTENT -> {
                                _contentLoadState.value = ContentLoadState.Failed(
                                    reason = bookmark.contentFailureReason ?: "No content available",
                                    canRetry = false
                                )
                            }
                            else -> fetchContentOnDemand(id)
                        }
                    }
                    is com.mydeck.app.domain.model.Bookmark.Type.Article -> {
                        currentScrollProgress = bookmark.readProgress
                        // If article was already completed, lock scroll tracking
                        // so scrolling back up doesn't reduce progress from 100
                        isReadLocked = bookmark.isRead()

                        // Handle content availability
                        when (bookmark.contentState) {
                            ContentState.DOWNLOADED -> {
                                if (contentPackageManager.getContentDir(id) != null) {
                                    // Content package on disk — check for annotation changes
                                    syncAnnotationsForContentPackage(id)
                                } else {
                                    // Legacy article path — content in Room
                                    syncAnnotationsIfNeeded(id)
                                }
                            }
                            ContentState.PERMANENT_NO_CONTENT -> {
                                // Signal to UI that content is permanently unavailable
                                _contentLoadState.value = ContentLoadState.Failed(
                                    reason = bookmark.contentFailureReason ?: "No content available",
                                    canRetry = false
                                )
                            }
                            else -> {
                                // NOT_ATTEMPTED or DIRTY — attempt on-demand fetch
                                fetchContentOnDemand(id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error initializing bookmark progress: ${e.message}")
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
        val id = _bookmarkId.value
        if (id != null && currentScrollProgress > 0) {
            try {
                bookmarkRepository.updateReadProgress(id, currentScrollProgress)
                Timber.w("Saved final read progress: $currentScrollProgress%")
            } catch (e: Exception) {
                Timber.e(e, "Error saving final progress: ${e.message}")
            }
        } else {
            Timber.w("Skipping progress save - bookmarkId: $id, progress: $currentScrollProgress")
        }
    }

    /**
     * Save current reading progress when the screen goes to background.
     * This avoids losing scroll position if the app is killed after onStop().
     */
    fun saveProgressOnPause() {
        viewModelScope.launch {
            Timber.w("saveProgressOnPause called for bookmark: ${_bookmarkId.value}, progress: $currentScrollProgress%")
            saveCurrentProgress()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = _bookmarkId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                bookmarkRepository.observeBookmark(id),
                updateState,
                template,
                typographySettings,
                readerAppearanceSelection
            ) { bookmark, updateState, template, typographySettings, readerAppearanceSelection ->
                if (bookmark == null) {
                    Timber.e("Error loading bookmark [bookmarkId=$id]")
                    UiState.Error
                } else if (template == null) {
                    Timber.e("Error loading template(s)")
                    UiState.Error
                } else {
                    // Check for local offline content package
                    val localHtml = contentPackageManager.getHtmlContent(id)
                    val effectiveArticleContent = localHtml ?: bookmark.articleContent
                    val offlineBaseUrl = if (localHtml != null) {
                        com.mydeck.app.domain.content.OfflineContentPathHandler.buildBaseUrl(id)
                    } else null

                    val descNormalized = bookmark.description
                        .replace(Regex("<[^>]*>"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val effNormalized = effectiveArticleContent
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                    val hasUsefulBody = effNormalized != null && effNormalized.isNotEmpty() && effNormalized != descNormalized
                    val isAbsoluteImage = bookmark.image.src.startsWith("http://") ||
                        bookmark.image.src.startsWith("https://") ||
                        bookmark.image.src.startsWith("data:") ||
                        bookmark.image.src.startsWith("file://")

                    UiState.Success(
                        bookmark = Bookmark(
                            url = bookmark.url,
                            title = bookmark.title,
                            authors = bookmark.authors,
                            createdDate = formatLocalDateTimeWithDateFormat(bookmark.created),
                            publishedDate = bookmark.published?.let { formatLocalDateTimeWithDateFormat(it) },
                            publishedDateInput = bookmark.published?.let { formatLocalDateTimeForEditor(it) },
                            bookmarkId = id,
                            siteName = bookmark.siteName,
                            imgSrc = bookmark.image.src,
                            iconSrc = bookmark.icon.src,
                            thumbnailSrc = bookmark.thumbnail.src,
                            isFavorite = bookmark.isMarked,
                            isArchived = bookmark.isArchived,
                            isRead = bookmark.isRead(),
                            type = when (bookmark.type) {
                                is com.mydeck.app.domain.model.Bookmark.Type.Article -> Bookmark.Type.ARTICLE
                                is com.mydeck.app.domain.model.Bookmark.Type.Picture -> Bookmark.Type.PHOTO
                                is com.mydeck.app.domain.model.Bookmark.Type.Video -> Bookmark.Type.VIDEO
                            },
                            articleContent = effectiveArticleContent,
                            embed = bookmark.embed,
                            lang = bookmark.lang,
                            textDirection = bookmark.textDirection,
                            wordCount = bookmark.wordCount,
                            readingTime = bookmark.readingTime,
                            description = bookmark.description,
                            omitDescription = bookmark.omitDescription,
                            labels = bookmark.labels,
                            readProgress = bookmark.readProgress,
                            debugInfo = buildDebugInfo(bookmark),
                            hasContent = when (bookmark.type) {
                                is com.mydeck.app.domain.model.Bookmark.Type.Article -> !effectiveArticleContent.isNullOrBlank()
                                is com.mydeck.app.domain.model.Bookmark.Type.Video -> !effectiveArticleContent.isNullOrBlank() || !bookmark.embed.isNullOrBlank()
                                is com.mydeck.app.domain.model.Bookmark.Type.Picture ->
                                    offlineBaseUrl != null
                            },
                            offlineBaseUrl = offlineBaseUrl
                        ),
                        updateBookmarkState = updateState,
                        template = template,
                        typographySettings = typographySettings,
                        readerAppearanceSelection = readerAppearanceSelection
                    )
                }
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

    fun onUpdateTitle(bookmarkId: String, title: String) {
        updateBookmark {
            val result = bookmarkRepository.updateTitle(bookmarkId, title)
            when (result) {
                is BookmarkRepository.UpdateResult.Success -> UpdateBookmarkUseCase.Result.Success
                is BookmarkRepository.UpdateResult.Error -> UpdateBookmarkUseCase.Result.GenericError(result.errorMessage)
                is BookmarkRepository.UpdateResult.NetworkError -> UpdateBookmarkUseCase.Result.NetworkError(result.errorMessage)
            }
        }
    }

    fun onUpdateMetadata(bookmarkId: String, metadata: BookmarkMetadataUpdate) {
        updateBookmark {
            val result = bookmarkRepository.updateMetadata(bookmarkId, metadata)
            when (result) {
                is BookmarkRepository.UpdateResult.Success -> UpdateBookmarkUseCase.Result.Success
                is BookmarkRepository.UpdateResult.Error -> UpdateBookmarkUseCase.Result.GenericError(result.errorMessage)
                is BookmarkRepository.UpdateResult.NetworkError -> UpdateBookmarkUseCase.Result.NetworkError(result.errorMessage)
            }
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
            val result = update()
            updateState.value = when (result) {
                is UpdateBookmarkUseCase.Result.Success -> UpdateBookmarkState.Success()
                is UpdateBookmarkUseCase.Result.GenericError -> UpdateBookmarkState.Error(result.message)
                is UpdateBookmarkUseCase.Result.NetworkError -> UpdateBookmarkState.Error(result.message)
            }
        }
    }

    /**
     * Lightweight HTML-only refresh after annotation create/delete.
     * Fetches updated HTML via multipart (no resources), enriches annotations,
     * writes to disk, and emits a JS innerHTML replacement event.
     * Falls back to full multipart refresh if the lightweight path fails.
     */
    private suspend fun refreshAnnotationHtml(bookmarkId: String) {
        val enrichedHtml = loadContentPackageUseCase.refreshHtmlForAnnotations(bookmarkId)
        if (enrichedHtml != null) {
            cacheAnnotationSnapshot(bookmarkId)
            _annotationRefreshEvent.send(AnnotationRefreshEvent.HtmlRefresh(enrichedHtml))
        } else {
            // Fallback: full multipart refresh (will cause WebView reload)
            val result = loadContentPackageUseCase.executeForceRefresh(bookmarkId)
            if (result is LoadContentPackageUseCase.Result.Success) {
                cacheAnnotationSnapshot(bookmarkId)
            } else {
                throw IllegalStateException("Content refresh failed: $result")
            }
        }
    }

    /**
     * Update annotation color attribute in the on-disk index.html.
     * Used for color changes where only the attribute value changes.
     */
    private fun updateAnnotationColorOnDisk(bookmarkId: String, annotationIds: List<String>, color: String) {
        var html = contentPackageManager.getHtmlContent(bookmarkId) ?: return
        for (id in annotationIds) {
            val escapedId = Regex.escape(id)
            // Match entire <rd-annotation ...> opening tag containing this annotation ID,
            // then replace the color attribute within it (handles any attribute order)
            val tagRegex = Regex("""<rd-annotation\b([^>]*data-annotation-id-value="$escapedId"[^>]*)>""")
            html = tagRegex.replace(html) { match ->
                val attrs = match.groupValues[1]
                val updatedAttrs = attrs.replace(
                    Regex("""data-annotation-color="[^"]*""""),
                    """data-annotation-color="$color""""
                )
                "<rd-annotation$updatedAttrs>"
            }
        }
        contentPackageManager.updateHtml(bookmarkId, html)
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

            _shareIntent.send(intent)
        }
    }

    fun onClickOpenUrl(url: String){
        viewModelScope.launch {
            _openUrlEvent.send(url)
        }
    }

    fun onExportDebugJson() {
        val bookmarkId = _bookmarkId.value ?: return
        viewModelScope.launch {
            _debugExportEvent.send(DebugExportEvent.Exporting)
            val exporter = BookmarkDebugExporter(context, bookmarkRepository, json)
            val result = exporter.exportBookmarkDebugJson(bookmarkId)
            if (result.success) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    result.file
                )
                _debugExportEvent.send(DebugExportEvent.Ready(uri, result.file.name))
            } else {
                _debugExportEvent.send(
                    DebugExportEvent.Error(result.errorMessage ?: "Export failed")
                )
            }
        }
    }

    fun onClickBack() {
        // Save progress before navigating back
        viewModelScope.launch {
            saveCurrentProgress()
            _navigationEvent.send(NavigationEvent.NavigateBack)
        }
    }

    fun onTypographySettingsChanged(settings: com.mydeck.app.domain.model.TypographySettings) {
        viewModelScope.launch {
            settingsDataStore.saveTypographySettings(settings)
        }
    }

    fun onReaderThemeSelectionChanged(selection: ReaderAppearanceSelection) {
        viewModelScope.launch {
            val currentLightAppearance = settingsDataStore.getLightAppearance()
            val currentDarkAppearance = settingsDataStore.getDarkAppearance()
            val currentThemeMode = settingsDataStore.getTheme()

            if (selection.lightAppearance != currentLightAppearance) {
                settingsDataStore.saveLightAppearance(selection.lightAppearance)
            }
            if (selection.darkAppearance != currentDarkAppearance) {
                settingsDataStore.saveDarkAppearance(selection.darkAppearance)
            }
            if (selection.themeMode != currentThemeMode) {
                settingsDataStore.saveTheme(selection.themeMode)
            }
        }
    }

    private fun formatLocalDateTimeWithDateFormat(localDateTime: LocalDateTime): String {
        val dateFormat = DateFormat.getDateInstance(
            DateFormat.LONG
        )
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis = localDateTime.toInstant(timeZone).toEpochMilliseconds()
        return dateFormat.format(Date(epochMillis))
    }

    private fun formatLocalDateTimeForEditor(localDateTime: LocalDateTime): String {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis = localDateTime.toInstant(timeZone).toEpochMilliseconds()
        return dateFormat.format(Date(epochMillis))
    }

    private fun fetchContentOnDemand(bookmarkId: String) {
        _contentLoadState.value = ContentLoadState.Loading(0f)
        viewModelScope.launch {
            // Track the current floor so the ticker and milestone callbacks
            // both advance monotonically without jumping backwards.
            var progressFloor = 0f
            val setProgress: (Float) -> Unit = { value ->
                val clamped = value.coerceAtLeast(progressFloor)
                progressFloor = clamped
                _contentLoadState.value = ContentLoadState.Loading(clamped)
            }

            // The network fetch (0.10 → 0.55) is the slowest phase. Run a ticker
            // that advances the bar in small increments so it keeps moving visually.
            // When the fetch completes, the milestone callback jumps past the cap.
            val tickerJob = launch {
                val tickStart = 0.10f
                val tickCap = 0.50f   // don't exceed this; leave room to jump to 0.55
                val tickStep = 0.05f
                val tickInterval = 300L // ms
                var tick = tickStart
                setProgress(tick)
                while (tick < tickCap) {
                    delay(tickInterval)
                    tick = (tick + tickStep).coerceAtMost(tickCap)
                    setProgress(tick)
                }
            }

            val pkgResult = loadContentPackageUseCase.execute(bookmarkId, setProgress)
            tickerJob.cancel()

            // Only fall back to legacy LoadArticleUseCase for Article type.
            // For Picture and Video, the legacy GET /bookmarks/{id}/article endpoint
            // is wrong: it marks hasArticle=false bookmarks as PERMANENT_NO_CONTENT,
            // which permanently blocks the content package path from ever retrying.
            val isArticleType = bookmarkType is com.mydeck.app.domain.model.Bookmark.Type.Article

            val loadState = when (pkgResult) {
                is LoadContentPackageUseCase.Result.Success -> ContentLoadState.Loaded
                is LoadContentPackageUseCase.Result.AlreadyDownloaded -> ContentLoadState.Loaded
                is LoadContentPackageUseCase.Result.PermanentFailure -> {
                    if (isArticleType) {
                        setProgress(0.55f)
                        val legacyResult = loadArticleUseCase.execute(
                            bookmarkId,
                            markDirtyAfterSuccess = true
                        )
                        when (legacyResult) {
                            is LoadArticleUseCase.Result.Success -> ContentLoadState.Loaded
                            is LoadArticleUseCase.Result.AlreadyDownloaded -> ContentLoadState.Loaded
                            is LoadArticleUseCase.Result.TransientFailure -> ContentLoadState.Failed(
                                reason = legacyResult.reason, canRetry = true
                            )
                            is LoadArticleUseCase.Result.PermanentFailure -> ContentLoadState.Failed(
                                reason = legacyResult.reason, canRetry = false
                            )
                        }
                    } else {
                        ContentLoadState.Failed(reason = pkgResult.reason, canRetry = false)
                    }
                }
                is LoadContentPackageUseCase.Result.TransientFailure -> {
                    if (isArticleType) {
                        setProgress(0.55f)
                        val legacyResult = loadArticleUseCase.execute(
                            bookmarkId,
                            markDirtyAfterSuccess = true
                        )
                        when (legacyResult) {
                            is LoadArticleUseCase.Result.Success -> ContentLoadState.Loaded
                            is LoadArticleUseCase.Result.AlreadyDownloaded -> ContentLoadState.Loaded
                            is LoadArticleUseCase.Result.TransientFailure -> ContentLoadState.Failed(
                                reason = legacyResult.reason, canRetry = true
                            )
                            is LoadArticleUseCase.Result.PermanentFailure -> ContentLoadState.Failed(
                                reason = legacyResult.reason, canRetry = false
                            )
                        }
                    } else {
                        ContentLoadState.Failed(reason = pkgResult.reason, canRetry = true)
                    }
                }
            }
            _contentLoadState.value = loadState
        }
    }

    fun retryContentFetch() {
        _bookmarkId.value?.let { fetchContentOnDemand(it) }
    }

    fun forceRefreshContent() {
        val bookmarkId = _bookmarkId.value ?: return
        if (contentRefreshJob?.isActive == true) {
            return
        }

        val job = viewModelScope.launch {
            try {
                // Full content refresh (re-downloads entire multipart package)
                val result = loadContentPackageUseCase.executeForceRefresh(bookmarkId)
                if (result is LoadContentPackageUseCase.Result.Success) {
                    cacheAnnotationSnapshot(bookmarkId)
                } else {
                    throw IllegalStateException("Content refresh failed: $result")
                }
                updateState.value = UpdateBookmarkState.Success(
                    context.getString(R.string.content_refresh_completed)
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to refresh article content for $bookmarkId")
                updateState.value = UpdateBookmarkState.Error(
                    context.getString(R.string.content_refresh_failed)
                )
            }
        }

        contentRefreshJob = job
        job.invokeOnCompletion {
            if (contentRefreshJob === job) {
                contentRefreshJob = null
            }
        }
    }

    fun showAnnotationsSheet() {
        _showAnnotationsSheet.value = true
        _annotationsState.update { it.copy(isLoading = true) }
    }

    fun hideAnnotationsSheet() {
        _showAnnotationsSheet.value = false
    }

    fun fetchAnnotations(
        bookmarkId: String,
        renderedAnnotations: List<Annotation> = emptyList()
    ) {
        viewModelScope.launch {
            _annotationsState.update { it.copy(isLoading = true) }

            // 1. Load from local cache first (offline-capable)
            val cachedEntities = try {
                cachedAnnotationDao.getAnnotationsForBookmark(bookmarkId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load cached annotations for $bookmarkId")
                emptyList()
            }
            val cachedById = cachedEntities.associateBy { it.id }
            val renderedById = renderedAnnotations.associateBy { it.id }

            // 2. If online, merge with API data; otherwise use local cache
            if (connectivityMonitor.isNetworkAvailable()) {
                try {
                    val response = readeckApi.getAnnotations(bookmarkId)
                    if (response.isSuccessful) {
                        val annotationDtos = response.body().orEmpty()
                        val annotations = annotationDtos.map { dto ->
                            val cached = cachedById[dto.id]
                            val rendered = renderedById[dto.id]
                            Annotation(
                                id = dto.id,
                                bookmarkId = bookmarkId,
                                text = dto.text,
                                color = dto.color.takeIf { it.isNotBlank() }
                                    ?: rendered?.color ?: cached?.color ?: "yellow",
                                note = dto.note.takeIf { it.isNotBlank() }
                                    ?: rendered?.note ?: cached?.note,
                                created = dto.created
                            )
                        }
                        _annotationsState.value = AnnotationsState(
                            annotations = annotations,
                            isLoading = false
                        )
                        return@launch
                    } else {
                        Timber.w("Failed to load annotations for $bookmarkId: HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load annotations from API for $bookmarkId")
                }
            }

            // 3. Offline or API failed: use cached or rendered annotations
            val annotations = if (cachedEntities.isNotEmpty()) {
                cachedEntities.map { entity ->
                    val rendered = renderedById[entity.id]
                    Annotation(
                        id = entity.id,
                        bookmarkId = bookmarkId,
                        text = rendered?.text ?: entity.text,
                        color = rendered?.color ?: entity.color,
                        note = rendered?.note ?: entity.note,
                        created = entity.created
                    )
                }
            } else {
                renderedAnnotations
            }

            _annotationsState.value = AnnotationsState(
                annotations = annotations,
                isLoading = false
            )
        }
    }

    fun syncAnnotationsIfNeeded(bookmarkId: String) {
        if (annotationSyncJob?.isActive == true) {
            return
        }

        val job = viewModelScope.launch {
            loadArticleUseCase.refreshCachedArticleIfAnnotationsChanged(bookmarkId)
        }
        annotationSyncJob = job
        job.invokeOnCompletion {
            if (annotationSyncJob === job) {
                annotationSyncJob = null
            }
        }
    }

    /**
     * Check for annotation changes from other clients for content-package bookmarks.
     *
     * Compares the current annotation list from the REST API against the cached snapshot.
     * If annotations have changed, performs a lightweight HTML-only refresh and pushes
     * the update to the WebView via JS — no full page reload.
     */
    private fun syncAnnotationsForContentPackage(bookmarkId: String) {
        if (annotationSyncJob?.isActive == true) return

        val job = viewModelScope.launch {
            if (!connectivityMonitor.isNetworkAvailable()) return@launch

            try {
                val response = readeckApi.getAnnotations(bookmarkId)
                if (!response.isSuccessful) return@launch

                val annotations = response.body().orEmpty()
                val currentSnapshot = annotations.toAnnotationCachePayload(json)
                val cachedSnapshot = settingsDataStore.getCachedAnnotationSnapshot(bookmarkId)

                val htmlContent = contentPackageManager.getHtmlContent(bookmarkId)
                val htmlHasAnnotations = htmlContent?.contains("rd-annotation") == true

                val shouldRefresh = when {
                    cachedSnapshot == null ->
                        annotations.isNotEmpty() || htmlHasAnnotations
                    cachedSnapshot != currentSnapshot -> true
                    else -> false
                }

                if (shouldRefresh) {
                    Timber.d("Annotation change detected for $bookmarkId, refreshing HTML")
                    refreshAnnotationHtml(bookmarkId)
                } else if (cachedSnapshot == null && annotations.isEmpty()) {
                    // No annotations exist anywhere — save empty snapshot to avoid re-checking
                    settingsDataStore.saveCachedAnnotationSnapshot(bookmarkId, currentSnapshot)
                }
            } catch (e: Exception) {
                Timber.w(e, "Content-package annotation sync failed for $bookmarkId")
            }
        }
        annotationSyncJob = job
        job.invokeOnCompletion {
            if (annotationSyncJob === job) annotationSyncJob = null
        }
    }

    private suspend fun cacheAnnotationSnapshot(bookmarkId: String) {
        try {
            val response = readeckApi.getAnnotations(bookmarkId)
            if (response.isSuccessful) {
                settingsDataStore.saveCachedAnnotationSnapshot(
                    bookmarkId,
                    response.body().orEmpty().toAnnotationCachePayload(json)
                )
            } else {
                Timber.w("Failed to cache annotations for $bookmarkId: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cache annotations for $bookmarkId")
        }
    }

    fun setAnnotations(annotations: List<Annotation>) {
        _annotationsState.value = AnnotationsState(
            annotations = annotations,
            isLoading = false
        )
    }

    fun showCreateAnnotationSheet(
        selectionData: SelectionData,
        existingAnnotations: List<Annotation> = emptyList()
    ) {
        val annotationIds = selectionData.selectedAnnotationIds.distinct()
        _annotationEditState.value = AnnotationEditState(
            annotationIds = annotationIds,
            color = resolveInitialAnnotationColor(existingAnnotations),
            selectionData = selectionData.takeIf { annotationIds.isEmpty() },
            text = selectionData.text,
            previewLines = buildAnnotationPreviewLines(selectionData.text, existingAnnotations),
            noteText = resolveExistingNotes(existingAnnotations)
        )
    }

    fun showEditAnnotationSheet(annotation: Annotation) {
        Timber.d(
            "[AnnotationTap] Showing edit sheet for annotation=%s color=%s note=%s",
            annotation.id,
            annotation.color,
            !annotation.note.isNullOrBlank()
        )
        _annotationEditState.value = AnnotationEditState(
            annotationIds = listOf(annotation.id),
            color = annotation.color,
            selectionData = null,
            text = annotation.text,
            previewLines = buildAnnotationPreviewLines(annotation.text, listOf(annotation)),
            noteText = annotation.note?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun buildAnnotationPreviewLines(
        selectionText: String,
        existingAnnotations: List<Annotation>
    ): List<AnnotationPreviewLine> {
        val annotations = existingAnnotations
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotBlank() }
        if (annotations.isEmpty()) return emptyList()

        val normalizedSelection = selectionText.trim()
        if (normalizedSelection.isBlank()) {
            return annotations.map { annotation ->
                AnnotationPreviewLine(
                    text = annotation.text,
                    color = annotation.color
                )
            }
        }

        if (annotations.size == 1) {
            val annotation = annotations.single()
            return listOf(
                AnnotationPreviewLine(
                    text = annotation.text,
                    color = annotation.color,
                    selectedRange = findContainedRange(annotation.text, normalizedSelection)
                        ?.takeUnless { it.first == 0 && it.last == annotation.text.lastIndex }
                )
            )
        }

        val lastIndex = annotations.lastIndex
        return annotations.mapIndexed { index, annotation ->
            val selectedRange = when (index) {
                0 -> findSuffixRange(annotation.text, normalizedSelection)
                lastIndex -> findPrefixRange(annotation.text, normalizedSelection)
                else -> null
            }?.takeUnless { it.first == 0 && it.last == annotation.text.lastIndex }

            AnnotationPreviewLine(
                text = annotation.text,
                color = annotation.color,
                selectedRange = selectedRange
            )
        }
    }

    private fun findContainedRange(text: String, selectionText: String): IntRange? {
        val directMatch = text.indexOf(selectionText)
        if (directMatch >= 0) {
            return directMatch..<(directMatch + selectionText.length)
        }

        val lowercaseText = text.lowercase()
        val lowercaseSelection = selectionText.lowercase()
        val caseInsensitiveMatch = lowercaseText.indexOf(lowercaseSelection)
        if (caseInsensitiveMatch >= 0) {
            return caseInsensitiveMatch..<(caseInsensitiveMatch + selectionText.length)
        }

        return null
    }

    private fun findSuffixRange(text: String, selectionText: String): IntRange? {
        val lowercaseText = text.lowercase()
        val lowercaseSelection = selectionText.lowercase()
        val maxLength = minOf(text.length, selectionText.length)

        for (length in maxLength downTo 1) {
            if (lowercaseText.endsWith(lowercaseSelection.take(length))) {
                val start = text.length - length
                return start..<(start + length)
            }
        }

        return findContainedRange(text, selectionText)
    }

    private fun findPrefixRange(text: String, selectionText: String): IntRange? {
        val lowercaseText = text.lowercase()
        val lowercaseSelection = selectionText.lowercase()
        val maxLength = minOf(text.length, selectionText.length)

        for (length in maxLength downTo 1) {
            if (lowercaseText.startsWith(lowercaseSelection.takeLast(length))) {
                return 0..<length
            }
        }

        return findContainedRange(text, selectionText)
    }

    fun dismissAnnotationEditSheet() {
        _annotationEditState.value = null
    }

    fun onAnnotationEditColorSelected(color: String) {
        _annotationEditState.update { state ->
            state?.copy(color = color)
        }
    }

    fun saveAnnotationEdit() {
        val state = _annotationEditState.value ?: return
        val selectionData = state.selectionData
        if (state.isSaving) return

        if (state.annotationIds.isNotEmpty()) {
            updateAnnotationColors(
                annotationIds = state.annotationIds,
                color = state.color
            )
        } else if (selectionData != null) {
            createAnnotation(
                startSelector = selectionData.startSelector,
                startOffset = selectionData.startOffset,
                endSelector = selectionData.endSelector,
                endOffset = selectionData.endOffset,
                color = state.color
            )
        }
    }

    fun deleteCurrentAnnotation() {
        val annotationIds = _annotationEditState.value?.annotationIds.orEmpty()
        if (annotationIds.isEmpty()) return
        deleteAnnotations(annotationIds)
    }

    fun createAnnotation(
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        color: String
    ) {
        val bookmarkId = _bookmarkId.value ?: return
        if (!connectivityMonitor.isNetworkAvailable()) {
            _annotationEditState.value = null
            updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_offline_unavailable))
            return
        }
        _annotationEditState.update { it?.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val response = readeckApi.createAnnotation(
                    bookmarkId = bookmarkId,
                    body = CreateAnnotationDto(
                        start_selector = startSelector,
                        start_offset = startOffset,
                        end_selector = endSelector,
                        end_offset = endOffset,
                        color = color
                    )
                )

                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code()}")
                }

                refreshAnnotationHtml(bookmarkId)
                _annotationEditState.value = null
            } catch (e: Exception) {
                Timber.w(e, "Failed to create annotation for $bookmarkId")
                _annotationEditState.update { it?.copy(isSaving = false) }
                updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_create_failed))
            }
        }
    }

    fun updateAnnotationColor(annotationId: String, color: String) {
        updateAnnotationColors(listOf(annotationId), color)
    }

    fun updateAnnotationColors(annotationIds: List<String>, color: String) {
        val bookmarkId = _bookmarkId.value ?: return
        if (!connectivityMonitor.isNetworkAvailable()) {
            _annotationEditState.value = null
            updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_offline_unavailable))
            return
        }
        _annotationEditState.update { it?.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                annotationIds.distinct().forEach { annotationId ->
                    val response = readeckApi.updateAnnotation(
                        bookmarkId = bookmarkId,
                        annotationId = annotationId,
                        body = UpdateAnnotationDto(color = color)
                    )

                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code()}")
                    }
                }

                // Color change: instant JS update + disk persistence (no full HTML refresh)
                _annotationRefreshEvent.send(AnnotationRefreshEvent.ColorUpdate(annotationIds, color))
                updateAnnotationColorOnDisk(bookmarkId, annotationIds, color)
                cacheAnnotationSnapshot(bookmarkId)
                _annotationEditState.value = null
            } catch (e: Exception) {
                Timber.w(e, "Failed to update annotations ${annotationIds.joinToString()} for $bookmarkId")
                _annotationEditState.update { it?.copy(isSaving = false) }
                updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_update_failed))
            }
        }
    }

    fun deleteAnnotation(annotationId: String) {
        deleteAnnotations(listOf(annotationId))
    }

    fun deleteAnnotations(annotationIds: List<String>) {
        val bookmarkId = _bookmarkId.value ?: return
        if (!connectivityMonitor.isNetworkAvailable()) {
            _annotationEditState.value = null
            updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_offline_unavailable))
            return
        }
        _annotationEditState.update { it?.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                annotationIds.distinct().forEach { annotationId ->
                    val response = readeckApi.deleteAnnotation(
                        bookmarkId = bookmarkId,
                        annotationId = annotationId
                    )

                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code()}")
                    }
                }

                refreshAnnotationHtml(bookmarkId)
                _annotationEditState.value = null
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete annotations ${annotationIds.joinToString()} for $bookmarkId")
                _annotationEditState.update { it?.copy(isSaving = false) }
                updateState.value = UpdateBookmarkState.Error(context.getString(R.string.highlight_delete_failed))
            }
        }
    }

    private fun resolveInitialAnnotationColor(existingAnnotations: List<Annotation>): String {
        if (existingAnnotations.isEmpty()) {
            return "yellow"
        }

        val distinctColors = existingAnnotations.map { it.color }.distinct()
        return when {
            distinctColors.size == 1 -> distinctColors.single()
            else -> existingAnnotations.first().color
        }
    }

    private fun resolveExistingNotes(existingAnnotations: List<Annotation>): String? {
        val notes = existingAnnotations
            .mapNotNull { annotation ->
                annotation.note?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinct()

        return when {
            notes.isEmpty() -> null
            else -> notes.joinToString(separator = "\n\n")
        }
    }

    fun scrollToAnnotation(annotationId: String) {
        _showAnnotationsSheet.value = false
        _pendingAnnotationScrollId.value = annotationId
    }

    fun onAnnotationScrollHandled() {
        _pendingAnnotationScrollId.value = null
    }

    sealed class ContentLoadState {
        data object Idle : ContentLoadState()
        data class Loading(val progress: Float = 0f) : ContentLoadState()
        data object Loaded : ContentLoadState()
        data class Failed(val reason: String, val canRetry: Boolean) : ContentLoadState()
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }

    sealed class DebugExportEvent {
        data object Exporting : DebugExportEvent()
        data class Ready(val uri: Uri, val fileName: String) : DebugExportEvent()
        data class Error(val message: String) : DebugExportEvent()
    }

    sealed class UiState {
        data class Success(
            val bookmark: Bookmark, 
            val updateBookmarkState: UpdateBookmarkState?, 
            val template: Template, 
            val typographySettings: com.mydeck.app.domain.model.TypographySettings,
            val readerAppearanceSelection: ReaderAppearanceSelection
        ) : UiState()

        data object Loading : UiState()
        data object Error : UiState()
    }

    data class Bookmark(
        val url: String,
        val title: String,
        val authors: List<String>,
        val createdDate: String,
        val publishedDate: String?,
        val publishedDateInput: String?,
        val bookmarkId: String,
        val siteName: String,
        val imgSrc: String,
        val iconSrc: String,
        val thumbnailSrc: String,
        val isFavorite: Boolean,
        val isArchived: Boolean,
        val isRead: Boolean,
        val type: Type,
        val articleContent: String?,
        val embed: String?,
        val lang: String,
        val textDirection: String,
        val wordCount: Int?,
        val readingTime: Int?,
        val description: String,
        val omitDescription: Boolean?,
        val labels: List<String>,
        val readProgress: Int,
        val debugInfo: String = "",
        val hasContent: Boolean,
        val offlineBaseUrl: String? = null
    ) {
        enum class Type {
            ARTICLE, PHOTO, VIDEO
        }

        fun shouldShowHeaderDescription(): Boolean {
            if (offlineBaseUrl != null && (type == Type.PHOTO || type == Type.VIDEO)) return false
            return description.isNotBlank() && !(
                omitDescription == true &&
                    !articleContent.isNullOrBlank()
                )
        }

        private fun normalizeText(s: String): String {
            return s
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
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
                    if (offlineBaseUrl != null && articleContent != null) {
                        htmlTemplate.replace("%s", articleContent)
                    } else {
                        val articleNormalized = articleContent?.let { normalizeText(it) }
                        val descNormalized = normalizeText(description)
                        val textPart = if (articleContent != null && articleNormalized != descNormalized) articleContent else ""
                        val imagePart = """<img src="$imgSrc"/>"""
                        htmlTemplate.replace("%s", imagePart + textPart)
                    }
                }

                Type.VIDEO -> {
                    val articleNormalized = articleContent?.let { normalizeText(it) }
                    val descNormalized = normalizeText(description)
                    val textPart = when {
                        articleContent != null && articleNormalized != descNormalized -> articleContent
                        !shouldShowHeaderDescription() && description.isNotBlank() -> "<p>$description</p>"
                        else -> ""
                    }
                    val embedPart = embed?.let { raw ->
                        if (raw.contains("<iframe")) {
                            """<div class="video-embed">$raw</div>"""
                        } else {
                            raw
                        }
                    } ?: ""
                    val content = embedPart + textPart
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
        data class Success(val message: String? = null) : UpdateBookmarkState()
        data class Error(val message: String) : UpdateBookmarkState()
    }

    private fun buildDebugInfo(bookmark: com.mydeck.app.domain.model.Bookmark): String {
        return buildString {
            appendLine("=== BOOKMARK DEBUG INFO ===")
            appendLine()
            appendLine("ID: ${bookmark.id}")
            appendLine("State: ${bookmark.state}")
            appendLine("Loaded: ${bookmark.loaded}")
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
            appendLine("  Omit Description: ${bookmark.omitDescription ?: "N/A"}")
            appendLine("  Has Article (server): ${bookmark.hasArticle}")
            appendLine("  Content State: ${bookmark.contentState}")
            if (bookmark.contentFailureReason != null) {
                appendLine("  Content Failure Reason: ${bookmark.contentFailureReason}")
            }
            appendLine("  Has Local Article Content: ${bookmark.articleContent != null}")
            if (bookmark.articleContent != null) {
                appendLine("  Article Content Length: ${bookmark.articleContent.length} chars")
                val preview = bookmark.articleContent.take(200)
                    .replace(Regex("<[^>]*>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(100)
                appendLine("  Content Preview: $preview")
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

    // Gallery functions
    fun onImageTapped(data: ImageGalleryData) {
        _galleryData.value = data
    }

    fun onDismissGallery() {
        _galleryData.value = null
    }

    fun onGalleryPageChanged(page: Int) {
        _galleryData.update { current -> current?.copy(currentIndex = page) }
    }

    // Reader context menu functions
    fun onShowImageContextMenu(imageUrl: String, imageAlt: String?, linkUrl: String?, linkType: String) {
        _readerContextMenu.value = ReaderContextMenuState(
            visible = true,
            imageUrl = imageUrl,
            imageAlt = imageAlt?.ifBlank { null },
            linkUrl = linkUrl,
            linkType = linkType,
        )
    }

    fun onShowLinkContextMenu(linkUrl: String, linkText: String = "") {
        _readerContextMenu.value = ReaderContextMenuState(
            visible = true,
            imageUrl = null,
            linkUrl = linkUrl,
            linkText = linkText.ifBlank { null },
            linkType = "page",
        )
    }

    fun onDismissReaderContextMenu() {
        _readerContextMenu.value = ReaderContextMenuState()
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
        _articleSearchState.update {
            it.copy(
                query = query,
                totalMatches = 0,
                currentMatch = 0
            )
        }

        // Debounce search execution
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            // Signal that search should be executed
            // The actual search is performed in the UI layer via WebViewSearchBridge
        }
    }

    fun onArticleSearchUpdateResults(totalMatches: Int, preferredMatch: Int = 0) {
        _articleSearchState.update { state ->
            val newCurrent = if (totalMatches > 0) {
                when {
                    state.currentMatch in 1..totalMatches -> state.currentMatch
                    preferredMatch in 1..totalMatches -> preferredMatch
                    else -> 1
                }
            } else {
                0
            }
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

    data class AnnotationsState(
        val annotations: List<Annotation> = emptyList(),
        val isLoading: Boolean = false
    )

    data class AnnotationEditState(
        val annotationIds: List<String> = emptyList(),
        val color: String,
        val selectionData: SelectionData?,
        val text: String,
        val previewLines: List<AnnotationPreviewLine> = emptyList(),
        val noteText: String? = null,
        val isSaving: Boolean = false
    ) {
        val annotationId: String?
            get() = annotationIds.singleOrNull()

        val hasExistingAnnotations: Boolean
            get() = annotationIds.isNotEmpty()
    }

    data class AnnotationPreviewLine(
        val text: String,
        val color: String,
        val selectedRange: IntRange? = null
    )

    data class ReaderContextMenuState(
        val visible: Boolean = false,
        val imageUrl: String? = null,
        val imageAlt: String? = null,
        val linkUrl: String? = null,
        val linkText: String? = null,
        val linkType: String = "none",  // "none" | "image" | "page"
    )

    /**
     * Events for in-place WebView annotation updates that avoid full page reload.
     */
    sealed class AnnotationRefreshEvent {
        /** Update annotation color attributes in the DOM via JS. No page reload needed. */
        data class ColorUpdate(val annotationIds: List<String>, val color: String) : AnnotationRefreshEvent()
        /** Replace .container innerHTML with fresh HTML. Used after create/delete. */
        data class HtmlRefresh(val containerHtml: String) : AnnotationRefreshEvent()
    }

}
