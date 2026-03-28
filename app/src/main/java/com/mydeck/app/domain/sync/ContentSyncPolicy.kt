package com.mydeck.app.domain.sync

import kotlinx.datetime.LocalDate

enum class ContentSyncMode {
    AUTOMATIC,   // Content fetched during bookmark sync
    MANUAL,      // Content fetched only when user opens a bookmark
    DATE_RANGE   // Content fetched for a user-specified date range on demand
}

enum class OfflineContentScope(val includesArchived: Boolean) {
    MY_LIST(includesArchived = false),
    MY_LIST_AND_ARCHIVED(includesArchived = true)
}

enum class OfflineImageStorageLimit(val bytes: Long) {
    MB_100(100L * 1024 * 1024),
    MB_250(250L * 1024 * 1024),
    MB_500(500L * 1024 * 1024),
    GB_1(1024L * 1024 * 1024),
    UNLIMITED(Long.MAX_VALUE)
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
