package com.mydeck.app.domain.content

import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.ContentPackageEntity
import com.mydeck.app.io.db.model.ContentResourceEntity
import com.mydeck.app.io.rest.sync.BookmarkSyncPackage
import com.mydeck.app.io.rest.sync.ResourcePart
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages local file storage for offline content packages.
 *
 * Handles staging, atomic commit, and cleanup of per-bookmark content directories
 * under files/offline_content/<bookmarkId>/.
 */
@Singleton
class ContentPackageManager @Inject constructor(
    private val contentPackageDao: ContentPackageDao,
    private val cachedAnnotationDao: CachedAnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val offlineContentDir: File
) {

    /**
     * Commit a successfully parsed content package to local storage.
     *
     * 1. Stage files into a temp directory
     * 2. Persist manifest + resources in Room
     * 3. Atomically move staging dir to final location
     * 4. Update contentState to DOWNLOADED
     */
    suspend fun commitPackage(
        pkg: BookmarkSyncPackage,
        packageKind: String,
        sourceUpdated: String
    ): Boolean {
        val bookmarkId = pkg.bookmarkId
        val finalDir = File(offlineContentDir, bookmarkId)
        val stagingDir = File(offlineContentDir, "${bookmarkId}_staging")
        val backupDir = File(offlineContentDir, "${bookmarkId}_backup_${System.nanoTime()}")
        var snapshotLoaded = false
        var existingPackage: ContentPackageEntity? = null
        var existingResources: List<ContentResourceEntity> = emptyList()
        var existingAnnotations: List<com.mydeck.app.io.db.model.CachedAnnotationEntity> = emptyList()

        suspend fun restoreDbSnapshot() {
            if (!snapshotLoaded) return
            if (existingPackage != null) {
                contentPackageDao.replacePackageAndResources(existingPackage!!, existingResources)
            } else {
                contentPackageDao.deleteResources(bookmarkId)
                contentPackageDao.deletePackage(bookmarkId)
            }
            if (existingAnnotations.isNotEmpty()) {
                cachedAnnotationDao.replaceAnnotationsForBookmark(bookmarkId, existingAnnotations)
            } else {
                cachedAnnotationDao.deleteAnnotationsForBookmark(bookmarkId)
            }
        }

        try {
            // Clean up any leftover staging dir
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            // Write HTML entry document
            if (pkg.html != null) {
                File(stagingDir, "index.html").writeText(pkg.html)
            }

            // Move resource temp files into staging dir
            val resourceEntities = mutableListOf<ContentResourceEntity>()
            for (resource in pkg.resources) {
                val targetFile = File(stagingDir, resource.path)
                targetFile.parentFile?.mkdirs()
                moveOrCopy(resource.tempFile, targetFile)
                resourceEntities.add(
                    ContentResourceEntity(
                        bookmarkId = bookmarkId,
                        path = resource.path,
                        mimeType = resource.mimeType,
                        group = resource.group,
                        localRelativePath = resource.path,
                        byteSize = targetFile.length()
                    )
                )
            }

            // Prepare DB entities (computed before swap, persisted before swap)
            val packageEntity = ContentPackageEntity(
                bookmarkId = bookmarkId,
                packageKind = packageKind,
                hasHtml = pkg.html != null,
                hasResources = resourceEntities.isNotEmpty(),
                sourceUpdated = sourceUpdated,
                lastRefreshed = System.currentTimeMillis(),
                localBasePath = "offline_content/$bookmarkId"
            )

            val annotationEntities = AnnotationHtmlParser.parse(pkg.html, bookmarkId)
            if (annotationEntities.isEmpty() && pkg.html != null && pkg.html.contains("rd-annotation")) {
                Timber.w("HTML for $bookmarkId contains rd-annotation tags but no annotations could be extracted (bare tags?)")
            }
            existingPackage = contentPackageDao.getPackage(bookmarkId)
            existingResources = existingPackage?.let {
                contentPackageDao.getResources(bookmarkId)
            }.orEmpty()
            existingAnnotations = cachedAnnotationDao.getAnnotationsForBookmark(bookmarkId)
            snapshotLoaded = true

            var dbCommitted = false
            try {
                contentPackageDao.replacePackageAndResources(packageEntity, resourceEntities)
                cachedAnnotationDao.replaceAnnotationsForBookmark(bookmarkId, annotationEntities)
                dbCommitted = true
                if (annotationEntities.isNotEmpty()) {
                    Timber.d("Cached ${annotationEntities.size} annotations for $bookmarkId")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist package metadata for $bookmarkId")
                stagingDir.deleteRecursively()
                bookmarkDao.updateContentState(
                    bookmarkId,
                    BookmarkEntity.ContentState.DIRTY.value,
                    "Failed to persist package metadata"
                )
                return false
            }

            // Atomic swap:
            // 1) Move existing final to backup (if present)
            var backupMade = false
            if (finalDir.exists()) {
                backupMade = finalDir.renameTo(backupDir)
                if (!backupMade) {
                    // If rename-to-backup fails, try copying to backup and then delete final
                    try {
                        finalDir.copyRecursively(backupDir, overwrite = true)
                        finalDir.deleteRecursively()
                        backupMade = true
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to create backup for $bookmarkId; aborting commit to preserve existing package")
                        if (dbCommitted) {
                            runCatching { restoreDbSnapshot() }
                                .onFailure { Timber.w(it, "Failed to restore DB snapshot for $bookmarkId") }
                        }
                        stagingDir.deleteRecursively()
                        return false
                    }
                }
            }

            // 2) Move staging to final
            val moved = stagingDir.renameTo(finalDir)
            if (!moved) {
                try {
                    stagingDir.copyRecursively(finalDir, overwrite = true)
                    stagingDir.deleteRecursively()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to promote staging to final for $bookmarkId; attempting rollback")
                    // Restore backup if we made one
                    if (backupMade && backupDir.exists()) {
                        finalDir.deleteRecursively()
                        backupDir.renameTo(finalDir)
                        backupDir.deleteRecursively()
                    }
                    if (dbCommitted) {
                        runCatching { restoreDbSnapshot() }
                            .onFailure { Timber.w(it, "Failed to restore DB snapshot for $bookmarkId") }
                    }
                    // Mark DIRTY and abort
                    bookmarkDao.updateContentState(
                        bookmarkId,
                        BookmarkEntity.ContentState.DIRTY.value,
                        "Failed to finalize package commit"
                    )
                    return false
                }
            }

            // 4) Remove backup now that commit is successful
            if (backupMade && backupDir.exists()) {
                backupDir.deleteRecursively()
            }

            // Update content state
            bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.DOWNLOADED.value, null)

            Timber.d("Package committed for $bookmarkId: kind=$packageKind, html=${pkg.html != null}, resources=${resourceEntities.size}")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to commit package for $bookmarkId")
            // Clean up staging
            stagingDir.deleteRecursively()
            // Restore DB snapshot if we already committed metadata
            runCatching { restoreDbSnapshot() }
                .onFailure { Timber.w(it, "Failed to restore DB snapshot for $bookmarkId") }
            // Mark as dirty
            bookmarkDao.updateContentState(
                bookmarkId,
                BookmarkEntity.ContentState.DIRTY.value,
                "Package commit failed: ${e.message}"
            )
            return false
        }
    }

    /**
     * Check whether the content package for a bookmark includes downloaded resources (images).
     * Returns null if no content package exists for this bookmark.
     */
    suspend fun hasResources(bookmarkId: String): Boolean? {
        return contentPackageDao.getPackage(bookmarkId)?.hasResources
    }

    /**
     * Update only the HTML entry document for a bookmark's existing content package.
     * Used for lightweight annotation refresh without re-downloading resources.
     *
     * @return true if the file was updated, false if the content directory doesn't exist
     */
    fun updateHtml(bookmarkId: String, html: String): Boolean {
        val dir = File(offlineContentDir, bookmarkId)
        if (!dir.exists()) return false
        return try {
            File(dir, "index.html").writeText(html)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to update HTML for $bookmarkId")
            false
        }
    }

    /**
     * Get the local content directory for a bookmark, or null if no package exists.
     */
    fun getContentDir(bookmarkId: String): File? {
        val dir = File(offlineContentDir, bookmarkId)
        return if (dir.exists()) dir else null
    }

    /**
     * Get the HTML entry document for a bookmark, or null if not present.
     */
    fun getHtmlContent(bookmarkId: String): String? {
        val file = File(offlineContentDir, "$bookmarkId/index.html")
        return if (file.exists()) file.readText() else null
    }

    /**
     * Delete all offline content for a single bookmark: files, DB metadata,
     * cached annotations, and reset contentState to NOT_ATTEMPTED.
     */
    suspend fun deletePackage(bookmarkId: String) {
        val dir = File(offlineContentDir, bookmarkId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        contentPackageDao.deleteResources(bookmarkId)
        contentPackageDao.deletePackage(bookmarkId)
        cachedAnnotationDao.deleteAnnotationsForBookmark(bookmarkId)
        bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.NOT_ATTEMPTED.value, null)
        Timber.d("Deleted content package for $bookmarkId")
    }

    /**
     * Delete only the resource files (images, etc.) for a bookmark while
     * keeping the HTML entry document. Updates the content_package row to
     * set hasResources=false and clears the content_resource rows.
     *
     * Callers should re-fetch HTML with absolute image URLs BEFORE calling
     * this method so the reader falls back to lazy-loading images rather than
     * showing broken references.
     */
    suspend fun deleteResources(bookmarkId: String) {
        val pkg = contentPackageDao.getPackage(bookmarkId) ?: return
        val resources = contentPackageDao.getResources(bookmarkId)
        if (resources.isEmpty()) return

        val dir = File(offlineContentDir, bookmarkId)
        for (resource in resources) {
            val file = File(dir, resource.localRelativePath)
            if (file.exists()) {
                file.delete()
            }
        }
        // Clean up empty subdirectories left after resource deletion
        dir.walkBottomUp().filter { it.isDirectory && it != dir && it.listFiles().isNullOrEmpty() }.forEach { it.delete() }

        contentPackageDao.deleteResources(bookmarkId)
        contentPackageDao.insertPackage(pkg.copy(hasResources = false))
        Timber.d("Deleted ${resources.size} resources for $bookmarkId (HTML retained)")
    }

    /**
     * Delete all offline content.
     */
    suspend fun deleteAllContent() {
        contentPackageDao.deleteAll()
        cachedAnnotationDao.deleteAll()
        offlineContentDir.listFiles()?.forEach { it.deleteRecursively() }
        bookmarkDao.resetAllContentState()
    }

    /**
     * Calculate total size of offline content in bytes.
     */
    fun calculateTotalSize(): Long {
        return offlineContentDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Generate a minimal wrapper HTML for picture bookmarks that don't have
     * a server-provided HTML part. References the local primary image.
     */
    fun generatePictureWrapperHtml(
        imagePath: String,
        description: String?
    ): String {
        val escapedDesc = description?.replace("&", "&amp;")
            ?.replace("<", "&lt;")
            ?.replace(">", "&gt;")
            ?.replace("\"", "&quot;")
        val captionHtml = if (!escapedDesc.isNullOrBlank()) {
            """<p class="picture-caption">$escapedDesc</p>"""
        } else ""
        return """<figure class="picture-content"><img src="$imagePath" alt="" />${captionHtml}</figure>"""
    }

    private fun moveOrCopy(src: File, dst: File) {
        if (!src.renameTo(dst)) {
            src.copyTo(dst, overwrite = true)
            src.delete()
        }
    }
}
