package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_annotation",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookmarkId"])]
)
data class CachedAnnotationEntity(
    @PrimaryKey
    val id: String,
    val bookmarkId: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String
)
