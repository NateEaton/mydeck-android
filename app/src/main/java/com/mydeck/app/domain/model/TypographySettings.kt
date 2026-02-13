package com.mydeck.app.domain.model

data class TypographySettings(
    val fontSizePercent: Int = 100,               // 80–200, step 10
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_DEFAULT,
    val lineSpacing: LineSpacing = LineSpacing.TIGHT,
    val textWidth: TextWidth = TextWidth.WIDE,
    val justified: Boolean = false,
    val hyphenation: Boolean = false
) {
    companion object {
        const val MIN_FONT_SIZE = 80
        const val MAX_FONT_SIZE = 200
        const val FONT_SIZE_STEP = 10
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

enum class LineSpacing(val displayName: String, val cssValue: String) {
    TIGHT("Tight", "1.7"),
    LOOSE("Loose", "2.2")
}

enum class TextWidth(val displayName: String, val cssMaxWidth: String) {
    WIDE("Wide", "90vw"),
    NARROW("Narrow", "75vw")
}
