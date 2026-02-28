package com.mydeck.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single image in the article gallery.
 * Built by the JavaScript image interceptor and sent via the bridge.
 */
@Serializable
data class GalleryImage(
    val src: String,
    val alt: String,
    val linkHref: String? = null,
    val linkType: String = "none",
)
