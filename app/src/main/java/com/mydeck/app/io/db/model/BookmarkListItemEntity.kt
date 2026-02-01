package com.mydeck.app.io.db.model

import kotlinx.datetime.Instant

data class BookmarkListItemEntity(
    val id: String,
    val url: String,
    val title: String,
    val siteName: String,
    val isMarked: Boolean,
    val isArchived: Boolean,
    val readProgress: Int,
    val thumbnailSrc: String,
    val imageSrc: String,
    val iconSrc: String,
    val labels: List<String>,
    val type: BookmarkEntity.Type,
    val readingTime: Int?,
    val created: Instant,
    val wordCount: Int?,
    val published: Instant?
)
