package com.mydeck.app.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic dynamic SVG thumbnails for bookmarks.
 *
 * Based on the Readeck implementation, creates unique but consistent SVGs
 * for each bookmark using a hash-based seed. Same input always produces
 * the same output, but different inputs produce visually distinct results.
 */
object DynamicSvgGenerator {
    private const val SVG_WIDTH = 1024
    private const val SVG_HEIGHT = 576
    private const val CIRCLE_COUNT_MIN = 5
    private const val CIRCLE_COUNT_MAX = 10
    private const val CIRCLE_RADIUS_MIN = 30
    private const val CIRCLE_RADIUS_MAX = 130

    /**
     * Generate an SVG thumbnail as a string for the given bookmark title.
     *
     * @param title The bookmark title or URL to seed the random generation
     * @return SVG string that can be displayed as an image
     */
    fun generateSvg(title: String): String {
        val seed = title.hashCode().toLong()
        val random = DeterministicRandom(seed)

        // Generate colors
        val hue = random.nextInt(360)
        val saturation = 50 + random.nextInt(40) // 50-90%
        val lightness = 50 + random.nextInt(20) // 50-70%

        val color1 = hslToRgb(hue, saturation, lightness)
        val color2 = hslToRgb((hue + 60 + random.nextInt(60)) % 360, saturation, lightness + 10)

        // Build SVG
        val circles = mutableListOf<CircleData>()
        val circleCount = CIRCLE_COUNT_MIN + random.nextInt(CIRCLE_COUNT_MAX - CIRCLE_COUNT_MIN + 1)

        repeat(circleCount) {
            val cx = random.nextInt(SVG_WIDTH)
            val cy = random.nextInt(SVG_HEIGHT)
            val radius = CIRCLE_RADIUS_MIN + random.nextInt(CIRCLE_RADIUS_MAX - CIRCLE_RADIUS_MIN + 1)
            val opacity = (10 + random.nextInt(16)) / 100f // 0.10-0.25
            circles.add(CircleData(cx, cy, radius, opacity))
        }

        return buildSvgString(color1, color2, circles)
    }

    private fun buildSvgString(color1: String, color2: String, circles: List<CircleData>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n")
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $SVG_WIDTH $SVG_HEIGHT">""")
        sb.append("\n")
        sb.append("""<defs>""")
        sb.append("\n")
        sb.append("""<linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">""")
        sb.append("\n")
        sb.append("""<stop offset="0%" style="stop-color:$color1;stop-opacity:1" />""")
        sb.append("\n")
        sb.append("""<stop offset="100%" style="stop-color:$color2;stop-opacity:1" />""")
        sb.append("\n")
        sb.append("""</linearGradient>""")
        sb.append("\n")
        sb.append("""</defs>""")
        sb.append("\n")
        sb.append("""<rect width="$SVG_WIDTH" height="$SVG_HEIGHT" fill="url(#grad)"/>""")
        sb.append("\n")

        // Add circles
        for (circle in circles) {
            sb.append("""<circle cx="${circle.cx}" cy="${circle.cy}" r="${circle.radius}" """)
            sb.append("""fill="white" opacity="${circle.opacity}"/>""")
            sb.append("\n")
        }

        sb.append("""</svg>""")
        return sb.toString()
    }

    /**
     * Convert HSL color to RGB hex string.
     */
    private fun hslToRgb(h: Int, s: Int, l: Int): String {
        val hNorm = h / 360f
        val sNorm = s / 100f
        val lNorm = l / 100f

        val c = (1f - kotlin.math.abs(2f * lNorm - 1f)) * sNorm
        val x = c * (1f - kotlin.math.abs((hNorm * 6f) % 2f - 1f))
        val m = lNorm - c / 2f

        val (r, g, b) = when {
            hNorm < 1f / 6f -> Triple(c, x, 0f)
            hNorm < 2f / 6f -> Triple(x, c, 0f)
            hNorm < 3f / 6f -> Triple(0f, c, x)
            hNorm < 4f / 6f -> Triple(0f, x, c)
            hNorm < 5f / 6f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val rInt = ((r + m) * 255).toInt()
        val gInt = ((g + m) * 255).toInt()
        val bInt = ((b + m) * 255).toInt()

        return String.format("#%02X%02X%02X", rInt, gInt, bInt)
    }

    /**
     * Deterministic random number generator seeded from bookmark data.
     */
    private class DeterministicRandom(seed: Long) {
        private var state: Long = seed

        fun nextInt(bound: Int): Int {
            if (bound <= 0) throw IllegalArgumentException("bound must be positive")
            state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            return abs((state shr 16).toInt()) % bound
        }
    }

    private data class CircleData(
        val cx: Int,
        val cy: Int,
        val radius: Int,
        val opacity: Float
    )
}
