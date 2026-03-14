package com.mydeck.app.util

import com.mydeck.app.domain.model.BookmarkShareFormat

fun formatBookmarkShareText(
    title: String?,
    url: String,
    format: BookmarkShareFormat
): String {
    val trimmedUrl = url.trim()
    val trimmedTitle = title?.trim().orEmpty()

    return when (format) {
        BookmarkShareFormat.URL_ONLY -> trimmedUrl
        BookmarkShareFormat.TITLE_AND_URL_MULTILINE -> {
            if (trimmedTitle.isBlank()) {
                trimmedUrl
            } else {
                "$trimmedTitle\n$trimmedUrl"
            }
        }
    }
}
