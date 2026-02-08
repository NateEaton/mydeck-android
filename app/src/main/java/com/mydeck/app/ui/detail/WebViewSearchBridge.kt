package com.mydeck.app.ui.detail

import android.webkit.WebView

/**
 * Bridge for executing search operations in WebView article content.
 * Provides JavaScript-based text search with highlighting and navigation.
 */
object WebViewSearchBridge {

    /**
     * Searches for text in the WebView and highlights all matches.
     * @param webView The WebView to search in
     * @param query The search query
     * @param callback Callback invoked with the total match count
     */
    fun searchAndHighlight(webView: WebView, query: String, callback: (Int) -> Unit) {
        if (query.isBlank()) {
            clearHighlights(webView)
            callback(0)
            return
        }

        val escapedQuery = query
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val javascript = """
            (function() {
                // Remove existing highlights
                var existingHighlights = document.querySelectorAll('.mydeck-search-match');
                existingHighlights.forEach(function(span) {
                    var parent = span.parentNode;
                    parent.replaceChild(document.createTextNode(span.textContent), span);
                    parent.normalize();
                });

                var query = '$escapedQuery';
                var container = document.querySelector('.container');
                if (!container) return 0;

                var matchCount = 0;
                var regexPattern = query.replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\${'$'}&');
                var regex = new RegExp(regexPattern, 'gi');

                function highlightNode(node) {
                    if (node.nodeType === Node.TEXT_NODE) {
                        var text = node.textContent;
                        var matches = text.match(regex);
                        if (matches && matches.length > 0) {
                            var fragment = document.createDocumentFragment();
                            var lastIndex = 0;
                            var match;
                            regex.lastIndex = 0;

                            while ((match = regex.exec(text)) !== null) {
                                // Add text before match
                                if (match.index > lastIndex) {
                                    fragment.appendChild(
                                        document.createTextNode(text.substring(lastIndex, match.index))
                                    );
                                }

                                // Add highlighted match
                                var span = document.createElement('span');
                                span.className = 'mydeck-search-match';
                                span.setAttribute('data-match-index', matchCount.toString());
                                span.textContent = match[0];
                                fragment.appendChild(span);

                                matchCount++;
                                lastIndex = regex.lastIndex;
                            }

                            // Add remaining text
                            if (lastIndex < text.length) {
                                fragment.appendChild(
                                    document.createTextNode(text.substring(lastIndex))
                                );
                            }

                            node.parentNode.replaceChild(fragment, node);
                        }
                    } else if (node.nodeType === Node.ELEMENT_NODE) {
                        // Don't highlight inside script, style, or existing search matches
                        var tagName = node.tagName.toLowerCase();
                        if (tagName !== 'script' && tagName !== 'style' &&
                            !node.classList.contains('mydeck-search-match')) {
                            var childNodes = Array.from(node.childNodes);
                            childNodes.forEach(highlightNode);
                        }
                    }
                }

                highlightNode(container);
                return matchCount;
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    /**
     * Highlights the current match and scrolls it into view.
     * @param webView The WebView to operate on
     * @param index The 0-based index of the match to highlight
     */
    fun highlightCurrentMatch(webView: WebView, index: Int) {
        val javascript = """
            (function() {
                // Remove current highlight from all matches
                var allMatches = document.querySelectorAll('.mydeck-search-match');
                allMatches.forEach(function(span) {
                    span.classList.remove('mydeck-search-current');
                });

                // Add current highlight to the specified match
                var targetMatch = document.querySelector(
                    '.mydeck-search-match[data-match-index="${index}"]'
                );

                if (targetMatch) {
                    targetMatch.classList.add('mydeck-search-current');
                    targetMatch.scrollIntoView({
                        block: 'center',
                        behavior: 'smooth'
                    });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript, null)
    }

    /**
     * Clears all search highlights from the WebView.
     * @param webView The WebView to clear highlights from
     */
    fun clearHighlights(webView: WebView) {
        val javascript = """
            (function() {
                var existingHighlights = document.querySelectorAll('.mydeck-search-match');
                existingHighlights.forEach(function(span) {
                    var parent = span.parentNode;
                    parent.replaceChild(document.createTextNode(span.textContent), span);
                    parent.normalize();
                });
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript, null)
    }
}
