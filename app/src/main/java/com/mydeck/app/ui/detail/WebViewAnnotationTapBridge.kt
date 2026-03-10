package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import timber.log.Timber

class WebViewAnnotationTapBridge(
    private val onAnnotationTapped: (String) -> Unit
) {
    @JavascriptInterface
    fun onAnnotationTapped(annotationId: String) {
        Handler(Looper.getMainLooper()).post {
            Timber.d("[AnnotationTap] JS bridge delivered annotation=%s", annotationId)
            onAnnotationTapped(annotationId)
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckAnnotationBridge"
    }
}
