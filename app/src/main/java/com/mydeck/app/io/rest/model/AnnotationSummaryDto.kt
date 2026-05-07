package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationSummaryDto(
    val id: String,
    val href: String,
    val text: String,
    val color: String,
    val note: String,
    val created: String,
    val bookmark_id: String,
    val bookmark_href: String,
    val bookmark_url: String,
    val bookmark_title: String,
    val bookmark_site_name: String,
)

fun AnnotationSummaryDto.toDomain(): com.mydeck.app.domain.model.HighlightSummary = com.mydeck.app.domain.model.HighlightSummary(
    id = id,
    text = text,
    color = color,
    note = note,
    created = kotlinx.datetime.Instant.parse(created),
    bookmarkId = bookmark_id,
    bookmarkTitle = bookmark_title,
    bookmarkSiteName = bookmark_site_name,
)
