package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthDeviceAuthorizationRequestDto(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("scope")
    val scope: String
)
