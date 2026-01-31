package com.mydeck.app.domain.model

import kotlinx.datetime.LocalDateTime

data class BookmarkListItem(
    val id: String,
    val url: String,
    val title: String,
    val siteName: String,
    val isMarked: Boolean,
    val isArchived: Boolean,
    val isRead: Boolean,
    val readProgress: Int,
    val thumbnailSrc: String,
    val iconSrc: String,
    val imageSrc: String,
    val labels: List<String>,
    val type: Bookmark.Type,
    val readingTime: Int?,
    val created: LocalDateTime,
    val wordCount: Int?
)
