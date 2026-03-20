package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.rest.sync.MultipartSyncClient
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
    private val connectivityMonitor: ConnectivityMonitor
) {
    sealed class Result {
        data object Success : Result()
        data object AlreadyDownloaded : Result()
        data class TransientFailure(val reason: String) : Result()
        data class PermanentFailure(val reason: String) : Result()
    }

    suspend fun execute(bookmarkId: String): Result {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

        // Guard: never re-fetch downloaded content
        if (bookmark.contentState == ContentState.DOWNLOADED) {
            return Result.AlreadyDownloaded
        }

        // Guard: don't attempt for permanent no-content
        if (bookmark.contentState == ContentState.PERMANENT_NO_CONTENT) {
            return Result.PermanentFailure(bookmark.contentFailureReason ?: "No content available")
        }

        // For articles, check hasArticle
        if (bookmark.type is Bookmark.Type.Article && !bookmark.hasArticle) {
            bookmarkDao.updateContentState(
                bookmarkId, BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value,
                "No article content available (type=${bookmark.type})"
            )
            return Result.PermanentFailure("No article content available")
        }

        // Videos are not content-package eligible
        if (bookmark.type is Bookmark.Type.Video) {
            return Result.PermanentFailure("Video bookmarks are not eligible for offline content packages")
        }

        // Fail fast when offline
        if (!connectivityMonitor.isNetworkAvailable()) {
            return Result.TransientFailure("Offline")
        }

        return try {
            val result = multipartSyncClient.fetchContentPackages(listOf(bookmarkId))
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

                    val sourceUpdated = pkg.json?.updated?.toString() ?: bookmark.updated.toString()
                    val committed = contentPackageManager.commitPackage(
                        effectivePkg, packageKind, sourceUpdated
                    )

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
}
