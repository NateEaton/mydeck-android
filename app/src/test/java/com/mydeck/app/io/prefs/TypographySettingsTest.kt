package com.mydeck.app.io.prefs

import com.mydeck.app.domain.model.LineSpacing
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.TypographySettings
import org.junit.Assert.assertEquals
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
        assertEquals("\"Noto Serif\", Georgia, serif", ReaderFontFamily.NOTO_SERIF.cssValue)
        assertEquals("\"Literata\", Georgia, serif", ReaderFontFamily.LITERATA.cssValue)
        assertEquals("\"Source Serif 4\", Georgia, serif", ReaderFontFamily.SOURCE_SERIF.cssValue)
        assertEquals("\"Noto Sans\", Roboto, sans-serif", ReaderFontFamily.NOTO_SANS.cssValue)
        assertEquals("\"JetBrains Mono\", monospace", ReaderFontFamily.JETBRAINS_MONO.cssValue)
    }

    @Test
    fun `ReaderFontFamily requiresBundledFont is correct`() {
        // System fonts should not require bundling
        assertEquals(false, ReaderFontFamily.SYSTEM_DEFAULT.requiresBundledFont)
        assertEquals(false, ReaderFontFamily.NOTO_SANS.requiresBundledFont)
        
        // Custom fonts should require bundling
        assertEquals(true, ReaderFontFamily.NOTO_SERIF.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.LITERATA.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.SOURCE_SERIF.requiresBundledFont)
        assertEquals(true, ReaderFontFamily.JETBRAINS_MONO.requiresBundledFont)
    }

    @Test
    fun `TypographySettings data class defaults are correct`() {
        val defaults = TypographySettings()
        assertEquals(100, defaults.fontSizePercent)
        assertEquals(ReaderFontFamily.SYSTEM_DEFAULT, defaults.fontFamily)
        assertEquals(LineSpacing.TIGHT, defaults.lineSpacing)
        assertEquals(TextWidth.WIDE, defaults.textWidth)
        assertEquals(false, defaults.justified)
        assertEquals(false, defaults.hyphenation)
    }

    @Test
    fun `LineSpacing enum has correct CSS values`() {
        assertEquals("1.7", LineSpacing.TIGHT.cssValue)
        assertEquals("2.2", LineSpacing.LOOSE.cssValue)
    }

    @Test
    fun `TextWidth enum has correct CSS max-width values`() {
        assertEquals("90vw", TextWidth.WIDE.cssMaxWidth)
        assertEquals("75vw", TextWidth.NARROW.cssMaxWidth)
    }
}
