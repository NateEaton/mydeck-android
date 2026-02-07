package com.mydeck.app.domain.sync

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

enum class DateRangePreset {
    PAST_DAY,
    PAST_WEEK,
    PAST_MONTH,
    PAST_YEAR,
    CUSTOM
}

/**
 * Converts a DateRangePreset to a pair of LocalDate (from, to).
 * "Today" is relative to the provided date.
 *
 * @param today The reference date (typically LocalDate.now())
 * @return Pair of (fromDate, toDate)
 */
fun DateRangePreset.toDateRange(today: LocalDate): Pair<LocalDate, LocalDate> {
    return when (this) {
        DateRangePreset.PAST_DAY -> Pair(today.minus(DatePeriod(days = 1)), today)
        DateRangePreset.PAST_WEEK -> Pair(today.minus(DatePeriod(days = 7)), today)
        DateRangePreset.PAST_MONTH -> Pair(today.minus(DatePeriod(days = 30)), today)
        DateRangePreset.PAST_YEAR -> Pair(today.minus(DatePeriod(days = 365)), today)
        DateRangePreset.CUSTOM -> throw IllegalArgumentException("CUSTOM preset cannot be converted to a date range without explicit dates")
    }
}
