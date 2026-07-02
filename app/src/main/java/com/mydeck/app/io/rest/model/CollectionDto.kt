package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server representation of a Readeck collection (a saved set of filter criteria).
 *
 * Returned by `GET /bookmarks/collections`, `GET /bookmarks/collections/{id}`, and
 * `PATCH /bookmarks/collections/{id}`. The flat filter fields map to [com.mydeck.app.domain.model.FilterFormState]
 * via the extension functions in `domain/model/CollectionFilterAdapter.kt`.
 */
@Serializable
data class CollectionDto(
    val id: String,
    val href: String,
    val created: String,
    val updated: String,
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
    @SerialName("has_errors")
    val hasErrors: Boolean? = null,
    @SerialName("has_labels")
    val hasLabels: Boolean? = null,
    @SerialName("range_start")
    val rangeStart: String? = null,
    @SerialName("range_end")
    val rangeEnd: String? = null,
)
