package com.mydeck.app.domain

import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import kotlinx.coroutines.flow.Flow
import com.mydeck.app.domain.model.BookmarkListItem

interface BookmarkRepository {
    fun observeBookmarks(
        type: Bookmark.Type? = null,
        unread: Boolean? = null,
        archived: Boolean? = null,
        favorite: Boolean? = null,
        state: Bookmark.State? = null
    ): Flow<List<Bookmark>>

    fun observeBookmarkListItems(
        type: Bookmark.Type? = null,
        unread: Boolean? = null,
        archived: Boolean? = null,
        favorite: Boolean? = null,
        label: String? = null,
        state: Bookmark.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItem>>

    suspend fun insertBookmarks(bookmarks: List<Bookmark>)
    suspend fun getBookmarkById(id: String): Bookmark
    fun observeBookmark(id: String): Flow<Bookmark?>
    suspend fun deleteAllBookmarks()
    suspend fun deleteBookmark(id: String): UpdateResult
    suspend fun createBookmark(title: String, url: String, labels: List<String> = emptyList()): String
    suspend fun updateBookmark(bookmarkId: String, isFavorite: Boolean?, isArchived: Boolean?, isRead: Boolean?): UpdateResult
    suspend fun updateReadProgress(bookmarkId: String, progress: Int): UpdateResult
    suspend fun updateLabels(bookmarkId: String, labels: List<String>): UpdateResult
    suspend fun performFullSync(): SyncResult
    suspend fun performDeltaSync(since: kotlinx.datetime.Instant?): SyncResult
    fun searchBookmarkListItems(
        searchQuery: String,
        type: Bookmark.Type? = null,
        unread: Boolean? = null,
        archived: Boolean? = null,
        favorite: Boolean? = null,
        label: String? = null,
        state: Bookmark.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItem>>

    fun observeAllBookmarkCounts(): Flow<BookmarkCounts>
    fun observeAllLabelsWithCounts(): Flow<Map<String, Int>>
    suspend fun renameLabel(oldLabel: String, newLabel: String): UpdateResult
    suspend fun deleteLabel(label: String): UpdateResult
    sealed class UpdateResult {
        data object Success: UpdateResult()
        data class Error(val errorMessage: String, val code: Int? = null, val ex: Exception? = null): UpdateResult()
        data class NetworkError(val errorMessage: String, val ex: Exception?): UpdateResult()
    }
    sealed class SyncResult {
        data class Success(val countDeleted: Int): SyncResult()
        data class Error(val errorMessage: String, val code: Int? = null, val ex: Exception? = null): SyncResult()
        data class NetworkError(val errorMessage: String, val ex: Exception?): SyncResult()
    }
}
