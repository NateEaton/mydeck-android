package com.mydeck.app.util

/**
 * Custom data class for dynamic SVG generation.
 * Used with Coil's ImageLoader to generate and cache dynamic SVG thumbnails.
 *
 * @param title The bookmark title or URL used to generate the SVG
 */
data class DynamicSvgData(val title: String) {
    override fun toString(): String = "dynamic://${title.hashCode()}"
}
