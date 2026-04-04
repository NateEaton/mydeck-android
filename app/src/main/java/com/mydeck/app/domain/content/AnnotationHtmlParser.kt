package com.mydeck.app.domain.content

import com.mydeck.app.io.db.model.CachedAnnotationEntity
import java.util.regex.Pattern

/**
 * Extracts annotation metadata from server-baked `<rd-annotation>` elements in article HTML.
 *
 * The Readeck server embeds annotations directly into article HTML as custom elements with
 * attributes like `data-annotation-id-value`, `data-annotation-color`, and `title` (note).
 * This parser extracts those into [CachedAnnotationEntity] rows for local persistence.
 */
object AnnotationHtmlParser {

    // Match <rd-annotation ...>...</rd-annotation> including nested content.
    // Uses reluctant quantifier on inner content to handle multiple annotations.
    private val ANNOTATION_ELEMENT_PATTERN: Pattern = Pattern.compile(
        "<rd-annotation\\b([^>]*)>(.*?)</rd-annotation>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val ATTR_ID = Pattern.compile(
        """data-annotation-id-value\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    private val ATTR_COLOR = Pattern.compile(
        """data-annotation-color\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    private val ATTR_TITLE = Pattern.compile(
        """title\s*=\s*["']([^"']*?)["']""",
        Pattern.CASE_INSENSITIVE
    )

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")

    /**
     * Parse all `<rd-annotation>` elements from the given HTML and return annotation entities.
     *
     * @param html The full article HTML string
     * @param bookmarkId The bookmark ID to associate annotations with
     * @return List of [CachedAnnotationEntity] extracted from the HTML, deduplicated by ID
     */
    fun parse(html: String?, bookmarkId: String): List<CachedAnnotationEntity> {
        if (html.isNullOrBlank()) return emptyList()

        val seen = mutableMapOf<String, CachedAnnotationEntity>()
        val matcher = ANNOTATION_ELEMENT_PATTERN.matcher(html)

        while (matcher.find()) {
            val attributes = matcher.group(1) ?: continue
            val innerHtml = matcher.group(2) ?: ""

            val id = extractAttribute(ATTR_ID, attributes) ?: continue
            val color = extractAttribute(ATTR_COLOR, attributes) ?: "yellow"
            val note = extractAttribute(ATTR_TITLE, attributes)?.takeIf { it.isNotBlank() }
            val text = stripHtmlTags(innerHtml).trim()

            // First occurrence wins (outermost annotation for nested cases)
            if (id !in seen) {
                seen[id] = CachedAnnotationEntity(
                    id = id,
                    bookmarkId = bookmarkId,
                    text = text,
                    color = color,
                    note = note,
                    created = ""
                )
            }
        }

        return seen.values.toList()
    }

    private fun extractAttribute(pattern: Pattern, attributes: String): String? {
        val matcher = pattern.matcher(attributes)
        return if (matcher.find()) matcher.group(1) else null
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
}
