package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateAnnotationResponseDto(
    val updated: String,
    val annotations: List<AnnotationDto>,
)
