package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedAnnotationSnapshot(
    val id: String,
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val created: String,
    val text: String
)

fun List<AnnotationDto>.toAnnotationCachePayload(json: Json): String {
    val normalizedSnapshots = map { annotation ->
        CachedAnnotationSnapshot(
            id = annotation.id,
            start_selector = annotation.start_selector,
            start_offset = annotation.start_offset,
            end_selector = annotation.end_selector,
            end_offset = annotation.end_offset,
            created = annotation.created,
            text = annotation.text
        )
    }.sortedWith(
        compareBy<CachedAnnotationSnapshot> { it.id }
            .thenBy { it.start_selector }
            .thenBy { it.start_offset }
            .thenBy { it.end_selector }
            .thenBy { it.end_offset }
    )

    return json.encodeToString(normalizedSnapshots)
}
