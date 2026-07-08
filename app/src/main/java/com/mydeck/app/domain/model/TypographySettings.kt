package com.mydeck.app.domain.model

import java.util.Locale

data class TypographySettings(
    val fontSizePercent: Int = 100,               // 80–170, step 5
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_DEFAULT,
    val lineSpacingPercent: Int = DEFAULT_LINE_SPACING_PERCENT,
    val textWidth: TextWidth = TextWidth.MEDIUM,
    val justified: Boolean = false,
    val hyphenation: Boolean = false,
    val fontVisibility: FontVisibility = FontVisibility.CORE
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

/**
 * Which fonts to surface in the reader font picker. Set on the Settings page via a
 * single "include native Readeck fonts" toggle.
 * - CORE: the app's own curated fonts (the default) — includes Literata and JetBrains Mono.
 * - ALL: CORE plus the remaining native Readeck fonts.
 */
enum class FontVisibility { CORE, ALL }

enum class ReaderFontFamily(
    val displayName: String,
    val cssValue: String,
    /** Filename prefix in assets/fonts/ (`<slug>-<subset>-<weight>.woff2`). Empty = not bundled. */
    val fileSlug: String = "",
    /** Whether a base-Cyrillic subset is bundled (covers Russian & Ukrainian). */
    val hasCyrillic: Boolean = false,
    /** The bundled woff2 weight used as the normal (400) face. Cormorant ships Medium. */
    val regularWeight: Int = 400,
) {
    // Declaration order is not display order — see the ordered lists below.
    SYSTEM_DEFAULT("System Default",
        """-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif"""),
    LITERATA("Literata", """"Literata", Georgia, serif""", "literata", hasCyrillic = true),
    CANTARELL("Cantarell", """"Cantarell", Roboto, sans-serif""", "cantarell"),
    CORMORANT_GARAMOND("Cormorant Garamond", """"Cormorant Garamond", Georgia, serif""",
        "cormorant-garamond", hasCyrillic = true, regularWeight = 500),
    RECURSIVE("Recursive", """"Recursive", Roboto, sans-serif""", "recursive"),
    BITTER("Bitter", """"Bitter", Georgia, serif""", "bitter", hasCyrillic = true),
    GENTIUM("Gentium", """"Gentium", Georgia, serif""", "gentium", hasCyrillic = true),
    OLD_STANDARD("Old Standard", """"Old Standard TT", Georgia, serif""", "old-standard-tt", hasCyrillic = true),
    JETBRAINS_MONO("JetBrains Mono", """"JetBrains Mono", monospace""", "jetbrains-mono", hasCyrillic = true),
    LORA("Lora", """"Lora", Georgia, serif""", "lora", hasCyrillic = true),
    PUBLIC_SANS("Public Sans", """"Public Sans", Roboto, sans-serif""", "public-sans"),
    MERRIWEATHER("Merriweather", """"Merriweather", Georgia, serif""", "merriweather", hasCyrillic = true),
    INTER("Inter", """"Inter", Roboto, sans-serif""", "inter", hasCyrillic = true),
    PLEX_SERIF("IBM Plex Serif", """"IBM Plex Serif", Georgia, serif""", "ibm-plex-serif", hasCyrillic = true),
    LUCIOLE("Luciole", """"Luciole", Roboto, sans-serif""", "luciole"),
    ATKINSON("Atkinson Hyperlegible", """"Atkinson Hyperlegible", Roboto, sans-serif""", "atkinson-hyperlegible");

    /** Whether this font requires woff2 files bundled in assets/fonts/. */
    val requiresBundledFont: Boolean
        get() = fileSlug.isNotEmpty()

    companion object {
        /** Readeck's native-reader list, in its native (screenshot) order. */
        private val readeckNativeFonts = listOf(
            LORA, PUBLIC_SANS, MERRIWEATHER, INTER, PLEX_SERIF,
            LITERATA, LUCIOLE, ATKINSON, JETBRAINS_MONO,
        )

        /** MyDeck's distinctive picks that are NOT in Readeck's native list. */
        private val nonNativeFonts = listOf(
            CANTARELL, CORMORANT_GARAMOND, RECURSIVE, BITTER, GENTIUM, OLD_STANDARD,
        )

        /**
         * CORE (default) = the app's own curated set. Includes Literata and JetBrains Mono
         * (which happen to also be native Readeck fonts). System Default first, mono last.
         */
        val coreFonts = listOf(
            SYSTEM_DEFAULT, LITERATA,
            CANTARELL, CORMORANT_GARAMOND, RECURSIVE, BITTER, GENTIUM, OLD_STANDARD,
            JETBRAINS_MONO,
        )

        /**
         * ALL = System Default, then every non-native font, then Readeck's native list.
         * Fonts in both (Literata, JetBrains Mono) stay in their normal Readeck sequence.
         */
        val allFonts = listOf(SYSTEM_DEFAULT) + nonNativeFonts + readeckNativeFonts

        fun fontsFor(visibility: FontVisibility): List<ReaderFontFamily> = when (visibility) {
            FontVisibility.CORE -> coreFonts
            FontVisibility.ALL -> allFonts
        }
    }
}

enum class TextWidth(
    val displayName: String,
    val widthFraction: Float,
    val pillLabel: String
) {
    WIDE("Wide", 0.98f, "W"),
    MEDIUM("Medium", 0.85f, "M"),
    NARROW("Narrow", 0.75f, "N")
}
