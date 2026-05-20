package com.mydeck.app.ui.highlights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mydeck.app.ui.components.VerticalScrollbar
import kotlinx.coroutines.launch

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
                if (uiState.isRefreshing && !uiState.isUserRefreshing && !uiState.isInitialLocalLoad) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                            isRefreshing = uiState.isUserRefreshing,
                            onRefresh = onRetry,
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
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
