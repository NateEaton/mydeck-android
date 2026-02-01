package com.mydeck.app.domain.model

enum class SortOption(val sqlOrderBy: String, val displayName: String) {
    ADDED_NEWEST("created DESC", "Added, newest first"),
    ADDED_OLDEST("created ASC", "Added, oldest first"),
    PUBLISHED_NEWEST("published DESC", "Published, newest first"),
    PUBLISHED_OLDEST("published ASC", "Published, oldest first"),
    TITLE_A_TO_Z("title COLLATE NOCASE ASC", "Title, A to Z"),
    TITLE_Z_TO_A("title COLLATE NOCASE DESC", "Title, Z to A"),
    SITE_A_TO_Z("siteName COLLATE NOCASE ASC", "Site Name, A to Z"),
    SITE_Z_TO_A("siteName COLLATE NOCASE DESC", "Site Name, Z to A"),
    DURATION_SHORTEST("readingTime ASC", "Duration, shortest first"),
    DURATION_LONGEST("readingTime DESC", "Duration, longest first")
}
