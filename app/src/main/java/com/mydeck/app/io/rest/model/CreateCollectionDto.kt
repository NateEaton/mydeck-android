package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /bookmarks/collections` (create).
 *
 * All fields except [name] are optional. Because the app's [kotlinx.serialization.json.Json] is
 * configured with `explicitNulls = false` and without `encodeDefaults`, fields left at their default
 * `null` are omitted from the wire body, so a sparse collection sends only the filter keys the user
 * actually populated.
 */
@Serializable
data class CreateCollectionDto(
    val name: String,
    @SerialName("is_pinned")
    val isPinned: Boolean? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean? = null,
    val search: String? = null,
    val title: String? = null,
    val author: String? = null,
    val site: String? = null,
    val type: List<String>? = null,
    val labels: String? = null,
    @SerialName("read_status")
    val readStatus: List<String>? = null,
    @SerialName("is_marked")
    val isMarked: Boolean? = null,
    @SerialName("is_archived")
    val isArchived: Boolean? = null,
    @SerialName("range_start")
    val rangeStart: String? = null,
    @SerialName("range_end")
    val rangeEnd: String? = null,
)

/** PATCH body reuses the same shape (all fields optional). */
typealias UpdateCollectionDto = CreateCollectionDto
