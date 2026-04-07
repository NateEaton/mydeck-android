package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val id: List<String>,
    // Boolean fields default to false (= don't include that content type).
    // The shared Json instance uses encodeDefaults=true so all fields are always serialized;
    // false values are benign — the server ignores them.
    @SerialName("with_json")
    val withJson: Boolean = false,
    @SerialName("with_html")
    val withHtml: Boolean = false,
    @SerialName("with_resources")
    val withResources: Boolean = false,
    @SerialName("resource_prefix")
    val resourcePrefix: String? = null
)
