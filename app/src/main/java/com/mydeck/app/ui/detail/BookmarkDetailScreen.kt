package com.mydeck.app.ui.detail

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.icu.text.MessageFormat
import android.net.Uri
import android.view.View
import android.webkit.WebView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import com.mydeck.app.ui.components.LongPressContextMenuDialog
import com.mydeck.app.ui.components.LongPressContextMenuItem
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mydeck.app.ui.theme.Dimens
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.mydeck.app.R
import com.mydeck.app.domain.model.Annotation
import com.mydeck.app.domain.model.ImageGalleryData
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.detail.BookmarkDetailViewModel.ContentLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.imageLoader
import com.mydeck.app.ui.detail.components.*

private const val PendingDeleteFromDetailKey = "pending_delete_bookmark_id"

@Composable
fun BookmarkDetailScreen(navHostController: NavController, bookmarkId: String?, showOriginal: Boolean = false) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel()
    BookmarkDetailHost(
        viewModel = viewModel,
        showOriginal = showOriginal,
        onNavigateBack = { pendingDeleteBookmarkId ->
            if (pendingDeleteBookmarkId != null) {
                navHostController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(PendingDeleteFromDetailKey, pendingDeleteBookmarkId)
            }
            navHostController.popBackStack()
        }
    )
}

@Composable
fun BookmarkDetailHost(
    viewModel: BookmarkDetailViewModel,
    showOriginal: Boolean,
    onNavigateBack: (String?) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissPendingDeleteSnackbar: () -> Unit = {
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    val onClickBack: () -> Unit = {
        dismissPendingDeleteSnackbar()
        viewModel.onClickBack()
    }
    val onClickToggleFavorite: (String, Boolean) -> Unit =
        { id, isFavorite ->
            dismissPendingDeleteSnackbar()
            viewModel.onToggleFavorite(id, isFavorite)
        }
    val onClickToggleArchive: (String, Boolean) -> Unit =
        { id, isArchived ->
            dismissPendingDeleteSnackbar()
            viewModel.onToggleArchive(id, isArchived)
        }
    val context = LocalContext.current
    val onClickOpenUrl: (String) -> Unit = {
        dismissPendingDeleteSnackbar()
        viewModel.onClickOpenUrl(it)
    }
    val onClickShareBookmark: (String) -> Unit = { url ->
        dismissPendingDeleteSnackbar()
        viewModel.onClickShareBookmark(url)
    }
    val onClickToggleRead: (String, Boolean) -> Unit = { id, isRead ->
        dismissPendingDeleteSnackbar()
        viewModel.onToggleRead(id, isRead)
    }
    val onUpdateLabels: (String, List<String>) -> Unit = { id, labels -> viewModel.onUpdateLabels(id, labels) }
    val uiState = viewModel.uiState.collectAsState().value
    val contentLoadState = viewModel.contentLoadState.collectAsState().value
    val articleSearchState = viewModel.articleSearchState.collectAsState().value
    val labelsWithCounts = viewModel.labelsWithCounts.collectAsState().value
    val galleryData = viewModel.galleryData.collectAsState().value
    val readerContextMenu = viewModel.readerContextMenu.collectAsState().value
    val annotations = viewModel.annotations.collectAsState().value
    val isAnnotationPanelOpen = viewModel.isAnnotationPanelOpen.collectAsState().value
    val isAnnotationSelectionActive = viewModel.isAnnotationSelectionActive.collectAsState().value
    val pendingSelection = viewModel.pendingSelection.collectAsState().value
    val tappedAnnotationId = viewModel.tappedAnnotationId.collectAsState().value
    val scrollToAnnotationId = viewModel.scrollToAnnotationId.collectAsState().value
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showTypographyPanel by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val onClickDeleteBookmark: (String) -> Unit = { id ->
        // Hand off deletion flow to list screen (Gmail-like): immediate back + staged delete there.
        onNavigateBack(id)
    }

    val onClickOpenInBrowser: (String) -> Unit = { url ->
        dismissPendingDeleteSnackbar()
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle error silently or show snackbar
        }
    }

    // Search callbacks
    val onArticleSearchActivate = { viewModel.onArticleSearchActivate() }
    val onArticleSearchDeactivate = { viewModel.onArticleSearchDeactivate() }
    val onArticleSearchQueryChange = { query: String -> viewModel.onArticleSearchQueryChange(query) }
    val onArticleSearchNext = { viewModel.onArticleSearchNext() }
    val onArticleSearchPrevious = { viewModel.onArticleSearchPrevious() }
    val onArticleSearchUpdateResults = { totalMatches: Int -> viewModel.onArticleSearchUpdateResults(totalMatches) }

    // Gallery and context menu callbacks
    val onImageTapped = { data: ImageGalleryData -> viewModel.onImageTapped(data) }
    val onImageLongPress = { imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String ->
        viewModel.onShowImageContextMenu(imageUrl, imageAlt, linkUrl, linkType)
    }
    val onLinkLongPress = { linkUrl: String, linkText: String -> viewModel.onShowLinkContextMenu(linkUrl, linkText) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                snackbarHostState.currentSnackbarData?.dismiss()
                val state = viewModel.uiState.value
                if (state is BookmarkDetailViewModel.UiState.Success &&
                    state.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE) {
                    viewModel.saveProgressOnPause()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { event ->
            when (event) {
                is BookmarkDetailViewModel.NavigationEvent.NavigateBack -> {
                    onNavigateBack(null)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collectLatest { url ->
            openUrlInCustomTab(context, url)
        }
    }

    val keepScreenOn by viewModel.keepScreenOnWhileReading.collectAsState()
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    when (uiState) {
        is BookmarkDetailViewModel.UiState.Success -> {
            var contentMode by remember(uiState.bookmark.bookmarkId) {
                mutableStateOf(
                    if (showOriginal) ContentMode.ORIGINAL
                    else ContentMode.READER
                )
            }

            // Auto-switch to Original mode when content fetch fails (any reason)
            // This handles both permanent failures (no server content) and
            // transient failures (server error fetching article)
            // Include contentMode in the key so manual "View article" toggles after a failed
            // fetch still snap back to Original mode when extracted content is unavailable.
            LaunchedEffect(contentLoadState, contentMode, uiState.bookmark.hasContent) {
                if (contentLoadState is ContentLoadState.Failed &&
                    contentMode == ContentMode.READER &&
                    !uiState.bookmark.hasContent) {
                    contentMode = ContentMode.ORIGINAL
                }
            }

            LaunchedEffect(key1 = uiState) {
                uiState.updateBookmarkState?.let {
                    when (it) {
                        is BookmarkDetailViewModel.UpdateBookmarkState.Success -> { }
                        is BookmarkDetailViewModel.UpdateBookmarkState.Error -> {
                            snackbarHostState.showSnackbar(
                                message = it.message,
                                duration = SnackbarDuration.Long
                            )
                        }
                    }
                    viewModel.onUpdateBookmarkStateConsumed()
                }
            }
            // Box ensures the gallery overlay stacks directly on top of the
            // reader screen regardless of how the NavHost lays out its children.
            Box(modifier = Modifier.fillMaxSize()) {
                BookmarkDetailScreen(
                    modifier = Modifier,
                    snackbarHostState = snackbarHostState,
                    onClickBack = if (articleSearchState.isActive) onArticleSearchDeactivate else onClickBack,
                    onClickToggleFavorite = onClickToggleFavorite,
                    onClickToggleArchive = onClickToggleArchive,
                    onClickToggleRead = onClickToggleRead,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickDeleteBookmark = onClickDeleteBookmark,
                    onClickOpenInBrowser = onClickOpenInBrowser,
                    onArticleSearchActivate = onArticleSearchActivate,
                    uiState = uiState,
                    onClickOpenUrl = onClickOpenUrl,
                    onShowDetails = { showDetailsDialog = true },
                    onScrollProgressChanged = { progress ->
                        viewModel.onScrollProgressChanged(progress)
                    },
                    initialReadProgress = viewModel.getInitialReadProgress(),
                    contentMode = contentMode,
                    onContentModeChange = { contentMode = it },
                    contentLoadState = contentLoadState,
                    articleSearchState = articleSearchState,
                    onArticleSearchDeactivate = onArticleSearchDeactivate,
                    onArticleSearchQueryChange = onArticleSearchQueryChange,
                    onArticleSearchNext = onArticleSearchNext,
                    onArticleSearchPrevious = onArticleSearchPrevious,
                    onArticleSearchUpdateResults = onArticleSearchUpdateResults,
                    onShowTypographyPanel = { showTypographyPanel = true },
                    onTitleChanged = { newTitle ->
                        viewModel.onUpdateTitle(uiState.bookmark.bookmarkId, newTitle)
                    },
                    onImageTapped = onImageTapped,
                    onImageLongPress = onImageLongPress,
                    onLinkLongPress = onLinkLongPress,
                    isAnnotationSelectionActive = isAnnotationSelectionActive,
                    onAnnotationToolbarClick = { viewModel.onAnnotationToolbarClick() },
                    annotations = annotations,
                    scrollToAnnotationId = scrollToAnnotationId,
                    onTextSelected = viewModel::onTextSelected,
                    onAnnotationClicked = viewModel::onAnnotationClicked,
                    onScrollToAnnotationConsumed = viewModel::onScrollToAnnotationConsumed,
                )

                // Gallery draws on top of the reader screen. fillMaxSize() fills the
                // full edge-to-edge area; system bar insets are handled within the
                // gallery composable itself.
                if (galleryData != null) {
                    ImageGalleryOverlay(
                        galleryData = galleryData,
                        onDismiss = { viewModel.onDismissGallery() },
                        onOpenLink = { url -> openUrlInCustomTab(context, url) },
                        onPageChanged = { page -> viewModel.onGalleryPageChanged(page) },
                    )
                }
            }

            // Reader context menu
            if (readerContextMenu.visible) {
                ReaderContextMenu(
                    state = readerContextMenu,
                    onDismiss = { viewModel.onDismissReaderContextMenu() },
                    bookmarkTitle = uiState.bookmark.title,
                    bookmarkIconUrl = uiState.bookmark.iconSrc,
                )
            }

            // Handle share intent events
            LaunchedEffect(Unit) {
                viewModel.shareIntent.collectLatest { intent ->
                    val chooser = Intent.createChooser(intent, null)
                    context.startActivity(chooser)
                }
            }
            // Handle debug export events
            LaunchedEffect(Unit) {
                viewModel.debugExportEvent.collectLatest { event ->
                    when (event) {
                        is BookmarkDetailViewModel.DebugExportEvent.Ready -> {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, event.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(shareIntent, "Export Debug JSON")
                            context.startActivity(chooser)
                        }
                        is BookmarkDetailViewModel.DebugExportEvent.Error -> {
                            snackbarHostState.showSnackbar(
                                message = "Export failed: ${event.message}",
                                duration = SnackbarDuration.Short
                            )
                        }
                        is BookmarkDetailViewModel.DebugExportEvent.Exporting -> {
                            snackbarHostState.showSnackbar(
                                message = "Exporting debug data...",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }
            if (showDetailsDialog) {
                BookmarkDetailsDialog(
                    bookmark = uiState.bookmark,
                    onDismissRequest = { showDetailsDialog = false },
                    onLabelsUpdate = { newLabels ->
                        onUpdateLabels(uiState.bookmark.bookmarkId, newLabels)
                    },
                    existingLabels = labelsWithCounts.keys.toList(),
                    onExportDebugJson = { viewModel.onExportDebugJson() },
                    onClickOpenUrl = onClickOpenUrl,
                    onClickOpenInBrowser = onClickOpenInBrowser
                )
            }
            if (showTypographyPanel) {
                ReaderSettingsBottomSheet(
                    currentSettings = uiState.typographySettings,
                    onSettingsChanged = { settings ->
                        viewModel.onTypographySettingsChanged(settings)
                    },
                    onDismiss = { showTypographyPanel = false }
                )
            }
            if (isAnnotationPanelOpen) {
                AnnotationsPanel(
                    annotations = annotations,
                    onStartSelection = { viewModel.onStartAnnotationSelection() },
                    onScrollToAnnotation = { id -> viewModel.onScrollToAnnotation(id) },
                    onDeleteAnnotation = { id -> viewModel.onDeleteAnnotation(id) },
                    onDismiss = { viewModel.onToggleAnnotationPanel() },
                )
            }
            if (pendingSelection != null) {
                AnnotationCreationSheet(
                    pendingSelection = pendingSelection,
                    onCreateAnnotation = { startSelector, startOffset, endSelector, endOffset, color ->
                        viewModel.onCreateAnnotation(startSelector, startOffset, endSelector, endOffset, color)
                    },
                    onDismiss = { viewModel.onDismissCreationSheet() },
                )
            }
            if (tappedAnnotationId != null) {
                val tappedAnnotation = annotations.find { it.id == tappedAnnotationId }
                if (tappedAnnotation != null) {
                    AnnotationActionSheet(
                        annotation = tappedAnnotation,
                        onUpdateColor = { color -> viewModel.onUpdateAnnotationColor(tappedAnnotationId, color) },
                        onDelete = { viewModel.onDeleteAnnotation(tappedAnnotationId) },
                        onDismiss = { viewModel.onDismissActionSheet() },
                    )
                }
            }
        }

        is BookmarkDetailViewModel.UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        else -> {
            BookmarkDetailErrorScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailScreen(
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    onClickBack: () -> Unit,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickToggleFavorite: (String, Boolean) -> Unit,
    onClickToggleArchive: (String, Boolean) -> Unit,
    onClickToggleRead: (String, Boolean) -> Unit,
    onClickDeleteBookmark: (String) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit = {},
    onArticleSearchActivate: () -> Unit = {},
    onShowDetails: () -> Unit = {},
    onScrollProgressChanged: (Int) -> Unit = {},
    initialReadProgress: Int = 0,
    contentMode: ContentMode = ContentMode.READER,
    onContentModeChange: (ContentMode) -> Unit = {},
    contentLoadState: ContentLoadState = ContentLoadState.Idle,
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState = BookmarkDetailViewModel.ArticleSearchState(),
    onArticleSearchDeactivate: () -> Unit = {},
    onArticleSearchQueryChange: (String) -> Unit = {},
    onArticleSearchNext: () -> Unit = {},
    onArticleSearchPrevious: () -> Unit = {},
    onArticleSearchUpdateResults: (Int) -> Unit = {},
    onShowTypographyPanel: () -> Unit = {},
    onTitleChanged: ((String) -> Unit)? = null,
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    isAnnotationSelectionActive: Boolean = false,
    onAnnotationToolbarClick: () -> Unit = {},
    annotations: List<Annotation> = emptyList(),
    scrollToAnnotationId: String? = null,
    onTextSelected: (startSelector: String, startOffset: Int, endSelector: String, endOffset: Int, text: String) -> Unit = { _, _, _, _, _ -> },
    onAnnotationClicked: (annotationId: String) -> Unit = {},
    onScrollToAnnotationConsumed: () -> Unit = {},
) {
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            BookmarkDetailTopBar(
                articleSearchState = articleSearchState,
                onArticleSearchQueryChange = onArticleSearchQueryChange,
                onArticleSearchPrevious = onArticleSearchPrevious,
                onArticleSearchNext = onArticleSearchNext,
                onArticleSearchDeactivate = onArticleSearchDeactivate,
                onClickBack = onClickBack,
                uiState = uiState,
                onClickToggleFavorite = onClickToggleFavorite,
                onClickToggleArchive = onClickToggleArchive,
                onShowTypographyPanel = onShowTypographyPanel,
                onShowDetails = onShowDetails,
                contentMode = contentMode,
                onClickToggleRead = onClickToggleRead,
                onClickShareBookmark = onClickShareBookmark,
                onClickDeleteBookmark = onClickDeleteBookmark,
                onArticleSearchActivate = onArticleSearchActivate,
                onClickOpenInBrowser = onClickOpenInBrowser,
                onContentModeChange = onContentModeChange,
                isAnnotationSelectionActive = isAnnotationSelectionActive,
                onAnnotationToolbarClick = onAnnotationToolbarClick,
                scrollBehavior = topBarScrollBehavior,
                scrollState = scrollState,
                onScrollToTop = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(0)
                    }
                },
            )
        }
    ) { padding ->
        // Main content with buttons at the end
        BookmarkDetailContent(
            modifier = Modifier.fillMaxSize().padding(padding),
            uiState = uiState,
            onClickOpenUrl = onClickOpenUrl,
            onScrollProgressChanged = onScrollProgressChanged,
            initialReadProgress = initialReadProgress,
            contentMode = contentMode,
            contentLoadState = contentLoadState,
            articleSearchState = articleSearchState,
            onArticleSearchUpdateResults = onArticleSearchUpdateResults,
            onTitleChanged = onTitleChanged,
            onImageTapped = onImageTapped,
            onImageLongPress = onImageLongPress,
            onLinkLongPress = onLinkLongPress,
            onClickToggleFavorite = onClickToggleFavorite,
            onClickToggleArchive = onClickToggleArchive,
            scrollState = scrollState,
            annotations = annotations,
            scrollToAnnotationId = scrollToAnnotationId,
            isAnnotationSelectionActive = isAnnotationSelectionActive,
            onTextSelected = onTextSelected,
            onAnnotationClicked = onAnnotationClicked,
            onScrollToAnnotationConsumed = onScrollToAnnotationConsumed,
        )
    }
}

@Composable
fun BookmarkDetailContent(
    modifier: Modifier = Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickOpenUrl: (String) -> Unit,
    onScrollProgressChanged: (Int) -> Unit = {},
    initialReadProgress: Int = 0,
    contentMode: ContentMode = ContentMode.READER,
    contentLoadState: ContentLoadState = ContentLoadState.Idle,
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState = BookmarkDetailViewModel.ArticleSearchState(),
    onArticleSearchUpdateResults: (Int) -> Unit = {},
    onTitleChanged: ((String) -> Unit)? = null,
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onClickToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onClickToggleArchive: (String, Boolean) -> Unit = { _, _ -> },
    scrollState: ScrollState = rememberScrollState(),
    annotations: List<Annotation> = emptyList(),
    scrollToAnnotationId: String? = null,
    isAnnotationSelectionActive: Boolean = false,
    onTextSelected: (startSelector: String, startOffset: Int, endSelector: String, endOffset: Int, text: String) -> Unit = { _, _, _, _, _ -> },
    onAnnotationClicked: (annotationId: String) -> Unit = {},
    onScrollToAnnotationConsumed: () -> Unit = {},
) {
    val hasArticleContent = uiState.bookmark.articleContent != null
    val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
    val needsRestore = isArticle && hasArticleContent && initialReadProgress > 0 && initialReadProgress <= 100
    val hasReaderContent = uiState.bookmark.hasContent && contentMode == ContentMode.READER
    // Key on hasArticleContent so when content arrives after on-demand fetch,
    // the state resets and scroll position restore is triggered
    var hasRestoredPosition by remember(hasArticleContent) { mutableStateOf(!needsRestore) }
    var isReaderContentReady by remember(
        uiState.bookmark.bookmarkId,
        uiState.bookmark.articleContent,
        uiState.bookmark.embed,
        contentMode
    ) {
        mutableStateOf(false)
    }
    var lastReportedProgress by remember { mutableStateOf(-1) }

    LaunchedEffect(hasReaderContent) {
        if (!hasReaderContent) {
            isReaderContentReady = false
        }
    }

    // Keep the spinner while reader content is loading/rendering, but not after a failed fetch.
    val showReaderLoadingOverlay =
        contentMode == ContentMode.READER &&
            (
                (uiState.bookmark.hasContent && !isReaderContentReady) ||
                    (!uiState.bookmark.hasContent && contentLoadState !is ContentLoadState.Failed)
                )

    // Restore scroll position when content is loaded (using initial progress, not reactive)
    LaunchedEffect(scrollState.maxValue) {
        if (!hasRestoredPosition && scrollState.maxValue > 0 && initialReadProgress > 0 && initialReadProgress <= 100) {
            val targetPosition = (scrollState.maxValue * initialReadProgress / 100f).toInt()
            scrollState.scrollTo(targetPosition)
            hasRestoredPosition = true
        }
    }

    // Track scroll progress and report changes (only depends on scroll value, not bookmark updates)
    // Only report when progress actually changes to avoid spam
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        val progress = if (scrollState.maxValue > 0) {
            ((scrollState.value.toFloat() / scrollState.maxValue.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            // Content fits on screen or is loading.
            // Returning 0 prevents premature 'read' lock on long articles that are still loading.
            0
        }

        // Only report if progress changed
        if (progress != lastReportedProgress) {
            lastReportedProgress = progress
            onScrollProgressChanged(progress)
        }
    }

    Box(modifier = modifier) {
        if (contentMode == ContentMode.ORIGINAL) {
            // Original mode: no outer scroll, WebView handles its own scrolling
            // Header is not shown in Original mode - full content experience
            BookmarkDetailOriginalWebView(
                modifier = Modifier.fillMaxSize(),
                url = uiState.bookmark.url
            )
        } else {
            // Reader mode: scrollable Column for article content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .alpha(if (hasRestoredPosition && !showReaderLoadingOverlay) 1f else 0f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val contentWidthFraction = when (uiState.typographySettings.textWidth) {
                    TextWidth.WIDE -> 0.9f
                    TextWidth.NARROW -> 0.8f
                }
                BookmarkDetailHeader(
                    modifier = Modifier.fillMaxWidth(contentWidthFraction),
                    uiState = uiState,
                    onClickOpenUrl = onClickOpenUrl,
                    onTitleChanged = onTitleChanged
                )

                if (uiState.bookmark.hasContent) {
                    BookmarkDetailArticle(
                        modifier = Modifier.fillMaxWidth(contentWidthFraction),
                        uiState = uiState,
                        articleSearchState = articleSearchState,
                        annotations = annotations,
                        scrollToAnnotationId = scrollToAnnotationId,
                        isAnnotationSelectionActive = isAnnotationSelectionActive,
                        onArticleSearchUpdateResults = onArticleSearchUpdateResults,
                        onContentReady = { ready -> isReaderContentReady = ready },
                        onImageTapped = onImageTapped,
                        onImageLongPress = onImageLongPress,
                        onLinkLongPress = onLinkLongPress,
                        onTextSelected = onTextSelected,
                        onAnnotationClicked = onAnnotationClicked,
                        onScrollToAnnotationConsumed = onScrollToAnnotationConsumed,
                    )
                }

                // Action buttons at the end of content (only show in reader mode for articles, videos, and photos)
                if ((uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO) &&
                    uiState.bookmark.hasContent &&
                    contentMode == ContentMode.READER &&
                    isReaderContentReady) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(contentWidthFraction)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favorite button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable {
                                    onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                                }
                                .background(
                                    if (uiState.bookmark.isFavorite) 
                                        MaterialTheme.colorScheme.primaryContainer
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.bookmark.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.bookmark.isFavorite) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.bookmark.isFavorite)
                                    stringResource(R.string.action_remove_from_favorites)
                                else
                                    stringResource(R.string.action_add_to_favorites),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.bookmark.isFavorite) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Archive button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable {
                                    onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                                }
                                .background(
                                    if (uiState.bookmark.isArchived) 
                                        MaterialTheme.colorScheme.primaryContainer
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.bookmark.isArchived) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.bookmark.isArchived)
                                    stringResource(R.string.action_remove_from_archive)
                                else
                                    stringResource(R.string.action_add_to_archive),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.bookmark.isArchived) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action buttons at the end of content (only show in reader mode for articles, videos, and photos)
                if ((uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO ||
                     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO) &&
                    contentMode == ContentMode.READER) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(contentWidthFraction)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favorite button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable {
                                    onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                                }
                                .background(
                                    if (uiState.bookmark.isFavorite) 
                                        MaterialTheme.colorScheme.primaryContainer
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.bookmark.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.bookmark.isFavorite) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.bookmark.isFavorite)
                                    stringResource(R.string.action_remove_from_favorites)
                                else
                                    stringResource(R.string.action_add_to_favorites),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.bookmark.isFavorite) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Archive button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable {
                                    onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                                }
                                .background(
                                    if (uiState.bookmark.isArchived) 
                                        MaterialTheme.colorScheme.primaryContainer
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.bookmark.isArchived) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.bookmark.isArchived)
                                    stringResource(R.string.action_remove_from_archive)
                                else
                                    stringResource(R.string.action_add_to_archive),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.bookmark.isArchived) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Crossfade(
                targetState = showReaderLoadingOverlay,
                animationSpec = tween(durationMillis = 220),
                label = "reader_loading_crossfade"
            ) { isLoading ->
                if (isLoading) {
                    ReaderLoadingOverlay(modifier = Modifier.fillMaxSize())
                }
            }

            // Scrollbar for reader mode
            com.mydeck.app.ui.components.VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                scrollState = scrollState
            )
        }
    }
    }


@Composable
private fun ReaderContextMenu(
    state: BookmarkDetailViewModel.ReaderContextMenuState,
    onDismiss: () -> Unit,
    bookmarkTitle: String,
    bookmarkIconUrl: String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (state.imageUrl != null) {
        val fileName = state.imageUrl.substringAfterLast('/').substringBefore('?').ifBlank { "image" }
        LongPressContextMenuDialog(
            headerImageUrl = state.imageUrl,
            title = state.imageAlt ?: fileName,
            subtitle = "",
            onDismiss = onDismiss,
        ) {
            LongPressContextMenuItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.action_copy_image),
                onClick = {
                    coroutineScope.launch {
                        readerContextMenuCopyImage(context, state.imageUrl)
                        onDismiss()
                    }
                }
            )
            LongPressContextMenuItem(
                icon = Icons.Outlined.Download,
                text = stringResource(R.string.action_download_image),
                onClick = {
                    readerContextMenuDownloadImage(context, state.imageUrl)
                    onDismiss()
                }
            )
            LongPressContextMenuItem(
                icon = Icons.Outlined.Share,
                text = stringResource(R.string.action_share_image),
                onClick = {
                    coroutineScope.launch {
                        readerContextMenuShareImage(context, state.imageUrl)
                        onDismiss()
                    }
                }
            )
        }
    } else if (state.linkUrl != null) {
        LongPressContextMenuDialog(
            headerImageUrl = bookmarkIconUrl,
            title = state.linkText ?: state.linkUrl,
            subtitle = state.linkUrl,
            onDismiss = onDismiss,
        ) {
            LongPressContextMenuItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.action_copy_link),
                onClick = {
                    readerContextMenuCopyToClipboard(context, state.linkUrl)
                    onDismiss()
                }
            )
            LongPressContextMenuItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.action_copy_link_text),
                onClick = {
                    readerContextMenuCopyToClipboard(context, state.linkText.orEmpty())
                    onDismiss()
                }
            )
            LongPressContextMenuItem(
                icon = Icons.Outlined.Download,
                text = stringResource(R.string.action_download_link),
                onClick = {
                    val fileName = state.linkText?.ifBlank { null }
                        ?: state.linkUrl.substringAfterLast('/').ifBlank { "download" }
                    readerContextMenuDownloadUrl(context, state.linkUrl, fileName)
                    onDismiss()
                }
            )
            LongPressContextMenuItem(
                icon = Icons.Outlined.Share,
                text = stringResource(R.string.action_share_link),
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.linkUrl)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                }
            )
            LongPressContextMenuItem(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                text = stringResource(R.string.action_open_in_browser),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.linkUrl)))
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ReaderLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

private fun readerContextMenuCopyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("", text))
}

private suspend fun readerContextMenuCopyImage(context: Context, imageUrl: String) {
    try {
        val imageUri = withContext(Dispatchers.IO) {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(imageUrl).build()
            val rawBitmap = (context.imageLoader.execute(request) as? coil3.request.SuccessResult)
                ?.image as? coil3.BitmapImage ?: throw Exception("no bitmap")
            val bitmap = rawBitmap.bitmap.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        && it.config == android.graphics.Bitmap.Config.HARDWARE)
                    it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it
            }
            val cacheDir = java.io.File(context.cacheDir, "images").also { it.mkdirs() }
            val file = java.io.File(cacheDir, "copy_${System.currentTimeMillis()}.jpg")
            file.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", imageUri))
    } catch (e: Exception) {
        timber.log.Timber.w(e, "Copy image failed")
    }
}

private fun readerContextMenuDownloadUrl(context: Context, url: String, fileName: String) {
    val sanitized = fileName.take(100).replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".html"
    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, sanitized)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}

private fun readerContextMenuDownloadImage(context: Context, imageUrl: String) {
    val uri = Uri.parse(imageUrl)
    // lastPathSegment strips query params; fall back to a timestamp-based name when the
    // URL has no usable filename (e.g., an opaque path). A null or blank subPath would
    // crash DownloadManager with NullPointerException on all API levels.
    val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "image_${System.currentTimeMillis()}.jpg"
    val request = DownloadManager.Request(uri)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}

private suspend fun readerContextMenuShareImage(context: Context, imageUrl: String) {
    try {
        val file = withContext(Dispatchers.IO) {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(imageUrl).build()
            val rawBitmap = (context.imageLoader.execute(request) as? coil3.request.SuccessResult)
                ?.image as? coil3.BitmapImage ?: throw Exception("no bitmap")
            val bitmap = rawBitmap.bitmap.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        && it.config == android.graphics.Bitmap.Config.HARDWARE)
                    it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it
            }
            val cacheDir = java.io.File(context.cacheDir, "images").also { it.mkdirs() }
            val f = java.io.File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
            f.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
            }
            f
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, null))
    } catch (e: Exception) {
        timber.log.Timber.w(e, "Share image failed")
    }
}

@Composable
fun BookmarkDetailErrorScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.an_error_occurred),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
fun BookmarkDetailScreenPreview() {
    MaterialTheme {
        BookmarkDetailScreen(
            modifier = Modifier,
            snackbarHostState = SnackbarHostState(),
            onClickBack = {},
            uiState = BookmarkDetailViewModel.UiState.Success(
                bookmark = sampleBookmark,
                updateBookmarkState = null,
                template = Template.SimpleTemplate("template"),
                zoomFactor = 100,
                typographySettings = com.mydeck.app.domain.model.TypographySettings()
            ),
            onClickToggleFavorite = { _, _ -> },
            onClickToggleArchive = { _, _ -> },
            onClickToggleRead = { _, _ -> },
            onClickDeleteBookmark = { },
            onClickOpenUrl = { },
            onClickShareBookmark = { }
        )
    }
}

@Preview
@Composable
private fun BookmarkDetailContentPreview() {
    Surface {
        BookmarkDetailContent(
            modifier = Modifier,
            uiState = BookmarkDetailViewModel.UiState.Success(
                bookmark = sampleBookmark,
                updateBookmarkState = null,
                template = Template.SimpleTemplate("template"),
                zoomFactor = 100,
                typographySettings = com.mydeck.app.domain.model.TypographySettings()
            ),
            onClickOpenUrl = {},
            onClickToggleFavorite = { _, _ -> },
            onClickToggleArchive = { _, _ -> }
        )
    }
}

@Preview
@Composable
private fun BookmarkDetailContentErrorPreview() {
    Surface {
        BookmarkDetailErrorScreen()
    }
}


private val sampleBookmark = BookmarkDetailViewModel.Bookmark(
    bookmarkId = "1",
    createdDate = "2024-01-15T10:00:00",
    publishedDate = "2024-01-12T08:00:00",
    url = "https://example.com",
    title = "This is a very long title of a small sample bookmark",
    siteName = "Example",
    authors = listOf("John Doe"),
    imgSrc = "https://via.placeholder.com/150",
    iconSrc = "",
    thumbnailSrc = "",
    isFavorite = false,
    isArchived = false,
    isRead = false,
    type = BookmarkDetailViewModel.Bookmark.Type.ARTICLE,
    articleContent = "articleContent",
    embed = null,
    lang = "en",
    wordCount = 1500,
    readingTime = 7,
    description = "This is a sample description",
    labels = listOf("tech", "android", "kotlin"),
    readProgress = 0,
    hasContent = true
)
