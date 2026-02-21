package com.mydeck.app.ui.list




import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.DrawerPreset
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.ui.components.FilterBar
import com.mydeck.app.ui.components.FilterBottomSheet
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.components.VerticalScrollbar
import com.mydeck.app.util.openUrlInCustomTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController

private const val PendingDeleteFromDetailKey = "pending_delete_bookmark_id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    navHostController: NavHostController,
    viewModel: BookmarkListViewModel,
    drawerState: DrawerState,
    showNavigationIcon: Boolean = true,
) {
    val uiState = viewModel.uiState.collectAsState().value
    val createBookmarkUiState = viewModel.createBookmarkUiState.collectAsState().value

    // Collect filter states
    val drawerPreset = viewModel.drawerPreset.collectAsState()
    val filterFormState = viewModel.filterFormState.collectAsState()
    val activeLabel = viewModel.activeLabel.collectAsState()
    val isFilterSheetOpen = viewModel.isFilterSheetOpen.collectAsState()
    val layoutMode = viewModel.layoutMode.collectAsState()
    val sortOption = viewModel.sortOption.collectAsState()
    val labelsWithCounts = viewModel.labelsWithCounts.collectAsState()
    val isLabelsSheetOpen = viewModel.isLabelsSheetOpen.collectAsState()

    var showLayoutMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameLabelDialog by remember { mutableStateOf(false) }
    var showDeleteLabelDialog by remember { mutableStateOf(false) }
    var scrollToTopTrigger by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pullToRefreshState = rememberPullToRefreshState()
    val isLoading by viewModel.loadBookmarksIsRunning.collectAsState()

    val isLabelMode = activeLabel.value != null
    val dismissPendingDeleteSnackbar: () -> Unit = {
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    fun stageDeleteWithSnackbar(bookmarkId: String, withHaptic: Boolean = false) {
        if (withHaptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        viewModel.onDeleteBookmark(bookmarkId)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Indefinite
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.onCancelDeleteBookmark()
            } else {
                viewModel.onConfirmDeleteBookmark()
            }
        }
    }

    // UI event handlers (pass filter update functions)
    val onClickBookmark: (String) -> Unit = { bookmarkId ->
        dismissPendingDeleteSnackbar()
        viewModel.onClickBookmark(bookmarkId)
    }
    val onClickDelete: (String) -> Unit = { bookmarkId ->
        stageDeleteWithSnackbar(bookmarkId, withHaptic = true)
    }
    val onClickFavorite: (String, Boolean) -> Unit = { bookmarkId, isFavorite ->
        dismissPendingDeleteSnackbar()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite)
    }
    val onClickArchive: (String, Boolean) -> Unit = { bookmarkId, isArchived ->
        dismissPendingDeleteSnackbar()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onToggleArchiveBookmark(bookmarkId, isArchived)
    }
    val onClickShareBookmark: (String) -> Unit = { url -> viewModel.onClickShareBookmark(url) }
    val onClickOpenUrl: (String) -> Unit = { bookmarkId ->
        dismissPendingDeleteSnackbar()
        viewModel.onClickBookmarkOpenOriginal(bookmarkId)
    }
    val onClickOpenInBrowser: (String) -> Unit = { url ->
        viewModel.onClickOpenInBrowser(url)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val onClickCopyLink: (String) -> Unit = { url ->
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(url))
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.link_copied),
                duration = SnackbarDuration.Short
            )
        }
    }
    val onClickShareLink: (String) -> Unit = { url ->
        viewModel.onClickShareBookmark(url)
    }
    val onClickCopyImageUrl: (String) -> Unit = { imageUrl ->
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(imageUrl))
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.image_url_copied),
                duration = SnackbarDuration.Short
            )
        }
    }
    val onClickDownloadImage: (String) -> Unit = { imageUrl ->
        if (imageUrl.isNotBlank() && imageUrl.startsWith("http")) {
            val request = android.app.DownloadManager.Request(
                android.net.Uri.parse(imageUrl)
            ).apply {
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    imageUrl.substringAfterLast('/').ifBlank { "image" }
                )
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.download_started),
                    duration = SnackbarDuration.Short
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.error_no_article_content),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    val onClickShareImage: (String) -> Unit = { imageUrl ->
        if (imageUrl.isNotBlank() && imageUrl.startsWith("http")) {
            scope.launch {
                try {
                    // Download image to cache
                    val url = java.net.URL(imageUrl)
                    val connection = url.openConnection()
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    
                    // Create unique file in cache
                    val fileName = imageUrl.substringAfterLast('/').ifBlank { "shared_image" }
                    val cacheDir = java.io.File(context.cacheDir, "images")
                    cacheDir.mkdirs()
                    val imageFile = java.io.File(cacheDir, "${fileName}_${System.currentTimeMillis()}")
                    
                    // Save image to file
                    inputStream.use { input ->
                        imageFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Share via FileProvider
                    val imageUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        imageFile
                    )
                    
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                        type = "image/*"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                } catch (e: Exception) {
                    // Fallback to URL sharing on error
                    val fallbackIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, imageUrl)
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(fallbackIntent, null))
                }
            }
        } else {
            // Fallback for invalid URLs
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.error_no_article_content),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    val onClickCopyLinkText: (String) -> Unit = { text ->
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.link_text_copied),
                duration = SnackbarDuration.Short
            )
        }
    }
    val onClickOpenInBrowserFromMenu: (String) -> Unit = { url ->
        viewModel.onClickOpenInBrowser(url)
    }
    LaunchedEffect(Unit) {
          viewModel.openUrlEvent.collectLatest { url ->
              openUrlInCustomTab(context, url)
          }
    }

    LaunchedEffect(navHostController) {
        val stateHandle = navHostController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        stateHandle.getStateFlow<String?>(PendingDeleteFromDetailKey, null).collectLatest { bookmarkId ->
            if (bookmarkId != null) {
                stateHandle[PendingDeleteFromDetailKey] = null
                dismissPendingDeleteSnackbar()
                stageDeleteWithSnackbar(bookmarkId)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Determine the current view title based on drawer preset
    val currentViewTitle = when (drawerPreset.value) {
        DrawerPreset.MY_LIST -> stringResource(id = R.string.my_list)
        DrawerPreset.ARCHIVE -> stringResource(id = R.string.archive)
        DrawerPreset.FAVORITES -> stringResource(id = R.string.favorites)
        DrawerPreset.ARTICLES -> stringResource(id = R.string.articles)
        DrawerPreset.VIDEOS -> stringResource(id = R.string.videos)
        DrawerPreset.PICTURES -> stringResource(id = R.string.pictures)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    if (isLabelMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                dismissPendingDeleteSnackbar()
                                scrollToTopTrigger++
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = activeLabel.value!!)
                        }
                    } else {
                        Text(
                            text = currentViewTitle,
                            modifier = Modifier.clickable {
                                dismissPendingDeleteSnackbar()
                                scrollToTopTrigger++
                            }
                        )
                    }
                },
                navigationIcon = {
                    if (showNavigationIcon) {
                        IconButton(
                            onClick = {
                                dismissPendingDeleteSnackbar()
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(id = R.string.menu)
                            )
                        }
                    }
                },
                actions = {
                    // Sort button with dropdown — one row per category, arrow shows direction of active sort
                    Box {
                        IconButton(onClick = {
                            dismissPendingDeleteSnackbar()
                            showSortMenu = true
                        }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            // Groups: (label, descOption, ascOption)
                            val sortGroups = listOf(
                                Triple("Added", SortOption.ADDED_NEWEST, SortOption.ADDED_OLDEST),
                                Triple("Published", SortOption.PUBLISHED_NEWEST, SortOption.PUBLISHED_OLDEST),
                                Triple("Title", SortOption.TITLE_A_TO_Z, SortOption.TITLE_Z_TO_A),
                                Triple("Site Name", SortOption.SITE_A_TO_Z, SortOption.SITE_Z_TO_A),
                                Triple("Duration", SortOption.DURATION_LONGEST, SortOption.DURATION_SHORTEST)
                            )
                            sortGroups.forEach { (label, firstOption, secondOption) ->
                                val isFirstSelected = sortOption.value == firstOption
                                val isSecondSelected = sortOption.value == secondOption
                                val isGroupSelected = isFirstSelected || isSecondSelected
                                val activeOption = if (isSecondSelected) secondOption else firstOption
                                val isDescending = activeOption.sqlOrderBy.contains("DESC")
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (isGroupSelected) {
                                            Icon(
                                                imageVector = if (isDescending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (isGroupSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        val newOption = when {
                                            isFirstSelected -> secondOption  // toggle to other direction
                                            isSecondSelected -> firstOption  // toggle back
                                            else -> firstOption              // first tap: use default
                                        }
                                        viewModel.onSortOptionSelected(newOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Layout button with dropdown — icon reflects current mode
                    Box {
                        val currentLayoutIcon = when (layoutMode.value) {
                            LayoutMode.GRID -> Icons.Filled.Apps
                            LayoutMode.COMPACT -> Icons.AutoMirrored.Filled.List
                            LayoutMode.MOSAIC -> Icons.Filled.GridView
                        }
                        IconButton(onClick = {
                            dismissPendingDeleteSnackbar()
                            showLayoutMenu = true
                        }) {
                            Icon(currentLayoutIcon, contentDescription = stringResource(R.string.layout))
                        }
                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false }
                        ) {
                            LayoutMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                LayoutMode.GRID -> Icons.Filled.Apps
                                                LayoutMode.COMPACT -> Icons.AutoMirrored.Filled.List
                                                LayoutMode.MOSAIC -> Icons.Filled.GridView
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = mode.displayName,
                                            fontWeight = if (mode == layoutMode.value) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.onLayoutModeSelected(mode)
                                        showLayoutMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (isLabelMode) {
                        // Overflow menu with Rename/Delete label options
                        Box {
                            IconButton(onClick = {
                                dismissPendingDeleteSnackbar()
                                showOverflowMenu = true
                            }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename_label)) },
                                    onClick = {
                                        showRenameLabelDialog = true
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_label)) },
                                    onClick = {
                                        showDeleteLabelDialog = true
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    } else {
                        // Filter button (non-label mode only)
                        IconButton(onClick = {
                            dismissPendingDeleteSnackbar()
                            viewModel.onOpenFilterSheet()
                        }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filter_bookmarks))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val clipboardManager = LocalClipboardManager.current
            FloatingActionButton(
                onClick = {
                    dismissPendingDeleteSnackbar()
                    val clipboardText = clipboardManager.getText()?.text
                    viewModel.openCreateBookmarkDialog(clipboardText)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.add_bookmark)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
        ) {
            // FilterBar: visible when filters are active beyond preset defaults, not in label mode
            if (!isLabelMode) {
                FilterBar(
                    filterFormState = filterFormState.value,
                    drawerPreset = drawerPreset.value,
                    onFilterChanged = { viewModel.onApplyFilter(it) },
                    onOpenFilterSheet = { viewModel.onOpenFilterSheet() }
                )
            }

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.onPullToRefresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (uiState) {
                    is BookmarkListViewModel.UiState.Empty -> {
                        val emptyIcon = when (uiState.messageResource) {
                            R.string.list_view_empty_error_loading_bookmarks -> Icons.Outlined.CloudOff
                            else -> Icons.Outlined.Bookmarks
                        }
                        EmptyScreen(icon = emptyIcon, headlineResource = uiState.messageResource)
                    }
                    is BookmarkListViewModel.UiState.Success -> {
                        if (uiState.bookmarks.isEmpty()) {
                            EmptyScreen(
                                icon = Icons.Outlined.SearchOff,
                                headlineResource = R.string.filter_no_results
                            )
                        } else {
                    BookmarkListView(
                                filterKey = Pair(filterFormState.value, activeLabel.value),
                                scrollToTopTrigger = scrollToTopTrigger,
                                layoutMode = layoutMode.value,
                                bookmarks = uiState.bookmarks,
                                onClickBookmark = onClickBookmark,
                                onClickDelete = onClickDelete,
                                onClickArchive = onClickArchive,
                                onClickFavorite = onClickFavorite,
                                onClickShareBookmark = onClickShareBookmark,
                                onClickLabel = { label -> viewModel.onClickLabel(label) },
                                onClickOpenUrl = onClickOpenUrl,
                                onClickOpenInBrowser = onClickOpenInBrowser,
                                onClickCopyLink = onClickCopyLink,
                                onClickCopyLinkText = onClickCopyLinkText,
                                onClickShareLink = onClickShareLink,
                        onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                        onClickCopyImageUrl = onClickCopyImageUrl,
                        onClickDownloadImage = onClickDownloadImage,
                        onClickShareImage = onClickShareImage,
                        onUserInteraction = dismissPendingDeleteSnackbar,
                    )
                }
                        // Consumes a shareIntent and creates the corresponding share dialog
                        ShareBookmarkChooser(
                            context = LocalContext.current,
                            intent = viewModel.shareIntent.collectAsState().value,
                            onShareIntentConsumed = { viewModel.onShareIntentConsumed() }
                        )
                    }
                }
            }
        }

        // Show the Add Bookmark bottom sheet based on the state
        when (createBookmarkUiState) {
            is BookmarkListViewModel.CreateBookmarkUiState.Open -> {
                AddBookmarkBottomSheet(
                    title = createBookmarkUiState.title,
                    url = createBookmarkUiState.url,
                    urlError = createBookmarkUiState.urlError,
                    isCreateEnabled = createBookmarkUiState.isCreateEnabled,
                    labels = createBookmarkUiState.labels,
                    isFavorite = createBookmarkUiState.isFavorite,
                    existingLabels = labelsWithCounts.value.keys.toList(),
                    onTitleChange = { viewModel.updateCreateBookmarkTitle(it) },
                    onUrlChange = { viewModel.updateCreateBookmarkUrl(it) },
                    onLabelsChange = { viewModel.updateCreateBookmarkLabels(it) },
                    onFavoriteToggle = { viewModel.updateCreateBookmarkFavorite(it) },
                    onCreateBookmark = { viewModel.createBookmark() },
                    onAction = { action -> viewModel.handleCreateBookmarkAction(action) },
                    onDismiss = { viewModel.closeCreateBookmarkDialog() }
                )
            }

            is BookmarkListViewModel.CreateBookmarkUiState.Loading -> {
                // Show a loading indicator
                Dialog(onDismissRequest = { viewModel.closeCreateBookmarkDialog() }) {
                    CircularProgressIndicator()
                }
            }

            is BookmarkListViewModel.CreateBookmarkUiState.Success -> {
                LaunchedEffect(key1 = createBookmarkUiState) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.bookmark_added),
                            duration = SnackbarDuration.Short
                        )
                        viewModel.closeCreateBookmarkDialog()
                    }
                }
            }

            is BookmarkListViewModel.CreateBookmarkUiState.Error -> {
                // Show an error message
                AlertDialog(
                    onDismissRequest = { viewModel.closeCreateBookmarkDialog() },
                    title = { Text(stringResource(id = R.string.error)) },
                    text = { Text(createBookmarkUiState.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.closeCreateBookmarkDialog() }) {
                            Text(stringResource(id = R.string.ok))
                        }
                    }
                )
            }

            is BookmarkListViewModel.CreateBookmarkUiState.Closed -> {
                // Do nothing when the dialog is closed
            }
        }

    }

    // Filter bottom sheet
    if (isFilterSheetOpen.value) {
        FilterBottomSheet(
            currentFilter = filterFormState.value,
            labels = labelsWithCounts.value,
            onApply = { viewModel.onApplyFilter(it) },
            onReset = { viewModel.onResetFilter() },
            onDismiss = { viewModel.onCloseFilterSheet() }
        )
    }

    if (isLabelsSheetOpen.value) {
        LabelsBottomSheet(
            labels = labelsWithCounts.value,
            selectedLabel = activeLabel.value,
            onLabelSelected = { label -> viewModel.onClickLabel(label) },
            onRenameLabel = { oldLabel, newLabel -> viewModel.onRenameLabel(oldLabel, newLabel) },
            onDeleteLabel = { label -> viewModel.onDeleteLabel(label) },
            onDismiss = { viewModel.onCloseLabelsSheet() }
        )
    }

    // Label mode: Rename label dialog (from TopAppBar overflow menu)
    if (showRenameLabelDialog && activeLabel.value != null) {
        val currentLabel = activeLabel.value!!
        var newName by remember(currentLabel) { mutableStateOf(currentLabel) }
        AlertDialog(
            onDismissRequest = { showRenameLabelDialog = false },
            title = { Text(stringResource(R.string.rename_label)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != currentLabel) {
                            viewModel.onRenameLabel(currentLabel, newName)
                        }
                        showRenameLabelDialog = false
                    }
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameLabelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Label mode: Delete label dialog (from TopAppBar overflow menu)
    if (showDeleteLabelDialog && activeLabel.value != null) {
        val currentLabel = activeLabel.value!!
        AlertDialog(
            onDismissRequest = { showDeleteLabelDialog = false },
            title = { Text(stringResource(R.string.delete_label)) },
            text = { Text(stringResource(R.string.delete_label_confirm_message, currentLabel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteLabel(currentLabel)
                        showDeleteLabelDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLabelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBookmarkBottomSheet(
    title: String,
    url: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    isFavorite: Boolean = false,
    existingLabels: List<String> = emptyList(),
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onFavoriteToggle: (Boolean) -> Unit = {},
    onCreateBookmark: () -> Unit,
    onAction: (SaveAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AddBookmarkSheet(
            url = url,
            title = title,
            urlError = urlError,
            isCreateEnabled = isCreateEnabled,
            labels = labels,
            isFavorite = isFavorite,
            existingLabels = existingLabels,
            onUrlChange = onUrlChange,
            onTitleChange = onTitleChange,
            onLabelsChange = onLabelsChange,
            onFavoriteToggle = onFavoriteToggle,
            onCreateBookmark = onCreateBookmark,
            onAction = onAction
        )
    }
}

@Composable
fun EmptyScreen(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Bookmarks,
    headlineResource: Int
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = headlineResource),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun BookmarkListView(
    modifier: Modifier = Modifier,
    filterKey: Any = Unit,
    scrollToTopTrigger: Int = 0,
    layoutMode: LayoutMode = LayoutMode.GRID,
    bookmarks: List<BookmarkListItem>,
    isMultiColumn: Boolean = LocalIsWideLayout.current,
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickShareLink: (String) -> Unit = {},
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImageUrl: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onUserInteraction: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp >= configuration.screenWidthDp
    val useMobilePortraitGridLayout = !isMultiColumn && isPortrait && layoutMode == LayoutMode.GRID

    if (isMultiColumn && layoutMode != LayoutMode.COMPACT) {
        val columns = when (layoutMode) {
            LayoutMode.GRID -> GridCells.Adaptive(minSize = 250.dp)
            LayoutMode.COMPACT -> GridCells.Adaptive(minSize = 350.dp)
            LayoutMode.MOSAIC -> GridCells.Adaptive(minSize = 180.dp)
        }
        val spacing = when (layoutMode) {
            LayoutMode.MOSAIC -> 4.dp
            else -> 8.dp
        }
        val lazyGridState = key(filterKey) { rememberLazyGridState() }
        LaunchedEffect(scrollToTopTrigger) {
            if (scrollToTopTrigger > 0) {
                lazyGridState.animateScrollToItem(0)
            }
        }
        LaunchedEffect(lazyGridState.isScrollInProgress) {
            if (lazyGridState.isScrollInProgress) onUserInteraction()
        }
        Box(modifier = modifier) {
            LazyVerticalGrid(
                columns = columns,
                state = lazyGridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(horizontal = spacing),
            ) {
                items(bookmarks) { bookmark ->
                    when (layoutMode) {
                        LayoutMode.GRID -> BookmarkGridCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage,
                            isInGrid = true,
                        )
                        LayoutMode.COMPACT -> BookmarkCompactCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage,
                        )
                        LayoutMode.MOSAIC -> BookmarkMosaicCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage
                        )
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                lazyGridState = lazyGridState
            )
        }
    } else {
        val lazyListState = key(filterKey) { rememberLazyListState() }
        LaunchedEffect(scrollToTopTrigger) {
            if (scrollToTopTrigger > 0) {
                lazyListState.animateScrollToItem(0)
            }
        }
        LaunchedEffect(lazyListState.isScrollInProgress) {
            if (lazyListState.isScrollInProgress) onUserInteraction()
        }
        Box(modifier = modifier) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                items(bookmarks) { bookmark ->
                    when (layoutMode) {
                        LayoutMode.GRID -> BookmarkGridCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage,
                            useMobilePortraitLayout = useMobilePortraitGridLayout,
                        )
                        LayoutMode.COMPACT -> BookmarkCompactCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage
                        )
                        LayoutMode.MOSAIC -> BookmarkMosaicCard(
                            bookmark = bookmark,
                            onClickCard = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickShareBookmark = onClickShareBookmark,
                            onClickLabel = onClickLabel,
                            onClickOpenUrl = onClickOpenUrl,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickCopyLink = onClickCopyLink,
                            onClickCopyLinkText = onClickCopyLinkText,
                            onClickShareLink = onClickShareLink,
                            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                            onClickCopyImageUrl = onClickCopyImageUrl,
                            onClickDownloadImage = onClickDownloadImage,
                            onClickShareImage = onClickShareImage
                        )
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                lazyListState = lazyListState
            )
        }
    }
}

@Preview
@Composable
fun EmptyScreenPreview() {
    EmptyScreen(headlineResource = R.string.list_view_empty_nothing_to_see)
}

@Preview(showBackground = true)
@Composable
fun BookmarkListViewPreview() {
    val sampleBookmark = BookmarkListItem(
        id = "1",
        url = "https://example.com",
        title = "Sample Bookmark",
        siteName = "Example",
        type = Bookmark.Type.Article,
        isMarked = false,
        isArchived = false,
        labels = listOf(
            "one",
            "two",
            "three",
            "fourhundretandtwentyone",
            "threethousendtwohundretandfive"
        ),
        isRead = true,
        readProgress = 100,
        iconSrc = "https://picsum.photos/seed/picsum/640/480",
        imageSrc = "https://picsum.photos/seed/picsum/640/480",
        thumbnailSrc = "https://picsum.photos/seed/picsum/640/480",
        readingTime = 8,
        created = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        wordCount = 2000,
        published = null
    )
    val bookmarks = listOf(sampleBookmark)

    // Provide a dummy NavHostController for the preview
    val navController = rememberNavController()
    BookmarkListView(
        modifier = Modifier,
        layoutMode = LayoutMode.GRID,
        bookmarks = bookmarks,
        onClickBookmark = {},
        onClickDelete = {},
        onClickArchive = { _, _ -> },
        onClickFavorite = { _, _ -> },
        onClickShareBookmark = {_ -> },
        onClickLabel = {}
    )
}
