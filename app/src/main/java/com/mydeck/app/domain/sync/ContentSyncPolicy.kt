package com.mydeck.app.domain.sync

import kotlinx.datetime.LocalDate

enum class ContentSyncMode {
    AUTOMATIC,   // Content fetched during bookmark sync
    MANUAL,      // Content fetched only when user opens a bookmark
    DATE_RANGE   // Content fetched for a user-specified date range on demand
}

data class ContentSyncConstraints(
    val wifiOnly: Boolean,
    val allowOnBatterySaver: Boolean
)

data class DateRangeParams(
    val from: LocalDate,
    val to: LocalDate
)
