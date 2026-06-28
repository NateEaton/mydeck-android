package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache row for a Readeck collection. The flat filter columns mirror the API fields; [type]
 * and [readStatus] are stored as JSON arrays via the existing [com.mydeck.app.io.db.Converters].
 */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPinned: Boolean,
    val search: String?,
    val title: String?,
    val author: String?,
    val site: String?,
    val labels: String?,
    val type: List<String>,
    val readStatus: List<String>,
    val isMarked: Boolean?,
    val isArchived: Boolean?,
    val rangeStart: String?,
    val rangeEnd: String?,
    val created: Long,
    val updated: Long,
)
