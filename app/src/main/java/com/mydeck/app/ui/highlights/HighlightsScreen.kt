package com.mydeck.app.ui.highlights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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

@Composable
fun HighlightsScreen(
    navController: NavHostController,
    viewModel: HighlightsViewModel = hiltViewModel(),
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
        onNavigateBack = { navController.popBackStack() },
        onNavigateToBookmark = { bookmarkId, annotationId ->
            navController.navigate(BookmarkDetailRoute(bookmarkId, annotationId = annotationId))
        },
        onRetry = { viewModel.retry() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsContent(
    uiState: HighlightsUiState,
    onNavigateBack: () -> Unit,
    onNavigateToBookmark: (String, String?) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.highlights_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                if (uiState.isRefreshing) {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (uiState.refreshFailed) {
                            item(key = "refresh_error") {
                                RefreshErrorBanner(onRetry = onRetry)
                            }
                        }
                        uiState.groups.forEach { group ->
                            items(group.highlights, key = { it.id }) { highlight ->
                                HighlightCard(
                                    highlight = highlight,
                                    onClick = { onNavigateToBookmark(group.bookmarkId, highlight.id) }
                                )
                            }
                            item(key = "title_${group.bookmarkId}") {
                                BookmarkTitleLine(
                                    group = group,
                                    onClick = { onNavigateToBookmark(group.bookmarkId, null) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshErrorBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.highlights_refresh_failed_showing_saved),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = highlight.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (highlight.note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = highlight.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
    val isDark = isSystemInDarkTheme()
    val base = when (color) {
        "yellow" -> Color(0xFFFFEB3B)
        "red"    -> Color(0xFFEF5350)
        "blue"   -> Color(0xFF42A5F5)
        "green"  -> Color(0xFF66BB6A)
        else     -> MaterialTheme.colorScheme.outline
    }
    return if (isDark) {
        // High alpha on dark background makes it lighter
        base.copy(alpha = 0.60f)
    } else {
        // High alpha on light background makes it darker
        base.copy(alpha = 0.60f)
    }
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
