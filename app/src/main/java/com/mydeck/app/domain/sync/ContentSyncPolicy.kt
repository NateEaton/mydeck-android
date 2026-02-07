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
    val preset: DateRangePreset = DateRangePreset.PAST_MONTH,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val downloading: Boolean = false
)
