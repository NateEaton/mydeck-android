package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import timber.log.Timber

class WebViewActionsBridge(
    private val onToggleFavorite: () -> Unit,
    private val onToggleArchive: () -> Unit,
) {
    @JavascriptInterface
    fun toggleFavorite() {
        Handler(Looper.getMainLooper()).post {
            Timber.d("[Actions] JS bridge -> toggleFavorite")
            onToggleFavorite()
        }
    }

    @JavascriptInterface
    fun toggleArchive() {
        Handler(Looper.getMainLooper()).post {
            Timber.d("[Actions] JS bridge -> toggleArchive")
            onToggleArchive()
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckActionsBridge"
    }
}
