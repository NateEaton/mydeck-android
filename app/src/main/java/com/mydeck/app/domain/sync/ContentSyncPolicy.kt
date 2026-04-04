package com.mydeck.app.domain.sync

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class OfflinePolicy {
    STORAGE_LIMIT,
    NEWEST_N,
    DATE_RANGE
}

enum class OfflineContentScope(val includesArchived: Boolean) {
    MY_LIST(includesArchived = false),
    MY_LIST_AND_ARCHIVED(includesArchived = true)
}

enum class OfflineImageStorageLimit(val bytes: Long) {
    // Test limits — remove before release
    MB_5(5L * 1024 * 1024),
    MB_10(10L * 1024 * 1024),
    MB_20(20L * 1024 * 1024),
    // Production limits
    MB_100(100L * 1024 * 1024),
    MB_250(250L * 1024 * 1024),
    MB_500(500L * 1024 * 1024),
    GB_1(1024L * 1024 * 1024),
    UNLIMITED(Long.MAX_VALUE)
}

object OfflinePolicyDefaults {
    const val STORAGE_LIMIT_BYTES: Long = 100L * 1024 * 1024
    const val NEWEST_N: Int = 100
    val DATE_RANGE_WINDOW: Duration = 30.days
    const val MAX_STORAGE_CAP_BYTES: Long = Long.MAX_VALUE
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
