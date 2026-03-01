package com.mydeck.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Data sent from JavaScript when an image is tapped.
 * Contains ALL qualifying images in the article for gallery navigation,
 * plus the index of the tapped image.
 */
@Serializable
data class ImageGalleryData(
    val images: List<GalleryImage>,
    val currentIndex: Int,
)
