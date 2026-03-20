package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val id: List<String>,
    @SerialName("with_json")
    val withJson: Boolean = true,
    @SerialName("with_html")
    val withHtml: Boolean = false,
    @SerialName("with_resources")
    val withResources: Boolean = false,
    @SerialName("resource_prefix")
    val resourcePrefix: String? = null
)
