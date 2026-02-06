package com.mydeck.app.ui.list

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
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
    val labelsWithCounts = viewModel.labelsWithCounts.collectAsState()

    // Collect filter states
    val filterState = viewModel.filterState.collectAsState()
    val isSearchActive = viewModel.isSearchActive.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
    val layoutMode = viewModel.layoutMode.collectAsState()
    val sortOption = viewModel.sortOption.collectAsState()

    var showLayoutMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSortMenu by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Label edit/delete state
    var isEditingLabel by remember { mutableStateOf(false) }
    var editedLabelName by remember { mutableStateOf("") }
    var pendingDeleteLabel by remember { mutableStateOf<String?>(null) }
    var deleteLabelJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val labelEditFocusRequester = remember { FocusRequester() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val bookmarkDeletedMessage = stringResource(R.string.snackbar_bookmark_deleted)
    val undoActionLabel = stringResource(R.string.action_undo)

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
                message = bookmarkDeletedMessage,
                actionLabel = undoActionLabel,
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
                    navHostController.navigate(BookmarkDetailRoute(event.bookmarkId, event.showOriginal))
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
                    HorizontalDivider()
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
                    NavigationDrawerItem(
                        label = { Text(
                            style = Typography.labelLarge,
                            text = stringResource(id = R.string.labels)
                        ) },
                        icon = { Icon(Icons.Outlined.Label, contentDescription = null) },
                        badge = {
                            if (labelsWithCounts.value.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        text = labelsWithCounts.value.size.toString()
                                    )
                                }
                            }
                        },
                        selected = filterState.value.viewingLabelsList || filterState.value.label != null,
                        onClick = {
                            viewModel.onClickLabelsView()
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
                    filterState.value.viewingLabelsList -> stringResource(id = R.string.select_label)
                    filterState.value.label != null -> "Label..."
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
                        if (!isSearchActive.value && !filterState.value.viewingLabelsList) {
                            // Sort button with dropdown
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.displayName,
                                                    fontWeight = if (option == sortOption.value) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                viewModel.onSortOptionSelected(option)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Layout button with dropdown
                            Box {
                                IconButton(onClick = { showLayoutMenu = true }) {
                                    Icon(Icons.Filled.GridView, contentDescription = "Layout")
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

                            // Search button
                            IconButton(onClick = { viewModel.onSearchActiveChange(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
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
                // Subheader with label name, edit icon, and delete icon when filtering by label
                if (filterState.value.label != null) {
                    val labelDeletedMessageFormat = stringResource(R.string.label_deleted)
                    val currentLabel = filterState.value.label!!

                    // Focus on edit field when entering edit mode and set cursor at end
                    LaunchedEffect(isEditingLabel) {
                        if (isEditingLabel) {
                            labelEditFocusRequester.requestFocus()
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditingLabel) {
                                TextField(
                                    value = editedLabelName,
                                    onValueChange = { editedLabelName = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(labelEditFocusRequester),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (editedLabelName.isNotBlank() && editedLabelName != currentLabel) {
                                                viewModel.onRenameLabel(currentLabel, editedLabelName)
                                            }
                                            isEditingLabel = false
                                        }
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        // Save the edited label
                                        if (editedLabelName.isNotBlank() && editedLabelName != currentLabel) {
                                            viewModel.onRenameLabel(currentLabel, editedLabelName)
                                        }
                                        isEditingLabel = false
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        currentLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editedLabelName = currentLabel
                                        isEditingLabel = true
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(id = R.string.edit_label),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        // Cancel any existing delete operation
                                        deleteLabelJob?.cancel()

                                        // Set pending delete
                                        pendingDeleteLabel = currentLabel

                                        // Show snackbar with undo option
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = labelDeletedMessageFormat.format(currentLabel),
                                                actionLabel = undoActionLabel,
                                                duration = SnackbarDuration.Long
                                            )

                                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                // User clicked undo, cancel the deletion
                                                deleteLabelJob?.cancel()
                                                pendingDeleteLabel = null
                                            }
                                        }

                                        // Schedule the actual deletion after 10 seconds
                                        deleteLabelJob = scope.launch {
                                            kotlinx.coroutines.delay(10000)
                                            if (pendingDeleteLabel == currentLabel) {
                                                viewModel.onDeleteLabel(currentLabel)
                                                pendingDeleteLabel = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(id = R.string.delete_label),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.onPullToRefresh() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Show labels list if viewing labels, otherwise show bookmarks list
                    if (filterState.value.viewingLabelsList) {
                    LabelsListView(
                        labels = labelsWithCounts.value,
                        onLabelSelected = { label ->
                            viewModel.onClickLabel(label)
                        }
                    )
                } else {
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
                                onClickShareBookmark = onClickShareBookmark,
                                onClickLabel = { label -> viewModel.onClickLabel(label) },
                                onClickOpenUrl = onClickOpenUrl
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
                        labels = createBookmarkUiState.labels,
                        onTitleChange = { viewModel.updateCreateBookmarkTitle(it) },
                        onUrlChange = { viewModel.updateCreateBookmarkUrl(it) },
                        onLabelsChange = { viewModel.updateCreateBookmarkLabels(it) },
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

        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBookmarkDialog(
    onDismiss: () -> Unit,
    title: String,
    url: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onCreateBookmark: () -> Unit
) {
    var newLabelInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.add_new_bookmark)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // URL field
                OutlinedTextField(
                    value = url,
                    onValueChange = { onUrlChange(it) },
                    isError = urlError != null,
                    label = { Text(stringResource(id = R.string.url)) },
                    supportingText = {
                        urlError?.let {
                            Text(text = stringResource(it))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it) },
                    label = { Text(stringResource(id = R.string.title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Labels Section
                CreateBookmarkLabelsSection(
                    labels = labels,
                    newLabelInput = newLabelInput,
                    onNewLabelChange = { newLabelInput = it },
                    onAddLabel = {
                        if (newLabelInput.isNotBlank()) {
                            // Split on commas and trim each label
                            val newLabels = newLabelInput.split(',')
                                .map { it.trim() }
                                .filter { it.isNotBlank() && !labels.contains(it) }

                            if (newLabels.isNotEmpty()) {
                                onLabelsChange(labels + newLabels)
                            }
                            newLabelInput = ""
                            keyboardController?.hide()
                        }
                    },
                    onRemoveLabel = { label ->
                        onLabelsChange(labels.filter { it != label })
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Process any pending label input before creating
                    if (newLabelInput.isNotBlank()) {
                        val newLabels = newLabelInput.split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() && !labels.contains(it) }

                        if (newLabels.isNotEmpty()) {
                            onLabelsChange(labels + newLabels)
                        }
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBookmarkLabelsSection(
    labels: List<String>,
    newLabelInput: String,
    onNewLabelChange: (String) -> Unit,
    onAddLabel: () -> Unit,
    onRemoveLabel: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_labels),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Existing labels
        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEach { label ->
                    LabelChip(
                        label = label,
                        onRemove = { onRemoveLabel(label) }
                    )
                }
            }
        }

        // Input field for new label
        OutlinedTextField(
            value = newLabelInput,
            onValueChange = onNewLabelChange,
            placeholder = { Text(stringResource(R.string.detail_label_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onAddLabel() }
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LabelChip(
    label: String,
    onRemove: () -> Unit = {}
) {
    Card(
        modifier = Modifier.padding(4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove label",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
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
fun LabelsListView(
    modifier: Modifier = Modifier,
    labels: Map<String, Int>,
    onLabelSelected: (String) -> Unit
) {
    if (labels.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.list_view_empty_nothing_to_see),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth()
        ) {
            item {
                Text(
                    text = stringResource(R.string.labels_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(
                items = labels.entries.sortedBy { it.key }.toList(),
                key = { it.key }
            ) { (label, count) ->
                NavigationDrawerItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.medium
                        ),
                    label = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label)
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(count.toString())
                            }
                        }
                    },
                    selected = false,
                    onClick = {
                        onLabelSelected(label)
                    }
                )
            }
        }
    }
}

@Composable
fun BookmarkListView(
    modifier: Modifier = Modifier,
    layoutMode: LayoutMode = LayoutMode.GRID,
    bookmarks: List<BookmarkListItem>,
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {}
) {
    LazyColumn(modifier = modifier) {
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
                    onClickOpenUrl = onClickOpenUrl
                )
                LayoutMode.COMPACT -> BookmarkCompactCard(
                    bookmark = bookmark,
                    onClickCard = onClickBookmark,
                    onClickDelete = onClickDelete,
                    onClickArchive = onClickArchive,
                    onClickFavorite = onClickFavorite,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickLabel = onClickLabel,
                    onClickOpenUrl = onClickOpenUrl
                )
                LayoutMode.MOSAIC -> BookmarkMosaicCard(
                    bookmark = bookmark,
                    onClickCard = onClickBookmark,
                    onClickDelete = onClickDelete,
                    onClickArchive = onClickArchive,
                    onClickFavorite = onClickFavorite,
                    onClickShareBookmark = onClickShareBookmark,
                    onClickLabel = onClickLabel,
                    onClickOpenUrl = onClickOpenUrl
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
