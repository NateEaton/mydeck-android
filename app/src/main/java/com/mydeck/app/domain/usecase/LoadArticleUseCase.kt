package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.io.rest.MyDeckApi
import timber.log.Timber
import javax.inject.Inject

class LoadArticleUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: MyDeckApi,
) {
    suspend fun execute(bookmarkId: String) {
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
        if (bookmark.hasArticle) {
            val response = readeckApi.getArticle(bookmarkId)
            if (response.isSuccessful) {
                val content = response.body()!!
                val bookmarkToSave = bookmark.copy(articleContent = content)
                bookmarkRepository.insertBookmarks(listOf(bookmarkToSave))
                return
            }
        } else {
            Timber.i("Bookmark has no article [type=${bookmark.type}]")
        }
    }
}