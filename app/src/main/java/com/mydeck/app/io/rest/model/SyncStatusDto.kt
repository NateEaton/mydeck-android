package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncStatusDto(
    val id: String,
    val status: String,  // "ok" or "deleted"
    val updated: String? = null  // ISO 8601 timestamp
)
