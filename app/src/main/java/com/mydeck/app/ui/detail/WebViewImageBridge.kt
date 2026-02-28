package com.mydeck.app.ui.detail

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.mydeck.app.domain.model.ImageGalleryData
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * JavaScript-to-native bridge for image interactions in the reader WebView.
 *
 * Registered on the WebView as "MyDeckImageBridge".
 * Called from injected JavaScript when the user taps a qualifying image.
 */
class WebViewImageBridge(
    private val json: Json,
    private val onImageTapped: (ImageGalleryData) -> Unit,
) {
    @JavascriptInterface
    fun onImageTapped(jsonString: String) {
        // @JavascriptInterface methods run on a background thread.
        // Parse JSON here, then post the result to the main thread.
        try {
            val data = json.decodeFromString<ImageGalleryData>(jsonString)
            Handler(Looper.getMainLooper()).post {
                onImageTapped(data)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse image tap data")
        }
    }

    companion object {
        const val BRIDGE_NAME = "MyDeckImageBridge"

        /** Returns JS to inject into the WebView after page load. */
        fun injectImageInterceptor(): String {
            return """
                (function() {
                    'use strict';

                    var MIN_DIM = 48;
                    var MIN_COMBINED = 128;
                    var IMAGE_EXT_RE = /\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff|avif)(\?.*)?${'$'}/i;

                    var container = document.querySelector('.container');
                    if (!container) return;

                    // Capture all img elements in DOM order NOW (synchronous).
                    // This locks in the article order before any async loads complete.
                    var allImgElements = Array.from(container.querySelectorAll('img[src]'));

                    // Parallel sparse array: null = not qualifying, object = gallery entry.
                    // Index matches allImgElements, so DOM order is preserved no matter
                    // what order the 'load' events fire.
                    var slots = new Array(allImgElements.length).fill(null);

                    function isSmall(img) {
                        // Use only intrinsic (natural) dimensions — never the CSS/rendered
                        // size, which can make a 16px icon look huge when styled to fill
                        // its container.
                        var w = img.naturalWidth;
                        var h = img.naturalHeight;
                        if (w === 0 || h === 0) return true;
                        if (w < MIN_DIM || h < MIN_DIM) return true;
                        if (w < MIN_COMBINED && h < MIN_COMBINED) return true;
                        return false;
                    }

                    function classifyLink(a) {
                        if (!a || a.tagName !== 'A' || a.children.length !== 1) {
                            return { href: null, type: 'none' };
                        }
                        var href = a.href;
                        if (!href) return { href: null, type: 'none' };
                        var type = IMAGE_EXT_RE.test(href) ? 'image' : 'page';
                        return { href: href, type: type };
                    }

                    function buildRegistry() {
                        // Filter nulls while preserving slot order = DOM order.
                        return slots.filter(function(item) { return item !== null; });
                    }

                    function processImage(img, slotIndex) {
                        if (isSmall(img)) return;

                        var parent = img.parentElement;
                        var link = classifyLink(parent);

                        slots[slotIndex] = {
                            src: img.src,
                            alt: img.alt || '',
                            linkHref: link.href,
                            linkType: link.type
                        };

                        img.style.cursor = 'pointer';

                        var clickTarget = (link.href && parent.tagName === 'A') ? parent : img;
                        clickTarget.addEventListener('click', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            // Build registry at tap time so async-loaded images are included.
                            var registry = buildRegistry();
                            var entry = slots[slotIndex];
                            var currentIndex = registry.indexOf(entry);
                            if (currentIndex >= 0) {
                                MyDeckImageBridge.onImageTapped(JSON.stringify({
                                    images: registry,
                                    currentIndex: currentIndex
                                }));
                            }
                        }, true);
                    }

                    allImgElements.forEach(function(img, slotIndex) {
                        if (img.complete && img.naturalWidth > 0) {
                            processImage(img, slotIndex);
                        } else {
                            img.addEventListener('load', function() {
                                processImage(img, slotIndex);
                            }, { once: true });
                        }
                    });
                })();
            """.trimIndent()
        }

        /** Returns JS to find the image URL inside a link with the given href. */
        fun getImageUrlAtLink(linkHref: String): String {
            val escaped = linkHref
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
            return """
                (function() {
                    var links = document.querySelectorAll('a[href="${escaped}"]');
                    for (var i = 0; i < links.length; i++) {
                        var img = links[i].querySelector('img');
                        if (img) return img.src;
                    }
                    return '';
                })();
            """.trimIndent()
        }
    }
}
