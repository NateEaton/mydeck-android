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
    
    // If fewer than this fraction of annotations are matched, abort enrichment
    // and return the original HTML unchanged.
    private const val MIN_MATCH_FRACTION = 0.5

    /**
     * Enrich bare `<rd-annotation>` tags in [html] with attributes from [annotations].
     *
     * Matching is done by comparing the stripped inner text of each bare tag against
     * the `text` field of each annotation DTO. Unmatched tags are left as-is.
     *
     * @param html The article HTML that may contain bare `<rd-annotation>` tags
     * @param annotations Annotation data from the REST API
     * @return The enriched HTML, the original HTML if enrichment quality was below threshold,
     *   or the original if no enrichment was needed
     */
    fun enrich(html: String?, annotations: List<AnnotationDto>): String? {
        if (html == null) return null
        if (annotations.isEmpty()) return html

        // Only skip if there are no bare (un-enriched) annotation tags remaining.
        // Do NOT exit on the presence of enriched tags — the HTML may be partially
        // enriched from a previous pass and still have bare tags that need enrichment.
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

        // Abort if match quality is below threshold — return original HTML unchanged
        // rather than committing a poorly-enriched version.
        if (annotations.isNotEmpty() && matched.size.toFloat() / annotations.size < MIN_MATCH_FRACTION) {
            Timber.w(
                "Annotation enrichment aborted: matched ${matched.size}/${annotations.size} " +
                    "(below ${(MIN_MATCH_FRACTION * 100).toInt()}% threshold) — returning original HTML"
            )
            return html
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
