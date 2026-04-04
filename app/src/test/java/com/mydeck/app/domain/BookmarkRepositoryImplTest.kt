package com.mydeck.app.domain

import androidx.room.withTransaction
import com.mydeck.app.domain.mapper.toEntity
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkMetadataUpdate
import com.mydeck.app.domain.sync.SyncScheduler
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.PendingActionDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.PendingActionEntity
import com.mydeck.app.worker.ActionSyncWorker
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.BookmarkDto
import com.mydeck.app.io.rest.model.CreateBookmarkDto
import com.mydeck.app.io.rest.model.EditBookmarkDto
import com.mydeck.app.io.rest.model.EditBookmarkErrorDto
import com.mydeck.app.io.rest.model.EditBookmarkResponseDto
import com.mydeck.app.io.rest.model.ImageResource
import com.mydeck.app.io.rest.model.Resource
import com.mydeck.app.io.rest.model.Resources
import com.mydeck.app.io.rest.model.StatusMessageDto
import com.mydeck.app.io.rest.model.SyncStatusDto
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkRepositoryImplTest {

    private lateinit var database: MyDeckDatabase
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var pendingActionDao: PendingActionDao
    private lateinit var readeckApi: ReadeckApi
    private lateinit var json: Json
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var settingsDataStore: SettingsDataStore
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var bookmarkRepositoryImpl: BookmarkRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        database = mockk<MyDeckDatabase>()
        bookmarkDao = mockk<BookmarkDao>(relaxed = true)
        pendingActionDao = mockk<PendingActionDao>()
        readeckApi = mockk<ReadeckApi>()
        json = Json { ignoreUnknownKeys = true }
        syncScheduler = mockk<SyncScheduler>(relaxed = true)
        settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        
        // Mock performTransaction catch-all
        // Mock performTransaction for different return types
        coEvery { database.performTransaction<Unit>(any()) } coAnswers {
            val block = it.invocation.args[0] as (suspend () -> Unit)
            block()
        }
        coEvery { database.performTransaction<Long>(any()) } coAnswers {
            val block = it.invocation.args[0] as (suspend () -> Long)
            block()
        }
        coEvery { database.performTransaction<Any?>(any()) } coAnswers {
            val block = it.invocation.args[0] as (suspend () -> Any?)
            block()
        }

        // Mock DAO getters
        every { database.getBookmarkDao() } returns bookmarkDao
        every { database.getPendingActionDao() } returns pendingActionDao

        // Standard mocks for PendingActionDao
        coEvery { pendingActionDao.find(any(), any()) } returns null
        coEvery { pendingActionDao.insert(any()) } returns 0L
        coEvery { pendingActionDao.deleteAllForBookmark(any()) } just runs
        coEvery { pendingActionDao.getActionsForBookmark(any()) } returns emptyList()
        coEvery { pendingActionDao.getActionsForBookmarks(any()) } returns emptyList()
        coEvery { pendingActionDao.delete(any()) } just runs
        coEvery { pendingActionDao.getAllActionsSorted() } returns emptyList()
        coEvery { pendingActionDao.updateAction(any(), any(), any()) } just runs
        
        // Standard mocks for BookmarkDao batch operations
        coEvery { bookmarkDao.getBookmarksByIds(any()) } returns emptyList()

        bookmarkRepositoryImpl = BookmarkRepositoryImpl(
            database = database,
            bookmarkDao = bookmarkDao,
            pendingActionDao = pendingActionDao,
            readeckApi = readeckApi,
            json = json,
            syncScheduler = syncScheduler,
            contentPackageManager = mockk(relaxed = true),
            settingsDataStore = settingsDataStore,
            applicationScope = testScope,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        clearMocks(database, bookmarkDao, pendingActionDao, syncScheduler)
        Dispatchers.resetMain()
    }

    @Test
    fun `updateBookmark successful`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(
            bookmarkId = bookmarkId,
            isFavorite = isFavorite,
            isArchived = null,
            isRead = null)

        // Assert
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `updateBookmark coalesces multiple updates of same type`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val existingAction = PendingActionEntity(
            id = 1,
            bookmarkId = bookmarkId,
            actionType = ActionType.TOGGLE_FAVORITE,
            payload = "old",
            createdAt = Clock.System.now()
        )
        coEvery { pendingActionDao.find(bookmarkId, ActionType.TOGGLE_FAVORITE) } returns existingAction

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, isFavorite = true, isArchived = null, isRead = null)

        // Assert
        coVerify { pendingActionDao.updateAction(1, any(), any()) }
        coVerify(exactly = 0) { pendingActionDao.insert(any()) }
    }

    @Test
    fun `updateBookmark isFavorite queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, isFavorite, null, null)

        // Assert
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `updateBookmark isArchived queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, null, isArchived, null)

        // Assert
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `updateBookmark isRead queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isRead = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, null, null, isRead)

        // Assert
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `deleteBookmark performs soft delete and queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"

        // Act
        bookmarkRepositoryImpl.deleteBookmark(id = bookmarkId)

        // Assert
        coVerify { bookmarkDao.softDeleteBookmark(bookmarkId) }
        coVerify { pendingActionDao.deleteAllForBookmark(bookmarkId) }
        coVerify { pendingActionDao.insert(match { it.actionType == ActionType.DELETE }) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `performFullSync successful sync with multiple pages`() = runTest {
        // Arrange
        val pageSize = 50
        val totalCount = 120
        val totalPages = 3
        val bookmarkList1 = List(pageSize) { bookmarkDto.copy(id = "bookmark_$it") }
        val bookmarkList2 = List(pageSize) { bookmarkDto.copy(id = "bookmark_${it + pageSize}") }
        val bookmarkList3 = List(20) { bookmarkDto.copy(id = "bookmark_${it + 2 * pageSize}") }

        coEvery {
            readeckApi.getBookmarks(limit = pageSize, offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(bookmarkList1, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, totalCount.toString(),
            ReadeckApi.Header.TOTAL_PAGES, totalPages.toString(),
            ReadeckApi.Header.CURRENT_PAGE, "1"
        ))

        coEvery {
            readeckApi.getBookmarks(limit = pageSize, offset = pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(bookmarkList2, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, totalCount.toString(),
            ReadeckApi.Header.TOTAL_PAGES, totalPages.toString(),
            ReadeckApi.Header.CURRENT_PAGE, "2"
        ))

        coEvery {
            readeckApi.getBookmarks(limit = pageSize, offset = 2 * pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(bookmarkList3, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, totalCount.toString(),
            ReadeckApi.Header.TOTAL_PAGES, totalPages.toString(),
            ReadeckApi.Header.CURRENT_PAGE, "3"
        ))

        coEvery { bookmarkDao.removeDeletedBookmars() } returns 10
        coEvery { bookmarkDao.insertRemoteBookmarkIds(any()) } returns Unit

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Success)
        assertEquals(10, (result as BookmarkRepository.SyncResult.Success).countDeleted)

        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null) }
        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null) }
        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = 2 * pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null) }
        coVerify { bookmarkDao.insertRemoteBookmarkIds(any()) }
        coVerify { bookmarkDao.removeDeletedBookmars() }
        coVerify { bookmarkDao.clearRemoteBookmarkIds() }
    }

    @Test
    fun `performFullSync persists bookmark metadata from paging response`() = runTest {
        // Arrange: single page of 3 bookmarks
        val bookmarkList = listOf(
            bookmarkDto.copy(id = "bk-1", title = "First"),
            bookmarkDto.copy(id = "bk-2", title = "Second"),
            bookmarkDto.copy(id = "bk-3", title = "Third")
        )

        coEvery {
            readeckApi.getBookmarks(limit = any(), offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(bookmarkList, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, "3",
            ReadeckApi.Header.TOTAL_PAGES, "1",
            ReadeckApi.Header.CURRENT_PAGE, "1"
        ))
        coEvery { bookmarkDao.removeDeletedBookmars() } returns 0

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert: metadata was persisted via upsertBookmarksMetadataOnly
        assertTrue(result is BookmarkRepository.SyncResult.Success)
        assertEquals(3, (result as BookmarkRepository.SyncResult.Success).countUpdated)
        coVerify(exactly = 1) {
            bookmarkDao.upsertBookmarksMetadataOnly(match { it.size == 3 })
        }
    }

    @Test
    fun `performFullSync returns inserted count in SyncResult`() = runTest {
        // Arrange: 2 pages
        val page1 = List(50) { bookmarkDto.copy(id = "p1-$it") }
        val page2 = List(25) { bookmarkDto.copy(id = "p2-$it") }

        coEvery {
            readeckApi.getBookmarks(limit = 50, offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(page1, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, "75",
            ReadeckApi.Header.TOTAL_PAGES, "2",
            ReadeckApi.Header.CURRENT_PAGE, "1"
        ))
        coEvery {
            readeckApi.getBookmarks(limit = 50, offset = 50, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = null)
        } returns Response.success(page2, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, "75",
            ReadeckApi.Header.TOTAL_PAGES, "2",
            ReadeckApi.Header.CURRENT_PAGE, "2"
        ))
        coEvery { bookmarkDao.removeDeletedBookmars() } returns 5

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        val success = result as BookmarkRepository.SyncResult.Success
        assertEquals(75, success.countUpdated)
        assertEquals(5, success.countDeleted)
        // Verify metadata was persisted for each page
        coVerify(exactly = 1) { bookmarkDao.upsertBookmarksMetadataOnly(match { it.size == 50 }) }
        coVerify(exactly = 1) { bookmarkDao.upsertBookmarksMetadataOnly(match { it.size == 25 }) }
    }

    @Test
    fun `insertBookmarks preserves existing embed when incoming embed is null`() = runTest {
        val existingEmbed = "<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>"
        val existingHostname = "www.youtube.com"
        val existingBookmark = bookmarkDto.copy(type = "video")
            .toDomain()
            .copy(embed = existingEmbed, embedHostname = existingHostname)
            .toEntity()
            .bookmark

        coEvery { bookmarkDao.getBookmarksByIds(listOf("1")) } returns listOf(existingBookmark)

        // Incoming bookmark has null embed (e.g. from list endpoint)
        val incoming = bookmarkDto.copy(type = "video").toDomain()
            .copy(embed = null, embedHostname = null)

        bookmarkRepositoryImpl.insertBookmarks(listOf(incoming))

        coVerify {
            bookmarkDao.upsertBookmarksMetadataOnly(match { bookmarks ->
                val bk = bookmarks.single()
                bk.embed == existingEmbed && bk.embedHostname == existingHostname
            })
        }
    }

    @Test
    fun `insertBookmarks overwrites embed when incoming embed is non-null`() = runTest {
        val existingBookmark = bookmarkDto.copy(type = "video")
            .toDomain()
            .copy(embed = "<iframe>old</iframe>", embedHostname = "old.example.com")
            .toEntity()
            .bookmark

        coEvery { bookmarkDao.getBookmarksByIds(listOf("1")) } returns listOf(existingBookmark)

        val newEmbed = "<iframe>new</iframe>"
        val incoming = bookmarkDto.copy(type = "video").toDomain()
            .copy(embed = newEmbed, embedHostname = "new.example.com")

        bookmarkRepositoryImpl.insertBookmarks(listOf(incoming))

        coVerify {
            bookmarkDao.upsertBookmarksMetadataOnly(match { bookmarks ->
                val bk = bookmarks.single()
                bk.embed == newEmbed && bk.embedHostname == "new.example.com"
            })
        }
    }

    @Test
    fun `performFullSync API error`() = runTest {
        // Arrange
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), sortOrder = ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = any()) } returns Response.error(500, "Error".toResponseBody())

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Error)
        assertEquals("Full sync failed", (result as BookmarkRepository.SyncResult.Error).errorMessage)
        assertEquals(500, result.code)
    }

    @Test
    fun `performFullSync missing headers`() = runTest {
        // Arrange
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), sortOrder = ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = any()) } returns Response.success(emptyList())

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Error)
        assertEquals("Missing headers in API response", (result as BookmarkRepository.SyncResult.Error).errorMessage)
    }

    @Test
    fun `performFullSync network error`() = runTest {
        // Arrange
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), sortOrder = ReadeckApi.SortOrder(ReadeckApi.Sort.Created), hasErrors = any()) } throws IOException("Network error")

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.NetworkError)
        assertEquals("Network error during full sync", (result as BookmarkRepository.SyncResult.NetworkError).errorMessage)
        assertTrue(result.ex is IOException)
    }


    @Test
    fun `performDeltaSync deletes bookmarks marked as deleted`() = runTest {
        // Arrange
        val since = kotlinx.datetime.Instant.parse("2023-10-27T10:00:00Z")
        val syncStatuses = listOf(
            SyncStatusDto(id = "bookmark-1", type = "update", time = "2023-10-27T11:00:00Z"),
            SyncStatusDto(id = "bookmark-2", type = "delete", time = "2023-10-27T11:00:00Z"),
        )
        coEvery { readeckApi.getSyncStatus(any()) } returns Response.success(syncStatuses)
        coEvery { bookmarkDao.deleteBookmark(any()) } just Runs

        // Act
        val result = bookmarkRepositoryImpl.performDeltaSync(since)

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Success)
        assertEquals(1, (result as BookmarkRepository.SyncResult.Success).countDeleted)
        assertEquals(1, result.countUpdated)
        coVerify(exactly = 1) { bookmarkDao.deleteBookmark("bookmark-2") }
        coVerify(exactly = 0) { bookmarkDao.deleteBookmark("bookmark-1") }
    }

    @Test
    fun `performDeltaSync returns error on API failure`() = runTest {
        // Arrange
        val since = kotlinx.datetime.Instant.parse("2023-10-27T10:00:00Z")
        coEvery { readeckApi.getSyncStatus(any()) } returns Response.error(
            500, "Server error".toResponseBody(null)
        )

        // Act
        val result = bookmarkRepositoryImpl.performDeltaSync(since)

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Error)
        assertEquals(500, (result as BookmarkRepository.SyncResult.Error).code)
    }

    @Test
    fun `createBookmark successful`() = runTest {
        // Arrange
        val title = "Test Bookmark"
        val url = "https://example.com"
        val labels = listOf("test", "bookmark")
        val bookmarkId = "new-bookmark-id"
        
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true
        
        val createBookmarkDto = CreateBookmarkDto(labels = labels, title = title, url = url)
        val headers = Headers.Builder()
            .add(ReadeckApi.Header.BOOKMARK_ID, bookmarkId)
            .build()
        coEvery { readeckApi.createBookmark(createBookmarkDto) } returns Response.success(StatusMessageDto(200, "Created"), headers)
        
        val bookmarkResponse = Response.success(bookmarkDto.copy(
            id = bookmarkId,
            state = 1, // LOADED
            hasArticle = true
        ))
        coEvery { readeckApi.getBookmarkById(bookmarkId) } returns bookmarkResponse
        
        // Act
        val result = bookmarkRepositoryImpl.createBookmark(title, url, labels)
        
        // Assert
        assertEquals(bookmarkId, result)
        coVerify { readeckApi.createBookmark(createBookmarkDto) }
        coVerify { readeckApi.getBookmarkById(bookmarkId) }
        coVerify { bookmarkDao.upsertBookmarksMetadataOnly(any()) }
        verify { syncScheduler.scheduleArticleDownload(bookmarkId) }
    }

    @Test
    fun `insertBookmarks uses content-aware path when article content is present`() = runTest {
        val bookmark = bookmarkDto.toDomain().copy(
            articleContent = "<article>Synced content</article>",
            contentState = Bookmark.ContentState.DOWNLOADED,
            contentFailureReason = null
        )

        bookmarkRepositoryImpl.insertBookmarks(listOf(bookmark))

        coVerify(exactly = 1) { bookmarkDao.insertBookmarksWithArticleContent(any()) }
        coVerify(exactly = 0) { bookmarkDao.upsertBookmarksMetadataOnly(any()) }
    }

    @Test
    fun `insertBookmarks uses content-aware path when content state is explicitly changed`() = runTest {
        val bookmark = bookmarkDto.toDomain().copy(
            articleContent = null,
            contentState = Bookmark.ContentState.DIRTY,
            contentFailureReason = "network timeout"
        )

        bookmarkRepositoryImpl.insertBookmarks(listOf(bookmark))

        coVerify(exactly = 1) { bookmarkDao.insertBookmarksWithArticleContent(any()) }
        coVerify(exactly = 0) { bookmarkDao.upsertBookmarksMetadataOnly(any()) }
    }

    @Test
    fun `insertBookmarks metadata-only preserves omitDescription when description is unchanged`() = runTest {
        val existingBookmark = bookmarkDto.toDomain()
            .copy(omitDescription = true)
            .toEntity()
            .bookmark
        val incomingBookmark = bookmarkDto.toDomain()

        coEvery { bookmarkDao.getBookmarksByIds(listOf(incomingBookmark.id)) } returns listOf(existingBookmark)

        bookmarkRepositoryImpl.insertBookmarks(listOf(incomingBookmark))

        coVerify(exactly = 1) {
            bookmarkDao.upsertBookmarksMetadataOnly(
                match { bookmarks ->
                    bookmarks.singleOrNull()?.omitDescription == true
                }
            )
        }
    }

    @Test
    fun `insertBookmarks metadata-only preserves omitDescription even when description changes`() = runTest {
        // The list endpoint omits omit_description, so incoming is always null.
        // We must preserve the existing value regardless of description changes.
        // Only a content package fetch (non-null incoming) or local metadata edit can change it.
        val existingBookmark = bookmarkDto.toDomain()
            .copy(description = "Original description", omitDescription = true)
            .toEntity()
            .bookmark
        val incomingBookmark = bookmarkDto.copy(description = "Updated description").toDomain()

        coEvery { bookmarkDao.getBookmarksByIds(listOf(incomingBookmark.id)) } returns listOf(existingBookmark)

        bookmarkRepositoryImpl.insertBookmarks(listOf(incomingBookmark))

        coVerify(exactly = 1) {
            bookmarkDao.upsertBookmarksMetadataOnly(
                match { bookmarks ->
                    bookmarks.singleOrNull()?.omitDescription == true
                }
            )
        }
    }

    @Test
    fun `updateMetadata clears omitDescription when description changes`() = runTest {
        val bookmarkId = "1"
        val existingBookmark = bookmarkDto.toDomain()
            .copy(description = "Original description", omitDescription = true)
            .toEntity()
            .bookmark
        val metadata = BookmarkMetadataUpdate(
            title = "Sample Article",
            description = "Updated description",
            siteName = "Example Site",
            authors = listOf("John Doe", "Jane Smith"),
            published = null,
            lang = "en",
            textDirection = "ltr"
        )

        coEvery { bookmarkDao.getBookmarkById(bookmarkId) } returns existingBookmark

        bookmarkRepositoryImpl.updateMetadata(bookmarkId, metadata)

        coVerify(exactly = 1) {
            bookmarkDao.updateMetadata(
                id = bookmarkId,
                title = metadata.title,
                description = metadata.description,
                siteName = metadata.siteName,
                authors = metadata.authors,
                published = metadata.published,
                lang = metadata.lang,
                textDirection = metadata.textDirection.orEmpty(),
                omitDescription = null
            )
        }
    }

    @Test
    fun `updateReadProgress successful queues action`() = runTest {
        // Arrange
        val bookmarkId = "test-bookmark-id"
        val progress = 75
        
        // Act
        val result = bookmarkRepositoryImpl.updateReadProgress(bookmarkId, progress)
        
        // Assert
        assertTrue(result is BookmarkRepository.UpdateResult.Success)
        coVerify { bookmarkDao.updateReadProgress(bookmarkId, progress) }
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `updateLabels successful queues action`() = runTest {
        // Arrange
        val bookmarkId = "test-bookmark-id"
        val labels = listOf("test", "labels")
        
        // Act
        val result = bookmarkRepositoryImpl.updateLabels(bookmarkId, labels)
        
        // Assert
        assertTrue(result is BookmarkRepository.UpdateResult.Success)
        coVerify { bookmarkDao.updateLabels(any(), any()) }
        coVerify { pendingActionDao.insert(any()) }
        verify { syncScheduler.scheduleActionSync() }
    }

    @Test
    fun `syncPendingActions handles 404 by hard deleting locally`() = runTest {
        // Arrange
        val action = PendingActionEntity(1, "123", ActionType.TOGGLE_FAVORITE, "{\"value\":true}", Clock.System.now())
        coEvery { pendingActionDao.getAllActionsSorted() } returns listOf(action)
        coEvery { readeckApi.editBookmark("123", any()) } returns Response.error(404, "".toResponseBody())

        // Act
        bookmarkRepositoryImpl.syncPendingActions()

        // Assert
        coVerify { bookmarkDao.hardDeleteBookmark("123") }
        coVerify { pendingActionDao.deleteAllForBookmark("123") }
        coVerify { pendingActionDao.delete(action) }
    }

    @Test
    fun `syncPendingActions stops on transient error`() = runTest {
        // Arrange
        val action1 = PendingActionEntity(1, "123", ActionType.TOGGLE_FAVORITE, "{\"value\":true}", Clock.System.now())
        val action2 = PendingActionEntity(2, "456", ActionType.TOGGLE_ARCHIVE, "{\"value\":true}", Clock.System.now())
        coEvery { pendingActionDao.getAllActionsSorted() } returns listOf(action1, action2)
        coEvery { readeckApi.editBookmark("123", any()) } throws IOException("Network error")

        // Act
        val result = bookmarkRepositoryImpl.syncPendingActions()

        // Assert
        assertTrue(result is BookmarkRepository.UpdateResult.NetworkError)
        coVerify(exactly = 0) { readeckApi.editBookmark("456", any()) }
        coVerify(exactly = 0) { pendingActionDao.delete(action1) }
    }

    private val editBookmarkResponseDto = EditBookmarkResponseDto(
        href = "http://example.com",
        id = "123",
        isArchived = true,
        isDeleted = true,
        isMarked = true,
        labels = listOf("label1", "label2"),
        readAnchor = "anchor1",
        readProgress = 50,
        title = "New Title",
        updated = Clock.System.now()
    )

    val bookmarkDto = BookmarkDto(
        id = "1",
        href = "https://example.com",
        created = Clock.System.now().minus(1.days),
        updated = Clock.System.now().minus(1.days),
        state = 1,
        loaded = true,
        url = "https://example.com/article",
        title = "Sample Article",
        siteName = "Example Site",
        site = "example.com",
        authors = listOf("John Doe", "Jane Smith"),
        lang = "en",
        textDirection = "ltr",
        documentTpe = "article",
        type = "article",
        hasArticle = true,
        description = "This is a sample article description.",
        isDeleted = false,
        isMarked = false,
        isArchived = false,
        labels = listOf("sample", "article"),
        readProgress = 0,
        resources = Resources(
            article = Resource(src = "https://example.com/article.pdf"),
            icon = ImageResource(src = "https://example.com/icon.png", width = 32, height = 32),
            image = ImageResource(src = "https://example.com/image.jpg", width = 600, height = 400),
            log = Resource(src = "https://example.com/log.txt"),
            props = Resource(src = "https://example.com/props.json"),
            thumbnail = ImageResource(
                src = "https://example.com/thumbnail.jpg",
                width = 200,
                height = 150
            )
        ),
        wordCount = 1000,
        readingTime = 5
    )
}
