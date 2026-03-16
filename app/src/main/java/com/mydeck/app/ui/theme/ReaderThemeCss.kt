package com.mydeck.app.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mydeck.app.domain.model.EffectiveAppearance
import java.util.Locale

data class ReaderThemePalette(
    val accentColor: String,
    val accentContainerColor: String,
    val accentUnderlineColor: String,
    val onAccentColor: String,
    val onAccentContainerColor: String,
    val bodyColor: String,
    val bodyBackgroundColor: String,
    val blockquoteBackgroundColor: String,
    val codeBackgroundColor: String,
    val tableBorderColor: String,
    val inputColor: String,
    val inputBackgroundColor: String,
    val inputBorderColor: String,
    val linkColor: String,
    val linkVisitedColor: String,
    val linkHoverColor: String,
    val buttonBorderColor: String,
    val annotationActiveOutlineColor: String,
    val annotationActiveShadowColor: String,
    val annotationYellowColor: String,
    val annotationRedColor: String,
    val annotationBlueColor: String,
    val annotationGreenColor: String,
    val annotationNoneUnderlineColor: String
)

fun resolveReaderThemePalette(
    context: Context,
    appearance: EffectiveAppearance
): ReaderThemePalette {
    val colorScheme = resolveAppColorScheme(context = context, appearance = appearance)
    return readerThemePalette(colorScheme = colorScheme, appearance = appearance)
}

internal fun readerThemePalette(
    colorScheme: ColorScheme,
    appearance: EffectiveAppearance
): ReaderThemePalette {
    val accentPalette = ReaderAccentPalette.from(colorScheme)

    return when (appearance) {
        EffectiveAppearance.PAPER -> ReaderThemePalette(
            accentColor = accentPalette.accentColor,
            accentContainerColor = accentPalette.accentContainerColor,
            accentUnderlineColor = accentPalette.accentUnderlineColor,
            onAccentColor = accentPalette.onAccentColor,
            onAccentContainerColor = accentPalette.onAccentContainerColor,
            bodyColor = "#4A4A4A",
            bodyBackgroundColor = "#F9F9F9",
            blockquoteBackgroundColor = "#F1F1F1",
            codeBackgroundColor = "#F1F1F1",
            tableBorderColor = "#F1F1F1",
            inputColor = "#4A4A4A",
            inputBackgroundColor = "#F1F1F1",
            inputBorderColor = "#F1F1F1",
            linkColor = accentPalette.accentColor,
            linkVisitedColor = accentPalette.accentColor,
            linkHoverColor = accentPalette.accentColor,
            buttonBorderColor = accentPalette.accentColor,
            annotationActiveOutlineColor = colorScheme.primary.toCssRgba(alpha = 0.70f),
            annotationActiveShadowColor = colorScheme.primary.toCssRgba(alpha = 0.18f),
            annotationYellowColor = "rgba(255, 235, 59, 0.4)",
            annotationRedColor = "rgba(239, 83, 80, 0.35)",
            annotationBlueColor = "rgba(66, 165, 245, 0.35)",
            annotationGreenColor = "rgba(102, 187, 106, 0.35)",
            annotationNoneUnderlineColor = "rgba(150, 150, 150, 0.6)"
        )
        EffectiveAppearance.SEPIA -> ReaderThemePalette(
            accentColor = "#A76D3D",
            accentContainerColor = "#A5734A",
            accentUnderlineColor = "rgba(140, 110, 80, 0.65)",
            onAccentColor = "#F9F9F9",
            onAccentContainerColor = "#F9F9F9",
            bodyColor = "#4A3B2B",
            bodyBackgroundColor = "#F4ECD8",
            blockquoteBackgroundColor = "#F9F3E1",
            codeBackgroundColor = "#F0E4C6",
            tableBorderColor = "#F1F1F1",
            inputColor = "#4A4A4A",
            inputBackgroundColor = "#F9F3E1",
            inputBorderColor = "#F9F3E1",
            linkColor = "#7A4B21",
            linkVisitedColor = "#68401B",
            linkHoverColor = "#A76D3D",
            buttonBorderColor = "#A86936",
            annotationActiveOutlineColor = "rgba(167, 109, 61, 0.75)",
            annotationActiveShadowColor = "rgba(167, 109, 61, 0.16)",
            annotationYellowColor = "rgba(214, 177, 84, 0.35)",
            annotationRedColor = "rgba(176, 98, 80, 0.3)",
            annotationBlueColor = "rgba(115, 141, 160, 0.3)",
            annotationGreenColor = "rgba(126, 148, 97, 0.3)",
            annotationNoneUnderlineColor = "rgba(140, 110, 80, 0.65)"
        )
        EffectiveAppearance.DARK -> ReaderThemePalette(
            accentColor = accentPalette.accentColor,
            accentContainerColor = accentPalette.accentContainerColor,
            accentUnderlineColor = accentPalette.accentUnderlineColor,
            onAccentColor = accentPalette.onAccentColor,
            onAccentContainerColor = accentPalette.onAccentContainerColor,
            bodyColor = "#C9C9C9",
            bodyBackgroundColor = "#222222",
            blockquoteBackgroundColor = "#4A4A4A",
            codeBackgroundColor = "#4A4A4A",
            tableBorderColor = "#4A4A4A",
            inputColor = "#C9C9C9",
            inputBackgroundColor = "#4A4A4A",
            inputBorderColor = "#4A4A4A",
            linkColor = accentPalette.accentColor,
            linkVisitedColor = accentPalette.accentColor,
            linkHoverColor = accentPalette.accentColor,
            buttonBorderColor = accentPalette.accentColor,
            annotationActiveOutlineColor = "rgba(255, 255, 255, 0.8)",
            annotationActiveShadowColor = "rgba(255, 255, 255, 0.18)",
            annotationYellowColor = "rgba(255, 235, 59, 0.3)",
            annotationRedColor = "rgba(239, 83, 80, 0.3)",
            annotationBlueColor = "rgba(66, 165, 245, 0.3)",
            annotationGreenColor = "rgba(102, 187, 106, 0.3)",
            annotationNoneUnderlineColor = "rgba(200, 200, 200, 0.5)"
        )
        EffectiveAppearance.BLACK -> ReaderThemePalette(
            accentColor = accentPalette.accentColor,
            accentContainerColor = accentPalette.accentContainerColor,
            accentUnderlineColor = accentPalette.accentUnderlineColor,
            onAccentColor = accentPalette.onAccentColor,
            onAccentContainerColor = accentPalette.onAccentContainerColor,
            bodyColor = "#F5F5F5",
            bodyBackgroundColor = "#000000",
            blockquoteBackgroundColor = "#111111",
            codeBackgroundColor = "#111111",
            tableBorderColor = "#1D1D1D",
            inputColor = "#F5F5F5",
            inputBackgroundColor = "#111111",
            inputBorderColor = "#111111",
            linkColor = accentPalette.accentColor,
            linkVisitedColor = accentPalette.accentColor,
            linkHoverColor = accentPalette.accentColor,
            buttonBorderColor = accentPalette.accentColor,
            annotationActiveOutlineColor = "rgba(255, 255, 255, 0.85)",
            annotationActiveShadowColor = "rgba(255, 255, 255, 0.2)",
            annotationYellowColor = "rgba(255, 235, 59, 0.3)",
            annotationRedColor = "rgba(239, 83, 80, 0.3)",
            annotationBlueColor = "rgba(66, 165, 245, 0.3)",
            annotationGreenColor = "rgba(102, 187, 106, 0.3)",
            annotationNoneUnderlineColor = "rgba(220, 220, 220, 0.6)"
        )
    }
}

internal fun ReaderThemePalette.toCssVariables(): Map<String, String> {
    return linkedMapOf(
        "--accent-color" to accentColor,
        "--accent-container-color" to accentContainerColor,
        "--accent-underline-color" to accentUnderlineColor,
        "--on-accent-color" to onAccentColor,
        "--on-accent-container-color" to onAccentContainerColor,
        "--body-color" to bodyColor,
        "--body-bg" to bodyBackgroundColor,
        "--blockquote-bg" to blockquoteBackgroundColor,
        "--code-bg" to codeBackgroundColor,
        "--table-border-color" to tableBorderColor,
        "--input-color" to inputColor,
        "--input-bg" to inputBackgroundColor,
        "--input-border-color" to inputBorderColor,
        "--link-color" to linkColor,
        "--link-visited-color" to linkVisitedColor,
        "--link-hover-color" to linkHoverColor,
        "--button-border-color" to buttonBorderColor,
        "--annotation-active-outline-color" to annotationActiveOutlineColor,
        "--annotation-active-shadow-color" to annotationActiveShadowColor,
        "--annotation-yellow-color" to annotationYellowColor,
        "--annotation-red-color" to annotationRedColor,
        "--annotation-blue-color" to annotationBlueColor,
        "--annotation-green-color" to annotationGreenColor,
        "--annotation-none-underline-color" to annotationNoneUnderlineColor
    )
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
