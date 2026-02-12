package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthClientRegistrationResponseDto(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("client_name")
    val clientName: String,

    @SerialName("client_uri")
    val clientUri: String,

    @SerialName("grant_types")
    val grantTypes: List<String>? = null,

    @SerialName("software_id")
    val softwareId: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("logo_uri")
    val logoUri: String? = null,

    @SerialName("redirect_uris")
    val redirectUris: List<String>? = null,

    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String? = null,

    @SerialName("response_types")
    val responseTypes: List<String>? = null
)
