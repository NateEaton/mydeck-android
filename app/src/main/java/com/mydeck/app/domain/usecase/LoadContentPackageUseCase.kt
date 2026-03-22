package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.content.AnnotationHtmlEnricher
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import javax.inject.Inject

/**
 * Fetches a full offline content package (JSON + HTML + resources) for a single bookmark
 * via the multipart sync endpoint, then persists it locally.
 *
 * Replaces LoadArticleUseCase for on-demand content downloads in Stage 2.
 */
class LoadContentPackageUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val multipartSyncClient: MultipartSyncClient,
    private val contentPackageManager: ContentPackageManager,
    private val contentPackageDao: ContentPackageDao,
    private val bookmarkDao: BookmarkDao,
    private val connectivityMonitor: ConnectivityMonitor,
    private val readeckApi: ReadeckApi
) {
    sealed class Result {
        data object Success : Result()
        data object AlreadyDownloaded : Result()
        data class TransientFailure(val reason: String) : Result()
        data class PermanentFailure(val reason: String) : Result()
    }

    /**
     * Force re-download a content package, bypassing DOWNLOADED and freshness guards.
     * Used for annotation-driven refresh where HTML needs updating even though the
     * bookmark's updated timestamp hasn't changed.
     */
    suspend fun executeForceRefresh(bookmarkId: String): Result {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

        if (bookmark.type is Bookmark.Type.Video) {
            return Result.PermanentFailure("Video bookmarks are not eligible for offline content packages")
        }
        if (!connectivityMonitor.isNetworkAvailable()) {
            return Result.TransientFailure("Offline")
        }

        return fetchAndCommit(bookmarkId, bookmark)
    }

    suspend fun execute(
        bookmarkId: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
        onProgress?.invoke(0.1f)

        // Guard: never re-fetch downloaded content
        if (bookmark.contentState == ContentState.DOWNLOADED) {
            return Result.AlreadyDownloaded
        }

        // Freshness check: if content is DIRTY but the existing package's sourceUpdated
        // matches the bookmark's current updated timestamp, the content hasn't changed —
        // just restore DOWNLOADED state without re-fetching.
        if (bookmark.contentState == ContentState.DIRTY) {
            val existingPkg = contentPackageDao.getPackage(bookmarkId)
            val pkgUpdatedInstant = existingPkg?.sourceUpdated?.let { parseInstantLenient(it) }
            val bookmarkUpdatedInstant = bookmark.updated.toInstant(TimeZone.currentSystemDefault())
            if (existingPkg != null && pkgUpdatedInstant != null && pkgUpdatedInstant == bookmarkUpdatedInstant) {
                val contentDir = contentPackageManager.getContentDir(bookmarkId)
                if (contentDir != null) {
                    bookmarkDao.updateContentState(
                        bookmarkId,
                        com.mydeck.app.io.db.model.BookmarkEntity.ContentState.DOWNLOADED.value,
                        null
                    )
                    Timber.d("Content package for $bookmarkId is fresh (sourceUpdated matches), restored DOWNLOADED")
                    return Result.AlreadyDownloaded
                }
            }
        }

        // Guard: don't attempt for permanent no-content
        if (bookmark.contentState == ContentState.PERMANENT_NO_CONTENT) {
            return Result.PermanentFailure(bookmark.contentFailureReason ?: "No content available")
        }

        // For articles and videos, check hasArticle
        if ((bookmark.type is Bookmark.Type.Article || bookmark.type is Bookmark.Type.Video) && !bookmark.hasArticle) {
            bookmarkDao.updateContentState(
                bookmarkId, BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value,
                "No article content available (type=${bookmark.type})"
            )
            return Result.PermanentFailure("No article content available")
        }

        // Fail fast when offline
        if (!connectivityMonitor.isNetworkAvailable()) {
            return Result.TransientFailure("Offline")
        }

        return fetchAndCommit(bookmarkId, bookmark, onProgress)
    }

    private suspend fun fetchAndCommit(
        bookmarkId: String,
        bookmark: Bookmark,
        onProgress: ((Float) -> Unit)? = null
    ): Result {
        return try {
            onProgress?.invoke(0.15f)
            val result = multipartSyncClient.fetchContentPackages(listOf(bookmarkId))
            onProgress?.invoke(0.5f) // Network fetch + multipart parse complete
            when (result) {
                is MultipartSyncClient.Result.Success -> {
                    val pkg = result.packages.firstOrNull { it.bookmarkId == bookmarkId }
                    if (pkg == null) {
                        bookmarkDao.updateContentState(
                            bookmarkId,
                            BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value,
                            "Server returned no content for this bookmark"
                        )
                        return Result.PermanentFailure("Server returned no content")
                    }

                    // Update metadata from JSON part if present
                    if (pkg.json != null) {
                        val domainBookmark = pkg.json.toDomain()
                        bookmarkRepository.insertBookmarks(listOf(domainBookmark))
                    }
                    onProgress?.invoke(0.6f) // Metadata updated

                    // Determine package kind
                    val packageKind = when (bookmark.type) {
                        is Bookmark.Type.Article -> "ARTICLE"
                        is Bookmark.Type.Picture -> "PICTURE"
                        is Bookmark.Type.Video -> "VIDEO"
                    }

                    // For pictures without HTML, generate wrapper
                    val effectivePkg = if (bookmark.type is Bookmark.Type.Picture && pkg.html == null) {
                        val primaryImage = pkg.resources.firstOrNull { it.group == "image" }
                        if (primaryImage != null) {
                            val wrapperHtml = contentPackageManager.generatePictureWrapperHtml(
                                imagePath = primaryImage.path,
                                description = bookmark.description
                            )
                            pkg.copy(html = wrapperHtml)
                        } else {
                            pkg
                        }
                    } else {
                        pkg
                    }

                    // Enrich bare <rd-annotation> tags with attributes from API
                    val enrichedPkg = enrichAnnotations(bookmarkId, effectivePkg)
                    onProgress?.invoke(0.75f) // Annotations enriched

                    val sourceUpdatedInstant = pkg.json?.updated ?: bookmark.updated.toInstant(TimeZone.currentSystemDefault())
                    val committed = contentPackageManager.commitPackage(
                        enrichedPkg, packageKind, sourceUpdatedInstant.toString()
                    )
                    onProgress?.invoke(0.95f) // Content committed

                    if (committed) {
                        Timber.i("Content package downloaded for $bookmarkId (kind=$packageKind)")
                        Result.Success
                    } else {
                        Result.TransientFailure("Failed to commit package to storage")
                    }
                }

                is MultipartSyncClient.Result.Error -> {
                    bookmarkDao.updateContentState(
                        bookmarkId, BookmarkEntity.ContentState.DIRTY.value, result.message
                    )
                    Result.TransientFailure(result.message)
                }

                is MultipartSyncClient.Result.NetworkError -> {
                    bookmarkDao.updateContentState(
                        bookmarkId, BookmarkEntity.ContentState.DIRTY.value, result.message
                    )
                    Result.TransientFailure(result.message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error fetching content package for $bookmarkId")
            bookmarkDao.updateContentState(
                bookmarkId, BookmarkEntity.ContentState.DIRTY.value, "Unexpected: ${e.message}"
            )
            Result.TransientFailure("Unexpected error: ${e.message}")
        }
    }

    /**
     * Enrich bare `<rd-annotation>` tags in the package HTML with proper attributes
     * from the annotations REST API. The Readeck multipart sync endpoint strips
     * annotation attributes; this step restores them for offline highlight support.
     *
     * If the API call fails, returns the package unchanged — annotations will render
     * as yellow but won't support tap-to-edit or offline listing.
     */
    private suspend fun enrichAnnotations(
        bookmarkId: String,
        pkg: com.mydeck.app.io.rest.sync.BookmarkSyncPackage
    ): com.mydeck.app.io.rest.sync.BookmarkSyncPackage {
        if (pkg.html == null || !pkg.html.contains("<rd-annotation>")) {
            return pkg
        }

        return try {
            val response = readeckApi.getAnnotations(bookmarkId)
            if (response.isSuccessful) {
                val annotations = response.body().orEmpty()
                if (annotations.isNotEmpty()) {
                    val enrichedHtml = AnnotationHtmlEnricher.enrich(pkg.html, annotations)
                    if (enrichedHtml != pkg.html) {
                        pkg.copy(html = enrichedHtml)
                    } else {
                        pkg
                    }
                } else {
                    pkg
                }
            } else {
                Timber.w("Annotation enrichment failed for $bookmarkId: HTTP ${response.code()}")
                pkg
            }
        } catch (e: Exception) {
            Timber.w(e, "Annotation enrichment failed for $bookmarkId")
            pkg
        }
    }

    /**
     * Batch download content packages for up to 10 IDs per request, committing those
     * that succeed and returning a per-ID result for worker fallback decisions.
     */
    suspend fun executeBatch(bookmarkIds: List<String>): Map<String, Result> {
        if (bookmarkIds.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Result>()

        fun markAll(ids: List<String>, reason: String): Map<String, Result> {
            val map = ids.associateWith { Result.TransientFailure(reason) }
            results.putAll(map)
            return map
        }

        val chunks = bookmarkIds.chunked(10)
        for (chunk in chunks) {
            try {
                val fetch = multipartSyncClient.fetchContentPackages(chunk)
                when (fetch) {
                    is MultipartSyncClient.Result.Success -> {
                        val pkgsById = fetch.packages.associateBy { it.bookmarkId }
                        for (id in chunk) {
                            val pkg = pkgsById[id]
                            if (pkg == null) {
                                results[id] = Result.TransientFailure("No package returned for id")
                                continue
                            }

                            val bookmark = try { bookmarkRepository.getBookmarkById(id) } catch (_: Exception) { null }
                            if (bookmark == null) {
                                results[id] = Result.PermanentFailure("Bookmark not found")
                                continue
                            }

                            // Update metadata if provided
                            pkg.json?.let { dto ->
                                try { bookmarkRepository.insertBookmarks(listOf(dto.toDomain())) } catch (_: Exception) {}
                            }

                            // Determine package kind
                            val packageKind = when (bookmark.type) {
                                is Bookmark.Type.Article -> "ARTICLE"
                                is Bookmark.Type.Picture -> "PICTURE"
                                is Bookmark.Type.Video -> "VIDEO"
                            }

                            // For pictures without HTML, generate wrapper
                            val effectivePkg = if (bookmark.type is Bookmark.Type.Picture && pkg.html == null) {
                                val primaryImage = pkg.resources.firstOrNull { it.group == "image" }
                                if (primaryImage != null) {
                                    val wrapperHtml = contentPackageManager.generatePictureWrapperHtml(
                                        imagePath = primaryImage.path,
                                        description = bookmark.description
                                    )
                                    pkg.copy(html = wrapperHtml)
                                } else pkg
                            } else pkg

                            // Enrich annotations if needed
                            val enrichedPkg = try { enrichAnnotations(id, effectivePkg) } catch (_: Exception) { effectivePkg }

                            val sourceUpdatedInstant = pkg.json?.updated ?: bookmark.updated.toInstant(TimeZone.currentSystemDefault())
                            val committed = contentPackageManager.commitPackage(
                                enrichedPkg, packageKind, sourceUpdatedInstant.toString()
                            )
                            results[id] = if (committed) Result.Success else Result.TransientFailure("Commit failed")
                        }
                    }
                    is MultipartSyncClient.Result.Error -> {
                        markAll(chunk, fetch.message)
                    }
                    is MultipartSyncClient.Result.NetworkError -> {
                        val msg = fetch.message
                        markAll(chunk, msg)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Batch execute failed for ids=${chunk.size}")
                markAll(chunk, "Unexpected error: ${e.message}")
            }
        }
        return results
    }

    private fun parseInstantLenient(value: String): Instant? {
        // Try ISO Instant first
        runCatching { return Instant.parse(value) }
        // Fallback: try LocalDateTime ISO then convert using system TZ
        return runCatching { LocalDateTime.parse(value).toInstant(TimeZone.currentSystemDefault()) }.getOrNull()
    }
}
