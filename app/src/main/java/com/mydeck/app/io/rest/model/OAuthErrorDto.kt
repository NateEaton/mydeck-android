package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthErrorDto(
    @SerialName("error")
    val error: String,

    @SerialName("error_description")
    val errorDescription: String? = null
)
