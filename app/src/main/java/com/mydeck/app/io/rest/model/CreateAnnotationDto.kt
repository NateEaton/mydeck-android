package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAnnotationDto(
    @SerialName("start_selector")
    val startSelector: String,
    @SerialName("start_offset")
    val startOffset: Int,
    @SerialName("end_selector")
    val endSelector: String,
    @SerialName("end_offset")
    val endOffset: Int,
    val color: String,
)
