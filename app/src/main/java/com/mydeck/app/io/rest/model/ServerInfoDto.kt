package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoDto(
    @SerialName("version")
    val version: VersionDto,
    @SerialName("features")
    val features: List<String>
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
