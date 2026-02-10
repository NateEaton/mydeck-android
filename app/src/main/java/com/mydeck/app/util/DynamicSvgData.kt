package com.mydeck.app.util

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Generates URLs for dynamic SVG thumbnails.
 * Uses a custom scheme that we can intercept and serve dynamically.
 *
 * @param title The bookmark title used to generate the SVG
 */
object DynamicSvgUri {
    const val SCHEME = "dynamic"

    /**
     * Generate a custom URL for a dynamic SVG thumbnail.
     * Format: dynamic://<urlencoded-title>
     */
    fun generate(title: String): String {
        val encoded = URLEncoder.encode(title, "UTF-8")
        return "$SCHEME://$encoded"
    }

    /**
     * Decode a dynamic SVG URL back to the original title.
     */
    fun decode(url: String): String? {
        if (!url.startsWith("$SCHEME://")) return null
        val encoded = url.substring("$SCHEME://".length)
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }
}
