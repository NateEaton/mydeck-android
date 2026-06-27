package com.mydeck.app.io.db.model

import kotlinx.datetime.Instant

/**
 * Lightweight bookmark entity for list display.
 * 
 * The hasResources field (derived from ContentPackageEntity) indicates
 * offline availability for bookmark card icons:
 * 
 * - null: No local content (NOT_ATTEMPTED)
 * - false: Text cached on-demand; outline download icon
 * - true: Full offline package; filled download icon
 */
data class BookmarkListItemEntity(
    val id: String,
    val href: String,
    val url: String,
    val title: String,
    val siteName: String,
    val isMarked: Boolean,
    val isArchived: Boolean,
    val readProgress: Int,
    val thumbnailSrc: String,
    val imageSrc: String,
    val iconSrc: String,
    val labels: List<String>,
    val type: BookmarkEntity.Type,
    val readingTime: Int?,
    val created: Instant,
    val wordCount: Int?,
    val published: Instant?,
    /** Server's article-content flag; with type/contentState drives offline eligibility (pinnability). */
    val hasArticle: Boolean,
    val contentState: BookmarkEntity.ContentState,
    /** Server-reported extraction error flag (errors array non-empty), reconciled by the metadata sync. */
    val hasServerErrors: Boolean,
    /** Loaded/error/loading state; [BookmarkEntity.State.ERROR] is the hard-error case the badge also covers. */
    val state: BookmarkEntity.State,
    /**
     * Whether this bookmark has downloaded resources (images).
     *
     * Combined with contentState to determine offline availability:
     * - null: No content package exists
     * - false: Text cached on-demand only
     * - true: Full offline package with resources
     */
    val hasResources: Boolean?,
    /**
     * Provenance of the content package (W2): `AUTOMATIC`, `MANUAL`, or null when
     * no package exists. Drives the MANUAL icon tint (W9). See
     * [com.mydeck.app.domain.content.ContentSource].
     */
    val source: String?
)
