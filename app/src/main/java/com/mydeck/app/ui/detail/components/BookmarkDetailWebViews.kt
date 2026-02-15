package com.mydeck.app.ui.detail.components

import android.net.Uri
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mydeck.app.R
import com.mydeck.app.ui.detail.BookmarkDetailViewModel
import com.mydeck.app.ui.detail.WebViewSearchBridge
import com.mydeck.app.ui.detail.WebViewTypographyBridge
import com.mydeck.app.util.openUrlInCustomTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EmptyBookmarkDetailArticle(
    modifier: Modifier
) {
    Text(
        modifier = modifier,
        text = stringResource(R.string.detail_view_no_content)
    )
}

@Composable
fun BookmarkDetailArticle(
    modifier: Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    articleSearchState: BookmarkDetailViewModel.ArticleSearchState = BookmarkDetailViewModel.ArticleSearchState(),
    onArticleSearchUpdateResults: (Int) -> Unit = {}
) {
    val isSystemInDarkMode = isSystemInDarkTheme()
    val content = remember(uiState.bookmark.bookmarkId, isSystemInDarkMode, uiState.template) {
        mutableStateOf<String?>(null)
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(uiState.bookmark.bookmarkId, isSystemInDarkMode, uiState.template) {
        content.value = getTemplate(uiState, isSystemInDarkMode)
        webViewRef.value?.settings?.textZoom = uiState.typographySettings.fontSizePercent
    }

    // Apply typography settings when they change (non-textZoom properties via JS)
    LaunchedEffect(uiState.typographySettings) {
        webViewRef.value?.let { webView ->
            webView.settings.textZoom = uiState.typographySettings.fontSizePercent
            // Small delay to let any pending content load finish
            delay(150)
            withContext(Dispatchers.Main) {
                val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
                webView.evaluateJavascript(js, null)
            }
        }
    }

    // Handle search query changes
    LaunchedEffect(articleSearchState.query) {
        webViewRef.value?.let { webView ->
            if (articleSearchState.isActive && articleSearchState.query.isNotEmpty()) {
                WebViewSearchBridge.searchAndHighlight(webView, articleSearchState.query) { matchCount ->
                    onArticleSearchUpdateResults(matchCount)
                }
            } else if (articleSearchState.query.isEmpty()) {
                WebViewSearchBridge.clearHighlights(webView)
                onArticleSearchUpdateResults(0)
            }
        }
    }

    // Handle current match navigation
    LaunchedEffect(articleSearchState.currentMatch) {
        webViewRef.value?.let { webView ->
            if (articleSearchState.isActive &&
                articleSearchState.currentMatch > 0 &&
                articleSearchState.totalMatches > 0) {
                // Convert 1-based index to 0-based for JavaScript
                WebViewSearchBridge.highlightCurrentMatch(webView, articleSearchState.currentMatch - 1)
            }
        }
    }

    // Clear highlights when search is deactivated
    LaunchedEffect(articleSearchState.isActive) {
        if (!articleSearchState.isActive) {
            webViewRef.value?.let { webView ->
                WebViewSearchBridge.clearHighlights(webView)
            }
        }
    }

    // Re-apply search when content is reloaded (theme change, etc.)
    LaunchedEffect(content.value, articleSearchState.query) {
        if (articleSearchState.isActive &&
            articleSearchState.query.isNotEmpty() &&
            content.value != null) {
            // Delay to ensure WebView has loaded the new content
            delay(100)
            webViewRef.value?.let { webView ->
                WebViewSearchBridge.searchAndHighlight(webView, articleSearchState.query) { matchCount ->
                    onArticleSearchUpdateResults(matchCount)
                }
            }
        }
    }
    if (content.value != null) {
        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier.padding(0.dp),
                factory = { context ->
                    WebView(context).apply {
                        val isVideo = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO
                        val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
                        settings.javaScriptEnabled = isVideo || isArticle  // Enable JS for articles (needed for search)
                        settings.domStorageEnabled = isVideo
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.defaultTextEncodingName = "utf-8"
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.textZoom = uiState.typographySettings.fontSizePercent

                        // Intercept link clicks and open in Chrome Custom Tabs
                        // Apply typography after page finishes loading
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                if (url != null) {
                                    openUrlInCustomTab(context, url)
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.let {
                                    val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
                                    it.evaluateJavascript(js, null)
                                }
                            }
                        }

                        webViewRef.value = this
                    }
                },
                update = {
                    if (content.value != null && it.tag as? String != content.value) {
                        val baseUrl = if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO) {
                            extractEmbedBaseUrl(uiState.bookmark.embed) ?: uiState.bookmark.url
                        } else {
                            null
                        }
                        it.loadDataWithBaseURL(
                            baseUrl,
                            content.value!!,
                            "text/html",
                            "utf-8",
                            null
                        )
                        it.tag = content.value
                    }
                    // Update reference and zoom
                    webViewRef.value = it
                    it.settings.textZoom = uiState.typographySettings.fontSizePercent
                }
            )
        }

    } else {
        CircularProgressIndicator()
    }
}

@Composable
fun BookmarkDetailOriginalWebView(
    modifier: Modifier = Modifier,
    url: String
) {
    var loadingProgress by remember { mutableStateOf(0) }
    var httpError by remember { mutableStateOf<Pair<Int, String>?>(null) }

    Column(modifier = modifier) {
        // Show progress indicator while loading
        if (loadingProgress < 100 && httpError == null) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }

        if (httpError != null) {
            // App-provided error message for HTTP errors
            val (errorCode, _) = httpError!!
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.webview_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.webview_error_message, errorCode),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.defaultTextEncodingName = "utf-8"
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        // Intercept HTTP errors to show app-provided messages
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                // Only handle errors for the main page, not subresources
                                if (request?.isForMainFrame == true) {
                                    val code = errorResponse?.statusCode ?: 0
                                    val description = errorResponse?.reasonPhrase ?: "Unknown error"
                                    httpError = Pair(code, description)
                                }
                            }
                        }

                        // Track loading progress
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadingProgress = newProgress
                            }
                        }

                        loadUrl(url)
                    }
                }
            )
        }
    }
}

suspend fun getTemplate(uiState: BookmarkDetailViewModel.UiState.Success, isSystemInDarkMode: Boolean): String? {
    return withContext(Dispatchers.Main) { // Should be Main because WebView.getContent requires Main or UI thread? Actually BookmarkDetailViewModel.Bookmark might not need it.
        uiState.bookmark.getContent(uiState.template, isSystemInDarkMode)
    }
}

/**
 * Extracts a base URL from an iframe's src attribute in embed HTML.
 * Returns scheme://host/ or null if parsing fails.
 */
private fun extractEmbedBaseUrl(embedHtml: String?): String? {
    if (embedHtml.isNullOrBlank()) return null
    val srcRegex = Regex("""<iframe[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val match = srcRegex.find(embedHtml) ?: return null
    val iframeSrc = match.groupValues[1]
    return try {
        val uri = Uri.parse(iframeSrc)
        val scheme = uri.scheme
        val host = uri.host
        if (!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
            "$scheme://$host/"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
