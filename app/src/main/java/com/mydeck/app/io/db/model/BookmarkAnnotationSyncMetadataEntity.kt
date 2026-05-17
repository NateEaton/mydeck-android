package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "bookmark_annotation_sync_metadata")
data class BookmarkAnnotationSyncMetadataEntity(
    @PrimaryKey val bookmarkId: String,
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val lastFailureAt: Instant? = null,
    val failureCount: Int = 0,
    val backoffUntil: Instant? = null,
)
