package com.mydeck.app.ui.detail

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.icu.text.MessageFormat
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import com.mydeck.app.ui.components.LongPressContextMenuDialog
import com.mydeck.app.ui.components.LongPressContextMenuItem
import com.mydeck.app.ui.components.VerticalScrollbar
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import com.mydeck.app.ui.theme.Dimens
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mydeck.app.R
import com.mydeck.app.domain.model.ImageGalleryData
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.detail.BookmarkDetailViewModel.ContentLoadState
import com.mydeck.app.ui.list.LocalIsWideLayout
import com.mydeck.app.ui.theme.resolveReaderThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.imageLoader
import com.mydeck.app.ui.detail.components.*
import timber.log.Timber
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

private const val PendingDeleteFromDetailKey = "pending_delete_bookmark_id"
private const val VideoFullscreenControlsAutoHideDelayMs = 3_000L
private const val ReadPositionLogPrefix = "READPOS"
private const val ReaderBottomTopBarRevealThreshold = 0.95f
private const val ReaderActionFooterAnimationMs = 180
private val ReaderActionFooterBottomGap = 8.dp

private enum class DetailOverlay {
    NONE,
    DETAILS,
    METADATA_EDITOR
}

enum class VideoFullscreenDismissSource {
    UI,
    WEB_CHROME
}

@Composable
fun BookmarkDetailScreen(
    navHostController: NavController,
    bookmarkId: String?,
    showOriginal: Boolean = false,
    annotationId: String? = null
) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel()
    BookmarkDetailHost(
        viewModel = viewModel,
        showOriginal = showOriginal,
        annotationId = annotationId,
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
    annotationId: String? = null,
    onNavigateBack: (String?) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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
    val onClickShareBookmark: (String, String) -> Unit = { title, url ->
        dismissPendingDeleteSnackbar()
        viewModel.onClickShareBookmark(title, url)
    }
    val onClickToggleRead: (String, Boolean) -> Unit = { id, isRead ->
        dismissPendingDeleteSnackbar()
        viewModel.onToggleRead(id, isRead)
    }
    val onUpdateLabels: (String, List<String>) -> Unit = { id, labels -> viewModel.onUpdateLabels(id, labels) }
    val uiState = viewModel.uiState.collectAsState().value
    val contentLoadState = viewModel.contentLoadState.collectAsState().value
    val articleSearchState = viewModel.articleSearchState.collectAsState().value
    val annotationsState = viewModel.annotationsState.collectAsState().value
    val showAnnotationsSheet = viewModel.showAnnotationsSheet.collectAsState().value
    val annotationEditState = viewModel.annotationEditState.collectAsState().value
    val readerContentReloadNonce = viewModel.readerContentReloadNonce.collectAsState().value
    val labelsWithCounts = viewModel.labelsWithCounts.collectAsState().value
    val galleryData = viewModel.galleryData.collectAsState().value
    val readerContextMenu = viewModel.readerContextMenu.collectAsState().value
    val extractionLogState = viewModel.extractionLogState.collectAsState().value
    val readerWebView = remember { mutableStateOf<WebView?>(null) }
    var detailOverlay by remember { mutableStateOf(DetailOverlay.NONE) }
    var showTypographyPanel by remember { mutableStateOf(false) }
    var videoFullscreenView by remember { mutableStateOf<View?>(null) }
    var videoFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentReaderWebView = readerWebView.value

    fun clearVideoFullscreenState(restoreReaderVisibility: Boolean = true): WebChromeClient.CustomViewCallback? {
        val callback = videoFullscreenCallback
        val previousView = videoFullscreenView
        (previousView?.parent as? ViewGroup)?.removeView(previousView)
        if (restoreReaderVisibility) {
            currentReaderWebView?.visibility = View.VISIBLE
        }
        videoFullscreenView = null
        videoFullscreenCallback = null
        return callback
    }

    fun dismissVideoFullscreen(source: VideoFullscreenDismissSource) {
        val callback = clearVideoFullscreenState()
        if (source == VideoFullscreenDismissSource.UI) {
            callback?.onCustomViewHidden()
        }
    }

    BackHandler(enabled = videoFullscreenView != null) {
        dismissVideoFullscreen(VideoFullscreenDismissSource.UI)
    }

    BackHandler(enabled = detailOverlay != DetailOverlay.NONE) {
        detailOverlay = when (detailOverlay) {
            DetailOverlay.METADATA_EDITOR -> DetailOverlay.DETAILS
            DetailOverlay.DETAILS -> DetailOverlay.NONE
            DetailOverlay.NONE -> DetailOverlay.NONE
        }
    }

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

    fun WebViewAnnotationBridge.RenderedAnnotation.toDomainAnnotation(bookmarkId: String): com.mydeck.app.domain.model.Annotation {
        return com.mydeck.app.domain.model.Annotation(
            id = id,
            bookmarkId = bookmarkId,
            text = text,
            color = color,
            note = note?.takeIf { it.isNotBlank() },
            created = ""
        )
    }

    fun requestAnnotations(bookmarkId: String) {
        val webView = readerWebView.value
        if (webView != null) {
            WebViewAnnotationBridge.getRenderedAnnotations(webView) { renderedAnnotations ->
                viewModel.fetchAnnotations(
                    bookmarkId = bookmarkId,
                    renderedAnnotations = renderedAnnotations
                        .sortedBy { it.position }
                        .map { it.toDomainAnnotation(bookmarkId) }
                )
            }
        } else {
            viewModel.fetchAnnotations(bookmarkId)
        }
    }

    fun showSelectionAnnotationEditor(
        bookmarkId: String,
        selectionData: com.mydeck.app.domain.model.SelectionData
    ) {
        val currentBookmark = (viewModel.uiState.value as? BookmarkDetailViewModel.UiState.Success)?.bookmark
        val currentType = currentBookmark?.type
        if (currentType == BookmarkDetailViewModel.Bookmark.Type.VIDEO &&
            currentBookmark?.articleContent.isNullOrBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.video_highlight_edit_not_supported))
            }
            return
        }
        if (selectionData.selectedAnnotationIds.isEmpty()) {
            viewModel.showCreateAnnotationSheet(selectionData)
            return
        }

        val fallbackAnnotations = annotationsState.annotations
            .filter { it.id in selectionData.selectedAnnotationIds }

        val webView = readerWebView.value
        if (webView != null) {
            WebViewAnnotationBridge.getRenderedAnnotations(webView) { renderedAnnotations ->
                val renderedById = renderedAnnotations.associateBy { it.id }
                val existingAnnotations = selectionData.selectedAnnotationIds.mapNotNull { annotationId ->
                    renderedById[annotationId]?.toDomainAnnotation(bookmarkId)
                        ?: fallbackAnnotations.firstOrNull { it.id == annotationId }
                }
                viewModel.showCreateAnnotationSheet(selectionData, existingAnnotations)
            }
        } else {
            viewModel.showCreateAnnotationSheet(selectionData, fallbackAnnotations)
        }
    }

    fun showAnnotationEditor(bookmarkId: String, annotationId: String) {
        val currentBookmark = (viewModel.uiState.value as? BookmarkDetailViewModel.UiState.Success)?.bookmark
        val currentType = currentBookmark?.type
        if (currentType == BookmarkDetailViewModel.Bookmark.Type.VIDEO &&
            currentBookmark?.articleContent.isNullOrBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.video_highlight_edit_not_supported))
            }
            return
        }
        Timber.d(
            "[AnnotationTap] Attempting to open editor for annotation=%s bookmark=%s cachedAnnotations=%d",
            annotationId,
            bookmarkId,
            annotationsState.annotations.size
        )
        annotationsState.annotations
            .firstOrNull { it.id == annotationId }
            ?.let { annotation ->
                Timber.d("[AnnotationTap] Found annotation=%s in cached sheet data", annotationId)
                viewModel.showEditAnnotationSheet(annotation)
                return
            }

        val webView = readerWebView.value
        if (webView != null) {
            WebViewAnnotationBridge.getRenderedAnnotation(webView, annotationId) { annotation ->
                annotation?.let {
                    Timber.d("[AnnotationTap] Found annotation=%s via rendered DOM lookup", annotationId)
                    viewModel.showEditAnnotationSheet(it.toDomainAnnotation(bookmarkId))
                } ?: Timber.d("[AnnotationTap] Failed rendered DOM lookup for annotation=%s", annotationId)
            }
        } else {
            Timber.d("[AnnotationTap] Cannot open annotation=%s because reader WebView is null", annotationId)
        }
    }

    // Search callbacks
    val onArticleSearchActivate = { viewModel.onArticleSearchActivate() }
    val onArticleSearchDeactivate = { viewModel.onArticleSearchDeactivate() }
    val onArticleSearchQueryChange = { query: String -> viewModel.onArticleSearchQueryChange(query) }
    val onArticleSearchNext = { viewModel.onArticleSearchNext() }
    val onArticleSearchPrevious = { viewModel.onArticleSearchPrevious() }
    val onArticleSearchUpdateResults = { totalMatches: Int, preferredMatch: Int ->
        viewModel.onArticleSearchUpdateResults(totalMatches, preferredMatch)
    }

    // Gallery and context menu callbacks
    val onImageTapped = { data: ImageGalleryData -> viewModel.onImageTapped(data) }
    val onImageLongPress = { imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String ->
        viewModel.onShowImageContextMenu(imageUrl, imageAlt, linkUrl, linkType)
    }
    val onLinkLongPress = { linkUrl: String, linkText: String -> viewModel.onShowLinkContextMenu(linkUrl, linkText) }
    val onIntrapageLinkLongPress = { linkUrl: String, linkText: String -> viewModel.onShowIntrapageLinkContextMenu(linkUrl, linkText) }

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

    // In-place annotation updates via JS (avoids full WebView reload)
    LaunchedEffect(Unit) {
        viewModel.annotationRefreshEvent.collect { event ->
            val webView = readerWebView.value ?: return@collect
            when (event) {
                is BookmarkDetailViewModel.AnnotationRefreshEvent.AttributeUpdate -> {
                    val js = buildString {
                        for (id in event.annotationIds) {
                            val escapedId = id.escapeForJavascript()
                            val escapedColor = event.color.escapeForJavascript()
                            val noteBase64 = android.util.Base64.encodeToString(
                                event.note.toByteArray(Charsets.UTF_8),
                                android.util.Base64.NO_WRAP
                            )
                            append("var nodes=Array.from(document.querySelectorAll('rd-annotation[data-annotation-id-value=\"")
                            append(escapedId)
                            append("\"]'));")
                            append("nodes.forEach(function(el,index){")
                            append("el.setAttribute('data-annotation-color','")
                            append(escapedColor)
                            append("');")
                            append("var note=decodeURIComponent(escape(atob('")
                            append(noteBase64)
                            append("')));")
                            append("el.removeAttribute('title');el.removeAttribute('data-annotation-note');")
                            append("if(note.trim().length!==0&&index===nodes.length-1){el.setAttribute('title',note);el.setAttribute('data-annotation-note','true');}")
                            append("});")
                        }
                    }
                    webView.evaluateJavascript(js, null)
                }
                is BookmarkDetailViewModel.AnnotationRefreshEvent.HtmlRefresh -> {
                    val base64Html = android.util.Base64.encodeToString(
                        event.containerHtml.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    webView.evaluateJavascript(
                        "var el=document.getElementById('rd-article-content');" +
                            "if(el)el.innerHTML=decodeURIComponent(escape(atob('$base64Html')));",
                        null
                    )
                    // Re-inject interaction listeners for the new DOM elements
                    webView.evaluateJavascript(
                        WebViewImageBridge.injectImageInterceptor(),
                        null
                    )
                    webView.evaluateJavascript(
                        WebViewAnnotationBridge.injectAnnotationInteractions(),
                        null
                    )
                }
                is BookmarkDetailViewModel.AnnotationRefreshEvent.VideoHtmlRefresh -> {
                    val base64Html = android.util.Base64.encodeToString(
                        event.articleHtml.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    // Surgical refresh: only replace the article div, leaving the
                    // <div class="video-embed"> iframe untouched so playback continues.
                    webView.evaluateJavascript(
                        "var el=document.getElementById('rd-article-content');" +
                            "if(el)el.innerHTML=decodeURIComponent(escape(atob('$base64Html')));",
                        null
                    )
                    webView.evaluateJavascript(
                        WebViewImageBridge.injectImageInterceptor(),
                        null
                    )
                    webView.evaluateJavascript(
                        WebViewAnnotationBridge.injectAnnotationInteractions(),
                        null
                    )
                }
            }
        }
    }

    val keepScreenOn by viewModel.keepScreenOnWhileReading.collectAsState()
    val fullscreenWhileReading by viewModel.fullscreenWhileReading.collectAsState()
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
            val isWideLayout = LocalIsWideLayout.current
            var selectionActionModeActive by remember(uiState.bookmark.bookmarkId) {
                mutableStateOf(false)
            }
            val annotationSurfaceActive = showAnnotationsSheet || annotationEditState != null

            LaunchedEffect(
                contentMode,
                isWideLayout,
                selectionActionModeActive,
                annotationSurfaceActive,
                videoFullscreenView
            ) {
                viewModel.updateReaderFooterGates(
                    readerMode = contentMode == ContentMode.READER,
                    hasDuplicateWideControls = isWideLayout,
                    selectionToolbarActive = selectionActionModeActive,
                    annotationEditorActive = annotationSurfaceActive,
                    videoFullscreenActive = videoFullscreenView != null
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
                        is BookmarkDetailViewModel.UpdateBookmarkState.Success -> {
                            it.message?.let { message ->
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
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
                    onShowDetails = { detailOverlay = DetailOverlay.DETAILS },
                    onShowHighlights = {
                        dismissPendingDeleteSnackbar()
                        viewModel.showAnnotationsSheet()
                        requestAnnotations(uiState.bookmark.bookmarkId)
                    },
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
                    onImageTapped = onImageTapped,
                    onImageLongPress = onImageLongPress,
                    onLinkLongPress = onLinkLongPress,
                    onIntrapageLinkLongPress = onIntrapageLinkLongPress,
                    onTextSelectionCaptured = { selectionData ->
                        showSelectionAnnotationEditor(uiState.bookmark.bookmarkId, selectionData)
                    },
                    onReaderSelectionActionModeChanged = { active ->
                        selectionActionModeActive = active
                    },
                    onAnnotationClicked = { annotationId ->
                        showAnnotationEditor(uiState.bookmark.bookmarkId, annotationId)
                    },
                    onReaderEndVisibleChanged = { visible ->
                        viewModel.onReaderEndVisibleChanged(visible)
                    },
                    onReaderWebViewChanged = { readerWebView.value = it },
                    readerWebView = readerWebView.value,
                    videoFullscreenView = videoFullscreenView,
                    onVideoEnterFullscreen = { view, callback ->
                        if (videoFullscreenView !== view) {
                            clearVideoFullscreenState(restoreReaderVisibility = false)
                            videoFullscreenView = view
                            videoFullscreenCallback = callback
                        }
                    },
                    onVideoExitFullscreen = { source -> dismissVideoFullscreen(source) },
                    fullscreenWhileReading = fullscreenWhileReading,
                    annotationId = annotationId,
                    readerContentReloadNonce = readerContentReloadNonce,
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
            if (detailOverlay == DetailOverlay.DETAILS) {
                BookmarkDetailsDialog(
                    bookmark = uiState.bookmark,
                    onDismissRequest = { detailOverlay = DetailOverlay.NONE },
                    onLabelsUpdate = { newLabels ->
                        onUpdateLabels(uiState.bookmark.bookmarkId, newLabels)
                    },
                    existingLabels = labelsWithCounts.keys.toList(),
                    onExportDebugJson = { viewModel.onExportDebugJson() },
                    onClickOpenInBrowser = onClickOpenInBrowser,
                    onRefreshContent = {
                        dismissPendingDeleteSnackbar()
                        viewModel.forceRefreshContent()
                    },
                    canRefreshContent = uiState.bookmark.hasContent,
                    onEditMetadata = {
                        detailOverlay = DetailOverlay.METADATA_EDITOR
                    },
                    extractionLogState = extractionLogState,
                    onViewExtractionLog = { viewModel.onViewExtractionLog() },
                    onDismissExtractionLog = { viewModel.onDismissExtractionLog() }
                )
            }
            if (detailOverlay == DetailOverlay.METADATA_EDITOR) {
                BookmarkMetadataEditorDialog(
                    bookmark = uiState.bookmark,
                    onDismissRequest = {
                        detailOverlay = DetailOverlay.DETAILS
                    },
                    onSave = { metadata ->
                        viewModel.onUpdateMetadata(uiState.bookmark.bookmarkId, metadata)
                    }
                )
            }
            if (showTypographyPanel) {
                ReaderSettingsBottomSheet(
                    currentSettings = uiState.typographySettings,
                    currentAppearanceSelection = uiState.readerAppearanceSelection,
                    onSettingsChanged = { settings ->
                        viewModel.onTypographySettingsChanged(settings)
                    },
                    onThemeSelectionChanged = { selection ->
                        viewModel.onReaderThemeSelectionChanged(selection)
                    },
                    onDismiss = { showTypographyPanel = false }
                )
            }
            if (showAnnotationsSheet &&
                (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                 uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO) &&
                contentMode == ContentMode.READER) {
                AnnotationsBottomSheet(
                    annotations = annotationsState.annotations,
                    isLoading = annotationsState.isLoading,
                    onDismiss = { viewModel.hideAnnotationsSheet() },
                    onAnnotationClick = { selectedAnnotationId ->
                        viewModel.hideAnnotationsSheet()
                        readerWebView.value?.evaluateJavascript(
                            "(function(){ var el = document.getElementById('annotation-$selectedAnnotationId'); " +
                                "if (el) el.scrollIntoView({block:'center', behavior:'smooth'}); })();",
                            null
                        )
                    }
                )
            }
            if (annotationEditState != null &&
                (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                    (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO &&
                        !uiState.bookmark.articleContent.isNullOrBlank())) &&
                contentMode == ContentMode.READER) {
                AnnotationEditSheet(
                    state = annotationEditState,
                    onColorSelected = { color -> viewModel.onAnnotationEditColorSelected(color) },
                    onNoteChanged = { note -> viewModel.onAnnotationEditNoteChanged(note) },
                    onSave = { viewModel.saveAnnotationEdit() },
                    onDelete = { viewModel.deleteCurrentAnnotation() },
                    onDismiss = { viewModel.dismissAnnotationEditSheet() }
                )
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
    onClickShareBookmark: (String, String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit = {},
    onArticleSearchActivate: () -> Unit = {},
    onShowDetails: () -> Unit = {},
    onShowHighlights: () -> Unit = {},
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
    onArticleSearchUpdateResults: (Int, Int) -> Unit = { _, _ -> },
    onShowTypographyPanel: () -> Unit = {},
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onIntrapageLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onTextSelectionCaptured: (com.mydeck.app.domain.model.SelectionData) -> Unit = {},
    onReaderSelectionActionModeChanged: (Boolean) -> Unit = {},
    onAnnotationClicked: (String) -> Unit = {},
    onReaderEndVisibleChanged: (Boolean) -> Unit = {},
    readerWebView: WebView? = null,
    onReaderWebViewChanged: (WebView?) -> Unit = {},
    videoFullscreenView: View? = null,
    onVideoEnterFullscreen: (View, WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    onVideoExitFullscreen: (VideoFullscreenDismissSource) -> Unit = {},
    fullscreenWhileReading: Boolean = false,
    annotationId: String? = null,
    readerContentReloadNonce: Int = 0,
) {
    val topBarState = rememberTopAppBarState()
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state = topBarState)
    val webViewDispatcher = remember { NestedScrollDispatcher() }
    var readProgressPercent by remember(uiState.bookmark.bookmarkId) {
        mutableIntStateOf(initialReadProgress.coerceIn(0, 100))
    }
    var canScrollReaderToTop by remember(
        uiState.bookmark.bookmarkId,
        contentMode,
        initialReadProgress,
        annotationId,
    ) {
        mutableStateOf(
            contentMode == ContentMode.READER &&
                (initialReadProgress > 0 || annotationId != null)
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var bottomTopBarRevealJob by remember { mutableStateOf<Job?>(null) }
    var isReaderContentReady by remember(uiState.bookmark.bookmarkId, contentMode) { mutableStateOf(false) }
    val fullscreenReaderMode = fullscreenWhileReading && contentMode == ContentMode.READER
    val immersiveModeEnabled = fullscreenReaderMode
    var showFullscreenTopBar by remember(uiState.bookmark.bookmarkId, fullscreenReaderMode) {
        mutableStateOf(fullscreenReaderMode)
    }
    val density = LocalDensity.current
    // Static derivation — keeps readerTopClearanceCssPx stable across the screen's
    // lifetime so the WebView's HTML clearance spacer doesn't get rewritten after
    // initial composition, which would otherwise reload the article and reset scroll.
    // The cushion absorbs any device-specific padding the M3 TopAppBar adds beyond
    // TopAppBarExpandedHeight + status bars (observed on Pixel Tablet emulator),
    // plus any sub-pixel rounding. Larger than strictly needed on phones, but the
    // extra ~8dp at the top of articles is negligible visually compared to the
    // cost of the measurement-based approach (one-shot WebView reload mid-scroll).
    val topBarClearance = with(density) {
        TopAppBarDefaults.TopAppBarExpandedHeight +
            WindowInsets.statusBars.getTop(this).toDp() +
            12.dp
    }
    val readerTopClearanceCssPx = topBarClearance.value.roundToInt().coerceAtLeast(0)
    val footerBottomSafeArea = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val readerFooterClearance = ReaderActionFooterHeight + ReaderActionFooterBottomGap + footerBottomSafeArea
    val readerBottomClearanceCssPx = if (uiState.footerEnabled) {
        readerFooterClearance.value.roundToInt().coerceAtLeast(0)
    } else {
        0
    }
    val footerAppearance = uiState.readerAppearanceSelection.effectiveAppearance(isSystemInDarkTheme())
    val footerContext = LocalContext.current
    val readerFooterPalette = remember(footerContext, footerAppearance) {
        resolveReaderThemePalette(context = footerContext, appearance = footerAppearance)
    }
    val topBarCanHide =
        uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE &&
            contentMode == ContentMode.READER
    val fullscreenTopBarRevealConnection = remember(
        fullscreenReaderMode,
        showFullscreenTopBar,
        articleSearchState.isActive
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    fullscreenReaderMode &&
                    !showFullscreenTopBar &&
                    !articleSearchState.isActive &&
                    shouldRevealFullscreenTopBarOnScroll(available.y)
                ) {
                    showFullscreenTopBar = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (
                    fullscreenReaderMode &&
                    !showFullscreenTopBar &&
                    !articleSearchState.isActive &&
                    (
                        shouldRevealFullscreenTopBarOnScroll(consumed.y) ||
                            shouldRevealFullscreenTopBarOnScroll(available.y)
                        )
                ) {
                    showFullscreenTopBar = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(fullscreenReaderMode) {
        if (!fullscreenReaderMode) {
            showFullscreenTopBar = false
        } else {
            showFullscreenTopBar = true
        }
    }

    LaunchedEffect(uiState.bookmark.bookmarkId, contentMode, articleSearchState.isActive) {
        bottomTopBarRevealJob?.cancel()
        bottomTopBarRevealJob = null
        topBarState.heightOffset = 0f
        topBarState.contentOffset = 0f
    }

    fun revealTopBarAtReaderBottom() {
        if (topBarState.heightOffset >= 0f || bottomTopBarRevealJob?.isActive == true) return
        bottomTopBarRevealJob = coroutineScope.launch {
            animate(
                initialValue = topBarState.heightOffset,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180)
            ) { value, _ ->
                topBarState.heightOffset = value
            }
            topBarState.heightOffset = 0f
            topBarState.contentOffset = 0f
        }
    }

    fun updateTopBarFromReaderScroll(
        scrollY: Int,
        deltaY: Int,
        scrollProgress: Float,
        userInitiated: Boolean
    ) {
        canScrollReaderToTop = scrollY > 0
        if (
            !userInitiated ||
            contentMode != ContentMode.READER ||
            articleSearchState.isActive ||
            videoFullscreenView != null ||
            fullscreenReaderMode ||
            !topBarCanHide
        ) {
            return
        }

        if (scrollY <= 0) {
            bottomTopBarRevealJob?.cancel()
            bottomTopBarRevealJob = null
            topBarState.heightOffset = 0f
            topBarState.contentOffset = 0f
            return
        }

        val limit = topBarState.heightOffsetLimit
        if (limit >= 0f || deltaY == 0) return

        if (scrollProgress >= ReaderBottomTopBarRevealThreshold) {
            revealTopBarAtReaderBottom()
            return
        }

        bottomTopBarRevealJob?.cancel()
        bottomTopBarRevealJob = null
        val composeDeltaY = -deltaY.toFloat()
        topBarState.heightOffset = (topBarState.heightOffset + composeDeltaY)
            .coerceIn(limit, 0f)
        topBarState.contentOffset += composeDeltaY
    }

    LaunchedEffect(fullscreenReaderMode, showFullscreenTopBar, articleSearchState.isActive) {
        if (fullscreenReaderMode && showFullscreenTopBar && !articleSearchState.isActive) {
            delay(2500)
            showFullscreenTopBar = false
        }
    }

    ReaderFullscreenEffect(enabled = immersiveModeEnabled)


    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(fullscreenTopBarRevealConnection)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                BookmarkDetailContent(
                    modifier = Modifier.fillMaxSize(),
                    uiState = uiState,
                    webViewDispatcher = webViewDispatcher,
                    onClickOpenUrl = onClickOpenUrl,
                    onScrollProgressChanged = { progress ->
                        readProgressPercent = progress.coerceIn(0, 100)
                        onScrollProgressChanged(progress)
                    },
                    initialReadProgress = initialReadProgress,
                    contentMode = contentMode,
                    contentLoadState = contentLoadState,
                    articleSearchState = articleSearchState,
                    onArticleSearchUpdateResults = onArticleSearchUpdateResults,
                    onImageTapped = onImageTapped,
                    onImageLongPress = onImageLongPress,
                    onLinkLongPress = onLinkLongPress,
                    onIntrapageLinkLongPress = onIntrapageLinkLongPress,
                    onTextSelectionCaptured = onTextSelectionCaptured,
                    onReaderSelectionActionModeChanged = onReaderSelectionActionModeChanged,
                    onAnnotationClicked = onAnnotationClicked,
                    onReaderEndVisibleChanged = onReaderEndVisibleChanged,
                    onReaderWebViewChanged = onReaderWebViewChanged,
                    onVideoEnterFullscreen = onVideoEnterFullscreen,
                    onVideoExitFullscreen = onVideoExitFullscreen,
                    onReaderScrollChanged = ::updateTopBarFromReaderScroll,
                    annotationId = annotationId,
                    readerContentReloadNonce = readerContentReloadNonce,
                    readerTopClearanceCssPx = readerTopClearanceCssPx,
                    readerBottomClearanceCssPx = readerBottomClearanceCssPx,
                    topBarClearance = topBarClearance,
                )

                if (contentMode == ContentMode.READER && !articleSearchState.isActive) {
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        progress = readProgressPercent / 100f
                    )
                }

                if (fullscreenReaderMode && !showFullscreenTopBar && !articleSearchState.isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(28.dp)
                            .clickable { showFullscreenTopBar = true }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.showFooter,
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = ReaderActionFooterAnimationMs)
                    ) { it } + fadeIn(animationSpec = tween(durationMillis = ReaderActionFooterAnimationMs)),
                    exit = slideOutVertically(
                        animationSpec = tween(durationMillis = ReaderActionFooterAnimationMs)
                    ) { it } + fadeOut(animationSpec = tween(durationMillis = ReaderActionFooterAnimationMs)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = footerBottomSafeArea + ReaderActionFooterBottomGap)
                ) {
                    ReaderActionFooter(
                        isFavorite = uiState.bookmark.isFavorite,
                        isArchived = uiState.bookmark.isArchived,
                        onToggleFavorite = {
                            onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                        },
                        onToggleArchive = {
                            onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                        },
                        palette = readerFooterPalette
                    )
                }
            }
        }

        if (
            videoFullscreenView == null &&
            (!fullscreenReaderMode || showFullscreenTopBar || articleSearchState.isActive)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
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
                    onShowHighlights = onShowHighlights,
                    contentMode = contentMode,
                    onClickToggleRead = onClickToggleRead,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickDeleteBookmark = onClickDeleteBookmark,
                    onArticleSearchActivate = onArticleSearchActivate,
                    onClickOpenInBrowser = onClickOpenInBrowser,
                    onContentModeChange = onContentModeChange,
                    scrollBehavior = topBarScrollBehavior,
                    canScrollToTop = canScrollReaderToTop,
                    onScrollToTop = {
                        readerWebView?.evaluateJavascript(
                            "window.scrollTo({top: 0, behavior: 'smooth'});",
                            null
                        )
                    },
                )
            }
        }

        if (videoFullscreenView != null) {
            VideoFullscreenOverlay(
                customView = videoFullscreenView,
                onDismiss = onVideoExitFullscreen
            )
        }
    }
}

internal fun shouldRevealFullscreenTopBarOnScroll(deltaY: Float): Boolean {
    return deltaY > 0f
}

@Composable
private fun VideoFullscreenOverlay(
    customView: View,
    onDismiss: (VideoFullscreenDismissSource) -> Unit,
) {
    var isRotated by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var controlsInteractionNonce by remember { mutableIntStateOf(0) }

    fun revealControls() {
        showControls = true
        controlsInteractionNonce += 1
    }

    LaunchedEffect(controlsInteractionNonce, showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(VideoFullscreenControlsAutoHideDelayMs)
            showControls = false
        }
    }

    BackHandler(onBack = {
        if (isRotated) {
            isRotated = false
            revealControls()
        } else {
            onDismiss(VideoFullscreenDismissSource.UI)
        }
    })

    DisposableEffect(customView) {
        onDispose {
            customView.rotation = 0f
            customView.translationX = 0f
            customView.translationY = 0f
            (customView.parent as? ViewGroup)?.removeView(customView)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            revealControls()
                        }
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { container ->
                if (customView.parent !== container) {
                    (customView.parent as? ViewGroup)?.removeView(customView)
                    container.removeAllViews()
                    container.addView(customView)
                }

                if (isRotated && containerSize.width > 0 && containerSize.height > 0) {
                    customView.rotation = 90f
                    customView.layoutParams = FrameLayout.LayoutParams(containerSize.height, containerSize.width)
                    customView.translationX = (containerSize.width - containerSize.height) / 2f
                    customView.translationY = (containerSize.height - containerSize.width) / 2f
                } else {
                    customView.rotation = 0f
                    customView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    customView.translationX = 0f
                    customView.translationY = 0f
                }
            }
        )

        // On-screen controls overlay
        val controlsModifier = if (isRotated) {
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(constraints.maxHeight, constraints.maxWidth)
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val xOffset = (constraints.maxWidth - placeable.width) / 2
                        val yOffset = (constraints.maxHeight - placeable.height) / 2
                        placeable.place(xOffset, yOffset)
                    }
                }
                .graphicsLayer {
                    rotationZ = 90f
                }
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = controlsModifier) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val controlWidth = 72.dp
                    val minHorizontalPadding = 24.dp
                    val leftOffset = ((maxWidth * 0.25f) - (controlWidth / 2))
                        .coerceAtLeast(minHorizontalPadding)
                    val rightOffset = ((maxWidth * 0.75f) - (controlWidth / 2))
                        .coerceAtMost(maxWidth - controlWidth - minHorizontalPadding)

                    VideoFullscreenControlButton(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .absoluteOffset(x = leftOffset),
                        onClick = { onDismiss(VideoFullscreenDismissSource.UI) },
                        contentDescription = stringResource(R.string.gallery_close),
                        icon = Icons.Default.Close
                    )
                    VideoFullscreenControlButton(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .absoluteOffset(x = rightOffset),
                        onClick = {
                            isRotated = !isRotated
                            revealControls()
                        },
                        contentDescription = stringResource(R.string.video_fullscreen_rotate),
                        icon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFullscreenControlButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .width(72.dp)
            .height(48.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

@Composable
private fun ReaderFullscreenEffect(enabled: Boolean) {
    val view = LocalView.current

    DisposableEffect(enabled, view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }

        if (enabled && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
fun BookmarkDetailContent(
    modifier: Modifier = Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    webViewDispatcher: NestedScrollDispatcher,
    onClickOpenUrl: (String) -> Unit,
    onScrollProgressChanged: (Int) -> Unit = {},
    initialReadProgress: Int = 0,
    contentMode: ContentMode = ContentMode.READER,
    contentLoadState: ContentLoadState = ContentLoadState.Idle,
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState = BookmarkDetailViewModel.ArticleSearchState(),
    onArticleSearchUpdateResults: (Int, Int) -> Unit = { _, _ -> },
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onIntrapageLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onTextSelectionCaptured: (com.mydeck.app.domain.model.SelectionData) -> Unit = {},
    onReaderSelectionActionModeChanged: (Boolean) -> Unit = {},
    onAnnotationClicked: (String) -> Unit = {},
    onReaderEndVisibleChanged: (Boolean) -> Unit = {},
    onReaderWebViewChanged: (WebView?) -> Unit = {},
    onVideoEnterFullscreen: (View, android.webkit.WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    onVideoExitFullscreen: (VideoFullscreenDismissSource) -> Unit = {},
    onReaderScrollChanged: (scrollY: Int, deltaY: Int, scrollProgress: Float, userInitiated: Boolean) -> Unit = { _, _, _, _ -> },
    annotationId: String? = null,
    readerContentReloadNonce: Int = 0,
    readerTopClearanceCssPx: Int = 0,
    readerBottomClearanceCssPx: Int = 0,
    topBarClearance: Dp = 0.dp,
) {
    val coroutineScope = rememberCoroutineScope()
    val hasArticleContent = uiState.bookmark.articleContent != null
    val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
    val needsRestore = isArticle && hasArticleContent && initialReadProgress > 0 && initialReadProgress <= 100
    val hasReaderContent = uiState.bookmark.hasContent && contentMode == ContentMode.READER
    // Track whether content was already available when this screen first composed.
    // If false, content was fetched on-demand and the parent's determinate progress bar
    // handles the loading indicator — the overlay should not show an indeterminate bar.
    val contentWasInitiallyAvailable by remember(uiState.bookmark.bookmarkId, contentMode) {
        mutableStateOf(uiState.bookmark.hasContent)
    }
    var isReaderContentReady by remember(
        uiState.bookmark.bookmarkId,
        uiState.bookmark.articleContent != null,
        uiState.bookmark.embed,
        contentMode
    ) {
        mutableStateOf(false)
    }
    var hasDisplayedReaderContent by remember(uiState.bookmark.bookmarkId, contentMode) {
        mutableStateOf(false)
    }
    var hasLoggedFirstVisible by remember(uiState.bookmark.bookmarkId, contentMode) {
        mutableStateOf(false)
    }
    var openStartMs by remember(uiState.bookmark.bookmarkId) {
        mutableStateOf(SystemClock.elapsedRealtime())
    }

    LaunchedEffect(uiState.bookmark.bookmarkId, initialReadProgress, needsRestore) {
        if (needsRestore || initialReadProgress <= 0) {
            openStartMs = SystemClock.elapsedRealtime()
        }
        Timber.d(
            "$ReadPositionLogPrefix: open bookmark=${uiState.bookmark.bookmarkId} " +
                "initial=$initialReadProgress needsRestore=$needsRestore"
        )
    }

    LaunchedEffect(hasReaderContent) {
        if (!hasReaderContent) {
            isReaderContentReady = false
            hasDisplayedReaderContent = false
        }
    }

    LaunchedEffect(isReaderContentReady, hasReaderContent) {
        if (hasReaderContent && isReaderContentReady) {
            hasDisplayedReaderContent = true
        }
    }

    // Keep the spinner for the initial reader load/render path, but do not re-show it
    // after content is already visible and the cached article HTML is refreshed in place.
    val showReaderLoadingOverlay =
        contentMode == ContentMode.READER &&
            !hasDisplayedReaderContent &&
            (
                (uiState.bookmark.hasContent && !isReaderContentReady) ||
                    (!uiState.bookmark.hasContent && contentLoadState !is ContentLoadState.Failed)
                )
    val shouldHideReaderContent = !hasDisplayedReaderContent && showReaderLoadingOverlay

    LaunchedEffect(shouldHideReaderContent, hasReaderContent, isReaderContentReady) {
        if (!shouldHideReaderContent && hasReaderContent && !hasLoggedFirstVisible) {
            hasLoggedFirstVisible = true
            Timber.d(
                "$ReadPositionLogPrefix: first-visible bookmark=${uiState.bookmark.bookmarkId} " +
                    "tOpenMs=${SystemClock.elapsedRealtime() - openStartMs} " +
                    "ready=$isReaderContentReady"
            )
        }
    }

    Box(modifier = modifier) {
        if (contentMode == ContentMode.ORIGINAL) {
            // Original mode: no outer scroll, WebView handles its own scrolling
            // Header is not shown in Original mode - full content experience
            BookmarkDetailOriginalWebView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarClearance),
                url = uiState.bookmark.url
            )
        } else {
            // Reader mode: scrollable Column for article content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (shouldHideReaderContent) 0f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.bookmark.hasContent) {
                    BookmarkDetailArticle(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        uiState = uiState,
                        webViewDispatcher = webViewDispatcher,
                        articleSearchState = articleSearchState,
                        onArticleSearchUpdateResults = onArticleSearchUpdateResults,
                        onContentReady = { ready ->
                            isReaderContentReady = ready
                        },
                        initialReadProgress = initialReadProgress,
                        onScrollProgressChanged = onScrollProgressChanged,
                        onWebViewChanged = onReaderWebViewChanged,
                        onImageTapped = onImageTapped,
                        onImageLongPress = onImageLongPress,
                        onLinkLongPress = onLinkLongPress,
                        onIntrapageLinkLongPress = onIntrapageLinkLongPress,
                        onTextSelectionCaptured = onTextSelectionCaptured,
                        onSelectionActionModeChanged = onReaderSelectionActionModeChanged,
                        onAnnotationClicked = onAnnotationClicked,
                        onEndVisibleChanged = onReaderEndVisibleChanged,
                        onVideoEnterFullscreen = onVideoEnterFullscreen,
                        onVideoExitFullscreen = onVideoExitFullscreen,
                        onScrollChanged = onReaderScrollChanged,
                        annotationId = annotationId ?: "",
                        contentReloadKey = readerContentReloadNonce,
                        readerTopClearanceCssPx = readerTopClearanceCssPx,
                        readerBottomClearanceCssPx = readerBottomClearanceCssPx,
                    )
                }
            }

            Crossfade(
                targetState = showReaderLoadingOverlay,
                animationSpec = tween(durationMillis = 220),
                label = "reader_loading_crossfade"
            ) { isLoading ->
                if (isLoading) {
                    ReaderLoadingOverlay(
                        contentAlreadyAvailable = contentWasInitiallyAvailable,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // TODO(spec section 4): scrollbar removed for Phase 2 — WebView is now the visible scroller.

            // Determinate progress bar for on-demand content loading.
            // Drawn last in the Scaffold content Box so it sits just below the
            // top app bar and renders on top of the loading overlay.
            val progressTarget = when (contentLoadState) {
                is ContentLoadState.Loading -> contentLoadState.progress.coerceIn(0f, 0.95f)
                ContentLoadState.Idle -> 0f
                else -> 1f
            }
            val animatedProgress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = tween(durationMillis = if (progressTarget >= 1f) 250 else 350),
                label = "contentLoadProgress"
            )
            var showProgress by remember(uiState.bookmark.bookmarkId) { mutableStateOf(false) }
            LaunchedEffect(contentLoadState, progressTarget) {
                if (progressTarget > 0f) showProgress = true
                if (progressTarget >= 1f) {
                    delay(250)
                    showProgress = false
                }
            }
            if (showProgress && animatedProgress < 1f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topBarClearance)
                        .height(4.dp)
                        .align(Alignment.TopStart)
                )
            }
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
            headerImageUrl = if (state.isIntrapage) null else bookmarkIconUrl,
            headerIcon = if (state.isIntrapage) Icons.Outlined.Link else null,
            title = state.linkText ?: state.linkUrl,
            subtitle = if (state.isIntrapage) stringResource(R.string.reader_intrapage_link_label) else state.linkUrl,
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
            if (!state.isIntrapage) {
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
            }
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
private fun ReaderLoadingOverlay(
    contentAlreadyAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (contentAlreadyAvailable) {
            // Content is in the DB/filesystem, just waiting for WebView to render.
            // No meaningful progress signal — the user requested to remove the indeterminate bar here.
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
                typographySettings = com.mydeck.app.domain.model.TypographySettings(),
                readerAppearanceSelection = com.mydeck.app.domain.model.ReaderAppearanceSelection(
                    themeMode = com.mydeck.app.domain.model.Theme.SYSTEM,
                    lightAppearance = com.mydeck.app.domain.model.LightAppearance.PAPER,
                    darkAppearance = com.mydeck.app.domain.model.DarkAppearance.DARK
                )
            ),
            onClickToggleFavorite = { _, _ -> },
            onClickToggleArchive = { _, _ -> },
            onClickToggleRead = { _, _ -> },
            onClickDeleteBookmark = { },
            onClickOpenUrl = { },
            onClickShareBookmark = { _, _ -> }
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
                typographySettings = com.mydeck.app.domain.model.TypographySettings(),
                readerAppearanceSelection = com.mydeck.app.domain.model.ReaderAppearanceSelection(
                    themeMode = com.mydeck.app.domain.model.Theme.SYSTEM,
                    lightAppearance = com.mydeck.app.domain.model.LightAppearance.PAPER,
                    darkAppearance = com.mydeck.app.domain.model.DarkAppearance.DARK
                )
            ),
            webViewDispatcher = remember { NestedScrollDispatcher() },
            onClickOpenUrl = {}
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
    publishedDateInput = "01/12/2024",
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
    textDirection = "ltr",
    wordCount = 1500,
    readingTime = 7,
    description = "This is a sample description",
    omitDescription = null,
    labels = listOf("tech", "android", "kotlin"),
    readProgress = 0,
    hasContent = true
)
