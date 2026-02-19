package com.mydeck.app.ui.detail

import android.content.Intent
import android.icu.text.MessageFormat
import android.net.Uri
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.mydeck.app.R
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.components.TimedDeleteSnackbar
import com.mydeck.app.ui.detail.BookmarkDetailViewModel.ContentLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mydeck.app.ui.detail.components.*

@Composable
fun BookmarkDetailScreen(navHostController: NavController, bookmarkId: String?, showOriginal: Boolean = false) {
    val viewModel: BookmarkDetailViewModel = hiltViewModel()
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onClickToggleFavorite: (String, Boolean) -> Unit =
        { id, isFavorite -> viewModel.onToggleFavorite(id, isFavorite) }
    val onClickToggleArchive: (String, Boolean) -> Unit =
        { id, isArchived -> viewModel.onToggleArchive(id, isArchived) }
    val context = LocalContext.current
    val onClickOpenUrl: (String) -> Unit = { viewModel.onClickOpenUrl(it) }
    val onClickShareBookmark: (String) -> Unit = { url -> viewModel.onClickShareBookmark(url) }
    val onClickToggleRead: (String, Boolean) -> Unit = { id, isRead -> viewModel.onToggleRead(id, isRead) }
    val onUpdateLabels: (String, List<String>) -> Unit = { id, labels -> viewModel.onUpdateLabels(id, labels) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsState().value
    val contentLoadState = viewModel.contentLoadState.collectAsState().value
    val articleSearchState = viewModel.articleSearchState.collectAsState().value
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showTypographyPanel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val onClickDeleteBookmark: (String) -> Unit = { id ->
        viewModel.deleteBookmark(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onCancelDeleteBookmark()
            }
        }
    }

    val onClickOpenInBrowser: (String) -> Unit = { url ->
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

    DisposableEffect(lifecycleOwner, bookmarkId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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
                    navHostController.popBackStack()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collectLatest { url ->
            openUrlInCustomTab(context, url)
        }
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
            LaunchedEffect(contentLoadState) {
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
                }
            )

            // Handle share intent events
            LaunchedEffect(Unit) {
                viewModel.shareIntent.collectLatest { intent ->
                    val chooser = Intent.createChooser(intent, null)
                    context.startActivity(chooser)
                }
            }
            if (showDetailsDialog) {
                BookmarkDetailsDialog(
                    bookmark = uiState.bookmark,
                    onDismissRequest = { showDetailsDialog = false },
                    onLabelsUpdate = { newLabels ->
                        onUpdateLabels(uiState.bookmark.bookmarkId, newLabels)
                    }
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
    onTitleChanged: ((String) -> Unit)? = null
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> TimedDeleteSnackbar(data) } },
        modifier = modifier,
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
                onContentModeChange = onContentModeChange
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
            articleSearchState = articleSearchState,
            onArticleSearchUpdateResults = onArticleSearchUpdateResults,
            onTitleChanged = onTitleChanged
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
    onTitleChanged: ((String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val hasArticleContent = uiState.bookmark.articleContent != null
    val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
    val needsRestore = isArticle && hasArticleContent && initialReadProgress > 0 && initialReadProgress <= 100
    // Key on hasArticleContent so when content arrives after on-demand fetch,
    // the state resets and scroll position restore is triggered
    var hasRestoredPosition by remember(hasArticleContent) { mutableStateOf(!needsRestore) }
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
                url = uiState.bookmark.url
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
                val headerWidthModifier = when (uiState.typographySettings.textWidth) {
                    TextWidth.WIDE -> Modifier.fillMaxWidth(0.9f)
                    TextWidth.NARROW -> Modifier.fillMaxWidth(0.75f)
                }
                BookmarkDetailHeader(
                    modifier = headerWidthModifier,
                    uiState = uiState,
                    onClickOpenUrl = onClickOpenUrl,
                    onTitleChanged = onTitleChanged
                )

                val hasContent = uiState.bookmark.hasContent
                if (hasContent) {
                    BookmarkDetailArticle(
                        modifier = Modifier,
                        uiState = uiState,
                        articleSearchState = articleSearchState,
                        onArticleSearchUpdateResults = onArticleSearchUpdateResults
                    )
                } else {
                    // No content yet — show loading spinner while fetch is in progress.
                    // Auto-switch to Original mode is handled by LaunchedEffect above
                    // when the content load state transitions to Failed.
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
                        else -> {
                            // Brief fallback while auto-switch hasn't happened yet
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

