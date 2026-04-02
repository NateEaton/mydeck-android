package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_resource",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookmarkId"])]
)
data class ContentResourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookmarkId: String,
    val path: String,            // logical path from multipart Path header
    val mimeType: String,
    val group: String?,          // icon, image, thumbnail, embedded
    val localRelativePath: String,
    val byteSize: Long
)
