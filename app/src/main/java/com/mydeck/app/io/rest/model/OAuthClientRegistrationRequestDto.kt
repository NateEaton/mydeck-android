package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthClientRegistrationRequestDto(
    @SerialName("client_name")
    val clientName: String,

    @SerialName("client_uri")
    val clientUri: String,

    @SerialName("software_id")
    val softwareId: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("grant_types")
    val grantTypes: List<String>
)
