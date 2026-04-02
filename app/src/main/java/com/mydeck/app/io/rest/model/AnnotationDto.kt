package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationDto(
    val id: String,
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val created: String,
    val text: String,
    val color: String = "yellow",
    val note: String = ""
)

@Serializable
data class CreateAnnotationDto(
    val start_selector: String,
    val start_offset: Int,
    val end_selector: String,
    val end_offset: Int,
    val color: String
)

@Serializable
data class UpdateAnnotationDto(
    val color: String
)
