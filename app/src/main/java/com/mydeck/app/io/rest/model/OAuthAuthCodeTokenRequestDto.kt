package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthAuthCodeTokenRequestDto(
    @SerialName("grant_type")
    val grantType: String,

    @SerialName("code")
    val code: String,

    @SerialName("code_verifier")
    val codeVerifier: String
)
