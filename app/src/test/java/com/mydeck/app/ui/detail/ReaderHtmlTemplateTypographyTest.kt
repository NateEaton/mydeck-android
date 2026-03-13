package com.mydeck.app.ui.detail

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlTemplateTypographyTest {

    @Test
    fun `reader templates share the updated body text baseline`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(template.contains("font-size: 1.95rem;"))
            assertTrue(template.contains("font-size: 1.75rem;"))
            assertTrue(template.contains("font-size: 1.65rem;"))
            assertFalse(template.contains("font-size: 1.53rem;"))
            assertFalse(template.contains("font-size: 1.35rem;"))
        }
    }

    @Test
    fun `reader templates share the moderated heading scale and spacing`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(template.contains("line-height: 1.18;"))
            assertTrue(template.contains("margin-top: 2.2rem;"))
            assertTrue(template.contains("margin-bottom: 1.1rem;"))
            assertTrue(template.contains("font-size: 1.5em;"))
            assertTrue(template.contains("font-size: 1.32em;"))
            assertTrue(template.contains("font-size: 1.18em;"))
            assertTrue(template.contains("font-size: 1.08em;"))
            assertTrue(template.contains("font-size: 0.94em;"))
            assertFalse(template.contains("font-size: 2.35em;"))
            assertFalse(template.contains("font-size: 2em;"))
            assertFalse(template.contains("font-size: 1.75em;"))
            assertFalse(template.contains("font-size: 1.25em;"))
        }
    }

    private fun loadTemplates(): Map<String, String> = TEMPLATE_FILES.associateWith { fileName ->
        String(Files.readAllBytes(resolveTemplatePath(fileName)), StandardCharsets.UTF_8)
    }

    private fun resolveTemplatePath(fileName: String): Path {
        val candidates = listOf(
            Path.of("app", "src", "main", "assets", fileName),
            Path.of("src", "main", "assets", fileName)
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Unable to locate template asset: $fileName")
    }

    private companion object {
        val TEMPLATE_FILES = listOf(
            "html_template_light.html",
            "html_template_dark.html",
            "html_template_sepia.html"
        )
    }
}
