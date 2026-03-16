package com.mydeck.app.ui.detail

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlTemplateTypographyTest {

    @Test
    fun `reader templates share the updated body text baseline`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(template.contains("font-size: 1.95rem;"))
            assertTrue(template.contains("max-width: 100%;"))
            assertFalse(template.contains("@media (max-width: 684px)"))
            assertFalse(template.contains("@media (max-width: 382px)"))
            assertFalse(template.contains("font-size: 1.75rem;"))
            assertFalse(template.contains("font-size: 1.65rem;"))
            assertFalse(template.contains("max-width: 38em;"))
        }
    }

    @Test
    fun `reader templates share the moderated heading scale and spacing`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(
                Regex("""h1, h2, h3, h4, h5, h6 \{[\s\S]*?font-weight: 600;""")
                    .containsMatchIn(template)
            )
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
            assertFalse(template.contains("font-weight: 700;"))
        }
    }

    @Test
    fun `reader templates expose css variables for runtime theming`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(template.contains("--body-color:"))
            assertTrue(template.contains("--body-bg:"))
            assertTrue(template.contains("--link-color:"))
            assertTrue(template.contains("--annotation-active-outline-color:"))
            assertTrue(template.contains("color: var(--body-color);"))
            assertTrue(template.contains("background-color: var(--body-bg);"))
            assertTrue(template.contains("text-decoration-color: var(--accent-underline-color);"))
            assertTrue(template.contains("outline: 2px solid var(--annotation-active-outline-color);"))
            assertFalse(template.contains("{{"))
        }
    }

    @Test
    fun `sepia template keeps curated article link defaults`() {
        val sepiaTemplate = loadTemplates().getValue("html_template_sepia.html")

        assertTrue(sepiaTemplate.contains("--link-color: #7A4B21;"))
        assertTrue(sepiaTemplate.contains("--link-visited-color: #68401B;"))
        assertTrue(sepiaTemplate.contains("--link-hover-color: #A76D3D;"))
        assertTrue(sepiaTemplate.contains("--accent-underline-color: rgba(140, 110, 80, 0.65);"))
        assertFalse(sepiaTemplate.contains("#1d7484"))
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
            "html_template_sepia.html",
            "html_template_black.html"
        )
    }
}
