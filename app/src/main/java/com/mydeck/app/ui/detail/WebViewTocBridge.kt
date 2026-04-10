package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import timber.log.Timber

/**
 * JavaScript-to-native bridge for fragment link (TOC) navigation in the reader WebView.
 *
 * Registered on the WebView as "MyDeckTocBridge".
 * Called from injected JavaScript when the user taps an in-page anchor link.
 */
class WebViewTocBridge(
    private val onFragmentScroll: (absoluteY: Int) -> Unit,
) {
    @JavascriptInterface
    fun onFragmentLinkTapped(absoluteY: Int) {
        // @JavascriptInterface methods run on a background thread.
        Handler(Looper.getMainLooper()).post {
            Timber.d("[TocNav] Fragment link tapped, absoluteY=%d", absoluteY)
            onFragmentScroll(absoluteY)
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckTocBridge"

        /**
         * Returns JavaScript to inject after page load.
         *
         * Attaches click interceptors to all <a href="#..."> elements in the
         * document. On click, prevents the WebView's own fragment-scroll attempt
         * (which is a no-op because Compose owns the scroll), resolves the target
         * element's absolute Y position, and reports it to Kotlin via the bridge.
         *
         * Uses an attribute selector `[id="targetId"]` rather than querySelector
         * with a CSS ID selector (`#uY.XlwQ.foo`) because dots in the Readeck-
         * prefixed IDs would be misinterpreted as class selectors.
         */
        fun injectFragmentLinkInterceptor(): String {
            return """
                (function() {
                    'use strict';
                    if (window.mydeckTocInstalled) return;
                    window.mydeckTocInstalled = true;

                    var tocBridge = window['${BRIDGE_NAME}'];
                    console.log('[TocNav] Injecting fragment link interceptor, bridge=' + (tocBridge ? 'found' : 'MISSING'));
                    if (!tocBridge) return;

                    var container = document.querySelector('.container') || document.body;
                    container.addEventListener('click', function(e) {
                        var target = e.target;
                        // Walk up from the tapped element to find an anchor link with a fragment href.
                        while (target && target !== container) {
                            if (target.tagName && target.tagName.toLowerCase() === 'a') {
                                var href = target.getAttribute('href');
                                if (href && href.charAt(0) === '#' && href.length > 1) {
                                    break;
                                }
                                return; // anchor without fragment href — let it propagate normally
                            }
                            target = target.parentElement;
                        }
                        if (!target || target === container) return;

                        var href = target.getAttribute('href');
                        console.log('[TocNav] Fragment link clicked href=' + href);
                        var targetId = href.slice(1);
                        // Try exact id match first.
                        var el = document.querySelector('[id="' + targetId + '"]');
                        if (!el) {
                            // Readeck strips id attributes from headings but keeps fragment hrefs
                            // in the TOC. Fall back to matching headings by normalized text.
                            // The fragment suffix (after stripping the per-article prefix like
                            // "eK.sfkN.") encodes the heading text with underscores for spaces
                            // and other chars removed, e.g. "CutTrim_With_Re-encoding" matches
                            // the heading "Cut/Trim With Re-encoding".
                            var dotIdx = targetId.indexOf('.');
                            var secondDotIdx = dotIdx >= 0 ? targetId.indexOf('.', dotIdx + 1) : -1;
                            var semantic = secondDotIdx >= 0 ? targetId.slice(secondDotIdx + 1) : targetId;
                            var normalizedTarget = semantic.replace(/_/g, ' ').toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim();
                            var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                            for (var hi = 0; hi < headings.length; hi++) {
                                var hText = (headings[hi].textContent || '').toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim();
                                if (hText === normalizedTarget) {
                                    el = headings[hi];
                                    console.log('[TocNav] Matched heading by text for id=' + targetId);
                                    break;
                                }
                            }
                        }
                        if (!el) {
                            console.log('[TocNav] Target element not found for id=' + targetId);
                            return;
                        }
                        e.preventDefault();
                        e.stopPropagation();
                        var rect = el.getBoundingClientRect();
                        // window.scrollY is always 0 because Compose owns the scroll.
                        // Multiply by devicePixelRatio to convert CSS pixels to physical
                        // pixels, which is what scrollState.animateScrollTo expects.
                        var absoluteY = Math.round((rect.top + window.scrollY) * window.devicePixelRatio);
                        console.log('[TocNav] Scrolling to absoluteY=' + absoluteY);
                        tocBridge.onFragmentLinkTapped(absoluteY);
                    }, false);

                    var links = document.querySelectorAll('a[href^="#"]');
                    console.log('[TocNav] Found ' + links.length + ' fragment links');
                })();
            """.trimIndent()
        }
    }
}
