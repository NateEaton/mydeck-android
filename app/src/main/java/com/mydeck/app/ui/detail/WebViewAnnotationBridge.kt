package com.mydeck.app.ui.detail

import android.webkit.WebView
import com.mydeck.app.domain.model.SelectionData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.resume

object WebViewAnnotationBridge {
    private val json = Json { ignoreUnknownKeys = true }
    private const val TAP_LOG_PREFIX = "[AnnotationTap]"

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

    fun getAnnotationIdAtPoint(
        webView: WebView,
        x: Float,
        y: Float,
        callback: (String?) -> Unit
    ) {
        val density = webView.resources.displayMetrics.density.toDouble()
        val webViewScale = webView.scale.toDouble().takeIf { it > 0.0 } ?: 1.0
        webView.evaluateJavascript(
            """
                (function() {
                    function extractAnnotationId(target) {
                        var current = target;
                        while (current) {
                            if (
                                current.tagName &&
                                current.tagName.toLowerCase() === 'rd-annotation' &&
                                current.hasAttribute('data-annotation-id-value')
                            ) {
                                return current.getAttribute('data-annotation-id-value');
                            }
                            current = current.parentElement;
                        }
                        return null;
                    }

                    function summarizeTarget(target) {
                        if (!target) {
                            return {
                                tagName: null,
                                elementId: null,
                                annotationId: null,
                                text: null
                            };
                        }

                        return {
                            tagName: target.tagName ? target.tagName.toLowerCase() : null,
                            elementId: target.id || null,
                            annotationId: extractAnnotationId(target),
                            text: ((target.textContent || '').trim().slice(0, 80)) || null
                        };
                    }

                    var pointX = ${x.toDouble()};
                    var pointY = ${y.toDouble()};
                    var density = ${density};
                    var webViewScale = ${webViewScale};
                    var strategies = [
                        { label: 'raw', x: pointX, y: pointY },
                        { label: 'webviewScale', x: pointX / webViewScale, y: pointY / webViewScale },
                        { label: 'density', x: pointX / density, y: pointY / density },
                        { label: 'densityAndScale', x: pointX / (density * webViewScale), y: pointY / (density * webViewScale) }
                    ];

                    var candidates = strategies.map(function(strategy) {
                        var target = document.elementFromPoint(strategy.x, strategy.y);
                        var summary = summarizeTarget(target);
                        return {
                            label: strategy.label,
                            x: strategy.x,
                            y: strategy.y,
                            tagName: summary.tagName,
                            elementId: summary.elementId,
                            annotationId: summary.annotationId,
                            text: summary.text
                        };
                    });

                    var match = candidates.find(function(candidate) {
                        return !!candidate.annotationId;
                    }) || null;

                    return JSON.stringify({
                        annotationId: match ? match.annotationId : null,
                        matchedStrategy: match ? match.label : null,
                        rawX: pointX,
                        rawY: pointY,
                        density: density,
                        webViewScale: webViewScale,
                        scrollX: window.scrollX || 0,
                        scrollY: window.scrollY || 0,
                        candidates: candidates
                    });
                })();
            """.trimIndent()
        ) { result ->
            val hitResult = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                if (decoded.isBlank() || decoded == "null") {
                    null
                } else {
                    json.decodeFromString<AnnotationHitTestResult>(decoded)
                }
            } catch (e: Exception) {
                Timber.w(e, "%s Failed to decode annotation id at touch point. Raw=%s", TAP_LOG_PREFIX, result)
                null
            }

            if (hitResult == null) {
                Timber.d(
                    "%s No hit-test result for point x=%.1f y=%.1f",
                    TAP_LOG_PREFIX,
                    x,
                    y
                )
                callback(null)
                return@evaluateJavascript
            }

            Timber.d(
                "%s point=(%.1f, %.1f) density=%.2f webViewScale=%.2f matched=%s via=%s candidates=%s",
                TAP_LOG_PREFIX,
                hitResult.rawX,
                hitResult.rawY,
                hitResult.density,
                hitResult.webViewScale,
                hitResult.annotationId ?: "none",
                hitResult.matchedStrategy ?: "none",
                hitResult.candidates.joinToString(" | ") { candidate ->
                    "${candidate.label}:${candidate.annotationId ?: "-"}:${candidate.tagName ?: "-"}:${candidate.elementId ?: "-"}"
                }
            )
            callback(hitResult.annotationId)
        }
    }

    fun consumePendingTappedAnnotation(
        webView: WebView,
        callback: (String?) -> Unit
    ) {
        webView.evaluateJavascript(
            """
                (function() {
                    if (typeof window.mydeckConsumeTappedAnnotation !== 'function') {
                        return null;
                    }
                    return window.mydeckConsumeTappedAnnotation();
                })();
            """.trimIndent()
        ) { result ->
            val annotationId = try {
                val decoded = WebViewImageBridge.decodeJsString(result)
                decoded.takeUnless { it.isBlank() || it == "null" }
            } catch (e: Exception) {
                Timber.w(e, "%s Failed to decode pending tapped annotation. Raw=%s", TAP_LOG_PREFIX, result)
                null
            }
            Timber.d("%s consumePendingTappedAnnotation=%s", TAP_LOG_PREFIX, annotationId ?: "none")
            callback(annotationId)
        }
    }

    fun injectAnnotationInteractions(): String {
        return """
            (function() {
                'use strict';

                var container = document.querySelector('.container');
                if (!container) return;
                var annotationBridge = window['${WebViewAnnotationTapBridge.BRIDGE_NAME}'];

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

                if (!window.mydeckAnnotationTapInstalled) {
                    window.mydeckAnnotationTapInstalled = true;
                    window.mydeckLastAnnotationTap = {
                        id: null,
                        time: 0
                    };
                    window.mydeckPendingTappedAnnotation = {
                        id: null,
                        time: 0
                    };

                    window.mydeckConsumeTappedAnnotation = function() {
                        var pending = window.mydeckPendingTappedAnnotation;
                        window.mydeckPendingTappedAnnotation = {
                            id: null,
                            time: 0
                        };
                        if (!pending || !pending.id) {
                            return null;
                        }
                        if ((Date.now() - pending.time) > 1200) {
                            return null;
                        }
                        return pending.id;
                    };

                    function findTappedAnnotation(node) {
                        var target = node;
                        while (target) {
                            if (
                                target.tagName &&
                                target.tagName.toLowerCase() === 'rd-annotation' &&
                                target.hasAttribute('data-annotation-id-value')
                            ) {
                                return target;
                            }
                            target = target.parentElement;
                        }
                        return null;
                    }

                    function hasActiveSelection() {
                        var selection = window.getSelection();
                        return !!(
                            selection &&
                            !selection.isCollapsed &&
                            (selection.toString() || '').trim()
                        );
                    }

                    function dispatchAnnotationTap(kind, event) {
                        if (hasActiveSelection()) {
                            console.log('${TAP_LOG_PREFIX} js=' + kind + ' skipped=active-selection');
                            return;
                        }

                        var annotationNode = findTappedAnnotation(event && event.target);
                        if (!annotationNode) {
                            return;
                        }

                        var annotationId = annotationNode.getAttribute('data-annotation-id-value');
                        if (!annotationId) {
                            return;
                        }

                        var now = Date.now();
                        var lastTap = window.mydeckLastAnnotationTap || { id: null, time: 0 };
                        if (lastTap.id === annotationId && (now - lastTap.time) < 400) {
                            console.log('${TAP_LOG_PREFIX} js=' + kind + ' deduped id=' + annotationId);
                            return;
                        }
                        window.mydeckLastAnnotationTap = { id: annotationId, time: now };
                        window.mydeckPendingTappedAnnotation = { id: annotationId, time: now };

                        console.log(
                            '${TAP_LOG_PREFIX} js=' + kind +
                            ' id=' + annotationId +
                            ' text=' + ((annotationNode.textContent || '').trim().slice(0, 80))
                        );

                        if (annotationBridge && typeof annotationBridge.onAnnotationTapped === 'function') {
                            annotationBridge.onAnnotationTapped(annotationId);
                            event.preventDefault();
                            event.stopPropagation();
                        } else {
                            console.log('${TAP_LOG_PREFIX} bridge-missing id=' + annotationId);
                        }
                    }

                    container.addEventListener('click', function(event) {
                        dispatchAnnotationTap('click', event);
                    }, true);

                    container.addEventListener('touchend', function(event) {
                        dispatchAnnotationTap('touchend', event);
                    }, true);
                }

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
    private data class AnnotationHitTestResult(
        val annotationId: String? = null,
        val matchedStrategy: String? = null,
        val rawX: Float = 0f,
        val rawY: Float = 0f,
        val density: Float = 1f,
        val webViewScale: Float = 1f,
        val scrollX: Float = 0f,
        val scrollY: Float = 0f,
        val candidates: List<AnnotationHitCandidate> = emptyList()
    )

    @kotlinx.serialization.Serializable
    private data class AnnotationHitCandidate(
        val label: String,
        val x: Float,
        val y: Float,
        val tagName: String? = null,
        val elementId: String? = null,
        val annotationId: String? = null,
        val text: String? = null
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
