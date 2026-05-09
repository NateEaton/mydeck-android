package com.mydeck.app.io.db.model

data class CachedAnnotationHighlightEntity(
    val id: String,
    val bookmarkId: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
)
