package com.mydeck.app.domain.model

data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val highlights: List<HighlightSummary>,  // ordered by created descending
)
