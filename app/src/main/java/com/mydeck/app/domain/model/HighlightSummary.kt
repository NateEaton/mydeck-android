package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class HighlightSummary(
    val id: String,
    val text: String,
    val color: String,        // "yellow", "red", "blue", "green", or "none"
    val note: String,         // empty string if no note
    val created: Instant,
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
)
