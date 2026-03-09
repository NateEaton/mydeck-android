package com.mydeck.app.ui.detail

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.resume

object WebViewAnnotationBridge {
    private val json = Json { ignoreUnknownKeys = true }

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
