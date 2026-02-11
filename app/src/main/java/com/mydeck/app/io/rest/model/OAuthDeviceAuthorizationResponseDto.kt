package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthDeviceAuthorizationResponseDto(
    @SerialName("device_code")
    val deviceCode: String,

    @SerialName("user_code")
    val userCode: String,

    @SerialName("verification_uri")
    val verificationUri: String,

    @SerialName("verification_uri_complete")
    val verificationUriComplete: String? = null,

    @SerialName("expires_in")
    val expiresIn: Int,

    @SerialName("interval")
    val interval: Int
)
