package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

/**
 * A named, persisted set of filter criteria. Selecting a collection applies its [filter] to the
 * bookmark list. Collections are managed server-side via the Readeck API and cached locally in Room.
 */
data class Collection(
    val id: String,
    val name: String,
    val isPinned: Boolean,
    val filter: FilterFormState,
    val created: Instant,
    val updated: Instant,
)
