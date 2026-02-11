package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthRevokeRequestDto(
    @SerialName("token")
    val token: String
)
