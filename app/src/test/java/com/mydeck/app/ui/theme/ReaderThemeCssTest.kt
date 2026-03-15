package com.mydeck.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeCssTest {

    @Test
    fun `applyReaderThemeTokens injects accent colors into template placeholders`() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF112233),
            onPrimary = Color(0xFFF0E0D0),
            primaryContainer = Color(0xFF445566),
            onPrimaryContainer = Color(0xFFAABBCC)
        )
        val template = """
            :root {
              --accent-color: {{ACCENT_COLOR}};
              --accent-container-color: {{ACCENT_CONTAINER_COLOR}};
              --accent-underline-color: {{ACCENT_UNDERLINE_COLOR}};
              --on-accent-color: {{ON_ACCENT_COLOR}};
              --on-accent-container-color: {{ON_ACCENT_CONTAINER_COLOR}};
            }
        """.trimIndent()

        val renderedTemplate = applyReaderThemeTokens(
            template = template,
            colorScheme = colorScheme
        )

        assertTrue(renderedTemplate.contains("--accent-color: #112233;"))
        assertTrue(renderedTemplate.contains("--accent-container-color: #445566;"))
        assertTrue(renderedTemplate.contains("--accent-underline-color: rgba(17, 34, 51, 0.55);"))
        assertTrue(renderedTemplate.contains("--on-accent-color: #F0E0D0;"))
        assertTrue(renderedTemplate.contains("--on-accent-container-color: #AABBCC;"))
        assertFalse(renderedTemplate.contains("{{"))
    }
}
