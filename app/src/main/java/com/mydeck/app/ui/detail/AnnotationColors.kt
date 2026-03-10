package com.mydeck.app.ui.detail

import androidx.compose.ui.graphics.Color

internal fun annotationColorForName(colorName: String): Color {
    return when (colorName) {
        "red" -> Color(0xFFEF5350)
        "blue" -> Color(0xFF42A5F5)
        "green" -> Color(0xFF66BB6A)
        "none" -> Color.Transparent
        else -> Color(0xFFFFEB3B)
    }
}
