package com.mydeck.app.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.random.Random

/**
 * A refined custom Drawable that generates a deterministic background gradient
 * and bubbles based on the Readeck "random_svg.go" logic.
 */
class ReadeckPlaceholderDrawable(
    private val seedString: String // The Bookmark URL
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Seed treatment: Readeck treats name as big-endian uint64
    // simplified here with a hash-based seed for Kotlin's Random
    private val seed = fnv1aHash(seedString)
    private val random = Random(seed)
    
    // 1. Pre-calculate Gradient Colors (HSL style)
    // Top Hue: 10-39 (Orange/Brown), Top Sat: 20-74%, Top Lightness: 70%
    private val topHue = random.nextInt(30) + 10f
    private val topSat = (random.nextInt(55) + 20) / 100f
    private val topColor = ColorUtils.HSLToColor(floatArrayOf(topHue, topSat, 0.7f))

    // Bottom Hue: 190-219 (Blue), Bottom Sat: 20-89%, Bottom Lightness: 70%
    private val bottomHue = random.nextInt(30) + 190f
    private val bottomSat = (random.nextInt(70) + 20) / 100f
    private val bottomColor = ColorUtils.HSLToColor(floatArrayOf(bottomHue, bottomSat, 0.7f))

    // 2. Pre-calculate Circle Properties
    private data class Circle(val cx: Float, val cy: Float, val radius: Float, val alpha: Int)
    
    // Number of circles: 5 to 9
    private val circles = List(random.nextInt(5) + 5) {
        Circle(
            cx = (random.nextInt(1024)) / 1024f, // normalized X
            cy = (random.nextInt(576)) / 576f,   // normalized Y
            radius = (random.nextInt(100) + 30) / 1024f, // normalized size relative to base width
            alpha = (random.nextInt(15) + 10) * 255 / 100 // Opacity 10-24%
        )
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        if (width <= 0 || height <= 0) return

        // Draw Background Gradient
        val gradient = LinearGradient(
            0f, 0f, 0f, height,
            topColor, bottomColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        paint.alpha = 255
        canvas.drawRect(bounds, paint)

        // Draw Bubbles
        paint.shader = null // Clear gradient for circles
        paint.color = Color.WHITE
        for (circle in circles) {
            paint.alpha = circle.alpha
            // Map normalized coordinates to actual view size
            val cx = circle.cx * width
            val cy = circle.cy * height
            // Base radius on width to keep proportions
            val r = circle.radius * width
            
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    // Readeck uses FNV-1a hashing in Go. Accurate Kotlin equivalent.
    private fun fnv1aHash(text: String): Long {
        var hash = -3750763034362895579L // FNV offset basis
        val bytes = text.toByteArray()
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 1099511628211L // FNV prime
        }
        return hash
    }
}
