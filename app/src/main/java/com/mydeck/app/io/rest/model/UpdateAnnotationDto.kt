package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateAnnotationDto(
    val color: String,
)
