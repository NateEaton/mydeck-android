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

            // Persist manifest in Room
            val packageEntity = ContentPackageEntity(
                bookmarkId = bookmarkId,
                packageKind = packageKind,
                hasHtml = pkg.html != null,
                hasResources = resourceEntities.isNotEmpty(),
                sourceUpdated = sourceUpdated,
                lastRefreshed = System.currentTimeMillis(),
                localBasePath = "offline_content/$bookmarkId"
            )

            // Extract annotation metadata from HTML
            val annotationEntities = AnnotationHtmlParser.parse(pkg.html, bookmarkId)

            if (annotationEntities.isEmpty() && pkg.html != null && pkg.html.contains("rd-annotation")) {
                Timber.w("HTML for $bookmarkId contains rd-annotation tags but no annotations could be extracted (bare tags?)")
            }

            contentPackageDao.replacePackageAndResources(packageEntity, resourceEntities)
            cachedAnnotationDao.replaceAnnotationsForBookmark(bookmarkId, annotationEntities)

            if (annotationEntities.isNotEmpty()) {
                Timber.d("Cached ${annotationEntities.size} annotations for $bookmarkId")
            }

            // Atomic swap: delete old, rename staging to final
            if (finalDir.exists()) {
                finalDir.deleteRecursively()
            }
            val renamed = stagingDir.renameTo(finalDir)
            if (!renamed) {
                // Fallback: copy then delete staging
                stagingDir.copyRecursively(finalDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            // Update content state
            bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.DOWNLOADED.value, null)

            Timber.d("Package committed for $bookmarkId: kind=$packageKind, html=${pkg.html != null}, resources=${resourceEntities.size}")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to commit package for $bookmarkId")
            // Clean up staging
            stagingDir.deleteRecursively()
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
