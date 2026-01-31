package com.mydeck.app.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atZone
import kotlinx.datetime.toLocalDateTime
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.navigation.AboutRoute
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import com.mydeck.app.ui.navigation.SettingsRoute
import com.mydeck.app.util.openUrlInCustomTab
import kotlinx.coroutines.launch
import androidx.compose.material3.Badge
import com.mydeck.app.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(navHostController: NavHostController) {
    val viewModel: BookmarkListViewModel = hiltViewModel()
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val openUrlEvent = viewModel.openUrlEvent.collectAsState()
    val uiState = viewModel.uiState.collectAsState().value
    val createBookmarkUiState = viewModel.createBookmarkUiState.collectAsState().value
    val bookmarkCounts = viewModel.bookmarkCounts.collectAsState()

    // Collect filter states
    val filterState = viewModel.filterState.collectAsState()
    val isSearchActive = viewModel.isSearchActive.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
    val layoutMode = viewModel.layoutMode.collectAsState()
    val sortOption = viewModel.sortOption.collectAsState()

    var showOverflowMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLayoutDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSortDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pullToRefreshState = rememberPullToRefreshState()
    val isLoading by viewModel.loadBookmarksIsRunning.collectAsState()

    // UI event handlers (pass filter update functions)
    val onClickFilterMyList: () -> Unit = { viewModel.onClickMyList() }
    val onClickFilterArchive: () -> Unit = { viewModel.onClickArchive() }
    val onClickFilterFavorite: () -> Unit = { viewModel.onClickFavorite() }
    val onClickSettings: () -> Unit = { viewModel.onClickSettings() }
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
    val onClickOpenInBrowser: (String) -> Unit = { url -> viewModel.onClickOpenInBrowser(url) }
    val onClickShareBookmark: (String) -> Unit = { url -> viewModel.onClickShareBookmark(url) }

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is BookmarkListViewModel.NavigationEvent.NavigateToSettings -> {
                    navHostController.navigate(SettingsRoute)
                    scope.launch { drawerState.close() }
                }

                is BookmarkListViewModel.NavigationEvent.NavigateToAbout -> {
                    navHostController.navigate(AboutRoute)
                    scope.launch { drawerState.close() }
                }

                is BookmarkListViewModel.NavigationEvent.NavigateToBookmarkDetail -> {
                    navHostController.navigate(BookmarkDetailRoute(event.bookmarkId))
                }
            }
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }

    val context = LocalContext.current
    LaunchedEffect(key1 = openUrlEvent.value) {
        openUrlInCustomTab(context, openUrlEvent.value)
        viewModel.onOpenUrlEventConsumed()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(id = R.string.app_name),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.my_list)
                        ) },
                        icon = { Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null)},
                        badge = {
                            val myListCount = bookmarkCounts.value.total - bookmarkCounts.value.archived
                            if (myListCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        text = myListCount.toString()
                                    )
                                }
                            }
                        },
                        selected = filterState.value.archived == false,
                        onClick = {
                            onClickFilterMyList()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.archive)
                        ) },
                        icon = { Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null) },
                        badge = {
                            bookmarkCounts.value.archived.let { count ->
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                        Text(
                                            text = count.toString()
                                        )
                                    }
                                }
                            }
                        },
                        selected = filterState.value.archived == true,
                        onClick = {
                            onClickFilterArchive()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.favorites)
                        ) },
                        icon = { Icon(imageVector = Icons.Filled.Grade, contentDescription = null) },
                        badge = {
                            bookmarkCounts.value.favorite.let { count ->
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                        Text(
                                            text = count.toString()
                                        )
                                    }
                                }
                            }
                        },
                        selected = filterState.value.favorite == true,
                        onClick = {
                            onClickFilterFavorite()
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.settings)
                        ) },
                        icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
                        selected = false,
                        onClick = {
                            onClickSettings()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.about_title)
                        ) },
                        icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                        selected = false,
                        onClick = {
                            viewModel.onClickAbout()
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Determine the current view title based on filter state
                val currentViewTitle = when {
                    filterState.value.archived == false -> stringResource(id = R.string.my_list)
                    filterState.value.archived == true -> stringResource(id = R.string.archive)
                    filterState.value.favorite == true -> stringResource(id = R.string.favorites)
                    else -> stringResource(id = R.string.my_list) // Default to My List
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
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
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
                        } else {
                            Text(currentViewTitle)
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
                            IconButton(onClick = { viewModel.onSearchActiveChange(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Layout") },
                                    onClick = { showLayoutDialog = true; showOverflowMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort") },
                                    onClick = { showSortDialog = true; showOverflowMenu = false }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.openCreateBookmarkDialog() }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(id = R.string.add_bookmark)
                    )
                }
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.onPullToRefresh() },
                state = pullToRefreshState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
            ) {
                when (uiState) {
                    is BookmarkListViewModel.UiState.Empty -> {
                        EmptyScreen(messageResource = uiState.messageResource)
                    }
                    is BookmarkListViewModel.UiState.Success -> {
                        LaunchedEffect(key1 = uiState.updateBookmarkState) {
                            uiState.updateBookmarkState?.let { result ->
                                val message = when (result) {
                                    is BookmarkListViewModel.UpdateBookmarkState.Success -> {
                                        "success"
                                    }

                                    is BookmarkListViewModel.UpdateBookmarkState.Error -> {
                                        result.message
                                    }
                                }
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                        BookmarkListView(
                            layoutMode = layoutMode.value,
                            bookmarks = uiState.bookmarks,
                            onClickBookmark = onClickBookmark,
                            onClickDelete = onClickDelete,
                            onClickArchive = onClickArchive,
                            onClickFavorite = onClickFavorite,
                            onClickOpenInBrowser = onClickOpenInBrowser,
                            onClickShareBookmark = onClickShareBookmark
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

            // Show the CreateBookmarkDialog based on the state
            when (createBookmarkUiState) {
                is BookmarkListViewModel.CreateBookmarkUiState.Open -> {
                    CreateBookmarkDialog(
                        onDismiss = { viewModel.closeCreateBookmarkDialog() },
                        title = createBookmarkUiState.title,
                        url = createBookmarkUiState.url,
                        urlError = createBookmarkUiState.urlError,
                        isCreateEnabled = createBookmarkUiState.isCreateEnabled,
                        onTitleChange = { viewModel.updateCreateBookmarkTitle(it) },
                        onUrlChange = { viewModel.updateCreateBookmarkUrl(it) },
                        onCreateBookmark = { viewModel.createBookmark() }
                    )
                }

                is BookmarkListViewModel.CreateBookmarkUiState.Loading -> {
                    // Show a loading indicator
                    Dialog(onDismissRequest = { viewModel.closeCreateBookmarkDialog() }) {
                        CircularProgressIndicator()
                    }
                }

                is BookmarkListViewModel.CreateBookmarkUiState.Success -> {
                    // Optionally show a success message
                    LaunchedEffect(key1 = createBookmarkUiState) {
                        // Dismiss the dialog after a short delay
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
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

            // Layout Picker Dialog
            if (showLayoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLayoutDialog = false },
                    title = { Text("Select Layout") },
                    text = {
                        Column {
                            LayoutMode.entries.forEach { mode ->
                                Button(
                                    onClick = {
                                        viewModel.onLayoutModeSelected(mode)
                                        showLayoutDialog = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(mode.name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLayoutDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Sort Picker Dialog
            if (showSortDialog) {
                AlertDialog(
                    onDismissRequest = { showSortDialog = false },
                    title = { Text("Sort by") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(SortOption.entries) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onSortOptionSelected(option)
                                            showSortDialog = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = sortOption.value == option,
                                        onClick = {
                                            viewModel.onSortOptionSelected(option)
                                            showSortDialog = false
                                        }
                                    )
                                    Text(
                                        option.displayName,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSortDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CreateBookmarkDialog(
    onDismiss: () -> Unit,
    title: String,
    url: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onCreateBookmark: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.add_new_bookmark)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { onUrlChange(it) },
                    isError = urlError != null,
                    label = { Text(stringResource(id = R.string.url)) },
                    supportingText = {
                        urlError?.let {
                            Text(text = stringResource(it))
                        }
                    }
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it) },
                    label = { Text(stringResource(id = R.string.title)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
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
    layoutMode: LayoutMode = LayoutMode.CARD,
    bookmarks: List<BookmarkListItem>,
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenInBrowser: (String) -> Unit,
    onClickShareBookmark: (String) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(bookmarks) { bookmark ->
            when (layoutMode) {
                LayoutMode.CARD -> BookmarkCard(
                    bookmark = bookmark,
                    onClickCard = onClickBookmark,
                    onClickDelete = onClickDelete,
                    onClickArchive = onClickArchive,
                    onClickFavorite = onClickFavorite,
                    onClickOpenUrl = onClickOpenInBrowser,
                    onClickShareBookmark = onClickShareBookmark
                )
                LayoutMode.MAGAZINE -> BookmarkMagazineView(
                    bookmark = bookmark,
                    onClickCard = onClickBookmark,
                    onClickDelete = onClickDelete,
                    onClickArchive = onClickArchive,
                    onClickFavorite = onClickFavorite,
                    onClickOpenUrl = onClickOpenInBrowser,
                    onClickShareBookmark = onClickShareBookmark
                )
                LayoutMode.LIST -> BookmarkListItemView(
                    bookmark = bookmark,
                    onClickCard = onClickBookmark,
                    onClickDelete = onClickDelete,
                    onClickArchive = onClickArchive,
                    onClickFavorite = onClickFavorite,
                    onClickOpenUrl = onClickOpenInBrowser,
                    onClickShareBookmark = onClickShareBookmark
                )
            }
        }
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
        created = Clock.System.now().atZone(TimeZone.currentSystemDefault()).toLocalDateTime(),
        wordCount = 2000
    )
    val bookmarks = listOf(sampleBookmark)

    // Provide a dummy NavHostController for the preview
    val navController = rememberNavController()
    BookmarkListView(
        modifier = Modifier,
        layoutMode = LayoutMode.CARD,
        bookmarks = bookmarks,
        onClickBookmark = {},
        onClickDelete = {},
        onClickArchive = { _, _ -> },
        onClickFavorite = { _, _ -> },
        onClickOpenInBrowser = {},
        onClickShareBookmark = {_ -> }
    )
}
