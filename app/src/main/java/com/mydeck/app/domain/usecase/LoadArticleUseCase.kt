package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.io.rest.ReadeckApi
import timber.log.Timber
import javax.inject.Inject

class LoadArticleUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
) {
    suspend fun execute(bookmarkId: String) {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
        if (bookmark.contentStatus == Bookmark.ContentStatus.DOWNLOADED) {
            Timber.d("Content already downloaded for bookmark $bookmarkId")
            return
        }

        if (!bookmark.hasArticle || bookmark.type != Bookmark.Type.Article) {
            Timber.i("Bookmark has no article [type=${bookmark.type}]")
            bookmarkRepository.updateContentState(
                bookmarkId = bookmarkId,
                status = Bookmark.ContentStatus.PERMANENT_NO_CONTENT,
                failureReason = "media"
            )
            return
        }

        if (bookmark.contentStatus == Bookmark.ContentStatus.PERMANENT_NO_CONTENT) {
            Timber.d("Bookmark marked as permanent no content [id=$bookmarkId]")
            return
        }

        try {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!
                val bookmarkToSave = bookmark.copy(
                    articleContent = content,
                    contentStatus = Bookmark.ContentStatus.DOWNLOADED,
                    contentFailureReason = null
                )
                bookmarkRepository.insertBookmarks(listOf(bookmarkToSave))
                return
            }

            val code = response.code()
            if (code in 400..499) {
                bookmarkRepository.updateContentState(
                    bookmarkId = bookmarkId,
                    status = Bookmark.ContentStatus.PERMANENT_NO_CONTENT,
                    failureReason = "http_$code"
                )
            } else {
                bookmarkRepository.updateContentState(
                    bookmarkId = bookmarkId,
                    status = Bookmark.ContentStatus.DIRTY,
                    failureReason = "http_$code"
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch article content [id=$bookmarkId]")
            bookmarkRepository.updateContentState(
                bookmarkId = bookmarkId,
                status = Bookmark.ContentStatus.DIRTY,
                failureReason = "exception"
            )
        }
    }
}
