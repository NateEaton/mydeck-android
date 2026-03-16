package com.mydeck.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mydeck.app.domain.model.EffectiveAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeCssTest {

    @Test
    fun `paper palette follows the provided accent color scheme`() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF112233),
            onPrimary = Color(0xFFF0E0D0),
            primaryContainer = Color(0xFF445566),
            onPrimaryContainer = Color(0xFFAABBCC)
        )

        val palette = readerThemePalette(
            colorScheme = colorScheme,
            appearance = EffectiveAppearance.PAPER
        )

        assertEquals("#112233", palette.accentColor)
        assertEquals("#445566", palette.accentContainerColor)
        assertEquals("rgba(17, 34, 51, 0.55)", palette.accentUnderlineColor)
        assertEquals("#F0E0D0", palette.onAccentColor)
        assertEquals("#AABBCC", palette.onAccentContainerColor)
        assertEquals("#4A4A4A", palette.bodyColor)
    }

    @Test
    fun `sepia palette keeps curated colors`() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF112233),
            onPrimary = Color(0xFFF0E0D0),
            primaryContainer = Color(0xFF445566),
            onPrimaryContainer = Color(0xFFAABBCC)
        )

        val palette = readerThemePalette(
            colorScheme = colorScheme,
            appearance = EffectiveAppearance.SEPIA
        )

        assertEquals("#A76D3D", palette.accentColor)
        assertEquals("#7A4B21", palette.linkColor)
        assertEquals("#68401B", palette.linkVisitedColor)
        assertEquals("#A76D3D", palette.linkHoverColor)
        assertEquals("rgba(140, 110, 80, 0.65)", palette.annotationNoneUnderlineColor)
    }

    @Test
    fun `black palette keeps dark reader surfaces while using accent colors`() {
        val colorScheme = darkColorScheme(
            primary = Color(0xFFABCDEF),
            onPrimary = Color(0xFF102030),
            primaryContainer = Color(0xFF203040),
            onPrimaryContainer = Color(0xFFDDEEFF)
        )

        val palette = readerThemePalette(
            colorScheme = colorScheme,
            appearance = EffectiveAppearance.BLACK
        )

        assertEquals("#ABCDEF", palette.accentColor)
        assertEquals("#000000", palette.bodyBackgroundColor)
        assertEquals("#111111", palette.codeBackgroundColor)
        assertEquals("rgba(255, 255, 255, 0.85)", palette.annotationActiveOutlineColor)
    }

    @Test
    fun `palette exports css variables for webview theme bridge`() {
        val palette = readerThemePalette(
            colorScheme = lightColorScheme(),
            appearance = EffectiveAppearance.PAPER
        )

        val cssVariables = palette.toCssVariables()

        assertEquals(palette.bodyColor, cssVariables["--body-color"])
        assertEquals(palette.bodyBackgroundColor, cssVariables["--body-bg"])
        assertEquals(palette.linkColor, cssVariables["--link-color"])
        assertEquals(
            palette.annotationActiveOutlineColor,
            cssVariables["--annotation-active-outline-color"]
        )
        assertTrue(cssVariables.containsKey("--accent-color"))
    }
}
