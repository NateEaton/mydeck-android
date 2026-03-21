package com.mydeck.app.domain.content

import com.mydeck.app.io.rest.model.AnnotationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHtmlEnricherTest {

    private fun makeDto(
        id: String,
        text: String,
        color: String = "yellow",
        note: String = ""
    ) = AnnotationDto(
        id = id,
        start_selector = "",
        start_offset = 0,
        end_selector = "",
        end_offset = 0,
        created = "",
        text = text,
        color = color,
        note = note
    )

    @Test
    fun `returns null for null html`() {
        assertNull(AnnotationHtmlEnricher.enrich(null, emptyList()))
    }

    @Test
    fun `returns unchanged html when no annotations`() {
        val html = "<p>Hello <rd-annotation>world</rd-annotation></p>"
        assertEquals(html, AnnotationHtmlEnricher.enrich(html, emptyList()))
    }

    @Test
    fun `returns unchanged html when already enriched`() {
        val html = """<p><rd-annotation id="annotation-abc" data-annotation-id-value="abc" data-annotation-color="blue">text</rd-annotation></p>"""
        val dto = makeDto("abc", "text", "blue")
        assertEquals(html, AnnotationHtmlEnricher.enrich(html, listOf(dto)))
    }

    @Test
    fun `returns unchanged html when no bare annotations`() {
        val html = "<p>No annotations here</p>"
        val dto = makeDto("abc", "text")
        assertEquals(html, AnnotationHtmlEnricher.enrich(html, listOf(dto)))
    }

    @Test
    fun `enriches single bare annotation`() {
        val html = "<p>Some <rd-annotation>highlighted text</rd-annotation> here</p>"
        val dto = makeDto("abc123", "highlighted text", "blue")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("""data-annotation-id-value="abc123""""))
        assertTrue(result.contains("""data-annotation-color="blue""""))
        assertTrue(result.contains("""id="annotation-abc123""""))
        assertTrue(result.contains(">highlighted text</rd-annotation>"))
    }

    @Test
    fun `enriches multiple bare annotations with correct colors`() {
        val html = """
            <p><rd-annotation>first</rd-annotation> middle <rd-annotation>second</rd-annotation></p>
        """.trimIndent()
        val dtos = listOf(
            makeDto("a1", "first", "yellow"),
            makeDto("b2", "second", "blue")
        )

        val result = AnnotationHtmlEnricher.enrich(html, dtos)!!

        assertTrue(result.contains("""data-annotation-id-value="a1""""))
        assertTrue(result.contains("""data-annotation-color="yellow""""))
        assertTrue(result.contains("""data-annotation-id-value="b2""""))
        assertTrue(result.contains("""data-annotation-color="blue""""))
    }

    @Test
    fun `preserves inner html of annotations`() {
        val html = "<p><rd-annotation>text with <strong>bold</strong></rd-annotation></p>"
        val dto = makeDto("x1", "text with bold", "green")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("text with <strong>bold</strong></rd-annotation>"))
        assertTrue(result.contains("""data-annotation-color="green""""))
    }

    @Test
    fun `adds note attributes when note is present`() {
        val html = "<p><rd-annotation>noted text</rd-annotation></p>"
        val dto = makeDto("n1", "noted text", "red", "My note")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("""title="My note""""))
        assertTrue(result.contains("""data-annotation-note="true""""))
    }

    @Test
    fun `omits note attributes when note is blank`() {
        val html = "<p><rd-annotation>no note</rd-annotation></p>"
        val dto = makeDto("nn1", "no note", "yellow", "")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(!result.contains("title="))
        assertTrue(!result.contains("data-annotation-note"))
    }

    @Test
    fun `leaves unmatched bare annotations unchanged`() {
        val html = "<p><rd-annotation>known</rd-annotation> <rd-annotation>unknown</rd-annotation></p>"
        val dto = makeDto("k1", "known", "blue")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("""data-annotation-id-value="k1""""))
        // The unmatched tag should remain as bare <rd-annotation>
        assertTrue(result.contains("<rd-annotation>unknown</rd-annotation>"))
    }

    @Test
    fun `escapes special characters in attributes`() {
        val html = """<p><rd-annotation>text</rd-annotation></p>"""
        val dto = makeDto("id\"with<special>", "text", "yellow", "note with \"quotes\"")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("&quot;"))
        assertTrue(!result.contains("\"quotes\""))
    }
}
