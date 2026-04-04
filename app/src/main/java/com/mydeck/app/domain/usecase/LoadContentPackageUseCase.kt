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
import java.io.File
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

        return fetchAndCommit(bookmarkId = bookmarkId, bookmark = bookmark)
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
                        bookmarkRepository.insertBookmarks(listOf(pkg.json.toDomain()))
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
                        // Direct write of omitDescription after commit to survive concurrent sync races
                        pkg.json?.omitDescription?.let { omitVal ->
                            bookmarkDao.updateOmitDescription(bookmarkId, omitVal)
                        }
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
     * Refresh article HTML for annotation changes.
     *
     * Only call this for bookmarks with a full offline package (hasResources=true).
     * Text-cached bookmarks (hasResources=false) must be handled by the caller via
     * the legacy article path — calling this for them would rewrite absolute image
     * URLs to relative paths that have no local files to back them.
     *
     * Prefers the legacy `/bookmarks/{id}/article` endpoint which returns HTML with
     * fully enriched `<rd-annotation>` tags (correct positioning and attributes).
     * Falls back to the multipart HTML-only fetch + client-side enrichment if the
     * legacy endpoint is unavailable.
     *
     * @return The refreshed HTML body, or null if the refresh failed
     */
    suspend fun refreshHtmlForAnnotations(bookmarkId: String): String? {
        return try {
            // Legacy article endpoint returns HTML with fully enriched <rd-annotation> tags,
            // avoiding the brittle text-matching in AnnotationHtmlEnricher
            val response = readeckApi.getArticle(bookmarkId)
            if (!response.isSuccessful || response.body() == null) {
                Timber.w("Legacy article fetch for annotation refresh failed for $bookmarkId: HTTP ${response.code()}")
                return fallbackMultipartRefresh(bookmarkId)
            }
            var html = response.body()!!

            // Legacy HTML has absolute image URLs — rewrite to relative so the offline
            // asset loader (OfflineContentPathHandler) can serve them from local storage.
            html = rewriteToRelativeResourceUrls(bookmarkId, html)

            contentPackageManager.updateHtml(bookmarkId, html)
            Timber.d("Annotation refresh via legacy article completed for $bookmarkId")
            html
        } catch (e: Exception) {
            Timber.w(e, "Legacy article refresh failed for $bookmarkId, falling back to multipart")
            fallbackMultipartRefresh(bookmarkId)
        }
    }

    /**
     * Fallback annotation refresh using multipart HTML-only fetch + client-side enrichment.
     * Used when the legacy article endpoint is unavailable (e.g., offline after partial sync).
     */
    private suspend fun fallbackMultipartRefresh(bookmarkId: String): String? {
        val result = multipartSyncClient.fetchHtmlOnly(listOf(bookmarkId))
        return when (result) {
            is MultipartSyncClient.Result.Success -> {
                val pkg = result.packages.firstOrNull { it.bookmarkId == bookmarkId }
                if (pkg?.html == null) {
                    Timber.w("HTML-only refresh returned no HTML for $bookmarkId")
                    return null
                }
                
                val enrichedPkg = enrichAnnotations(bookmarkId, pkg)
                val enrichedHtml = enrichedPkg.html ?: pkg.html
                
                contentPackageManager.updateHtml(bookmarkId, enrichedHtml)
                Timber.d("Annotation HTML refresh (multipart fallback) completed for $bookmarkId")
                enrichedHtml
            }
            is MultipartSyncClient.Result.Error -> {
                Timber.w("HTML-only refresh failed for $bookmarkId: ${result.message}")
                null
            }
            is MultipartSyncClient.Result.NetworkError -> {
                Timber.w("HTML-only refresh network error for $bookmarkId: ${result.message}")
                null
            }
        }
    }

    /**
     * Rewrite absolute Readeck image URLs to relative paths for offline asset loading.
     *
     * Legacy article HTML references images as absolute Readeck URLs:
     *   `https://server/bm/XX/{bookmarkId}/_resources/{filename}`
     *
     * The offline content directory stores them flat at:
     *   `offline_content/{bookmarkId}/{filename}`
     *
     * Rewriting to `./{filename}` allows the WebView's offline base URL
     * (`https://offline.mydeck.local/{bookmarkId}/`) to resolve them through the
     * OfflineContentPathHandler.
     *
     * Note: the legacy endpoint uses `_resources/{filename}` in its absolute URLs but
     * the multipart sync endpoint sends flat filenames in the `path` header. This
     * function bridges that inconsistency when refreshing HTML via the legacy endpoint.
     */
    private fun rewriteToRelativeResourceUrls(bookmarkId: String, html: String): String {
        val pattern = Regex(
            """(src|poster)=(["'])https?://[^"']*/${Regex.escape(bookmarkId)}/_resources/([^"']+)\2"""
        )
        
        val rewrittenHtml = pattern.replace(html) { match ->
            val attr = match.groupValues[1]
            val quote = match.groupValues[2]
            val filename = match.groupValues[3]
            // Images are stored directly in content dir, not in _resources subdirectory
            val newUrl = """$attr=${quote}./$filename$quote"""
            newUrl
        }
        
        return rewrittenHtml
    }

    /**
     * Batch download content packages for up to 10 IDs per request, committing those
     * that succeed and returning a per-ID result for worker fallback decisions.
     *
     * All bookmarks receive full content packages (text + images).
     */
    suspend fun executeBatch(bookmarkIds: List<String>): Map<String, Result> {
        if (bookmarkIds.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Result>()

        fun markAll(ids: List<String>, reason: String): Map<String, Result> {
            val map = ids.associateWith { Result.TransientFailure(reason) }
            results.putAll(map)
            return map
        }

        // Pre-load bookmarks for use in processChunk (annotation enrichment, package kind, etc.).
        val cachedBookmarks: Map<String, Bookmark?> = bookmarkIds.associateWith { id ->
            try { bookmarkRepository.getBookmarkById(id) } catch (_: Exception) { null }
        }

        // Processes a fetch result for a set of IDs, committing packages and recording outcomes.
        suspend fun processChunk(chunkIds: List<String>, fetchResult: MultipartSyncClient.Result) {
            when (fetchResult) {
                is MultipartSyncClient.Result.Success -> {
                    val pkgsById = fetchResult.packages.associateBy { it.bookmarkId }
                    for (id in chunkIds) {
                        val pkg = pkgsById[id]
                        if (pkg == null) {
                            results[id] = Result.TransientFailure("No package returned for id")
                            continue
                        }

                        val bookmark = cachedBookmarks[id]
                            ?: try { bookmarkRepository.getBookmarkById(id) } catch (_: Exception) { null }
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
                        if (committed) {
                            pkg.json?.omitDescription?.let { omitVal ->
                                bookmarkDao.updateOmitDescription(id, omitVal)
                            }
                        }
                        results[id] = if (committed) Result.Success else Result.TransientFailure("Commit failed")
                    }
                }
                is MultipartSyncClient.Result.Error -> markAll(chunkIds, fetchResult.message)
                is MultipartSyncClient.Result.NetworkError -> markAll(chunkIds, fetchResult.message)
            }
        }

        val chunks = bookmarkIds.chunked(10)
        for (chunk in chunks) {
            try {
                processChunk(chunk, multipartSyncClient.fetchContentPackages(chunk))
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
