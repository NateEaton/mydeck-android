package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import java.util.Locale

object WebViewScrollBridge {
    const val BRIDGE_NAME = "AndroidScroll"

    class BookmarkScrollInterface(
        private val onProgressChanged: (Float) -> Unit
    ) {
        @JavascriptInterface
        fun reportProgress(percentage: Float) {
            Handler(Looper.getMainLooper()).post {
                onProgressChanged(percentage)
            }
        }
    }

    /** Returns JS that restores scroll, observes reflows, and reports user scroll progress. */
    fun injectScrollManager(targetPercentage: Float, targetAnnotationId: String = ""): String {
        // Escape the annotation ID for safe embedding in a JS string literal
        val escapedAnnotationId = targetAnnotationId
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """
        (function() {
            if (window.__mdScrollManagerInstalled) return;
            window.__mdScrollManagerInstalled = true;
            var targetPercentage = ${"%.6f".format(Locale.US, targetPercentage)};
            var targetAnnotationId = "$escapedAnnotationId";
            var isUserScrolling = false;
            var initialPositionApplied = false;

            function getScrollMax() {
                return document.documentElement.scrollHeight - window.innerHeight;
            }

            function applyInitialPosition() {
                if (initialPositionApplied || isUserScrolling) return;
                if (targetAnnotationId) {
                    var el = document.getElementById('annotation-' + targetAnnotationId);
                    if (el) {
                        el.scrollIntoView({block: 'center', behavior: 'instant'});
                        initialPositionApplied = true;
                        return;
                    }
                    return;
                }
                if (targetPercentage > 0) {
                    var max = getScrollMax();
                    if (max > 0) {
                        window.scrollTo(0, max * targetPercentage);
                        initialPositionApplied = true;
                    }
                }
            }

            applyInitialPosition();
            new ResizeObserver(applyInitialPosition).observe(document.documentElement);

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
}
