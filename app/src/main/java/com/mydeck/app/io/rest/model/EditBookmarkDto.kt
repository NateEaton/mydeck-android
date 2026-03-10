package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class EditBookmarkDto(
    @SerialName("add_labels")
    val addLabels: List<String>? = null,
    val authors: List<String>? = null,
    val description: String? = null,
    @SerialName("is_archived")
    val isArchived: Boolean? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean? = null,
    @SerialName("is_marked")
    val isMarked: Boolean? = null,
    val lang: String? = null,
    val labels: List<String>? = null,
    val published: Instant? = null,
    @SerialName("read_anchor")
    val readAnchor: String? = null,
    @SerialName("read_progress")
    val readProgress: Int? = null,
    @SerialName("remove_labels")
    val removeLabels: List<String>? = null,
    @SerialName("site_name")
    val siteName: String? = null,
    @SerialName("text_direction")
    val textDirection: String? = null,
    val title: String? = null
)
