package com.mydeck.app.ui.list




import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.components.VerticalScrollbar
import com.mydeck.app.util.openUrlInCustomTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.Badge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    navHostController: NavHostController,
    viewModel: BookmarkListViewModel,
    drawerState: DrawerState,
) {
    val uiState = viewModel.uiState.collectAsState().value
    val createBookmarkUiState = viewModel.createBookmarkUiState.collectAsState().value

    // Collect filter states
    val filterState = viewModel.filterState.collectAsState()
    val isSearchActive = viewModel.isSearchActive.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
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

    val pullToRefreshState = rememberPullToRefreshState()
    val isLoading by viewModel.loadBookmarksIsRunning.collectAsState()

    // UI event handlers (pass filter update functions)
    val onClickBookmark: (String) -> Unit = { bookmarkId -> viewModel.onClickBookmark(bookmarkId) }
    val onClickDelete: (String) -> Unit = { bookmarkId ->
        viewModel.onDeleteBookmark(bookmarkId)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.onCancelDeleteBookmark()
            }
        }
    }
    val onClickFavorite: (String, Boolean) -> Unit = { bookmarkId, isFavorite -> viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite) }
    val onClickArchive: (String, Boolean) -> Unit = { bookmarkId, isArchived -> viewModel.onToggleArchiveBookmark(bookmarkId, isArchived) }
    val onClickShareBookmark: (String) -> Unit = { url -> viewModel.onClickShareBookmark(url) }
    val onClickOpenUrl: (String) -> Unit = { bookmarkId ->
        viewModel.onClickBookmarkOpenOriginal(bookmarkId)
    }
    val onClickOpenInBrowser: (String) -> Unit = { url ->
        viewModel.onClickOpenInBrowser(url)
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
          viewModel.openUrlEvent.collectLatest { url ->
              openUrlInCustomTab(context, url)
          }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val isLabelMode = filterState.value.label != null

            // Determine the current view title based on filter state
            val currentViewTitle = when {
                filterState.value.archived == false -> stringResource(id = R.string.my_list)
                filterState.value.archived == true -> stringResource(id = R.string.archive)
                filterState.value.favorite == true -> stringResource(id = R.string.favorites)
                else -> stringResource(id = R.string.my_list)
            }

            val searchFocusRequester = remember { FocusRequester() }

            LaunchedEffect(isSearchActive.value) {
                if (isSearchActive.value) {
                    searchFocusRequester.requestFocus()
                }
            }

            TopAppBar(
                title = {
                    if (isSearchActive.value) {
                        OutlinedTextField(
                            value = searchQuery.value,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = {
                                Text(stringResource(R.string.search_bookmarks))
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                if (searchQuery.value.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onClearSearch() }) {
                                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    } else if (isLabelMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { scrollToTopTrigger++ }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = filterState.value.label!!)
                        }
                    } else {
                        Text(
                            text = currentViewTitle,
                            modifier = Modifier.clickable {
                                scrollToTopTrigger++
                            }
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchActive.value) {
                        IconButton(onClick = { viewModel.onSearchActiveChange(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close_search))
                        }
                    } else {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(id = R.string.menu)
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearchActive.value) {
                        // Sort button with dropdown
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            RadioButton(
                                                selected = option == sortOption.value,
                                                onClick = null
                                            )
                                        },
                                        text = { Text(text = option.displayName) },
                                        onClick = {
                                            viewModel.onSortOptionSelected(option)
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
                            IconButton(onClick = { showLayoutMenu = true }) {
                                Icon(currentLayoutIcon, contentDescription = "Layout")
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
                                                fontWeight = if (mode == layoutMode.value) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
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
                                IconButton(onClick = { showOverflowMenu = true }) {
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
                            // Search button (non-label mode only)
                            IconButton(onClick = { viewModel.onSearchActiveChange(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val clipboardManager = LocalClipboardManager.current
            FloatingActionButton(
                onClick = {
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
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.onPullToRefresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (uiState) {
                    is BookmarkListViewModel.UiState.Empty -> {
                        EmptyScreen(messageResource = uiState.messageResource)
                    }
                    is BookmarkListViewModel.UiState.Success -> {
                        BookmarkListView(
                            filterKey = filterState.value,
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
                            onClickOpenInBrowser = onClickOpenInBrowser
                        )
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
                    onTitleChange = { viewModel.updateCreateBookmarkTitle(it) },
                    onUrlChange = { viewModel.updateCreateBookmarkUrl(it) },
                    onLabelsChange = { viewModel.updateCreateBookmarkLabels(it) },
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

    if (isLabelsSheetOpen.value) {
        LabelsBottomSheet(
            labels = labelsWithCounts.value,
            selectedLabel = filterState.value.label,
            onLabelSelected = { label -> viewModel.onClickLabel(label) },
            onRenameLabel = { oldLabel, newLabel -> viewModel.onRenameLabel(oldLabel, newLabel) },
            onDeleteLabel = { label -> viewModel.onDeleteLabel(label) },
            onDismiss = { viewModel.onCloseLabelsSheet() }
        )
    }

    // Label mode: Rename label dialog (from TopAppBar overflow menu)
    if (showRenameLabelDialog && filterState.value.label != null) {
        val currentLabel = filterState.value.label!!
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
    if (showDeleteLabelDialog && filterState.value.label != null) {
        val currentLabel = filterState.value.label!!
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
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
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
            onUrlChange = onUrlChange,
            onTitleChange = onTitleChange,
            onLabelsChange = onLabelsChange,
            onCreateBookmark = onCreateBookmark,
            onAction = onAction
        )
    }
}

@Composable
fun EmptyScreen(
    modifier: Modifier = Modifier,
    messageResource: Int
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(stringResource(id = messageResource))
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
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {}
) {
    val lazyListState = key(filterKey) { rememberLazyListState() }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            lazyListState.animateScrollToItem(0)
        }
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
                        onClickOpenInBrowser = onClickOpenInBrowser
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
                        onClickOpenInBrowser = onClickOpenInBrowser
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
                    onClickOpenInBrowser = onClickOpenInBrowser
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

@Preview
@Composable
fun EmptyScreenPreview() {
    EmptyScreen(messageResource = R.string.list_view_empty_nothing_to_see)
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
