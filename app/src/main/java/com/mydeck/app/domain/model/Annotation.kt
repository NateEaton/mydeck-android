package com.mydeck.app.domain.model

data class Annotation(
    val id: String,
    val bookmarkId: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String
)
