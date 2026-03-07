package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookmarkId")]
)
data class AnnotationEntity(
    @PrimaryKey
    val id: String,
    val bookmarkId: String,
    val startSelector: String,
    val startOffset: Int,
    val endSelector: String,
    val endOffset: Int,
    val color: String,
    val text: String,
    val created: Long,
)
