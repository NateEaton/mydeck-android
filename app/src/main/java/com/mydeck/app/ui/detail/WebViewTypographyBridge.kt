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
     *
     * @param settings Typography settings to apply.
     * @param contentMaxWidthPercent Percent (1-100) to expose as the
     *   `--mydeck-content-max-width` CSS variable on the document element so the
     *   reader stylesheet can constrain the body width without changing the
     *   WebView's Compose width. Also written to `body.style.maxWidth` so the
     *   inline style matches the variable.
     */
    fun applyTypography(
        settings: TypographySettings,
        contentMaxWidthPercent: Int
    ): String {
        val widthPercent = contentMaxWidthPercent.coerceIn(1, 100)
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

                // Expose content max-width to CSS via a custom property so the
                // reader stylesheet can constrain body width while the WebView
                // itself remains full-width in Compose.
                document.documentElement.style.setProperty('--mydeck-content-max-width', '$widthPercent%');

                var desiredHyphens = '${if (settings.hyphenation) "auto" else "manual"}';
                window.mydeckDesiredHyphens = desiredHyphens;

                // Apply typography to body
                var body = document.body;
                body.style.fontFamily = '${settings.fontFamily.cssValue}';
                body.style.lineHeight = '${TypographySettings.lineSpacingCssValue(settings.lineSpacingPercent)}';
                body.style.maxWidth = '$widthPercent%';
                body.style.margin = '0 auto';
                body.style.padding = '0 8px calc(8px + var(--mydeck-bottom-clearance-px, 0px)) 8px';
                body.style.textAlign = '${if (settings.justified) "justify" else "left"}';
                var appliedHyphens = window.mydeckHyphenationSelectionSuspended ? 'manual' : desiredHyphens;
                body.style.hyphens = appliedHyphens;
                body.style.webkitHyphens = appliedHyphens;

                // Also apply font-family to headings for consistency
                var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                headings.forEach(function(h) {
                    h.style.fontFamily = '${settings.fontFamily.cssValue}';
                });
            })();
        """.trimIndent()
    }

    fun suspendHyphenationForSelection(): String {
        return """
            (function() {
                window.mydeckHyphenationSelectionSuspended = true;
                var body = document.body;
                if (!body) return;
                body.style.hyphens = 'manual';
                body.style.webkitHyphens = 'manual';
            })();
        """.trimIndent()
    }

    fun restoreHyphenationAfterSelection(): String {
        return """
            (function() {
                window.mydeckHyphenationSelectionSuspended = false;
                var body = document.body;
                if (!body) return;
                var desiredHyphens = window.mydeckDesiredHyphens || 'manual';
                body.style.hyphens = desiredHyphens;
                body.style.webkitHyphens = desiredHyphens;
            })();
        """.trimIndent()
    }

    // Standard Google/Fontsource subset ranges. Files are bundled per subset so the WebView
    // only loads the subset(s) a given article actually uses.
    private const val LATIN_RANGE =
        "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329," +
            "U+2000-206F,U+2074,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"
    // Latin Extended-A/B — covers Polish and other European scripts. Deliberately narrower
    // than Google's full latin-ext (drops phonetic/medievalist blocks no locale needs), which
    // keeps the linguistics-heavy Gentium subset small.
    private const val LATIN_EXT_RANGE = "U+0100-024F"
    private const val CYRILLIC_RANGE = "U+0301,U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116"

    /**
     * Builds @font-face declarations for a bundled font: one per (subset × weight), so the
     * reader has real Bold (700) and covers Latin, Latin Extended, and — where available —
     * Cyrillic. Returns empty string for system fonts.
     */
    private fun buildFontFaceCss(fontFamily: ReaderFontFamily): String {
        if (!fontFamily.requiresBundledFont) return ""

        val familyName = fontFamily.cssValue.substringAfter('"').substringBefore('"')
        val subsets = buildList {
            add("latin" to LATIN_RANGE)
            add("latin-ext" to LATIN_EXT_RANGE)
            if (fontFamily.hasCyrillic) add("cyrillic" to CYRILLIC_RANGE)
        }
        // (cssWeight to bundled file weight). Cormorant's normal face is its Medium (500) file.
        val weights = listOf(400 to fontFamily.regularWeight, 700 to 700)

        return buildString {
            for ((subset, range) in subsets) {
                for ((cssWeight, fileWeight) in weights) {
                    val file = "${fontFamily.fileSlug}-$subset-$fileWeight.woff2"
                    append(
                        """
                        @font-face {
                            font-family: "$familyName";
                            src: url("file:///android_asset/fonts/$file") format("woff2");
                            font-weight: $cssWeight;
                            font-style: normal;
                            font-display: swap;
                            unicode-range: $range;
                        }
                        """.trimIndent()
                    )
                    append("\n")
                }
            }
        }
    }
}
