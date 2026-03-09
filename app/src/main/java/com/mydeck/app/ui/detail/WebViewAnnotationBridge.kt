package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.mydeck.app.domain.model.SelectionData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.resume

object WebViewAnnotationBridge {
    private val json = Json { ignoreUnknownKeys = true }

    const val BRIDGE_NAME = "MyDeckAnnotationBridge"

    fun scrollToAnnotation(webView: WebView, annotationId: String) {
        webView.evaluateJavascript(
            """
                (function() {
                    var target = document.getElementById('annotation-${annotationId.escapeForJavascript()}');
                    if (!target) return false;
                    target.scrollIntoView({
                        block: 'center',
                        behavior: 'smooth'
                    });
                    return true;
                })();
            """.trimIndent(),
            null
        )
    }

    fun getRenderedAnnotations(
        webView: WebView,
        callback: (List<RenderedAnnotation>) -> Unit
    ) {
        webView.evaluateJavascript(
            """
                (function() {
                    var noteMap = {};
                    var result = [];

                    Array.from(
                        document.querySelectorAll('rd-annotation[data-annotation-note][data-annotation-id-value]')
                    ).forEach(function(node) {
                        var annotationId = node.getAttribute('data-annotation-id-value');
                        if (!annotationId) return;
                        var note = (node.getAttribute('title') || '').trim();
                        noteMap[annotationId] = note || null;
                    });

                    Array.from(
                        document.querySelectorAll('rd-annotation[id][data-annotation-id-value]')
                    ).forEach(function(node, index) {
                        var annotationId = node.getAttribute('data-annotation-id-value');
                        if (!annotationId) return;
                        var text = (node.textContent || '').trim();
                        if (!text) return;

                        result.push({
                            id: annotationId,
                            text: text,
                            color: (node.getAttribute('data-annotation-color') || 'yellow').trim() || 'yellow',
                            note: noteMap[annotationId] || null,
                            position: index
                        });
                    });

                    return JSON.stringify(result);
                })();
            """.trimIndent()
        ) { result ->
            val annotations = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                if (decoded.isBlank() || decoded == "null") {
                    emptyList()
                } else {
                    json.decodeFromString<List<RenderedAnnotation>>(decoded)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode rendered annotations")
                emptyList()
            }
            callback(annotations)
        }
    }

    fun getRenderedAnnotation(
        webView: WebView,
        annotationId: String,
        callback: (RenderedAnnotation?) -> Unit
    ) {
        webView.evaluateJavascript(
            """
                (function() {
                    var target = document.getElementById('annotation-${annotationId.escapeForJavascript()}');
                    if (!target) return null;

                    var noteNode = document.querySelector(
                        'rd-annotation[data-annotation-note][data-annotation-id-value="${annotationId.escapeForJavascript()}"]'
                    );
                    var allAnnotations = Array.from(
                        document.querySelectorAll('rd-annotation[id][data-annotation-id-value]')
                    );
                    var position = allAnnotations.findIndex(function(node) {
                        return node.getAttribute('data-annotation-id-value') === '${annotationId.escapeForJavascript()}';
                    });

                    return JSON.stringify({
                        id: '${annotationId.escapeForJavascript()}',
                        text: (target.textContent || '').trim(),
                        color: (target.getAttribute('data-annotation-color') || 'yellow').trim() || 'yellow',
                        note: noteNode ? ((noteNode.getAttribute('title') || '').trim() || null) : null,
                        position: position < 0 ? 0 : position
                    });
                })();
            """.trimIndent()
        ) { result ->
            val annotation = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                if (decoded.isBlank() || decoded == "null") {
                    null
                } else {
                    json.decodeFromString<RenderedAnnotation>(decoded)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode rendered annotation")
                null
            }
            callback(annotation)
        }
    }

    fun captureSelection(
        webView: WebView,
        callback: (SelectionData?) -> Unit
    ) {
        webView.evaluateJavascript(
            """
                (function() {
                    if (typeof window.mydeckCaptureSelection !== 'function') {
                        return null;
                    }
                    var selection = window.mydeckCaptureSelection();
                    return selection ? JSON.stringify(selection) : null;
                })();
            """.trimIndent()
        ) { result ->
            val selectionData = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                if (decoded.isBlank() || decoded == "null") {
                    null
                } else {
                    json.decodeFromString<SelectionData>(decoded)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode selection data")
                null
            }
            callback(selectionData)
        }
    }

    fun injectAnnotationInteractions(): String {
        return """
            (function() {
                'use strict';

                var container = document.querySelector('.container');
                if (!container) return;

                function getNodeTextLength(node) {
                    if (!node) return 0;
                    if (node.nodeType === Node.TEXT_NODE) {
                        return (node.textContent || '').length;
                    }

                    var total = 0;
                    Array.from(node.childNodes || []).forEach(function(child) {
                        total += getNodeTextLength(child);
                    });
                    return total;
                }

                function getNearestElement(node) {
                    var current = node;
                    while (current && current.nodeType !== Node.ELEMENT_NODE) {
                        current = current.parentNode;
                    }
                    return current;
                }

                function getXPathForElement(element) {
                    if (!element || !container.contains(element)) return null;

                    var segments = [];
                    var current = element;
                    while (current && current !== container) {
                        var tagName = (current.tagName || '').toLowerCase();
                        if (!tagName) return null;

                        var index = 1;
                        var sibling = current.previousElementSibling;
                        while (sibling) {
                            if (sibling.tagName === current.tagName) {
                                index += 1;
                            }
                            sibling = sibling.previousElementSibling;
                        }

                        segments.unshift(tagName + '[' + index + ']');
                        current = current.parentElement;
                    }

                    return '/' + segments.join('/');
                }

                function getOffsetWithinElement(element, targetNode, targetOffset) {
                    var total = 0;
                    var found = false;

                    function walk(node) {
                        if (!node || found) return;

                        if (node === targetNode) {
                            if (node.nodeType === Node.TEXT_NODE) {
                                total += Math.min(targetOffset, (node.textContent || '').length);
                            } else {
                                Array.from(node.childNodes || [])
                                    .slice(0, Math.min(targetOffset, node.childNodes.length))
                                    .forEach(function(child) {
                                        total += getNodeTextLength(child);
                                    });
                            }
                            found = true;
                            return;
                        }

                        if (node.nodeType === Node.TEXT_NODE) {
                            total += (node.textContent || '').length;
                            return;
                        }

                        Array.from(node.childNodes || []).forEach(function(child) {
                            walk(child);
                        });
                    }

                    walk(element);
                    return total;
                }

                function getSelectedAnnotationIds(range) {
                    var annotationIds = [];

                    Array.from(
                        container.querySelectorAll('rd-annotation[id][data-annotation-id-value]')
                    ).forEach(function(node) {
                        var annotationId = node.getAttribute('data-annotation-id-value');
                        if (!annotationId) {
                            return;
                        }

                        try {
                            if (range.intersectsNode(node)) {
                                annotationIds.push(annotationId);
                            }
                        } catch (error) {
                            // Ignore nodes the browser refuses to compare.
                        }
                    });

                    return Array.from(new Set(annotationIds));
                }

                window.mydeckCaptureSelection = function() {
                    var selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
                        return null;
                    }

                    var range = selection.getRangeAt(0);
                    var text = (selection.toString() || '').trim();
                    if (!text) {
                        return null;
                    }

                    var startElement = getNearestElement(range.startContainer);
                    var endElement = getNearestElement(range.endContainer);
                    if (!startElement || !endElement) {
                        return null;
                    }
                    if (!container.contains(startElement) || !container.contains(endElement)) {
                        return null;
                    }

                    var startSelector = getXPathForElement(startElement);
                    var endSelector = getXPathForElement(endElement);
                    if (!startSelector || !endSelector) {
                        return null;
                    }

                    return {
                        text: text,
                        startSelector: startSelector,
                        startOffset: getOffsetWithinElement(
                            startElement,
                            range.startContainer,
                            range.startOffset
                        ),
                        endSelector: endSelector,
                        endOffset: getOffsetWithinElement(
                            endElement,
                            range.endContainer,
                            range.endOffset
                        ),
                        selectedAnnotationIds: getSelectedAnnotationIds(range)
                    };
                };

                Array.from(
                    container.querySelectorAll('rd-annotation[id][data-annotation-id-value]')
                ).forEach(function(node) {
                    if (node.dataset.mydeckAnnotationBound === '1') {
                        return;
                    }

                    node.dataset.mydeckAnnotationBound = '1';
                    node.addEventListener('click', function(event) {
                        var annotationId = node.getAttribute('data-annotation-id-value');
                        if (!annotationId || typeof MyDeckAnnotationBridge === 'undefined') {
                            return;
                        }

                        event.preventDefault();
                        event.stopPropagation();
                        MyDeckAnnotationBridge.onAnnotationClicked(annotationId);
                    }, true);
                });
            })();
        """.trimIndent()
    }

    suspend fun getAnnotationViewportInfo(
        webView: WebView,
        annotationId: String
    ): AnnotationViewportInfo? = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(
            """
                (function() {
                    var target = document.getElementById('annotation-${annotationId.escapeForJavascript()}');
                    if (!target) return null;

                    var rect = target.getBoundingClientRect();
                    var documentHeight = Math.max(
                        document.documentElement ? document.documentElement.scrollHeight : 0,
                        document.body ? document.body.scrollHeight : 0,
                        1
                    );
                    var absoluteCenter = rect.top + window.scrollY + (rect.height / 2);
                    return JSON.stringify({
                        centerRatio: Math.max(0, Math.min(1, absoluteCenter / documentHeight))
                    });
                })();
            """.trimIndent()
        ) { result ->
            val viewportInfo = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                if (decoded.isBlank() || decoded == "null") {
                    null
                } else {
                    json.decodeFromString<AnnotationViewportInfo>(decoded)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode annotation viewport info")
                null
            }

            if (continuation.isActive) {
                continuation.resume(viewportInfo)
            }
        }
    }

    private fun String.escapeForJavascript(): String {
        return replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    @kotlinx.serialization.Serializable
    data class AnnotationViewportInfo(
        val centerRatio: Float
    )

    @kotlinx.serialization.Serializable
    data class RenderedAnnotation(
        val id: String,
        val text: String,
        val color: String = "yellow",
        val note: String? = null,
        val position: Int = Int.MAX_VALUE
    )
}

class WebViewAnnotationCallbackBridge(
    private val onAnnotationClicked: (String) -> Unit
) {
    @JavascriptInterface
    fun onAnnotationClicked(annotationId: String) {
        Handler(Looper.getMainLooper()).post {
            onAnnotationClicked(annotationId)
        }
    }
}
