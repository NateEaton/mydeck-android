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
            assertTrue(template.contains("max-width: 100%;"))
            assertFalse(template.contains("font-size: 1.53rem;"))
            assertFalse(template.contains("font-size: 1.35rem;"))
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
    fun `reader templates underline article links in every appearance`() {
        val templates = loadTemplates()

        templates.values.forEach { template ->
            assertTrue(template.contains("text-decoration: underline;"))
            assertTrue(template.contains("text-underline-offset: 0.12em;"))
            assertTrue(template.contains("text-decoration-thickness: 1px;"))
        }
    }

    @Test
    fun `dynamic reader templates expose accent tokens for runtime injection`() {
        val templates = loadTemplates()

        listOf(
            "html_template_light.html",
            "html_template_dark.html",
            "html_template_black.html"
        ).forEach { fileName ->
            val template = templates.getValue(fileName)
            assertTrue(template.contains("--accent-color: {{ACCENT_COLOR}};"))
            assertTrue(template.contains("--accent-container-color: {{ACCENT_CONTAINER_COLOR}};"))
            assertTrue(template.contains("--accent-underline-color: {{ACCENT_UNDERLINE_COLOR}};"))
            assertTrue(template.contains("--on-accent-color: {{ON_ACCENT_COLOR}};"))
            assertTrue(template.contains("--on-accent-container-color: {{ON_ACCENT_CONTAINER_COLOR}};"))
        }
    }

    @Test
    fun `sepia template keeps curated article links subtly underlined`() {
        val sepiaTemplate = loadTemplates().getValue("html_template_sepia.html")

        assertTrue(sepiaTemplate.contains("text-decoration: underline;"))
        assertTrue(sepiaTemplate.contains("text-decoration-color: rgba(140, 110, 80, 0.65);"))
        assertTrue(sepiaTemplate.contains("text-underline-offset: 0.12em;"))
        assertTrue(sepiaTemplate.contains("text-decoration-thickness: 1px;"))
        assertTrue(sepiaTemplate.contains("color: #7a4b21;"))
        assertTrue(sepiaTemplate.contains("color: #68401b;"))
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
