package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import timber.log.Timber

class WebViewAnnotationTapBridge(
    private val onAnnotationTapped: (String) -> Unit
) {
    private var lastAnnotationId: String? = null
    private var lastTapTime = 0L
    
    @JavascriptInterface
    fun onAnnotationTapped(annotationId: String) {
        Handler(Looper.getMainLooper()).post {
            // Simple deduplication - ignore if same annotation within 100ms
            val now = System.currentTimeMillis()
            if (lastAnnotationId == annotationId && (now - lastTapTime) < 100) {
                return@post
            }
            lastAnnotationId = annotationId
            lastTapTime = now
            
            Timber.d("[AnnotationTap] JS bridge delivered annotation=%s", annotationId)
            onAnnotationTapped(annotationId)
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckAnnotationBridge"
    }
}
