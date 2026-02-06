package com.mydeck.app.ui.detail

import android.icu.text.MessageFormat
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mydeck.app.R
import com.mydeck.app.domain.model.Template
import androidx.compose.material3.Button
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.detail.BookmarkDetailViewModel.ContentLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookmarkDetailScreen(navHostController: NavController, bookmarkId: String?, showOriginal: Boolean = false) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel()
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val openUrlEvent = viewModel.openUrlEvent.collectAsState()
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onClickToggleFavorite: (String, Boolean) -> Unit =
        { id, isFavorite -> viewModel.onToggleFavorite(id, isFavorite) }
    val onClickToggleArchive: (String, Boolean) -> Unit =
        { id, isArchived -> viewModel.onToggleArchive(id, isArchived) }
    val onClickIncreaseZoomFactor: () -> Unit =
        { viewModel.onClickChangeZoomFactor(25) }
    val onClickDecreaseZoomFactor: () -> Unit =
        { viewModel.onClickChangeZoomFactor(-25) }

    val onClickOpenUrl: (String) -> Unit = { viewModel.onClickOpenUrl(it) }
    val onClickShareBookmark: (String) -> Unit = { url -> viewModel.onClickShareBookmark(url) }
    val onClickToggleRead: (String, Boolean) -> Unit = { id, isRead -> viewModel.onToggleRead(id, isRead) }
    val onUpdateLabels: (String, List<String>) -> Unit = { id, labels -> viewModel.onUpdateLabels(id, labels) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsState().value
    val contentLoadState = viewModel.contentLoadState.collectAsState().value
    var showDetailsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val onClickDeleteBookmark: (String) -> Unit = { id ->
        viewModel.deleteBookmark(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onCancelDeleteBookmark()
            }
        }
    }

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is BookmarkDetailViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
            }
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }

    val context = LocalContext.current
    LaunchedEffect(key1 = openUrlEvent.value){
        openUrlInCustomTab(context, openUrlEvent.value)
        viewModel.onOpenUrlEventConsumed()
    }

    when (uiState) {
        is BookmarkDetailViewModel.UiState.Success -> {
            var contentMode by remember(uiState.bookmark.bookmarkId) {
                mutableStateOf(
                    if (showOriginal) ContentMode.ORIGINAL
                    else ContentMode.READER
                )
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
            BookmarkDetailScreen(
                modifier = Modifier,
                snackbarHostState = snackbarHostState,
                onClickBack = onClickBack,
                onClickToggleFavorite = onClickToggleFavorite,
                onClickToggleArchive = onClickToggleArchive,
                onClickToggleRead = onClickToggleRead,
                onClickShareBookmark = onClickShareBookmark,
                onClickDeleteBookmark = onClickDeleteBookmark,
                uiState = uiState,
                onClickOpenUrl = onClickOpenUrl,
                onClickIncreaseZoomFactor = onClickIncreaseZoomFactor,
                onClickDecreaseZoomFactor = onClickDecreaseZoomFactor,
                onShowDetails = { showDetailsDialog = true },
                onScrollProgressChanged = { progress ->
                    viewModel.onScrollProgressChanged(progress)
                },
                initialReadProgress = viewModel.getInitialReadProgress(),
                contentMode = contentMode,
                onContentModeChange = { contentMode = it },
                contentLoadState = contentLoadState,
                onRetryContentFetch = { viewModel.retryContentFetch() },
                onSwitchToOriginal = { contentMode = ContentMode.ORIGINAL }
            )
            // Consumes a shareIntent and creates the corresponding share dialog
            ShareBookmarkChooser(
                context = LocalContext.current,
                intent = viewModel.shareIntent.collectAsState().value,
                onShareIntentConsumed = { viewModel.onShareIntentConsumed() }
            )
            if (showDetailsDialog) {
                BookmarkDetailsDialog(
                    bookmark = uiState.bookmark,
                    onDismissRequest = { showDetailsDialog = false },
                    onLabelsUpdate = { newLabels ->
                        onUpdateLabels(uiState.bookmark.bookmarkId, newLabels)
                    }
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
    onClickShareBookmark: (String) -> Unit,
    onClickIncreaseZoomFactor: () -> Unit,
    onClickDecreaseZoomFactor: () -> Unit,
    onShowDetails: () -> Unit = {},
    onScrollProgressChanged: (Int) -> Unit = {},
    initialReadProgress: Int = 0,
    contentMode: ContentMode = ContentMode.READER,
    onContentModeChange: (ContentMode) -> Unit = {},
    contentLoadState: ContentLoadState = ContentLoadState.Idle,
    onRetryContentFetch: () -> Unit = {},
    onSwitchToOriginal: () -> Unit = {}
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClickBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onClickToggleFavorite(uiState.bookmark.bookmarkId, !uiState.bookmark.isFavorite)
                    }) {
                        Icon(
                            imageVector = if (uiState.bookmark.isFavorite) Icons.Filled.Grade else Icons.Outlined.Grade,
                            contentDescription = stringResource(R.string.action_favorite)
                        )
                    }
                    IconButton(onClick = {
                        onClickToggleArchive(uiState.bookmark.bookmarkId, !uiState.bookmark.isArchived)
                    }) {
                        Icon(
                            imageVector = if (uiState.bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                            contentDescription = stringResource(R.string.action_archive)
                        )
                    }
                    IconButton(onClick = { onShowDetails() }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.detail_dialog_title)
                        )
                    }
                    BookmarkDetailMenu(
                        uiState = uiState,
                        onClickToggleRead = onClickToggleRead,
                        onClickShareBookmark = onClickShareBookmark,
                        onClickDeleteBookmark = onClickDeleteBookmark,
                        onClickIncreaseZoomFactor = onClickIncreaseZoomFactor,
                        onClickDecreaseZoomFactor = onClickDecreaseZoomFactor,
                        contentMode = contentMode,
                        onContentModeChange = onContentModeChange
                    )
                }
            )
        }
    ) { padding ->
        BookmarkDetailContent(
            modifier = Modifier.padding(padding),
            uiState = uiState,
            onClickOpenUrl = onClickOpenUrl,
            onScrollProgressChanged = onScrollProgressChanged,
            initialReadProgress = initialReadProgress,
            contentMode = contentMode,
            contentLoadState = contentLoadState,
            onRetryContentFetch = onRetryContentFetch,
            onSwitchToOriginal = onSwitchToOriginal
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
    onRetryContentFetch: () -> Unit = {},
    onSwitchToOriginal: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val hasArticleContent = uiState.bookmark.articleContent != null
    val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
    val needsRestore = isArticle && hasArticleContent && initialReadProgress > 0 && initialReadProgress <= 100
    var hasRestoredPosition by remember { mutableStateOf(!needsRestore) }
    var lastReportedProgress by remember { mutableStateOf(-1) }

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
                uiState = uiState
            )
        } else {
            // Reader mode: scrollable Column for article content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .alpha(if (hasRestoredPosition) 1f else 0f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BookmarkDetailHeader(
                    modifier = Modifier,
                    uiState = uiState,
                    onClickOpenUrl = onClickOpenUrl
                )

                val hasContent = uiState.bookmark.hasContent
                if (hasContent) {
                    BookmarkDetailArticle(
                        modifier = Modifier,
                        uiState = uiState
                    )
                } else {
                    // No content yet — show loading/retry/auto-switch based on content load state
                    when (contentLoadState) {
                        is ContentLoadState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is ContentLoadState.Failed -> {
                            if (!contentLoadState.canRetry) {
                                // Auto-switch to original view for permanent failures
                                LaunchedEffect(Unit) { onSwitchToOriginal() }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_view_no_content),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = contentLoadState.reason,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = onRetryContentFetch) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        else -> {
                            EmptyBookmarkDetailArticle(
                                modifier = Modifier
                            )
                        }
                    }
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
fun EmptyBookmarkDetailArticle(
    modifier: Modifier
) {
    Text(
        modifier = modifier,
        text = stringResource(R.string.detail_view_no_content)
    )
}

@Composable
fun BookmarkDetailArticle(
    modifier: Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success
) {
    val isSystemInDarkMode = isSystemInDarkTheme()
    val content = remember(uiState.bookmark.bookmarkId, isSystemInDarkMode, uiState.template) {
        mutableStateOf<String?>(null)
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(uiState.bookmark.bookmarkId, isSystemInDarkMode, uiState.template) {
        content.value = getTemplate(uiState, isSystemInDarkMode)
        webViewRef.value?.settings?.textZoom = uiState.zoomFactor
    }
    if (content.value != null) {
        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier.padding(0.dp),
                factory = { context ->
                    WebView(context).apply {
                        val isVideo = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO
                        settings.javaScriptEnabled = isVideo
                        settings.domStorageEnabled = isVideo
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.defaultTextEncodingName = "utf-8"
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.textZoom = uiState.zoomFactor
                        webViewRef.value = this
                    }
                },
                update = {
                    if (content.value != null && it.tag as? String != content.value) {
                        val baseUrl = if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO) {
                            uiState.bookmark.url
                        } else {
                            null
                        }
                        it.loadDataWithBaseURL(
                            baseUrl,
                            content.value!!,
                            "text/html",
                            "utf-8",
                            null
                        )
                        it.tag = content.value
                    }
                    // Update reference and zoom
                    webViewRef.value = it
                    it.settings.textZoom = uiState.zoomFactor
                }
            )
        }

    } else {
        CircularProgressIndicator()
    }
}

@Composable
fun BookmarkDetailOriginalWebView(
    modifier: Modifier = Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success
) {
    var loadingProgress by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        // Show progress indicator while loading
        if (loadingProgress < 100) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }

        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.defaultTextEncodingName = "utf-8"
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        // Track loading progress
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadingProgress = newProgress
                            }
                        }

                        loadUrl(uiState.bookmark.url)
                    }
                }
            )
        }
    }
}

suspend fun getTemplate(uiState: BookmarkDetailViewModel.UiState.Success, isSystemInDarkMode: Boolean): String? {
    return withContext(Dispatchers.IO) {
        uiState.bookmark.getContent(uiState.template, isSystemInDarkMode)
    }
}


@Composable
fun BookmarkDetailHeader(
    modifier: Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickOpenUrl: (String) -> Unit
) {
    val msg = stringResource(R.string.authors)
    val author = MessageFormat.format(
        msg, mapOf(
            "count" to uiState.bookmark.authors.size,
            "author" to uiState.bookmark.authors.firstOrNull()
        )
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section Start
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            text = uiState.bookmark.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = "$author - ${uiState.bookmark.createdDate}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = uiState.bookmark.siteName,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))
        // Header Section End
    }
}

@Composable
fun BookmarkDetailMenu(
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickToggleRead: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickDeleteBookmark: (String) -> Unit,
    onClickIncreaseZoomFactor: () -> Unit,
    onClickDecreaseZoomFactor: () -> Unit,
    contentMode: ContentMode = ContentMode.READER,
    onContentModeChange: (ContentMode) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_increase_text_size)) },
                onClick = {
                    onClickIncreaseZoomFactor()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.TextIncrease,
                        contentDescription = stringResource(R.string.action_increase_text_size)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_decrease_text_size)) },
                onClick = {
                    onClickDecreaseZoomFactor()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.TextDecrease,
                        contentDescription = stringResource(R.string.action_decrease_text_size)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_mark_read)) },
                onClick = {
                    onClickToggleRead(uiState.bookmark.bookmarkId, !uiState.bookmark.isRead)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (uiState.bookmark.isRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.action_mark_read)
                    )
                }
            )
            // View Original/Content toggle for all bookmark types
            if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
                uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO ||
                uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO) {

                val (labelRes, icon) = when {
                    contentMode == ContentMode.READER -> {
                        // In Reader mode, always show "View Original" with OpenInNew icon
                        Pair(R.string.action_view_original, Icons.AutoMirrored.Filled.OpenInNew)
                    }
                    // In Original mode, show type-specific label with type-specific icon
                    uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE -> {
                        Pair(R.string.action_view_article, Icons.Outlined.Description)
                    }
                    uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO -> {
                        Pair(R.string.action_view_photo, Icons.Filled.Image)
                    }
                    else -> { // VIDEO
                        Pair(R.string.action_view_video, Icons.Filled.Movie)
                    }
                }

                val isReaderMode = contentMode == ContentMode.READER
                val isEnabled = if (isReaderMode) true else uiState.bookmark.hasContent

                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    enabled = isEnabled,
                    onClick = {
                        val newMode = if (contentMode == ContentMode.READER) ContentMode.ORIGINAL else ContentMode.READER
                        onContentModeChange(newMode)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(labelRes),
                            tint = if (isEnabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                onClick = {
                    onClickShareBookmark(uiState.bookmark.url)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.action_share)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    onClickDeleteBookmark(uiState.bookmark.bookmarkId)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete)
                    )
                }
            )
        }
    }
}

@Composable
fun BookmarkDetailErrorScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.error_no_article_content))
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkDetailScreenPreview() {
    BookmarkDetailScreen(
        modifier = Modifier,
        snackbarHostState = SnackbarHostState(),
        onClickBack = {},
        onClickDeleteBookmark = {},
        onClickToggleFavorite = { _, _ -> },
        onClickToggleRead = { _, _ -> },
        onClickShareBookmark = {_ -> },
        onClickIncreaseZoomFactor = { },
        onClickDecreaseZoomFactor = { },
        onClickToggleArchive = { _, _ -> },
        uiState = BookmarkDetailViewModel.UiState.Success(
            bookmark = sampleBookmark,
            updateBookmarkState = null,
            template = Template.SimpleTemplate("template"),
            zoomFactor = 100
        ),
        onClickOpenUrl = {}
    )
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
                zoomFactor = 100
            ),
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

@Preview(showBackground = true)
@Composable
private fun BookmarkDetailHeaderPreview() {
    BookmarkDetailHeader(
        modifier = Modifier,
        uiState = BookmarkDetailViewModel.UiState.Success(
            bookmark = sampleBookmark,
            updateBookmarkState = null,
            template = Template.SimpleTemplate("template"),
            zoomFactor = 100
        ),
        onClickOpenUrl = {}
    )
}


private val sampleBookmark = BookmarkDetailViewModel.Bookmark(
    bookmarkId = "1",
    createdDate = "2024-01-15T10:00:00",
    url = "https://example.com",
    title = "This is a very long title of a small sample bookmark",
    siteName = "Example",
    authors = listOf("John Doe"),
    imgSrc = "https://via.placeholder.com/150",
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

enum class ContentMode {
    READER,
    ORIGINAL
}
