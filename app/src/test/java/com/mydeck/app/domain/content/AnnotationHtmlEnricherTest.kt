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

    // --- Threshold and lock-in tests ---

    @Test
    fun `partial match above threshold proceeds with enrichment`() {
        // 2 matched / 3 annotations = 0.67 > 0.5 → enrichment should proceed
        val html = """<p><rd-annotation>alpha</rd-annotation> <rd-annotation>beta</rd-annotation></p>"""
        val dtos = listOf(
            makeDto("a1", "alpha", "blue"),
            makeDto("b2", "beta", "yellow"),
            makeDto("c3", "gamma") // extra DTO not present in HTML
        )

        val result = AnnotationHtmlEnricher.enrich(html, dtos)!!

        assertTrue(result.contains("""data-annotation-id-value="a1""""))
        assertTrue(result.contains("""data-annotation-id-value="b2""""))
    }

    @Test
    fun `partial match below threshold aborts and returns original html`() {
        // 1 matched / 5 annotations = 0.2 < 0.5 → abort, return original
        val html = """<p>""" +
            """<rd-annotation>only this matches</rd-annotation>""" +
            """<rd-annotation>no match 1</rd-annotation>""" +
            """<rd-annotation>no match 2</rd-annotation>""" +
            """<rd-annotation>no match 3</rd-annotation>""" +
            """<rd-annotation>no match 4</rd-annotation>""" +
            """</p>"""
        val dtos = listOf(
            makeDto("a1", "only this matches"),
            makeDto("b2", "different text 1"),
            makeDto("c3", "different text 2"),
            makeDto("d4", "different text 3"),
            makeDto("e5", "different text 4")
        )

        val result = AnnotationHtmlEnricher.enrich(html, dtos)

        assertEquals(html, result)
    }

    @Test
    fun `match at exactly threshold boundary proceeds with enrichment`() {
        // 1 matched / 2 annotations = 0.5 → exactly at threshold (not < 0.5) → enrichment proceeds
        val html = """<p><rd-annotation>text1</rd-annotation> <rd-annotation>text2</rd-annotation></p>"""
        val dtos = listOf(
            makeDto("a1", "text1", "blue"),
            makeDto("b2", "no match") // second DTO doesn't match text2
        )

        val result = AnnotationHtmlEnricher.enrich(html, dtos)!!

        assertTrue(result.contains("""data-annotation-id-value="a1""""))
        // The unmatched bare tag for text2 remains
        assertTrue(result.contains("<rd-annotation>text2</rd-annotation>"))
    }

    @Test
    fun `partially enriched html re-enriches bare tags (lock-in regression)`() {
        // HTML already has one enriched tag AND one bare tag (previous partial enrichment)
        // OLD bug: early-exit on data-annotation-id-value would skip → bare tag stuck forever
        // NEW behavior: bare tag is still present → enrichment runs → bare tag gets enriched
        val html = """<p>""" +
            """<rd-annotation id="annotation-a1" data-annotation-id-value="a1" data-annotation-color="blue">first</rd-annotation>""" +
            """ <rd-annotation>second</rd-annotation>""" +
            """</p>"""
        val dtos = listOf(
            makeDto("a1", "first", "blue"),
            makeDto("b2", "second", "yellow")
        )

        val result = AnnotationHtmlEnricher.enrich(html, dtos)!!

        // The previously bare tag should now be enriched
        assertTrue(result.contains("""data-annotation-id-value="b2""""))
        assertTrue(result.contains("""data-annotation-color="yellow""""))
    }

    @Test
    fun `annotation text with html entities matches correctly`() {
        // AnnotationDto.text has & and < characters; HTML has &amp; and &lt; entities
        val html = """<p><rd-annotation>cats &amp; dogs</rd-annotation></p>"""
        val dto = makeDto("e1", "cats & dogs", "green")

        val result = AnnotationHtmlEnricher.enrich(html, listOf(dto))!!

        assertTrue(result.contains("""data-annotation-id-value="e1""""))
        assertTrue(result.contains("""data-annotation-color="green""""))
    }
}
