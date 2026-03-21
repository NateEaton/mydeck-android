package com.mydeck.app.ui.detail.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.mydeck.app.domain.content.OfflineContentPathHandler
import com.mydeck.app.domain.model.ImageGalleryData
import com.mydeck.app.domain.model.SelectionData
import com.mydeck.app.ui.detail.WebViewAnnotationBridge
import com.mydeck.app.ui.detail.WebViewImageBridge
import com.mydeck.app.ui.detail.WebViewAnnotationTapBridge
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.ScrollState
import com.mydeck.app.R
import com.mydeck.app.ui.detail.BookmarkDetailViewModel
import com.mydeck.app.ui.detail.VideoFullscreenDismissSource
import com.mydeck.app.ui.detail.WebViewSearchBridge
import com.mydeck.app.ui.detail.WebViewThemeBridge
import com.mydeck.app.ui.detail.WebViewTypographyBridge
import com.mydeck.app.ui.theme.resolveReaderThemePalette
import com.mydeck.app.util.openUrlInCustomTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayInputStream

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
    onArticleSearchUpdateResults: (Int, Int) -> Unit = { _, _ -> },
    articleViewportTopPx: Int = 0,
    articleTopOffsetPx: Float = 0f,
    viewportHeightPx: Int = 0,
    scrollState: ScrollState,
    onContentReady: (Boolean) -> Unit = {},
    onWebViewChanged: (WebView?) -> Unit = {},
    onImageTapped: (ImageGalleryData) -> Unit = {},
    onImageLongPress: (imageUrl: String, linkUrl: String?, linkType: String, imageAlt: String) -> Unit = { _, _, _, _ -> },
    onLinkLongPress: (linkUrl: String, linkText: String) -> Unit = { _, _ -> },
    onTextSelectionCaptured: (SelectionData) -> Unit = {},
    onAnnotationClicked: (String) -> Unit = {},
    onVideoEnterFullscreen: (View, android.webkit.WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    onVideoExitFullscreen: (VideoFullscreenDismissSource) -> Unit = {},
) {
    val isSystemInDarkMode = isSystemInDarkTheme()
    val localContext = LocalContext.current
    val effectiveAppearance = uiState.readerAppearanceSelection.effectiveAppearance(isSystemInDarkMode)
    val readerThemePalette = remember(localContext, effectiveAppearance) {
        resolveReaderThemePalette(context = localContext, appearance = effectiveAppearance)
    }
    val content = remember(
        uiState.bookmark.bookmarkId,
        uiState.bookmark.articleContent,
        uiState.bookmark.embed
    ) {
        mutableStateOf(uiState.bookmark.getContent(uiState.template, isSystemInDarkMode))
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val offlineAssetLoader = remember(uiState.bookmark.offlineBaseUrl) {
        if (uiState.bookmark.offlineBaseUrl != null) {
            val offlineDir = java.io.File(localContext.filesDir, "offline_content")
            WebViewAssetLoader.Builder()
                .setDomain(OfflineContentPathHandler.OFFLINE_HOST)
                .addPathHandler(
                    OfflineContentPathHandler.OFFLINE_PATH_PREFIX,
                    OfflineContentPathHandler(offlineDir)
                )
                .build()
        } else null
    }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val latestSelectionHandler = rememberUpdatedState(onTextSelectionCaptured)
    val latestAnnotationClickHandler = rememberUpdatedState(onAnnotationClicked)
    val latestTypographySettings = rememberUpdatedState(uiState.typographySettings)
    val latestThemePalette = rememberUpdatedState(readerThemePalette)
    val latestThemeScript = rememberUpdatedState(WebViewThemeBridge.applyTheme(readerThemePalette))
    val lastDeliveredAnnotationTap = remember { mutableStateOf<Pair<String, Long>?>(null) }
    var hasReportedReady by remember(uiState.bookmark.bookmarkId, content.value) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun applyTheme(webView: WebView) {
        webView.setBackgroundColor(android.graphics.Color.parseColor(latestThemePalette.value.bodyBackgroundColor))
        webView.evaluateJavascript(latestThemeScript.value, null)
    }

    suspend fun focusSearchMatch(webView: WebView, matchIndex: Int) {
        if (matchIndex < 0) return
        WebViewSearchBridge.highlightCurrentMatch(webView, matchIndex)
        if (viewportHeightPx <= 0) return
        val centerRatio = WebViewSearchBridge.getMatchViewportCenterRatio(webView, matchIndex) ?: return
        val targetInsideArticle = webView.height.toFloat() * centerRatio
        val targetScroll = (
            articleTopOffsetPx +
                targetInsideArticle -
                (viewportHeightPx / 2f)
            ).toInt().coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(targetScroll)
    }

    fun deliverAnnotationTap(annotationId: String, source: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastDeliveredAnnotationTap.value
        if (previous != null && previous.first == annotationId && (now - previous.second) < 500L) {
            Timber.d("[AnnotationTap] Ignoring duplicate annotation=%s from %s", annotationId, source)
            return
        }
        lastDeliveredAnnotationTap.value = annotationId to now
        Timber.d(
            "[AnnotationTap] Delivering annotation=%s for bookmark=%s from %s",
            annotationId,
            uiState.bookmark.bookmarkId,
            source
        )
        latestAnnotationClickHandler.value(annotationId)
    }

    LaunchedEffect(
        uiState.bookmark.bookmarkId,
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
            onWebViewChanged(null)
            onContentReady(true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onWebViewChanged(null)
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
            // Font size still uses textZoom so body text and in-article headings
            // scale together under the current simplified reader model.
            webView.settings.textZoom = uiState.typographySettings.fontSizePercent
            // Small delay to let any pending content load finish
            delay(150)
            withContext(Dispatchers.Main) {
                val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
                webView.evaluateJavascript(js, null)
            }
        }
    }

    LaunchedEffect(readerThemePalette) {
        webViewRef.value?.let { webView ->
            withContext(Dispatchers.Main) {
                applyTheme(webView)
            }
        }
    }

    // Handle search query changes
    LaunchedEffect(articleSearchState.query) {
        webViewRef.value?.let { webView ->
            if (articleSearchState.isActive && articleSearchState.query.isNotEmpty()) {
                WebViewSearchBridge.searchAndHighlight(
                    webView = webView,
                    query = articleSearchState.query,
                    viewportTopPx = articleViewportTopPx
                ) { matchCount, preferredIndex ->
                    val targetMatch = when {
                        articleSearchState.currentMatch in 1..matchCount -> articleSearchState.currentMatch
                        preferredIndex >= 0 -> preferredIndex + 1
                        matchCount > 0 -> 1
                        else -> 0
                    }
                    onArticleSearchUpdateResults(matchCount, targetMatch)
                    if (targetMatch > 0 && articleSearchState.currentMatch == targetMatch) {
                        coroutineScope.launch {
                            focusSearchMatch(webView, targetMatch - 1)
                        }
                    }
                }
            } else if (articleSearchState.query.isEmpty()) {
                WebViewSearchBridge.clearHighlights(webView)
                onArticleSearchUpdateResults(0, 0)
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
                focusSearchMatch(webView, articleSearchState.currentMatch - 1)
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
                WebViewSearchBridge.searchAndHighlight(
                    webView = webView,
                    query = articleSearchState.query,
                    viewportTopPx = articleViewportTopPx
                ) { matchCount, preferredIndex ->
                    val targetMatch = when {
                        articleSearchState.currentMatch in 1..matchCount -> articleSearchState.currentMatch
                        preferredIndex >= 0 -> preferredIndex + 1
                        matchCount > 0 -> 1
                        else -> 0
                    }
                    onArticleSearchUpdateResults(matchCount, targetMatch)
                    if (targetMatch > 0 && articleSearchState.currentMatch == targetMatch) {
                        coroutineScope.launch {
                            focusSearchMatch(webView, targetMatch - 1)
                        }
                    }
                }
            }
        }
    }
    if (content.value != null) {
        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier.padding(0.dp),
                factory = { context ->
                    HighlightActionWebView(
                        context = context,
                        highlightActionLabel = context.getString(R.string.highlight_action),
                        onAnnotationTapRequested = { webView ->
                            WebViewAnnotationBridge.consumePendingTappedAnnotation(webView) { annotationId ->
                                if (annotationId == null) {
                                    Timber.d(
                                        "[AnnotationTap] No pending tapped annotation for bookmark=%s",
                                        uiState.bookmark.bookmarkId
                                    )
                                } else {
                                    deliverAnnotationTap(annotationId, "pending-js")
                                }
                            }
                        },
                        onHighlightActionRequested = { webView, finishActionMode ->
                            WebViewAnnotationBridge.captureSelection(webView) { selection ->
                                selection?.let { latestSelectionHandler.value(it) }
                                finishActionMode()
                            }
                        }
                    ).apply {
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
                        addJavascriptInterface(
                            WebViewAnnotationTapBridge(
                                onAnnotationTapped = { annotationId ->
                                    deliverAnnotationTap(annotationId, "js-bridge")
                                }
                            ),
                            WebViewAnnotationTapBridge.BRIDGE_NAME
                        )
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onShowCustomView(
                                view: View?,
                                callback: CustomViewCallback?
                            ) {
                                if (!isVideo || view == null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                this@apply.visibility = View.GONE
                                onVideoEnterFullscreen(view, callback)
                            }

                            override fun onHideCustomView() {
                                if (isVideo) {
                                    this@apply.visibility = View.VISIBLE
                                    onVideoExitFullscreen(VideoFullscreenDismissSource.WEB_CHROME)
                                } else {
                                    super.onHideCustomView()
                                }
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                val message = consoleMessage.message()
                                if (message.contains("[AnnotationTap]")) {
                                    Timber.d(
                                        "%s line=%d source=%s",
                                        message,
                                        consoleMessage.lineNumber(),
                                        consoleMessage.sourceId()
                                    )
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }
                        // Intercept link clicks and open in Chrome Custom Tabs
                        // Apply typography after page finishes loading
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                // If offline, substitute a lightweight placeholder for common video embed hosts
                                try {
                                    val cm = view?.context?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                                    val network = cm?.activeNetwork
                                    val isOffline = network == null || cm.getNetworkCapabilities(network)
                                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true
                                    if (isOffline) {
                                        val host = url.host?.lowercase() ?: ""
                                        if (host.contains("youtube.com") || host.contains("youtube-nocookie.com") || host.contains("youtu.be") || host.contains("vimeo.com")) {
                                            val offlineHtml = """
                                                <!DOCTYPE html>
                                                <html>
                                                  <head>
                                                    <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
                                                    <style>
                                                      body { margin:0; padding:0; font-family: sans-serif; background: transparent; }
                                                      .placeholder { display:flex; align-items:center; justify-content:center; text-align:center; padding: 24px; color: #888888; }
                                                    </style>
                                                  </head>
                                                  <body>
                                                    <div class='placeholder'>${view?.context?.getString(com.mydeck.app.R.string.video_embed_offline)}</div>
                                                  </body>
                                                </html>
                                            """.trimIndent()
                                            return WebResourceResponse(
                                                "text/html",
                                                "utf-8",
                                                ByteArrayInputStream(offlineHtml.toByteArray(Charsets.UTF_8))
                                            )
                                        }
                                    }
                                } catch (_: Exception) { /* ignore and fall through */ }
                                offlineAssetLoader?.let { loader ->
                                    val response = loader.shouldInterceptRequest(url)
                                    if (response != null) return response
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            private fun applyReaderEnhancements(webView: WebView) {
                                val typographyJs =
                                    WebViewTypographyBridge.applyTypography(latestTypographySettings.value)
                                applyTheme(webView)
                                webView.evaluateJavascript(typographyJs, null)
                                val imageJs = WebViewImageBridge.injectImageInterceptor()
                                webView.evaluateJavascript(imageJs, null)
                                val annotationJs = WebViewAnnotationBridge.injectAnnotationInteractions()
                                webView.evaluateJavascript(annotationJs, null)
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
                        onWebViewChanged(this)
                    }
                },
                update = {
                    if (content.value != null && it.tag as? String != content.value) {
                        val baseUrl = when {
                            uiState.bookmark.offlineBaseUrl != null -> uiState.bookmark.offlineBaseUrl
                            uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO ->
                                extractEmbedBaseUrl(uiState.bookmark.embed) ?: uiState.bookmark.url
                            else -> null
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
                    onWebViewChanged(it)
                    it.settings.textZoom = uiState.typographySettings.fontSizePercent
                    it.setBackgroundColor(android.graphics.Color.parseColor(readerThemePalette.bodyBackgroundColor))
                }
            )
        }

    } else {
        CircularProgressIndicator()
    }
}

private class HighlightActionWebView(
    context: Context,
    private val highlightActionLabel: String,
    private val onAnnotationTapRequested: (WebView) -> Unit,
    private val onHighlightActionRequested: (WebView, finishActionMode: () -> Unit) -> Unit
) : WebView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        return super.startActionMode(wrapActionModeCallback(callback), type)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return super.startActionMode(wrapActionModeCallback(callback))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
            }

            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                    kotlin.math.abs(event.y - downY) > touchSlop
                val longPress = (event.eventTime - downTime) >= ViewConfiguration.getLongPressTimeout()
                if (!moved && !longPress) {
                    Timber.d("[AnnotationTap] ACTION_UP fallback tap check scheduled")
                    postDelayed({
                        onAnnotationTapRequested(this)
                    }, 150L)
                }
            }
        }

        return handled
    }

    private fun wrapActionModeCallback(callback: ActionMode.Callback?): ActionMode.Callback? {
        if (callback == null) return null
        if (callback is HighlightActionModeCallback) return callback
        return HighlightActionModeCallback(callback)
    }

    private inner class HighlightActionModeCallback(
        private val delegate: ActionMode.Callback
    ) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = delegate.onCreateActionMode(mode, menu)
            addHighlightAction(menu)
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val prepared = delegate.onPrepareActionMode(mode, menu)
            addHighlightAction(menu)
            return prepared
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == HIGHLIGHT_ACTION_ID) {
                onHighlightActionRequested(this@HighlightActionWebView) {
                    mode.finish()
                }
                return true
            }
            return delegate.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            delegate.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            if (delegate is ActionMode.Callback2) {
                delegate.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }

        private fun addHighlightAction(menu: Menu) {
            menu.removeItem(HIGHLIGHT_ACTION_ID)

            val copyOrder = menu.findItem(android.R.id.copy)?.order
            val shareItem = menu.findItem(android.R.id.shareText)
            val shareOrder = shareItem?.order
            val highlightOrder = when {
                copyOrder != null && shareOrder != null -> maxOf(copyOrder, shareOrder - 1)
                copyOrder != null -> copyOrder + 1
                shareOrder != null -> shareOrder
                else -> HIGHLIGHT_ACTION_ORDER
            }

            menu.add(Menu.NONE, HIGHLIGHT_ACTION_ID, highlightOrder, highlightActionLabel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)

            shareItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        }
    }

    private companion object {
        const val HIGHLIGHT_ACTION_ID = 0x4D59444B
        const val HIGHLIGHT_ACTION_ORDER = 1
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
