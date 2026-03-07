package com.mydeck.app.io.rest.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnotationDto(
    val id: String,
    @SerialName("start_selector")
    val startSelector: String,
    @SerialName("start_offset")
    val startOffset: Int,
    @SerialName("end_selector")
    val endSelector: String,
    @SerialName("end_offset")
    val endOffset: Int,
    val created: Instant,
    val text: String,
)
