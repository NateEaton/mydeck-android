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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.widget.TextView
import com.mydeck.app.R
import com.mydeck.app.ui.navigation.UserGuideRoute
import com.mydeck.app.ui.navigation.UserGuideSectionRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideSectionScreen(
    navHostController: NavHostController,
) {
    val viewModel: UserGuideSectionViewModel = hiltViewModel()
    val uiState = viewModel.uiState
    val colorScheme = MaterialTheme.colorScheme
    // Translucent so the underlying text stays readable in both light and dark themes.
    val highlightColor = colorScheme.primary.copy(alpha = 0.35f).toArgb()

    val scrollState = rememberScrollState()
    var contentTextView by remember { mutableStateOf<android.widget.TextView?>(null) }
    var didScrollToAnchor by remember(uiState.content, uiState.searchAnchor) {
        mutableStateOf(false)
    }

    // When arriving from guide search, scroll to the matched heading (or the query).
    // Keyed on scrollState.maxValue so it re-runs once the rendered content has been
    // measured — otherwise scrollTo would clamp to a stale (smaller) scroll range.
    LaunchedEffect(uiState.content, uiState.searchAnchor, contentTextView, scrollState.maxValue) {
        val anchor = uiState.searchAnchor
        val textView = contentTextView
        if (anchor.isNullOrBlank() || didScrollToAnchor || textView == null) return@LaunchedEffect
        if (uiState.content.isEmpty() || scrollState.maxValue <= 0) return@LaunchedEffect
        val layout = textView.layout ?: return@LaunchedEffect
        val index = textView.text?.toString()?.indexOf(anchor, ignoreCase = true) ?: -1
        if (index >= 0) {
            val line = layout.getLineForOffset(index)
            val targetY = layout.getLineTop(line) + textView.paddingTop
            scrollState.scrollTo(targetY.coerceIn(0, scrollState.maxValue))
            didScrollToAnchor = true
        }
    }

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
                },
                actions = {
                    TextButton(
                        onClick = {
                            navHostController.popBackStack(
                                route = UserGuideRoute,
                                inclusive = false
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.user_guide_contents),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentTextView = textView
                            applyMarkwonColors(textView, colorScheme)
                            // Only set markdown if the content has actually changed
                            if (textView.tag != uiState.content) {
                                markwon.setMarkdown(textView, uiState.content)
                                textView.tag = uiState.content
                                // Highlight every occurrence of the search term when
                                // arriving from guide search.
                                highlightSearchTerm(textView, uiState.searchQuery, highlightColor)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}

/** Marker subclass so our search highlights can be found and removed on re-apply. */
private class SearchHighlightSpan(color: Int) : BackgroundColorSpan(color)

/**
 * Highlight every case-insensitive occurrence of [query] in the rendered text.
 * Matching against the already-rendered text is more accurate than the raw
 * markdown (links are flattened to their display text, headings have no `#`).
 */
private fun highlightSearchTerm(textView: TextView, query: String?, color: Int) {
    val term = query?.trim().orEmpty()
    val current = textView.text
    val spannable: Spannable = when {
        term.isEmpty() || current.isEmpty() -> return
        current is Spannable -> current
        else -> SpannableString(current).also { textView.setText(it, TextView.BufferType.SPANNABLE) }
    }

    // Clear any previous highlights before re-applying.
    spannable.getSpans(0, spannable.length, SearchHighlightSpan::class.java)
        .forEach { spannable.removeSpan(it) }

    val haystack = spannable.toString().lowercase()
    val needle = term.lowercase()
    var index = haystack.indexOf(needle)
    while (index >= 0) {
        spannable.setSpan(
            SearchHighlightSpan(color),
            index,
            index + needle.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        index = haystack.indexOf(needle, index + needle.length)
    }
}
