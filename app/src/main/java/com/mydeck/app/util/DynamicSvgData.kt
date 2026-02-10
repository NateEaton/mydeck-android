package com.mydeck.app.util

import android.util.Base64

/**
 * Generates a data URI for dynamic SVG thumbnails.
 *
 * @param title The bookmark title or URL used to generate the SVG
 */
object DynamicSvgUri {
    fun generate(title: String): String {
        val svg = DynamicSvgGenerator.generateSvg(title)
        val encoded = Base64.encodeToString(svg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "data:image/svg+xml;base64,$encoded"
    }
}
