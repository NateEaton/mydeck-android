package com.mydeck.app.domain.model

import java.util.Locale

data class TypographySettings(
    val fontSizePercent: Int = 100,               // 80–170, step 5
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_DEFAULT,
    val lineSpacingPercent: Int = DEFAULT_LINE_SPACING_PERCENT,
    val textWidth: TextWidth = TextWidth.MEDIUM,
    val justified: Boolean = false,
    val hyphenation: Boolean = false
) {
    companion object {
        const val MIN_FONT_SIZE = 80
        const val MAX_FONT_SIZE = 170
        const val FONT_SIZE_STEP = 5
        const val DEFAULT_LINE_SPACING = 1.7
        const val DEFAULT_LINE_SPACING_PERCENT = 100
        const val MIN_LINE_SPACING_PERCENT = 80
        const val MAX_LINE_SPACING_PERCENT = 125
        const val LINE_SPACING_STEP = 5

        fun clampFontSizePercent(value: Int): Int = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

        fun clampLineSpacingPercent(value: Int): Int =
            value.coerceIn(MIN_LINE_SPACING_PERCENT, MAX_LINE_SPACING_PERCENT)

        fun lineSpacingCssValue(percent: Int): String {
            val normalizedPercent = clampLineSpacingPercent(percent)
            val lineHeight = DEFAULT_LINE_SPACING * normalizedPercent / 100.0
            return String.format(Locale.US, "%.3f", lineHeight)
                .trimEnd('0')
                .trimEnd('.')
        }
    }
}

enum class ReaderFontFamily(val displayName: String, val cssValue: String) {
    SYSTEM_DEFAULT("System Default",
        """-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif"""),
    NOTO_SERIF("Noto Serif", """"Noto Serif", Georgia, serif"""),
    LITERATA("Literata", """"Literata", Georgia, serif"""),
    SOURCE_SERIF("Source Serif", """"Source Serif 4", Georgia, serif"""),
    NOTO_SANS("Noto Sans", """"Noto Sans", Roboto, sans-serif"""),
    JETBRAINS_MONO("JetBrains Mono", """"JetBrains Mono", monospace""");

    /** Whether this font requires a woff2 file bundled in assets/fonts/ */
    val requiresBundledFont: Boolean
        get() = this != SYSTEM_DEFAULT && this != NOTO_SANS
}

enum class TextWidth(
    val displayName: String,
    val widthFraction: Float,
    val pillLabel: String
) {
    WIDE("Wide", 0.95f, "W"),
    MEDIUM("Medium", 0.88f, "M"),
    NARROW("Narrow", 0.825f, "N")
}
