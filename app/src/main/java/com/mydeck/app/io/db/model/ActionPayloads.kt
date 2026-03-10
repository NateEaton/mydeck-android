package com.mydeck.app.io.db.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TogglePayload(val value: Boolean)

@Serializable
data class ProgressPayload(val progress: Int, val timestamp: Instant)

@Serializable
data class LabelsPayload(val labels: List<String>)

@Serializable
data class TitlePayload(val title: String)

@Serializable
data class MetadataPayload(
    val title: String,
    val description: String,
    val siteName: String,
    val authors: List<String>,
    val published: Instant?,
    val lang: String,
    val textDirection: String?
)
