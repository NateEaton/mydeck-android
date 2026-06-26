package com.mydeck.app.ui.list




import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.mydeck.app.domain.model.SwipeAction
import com.mydeck.app.domain.model.SwipeConfig
import com.mydeck.app.ui.components.FilterBar
import com.mydeck.app.ui.components.FilterBottomSheet
import com.mydeck.app.ui.components.ShareBookmarkChooser
import com.mydeck.app.ui.components.VerticalScrollbar
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.domain.model.OpenWebPagesIn
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import timber.log.Timber

private const val PendingDeleteFromDetailKey = "pending_delete_bookmark_id"
private const val PendingDeleteSnackbarTitleMaxChars = 18

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    navHostController: NavHostController,
    viewModel: BookmarkListViewModel,
    drawerState: DrawerState,
    showNavigationIcon: Boolean = true,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val createBookmarkUiState = viewModel.createBookmarkUiState.collectAsStateWithLifecycle().value

    // Collect filter states
    val drawerPreset = viewModel.drawerPreset.collectAsState()
    val filterFormState = viewModel.filterFormState.collectAsState()
    val activeLabel = viewModel.activeLabel.collectAsState()
    val isFilterSheetOpen = viewModel.isFilterSheetOpen.collectAsState()
    val layoutMode = viewModel.layoutMode.collectAsState()
    val sortOption = viewModel.sortOption.collectAsState()
    val labelsWithCounts = viewModel.labelsWithCounts.collectAsState()
    val isLabelsSheetOpen = viewModel.isLabelsSheetOpen.collectAsState()
    val pendingDeletionBookmarkIds = viewModel.pendingDeletionBookmarkIds.collectAsState()
    val pendingBatchDeletionBookmarkIds = viewModel.pendingBatchDeletionBookmarkIds.collectAsState()
    val multiSelectState = viewModel.multiSelectState.collectAsState()
    val multiSelectTargets = viewModel.multiSelectTargets.collectAsState()
    val offlineReadingEnabled = viewModel.offlineReadingEnabled.collectAsState()
    val showAddLabelsPicker = viewModel.showAddLabelsPicker.collectAsState()
    val editLabelsTarget = viewModel.editLabelsTarget.collectAsState()
    val swipeConfig = viewModel.swipeConfig.collectAsState()
    val showCompactFavicons = viewModel.showCompactFavicons.collectAsState()
    val showAddBookmarkFab = viewModel.showAddBookmarkFab.collectAsState()
    val openWebPagesIn = viewModel.openWebPagesIn.collectAsState()

    var showSelectionOverflowMenu by remember { mutableStateOf(false) }
    var showRenameLabelDialog by remember { mutableStateOf(false) }
    var showDeleteLabelDialog by remember { mutableStateOf(false) }
    var scrollToTopTrigger by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val undoActionLabel = stringResource(R.string.action_undo)

    val pullToRefreshState = rememberPullToRefreshState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isUserRefreshing by viewModel.isUserRefreshing.collectAsState()
    val syncFraction by viewModel.syncFraction.collectAsState()

    val isLabelMode = activeLabel.value != null
    val isMultiSelectMode = multiSelectState.value.active
    val dismissPendingDeleteSnackbar: () -> Unit = {
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    fun pendingDeleteSnackbarMessage(bookmarkId: String): String {
        val titleSnippet = formatPendingDeleteSnackbarTitleSnippet(
            title = (uiState as? BookmarkListViewModel.UiState.Success)
                ?.bookmarks
                ?.firstOrNull { it.id == bookmarkId }
                ?.title
                .orEmpty()
        )
        return if (titleSnippet.isNotEmpty()) {
            context.getString(R.string.bookmark_delete_pending_named, titleSnippet)
        } else {
            context.getString(R.string.bookmark_delete_pending)
        }
    }

    fun pendingDeleteSnackbarVisuals(bookmarkId: String): PendingDeleteSnackbarVisuals? {
        val titleSnippet = formatPendingDeleteSnackbarTitleSnippet(
            title = (uiState as? BookmarkListViewModel.UiState.Success)
                ?.bookmarks
                ?.firstOrNull { it.id == bookmarkId }
                ?.title
                .orEmpty()
        )
        if (titleSnippet.isEmpty()) {
            return null
        }

        return PendingDeleteSnackbarVisuals(
            message = context.getString(R.string.bookmark_delete_pending_named, titleSnippet),
            prefixText = context.getString(R.string.bookmark_delete_pending),
            titleSnippet = titleSnippet,
            actionLabel = undoActionLabel,
            duration = SnackbarDuration.Indefinite
        )
    }

    fun stageDeleteWithSnackbar(bookmarkId: String, withHaptic: Boolean = false) {
        if (withHaptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        val pendingDeleteMessage = pendingDeleteSnackbarMessage(bookmarkId)
        val pendingDeleteVisuals = pendingDeleteSnackbarVisuals(bookmarkId)
        viewModel.onDeleteBookmark(bookmarkId)
        scope.launch {
            val result = if (pendingDeleteVisuals != null) {
                snackbarHostState.showSnackbar(pendingDeleteVisuals)
            } else {
                snackbarHostState.showSnackbar(
                    message = pendingDeleteMessage,
                    actionLabel = undoActionLabel,
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.onCancelDeleteBookmark(bookmarkId)
            } else {
                viewModel.onConfirmDeleteBookmark(bookmarkId)
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
    val onClickOpenUrl: (String) -> Unit = { bookmarkId ->
        dismissPendingDeleteSnackbar()
        viewModel.onClickBookmarkOpenOriginal(bookmarkId)
    }
    val onClickOpenInBrowser: (String) -> Unit = { url ->
        viewModel.onClickOpenInBrowser(url)
    }

    val clipboardManager = LocalClipboardManager.current

    val onClickCopyLink: (String) -> Unit = { url ->
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(url))
    }
    val onClickShareLink: (String, String) -> Unit = { title, url ->
        viewModel.onClickShareBookmark(title, url)
    }
    val onClickCopyImage: (String) -> Unit = { imageUrl ->
        if (imageUrl.isNotBlank()) {
            scope.launch {
                try {
                    val imageUri = withContext(Dispatchers.IO) {
                        val request = coil3.request.ImageRequest.Builder(context)
                            .data(imageUrl).build()
                        val result = context.imageLoader.execute(request) as? coil3.request.SuccessResult
                        val rawBitmap = (result?.image as? coil3.BitmapImage)?.bitmap
                            ?: throw Exception("no bitmap")
                        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                                && rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE)
                            rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else rawBitmap
                        val cacheDir = java.io.File(context.cacheDir, "images").also { it.mkdirs() }
                        val file = java.io.File(cacheDir, "copy_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                        }
                        androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                    }
                    val androidClipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    androidClipboard.setPrimaryClip(
                        android.content.ClipData.newUri(context.contentResolver, "image", imageUri)
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to copy image to clipboard")
                }
            }
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
                    "thumbnail.jpg"
                )
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
        }
    }
    val onClickDownloadLink: (String, String) -> Unit = { url, title ->
        if (url.isNotBlank() && url.startsWith("http")) {
            val fileName = title.ifBlank { url.substringAfterLast('/').ifBlank { "download" } }
                .take(100).replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".html"
            val request = android.app.DownloadManager.Request(
                android.net.Uri.parse(url)
            ).apply {
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
        }
    }
    val onClickShareImage: (String) -> Unit = { imageUrl ->
        if (imageUrl.isNotBlank()) {
            scope.launch {
                try {
                    val imageFile = withContext(Dispatchers.IO) {
                        val request = coil3.request.ImageRequest.Builder(context)
                            .data(imageUrl).build()
                        val result = context.imageLoader.execute(request) as? coil3.request.SuccessResult
                        val rawBitmap = (result?.image as? coil3.BitmapImage)?.bitmap
                            ?: throw Exception("no bitmap")
                        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                                && rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE)
                            rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else rawBitmap
                        val cacheDir = java.io.File(context.cacheDir, "images").also { it.mkdirs() }
                        val file = java.io.File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                        }
                        file
                    }
                    val imageUri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", imageFile
                    )
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                        type = "image/jpeg"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to share image")
                }
            }
        }
    }
    val onClickCopyLinkText: (String) -> Unit = { text ->
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
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

    // Scroll to top when initial sync completes so the list starts at item 0
    LaunchedEffect(Unit) {
        var wasLoading = false
        snapshotFlow { isInitialLoading }.collect { loading ->
            if (wasLoading && !loading) {
                scrollToTopTrigger++
            }
            wasLoading = loading
        }
    }

    // Constraint feedback snackbar (fires once after app-open sync if content sync is blocked)
    LaunchedEffect(Unit) {
        viewModel.constraintSnackbarEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(
                message = context.getString(messageRes),
                duration = SnackbarDuration.Short
            )
        }
    }

    val resources = context.resources
    LaunchedEffect(Unit) {
        viewModel.batchActionSnackbarEvent.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            // Pin/Unpin are informational only (no Undo; selection mode stays open). Pin carries two
            // counts that sum to the selection: items now pinned + items with no offline content.
            if (event is BatchActionSnackbarEvent.PinnedOffline) {
                val message = if (event.skippedCount == 0) {
                    resources.getString(R.string.multi_select_pinned_offline, event.pinnedCount)
                } else {
                    resources.getString(
                        R.string.multi_select_pinned_with_skipped,
                        event.pinnedCount,
                        event.skippedCount
                    )
                }
                snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
                return@collect
            }
            if (event is BatchActionSnackbarEvent.Unpinned) {
                snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.multi_select_unpinned, event.count),
                    duration = SnackbarDuration.Long
                )
                return@collect
            }
            val stringRes = when (event) {
                is BatchActionSnackbarEvent.FavoritesAdded -> R.string.multi_select_set_as_favorite
                is BatchActionSnackbarEvent.FavoritesRemoved -> R.string.multi_select_unset_as_favorite
                is BatchActionSnackbarEvent.Archived -> R.string.multi_select_set_as_archived
                is BatchActionSnackbarEvent.Unarchived -> R.string.multi_select_unset_as_archived
                is BatchActionSnackbarEvent.LabelsAdded -> R.string.multi_select_labels_added
                is BatchActionSnackbarEvent.Deleted -> R.string.multi_select_deleted_count
                is BatchActionSnackbarEvent.PinnedOffline -> return@collect // handled above
                is BatchActionSnackbarEvent.Unpinned -> return@collect // handled above
            }
            // Delete is destructive and staged, so it stays until the user acts or interacts
            // elsewhere (matching single-item delete); favorite/archive are already applied.
            val duration = if (event is BatchActionSnackbarEvent.Deleted) {
                SnackbarDuration.Indefinite
            } else {
                SnackbarDuration.Long
            }
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(stringRes, event.count),
                actionLabel = undoActionLabel,
                duration = duration
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.onUndoBatchAction(event)
            } else {
                viewModel.onConfirmBatchAction(event)
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

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onExitMultiSelectMode()
        }
    }

    // Determine the current view title based on drawer preset
    val currentPresetTitle = when (drawerPreset.value) {
        DrawerPreset.MY_LIST -> stringResource(id = R.string.my_list)
        DrawerPreset.ARCHIVE -> stringResource(id = R.string.archive)
        DrawerPreset.FAVORITES -> stringResource(id = R.string.favorites)
        DrawerPreset.ARTICLES -> stringResource(id = R.string.articles)
        DrawerPreset.VIDEOS -> stringResource(id = R.string.videos)
        DrawerPreset.PICTURES -> stringResource(id = R.string.pictures)
        else -> stringResource(id = R.string.my_list)
    }
    val bookmarkCount = (uiState as? BookmarkListViewModel.UiState.Success)?.bookmarks?.size
    val currentViewTitle = if (!isLabelMode && filterFormState.value.differsFromPreset(drawerPreset.value)) {
        val base = stringResource(id = R.string.filtered_list)
        if (bookmarkCount != null) "$base ($bookmarkCount)" else base
    } else {
        currentPresetTitle
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { snackbarData ->
                val pendingDeleteVisuals = snackbarData.visuals as? PendingDeleteSnackbarVisuals
                if (pendingDeleteVisuals != null) {
                    PendingDeleteSnackbar(
                        snackbarData = snackbarData,
                        visuals = pendingDeleteVisuals
                    )
                } else {
                    Snackbar(snackbarData = snackbarData)
                }
            }
        },
        topBar = {
            Column {
            TopAppBar(
                title = {
                    if (isMultiSelectMode) {
                        Text(
                            text = stringResource(
                                R.string.selected_bookmark_count,
                                multiSelectState.value.selectedCount
                            )
                        )
                    } else if (isLabelMode) {
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
                    if (isMultiSelectMode) {
                        IconButton(
                            onClick = {
                                dismissPendingDeleteSnackbar()
                                viewModel.onExitMultiSelectMode()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_exit_selection_mode)
                            )
                        }
                    } else if (showNavigationIcon) {
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
                    if (isMultiSelectMode) {
                        val targets = multiSelectTargets.value
                        if (multiSelectState.value.hasSelection) {
                            // Archive slot: shows Unarchive when all selected are already
                            // archived (one-tap reversal); otherwise shows Archive.
                            val archiveBarIsRemove = targets.selectedAllArchived
                            IconButton(
                                onClick = {
                                    dismissPendingDeleteSnackbar()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (archiveBarIsRemove) {
                                        viewModel.onUnarchiveSelectedBookmarks()
                                    } else {
                                        viewModel.onArchiveSelectedBookmarks()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (archiveBarIsRemove) Icons.Outlined.Inventory2 else Icons.Filled.Inventory2,
                                    contentDescription = stringResource(
                                        if (archiveBarIsRemove) R.string.action_remove_from_archive
                                        else R.string.action_add_to_archive
                                    )
                                )
                            }
                            // Favorite slot: shows Remove-favorite when all selected are already
                            // favorited (one-tap reversal); otherwise shows Add-favorite.
                            val favoriteBarIsRemove = targets.selectedAllFavorited
                            IconButton(
                                onClick = {
                                    dismissPendingDeleteSnackbar()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (favoriteBarIsRemove) {
                                        viewModel.onUnfavoriteSelectedBookmarks()
                                    } else {
                                        viewModel.onFavoriteSelectedBookmarks()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (favoriteBarIsRemove) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite,
                                    contentDescription = stringResource(
                                        if (favoriteBarIsRemove) R.string.action_remove_from_favorites
                                        else R.string.action_add_to_favorites
                                    )
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showSelectionOverflowMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionOverflowMenu,
                                onDismissRequest = { showSelectionOverflowMenu = false }
                            ) {
                                if (multiSelectState.value.hasSelection) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null
                                            )
                                        },
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        onClick = {
                                            showSelectionOverflowMenu = false
                                            dismissPendingDeleteSnackbar()
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onDeleteSelectedBookmarks()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                                contentDescription = null
                                            )
                                        },
                                        text = { Text(stringResource(R.string.add_labels)) },
                                        onClick = {
                                            showSelectionOverflowMenu = false
                                            dismissPendingDeleteSnackbar()
                                            viewModel.onAddLabelsToSelection()
                                        }
                                    )
                                    // Pin/Unpin offline — hidden entirely when offline storage is
                                    // disabled (offline-pinning spec §6). Contextual: Unpin when all
                                    // selected are already pinned, otherwise Pin.
                                    if (offlineReadingEnabled.value) {
                                        val allPinned = targets.selectedAllPinned
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = null
                                                )
                                            },
                                            text = {
                                                Text(stringResource(
                                                    if (allPinned) R.string.action_unpin_offline
                                                    else R.string.action_pin_offline
                                                ))
                                            },
                                            onClick = {
                                                showSelectionOverflowMenu = false
                                                dismissPendingDeleteSnackbar()
                                                if (allPinned) viewModel.onUnpinSelection()
                                                else viewModel.onPinSelection()
                                            }
                                        )
                                    }
                                }
                                val allSelected = targets.allVisibleSelected
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(stringResource(
                                            if (allSelected) R.string.action_deselect_all
                                            else R.string.action_select_all
                                        ))
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        dismissPendingDeleteSnackbar()
                                        viewModel.onToggleSelectAllBookmarks()
                                    }
                                )
                            }
                        }
                    } else {
                    BookmarkListBarActions(
                        isLabelMode = isLabelMode,
                        layoutMode = layoutMode.value,
                        sortOption = sortOption.value,
                        onLayoutModeSelected = { viewModel.onLayoutModeSelected(it) },
                        onSortOptionSelected = { viewModel.onSortOptionSelected(it) },
                        onDismissPendingDelete = dismissPendingDeleteSnackbar,
                        onOpenFilterSheet = { viewModel.onOpenFilterSheet() },
                        onEnterMultiSelectMode = { viewModel.onEnterMultiSelectMode() },
                        onRequestRenameLabel = { showRenameLabelDialog = true },
                        onRequestDeleteLabel = { showDeleteLabelDialog = true },
                    )
                    }
                }
            )
            val fraction = syncFraction
            if (isInitialLoading && fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode && showAddBookmarkFab.value) {
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
        }
    ) { padding ->
        val allPendingDeletionIds = pendingDeletionBookmarkIds.value.toSet() + pendingBatchDeletionBookmarkIds.value
        val hasPendingDeletion = allPendingDeletionIds.isNotEmpty()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                // Any touch in content area confirms a pending delete (snackbar's
                // Undo lives in its own Scaffold slot and is excluded). Initial-pass
                // observer that never consumes — children still receive the event.
                .pointerInput(hasPendingDeletion) {
                    if (!hasPendingDeletion) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        dismissPendingDeleteSnackbar()
                    }
                }
        ) {
            // FilterBar: visible when filters are active beyond preset defaults, not in label mode
            if (!isLabelMode && !isMultiSelectMode) {
                FilterBar(
                    filterFormState = filterFormState.value,
                    drawerPreset = drawerPreset.value,
                    onFilterChanged = { viewModel.onApplyFilter(it) },
                    onOpenFilterSheet = { viewModel.onOpenFilterSheet() }
                )
            }

            PullToRefreshBox(
                isRefreshing = isUserRefreshing,
                onRefresh = { viewModel.onPullToRefresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (uiState) {
                    is BookmarkListViewModel.UiState.Loading -> {
                        // Intentionally blank — avoids flash of empty state while Room emits first value
                        Box(modifier = Modifier.fillMaxSize())
                    }
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
                    CompositionLocalProvider(
                        LocalOpenWebPageExternally provides
                            (openWebPagesIn.value == OpenWebPagesIn.EXTERNAL_BROWSER)
                    ) {
                    BookmarkListView(
                                filterKey = Pair(filterFormState.value, activeLabel.value),
                                scrollToTopTrigger = scrollToTopTrigger,
                                layoutMode = layoutMode.value,
                                bookmarks = uiState.bookmarks,
                                pendingDeletionBookmarkIds = allPendingDeletionIds,
                                isMultiSelectMode = isMultiSelectMode,
                                selectedBookmarkIds = multiSelectState.value.selectedIds,
                                swipeConfig = swipeConfig.value,
                                showCompactFavicons = showCompactFavicons.value,
                                onClickBookmark = onClickBookmark,
                                onToggleBookmarkSelection = { viewModel.onToggleBookmarkSelected(it) },
                                onClickDelete = onClickDelete,
                                onClickArchive = onClickArchive,
                                onClickFavorite = onClickFavorite,
                                onClickLabel = { label ->
                                    dismissPendingDeleteSnackbar()
                                    viewModel.onClickLabel(label)
                                },
                                onClickEditLabels = { bookmarkId ->
                                    dismissPendingDeleteSnackbar()
                                    viewModel.onOpenEditLabels(bookmarkId)
                                },
                                onClickOpenUrl = onClickOpenUrl,
                                onClickOpenInBrowser = onClickOpenInBrowser,
                                onClickCopyLink = onClickCopyLink,
                                onClickCopyLinkText = onClickCopyLinkText,
                                onClickShareLink = onClickShareLink,
                        onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
                        onClickCopyImage = onClickCopyImage,
                        onClickDownloadLink = onClickDownloadLink,
                        onClickDownloadImage = onClickDownloadImage,
                        onClickShareImage = onClickShareImage,
                        onUserInteraction = dismissPendingDeleteSnackbar,
                    )
                    }
                }
                        // Consumes a shareIntent and creates the corresponding share dialog
                        ShareBookmarkChooser(
                            context = LocalContext.current,
                            intent = viewModel.shareIntent.collectAsStateWithLifecycle().value,
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
        LabelPickerBottomSheet(
            labels = labelsWithCounts.value,
            mode = LabelPickerMode.SingleSelect(
                selectedLabel = activeLabel.value,
                onLabelSelected = { label -> viewModel.onClickLabel(label) },
                onRenameLabel = { oldLabel, newLabel -> viewModel.onRenameLabel(oldLabel, newLabel) },
                onDeleteLabel = { label -> viewModel.onDeleteLabel(label) }
            ),
            onDismiss = { viewModel.onCloseLabelsSheet() }
        )
    }

    // Multi-select: Add labels picker (additive union applied to the captured selection).
    // The mode is remembered so it stays referentially stable across this screen's frequent
    // recompositions; otherwise the sheet's remember(mode) would reset the in-progress selection.
    if (showAddLabelsPicker.value) {
        val addLabelsMode = remember {
            LabelPickerMode.MultiSelect(
                initialSelection = emptySet(),
                onDone = { chosen -> viewModel.onLabelsPicked(chosen) }
            )
        }
        LabelPickerBottomSheet(
            labels = labelsWithCounts.value,
            mode = addLabelsMode,
            onDismiss = { viewModel.onDismissAddLabelsPicker() }
        )
    }

    // Single-bookmark quick label edit (from the card label button). Replaces the bookmark's full
    // label set, pre-populated with its current labels. The target's own labels are merged into the
    // options so a label not yet in the global list still appears as selected.
    editLabelsTarget.value?.let { target ->
        val labelOptions = remember(labelsWithCounts.value, target) {
            labelsWithCounts.value + target.currentLabels.associateWith { label ->
                labelsWithCounts.value[label] ?: 0
            }
        }
        // The mode must be referentially stable across recompositions, otherwise the picker's
        // remember(mode)-keyed selection state resets to initialSelection on every recomposition
        // (unchecks pop back, additions are lost). Key on target so it refreshes per bookmark.
        val editLabelsMode = remember(target) {
            LabelPickerMode.MultiSelect(
                initialSelection = target.currentLabels.toSet(),
                onDone = { selected ->
                    val updated = target.currentLabels.filter { it in selected } +
                        selected.filterNot { it in target.currentLabels }.sorted()
                    viewModel.onSetLabelsForBookmark(target.bookmarkId, updated)
                }
            )
        }
        LabelPickerBottomSheet(
            labels = labelOptions,
            mode = editLabelsMode,
            onDismiss = { viewModel.onDismissEditLabels() }
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

    // Constraint override dialog for user-initiated refresh
    val constraintOverrideBodyRes by viewModel.constraintOverrideBodyRes.collectAsState()
    constraintOverrideBodyRes?.let { bodyRes ->
        AlertDialog(
            onDismissRequest = { viewModel.onConstraintOverrideCancelled() },
            title = { Text(stringResource(R.string.sync_constraint_override_title)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                Button(onClick = { viewModel.onConstraintOverrideConfirmed() }) {
                    Text(stringResource(R.string.sync_constraint_override_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onConstraintOverrideCancelled() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * The normal / label-mode top-app-bar actions row (Layout, Sort, Overflow).
 * Extracted as a standalone composable so it can be tested in isolation without
 * wiring up a full BookmarkListViewModel.
 */
@Composable
internal fun BookmarkListBarActions(
    isLabelMode: Boolean,
    layoutMode: LayoutMode,
    sortOption: SortOption,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onDismissPendingDelete: () -> Unit = {},
    onOpenFilterSheet: () -> Unit,
    onEnterMultiSelectMode: () -> Unit,
    onRequestRenameLabel: () -> Unit,
    onRequestDeleteLabel: () -> Unit,
) {
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Layout button with dropdown — icon reflects current mode
    Box {
        val currentLayoutIcon = when (layoutMode) {
            LayoutMode.GRID -> Icons.Filled.Apps
            LayoutMode.COMPACT -> Icons.AutoMirrored.Filled.List
            LayoutMode.MOSAIC -> Icons.Filled.GridView
        }
        IconButton(onClick = {
            onDismissPendingDelete()
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
                            fontWeight = if (mode == layoutMode) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onLayoutModeSelected(mode)
                        showLayoutMenu = false
                    }
                )
            }
        }
    }

    // Sort button with dropdown — one row per category, arrow shows direction of active sort
    Box {
        IconButton(onClick = {
            onDismissPendingDelete()
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
                Triple("Site name", SortOption.SITE_A_TO_Z, SortOption.SITE_Z_TO_A),
                Triple("Duration", SortOption.DURATION_LONGEST, SortOption.DURATION_SHORTEST)
            )
            sortGroups.forEach { (label, firstOption, secondOption) ->
                val isFirstSelected = sortOption == firstOption
                val isSecondSelected = sortOption == secondOption
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
                        onSortOptionSelected(newOption)
                        showSortMenu = false
                    }
                )
            }
        }
    }

    // Overflow menu — always present; same bar shape in normal and label modes.
    // Label mode lists Rename/Delete label (label-scoped) above a divider, then
    // Select bookmarks. Normal mode lists Filter, then Select bookmarks (no Filter
    // in label mode — the label itself is the filter).
    Box {
        IconButton(onClick = {
            onDismissPendingDelete()
            showOverflowMenu = true
        }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { showOverflowMenu = false }
        ) {
            if (isLabelMode) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.rename_label)) },
                    onClick = {
                        onRequestRenameLabel()
                        showOverflowMenu = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.delete_label)) },
                    onClick = {
                        onRequestDeleteLabel()
                        showOverflowMenu = false
                    }
                )
                HorizontalDivider()
            } else {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.FilterList, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.filter_bookmarks)) },
                    onClick = {
                        showOverflowMenu = false
                        onDismissPendingDelete()
                        onOpenFilterSheet()
                    }
                )
            }
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null)
                },
                text = { Text(stringResource(R.string.action_select_bookmarks)) },
                onClick = {
                    showOverflowMenu = false
                    showLayoutMenu = false
                    showSortMenu = false
                    onDismissPendingDelete()
                    onEnterMultiSelectMode()
                }
            )
        }
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
    pendingDeletionBookmarkIds: Set<String> = emptySet(),
    isMultiSelectMode: Boolean = false,
    selectedBookmarkIds: Set<String> = emptySet(),
    swipeConfig: SwipeConfig = SwipeConfig.Default,
    showCompactFavicons: Boolean = true,
    isMultiColumn: Boolean = LocalIsWideLayout.current,
    onClickBookmark: (String) -> Unit,
    onToggleBookmarkSelection: (String) -> Unit = {},
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickEditLabels: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
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
                itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.id }) { index, bookmark ->
                    val isPendingDeletion = bookmark.id in pendingDeletionBookmarkIds
                    val confirmDelete: (String) -> Unit = { _ -> onUserInteraction() }
                    val noop: (String) -> Unit = {}
                    val noop2: (String, Boolean) -> Unit = { _, _ -> }
                    val noop2s: (String, String) -> Unit = { _, _ -> }
                    val noopShare: (String, String) -> Unit = { _, _ -> }
                    val isSelected = bookmark.id in selectedBookmarkIds
                    val selectionModeForCard = isMultiSelectMode && !isPendingDeletion
                    val selectBookmark: (String) -> Unit = { onToggleBookmarkSelection(it) }
                    val cardClick = when {
                        isPendingDeletion -> confirmDelete
                        selectionModeForCard -> selectBookmark
                        else -> onClickBookmark
                    }
                    val actionsDisabled = isPendingDeletion || selectionModeForCard
                    SwipeWrappedBookmark(
                        bookmark = bookmark,
                        isPendingDeletion = isPendingDeletion,
                        swipeConfig = swipeConfig.copy(enabled = false),
                        onClickArchive = onClickArchive,
                        onClickDelete = onClickDelete,
                        onClickFavorite = onClickFavorite,
                        onPendingDeletionTap = onUserInteraction,
                        modifier = Modifier.animateItem(),
                    ) {
                        when (layoutMode) {
                            LayoutMode.GRID -> BookmarkGridCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                isInGrid = true,
                                index = index + 1,
                            )
                            LayoutMode.COMPACT -> BookmarkCompactCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                showFavicon = showCompactFavicons,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                index = index + 1,
                            )
                            LayoutMode.MOSAIC -> BookmarkMosaicCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                index = index + 1,
                            )
                        }
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
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.id }) { index, bookmark ->
                    val isPendingDeletion = bookmark.id in pendingDeletionBookmarkIds
                    val confirmDelete: (String) -> Unit = { _ -> onUserInteraction() }
                    val noop: (String) -> Unit = {}
                    val noop2: (String, Boolean) -> Unit = { _, _ -> }
                    val noop2s: (String, String) -> Unit = { _, _ -> }
                    val noopShare: (String, String) -> Unit = { _, _ -> }
                    val isSelected = bookmark.id in selectedBookmarkIds
                    val selectionModeForCard = isMultiSelectMode && !isPendingDeletion
                    val selectBookmark: (String) -> Unit = { onToggleBookmarkSelection(it) }
                    val cardClick = when {
                        isPendingDeletion -> confirmDelete
                        selectionModeForCard -> selectBookmark
                        else -> onClickBookmark
                    }
                    val actionsDisabled = isPendingDeletion || selectionModeForCard
                    SwipeWrappedBookmark(
                        bookmark = bookmark,
                        isPendingDeletion = isPendingDeletion,
                        swipeConfig = if (isMultiSelectMode) swipeConfig.copy(enabled = false) else swipeConfig,
                        onClickArchive = onClickArchive,
                        onClickDelete = onClickDelete,
                        onClickFavorite = onClickFavorite,
                        onPendingDeletionTap = onUserInteraction,
                        modifier = Modifier.animateItem(),
                    ) {
                        when (layoutMode) {
                            LayoutMode.GRID -> BookmarkGridCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                useMobilePortraitLayout = useMobilePortraitGridLayout,
                                index = index + 1,
                            )
                            LayoutMode.COMPACT -> BookmarkCompactCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                showFavicon = showCompactFavicons,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                index = index + 1,
                            )
                            LayoutMode.MOSAIC -> BookmarkMosaicCard(
                                bookmark = bookmark,
                                onClickCard = cardClick,
                                onClickDelete = if (actionsDisabled) noop else onClickDelete,
                                onClickArchive = if (actionsDisabled) noop2 else onClickArchive,
                                onClickFavorite = if (actionsDisabled) noop2 else onClickFavorite,
                                onClickLabel = if (actionsDisabled) noop else onClickLabel,
                                onClickEditLabels = if (actionsDisabled) noop else onClickEditLabels,
                                onClickOpenUrl = if (actionsDisabled) noop else onClickOpenUrl,
                                onClickOpenInBrowser = if (actionsDisabled) noop else onClickOpenInBrowser,
                                onClickCopyLink = if (actionsDisabled) noop else onClickCopyLink,
                                onClickCopyLinkText = if (actionsDisabled) noop else onClickCopyLinkText,
                                onClickShareLink = if (actionsDisabled) noopShare else onClickShareLink,
                                onClickOpenInBrowserFromMenu = if (actionsDisabled) noop else onClickOpenInBrowserFromMenu,
                                onClickCopyImage = if (actionsDisabled) noop else onClickCopyImage,
                                onClickDownloadLink = if (actionsDisabled) noop2s else onClickDownloadLink,
                                onClickDownloadImage = if (actionsDisabled) noop else onClickDownloadImage,
                                onClickShareImage = if (actionsDisabled) noop else onClickShareImage,
                                isSelectionMode = selectionModeForCard,
                                isSelected = isSelected,
                                onToggleSelection = onToggleBookmarkSelection,
                                index = index + 1,
                            )
                        }
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

@Composable
private fun swipeActionA11yLabel(action: SwipeAction): String = when (action) {
    SwipeAction.ARCHIVE -> stringResource(R.string.swipe_a11y_archive)
    SwipeAction.DELETE -> stringResource(R.string.swipe_a11y_delete)
    SwipeAction.FAVORITE -> stringResource(R.string.swipe_a11y_favorite)
    SwipeAction.NONE -> ""
}

private fun invokeSwipeAction(
    action: SwipeAction,
    bookmark: BookmarkListItem,
    onClickArchive: (String, Boolean) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
) {
    when (action) {
        SwipeAction.ARCHIVE -> onClickArchive(bookmark.id, !bookmark.isArchived)
        SwipeAction.DELETE -> onClickDelete(bookmark.id)
        SwipeAction.FAVORITE -> onClickFavorite(bookmark.id, !bookmark.isMarked)
        SwipeAction.NONE -> Unit
    }
}

@Composable
private fun SwipeWrappedBookmark(
    bookmark: BookmarkListItem,
    isPendingDeletion: Boolean,
    swipeConfig: SwipeConfig,
    onClickArchive: (String, Boolean) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onPendingDeletionTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effectiveConfig = if (isPendingDeletion) swipeConfig.copy(enabled = false) else swipeConfig
    SwipeableCardContainer(
        config = effectiveConfig,
        leftAction = effectiveConfig.leftAction,
        rightAction = effectiveConfig.rightAction,
        a11yLeftLabel = swipeActionA11yLabel(effectiveConfig.leftAction),
        a11yRightLabel = swipeActionA11yLabel(effectiveConfig.rightAction),
        onCommitLeft = {
            invokeSwipeAction(
                action = effectiveConfig.leftAction,
                bookmark = bookmark,
                onClickArchive = onClickArchive,
                onClickDelete = onClickDelete,
                onClickFavorite = onClickFavorite,
            )
        },
        onCommitRight = {
            invokeSwipeAction(
                action = effectiveConfig.rightAction,
                bookmark = bookmark,
                onClickArchive = onClickArchive,
                onClickDelete = onClickDelete,
                onClickFavorite = onClickFavorite,
            )
        },
        modifier = modifier,
    ) {
        Box {
            Box(modifier = Modifier.alpha(if (isPendingDeletion) 0.38f else 1f)) {
                content()
            }
            // While pending deletion, an opaque click-interceptor overlay catches
            // every tap and long-press on the card before they can reach any of
            // the inner click surfaces (image, title overlay, body, action icons).
            // Routes to onPendingDeletionTap, which dismisses the snackbar without
            // Undo — committing the delete — and ensures the reading view does
            // NOT also open from a stray click leak.
            if (isPendingDeletion) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPendingDeletionTap
                        )
                )
            }
        }
    }
}

internal fun formatPendingDeleteSnackbarTitleSnippet(
    title: String,
    maxChars: Int = PendingDeleteSnackbarTitleMaxChars
): String {
    val normalizedTitle = title
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalizedTitle.isEmpty()) {
        return ""
    }

    val safeMaxChars = maxChars.coerceAtLeast(4)
    return if (normalizedTitle.length <= safeMaxChars) {
        normalizedTitle
    } else {
        normalizedTitle.take(safeMaxChars - 3).trimEnd() + "..."
    }
}

private data class PendingDeleteSnackbarVisuals(
    override val message: String,
    val prefixText: String,
    val titleSnippet: String,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    override val withDismissAction: Boolean = false
) : SnackbarVisuals

@Composable
private fun PendingDeleteSnackbar(
    snackbarData: SnackbarData,
    visuals: PendingDeleteSnackbarVisuals
) {
    Snackbar(
        action = {
            visuals.actionLabel?.let { actionLabel ->
                TextButton(onClick = { snackbarData.performAction() }) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    ) {
        Text(
            text = buildAnnotatedString {
                append(visuals.prefixText)
                append(" \"")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(visuals.titleSnippet)
                }
                append("\"")
            }
        )
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
        href = "https://example.com",
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
        onClickLabel = {}
    )
}
