package com.mydeck.app.domain

import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.BookmarkMetadataUpdate
import com.mydeck.app.domain.model.ProgressFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class BookmarkBatchUpdate(
    val bookmarkId: String,
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
    val isRead: Boolean? = null
)

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
        minReadingTime: Int? = null,
        maxReadingTime: Int? = null,
        includeNullReadingTime: Boolean = false,
        minWordCount: Int? = null,
        maxWordCount: Int? = null,
        includeNullWordCount: Boolean = false,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItem>>

    suspend fun insertBookmarks(bookmarks: List<Bookmark>)
    suspend fun replaceServerErrorFlags(bookmarkIds: Set<String>)
    suspend fun getBookmarkById(id: String): Bookmark
    fun observeBookmark(id: String): Flow<Bookmark?>
    suspend fun deleteAllBookmarks()
    suspend fun deleteBookmark(id: String): UpdateResult
    suspend fun deleteBookmarks(ids: List<String>): UpdateResult
    suspend fun createBookmark(
        title: String,
        url: String,
        labels: List<String> = emptyList(),
        // When non-null, this is a create RETRY: before POSTing, reconcile against the server and
        // adopt an already-created bookmark for this URL if one exists (W3 lost-response idempotency).
        // The value is the original attempt time (epoch ms) used to tell "this add" from a prior add.
        attemptTimestampMs: Long? = null
    ): String
    suspend fun updateBookmark(bookmarkId: String, isFavorite: Boolean?, isArchived: Boolean?, isRead: Boolean?): UpdateResult
    suspend fun updateBookmarks(updates: List<BookmarkBatchUpdate>): UpdateResult
    suspend fun updateReadProgress(bookmarkId: String, progress: Int): UpdateResult
    suspend fun updateLabels(bookmarkId: String, labels: List<String>): UpdateResult
    val syncProgress: StateFlow<BookmarkSyncProgress>

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
        minReadingTime: Int? = null,
        maxReadingTime: Int? = null,
        includeNullReadingTime: Boolean = false,
        minWordCount: Int? = null,
        maxWordCount: Int? = null,
        includeNullWordCount: Boolean = false,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItem>>

    fun observeAllBookmarkCounts(): Flow<BookmarkCounts>
    fun observeAllLabelsWithCounts(): Flow<Map<String, Int>>
    fun observePendingActionCount(): Flow<Int>
    suspend fun updateTitle(bookmarkId: String, title: String): UpdateResult
    suspend fun updateMetadata(bookmarkId: String, metadata: BookmarkMetadataUpdate): UpdateResult
    suspend fun renameLabel(oldLabel: String, newLabel: String): UpdateResult
    suspend fun deleteLabel(label: String): UpdateResult

    /**
     * Adds [labels] to each bookmark in [ids] (additive union per bookmark).
     * Returns the prior label list of every bookmark that actually changed,
     * keyed by bookmark id, so the change can be undone.
     */
    suspend fun addLabelsToBookmarks(ids: List<String>, labels: List<String>): Map<String, List<String>>

    /** Restores the captured prior label lists (Undo for [addLabelsToBookmarks]). */
    suspend fun restoreBookmarkLabels(priorByBookmark: Map<String, List<String>>)
    suspend fun fetchRawBookmarkJson(bookmarkId: String): String?
    suspend fun refreshBookmarkMetadata(bookmarkId: String)
    suspend fun fetchExtractionLog(bookmarkId: String): ExtractionLogResult

    /** Debug-only: aggregated offline storage facts for a single bookmark. */
    suspend fun getOfflineContentDebugInfo(bookmarkId: String): OfflineContentDebugInfo

    sealed interface BookmarkSyncProgress {
        data object Idle : BookmarkSyncProgress
        data class Running(val page: Int, val totalPages: Int) : BookmarkSyncProgress
    }

    sealed class ExtractionLogResult {
        data class Success(val text: String) : ExtractionLogResult()
        data class HttpError(val code: Int) : ExtractionLogResult()
        data object NetworkError : ExtractionLogResult()
        data object Unavailable : ExtractionLogResult()
    }

    sealed class UpdateResult {
        data object Success: UpdateResult()
        data class Error(val errorMessage: String, val code: Int? = null, val ex: Exception? = null): UpdateResult()
        data class NetworkError(val errorMessage: String, val ex: Exception?): UpdateResult()
    }
    sealed class SyncResult {
        data class Success(
            val countDeleted: Int,
            val countUpdated: Int = 0,
            val updatedIds: List<String> = emptyList(),
            val maxServerTime: kotlinx.datetime.Instant? = null
        ): SyncResult()
        data class Error(val errorMessage: String, val code: Int? = null, val ex: Exception? = null): SyncResult()
        data class NetworkError(val errorMessage: String, val ex: Exception?): SyncResult()
    }
}

/** Immutable snapshot of offline storage facts used by the debug exporter. */
data class OfflineContentDebugInfo(
    val hasArticleContent: Boolean,
    val articleContentLength: Int,
    val hasPackage: Boolean,
    val hasResources: Boolean,
    val source: String?,        // "AUTOMATIC" | "MANUAL" | null
    val resourceCount: Int,
    val resourceTotalBytes: Long,
    val contentDir: java.io.File?
)
