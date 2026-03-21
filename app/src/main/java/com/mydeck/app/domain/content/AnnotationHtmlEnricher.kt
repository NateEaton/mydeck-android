package com.mydeck.app.domain.content

import com.mydeck.app.io.rest.model.AnnotationDto
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Enriches bare `<rd-annotation>` elements in multipart HTML with proper attributes.
 *
 * The Readeck multipart sync endpoint strips attributes from `<rd-annotation>` elements,
 * returning bare `<rd-annotation>text</rd-annotation>` tags. This enricher patches them
 * with `id`, `data-annotation-id-value`, and `data-annotation-color` attributes using
 * data from the annotations REST API, enabling offline highlight listing, tap-to-edit,
 * scroll-to-annotation, and correct color rendering.
 */
object AnnotationHtmlEnricher {

    // Matches <rd-annotation> without any attributes (bare tags from multipart)
    private val BARE_ANNOTATION_PATTERN: Pattern = Pattern.compile(
        "<rd-annotation>(.*?)</rd-annotation>",
        Pattern.DOTALL
    )

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")

    /**
     * Enrich bare `<rd-annotation>` tags in [html] with attributes from [annotations].
     *
     * Matching is done by comparing the stripped inner text of each bare tag against
     * the `text` field of each annotation DTO. Unmatched tags are left as-is.
     *
     * @param html The article HTML that may contain bare `<rd-annotation>` tags
     * @param annotations Annotation data from the REST API
     * @return The enriched HTML, or the original if no enrichment was needed
     */
    fun enrich(html: String?, annotations: List<AnnotationDto>): String? {
        if (html == null) return null
        if (annotations.isEmpty()) return html

        // Check if HTML already has enriched annotations (has data-annotation-id-value)
        if (html.contains("data-annotation-id-value")) return html

        // Check if there are any bare annotations to enrich
        if (!BARE_ANNOTATION_PATTERN.matcher(html).find()) return html

        // Build lookup from stripped text → annotation
        val annotationByText = annotations.associateBy { it.text.trim() }
        val matched = mutableSetOf<String>()

        val matcher = BARE_ANNOTATION_PATTERN.matcher(html)
        val result = StringBuffer()

        while (matcher.find()) {
            val innerHtml = matcher.group(1) ?: ""
            val strippedText = stripHtmlTags(innerHtml).trim()

            val annotation = annotationByText[strippedText]
            if (annotation != null && annotation.id !in matched) {
                matched.add(annotation.id)
                val enrichedOpenTag = buildEnrichedOpenTag(annotation)
                val replacement = "$enrichedOpenTag$innerHtml</rd-annotation>"
                matcher.appendReplacement(result, replacement.escapeReplacement())
            }
            // Unmatched bare tags are left as-is (appendReplacement not called)
        }
        matcher.appendTail(result)

        if (matched.isNotEmpty()) {
            Timber.d(
                "Enriched ${matched.size}/${annotations.size} annotations in HTML " +
                    "(${annotations.size - matched.size} unmatched)"
            )
        }

        return result.toString()
    }

    private fun buildEnrichedOpenTag(annotation: AnnotationDto): String {
        val id = annotation.id.escapeAttr()
        val color = annotation.color.escapeAttr()
        val noteAttr = if (annotation.note.isNotBlank()) {
            """ title="${annotation.note.escapeAttr()}" data-annotation-note="true""""
        } else ""
        return """<rd-annotation id="annotation-$id" data-annotation-id-value="$id" data-annotation-color="$color"$noteAttr>"""
    }

    private fun stripHtmlTags(html: String): String {
        return HTML_TAG_PATTERN.matcher(html).replaceAll("")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    private fun String.escapeAttr(): String {
        return replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun String.escapeReplacement(): String {
        return replace("\\", "\\\\").replace("$", "\\$")
    }
}
