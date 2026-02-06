package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark.ContentState
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.rest.ReadeckApi
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class LoadArticleUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val bookmarkDao: BookmarkDao
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

        if (!bookmark.hasArticle) {
            // Server says no article content — mark permanent
            bookmarkDao.updateContentState(
                bookmarkId, BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value,
                "No article content available (type=${bookmark.type})"
            )
            Timber.i("Bookmark has no article [type=${bookmark.type}]")
            return Result.PermanentFailure("No article content available")
        }

        return try {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!
                val bookmarkToSave = bookmark.copy(
                    articleContent = content,
                    contentState = ContentState.DOWNLOADED,
                    contentFailureReason = null
                )
                bookmarkRepository.insertBookmarks(listOf(bookmarkToSave))
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
}
