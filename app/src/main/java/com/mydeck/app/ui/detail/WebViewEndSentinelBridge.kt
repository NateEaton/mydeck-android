package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class WebViewEndSentinelBridge(
    private val onEndVisibleChanged: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun onEndVisible(visible: Boolean) {
        Handler(Looper.getMainLooper()).post {
            onEndVisibleChanged(visible)
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckEndSentinel"

        fun injectEndSentinelObserver(): String = """
            (function() {
                if (window.__mydeckEndSentinelObserverInstalled) return;
                window.__mydeckEndSentinelObserverInstalled = true;

                var sentinel = document.getElementById('mydeck-end-sentinel');
                var lastVisible = null;

                function report(visible) {
                    if (lastVisible === visible) return;
                    lastVisible = visible;
                    if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onEndVisible) {
                        window.$BRIDGE_NAME.onEndVisible(visible);
                    }
                }

                if (!sentinel || !('IntersectionObserver' in window)) {
                    report(false);
                    return;
                }

                var observer = new IntersectionObserver(function(entries) {
                    var visible = entries.some(function(entry) {
                        return entry.isIntersecting;
                    });
                    report(visible);
                }, { root: null, threshold: 0 });

                observer.observe(sentinel);
                window.__mydeckEndSentinelObserver = observer;
            })();
        """.trimIndent()
    }
}
