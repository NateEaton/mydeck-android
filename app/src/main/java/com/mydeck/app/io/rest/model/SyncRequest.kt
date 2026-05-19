package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val id: List<String>,
    // Boolean fields default to false (= don't include that content type).
    // The shared Json instance uses encodeDefaults=false, so a caller passing
    // an explicit `true` differs from the default and serializes; an unset
    // field is omitted from the body, which the server treats as false.
    @SerialName("with_json")
    val withJson: Boolean = false,
    @SerialName("with_html")
    val withHtml: Boolean = false,
    @SerialName("with_resources")
    val withResources: Boolean = false,
    @SerialName("resource_prefix")
    val resourcePrefix: String? = null
)
