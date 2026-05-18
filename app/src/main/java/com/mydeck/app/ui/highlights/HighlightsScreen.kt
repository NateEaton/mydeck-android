package com.mydeck.app.ui.highlights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.mydeck.app.R
import com.mydeck.app.domain.model.BookmarkHighlightGroup
import com.mydeck.app.domain.model.HighlightSummary
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import kotlinx.datetime.toJavaInstant
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mydeck.app.ui.components.VerticalScrollbar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HighlightsScreen(
    navController: NavHostController,
    viewModel: HighlightsViewModel = hiltViewModel(),
    showBackButton: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFromScreenOpen()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HighlightsContent(
        uiState = uiState,
        showBackButton = showBackButton,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToBookmark = { bookmarkId, annotationId ->
            navController.navigate(BookmarkDetailRoute(bookmarkId, annotationId = annotationId))
        },
        onRetry = { viewModel.retry() },
        onSearchActiveChange = viewModel::setSearchActive,
        onSearchQueryChange = viewModel::setSearchQuery,
        onClearSearch = viewModel::clearSearch,
        onToggleSearchTarget = viewModel::toggleSearchTarget,
        onSelectColorFilter = viewModel::selectColorFilter,
        onSelectNoteFilter = viewModel::selectNoteFilter,
        onClearFilters = viewModel::clearFilters,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onTitleTap = viewModel::logTitleTap,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsContent(
    uiState: HighlightsUiState,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit,
    onNavigateToBookmark: (String, String?) -> Unit,
    onRetry: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleSearchTarget: (HighlightSearchTarget) -> Unit,
    onSelectColorFilter: (HighlightColorFilter) -> Unit,
    onSelectNoteFilter: (HighlightNoteFilter) -> Unit,
    onClearFilters: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onTitleTap: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(
        uiState.query,
        uiState.searchTargets,
        uiState.selectedColor,
        uiState.noteFilter,
        uiState.sortOrder,
    ) {
        if ((uiState.hasActiveSearchOrFilters || uiState.filteredGroups.isNotEmpty()) && uiState.filteredGroups.isNotEmpty()) {
            lazyListState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (uiState.isSearchActive) {
                            HighlightsSearchField(
                                query = uiState.query,
                                onQueryChange = onSearchQueryChange,
                                onClearSearch = onClearSearch,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.highlights_title),
                                modifier = Modifier.clickable {
                                    onTitleTap()
                                    coroutineScope.launch { lazyListState.scrollToItem(0) }
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackButton || uiState.isSearchActive) {
                            IconButton(
                                onClick = {
                                    if (uiState.isSearchActive) {
                                        onSearchActiveChange(false)
                                    } else {
                                        onNavigateBack()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSearchActive) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleSortOrder) {
                            Icon(
                                imageVector = if (uiState.sortOrder == HighlightSortOrder.Descending) {
                                    Icons.Filled.ArrowDownward
                                } else {
                                    Icons.Filled.ArrowUpward
                                },
                                contentDescription = if (uiState.sortOrder == HighlightSortOrder.Descending) {
                                    stringResource(R.string.highlights_sort_oldest_first)
                                } else {
                                    stringResource(R.string.highlights_sort_newest_first)
                                }
                            )
                        }
                        if (!uiState.isSearchActive) {
                            IconButton(onClick = { onSearchActiveChange(true) }) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = stringResource(R.string.highlights_filter),
                                    tint = if (uiState.hasActiveSearchOrFilters) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }
                        }
                    }
                )
                if (uiState.isSearchActive) {
                    HighlightsFilterControls(
                        uiState = uiState,
                        onToggleSearchTarget = onToggleSearchTarget,
                        onSelectColorFilter = onSelectColorFilter,
                        onSelectNoteFilter = onSelectNoteFilter,
                        onClearFilters = onClearFilters,
                    )
                }
                if (uiState.cachePartial && uiState.groups.isNotEmpty()) {
                    HighlightsPartialCacheBanner()
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isInitialLocalLoad -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.groups.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.refreshFailed) {
                                stringResource(R.string.highlights_refresh_failed)
                            } else {
                                stringResource(R.string.highlights_empty)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.refreshFailed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (uiState.isRefreshing) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.highlights_refreshing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.refreshFailed) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.hasNoMatches) {
                        NoMatchingHighlightsState(
                            filtersActive = uiState.hasActiveSearchOrFilters,
                            onClearFilters = onClearFilters,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val pullToRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = onRetry,
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val dateAnchors = remember(uiState.filteredGroups) {
                                    buildHighlightDateAnchors(uiState.filteredGroups)
                                }
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    uiState.filteredGroups.forEach { group ->
                                        items(group.highlights, key = { "${group.bookmarkId}_${it.id}" }) { highlight ->
                                            HighlightCard(
                                                highlight = highlight,
                                                onClick = { onNavigateToBookmark(group.bookmarkId, highlight.id) }
                                            )
                                        }
                                        item(key = "title_${group.bookmarkId}_${group.groupDate}") {
                                            BookmarkTitleLine(
                                                group = group,
                                                onClick = { onNavigateToBookmark(group.bookmarkId, null) }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                                if (dateAnchors.size > 1) {
                                    HighlightsDateFastScroller(
                                        anchors = dateAnchors,
                                        lazyListState = lazyListState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    VerticalScrollbar(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight(),
                                        lazyListState = lazyListState
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightsPartialCacheBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SyncProblem,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.highlights_partial_cache_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun HighlightsDateFastScroller(
    anchors: List<HighlightDateAnchor>,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (anchors.isEmpty()) {
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val locale = Locale.getDefault()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var selectedAnchor by remember { mutableStateOf<HighlightDateAnchor?>(null) }
    var lastScrolledItemIndex by remember { mutableStateOf<Int?>(null) }

    val activeAnchor by remember(anchors, lazyListState, selectedAnchor, isDragging) {
        derivedStateOf {
            selectedAnchor
                ?: anchors.lastOrNull { it.itemIndex <= lazyListState.firstVisibleItemIndex }
                ?: anchors.first()
        }
    }
    val activeLabel = activeAnchor.monthYearLabel(locale)
    val fastScrollDescription = stringResource(R.string.highlights_fast_scroll)

    LaunchedEffect(lazyListState.isScrollInProgress, isDragging) {
        if (lazyListState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1200)
            isVisible = false
            selectedAnchor = null
            lastScrolledItemIndex = null
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "HighlightsDateFastScrollerAlpha"
    )

    if (!isVisible && !isDragging && alpha <= 0.01f) {
        return
    }

    fun updateAnchorForPosition(y: Float) {
        val height = containerSize.height
        if (height <= 0) {
            return
        }
        val progress = (y / height.toFloat()).coerceIn(0f, 1f)
        val anchorIndex = (progress * anchors.lastIndex).roundToInt()
            .coerceIn(0, anchors.lastIndex)
        val anchor = anchors[anchorIndex]
        selectedAnchor = anchor
        if (lastScrolledItemIndex != anchor.itemIndex) {
            lastScrolledItemIndex = anchor.itemIndex
            coroutineScope.launch {
                lazyListState.scrollToItem(anchor.itemIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .width(128.dp)
            .alpha(alpha)
            .onSizeChanged { containerSize = it }
    ) {
        val activeAnchorIndex = anchors.indexOfFirst {
            it.year == activeAnchor.year && it.month == activeAnchor.month
        }.coerceAtLeast(0)
        val activeBias = if (anchors.size == 1) {
            -1f
        } else {
            (activeAnchorIndex.toFloat() / anchors.lastIndex.toFloat() * 2f) - 1f
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            visibleYearAnchors(anchors, activeAnchor).forEach { anchor ->
                FastScrollDateChip(
                    text = anchor.year.toString(),
                    selected = anchor.year == activeAnchor.year,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(48.dp)
                .semantics {
                    contentDescription = fastScrollDescription
                    stateDescription = activeLabel
                }
                .pointerInput(anchors, containerSize) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateAnchorForPosition(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            updateAnchorForPosition(change.position.y)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        )

        FastScrollDateChip(
            text = activeLabel,
            selected = true,
            modifier = Modifier
                .align(BiasAlignment(1f, activeBias))
                .padding(end = 88.dp)
        )

        Surface(
            modifier = Modifier
                .align(BiasAlignment(1f, activeBias))
                .padding(end = 6.dp)
                .size(width = 40.dp, height = if (isDragging) 72.dp else 56.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF202124).copy(alpha = 0.86f),
            contentColor = Color.White,
            tonalElevation = 4.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun FastScrollDateChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
        } else {
            Color(0xFF202124).copy(alpha = 0.88f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            Color.White
        },
        tonalElevation = 3.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

private fun visibleYearAnchors(
    anchors: List<HighlightDateAnchor>,
    activeAnchor: HighlightDateAnchor,
): List<HighlightDateAnchor> {
    val yearAnchors = anchors.distinctBy { it.year }
    val maxYearChips = 9
    if (yearAnchors.size <= maxYearChips) {
        return yearAnchors
    }

    val selectedIndices = mutableSetOf<Int>()
    repeat(maxYearChips) { index ->
        val sourceIndex = (index * (yearAnchors.lastIndex.toFloat() / (maxYearChips - 1))).roundToInt()
        selectedIndices += sourceIndex.coerceIn(0, yearAnchors.lastIndex)
    }
    val activeIndex = yearAnchors.indexOfFirst { it.year == activeAnchor.year }
    if (activeIndex >= 0) {
        selectedIndices += activeIndex
    }

    return selectedIndices
        .sorted()
        .map { yearAnchors[it] }
}

private fun HighlightDateAnchor.monthYearLabel(locale: Locale): String {
    val monthLabel = Month.of(month).getDisplayName(TextStyle.SHORT, locale)
    return "$monthLabel $year"
}

@Composable
private fun HighlightsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.highlights_search_hint)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.highlights_clear_search)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
    )
}

@Composable
private fun HighlightsFilterControls(
    uiState: HighlightsUiState,
    onToggleSearchTarget: (HighlightSearchTarget) -> Unit,
    onSelectColorFilter: (HighlightColorFilter) -> Unit,
    onSelectNoteFilter: (HighlightNoteFilter) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.highlights_grouped_results_count,
                    uiState.filteredHighlightCount,
                    uiState.filteredGroups.size
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchTargetChip(
                selected = uiState.searchTargets.text,
                label = stringResource(R.string.highlights_filter_text),
                onClick = { onToggleSearchTarget(HighlightSearchTarget.Text) },
            )
            SearchTargetChip(
                selected = uiState.searchTargets.notes,
                label = stringResource(R.string.highlights_filter_notes),
                onClick = { onToggleSearchTarget(HighlightSearchTarget.Notes) },
            )
            SearchTargetChip(
                selected = uiState.searchTargets.title,
                label = stringResource(R.string.highlights_filter_title),
                onClick = { onToggleSearchTarget(HighlightSearchTarget.Title) },
            )
            SearchTargetChip(
                selected = uiState.searchTargets.site,
                label = stringResource(R.string.highlights_filter_site),
                onClick = { onToggleSearchTarget(HighlightSearchTarget.Site) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HighlightColorFilter.values().forEach { colorFilter ->
                ColorFilterChip(
                    colorFilter = colorFilter,
                    selected = uiState.selectedColor == colorFilter,
                    onClick = { onSelectColorFilter(colorFilter) },
                )
            }
            NoteFilterChip(
                selected = uiState.noteFilter == HighlightNoteFilter.Any,
                label = stringResource(R.string.highlights_filter_notes_any),
                onClick = { onSelectNoteFilter(HighlightNoteFilter.Any) },
            )
            NoteFilterChip(
                selected = uiState.noteFilter == HighlightNoteFilter.WithNotes,
                label = stringResource(R.string.highlights_filter_with_notes),
                onClick = { onSelectNoteFilter(HighlightNoteFilter.WithNotes) },
            )
            NoteFilterChip(
                selected = uiState.noteFilter == HighlightNoteFilter.WithoutNotes,
                label = stringResource(R.string.highlights_filter_without_notes),
                onClick = { onSelectNoteFilter(HighlightNoteFilter.WithoutNotes) },
            )
            if (uiState.hasActiveSearchOrFilters) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.highlights_clear_filters))
                }
            }
        }
    }
}

@Composable
private fun SearchTargetChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun NoteFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun ColorFilterChip(
    colorFilter: HighlightColorFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(colorFilter.label()) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colorFilter.swatchColor(), CircleShape)
            )
        },
    )
}

@Composable
private fun HighlightColorFilter.label(): String {
    return when (this) {
        HighlightColorFilter.Any -> stringResource(R.string.highlights_filter_color_any)
        HighlightColorFilter.Yellow -> stringResource(R.string.highlights_filter_color_yellow)
        HighlightColorFilter.Red -> stringResource(R.string.highlights_filter_color_red)
        HighlightColorFilter.Blue -> stringResource(R.string.highlights_filter_color_blue)
        HighlightColorFilter.Green -> stringResource(R.string.highlights_filter_color_green)
        HighlightColorFilter.None -> stringResource(R.string.highlights_filter_color_none)
    }
}

@Composable
private fun HighlightColorFilter.swatchColor(): Color {
    return when (this) {
        HighlightColorFilter.Any -> MaterialTheme.colorScheme.outline
        HighlightColorFilter.Yellow -> Color(0xFFFFEB3B)
        HighlightColorFilter.Red -> Color(0xFFEF5350)
        HighlightColorFilter.Blue -> Color(0xFF42A5F5)
        HighlightColorFilter.Green -> Color(0xFF66BB6A)
        HighlightColorFilter.None -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun NoMatchingHighlightsState(
    filtersActive: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.highlights_no_matches),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filtersActive) {
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.highlights_clear_filters))
            }
        }
    }
}

@Composable
private fun HighlightCard(
    highlight: HighlightSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = annotationColor(highlight.color)
    val borderColor = annotationBorderColor(highlight.color)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
            Text(
                text = formatter.format(highlight.created.toJavaInstant()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (highlight.note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = highlight.note,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = highlight.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun annotationColor(color: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (color) {
        "yellow" -> if (isDark) Color(0xFFFFEB3B).copy(alpha = 0.15f) else Color(0xFFFFEB3B).copy(alpha = 0.10f)
        "red"    -> if (isDark) Color(0xFFEF5350).copy(alpha = 0.15f) else Color(0xFFEF5350).copy(alpha = 0.10f)
        "blue"   -> if (isDark) Color(0xFF42A5F5).copy(alpha = 0.15f) else Color(0xFF42A5F5).copy(alpha = 0.10f)
        "green"  -> if (isDark) Color(0xFF66BB6A).copy(alpha = 0.15f) else Color(0xFF66BB6A).copy(alpha = 0.10f)
        else     -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
}

@Composable
private fun annotationBorderColor(color: String): Color {
    val base = when (color) {
        "yellow" -> Color(0xFFFFEB3B)
        "red"    -> Color(0xFFEF5350)
        "blue"   -> Color(0xFF42A5F5)
        "green"  -> Color(0xFF66BB6A)
        else     -> MaterialTheme.colorScheme.outline
    }
    return base.copy(alpha = 0.60f)
}

@Composable
private fun BookmarkTitleLine(
    group: BookmarkHighlightGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = buildAnnotatedString {
        if (group.bookmarkSiteName.isNotBlank()) {
            withStyle(style = SpanStyle(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )) {
                append(group.bookmarkSiteName)
            }
            append(" — ")
        }
        withStyle(style = SpanStyle(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )) {
            append(group.bookmarkTitle)
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
