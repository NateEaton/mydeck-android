package com.mydeck.app.domain.model

import java.util.concurrent.TimeUnit


enum class AutoSyncTimeframe(val repeatInterval: Long, val repeatIntervalTimeUnit: TimeUnit) {
    MANUAL(0L, TimeUnit.MILLISECONDS),
    MINUTES_15(15L, TimeUnit.MINUTES),
    HOURS_01(1L, TimeUnit.HOURS),
    HOURS_06(6L, TimeUnit.HOURS),
    HOURS_12(12L, TimeUnit.HOURS),
    DAYS_01(1L, TimeUnit.DAYS),
    DAYS_07(7L, TimeUnit.DAYS),
    DAYS_14(14L, TimeUnit.DAYS),
    DAYS_30(30L, TimeUnit.DAYS)
}