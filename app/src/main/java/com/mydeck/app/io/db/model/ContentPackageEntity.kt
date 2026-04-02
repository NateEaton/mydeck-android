package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_package",
    foreignKeys = [ForeignKey(
        entity = BookmarkEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookmarkId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ContentPackageEntity(
    @PrimaryKey
    val bookmarkId: String,
    val packageKind: String,     // ARTICLE, PICTURE, VIDEO
    val hasHtml: Boolean,
    val hasResources: Boolean,
    val sourceUpdated: String,   // bookmark updated timestamp at time of package download
    val lastRefreshed: Long,     // epoch millis of last successful refresh
    val localBasePath: String    // relative path under app-private storage
)
