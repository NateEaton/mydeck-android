package com.mydeck.app.domain.model

import kotlinx.datetime.LocalDateTime

data class BookmarkListItem(
    val id: String,
    val href: String,
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
    val wordCount: Int?,
    val published: LocalDateTime?,
    val offlineState: OfflineState = OfflineState.NOT_DOWNLOADED
) {
    enum class OfflineState {
        NOT_DOWNLOADED,
        DOWNLOADED_TEXT_ONLY,
        DOWNLOADED_FULL
    }
}
