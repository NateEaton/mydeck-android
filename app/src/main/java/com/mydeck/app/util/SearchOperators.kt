package com.mydeck.app.util

data class ParsedSearchQuery(
    val textQuery: String
)

fun parseSearchQuery(query: String): ParsedSearchQuery {
    return ParsedSearchQuery(textQuery = query.trim())
}
