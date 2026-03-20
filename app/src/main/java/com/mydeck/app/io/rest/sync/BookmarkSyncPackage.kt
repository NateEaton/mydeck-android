package com.mydeck.app.io.rest.sync

import com.mydeck.app.io.rest.model.BookmarkDto
import java.io.File

/**
 * Assembled result of parsing a multipart sync response for a single bookmark.
 */
data class BookmarkSyncPackage(
    val bookmarkId: String,
    val json: BookmarkDto? = null,
    val html: String? = null,
    val resources: List<ResourcePart> = emptyList(),
    val parseWarnings: List<String> = emptyList()
)

/**
 * A single resource part from the multipart response, staged to a temporary file on disk.
 */
data class ResourcePart(
    val path: String,
    val filename: String,
    val mimeType: String,
    val group: String?,
    val tempFile: File,
    val byteSize: Long
)
