package com.mydeck.app.ui.detail

import com.mydeck.app.domain.model.LineSpacing
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.TypographySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WebViewTypographyBridge JavaScript generation.
 */
class WebViewTypographyBridgeTest {

    @Test
    fun `applyTypography generates valid JavaScript`() {
        val settings = TypographySettings()
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        // Verify it's wrapped in IIFE
        assertTrue(js.contains("(function() {"))
        assertTrue(js.contains("})();"))
    }

    @Test
    fun `applyTypography includes font-family CSS`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.LITERATA)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.fontFamily"))
        assertTrue(js.contains("Literata"))
    }

    @Test
    fun `applyTypography includes line-height CSS`() {
        val settings = TypographySettings(lineSpacing = LineSpacing.TIGHT)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.lineHeight"))
        assertTrue(js.contains("1.7"))
    }

    @Test
    fun `applyTypography includes max-width for narrow`() {
        val settings = TypographySettings(textWidth = TextWidth.NARROW)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.maxWidth"))
        assertTrue(js.contains("75vw"))
    }

    @Test
    fun `applyTypography includes max-width for wide`() {
        val settings = TypographySettings(textWidth = TextWidth.WIDE)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.maxWidth"))
        assertTrue(js.contains("90vw"))
    }

    @Test
    fun `applyTypography includes text-align justify when enabled`() {
        val settings = TypographySettings(justified = true)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.textAlign"))
        assertTrue(js.contains("justify"))
    }

    @Test
    fun `applyTypography includes text-align left when justify disabled`() {
        val settings = TypographySettings(justified = false)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.textAlign"))
        assertTrue(js.contains("left"))
    }

    @Test
    fun `applyTypography includes hyphenation CSS when enabled`() {
        val settings = TypographySettings(hyphenation = true)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.hyphens"))
        assertTrue(js.contains("auto"))
    }

    @Test
    fun `applyTypography includes hyphenation CSS when disabled`() {
        val settings = TypographySettings(hyphenation = false)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.hyphens"))
        assertTrue(js.contains("manual"))
    }

    @Test
    fun `applyTypography includes font-face for bundled fonts`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.LITERATA)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("@font-face"))
        assertTrue(js.contains("Literata"))
        assertTrue(js.contains("literata-regular.woff2"))
    }

    @Test
    fun `applyTypography does not include font-face declaration for system fonts`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.SYSTEM_DEFAULT)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertFalse(js.contains("@font-face {"))
        assertFalse(js.contains(".woff2"))
    }

    @Test
    fun `applyTypography applies font to headings`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.NOTO_SERIF)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("querySelectorAll('h1,h2,h3,h4,h5,h6')"))
        assertTrue(js.contains("h.style.fontFamily"))
    }

    @Test
    fun `applyTypography generates correct font-face for Noto Serif`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.NOTO_SERIF)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("noto-serif-regular.woff2"))
    }

    @Test
    fun `applyTypography generates correct font-face for Source Serif`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.SOURCE_SERIF)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("source-serif-4-regular.woff2"))
    }

    @Test
    fun `applyTypography generates correct font-face for JetBrains Mono`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.JETBRAINS_MONO)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("jetbrains-mono-regular.woff2"))
    }

    @Test
    fun `applyTypography does not include font-face declaration for Noto Sans`() {
        val settings = TypographySettings(fontFamily = ReaderFontFamily.NOTO_SANS)
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        // Noto Sans is a system font on most Android devices
        assertFalse(js.contains("@font-face {"))
        assertFalse(js.contains(".woff2"))
    }

    @Test
    fun `applyTypography includes margin auto for centering`() {
        val settings = TypographySettings()
        val js = WebViewTypographyBridge.applyTypography(settings)
        
        assertTrue(js.contains("body.style.margin = '0 auto'"))
    }
}
