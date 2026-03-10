package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class BookmarkMetadataUpdate(
    val title: String,
    val description: String,
    val siteName: String,
    val authors: List<String>,
    val published: Instant?,
    val lang: String,
    val textDirection: String?
)
