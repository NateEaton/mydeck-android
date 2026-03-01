package com.mydeck.app.domain

import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.ProgressFilter
import kotlinx.coroutines.flow.Flow

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
    suspend fun refreshBookmarkFromApi(id: String)
    fun observeBookmark(id: String): Flow<Bookmark?>
    suspend fun deleteAllBookmarks()
    suspend fun deleteBookmark(id: String): UpdateResult
    suspend fun createBookmark(
        title: String,
        url: String,
        labels: List<String> = emptyList()
    ): String
    suspend fun updateBookmark(bookmarkId: String, isFavorite: Boolean?, isArchived: Boolean?, isRead: Boolean?): UpdateResult
    suspend fun updateReadProgress(bookmarkId: String, progress: Int): UpdateResult
    suspend fun updateLabels(bookmarkId: String, labels: List<String>): UpdateResult
    suspend fun performFullSync(): SyncResult
    suspend fun performDeltaSync(since: kotlinx.datetime.Instant?): SyncResult
    suspend fun syncPendingActions(): UpdateResult
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

    fun observeFilteredBookmarkListItems(
        searchQuery: String? = null,
        title: String? = null,
        author: String? = null,
        site: String? = null,
        types: Set<Bookmark.Type> = emptySet(),
        progressFilters: Set<ProgressFilter> = emptySet(),
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        label: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        isLoaded: Boolean? = null,
        withLabels: Boolean? = null,
        withErrors: Boolean? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItem>>

    fun observeAllBookmarkCounts(): Flow<BookmarkCounts>
    fun observeAllLabelsWithCounts(): Flow<Map<String, Int>>
    fun observePendingActionCount(): Flow<Int>
    suspend fun updateTitle(bookmarkId: String, title: String): UpdateResult
    suspend fun renameLabel(oldLabel: String, newLabel: String): UpdateResult
    suspend fun deleteLabel(label: String): UpdateResult
    suspend fun fetchRawBookmarkJson(bookmarkId: String): String?
    suspend fun fetchRawArticleHtml(bookmarkId: String): String?
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
