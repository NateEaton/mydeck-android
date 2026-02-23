package com.mydeck.app.ui.userguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.navigation.UserGuideSectionRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideSectionScreen(
    navHostController: NavHostController
) {
    val viewModel: UserGuideSectionViewModel = hiltViewModel()
    val uiState = viewModel.uiState
    val colorScheme = MaterialTheme.colorScheme

    val markwon = rememberMarkwon(
        onSectionNavigate = { fileName ->
            val title = MarkdownAssetLoader.DEFAULT_SECTIONS
                .find { it.fileName == fileName }?.title
                ?: fileName.removeSuffix(".md")
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() }
            navHostController.navigate(
                UserGuideSectionRoute(fileName = fileName, title = title)
            )
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
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
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.TextView(ctx).apply {
                                setPadding(32, 16, 32, 32)
                                setTextIsSelectable(true)
                                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                            }
                        },
                        update = { textView ->
                            applyMarkwonColors(textView, colorScheme)
                            // Only set markdown if the content has actually changed
                            if (textView.tag != uiState.content) {
                                markwon.setMarkdown(textView, uiState.content)
                                textView.tag = uiState.content
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
