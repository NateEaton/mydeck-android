package com.mydeck.app.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mydeck.app.domain.model.EffectiveAppearance
import java.util.Locale

private const val ACCENT_COLOR_TOKEN = "{{ACCENT_COLOR}}"
private const val ACCENT_CONTAINER_COLOR_TOKEN = "{{ACCENT_CONTAINER_COLOR}}"
private const val ACCENT_UNDERLINE_COLOR_TOKEN = "{{ACCENT_UNDERLINE_COLOR}}"
private const val ON_ACCENT_COLOR_TOKEN = "{{ON_ACCENT_COLOR}}"
private const val ON_ACCENT_CONTAINER_COLOR_TOKEN = "{{ON_ACCENT_CONTAINER_COLOR}}"

fun applyReaderThemeTokens(
    template: String,
    context: Context,
    appearance: EffectiveAppearance
): String {
    val colorScheme = resolveAppColorScheme(context = context, appearance = appearance)
    return applyReaderThemeTokens(template = template, colorScheme = colorScheme)
}

internal fun applyReaderThemeTokens(
    template: String,
    colorScheme: ColorScheme
): String {
    val accentPalette = ReaderAccentPalette.from(colorScheme)
    return template
        .replace(ACCENT_COLOR_TOKEN, accentPalette.accentColor)
        .replace(ACCENT_CONTAINER_COLOR_TOKEN, accentPalette.accentContainerColor)
        .replace(ACCENT_UNDERLINE_COLOR_TOKEN, accentPalette.accentUnderlineColor)
        .replace(ON_ACCENT_COLOR_TOKEN, accentPalette.onAccentColor)
        .replace(ON_ACCENT_CONTAINER_COLOR_TOKEN, accentPalette.onAccentContainerColor)
}

private data class ReaderAccentPalette(
    val accentColor: String,
    val accentContainerColor: String,
    val accentUnderlineColor: String,
    val onAccentColor: String,
    val onAccentContainerColor: String
) {
    companion object {
        fun from(colorScheme: ColorScheme): ReaderAccentPalette {
            return ReaderAccentPalette(
                accentColor = colorScheme.primary.toCssHex(),
                accentContainerColor = colorScheme.primaryContainer.toCssHex(),
                accentUnderlineColor = colorScheme.primary.toCssRgba(alpha = 0.55f),
                onAccentColor = colorScheme.onPrimary.toCssHex(),
                onAccentContainerColor = colorScheme.onPrimaryContainer.toCssHex()
            )
        }
    }
}

private fun Color.toCssHex(): String {
    val argb = toArgb()
    return String.format(Locale.US, "#%06X", argb and 0x00FFFFFF)
}

private fun Color.toCssRgba(alpha: Float): String {
    val argb = toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return String.format(Locale.US, "rgba(%d, %d, %d, %.2f)", red, green, blue, alpha)
}
