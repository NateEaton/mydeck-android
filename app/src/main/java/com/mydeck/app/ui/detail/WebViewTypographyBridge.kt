package com.mydeck.app.ui.detail

import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TypographySettings

/**
 * JavaScript bridge for applying typography settings to WebView content.
 * Generates JavaScript code that dynamically updates CSS properties.
 */
object WebViewTypographyBridge {

    /**
     * Generates JavaScript that applies typography settings to the reader content.
     * Call via webView.evaluateJavascript(js, null).
     */
    fun applyTypography(settings: TypographySettings): String {
        val fontFaceDeclarations = buildFontFaceCss(settings.fontFamily)
        val fontFaceInjection = if (fontFaceDeclarations.isNotEmpty()) {
            """
                // Inject or update @font-face
                var styleId = 'mydeck-typography-fonts';
                var existing = document.getElementById(styleId);
                if (existing) {
                    existing.textContent = `${fontFaceDeclarations}`;
                } else {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `${fontFaceDeclarations}`;
                    document.head.appendChild(style);
                }
            """.trimIndent()
        } else {
            """
                // Remove any existing @font-face when using system font
                var styleId = 'mydeck-typography-fonts';
                var existing = document.getElementById(styleId);
                if (existing) { existing.remove(); }
            """.trimIndent()
        }
        
        return """
            (function() {
                $fontFaceInjection

                // Apply typography to body
                var body = document.body;
                body.style.fontFamily = '${settings.fontFamily.cssValue}';
                body.style.lineHeight = '${settings.lineSpacing.cssValue}';
                body.style.maxWidth = '${settings.textWidth.cssMaxWidth}';
                body.style.margin = '0 auto';
                body.style.padding = '0 8px';
                body.style.textAlign = '${if (settings.justified) "justify" else "left"}';
                body.style.hyphens = '${if (settings.hyphenation) "auto" else "manual"}';
                body.style.webkitHyphens = '${if (settings.hyphenation) "auto" else "manual"}';

                // Also apply font-family to headings for consistency
                var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                headings.forEach(function(h) {
                    h.style.fontFamily = '${settings.fontFamily.cssValue}';
                });
            })();
        """.trimIndent()
    }

    /**
     * Builds @font-face CSS declarations for bundled fonts.
     * Returns empty string for system fonts.
     */
    private fun buildFontFaceCss(fontFamily: ReaderFontFamily): String {
        if (!fontFamily.requiresBundledFont) return ""
        
        val fileName = when (fontFamily) {
            ReaderFontFamily.NOTO_SERIF -> "noto-serif-regular.woff2"
            ReaderFontFamily.LITERATA -> "literata-regular.woff2"
            ReaderFontFamily.SOURCE_SERIF -> "source-serif-4-regular.woff2"
            ReaderFontFamily.JETBRAINS_MONO -> "jetbrains-mono-regular.woff2"
            else -> return ""
        }
        
        val familyName = fontFamily.cssValue.substringAfter('"').substringBefore('"')
        return """
            @font-face {
                font-family: "$familyName";
                src: url("file:///android_asset/fonts/$fileName") format("woff2");
                font-weight: 400;
                font-display: swap;
            }
        """.trimIndent()
    }
}
