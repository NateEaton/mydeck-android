package com.mydeck.app.ui.detail.components

import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.WebView
import com.mydeck.app.domain.model.ImageGalleryData
import com.mydeck.app.ui.detail.AnnotationJsBridge
import com.mydeck.app.ui.detail.WebViewAnnotationBridge
import com.mydeck.app.ui.detail.WebViewImageBridge
import com.mydeck.app.domain.model.Annotation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.serialization.json.Json

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
    annotations: List<Annotation> = emptyList(),
    scrollToAnnotationId: String? = null,
    isAnnotationSelectionActive: Boolean = false,
    onArticleSearchUpdateResults: (Int) -> Unit = {},
    onContentReady: (Boolean) -> Unit = {},
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onTextSelected: (startSelector: String, startOffset: Int, endSelector: String, endOffset: Int, text: String) -> Unit = { _, _, _, _, _ -> },
    onAnnotationClicked: (annotationId: String) -> Unit = {},
    onScrollToAnnotationConsumed: () -> Unit = {},
) {
    val isSystemInDarkMode = isSystemInDarkTheme()
    val content = remember(
        uiState.bookmark.bookmarkId,
        isSystemInDarkMode,
        uiState.template,
        uiState.bookmark.articleContent,
        uiState.bookmark.embed
    ) {
        mutableStateOf(uiState.bookmark.getContent(uiState.template, isSystemInDarkMode))
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val latestAnnotationsState = rememberUpdatedState(annotations)
    var hasReportedReady by remember(uiState.bookmark.bookmarkId, content.value) { mutableStateOf(false) }
    var isAnnotationBridgeReady by remember(uiState.bookmark.bookmarkId, content.value) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        uiState.bookmark.bookmarkId,
        isSystemInDarkMode,
        uiState.template,
        uiState.bookmark.articleContent,
        uiState.bookmark.embed
    ) {
        content.value = uiState.bookmark.getContent(uiState.template, isSystemInDarkMode)
        webViewRef.value?.settings?.textZoom = uiState.typographySettings.fontSizePercent
    }

    LaunchedEffect(content.value) {
        if (content.value != null) {
            hasReportedReady = false
            onContentReady(false)
        } else {
            onContentReady(true)
        }
    }

    // Some embed-heavy pages can delay WebView finished callbacks for a long time.
    // If that happens, unblock the reader once we have waited long enough.
    LaunchedEffect(content.value, uiState.bookmark.bookmarkId) {
        if (content.value != null) {
            delay(3000)
            if (!hasReportedReady) {
                hasReportedReady = true
                onContentReady(true)
            }
        }
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
                    if (matchCount > 0) {
                        WebViewSearchBridge.highlightCurrentMatch(webView, 0)
                    }
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
                    if (matchCount > 0) {
                        WebViewSearchBridge.highlightCurrentMatch(webView, 0)
                    }
                }
            }
        }
    }

    // Re-render annotations whenever they change after the page bridge is ready
    LaunchedEffect(annotations, isAnnotationBridgeReady) {
        if (isAnnotationBridgeReady) {
            withContext(Dispatchers.Main) {
                webViewRef.value?.evaluateJavascript(
                    WebViewAnnotationBridge.renderAnnotations(annotations), null
                )
            }
        }
    }

    LaunchedEffect(isAnnotationSelectionActive, isAnnotationBridgeReady) {
        if (isAnnotationBridgeReady) {
            withContext(Dispatchers.Main) {
                webViewRef.value?.evaluateJavascript(
                    WebViewAnnotationBridge.setSelectionMode(isAnnotationSelectionActive),
                    null
                )
            }
        }
    }

    // Scroll to a specific annotation on demand
    LaunchedEffect(scrollToAnnotationId, isAnnotationBridgeReady, annotations) {
        if (!isAnnotationBridgeReady) return@LaunchedEffect
        scrollToAnnotationId?.let { id ->
            webViewRef.value?.evaluateJavascript(
                WebViewAnnotationBridge.scrollToAnnotation(id)
            ) { result ->
                if (result == "true") {
                    onScrollToAnnotationConsumed()
                } else {
                    webViewRef.value?.evaluateJavascript(
                        WebViewAnnotationBridge.renderAnnotations(annotations)
                    ) {
                        webViewRef.value?.evaluateJavascript(
                            WebViewAnnotationBridge.scrollToAnnotation(id),
                            null
                        )
                        onScrollToAnnotationConsumed()
                    }
                }
            }
        }
    }

    if (content.value != null) {
        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    WebView(context).apply {
                        val isVideo = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO
                        val isArticle = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE
                        val isPhoto = uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO
                        settings.javaScriptEnabled = isVideo || isArticle || isPhoto  // Enable JS for articles (search) and photos (lightbox)
                        settings.domStorageEnabled = isVideo
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.defaultTextEncodingName = "utf-8"
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.textZoom = uiState.typographySettings.fontSizePercent

                        // Register the JS-to-native image bridge
                        addJavascriptInterface(
                            WebViewImageBridge(
                                json = json,
                                onImageTapped = { data -> onImageTapped(data) }
                            ),
                            WebViewImageBridge.BRIDGE_NAME
                        )

                        // Register the annotation bridge
                        addJavascriptInterface(
                            AnnotationJsBridge(
                                onTextSelected = onTextSelected,
                                onAnnotationClicked = onAnnotationClicked,
                            ),
                            "AnnotationInterface"
                        )

                        // Intercept link clicks and open in Chrome Custom Tabs
                        // Apply typography after page finishes loading
                        webViewClient = object : android.webkit.WebViewClient() {
                            private fun applyReaderEnhancements(webView: WebView) {
                                val typographyJs =
                                    WebViewTypographyBridge.applyTypography(uiState.typographySettings)
                                webView.evaluateJavascript(typographyJs, null)
                                val imageJs = WebViewImageBridge.injectImageInterceptor()
                                webView.evaluateJavascript(imageJs, null)
                                webView.evaluateJavascript(
                                    WebViewAnnotationBridge.injectUtilities(), null
                                )
                                webView.evaluateJavascript(
                                    WebViewAnnotationBridge.injectSelectionObserver(), null
                                )
                                webView.evaluateJavascript(
                                    WebViewAnnotationBridge.setSelectionMode(
                                        isAnnotationSelectionActive
                                    ),
                                    null
                                )
                                webView.evaluateJavascript(
                                    WebViewAnnotationBridge.renderAnnotations(latestAnnotationsState.value), null
                                )
                                isAnnotationBridgeReady = true
                            }

                            private fun reportReadyIfNeeded(webView: WebView?) {
                                if (hasReportedReady) return
                                if (webView == null) {
                                    hasReportedReady = true
                                    onContentReady(true)
                                    return
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    webView.postVisualStateCallback(
                                        System.currentTimeMillis(),
                                        object : WebView.VisualStateCallback() {
                                            override fun onComplete(requestId: Long) {
                                                if (!hasReportedReady) {
                                                    hasReportedReady = true
                                                    onContentReady(true)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    hasReportedReady = true
                                    onContentReady(true)
                                }
                            }

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

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                view?.let { applyReaderEnhancements(it) }
                                if (!hasReportedReady) {
                                    hasReportedReady = true
                                    onContentReady(true)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.let { applyReaderEnhancements(it) }
                                reportReadyIfNeeded(view)
                            }
                        }

                        // Long-press context menu via native hit testing
                        setOnLongClickListener { view ->
                            val webView = view as WebView
                            val result = webView.hitTestResult
                            val imageExtRe = Regex(
                                """\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff|avif)(\?.*)?$""",
                                RegexOption.IGNORE_CASE
                            )
                            when (result.type) {
                                WebView.HitTestResult.IMAGE_TYPE -> {
                                    val imageUrl = result.extra ?: ""
                                    webView.evaluateJavascript(
                                        WebViewImageBridge.getImageAltForUrl(imageUrl)
                                    ) { alt ->
                                        onImageLongPress(imageUrl, null, "none", WebViewImageBridge.decodeJsString(alt))
                                    }
                                    true
                                }
                                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                    val linkUrl = result.extra ?: ""
                                    webView.evaluateJavascript(
                                        WebViewImageBridge.getLinkTextForUrl(linkUrl)
                                    ) { text ->
                                        onLinkLongPress(linkUrl, WebViewImageBridge.decodeJsString(text))
                                    }
                                    true
                                }
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                    val linkUrl = result.extra ?: ""
                                    webView.evaluateJavascript(
                                        WebViewImageBridge.getImageUrlAtLink(linkUrl)
                                    ) { imageUrl ->
                                        val cleanUrl = WebViewImageBridge.decodeJsString(imageUrl)
                                        webView.evaluateJavascript(
                                            WebViewImageBridge.getImageAltForUrl(cleanUrl)
                                        ) { alt ->
                                            val linkType = if (imageExtRe.containsMatchIn(linkUrl)) "image" else "page"
                                            onImageLongPress(cleanUrl, linkUrl, linkType, WebViewImageBridge.decodeJsString(alt))
                                        }
                                    }
                                    true
                                }
                                else -> false
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
    // Reactively observe connectivity to detect offline state immediately
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    }
    var isNetworkError by remember {
        val network = connectivityManager.activeNetwork
        val isOffline = network == null || connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true
        mutableStateOf(isOffline)
    }
    // Update reactively when connectivity changes
    DisposableEffect(connectivityManager) {
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isNetworkError = false
            }
            override fun onLost(network: android.net.Network) {
                isNetworkError = true
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    Column(modifier = modifier) {
        // Show progress indicator while loading
        if (loadingProgress < 100 && httpError == null && !isNetworkError) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }

        if (isNetworkError) {
            // Offline page - simple icon and message, matching Readeck style
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.webview_offline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (httpError != null) {
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
                    Spacer(modifier = Modifier.height(8.dp))
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

                        // Intercept errors to show app-provided messages
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isNetworkError = true
                                }
                            }

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
