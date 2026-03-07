package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.mydeck.app.domain.model.Annotation
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript bridge for creating and rendering text annotations (highlights) in WebView content.
 *
 * Architecture mirrors WebViewTypographyBridge and WebViewSearchBridge:
 * - Static functions generate JavaScript strings, injected via evaluateJavascript().
 * - AnnotationJsBridge is a @JavascriptInterface class registered on the WebView as "AnnotationInterface".
 */
object WebViewAnnotationBridge {

    /**
     * Injects the getXPath() and resolveXPathToPoint() utility functions into the page.
     * Must be called once after page load, before renderAnnotations() or injectSelectionObserver().
     */
    fun injectUtilities(): String = """
        (function() {
            if (window.__mydeckAnnotationsReady) return;
            window.__mydeckAnnotationsReady = true;
            window.__mydeckSelectionModeActive = false;

            // Compute an XPath expression for a DOM element node
            window.__getXPath = function(element) {
                if (element.id) return '//*[@id="' + element.id + '"]';
                var parts = [];
                var node = element;
                while (node && node.nodeType === Node.ELEMENT_NODE) {
                    var index = 1;
                    var sibling = node.previousSibling;
                    while (sibling) {
                        if (sibling.nodeType === Node.ELEMENT_NODE &&
                            sibling.nodeName === node.nodeName) { index++; }
                        sibling = sibling.previousSibling;
                    }
                    parts.unshift(node.nodeName.toLowerCase() + '[' + index + ']');
                    node = node.parentNode;
                }
                return parts.length ? ('/' + parts.join('/')) : '';
            };

            window.__annotationRoot = function() {
                return document.querySelector('.container') || document.body;
            };

            window.__stripServerBodyRoot = function(xpath) {
                if (!xpath) return null;

                var normalized = xpath
                    .replace(/^\/html\[\d+\]\/body\[\d+\]/, '/html/body')
                    .replace(/^\/html\[\d+\]\/body/, '/html/body')
                    .replace(/^\/html\/body\[\d+\]/, '/html/body')
                    .replace(/^\/body\[\d+\]/, '/body');

                if (normalized === '/html/body' || normalized === '/body') return '';
                if (normalized.indexOf('/html/body/') === 0) {
                    return normalized.substring('/html/body/'.length);
                }
                if (normalized.indexOf('/body/') === 0) {
                    return normalized.substring('/body/'.length);
                }
                return null;
            };

            window.__normalizeXPathForServer = function(xpath) {
                if (!xpath || typeof window.__getXPath === 'undefined') return xpath;
                var root = window.__annotationRoot();
                if (!root) return xpath;
                var rootXPath = window.__getXPath(root);
                if (!rootXPath) return xpath;
                if (xpath === rootXPath) return '/html/body';
                if (xpath.indexOf(rootXPath + '/') === 0) {
                    return '/html/body/' + xpath.substring(rootXPath.length + 1);
                }
                return xpath;
            };

            window.__resolveAnnotationElement = function(xpath) {
                if (!xpath) return null;

                function evaluate(targetXpath, contextNode) {
                    try {
                        var result = document.evaluate(
                            targetXpath,
                            contextNode || document,
                            null,
                            XPathResult.FIRST_ORDERED_NODE_TYPE,
                            null
                        );
                        return result.singleNodeValue;
                    } catch (e) {
                        return null;
                    }
                }

                var direct = evaluate(xpath, document);
                if (direct) return direct;

                var root = window.__annotationRoot();
                if (!root) return null;

                var bodyRelativePath = window.__stripServerBodyRoot(xpath);
                if (bodyRelativePath !== null) {
                    if (bodyRelativePath === '') return root;

                    var rootXPath = window.__getXPath(root);
                    var localXpath = rootXPath + '/' + bodyRelativePath;
                    var mapped = evaluate(localXpath, document);
                    if (mapped) return mapped;

                    var relativeMapped = evaluate('./' + bodyRelativePath, root);
                    if (relativeMapped) return relativeMapped;
                }

                return null;
            };

            // Resolve an XPath expression + character offset to a {node, offset} pair
            // suitable for Range.setStart / Range.setEnd.
            window.__resolveXPathToPoint = function(xpath, charOffset) {
                var element = window.__resolveAnnotationElement
                    ? window.__resolveAnnotationElement(xpath)
                    : null;
                if (!element) return null;
                var walker = document.createTreeWalker(
                    element, NodeFilter.SHOW_TEXT, null
                );
                var count = 0;
                var node;
                var lastNode = null;
                while ((node = walker.nextNode())) {
                    lastNode = node;
                    var len = node.textContent.length;
                    if (count + len >= charOffset) {
                        return { node: node, offset: charOffset - count };
                    }
                    count += len;
                }
                // Fallback: clamp to last text node
                if (lastNode) return { node: lastNode, offset: lastNode.textContent.length };
                return null;
            };
        })();
    """.trimIndent()

    /**
     * Generates JavaScript that clears any existing highlight marks and re-renders
     * all provided annotations as inline span elements with click listeners.
     */
    fun renderAnnotations(annotations: List<Annotation>): String {
        val annotationsJson = annotationsJson(annotations)
        return """
            (function() {
                // Remove existing highlight wrappers (restore original text nodes)
                document.querySelectorAll('.mydeck-annotation[data-annotation-id]').forEach(function(mark) {
                    if (!mark.parentNode) return;
                    var parent = mark.parentNode;
                    while (mark.firstChild) {
                        parent.insertBefore(mark.firstChild, mark);
                    }
                    parent.removeChild(mark);
                    parent.normalize();
                });

                if (typeof window.__resolveXPathToPoint === 'undefined') return;

                function intersectsTextNode(range, node) {
                    try {
                        return range.intersectsNode(node);
                    } catch (e) {
                        try {
                            var nodeRange = document.createRange();
                            nodeRange.selectNodeContents(node);
                            return range.compareBoundaryPoints(Range.END_TO_START, nodeRange) < 0 &&
                                range.compareBoundaryPoints(Range.START_TO_END, nodeRange) > 0;
                        } catch (inner) {
                            return false;
                        }
                    }
                }

                function collectTextNodes(range) {
                    var root = range.commonAncestorContainer.nodeType === Node.TEXT_NODE
                        ? range.commonAncestorContainer.parentNode
                        : range.commonAncestorContainer;
                    if (!root) return [];

                    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                        acceptNode: function(node) {
                            if (!node.textContent || node.textContent.length === 0) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            if (node.parentNode &&
                                node.parentNode.nodeType === Node.ELEMENT_NODE &&
                                node.parentNode.hasAttribute('data-annotation-id')) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            return intersectsTextNode(range, node)
                                ? NodeFilter.FILTER_ACCEPT
                                : NodeFilter.FILTER_REJECT;
                        }
                    });

                    var nodes = [];
                    var current;
                    while ((current = walker.nextNode())) {
                        nodes.push(current);
                    }
                    return nodes;
                }

                function createAnnotationNode(ann, text) {
                    var span = document.createElement('span');
                    span.className = 'mydeck-annotation';
                    span.setAttribute('data-annotation-id', ann.id);
                    span.setAttribute('data-color', ann.color);
                    span.style.backgroundColor = ann.color;
                    span.style.color = 'inherit';
                    span.style.cursor = 'pointer';
                    span.style.borderRadius = '2px';
                    span.style.padding = '0';
                    span.style.boxDecorationBreak = 'clone';
                    span.style.webkitBoxDecorationBreak = 'clone';
                    span.textContent = text;
                    span.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (typeof AnnotationInterface !== 'undefined') {
                            AnnotationInterface.onAnnotationClicked(ann.id);
                        }
                    });
                    return span;
                }

                function wrapTextNodeSegment(node, startOffset, endOffset, ann) {
                    if (!node || !node.parentNode || startOffset >= endOffset) return;
                    var text = node.textContent || '';
                    if (endOffset > text.length) endOffset = text.length;

                    var fragment = document.createDocumentFragment();
                    if (startOffset > 0) {
                        fragment.appendChild(document.createTextNode(text.substring(0, startOffset)));
                    }
                    fragment.appendChild(
                        createAnnotationNode(ann, text.substring(startOffset, endOffset))
                    );
                    if (endOffset < text.length) {
                        fragment.appendChild(document.createTextNode(text.substring(endOffset)));
                    }
                    node.parentNode.replaceChild(fragment, node);
                }

                var annotations = $annotationsJson;
                annotations.forEach(function(ann) {
                    try {
                        var startPt = window.__resolveXPathToPoint(ann.startXPath, ann.startOffset);
                        var endPt   = window.__resolveXPathToPoint(ann.endXPath,   ann.endOffset);
                        if (!startPt || !endPt) return;

                        var range = document.createRange();
                        range.setStart(startPt.node, startPt.offset);
                        range.setEnd(endPt.node, endPt.offset);
                        if (range.collapsed) return;

                        var textNodes = collectTextNodes(range);
                        if (textNodes.length === 0 && startPt.node === endPt.node) {
                            textNodes = [startPt.node];
                        }

                        textNodes.forEach(function(node) {
                            var segmentStart = node === startPt.node ? startPt.offset : 0;
                            var segmentEnd = node === endPt.node ? endPt.offset : node.textContent.length;
                            wrapTextNodeSegment(node, segmentStart, segmentEnd, ann);
                        });
                    } catch(e) {
                        console.warn('MyDeck: failed to render annotation ' + ann.id + ': ' + e.message);
                    }
                });
            })();
        """.trimIndent()
    }

    fun injectSelectionObserver(): String = """
        (function() {
            if (window.__mydeckSelectionObserverReady) return;
            window.__mydeckSelectionObserverReady = true;
            window.__mydeckLastSelectionKey = null;
            document.body.style.webkitUserSelect = 'text';
            document.body.style.userSelect = 'text';

            function emitSelection() {
                if (!window.__mydeckSelectionModeActive) return;
                var sel = window.getSelection();
                if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
                    window.__mydeckLastSelectionKey = null;
                    return;
                }
                var range = sel.getRangeAt(0);
                if (range.collapsed) return;

                var startContainer = range.startContainer;
                var endContainer   = range.endContainer;

                var startElement = startContainer.nodeType === Node.TEXT_NODE
                    ? startContainer.parentElement : startContainer;
                var endElement   = endContainer.nodeType === Node.TEXT_NODE
                    ? endContainer.parentElement : endContainer;

                if (!startElement || !endElement) return;

                var startOffset = range.startOffset;
                if (startContainer.nodeType === Node.TEXT_NODE) {
                    var walker = document.createTreeWalker(startElement, NodeFilter.SHOW_TEXT, null);
                    var n, count = 0;
                    while ((n = walker.nextNode())) {
                        if (n === startContainer) { startOffset = count + range.startOffset; break; }
                        count += n.textContent.length;
                    }
                }

                var endOffset = range.endOffset;
                if (endContainer.nodeType === Node.TEXT_NODE) {
                    var walker2 = document.createTreeWalker(endElement, NodeFilter.SHOW_TEXT, null);
                    var n2, count2 = 0;
                    while ((n2 = walker2.nextNode())) {
                        if (n2 === endContainer) { endOffset = count2 + range.endOffset; break; }
                        count2 += n2.textContent.length;
                    }
                }

                var startXPath = window.__getXPath ? window.__getXPath(startElement) : '';
                var endXPath   = window.__getXPath ? window.__getXPath(endElement) : '';
                if (window.__normalizeXPathForServer) {
                    startXPath = window.__normalizeXPathForServer(startXPath);
                    endXPath = window.__normalizeXPathForServer(endXPath);
                }
                var text = sel.toString().trim();
                var selectionKey = [startXPath, startOffset, endXPath, endOffset, text].join('|');

                if (startXPath && endXPath && text.length > 0 && typeof AnnotationInterface !== 'undefined') {
                    if (window.__mydeckLastSelectionKey === selectionKey) return;
                    window.__mydeckLastSelectionKey = selectionKey;
                    window.__mydeckSelectionModeActive = false;
                    AnnotationInterface.onTextSelected(
                        startXPath, startOffset, endXPath, endOffset, text
                    );
                    setTimeout(function() {
                        var activeSelection = window.getSelection();
                        if (activeSelection) {
                            activeSelection.removeAllRanges();
                        }
                    }, 0);
                }
            }

            window.__mydeckSelectionDebounce = null;
            document.addEventListener('selectionchange', function() {
                if (!window.__mydeckSelectionModeActive) {
                    window.__mydeckLastSelectionKey = null;
                    return;
                }
                if (window.__mydeckSelectionDebounce) {
                    clearTimeout(window.__mydeckSelectionDebounce);
                }
                window.__mydeckSelectionDebounce = setTimeout(emitSelection, 350);
            });
        })();
    """.trimIndent()

    fun setSelectionMode(isActive: Boolean): String = """
        (function() {
            window.__mydeckSelectionModeActive = ${if (isActive) "true" else "false"};
            window.__mydeckLastSelectionKey = null;
            if (!window.__mydeckSelectionModeActive) {
                var sel = window.getSelection ? window.getSelection() : null;
                if (sel) {
                    sel.removeAllRanges();
                }
            }
        })();
    """.trimIndent()

    /**
     * Generates JavaScript that scrolls the highlight with the given annotation ID into view.
     */
    fun scrollToAnnotation(annotationId: String): String {
        val escapedId = annotationId.replace("'", "\\'")
        return """
            (function() {
                var mark = document.querySelector('.mydeck-annotation[data-annotation-id="${escapedId}"]');
                if (!mark) return false;
                try {
                    mark.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
                } catch (e) {
                    mark.scrollIntoView();
                }
                return true;
            })();
        """.trimIndent()
    }

    private fun annotationsJson(annotations: List<Annotation>): String {
        val array = JSONArray()
        annotations.forEach { annotation ->
            array.put(
                JSONObject()
                    .put("id", annotation.id)
                    .put("startXPath", annotation.startSelector)
                    .put("startOffset", annotation.startOffset)
                    .put("endXPath", annotation.endSelector)
                    .put("endOffset", annotation.endOffset)
                    .put("color", annotation.color)
                    .put("text", annotation.text)
            )
        }
        return array.toString()
    }
}

/**
 * JavascriptInterface class registered on the WebView as "AnnotationInterface".
 * Callbacks arrive on a background thread; dispatched to main via Handler.
 */
class AnnotationJsBridge(
    private val onTextSelected: (startSelector: String, startOffset: Int, endSelector: String, endOffset: Int, text: String) -> Unit,
    private val onAnnotationClicked: (annotationId: String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onTextSelected(
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        text: String,
    ) {
        mainHandler.post {
            onTextSelected(startSelector, startOffset, endSelector, endOffset, text)
        }
    }

    @JavascriptInterface
    fun onAnnotationClicked(annotationId: String) {
        mainHandler.post { onAnnotationClicked(annotationId) }
    }
}
