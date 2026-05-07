package com.mydeck.app.ui.detail

import android.webkit.JavascriptInterface
import java.util.Locale

object WebViewScrollBridge {
    const val BRIDGE_NAME = "AndroidScroll"

    class BookmarkScrollInterface(
        private val onProgressChanged: (Float) -> Unit
    ) {
        @JavascriptInterface
        fun reportProgress(percentage: Float) {
            onProgressChanged(percentage)
        }
    }

    /** Returns JS that restores scroll, observes reflows, and reports user scroll progress. */
    fun injectScrollManager(targetPercentage: Float): String = """
        (function() {
            if (window.__mdScrollManagerInstalled) return;
            window.__mdScrollManagerInstalled = true;
            var targetPercentage = ${"%.6f".format(Locale.US, targetPercentage)};
            var isUserScrolling = false;

            function getScrollMax() {
                return document.documentElement.scrollHeight - window.innerHeight;
            }

            function maintainScrollPosition() {
                if (!isUserScrolling && targetPercentage > 0) {
                    var max = getScrollMax();
                    if (max > 0) window.scrollTo(0, max * targetPercentage);
                }
            }

            maintainScrollPosition();
            new ResizeObserver(maintainScrollPosition).observe(document.documentElement);

            window.addEventListener('scroll', function() {
                if (!isUserScrolling) return;
                var max = getScrollMax();
                var percentage = max > 0 ? window.scrollY / max : 0;
                targetPercentage = percentage;
                if (window.AndroidScroll && window.AndroidScroll.reportProgress) {
                    window.AndroidScroll.reportProgress(percentage);
                }
            });

            ['touchstart', 'wheel', 'pointerdown'].forEach(function(evt) {
                window.addEventListener(evt, function() { isUserScrolling = true; }, { passive: true });
            });
        })();
    """.trimIndent()
}
