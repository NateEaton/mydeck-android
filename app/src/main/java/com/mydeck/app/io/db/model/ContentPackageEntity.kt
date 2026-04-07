package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a downloaded content package for a bookmark.
 * 
 * The hasResources field distinguishes between text-only cached content
 * and full offline packages:
 * 
 * - hasResources=false: Text cached on-demand; images lazy-load from network.
 *   This is a performance optimization, not managed offline content.
 * - hasResources=true: Full offline package (text + images); subject to
 *   offline storage management policies.
 */
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
    /**
     * Whether this package includes downloaded resources (images, etc.).
     * 
     * false = Text-only content cached on-demand for fast re-open performance.
     * true = Full offline package with text + all resources; managed offline.
     */
    val hasResources: Boolean,
    val sourceUpdated: String,   // bookmark updated timestamp at time of package download
    val lastRefreshed: Long,     // epoch millis of last successful refresh
    val localBasePath: String    // relative path under app-private storage
)
