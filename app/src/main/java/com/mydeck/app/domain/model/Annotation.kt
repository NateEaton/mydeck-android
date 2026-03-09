package com.mydeck.app.domain.model

import kotlinx.serialization.Serializable

data class Annotation(
    val id: String,
    val bookmarkId: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String
)

@Serializable
data class SelectionData(
    val text: String,
    val startSelector: String,
    val startOffset: Int,
    val endSelector: String,
    val endOffset: Int,
    val selectedAnnotationIds: List<String> = emptyList()
)
