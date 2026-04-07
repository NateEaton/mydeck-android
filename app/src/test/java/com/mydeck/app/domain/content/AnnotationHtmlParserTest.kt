package com.mydeck.app.domain.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHtmlParserTest {

    private val bookmarkId = "test-bookmark-123"

    @Test
    fun `parse returns empty list for null html`() {
        val result = AnnotationHtmlParser.parse(null, bookmarkId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list for blank html`() {
        val result = AnnotationHtmlParser.parse("   ", bookmarkId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list for html with no annotations`() {
        val html = "<html><body><p>Hello world</p></body></html>"
        val result = AnnotationHtmlParser.parse(html, bookmarkId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse extracts single annotation with all attributes`() {
        val html = """
            <p>Some text before
            <rd-annotation id="annotation-abc123" data-annotation-id-value="abc123" data-annotation-color="blue" title="My note">highlighted text</rd-annotation>
            some text after</p>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        val annotation = result[0]
        assertEquals("abc123", annotation.id)
        assertEquals(bookmarkId, annotation.bookmarkId)
        assertEquals("highlighted text", annotation.text)
        assertEquals("blue", annotation.color)
        assertEquals("My note", annotation.note)
    }

    @Test
    fun `parse extracts multiple annotations`() {
        val html = """
            <p><rd-annotation id="annotation-a1" data-annotation-id-value="a1" data-annotation-color="yellow">first</rd-annotation>
            middle text
            <rd-annotation id="annotation-b2" data-annotation-id-value="b2" data-annotation-color="red">second</rd-annotation></p>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(2, result.size)
        assertEquals("a1", result[0].id)
        assertEquals("yellow", result[0].color)
        assertEquals("first", result[0].text)
        assertEquals("b2", result[1].id)
        assertEquals("red", result[1].color)
        assertEquals("second", result[1].text)
    }

    @Test
    fun `parse defaults color to yellow when missing`() {
        val html = """
            <rd-annotation id="annotation-x1" data-annotation-id-value="x1">no color</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("yellow", result[0].color)
    }

    @Test
    fun `parse returns null note when title attribute is missing`() {
        val html = """
            <rd-annotation id="annotation-x1" data-annotation-id-value="x1" data-annotation-color="green">text</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals(null, result[0].note)
    }

    @Test
    fun `parse returns null note when title attribute is blank`() {
        val html = """
            <rd-annotation id="annotation-x1" data-annotation-id-value="x1" data-annotation-color="green" title="">text</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals(null, result[0].note)
    }

    @Test
    fun `parse skips elements without data-annotation-id-value`() {
        val html = """
            <rd-annotation id="annotation-x1" data-annotation-color="green">no id value</rd-annotation>
            <rd-annotation id="annotation-y2" data-annotation-id-value="y2" data-annotation-color="blue">has id</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("y2", result[0].id)
    }

    @Test
    fun `parse strips inner html tags from text`() {
        val html = """
            <rd-annotation id="annotation-a1" data-annotation-id-value="a1" data-annotation-color="yellow">text with <strong>bold</strong> and <em>italic</em></rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("text with bold and italic", result[0].text)
    }

    @Test
    fun `parse deduplicates by annotation id keeping first occurrence`() {
        val html = """
            <rd-annotation id="annotation-dup" data-annotation-id-value="dup" data-annotation-color="yellow">first occurrence</rd-annotation>
            <rd-annotation id="annotation-dup" data-annotation-id-value="dup" data-annotation-color="red">second occurrence</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("dup", result[0].id)
        assertEquals("yellow", result[0].color)
        assertEquals("first occurrence", result[0].text)
    }

    @Test
    fun `parse handles html entity decoding in text`() {
        val html = """
            <rd-annotation id="annotation-e1" data-annotation-id-value="e1" data-annotation-color="yellow">Tom &amp; Jerry &lt;show&gt;</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("Tom & Jerry <show>", result[0].text)
    }

    @Test
    fun `parse handles annotation spanning multiple lines`() {
        val html = """
            <rd-annotation id="annotation-ml" data-annotation-id-value="ml" data-annotation-color="purple">line one
            line two
            line three</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertTrue(result[0].text.contains("line one"))
        assertTrue(result[0].text.contains("line three"))
    }

    @Test
    fun `parse sets empty created timestamp`() {
        val html = """
            <rd-annotation id="annotation-t1" data-annotation-id-value="t1" data-annotation-color="yellow">text</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("", result[0].created)
    }

    @Test
    fun `parse sets correct bookmarkId on all entities`() {
        val html = """
            <rd-annotation id="annotation-a" data-annotation-id-value="a" data-annotation-color="yellow">one</rd-annotation>
            <rd-annotation id="annotation-b" data-annotation-id-value="b" data-annotation-color="blue">two</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(2, result.size)
        assertTrue(result.all { it.bookmarkId == bookmarkId })
    }

    @Test
    fun `parse handles single-quoted attributes`() {
        val html = """
            <rd-annotation id='annotation-sq' data-annotation-id-value='sq1' data-annotation-color='green' title='a note'>quoted</rd-annotation>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(1, result.size)
        assertEquals("sq1", result[0].id)
        assertEquals("green", result[0].color)
        assertEquals("a note", result[0].note)
    }

    @Test
    fun `parse handles realistic article html with annotations interspersed`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body>
            <article>
                <p>This is a normal paragraph with no annotations.</p>
                <p>This paragraph has <rd-annotation id="annotation-h1" data-annotation-id-value="h1" data-annotation-color="yellow">a highlighted phrase</rd-annotation> in it.</p>
                <p>Another paragraph.</p>
                <p><rd-annotation id="annotation-h2" data-annotation-id-value="h2" data-annotation-color="red" title="Important">entire paragraph highlighted</rd-annotation></p>
                <img src="image.jpg" />
                <p>Final paragraph with <rd-annotation id="annotation-h3" data-annotation-id-value="h3" data-annotation-color="blue">another highlight</rd-annotation> here.</p>
            </article>
            </body>
            </html>
        """.trimIndent()

        val result = AnnotationHtmlParser.parse(html, bookmarkId)

        assertEquals(3, result.size)
        assertEquals("h1", result[0].id)
        assertEquals("a highlighted phrase", result[0].text)
        assertEquals("yellow", result[0].color)
        assertEquals(null, result[0].note)

        assertEquals("h2", result[1].id)
        assertEquals("entire paragraph highlighted", result[1].text)
        assertEquals("red", result[1].color)
        assertEquals("Important", result[1].note)

        assertEquals("h3", result[2].id)
        assertEquals("another highlight", result[2].text)
        assertEquals("blue", result[2].color)
    }
}
