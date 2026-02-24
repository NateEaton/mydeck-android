package com.mydeck.app.ui.userguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.navigation.UserGuideSectionRoute
import androidx.compose.foundation.clickable

fun getSectionIcon(fileName: String) = when (fileName) {
    "getting-started.md" -> Icons.Default.Info
    "your-bookmarks.md"  -> Icons.Default.CollectionsBookmark
    "reading.md"         -> Icons.Default.Bookmark
    "organising.md"      -> Icons.AutoMirrored.Filled.Label
    "settings.md"        -> Icons.Outlined.Settings
    else                 -> Icons.Default.Info
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideIndexScreen(
    navHostController: NavHostController
) {
    val viewModel: UserGuideIndexViewModel = hiltViewModel()
    val uiState = viewModel.uiState
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_guide)) },
                navigationIcon = {
                    IconButton(onClick = { navHostController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(uiState.sections) { section ->
                        ListItem(
                            headlineContent = { Text(section.title) },
                            leadingContent = {
                                Icon(
                                    imageVector = getSectionIcon(section.fileName),
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navHostController.navigate(
                                    UserGuideSectionRoute(
                                        fileName = section.fileName,
                                        title = section.title
                                    )
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
