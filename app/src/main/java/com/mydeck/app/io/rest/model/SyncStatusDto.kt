package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncStatusDto(
    val id: String,
    val type: String,  // "update" or "delete"
    val time: String? = null  // ISO 8601 timestamp
)
