package com.mydeck.app.ui.userguide

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    navHostController: NavHostController
) {
    val viewModel: UserGuideViewModel = hiltViewModel()
    val uiState = viewModel.uiState
    val context = LocalContext.current
    
    // Initialize Markwon
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(GlideImagesPlugin.create(context))
            .build()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_guide)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navHostController.popBackStack() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Table of Contents
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.sections) { section ->
                            NavigationItem(
                                section = section,
                                isSelected = section.fileName == uiState.selectedSection?.fileName,
                                onClick = { viewModel.selectSection(section) }
                            )
                        }
                    }
                }
                
                // Content Area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (uiState.isLoadingContent) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.contentError != null) {
                        Text(
                            text = "Error: ${uiState.contentError}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Render markdown content
                        AndroidView(
                            factory = { context ->
                                android.widget.TextView(context).apply {
                                    setPadding(16, 16, 16, 16)
                                    setTextIsSelectable(true)
                                }
                            },
                            update = { textView ->
                                markwon.setMarkdown(textView, uiState.currentContent)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(
    section: GuideSection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Text(
            text = section.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UserGuideScreenPreview() {
    // Preview disabled due to NavHostController complexity
    // Use actual app to test User Guide functionality
}
