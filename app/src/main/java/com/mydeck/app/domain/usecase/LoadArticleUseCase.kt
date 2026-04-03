package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import com.mydeck.app.io.rest.model.toAnnotationCachePayload
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class LoadArticleUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val bookmarkDao: BookmarkDao,
    private val connectivityMonitor: ConnectivityMonitor,
    private val settingsDataStore: SettingsDataStore,
    private val json: Json
) {
    sealed class Result {
        data object Success : Result()
        data object AlreadyDownloaded : Result()
        data class TransientFailure(val reason: String) : Result()
        data class PermanentFailure(val reason: String) : Result()
    }

    suspend fun execute(bookmarkId: String, markDirtyAfterSuccess: Boolean = false): Result {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

        // Guard: never re-fetch downloaded content
        if (bookmark.contentState == ContentState.DOWNLOADED) {
            return Result.AlreadyDownloaded
        }

        // Guard: don't attempt for permanent no-content
        if (bookmark.contentState == ContentState.PERMANENT_NO_CONTENT) {
            return Result.PermanentFailure(bookmark.contentFailureReason ?: "No content available")
        }

        if (!bookmark.hasArticle) {
            // Server says no article content — mark permanent
            bookmarkDao.updateContentState(
                bookmarkId, BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value,
                "No article content available (type=${bookmark.type})"
            )
            Timber.i("Bookmark has no article [type=${bookmark.type}]")
            return Result.PermanentFailure("No article content available")
        }

        // Fail fast when offline to avoid OkHttp's ~10s connect timeout
        if (!connectivityMonitor.isNetworkAvailable()) {
            return Result.TransientFailure("Offline")
        }

        return try {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!
                storeDownloadedArticle(
                    bookmark = bookmark,
                    articleContent = content,
                    markDirtyAfterSuccess = markDirtyAfterSuccess
                )
                Result.Success
            } else {
                val reason = "HTTP ${response.code()}"
                bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.DIRTY.value, reason)
                Timber.w("Content fetch failed for $bookmarkId: $reason")
                Result.TransientFailure(reason)
            }
        } catch (e: IOException) {
            val reason = "Network error: ${e.message}"
            bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.DIRTY.value, reason)
            Timber.w(e, "Content fetch network error for $bookmarkId")
            Result.TransientFailure(reason)
        } catch (e: Exception) {
            val reason = "Unexpected error: ${e.message}"
            bookmarkDao.updateContentState(bookmarkId, BookmarkEntity.ContentState.DIRTY.value, reason)
            Timber.e(e, "Content fetch unexpected error for $bookmarkId")
            Result.TransientFailure(reason)
        }
    }

    /**
     * Refresh cached article HTML after an annotation change.
     *
     * This is the text-cache counterpart to content-package HTML refresh: it always uses
     * the legacy article endpoint and preserves the bookmark as a text-cached article
     * rather than promoting it into a full offline package.
     *
     * @return The refreshed HTML body, or null if the refresh failed
     */
    suspend fun refreshHtmlForAnnotations(bookmarkId: String): String? {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

        if (bookmark.contentState == ContentState.PERMANENT_NO_CONTENT || !bookmark.hasArticle) {
            return null
        }
        if (!connectivityMonitor.isNetworkAvailable()) {
            return null
        }

        return try {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!
                storeDownloadedArticle(bookmark = bookmark, articleContent = content)
                Timber.d("Refreshed cached article HTML after annotation change for $bookmarkId")
                content
            } else {
                Timber.w("Cached article annotation refresh failed for $bookmarkId: HTTP ${response.code()}")
                null
            }
        } catch (e: IOException) {
            Timber.w(e, "Cached article annotation refresh network error for $bookmarkId")
            null
        } catch (e: Exception) {
            Timber.w(e, "Cached article annotation refresh failed for $bookmarkId")
            null
        }
    }

    suspend fun refreshCachedArticleIfAnnotationsChanged(bookmarkId: String) {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
        val cachedArticleContent = bookmarkDao.getArticleContent(bookmarkId)

        if (bookmark.contentState != ContentState.DOWNLOADED ||
            cachedArticleContent.isNullOrBlank() ||
            !bookmark.hasArticle
        ) {
            return
        }

        if (!connectivityMonitor.isNetworkAvailable()) {
            return
        }

        try {
            when (val annotationSnapshot = fetchAnnotationSnapshot(bookmarkId)) {
                is AnnotationSnapshotResult.Failure -> {
                    Timber.w("Skipping annotation sync for $bookmarkId: ${annotationSnapshot.reason}")
                }

                is AnnotationSnapshotResult.Success -> {
                    val cachedSnapshot = settingsDataStore.getCachedAnnotationSnapshot(bookmarkId)
                    val articleHtmlAlreadyContainsAnnotations =
                        cachedArticleContent.contains("rd-annotation", ignoreCase = true)
                    val shouldRefreshArticle = when {
                        cachedSnapshot == null ->
                            annotationSnapshot.annotations.isNotEmpty() || articleHtmlAlreadyContainsAnnotations
                        cachedSnapshot != annotationSnapshot.snapshot -> true
                        else -> false
                    }

                    if (shouldRefreshArticle) {
                        val articleResponse = readeckApi.getArticle(bookmarkId)
                        if (articleResponse.isSuccessful && articleResponse.body() != null) {
                            storeDownloadedArticle(
                                bookmark = bookmark,
                                articleContent = articleResponse.body()!!,
                                annotationSnapshot = annotationSnapshot.snapshot
                            )
                            Timber.d("Refreshed article HTML after annotation change for $bookmarkId")
                        } else {
                            Timber.w(
                                "Failed to refresh article HTML after annotation change for $bookmarkId: HTTP ${articleResponse.code()}"
                            )
                        }
                    } else if (cachedSnapshot == null) {
                        settingsDataStore.saveCachedAnnotationSnapshot(bookmarkId, annotationSnapshot.snapshot)
                    }
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Network error while checking annotation sync for $bookmarkId")
        } catch (e: Exception) {
            Timber.w(e, "Unexpected error while checking annotation sync for $bookmarkId")
        }
    }

    private suspend fun storeDownloadedArticle(
        bookmark: Bookmark,
        articleContent: String,
        annotationSnapshot: String? = null,
        markDirtyAfterSuccess: Boolean = false
    ) {
        val bookmarkToSave = bookmark.copy(
            articleContent = articleContent,
            contentState = if (markDirtyAfterSuccess) ContentState.DIRTY else ContentState.DOWNLOADED,
            contentFailureReason = null,
            omitDescription = bookmark.omitDescription
        )
        bookmarkRepository.insertBookmarks(listOf(bookmarkToSave))

        if (markDirtyAfterSuccess) {
            return
        }

        if (annotationSnapshot != null) {
            settingsDataStore.saveCachedAnnotationSnapshot(bookmark.id, annotationSnapshot)
            return
        }

        try {
            when (val fetchedSnapshot = fetchAnnotationSnapshot(bookmark.id)) {
                is AnnotationSnapshotResult.Success -> {
                    settingsDataStore.saveCachedAnnotationSnapshot(bookmark.id, fetchedSnapshot.snapshot)
                }

                is AnnotationSnapshotResult.Failure -> {
                    Timber.w("Failed to cache annotation snapshot for ${bookmark.id}: ${fetchedSnapshot.reason}")
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Network error while caching annotation snapshot for ${bookmark.id}")
        } catch (e: Exception) {
            Timber.w(e, "Unexpected error while caching annotation snapshot for ${bookmark.id}")
        }
    }

    private suspend fun fetchAnnotationSnapshot(bookmarkId: String): AnnotationSnapshotResult {
        val response = readeckApi.getAnnotations(bookmarkId)
        if (!response.isSuccessful) {
            return AnnotationSnapshotResult.Failure("HTTP ${response.code()}")
        }

        val annotations = response.body().orEmpty()
        return AnnotationSnapshotResult.Success(
            annotations = annotations,
            snapshot = annotations.toAnnotationCachePayload(json)
        )
    }

    private sealed class AnnotationSnapshotResult {
        data class Success(
            val annotations: List<AnnotationDto>,
            val snapshot: String
        ) : AnnotationSnapshotResult()

        data class Failure(val reason: String) : AnnotationSnapshotResult()
    }
}
