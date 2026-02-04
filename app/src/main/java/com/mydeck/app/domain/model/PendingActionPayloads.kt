package com.mydeck.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProgressPayload(
    val progress: Int,
    val timestamp: Instant
)

@Serializable
data class TogglePayload(
    val value: Boolean
)
