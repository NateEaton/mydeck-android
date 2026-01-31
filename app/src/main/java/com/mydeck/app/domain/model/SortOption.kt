package com.mydeck.app.domain.model

enum class SortOption(val sqlOrderBy: String, val displayName: String) {
    NEWEST_ADDED("created DESC", "Newest Saved"),
    OLDEST_ADDED("created ASC", "Oldest Saved"),
    LONGEST_ARTICLE("readingTime DESC", "Longest Articles"),
    SHORTEST_ARTICLE("readingTime ASC", "Shortest Articles"),
    READ_PROGRESS("readProgress ASC", "Read Progress")
}
