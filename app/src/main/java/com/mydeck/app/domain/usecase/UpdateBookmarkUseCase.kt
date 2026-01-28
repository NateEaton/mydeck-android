package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import javax.inject.Inject

class UpdateBookmarkUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    suspend fun updateIsFavorite(bookmarkId: String, isFavorite: Boolean): Result {
        return handleResult(bookmarkRepository.updateBookmark(
            bookmarkId = bookmarkId,
            isFavorite = isFavorite,
            isArchived = null,
            isRead = null
        ))
    }

    suspend fun updateIsArchived(bookmarkId: String, isArchived: Boolean): Result {
        return handleResult(bookmarkRepository.updateBookmark(
            bookmarkId = bookmarkId,
            isFavorite = null,
            isArchived = isArchived,
            isRead = null
        ))
    }

    suspend fun updateIsRead(bookmarkId: String, isRead: Boolean): Result {
        return handleResult(bookmarkRepository.updateBookmark(
            bookmarkId = bookmarkId,
            isFavorite = null,
            isArchived = null,
            isRead = isRead
        ))
    }

    suspend fun deleteBookmark(bookmarkId: String): Result {
        return handleResult(bookmarkRepository.deleteBookmark(bookmarkId))
    }

    private fun handleResult(result: BookmarkRepository.UpdateResult): Result {
        return when(result) {
            is BookmarkRepository.UpdateResult.Success -> Result.Success
            is BookmarkRepository.UpdateResult.Error -> Result.GenericError(result.errorMessage)
            is BookmarkRepository.UpdateResult.NetworkError -> Result.NetworkError(result.errorMessage)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class GenericError(val message: String) : Result()
        data class NetworkError(val message: String) : Result()
    }
}
