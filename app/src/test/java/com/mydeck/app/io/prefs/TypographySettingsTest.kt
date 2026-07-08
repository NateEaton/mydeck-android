package com.mydeck.app.io.prefs

import com.mydeck.app.domain.model.FontVisibility
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.TypographySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Typography Settings data model.
 * 
 * Note: Integration tests for SettingsDataStoreImpl are deferred to avoid
 * complexity with mocking EncryptedSharedPreferences static methods.
 * The data model validation here ensures correct enum values and defaults.
 */
class TypographySettingsTest {

    @Test
    fun `ReaderFontFamily cssValue produces valid CSS strings`() {
        // Test each font family produces valid CSS
        assertEquals("-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, \"Noto Sans\", sans-serif", ReaderFontFamily.SYSTEM_DEFAULT.cssValue)
        assertEquals("\"Literata\", Georgia, serif", ReaderFontFamily.LITERATA.cssValue)
        assertEquals("\"Cormorant Garamond\", Georgia, serif", ReaderFontFamily.CORMORANT_GARAMOND.cssValue)
        assertEquals("\"Luciole\", Roboto, sans-serif", ReaderFontFamily.LUCIOLE.cssValue)
        assertEquals("\"JetBrains Mono\", monospace", ReaderFontFamily.JETBRAINS_MONO.cssValue)
    }

    @Test
    fun `ReaderFontFamily requiresBundledFont is correct`() {
        // Only System Default falls back to the platform font stack.
        assertEquals(false, ReaderFontFamily.SYSTEM_DEFAULT.requiresBundledFont)

        // Everything else ships a bundled woff2 (including Luciole).
        assertEquals(true, ReaderFontFamily.LITERATA.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.CORMORANT_GARAMOND.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.LUCIOLE.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.JETBRAINS_MONO.requiresBundledFont)
    }

    @Test
    fun `TypographySettings data class defaults are correct`() {
        val defaults = TypographySettings()
        assertEquals(100, defaults.fontSizePercent)
        assertEquals(ReaderFontFamily.SYSTEM_DEFAULT, defaults.fontFamily)
        assertEquals(TypographySettings.DEFAULT_LINE_SPACING_PERCENT, defaults.lineSpacingPercent)
        assertEquals(TextWidth.MEDIUM, defaults.textWidth)
        assertEquals(false, defaults.justified)
        assertEquals(false, defaults.hyphenation)
        assertEquals(FontVisibility.CORE, defaults.fontVisibility)
    }

    @Test
    fun `fontsFor core is the curated set incl Literata and JetBrains, no native-only fonts`() {
        val core = ReaderFontFamily.fontsFor(FontVisibility.CORE)
        assertEquals(ReaderFontFamily.SYSTEM_DEFAULT, core.first())
        assertEquals(9, core.size)
        assertTrue(core.contains(ReaderFontFamily.LITERATA))
        assertTrue(core.contains(ReaderFontFamily.JETBRAINS_MONO))
        // native-only fonts are excluded from CORE
        assertFalse(core.contains(ReaderFontFamily.LORA))
        assertFalse(core.contains(ReaderFontFamily.LUCIOLE))
        assertEquals(ReaderFontFamily.JETBRAINS_MONO, core.last())
    }

    @Test
    fun `fontsFor all lists non-native first then Readeck native, mono last`() {
        val all = ReaderFontFamily.fontsFor(FontVisibility.ALL)
        assertEquals(16, all.size)
        assertEquals(ReaderFontFamily.SYSTEM_DEFAULT, all.first())
        assertEquals(ReaderFontFamily.JETBRAINS_MONO, all.last())
        // Shared fonts appear once, in their native sequence (after the non-native block).
        assertEquals(1, all.count { it == ReaderFontFamily.LITERATA })
        assertTrue(
            all.indexOf(ReaderFontFamily.CORMORANT_GARAMOND) < all.indexOf(ReaderFontFamily.LORA)
        )
    }

    @Test
    fun `TypographySettings font size constants and clamp are correct`() {
        assertEquals(80, TypographySettings.MIN_FONT_SIZE)
        assertEquals(170, TypographySettings.MAX_FONT_SIZE)
        assertEquals(5, TypographySettings.FONT_SIZE_STEP)
        assertEquals(80, TypographySettings.clampFontSizePercent(75))
        assertEquals(100, TypographySettings.clampFontSizePercent(100))
        assertEquals(170, TypographySettings.clampFontSizePercent(200))
    }

    @Test
    fun `TypographySettings line spacing constants and css values are correct`() {
        assertEquals(100, TypographySettings.DEFAULT_LINE_SPACING_PERCENT)
        assertEquals(80, TypographySettings.MIN_LINE_SPACING_PERCENT)
        assertEquals(125, TypographySettings.MAX_LINE_SPACING_PERCENT)
        assertEquals(5, TypographySettings.LINE_SPACING_STEP)
        assertEquals(80, TypographySettings.clampLineSpacingPercent(75))
        assertEquals(110, TypographySettings.clampLineSpacingPercent(110))
        assertEquals(125, TypographySettings.clampLineSpacingPercent(130))
        assertEquals("1.7", TypographySettings.lineSpacingCssValue(100))
        assertEquals("1.36", TypographySettings.lineSpacingCssValue(80))
        assertEquals("2.125", TypographySettings.lineSpacingCssValue(125))
    }

    @Test
    fun `TextWidth enum has correct width fractions and pill labels`() {
        assertEquals(0.98f, TextWidth.WIDE.widthFraction)
        assertEquals(0.85f, TextWidth.MEDIUM.widthFraction)
        assertEquals(0.75f, TextWidth.NARROW.widthFraction)
        assertEquals("W", TextWidth.WIDE.pillLabel)
        assertEquals("M", TextWidth.MEDIUM.pillLabel)
        assertEquals("N", TextWidth.NARROW.pillLabel)
    }
}
