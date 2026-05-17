package com.mydeck.app.ui.detail

import android.webkit.JavascriptInterface

/**
 * Reports whether the current text selection lies inside the article body container
 * (`#rd-article-content`). Reader views built without that container — PHOTO bookmarks
 * and any selectable text outside the article body (e.g. header description) — produce
 * `false` so the "Add Highlight" action mode item can be suppressed.
 */
class WebViewSelectionScopeBridge(
    private val onScopeChanged: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun setInsideArticleBody(inside: Boolean) {
        // JavascriptInterface methods run on a background thread; the listener is
        // responsible for any thread marshalling it needs.
        onScopeChanged(inside)
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckSelectionScopeBridge"

        fun injectSelectionScopeWatcher(): String = """
            (function() {
                if (window.__mydeckSelectionScopeInstalled) return;
                window.__mydeckSelectionScopeInstalled = true;

                var bridge = window['$BRIDGE_NAME'];
                if (!bridge || typeof bridge.setInsideArticleBody !== 'function') return;

                var lastInside = null;
                function notify(inside) {
                    if (inside === lastInside) return;
                    lastInside = inside;
                    try { bridge.setInsideArticleBody(inside); } catch (e) {}
                }

                function selectionInsideBody() {
                    var body = document.getElementById('rd-article-content');
                    if (!body) return false;
                    var sel = window.getSelection && window.getSelection();
                    if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return false;
                    var range = sel.getRangeAt(0);
                    var ancestor = range.commonAncestorContainer;
                    if (!ancestor) return false;
                    var node = ancestor.nodeType === Node.ELEMENT_NODE ? ancestor : ancestor.parentNode;
                    if (!node) return false;
                    return body.contains(node);
                }

                document.addEventListener('selectionchange', function() {
                    notify(selectionInsideBody());
                }, true);

                // Initial state (typically no selection on load).
                notify(selectionInsideBody());
            })();
        """.trimIndent()
    }
}
