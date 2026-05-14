package com.mydeck.app.domain.model

import kotlinx.datetime.LocalDate

data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val groupDate: LocalDate,
    val highlights: List<HighlightSummary>,  // ordered by created descending
)
