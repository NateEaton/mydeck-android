package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class HighlightsSyncMetadata(
    val lastGlobalAttemptAt: Instant? = null,
    val lastGlobalSuccessAt: Instant? = null,
    val lastGlobalFailureAt: Instant? = null,
    val globalFailureCount: Int = 0,
    val globalBackoffUntil: Instant? = null,
    val cacheComplete: Boolean = false,
    val skippedOrphanAnnotationCount: Int = 0,
    val missingBookmarkCount: Int = 0,
    val firstOrphanOffset: Int? = null,
    val lastOrphanWarningAt: Instant? = null,
)
