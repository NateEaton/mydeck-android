package com.mydeck.app.ui.userguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.navigation.UserGuideSectionRoute
import androidx.compose.foundation.clickable

fun getSectionIcon(fileName: String) = when (fileName) {
    "getting-started.md"  -> Icons.Default.Info
    "your-bookmarks.md"   -> Icons.Default.CollectionsBookmark
    "reading.md"          -> Icons.Default.Bookmark
    "labels.md"           -> Icons.AutoMirrored.Filled.Label
    "highlights.md"       -> Icons.Default.EditNote
    "collections.md"      -> Icons.Default.Collections
    "organizing.md"       -> Icons.Outlined.Inventory2
    "offline-reading.md"  -> Icons.Outlined.DownloadForOffline
    "settings.md"         -> Icons.Outlined.Settings
    else                  -> Icons.Default.Info
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideIndexScreen(
    navHostController: NavHostController,
    showBackButton: Boolean = true,
) {
    val viewModel: UserGuideIndexViewModel = hiltViewModel()
    val uiState = viewModel.uiState
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_guide)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { navHostController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.user_guide_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.user_guide_search_clear)
                                    )
                                }
                            }
                        },
                        singleLine = true
                    )

                    when {
                        uiState.searchQuery.isBlank() -> {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.sections) { section ->
                                    GuideSectionRow(
                                        title = section.title,
                                        supporting = null,
                                        fileName = section.fileName,
                                        onClick = {
                                            navHostController.navigate(
                                                UserGuideSectionRoute(
                                                    fileName = section.fileName,
                                                    title = section.title
                                                )
                                            ) { launchSingleTop = true }
                                        }
                                    )
                                }
                            }
                        }
                        uiState.searchResults.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.user_guide_search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(uiState.searchResults) { result ->
                                    GuideSectionRow(
                                        title = result.section.title,
                                        supporting = result.matchedHeading ?: result.snippet,
                                        fileName = result.section.fileName,
                                        onClick = {
                                            navHostController.navigate(
                                                UserGuideSectionRoute(
                                                    fileName = result.section.fileName,
                                                    title = result.section.title
                                                )
                                            ) { launchSingleTop = true }
                                        }
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
private fun GuideSectionRow(
    title: String,
    supporting: String?,
    fileName: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let {
            {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = getSectionIcon(fileName),
                contentDescription = null
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
