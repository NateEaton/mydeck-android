package com.mydeck.app.domain

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.coroutine.IoDispatcher
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.mapper.toEntity
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.PendingActionDao
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.PendingActionEntity
import com.mydeck.app.io.db.model.ProgressPayload
import com.mydeck.app.io.db.model.LabelsPayload
import com.mydeck.app.io.db.model.TitlePayload
import com.mydeck.app.io.db.model.RemoteBookmarkIdEntity
import com.mydeck.app.io.db.model.TogglePayload
import com.mydeck.app.io.db.model.BookmarkListItemEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.BookmarkDto
import com.mydeck.app.io.rest.model.CreateBookmarkDto
import com.mydeck.app.io.rest.model.EditBookmarkDto
import com.mydeck.app.io.rest.model.EditBookmarkErrorDto
import com.mydeck.app.io.rest.model.StatusMessageDto
import com.mydeck.app.io.rest.model.SyncContentRequestDto
import com.mydeck.app.io.rest.model.EditBookmarkResponseDto
import com.mydeck.app.worker.LoadArticleWorker
import com.mydeck.app.worker.ActionSyncWorker
import com.mydeck.app.io.db.MyDeckDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val database: MyDeckDatabase,
    private val bookmarkDao: BookmarkDao,
    private val pendingActionDao: PendingActionDao,
    private val readeckApi: ReadeckApi,
    private val json: Json,
    private val workManager: WorkManager,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    @IoDispatcher
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BookmarkRepository {
    override fun observeBookmarks(
        type: Bookmark.Type?,
        unread: Boolean?,
        archived: Boolean?,
        favorite: Boolean?,
        state: Bookmark.State?
    ): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByFilters(
            type = type?.let {
                when (it) {
                    Bookmark.Type.Article -> BookmarkEntity.Type.ARTICLE
                    Bookmark.Type.Picture -> BookmarkEntity.Type.PHOTO
                    Bookmark.Type.Video -> BookmarkEntity.Type.VIDEO
                }
            },
            isUnread = unread,
            isArchived = archived,
            isFavorite = favorite,
            state = state?.let {
                when (it) {
                    Bookmark.State.LOADED -> BookmarkEntity.State.LOADED
                    Bookmark.State.ERROR -> BookmarkEntity.State.ERROR
                    Bookmark.State.LOADING -> BookmarkEntity.State.LOADING
                }
            }
        ).map { bookmarks -> bookmarks.map { it.toDomain() } }
    }

    override fun observeBookmarkListItems(
        type: Bookmark.Type?,
        unread: Boolean?,
        archived: Boolean?,
        favorite: Boolean?,
        label: String?,
        state: Bookmark.State?,
        orderBy: String
    ): Flow<List<BookmarkListItem>> {
        return bookmarkDao.getBookmarkListItemsByFilters(
            type = type?.let {
                when (it) {
                    Bookmark.Type.Article -> BookmarkEntity.Type.ARTICLE
                    Bookmark.Type.Picture -> BookmarkEntity.Type.PHOTO
                    Bookmark.Type.Video -> BookmarkEntity.Type.VIDEO
                }
            },
            isUnread = unread,
            isArchived = archived,
            isFavorite = favorite,
            label = label,
            state = state?.let {
                when (it) {
                    Bookmark.State.LOADED -> BookmarkEntity.State.LOADED
                    Bookmark.State.ERROR -> BookmarkEntity.State.ERROR
                    Bookmark.State.LOADING -> BookmarkEntity.State.LOADING
                }
            },
            orderBy = orderBy
        ).map { listItems ->
            listItems.map { listItem ->
                BookmarkListItem(
                    id = listItem.id,
                    url = listItem.url,
                    title = listItem.title,
                    siteName = listItem.siteName,
                    isMarked = listItem.isMarked,
                    isArchived = listItem.isArchived,
                    isRead = listItem.readProgress == 100,
                    readProgress = listItem.readProgress,
                    thumbnailSrc = listItem.thumbnailSrc,
                    iconSrc = listItem.iconSrc,
                    imageSrc = listItem.imageSrc,
                    labels = listItem.labels,
                    type = when (listItem.type) {
                        BookmarkEntity.Type.ARTICLE -> Bookmark.Type.Article
                        BookmarkEntity.Type.PHOTO -> Bookmark.Type.Picture
                        BookmarkEntity.Type.VIDEO -> Bookmark.Type.Video
                    },
                    readingTime = listItem.readingTime,
                    created = listItem.created.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                    wordCount = listItem.wordCount,
                    published = listItem.published?.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                )
            }
        }
    }

    override fun searchBookmarkListItems(
        searchQuery: String,
        type: Bookmark.Type?,
        unread: Boolean?,
        archived: Boolean?,
        favorite: Boolean?,
        label: String?,
        state: Bookmark.State?,
        orderBy: String
    ): Flow<List<BookmarkListItem>> {
        return bookmarkDao.searchBookmarkListItems(
            searchQuery = searchQuery,
            type = type?.let {
                when (it) {
                    Bookmark.Type.Article -> BookmarkEntity.Type.ARTICLE
                    Bookmark.Type.Picture -> BookmarkEntity.Type.PHOTO
                    Bookmark.Type.Video -> BookmarkEntity.Type.VIDEO
                }
            },
            isUnread = unread,
            isArchived = archived,
            isFavorite = favorite,
            label = label,
            state = state?.let {
                when (it) {
                    Bookmark.State.LOADED -> BookmarkEntity.State.LOADED
                    Bookmark.State.ERROR -> BookmarkEntity.State.ERROR
                    Bookmark.State.LOADING -> BookmarkEntity.State.LOADING
                }
            },
            orderBy = orderBy
        ).map { listItems ->
            listItems.map { listItem ->
                BookmarkListItem(
                    id = listItem.id,
                    url = listItem.url,
                    title = listItem.title,
                    siteName = listItem.siteName,
                    isMarked = listItem.isMarked,
                    isArchived = listItem.isArchived,
                    isRead = listItem.readProgress == 100,
                    readProgress = listItem.readProgress,
                    thumbnailSrc = listItem.thumbnailSrc,
                    iconSrc = listItem.iconSrc,
                    imageSrc = listItem.imageSrc,
                    labels = listItem.labels,
                    type = when (listItem.type) {
                        BookmarkEntity.Type.ARTICLE -> Bookmark.Type.Article
                        BookmarkEntity.Type.PHOTO -> Bookmark.Type.Picture
                        BookmarkEntity.Type.VIDEO -> Bookmark.Type.Video
                    },
                    readingTime = listItem.readingTime,
                    created = listItem.created.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                    wordCount = listItem.wordCount,
                    published = listItem.published?.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                )
            }
        }
    }

    override suspend fun insertBookmarks(bookmarks: List<Bookmark>) {
        val mergedBookmarks = bookmarks.map { remote ->
            val pendingActions = pendingActionDao.getActionsForBookmark(remote.id)
            if (pendingActions.isEmpty()) {
                remote.toEntity()
            } else {
                // Merge: start with remote, but preserve local status for fields with pending actions
                val local = bookmarkDao.getBookmarkById(remote.id) // This might be null for new bookmarks
                val remoteEntity = remote.toEntity()
                
                var mergedBookmark = remoteEntity.bookmark
                for (action in pendingActions) {
                    mergedBookmark = when (action.actionType) {
                        ActionType.TOGGLE_FAVORITE -> local?.let { mergedBookmark.copy(isMarked = it.isMarked) } ?: mergedBookmark
                        ActionType.TOGGLE_ARCHIVE -> local?.let { mergedBookmark.copy(isArchived = it.isArchived) } ?: mergedBookmark
                        ActionType.TOGGLE_READ -> local?.let { mergedBookmark.copy(readProgress = it.readProgress) } ?: mergedBookmark
                        ActionType.UPDATE_PROGRESS -> local?.let { mergedBookmark.copy(readProgress = it.readProgress) } ?: mergedBookmark
                        ActionType.UPDATE_LABELS -> local?.let { mergedBookmark.copy(labels = it.labels) } ?: mergedBookmark
                        ActionType.UPDATE_TITLE -> local?.let { mergedBookmark.copy(title = it.title) } ?: mergedBookmark
                        ActionType.DELETE -> mergedBookmark.copy(isLocalDeleted = true)
                    }
                }
                remoteEntity.copy(bookmark = mergedBookmark)
            }
        }
        bookmarkDao.insertBookmarksWithArticleContent(mergedBookmarks)
    }

    override suspend fun getBookmarkById(id: String): Bookmark {
        return bookmarkDao.getBookmarkById(id).toDomain()
    }

    override suspend fun refreshBookmarkFromApi(id: String) {
        withContext(dispatcher) {
            try {
                val response = readeckApi.getBookmarkById(id)
                if (response.isSuccessful && response.body() != null) {
                    val bookmark = response.body()!!.toDomain()
                    insertBookmarks(listOf(bookmark))
                    Timber.d("Refreshed bookmark from API: $id")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to refresh bookmark from API: $id")
            }
        }
    }

    override fun observeBookmark(id: String): Flow<Bookmark?> {
        return bookmarkDao.observeBookmarkWithArticleContent(id).map {
            it?.let {
                it.bookmark.toDomain().copy(articleContent = it.articleContent?.content)
            }
        }
    }

    override suspend fun deleteAllBookmarks() {
        bookmarkDao.deleteAllBookmarks()
    }

    override suspend fun createBookmark(
        title: String,
        url: String,
        labels: List<String>
    ): String {
        val createBookmarkDto = CreateBookmarkDto(labels = labels, title = title, url = url)
        val response = readeckApi.createBookmark(createBookmarkDto)
        if (!response.isSuccessful) {
            throw Exception("Failed to create bookmark")
        }

        val bookmarkId = response.headers()[ReadeckApi.Header.BOOKMARK_ID]!!

        // Fetch and insert bookmark metadata
        try {
            val bookmarkResponse = readeckApi.getBookmarkById(bookmarkId)
            if (bookmarkResponse.isSuccessful && bookmarkResponse.body() != null) {
                val bookmark = bookmarkResponse.body()!!.toDomain()
                insertBookmarks(listOf(bookmark))
                Timber.d("Bookmark created and inserted locally: $bookmarkId")

                // If not yet loaded, poll in the background
                if (bookmark.state == Bookmark.State.LOADING) {
                    pollForBookmarkReady(bookmarkId)
                } else if (bookmark.hasArticle) {
                    // Content available, enqueue download
                    enqueueArticleDownload(bookmarkId)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch and insert created bookmark")
        }

        return bookmarkId
    }

    private fun pollForBookmarkReady(bookmarkId: String) {
        // Launch in application scope so it survives navigation
        applicationScope.launch {
            var attempts = 0
            val maxAttempts = 30
            val delayMs = 2000L

            while (attempts < maxAttempts) {
                delay(delayMs)
                attempts++

                try {
                    val response = readeckApi.getBookmarkById(bookmarkId)
                    if (response.isSuccessful && response.body() != null) {
                        val bookmark = response.body()!!.toDomain()
                        insertBookmarks(listOf(bookmark))  // Update local metadata

                        when (bookmark.state) {
                            Bookmark.State.LOADED -> {
                                Timber.d("Bookmark loaded after $attempts polls: $bookmarkId")
                                if (bookmark.hasArticle) {
                                    enqueueArticleDownload(bookmarkId)
                                }
                                return@launch  // Done
                            }
                            Bookmark.State.ERROR -> {
                                Timber.w("Bookmark extraction failed: $bookmarkId")
                                return@launch  // Done (with error)
                            }
                            else -> { /* still loading, continue polling */ }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Poll attempt $attempts failed for $bookmarkId")
                }
            }

            Timber.w("Polling timed out for bookmark $bookmarkId after $maxAttempts attempts")
        }
    }

    private fun enqueueArticleDownload(bookmarkId: String) {
        val request = OneTimeWorkRequestBuilder<LoadArticleWorker>()
            .setInputData(
                Data.Builder()
                    .putString(LoadArticleWorker.PARAM_BOOKMARK_ID, bookmarkId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueue(request)
        Timber.d("Article download enqueued for bookmark: $bookmarkId")
    }

    override suspend fun updateBookmark(
        bookmarkId: String,
        isFavorite: Boolean?,
        isArchived: Boolean?,
        isRead: Boolean?
    ): BookmarkRepository.UpdateResult {
        withContext(dispatcher) {
            database.performTransaction {
                isFavorite?.let {
                    bookmarkDao.updateIsMarked(bookmarkId, it)
                    upsertPendingAction(bookmarkId, ActionType.TOGGLE_FAVORITE, TogglePayload(it))
                }
                isArchived?.let {
                    bookmarkDao.updateIsArchived(bookmarkId, it)
                    upsertPendingAction(bookmarkId, ActionType.TOGGLE_ARCHIVE, TogglePayload(it))
                }
                isRead?.let {
                    bookmarkDao.updateReadProgress(bookmarkId, if (it) 100 else 0)
                    upsertPendingAction(bookmarkId, ActionType.TOGGLE_READ, TogglePayload(it))
                }
            }
            ActionSyncWorker.enqueue(workManager)
        }
        return BookmarkRepository.UpdateResult.Success
    }

    override suspend fun deleteBookmark(id: String): BookmarkRepository.UpdateResult {
        withContext(dispatcher) {
            database.performTransaction {
                // 1. Optimistic Soft Delete
                bookmarkDao.softDeleteBookmark(id)

                // 2. Delete Supremacy: remove other actions for this bookmark
                pendingActionDao.deleteAllForBookmark(id)

                // 3. Queue Delete Action
                pendingActionDao.insert(
                    PendingActionEntity(
                        bookmarkId = id,
                        actionType = ActionType.DELETE,
                        payload = null,
                        createdAt = Clock.System.now()
                    )
                )
            }
            ActionSyncWorker.enqueue(workManager)
        }
        return BookmarkRepository.UpdateResult.Success
    }

    private suspend fun upsertPendingAction(
        bookmarkId: String,
        type: ActionType,
        payload: Any
    ) {
        val payloadString = when(payload) {
            is TogglePayload -> json.encodeToString(TogglePayload.serializer(), payload)
            is ProgressPayload -> json.encodeToString(ProgressPayload.serializer(), payload)
            is LabelsPayload -> json.encodeToString(LabelsPayload.serializer(), payload)
            is TitlePayload -> json.encodeToString(TitlePayload.serializer(), payload)
            else -> null
        }

        val existingAction = pendingActionDao.find(bookmarkId, type)
        if (existingAction != null) {
            pendingActionDao.updateAction(existingAction.id, payloadString, Clock.System.now())
        } else {
            pendingActionDao.insert(
                PendingActionEntity(
                    bookmarkId = bookmarkId,
                    actionType = type,
                    payload = payloadString,
                    createdAt = Clock.System.now()
                )
            )
        }
    }

    override suspend fun updateTitle(
        bookmarkId: String,
        title: String
    ): BookmarkRepository.UpdateResult {
        withContext(dispatcher) {
            database.performTransaction {
                bookmarkDao.updateTitle(bookmarkId, title)
                upsertPendingAction(bookmarkId, ActionType.UPDATE_TITLE, TitlePayload(title))
            }
            ActionSyncWorker.enqueue(workManager)
        }
        return BookmarkRepository.UpdateResult.Success
    }

    override suspend fun updateLabels(
        bookmarkId: String,
        labels: List<String>
    ): BookmarkRepository.UpdateResult {
        withContext(dispatcher) {
            database.performTransaction {
                // Update local database with new labels
                val labelsString = labels.joinToString(",")
                bookmarkDao.updateLabels(bookmarkId, labelsString)
                
                // Queue the sync action
                upsertPendingAction(bookmarkId, ActionType.UPDATE_LABELS, LabelsPayload(labels))
            }
            ActionSyncWorker.enqueue(workManager)
        }
        return BookmarkRepository.UpdateResult.Success
    }

    override suspend fun updateReadProgress(
        bookmarkId: String,
        progress: Int
    ): BookmarkRepository.UpdateResult {
        withContext(dispatcher) {
            database.performTransaction {
                bookmarkDao.updateReadProgress(bookmarkId, progress.coerceIn(0, 100))
                upsertPendingAction(
                    bookmarkId,
                    ActionType.UPDATE_PROGRESS,
                    ProgressPayload(progress.coerceIn(0, 100), Clock.System.now())
                )
            }
            ActionSyncWorker.enqueue(workManager)
        }
        return BookmarkRepository.UpdateResult.Success
    }

    private fun handleStatusMessage(code: Int, errorBody: String?): StatusMessageDto {
        return if (!errorBody.isNullOrBlank()) {
            try {
                json.decodeFromString<StatusMessageDto>(errorBody)
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to parse error: ${e.message}")
                StatusMessageDto(
                    status = code,
                    message = "Failed to parse error: ${e.message}"
                )
            }
        } else {
            Timber.e("Empty error body")
            StatusMessageDto(
                status = code,
                message = "Empty error body"
            )
        }
    }

    override suspend fun performFullSync(): BookmarkRepository.SyncResult = withContext(dispatcher) {
        try {
            bookmarkDao.clearRemoteBookmarkIds() // Clear any previous sync data

            val pageSize = 50
            var offset = 0
            var hasMore = true

            while (hasMore) {
                val response = readeckApi.getBookmarks(limit = pageSize, offset = offset, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created))

                if (response.isSuccessful) {
                    val remoteBookmarks = response.body() ?: emptyList()
                    Timber.d("Fetched ${remoteBookmarks.size} remote bookmarks (offset=$offset)")

                    val totalCountHeader = response.headers()[ReadeckApi.Header.TOTAL_COUNT]
                    val totalPagesHeader = response.headers()[ReadeckApi.Header.TOTAL_PAGES]
                    val currentPageHeader = response.headers()[ReadeckApi.Header.CURRENT_PAGE]

                    if (totalCountHeader == null || totalPagesHeader == null || currentPageHeader == null) {
                        return@withContext BookmarkRepository.SyncResult.Error("Missing headers in API response")
                    }

                    val totalCount = totalCountHeader.toInt()
                    val totalPages = totalPagesHeader.toInt()
                    val currentPage = currentPageHeader.toInt()

                    Timber.d("currentPage=$currentPage")
                    Timber.d("totalPages=$totalPages")
                    Timber.d("totalCount=$totalCount")

                    // Save remote bookmark IDs to the temporary table
                    val remoteBookmarkIdEntities = remoteBookmarks.map { RemoteBookmarkIdEntity(it.id) }
                    bookmarkDao.insertRemoteBookmarkIds(remoteBookmarkIdEntities)

                    if (currentPage < totalPages) {
                        offset += pageSize
                    } else {
                        hasMore = false
                    }
                } else {
                    Timber.e("Full sync failed at offset=$offset with code: ${response.code()}")
                    return@withContext BookmarkRepository.SyncResult.Error(
                        errorMessage = "Full sync failed",
                        code = response.code(),
                        ex = null
                    )
                }
            }

            // After fetching all remote IDs, find local bookmarks to delete
            // Crucial: skip hard-deletion of soft-deleted bookmarks that are pending sync
            val deleted = bookmarkDao.removeDeletedBookmars()
            Timber.i("Deleted bookmarks: $deleted")

            bookmarkDao.clearRemoteBookmarkIds() // Clean up the temporary table

            BookmarkRepository.SyncResult.Success(countDeleted = deleted)
        } catch (e: Exception) {
            Timber.e(e, "Full sync failed")
            BookmarkRepository.SyncResult.NetworkError(errorMessage = "Network error during full sync", ex = e)
        }
    }

    override suspend fun performDeltaSync(since: kotlinx.datetime.Instant?): BookmarkRepository.SyncResult = withContext(dispatcher) {
        // DEPRECATED: The /api/bookmarks/sync endpoint has a server-side bug with SQLite.
        // This method is kept for future use if/when the server bug is fixed.
        // For now, always return an error to trigger fallback to full sync.
        Timber.w("Delta sync is disabled due to server-side SQLite compatibility issue")
        return@withContext BookmarkRepository.SyncResult.Error(
            errorMessage = "Delta sync disabled - server endpoint incompatible with SQLite",
            code = 500
        )
    }

    override suspend fun syncPendingActions(): BookmarkRepository.UpdateResult {
        return withContext(dispatcher) {
            val actions = pendingActionDao.getAllActionsSorted()
            Timber.d("Starting syncPendingActions. Pending actions: ${actions.size}")

            for (action in actions) {
                try {
                    processPendingAction(action)
                    pendingActionDao.delete(action)
                    Timber.d("Successfully processed action: ${action.actionType} for ${action.bookmarkId}")
                } catch (e: Exception) {
                    if (isTransientError(e)) {
                        Timber.w(e, "Transient error processing action ${action.actionType}. Retrying...")
                        return@withContext BookmarkRepository.UpdateResult.NetworkError("Transient error", e)
                    } else {
                        Timber.e(e, "Permanent error processing action ${action.actionType} for ${action.bookmarkId}")
                        // Drop the action to prevent queue clog
                        pendingActionDao.delete(action)
                    }
                }
            }
            BookmarkRepository.UpdateResult.Success
        }
    }

    private suspend fun processPendingAction(action: PendingActionEntity) {
        when (action.actionType) {
            ActionType.TOGGLE_FAVORITE -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(isMarked = payload.value))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.TOGGLE_ARCHIVE -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(isArchived = payload.value))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.TOGGLE_READ -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(readProgress = if (payload.value) 100 else 0))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.UPDATE_PROGRESS -> {
                val payload = json.decodeFromString<ProgressPayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(readProgress = payload.progress))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.UPDATE_LABELS -> {
                val payload = json.decodeFromString<LabelsPayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(labels = payload.labels))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.UPDATE_TITLE -> {
                val payload = json.decodeFromString<TitlePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(title = payload.title))
                handleSyncApiResponse(action.bookmarkId, response)
            }
            ActionType.DELETE -> {
                val response = readeckApi.deleteBookmark(action.bookmarkId)
                handleSyncApiResponse(action.bookmarkId, response)
            }
        }
    }

    private suspend fun <T> handleSyncApiResponse(bookmarkId: String, response: retrofit2.Response<T>) {
        if (response.isSuccessful) return

        val code = response.code()
        if (code == 404) {
            Timber.w("Bookmark $bookmarkId not found on server (404). Hard deleting locally.")
            bookmarkDao.hardDeleteBookmark(bookmarkId)
            pendingActionDao.deleteAllForBookmark(bookmarkId)
            return
        }

        throw Exception("API Error: $code - ${response.errorBody()?.string()}")
    }

    private fun isTransientError(e: Exception): Boolean {
        return e is IOException || e.message?.contains("500") == true || e.message?.contains("429") == true || e.message?.contains("408") == true
    }

    override fun observeAllBookmarkCounts(): Flow<BookmarkCounts> {
        return bookmarkDao.observeAllBookmarkCounts().map { entity ->
            if (entity != null) {
                BookmarkCounts(
                    unread = entity.unread,
                    archived = entity.archived,
                    favorite = entity.favorite,
                    article = entity.article,
                    video = entity.video,
                    picture = entity.picture,
                    total = entity.total
                )
            } else {
                BookmarkCounts()
            }
        }
    }

    override fun observeAllLabelsWithCounts(): Flow<Map<String, Int>> =
        bookmarkDao.observeAllLabels().map { labelsStringList ->
            val labelCounts = mutableMapOf<String, Int>()

            // Parse each labels string and count occurrences
            for (labelsString in labelsStringList) {
                if (labelsString.isNotEmpty()) {
                    // Split by comma to get individual labels
                    val labels = labelsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    for (label in labels) {
                        labelCounts[label] = (labelCounts[label] ?: 0) + 1
                    }
                }
            }

            labelCounts.toMap()
        }

    override fun observePendingActionCount(): Flow<Int> {
        return pendingActionDao.getCountFlow()
    }

    override suspend fun renameLabel(oldLabel: String, newLabel: String): BookmarkRepository.UpdateResult =
        withContext(dispatcher) {
            try {
                val bookmarksWithContent = bookmarkDao.getAllBookmarksWithContent()
                    .filter { bookmark ->
                        bookmark.bookmark.labels.contains(oldLabel)
                    }

                database.performTransaction {
                    for (bookmarkWithContent in bookmarksWithContent) {
                        val bookmark = bookmarkWithContent.bookmark
                        val updatedLabels = bookmark.labels.map { label ->
                            if (label == oldLabel) newLabel else label
                        }
                        bookmarkDao.updateLabels(bookmark.id, updatedLabels.joinToString(","))
                        upsertPendingAction(bookmark.id, ActionType.UPDATE_LABELS, LabelsPayload(updatedLabels))
                    }
                }
                ActionSyncWorker.enqueue(workManager)
                BookmarkRepository.UpdateResult.Success
            } catch (e: Exception) {
                Timber.e(e, "Error renaming label")
                BookmarkRepository.UpdateResult.Error("Failed to rename label: ${e.message}")
            }
        }

    override suspend fun deleteLabel(label: String): BookmarkRepository.UpdateResult =
        withContext(dispatcher) {
            try {
                val bookmarksWithContent = bookmarkDao.getAllBookmarksWithContent()
                    .filter { bookmark ->
                        bookmark.bookmark.labels.contains(label)
                    }

                database.performTransaction {
                    for (bookmarkWithContent in bookmarksWithContent) {
                        val bookmark = bookmarkWithContent.bookmark
                        val updatedLabels = bookmark.labels.filter { it != label }
                        bookmarkDao.updateLabels(bookmark.id, updatedLabels.joinToString(","))
                        upsertPendingAction(bookmark.id, ActionType.UPDATE_LABELS, LabelsPayload(updatedLabels))
                    }
                }
                ActionSyncWorker.enqueue(workManager)
                BookmarkRepository.UpdateResult.Success
            } catch (e: Exception) {
                Timber.e(e, "Error deleting label")
                BookmarkRepository.UpdateResult.Error("Failed to delete label: ${e.message}")
            }
        }
}
