package com.mydeck.app.io.rest.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncContentRequestDto(
    val ids: List<String>
)
