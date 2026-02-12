package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenRequestDto(
    @SerialName("grant_type")
    val grantType: String,

    @SerialName("client_id")
    val clientId: String,

    @SerialName("device_code")
    val deviceCode: String
)
