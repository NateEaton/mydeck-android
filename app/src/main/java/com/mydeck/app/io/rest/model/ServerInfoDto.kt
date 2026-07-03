package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoDto(
    @SerialName("version")
    val version: VersionDto,
    // Servers older than 0.21 (which introduced OAuth) never send this key at all,
    // rather than sending an empty list — default to empty so parsing succeeds and
    // the caller's "does not support oauth" check fires instead of a deserialization error.
    @SerialName("features")
    val features: List<String> = emptyList()
)

@Serializable
data class VersionDto(
    @SerialName("canonical")
    val canonical: String,
    @SerialName("release")
    val release: String,
    @SerialName("build")
    val build: String
)
