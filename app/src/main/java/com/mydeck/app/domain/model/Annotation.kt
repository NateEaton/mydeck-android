package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class Annotation(
    val id: String,
    val bookmarkId: String,
    val startSelector: String,
    val startOffset: Int,
    val endSelector: String,
    val endOffset: Int,
    val color: String,
    val text: String,
    val created: Instant,
)
