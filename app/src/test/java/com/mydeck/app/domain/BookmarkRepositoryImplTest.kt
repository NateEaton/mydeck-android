package com.mydeck.app.domain

import androidx.room.withTransaction
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.PendingActionDao
import com.mydeck.app.io.db.model.PendingActionEntity
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
import com.mydeck.app.io.rest.model.SyncContentRequestDto
import com.mydeck.app.io.rest.model.SyncStatusDto
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
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
    private lateinit var workManager: WorkManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var bookmarkRepositoryImpl: BookmarkRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        database = mockk<MyDeckDatabase>(relaxed = true)
        bookmarkDao = mockk<BookmarkDao>(relaxed = true)
        pendingActionDao = mockk<PendingActionDao>(relaxed = true)
        readeckApi = mockk<ReadeckApi>()
        json = Json { ignoreUnknownKeys = true }
        workManager = mockk<WorkManager>(relaxed = true)
        
        // Mock performTransaction to just execute the block
        coEvery { database.performTransaction<Any?>(any()) } coAnswers {
            val block = arg<suspend () -> Any?>(0)
            block.invoke()
        }

        bookmarkRepositoryImpl = BookmarkRepositoryImpl(
            database = database,
            bookmarkDao = bookmarkDao,
            pendingActionDao = pendingActionDao,
            readeckApi = readeckApi,
            json = json,
            workManager = workManager,
            applicationScope = testScope,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        clearMocks(database, bookmarkDao, pendingActionDao, workManager)
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
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `updateBookmark isFavorite queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, isFavorite, null, null)

        // Assert
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `updateBookmark isArchived queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, null, isArchived, null)

        // Assert
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `updateBookmark isRead queues action`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isRead = true

        // Act
        bookmarkRepositoryImpl.updateBookmark(bookmarkId, null, null, isRead)

        // Assert
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
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
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
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
            readeckApi.getBookmarks(limit = pageSize, offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created))
        } returns Response.success(bookmarkList1, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, totalCount.toString(),
            ReadeckApi.Header.TOTAL_PAGES, totalPages.toString(),
            ReadeckApi.Header.CURRENT_PAGE, "1"
        ))

        coEvery {
            readeckApi.getBookmarks(limit = pageSize, offset = pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created))
        } returns Response.success(bookmarkList2, Headers.headersOf(
            ReadeckApi.Header.TOTAL_COUNT, totalCount.toString(),
            ReadeckApi.Header.TOTAL_PAGES, totalPages.toString(),
            ReadeckApi.Header.CURRENT_PAGE, "2"
        ))

        coEvery {
            readeckApi.getBookmarks(limit = pageSize, offset = 2 * pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created))
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

        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = 0, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) }
        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) }
        coVerify { readeckApi.getBookmarks(limit = pageSize, offset = 2 * pageSize, updatedSince = null, ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) }
        coVerify { bookmarkDao.insertRemoteBookmarkIds(any()) }
        coVerify { bookmarkDao.removeDeletedBookmars() }
        coVerify { bookmarkDao.clearRemoteBookmarkIds() }
    }

    @Test
    fun `performFullSync API error`() = runTest {
        // Arrange
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) } returns Response.error(500, "Error".toResponseBody())

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
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) } returns Response.success(emptyList())

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Error)
        assertEquals("Missing headers in API response", (result as BookmarkRepository.SyncResult.Error).errorMessage)
    }

    @Test
    fun `performFullSync network error`() = runTest {
        // Arrange
        coEvery { readeckApi.getBookmarks(limit = any(), offset = any(), updatedSince = any(), ReadeckApi.SortOrder(ReadeckApi.Sort.Created)) } throws IOException("Network error")

        // Act
        val result = bookmarkRepositoryImpl.performFullSync()

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.NetworkError)
        assertEquals("Network error during full sync", (result as BookmarkRepository.SyncResult.NetworkError).errorMessage)
        assertTrue(result.ex is IOException)
    }


    @Test
    fun `performDeltaSync returns error because it is disabled`() = runTest {
        // Arrange
        val since = kotlinx.datetime.Instant.parse("2023-10-27T10:00:00Z")

        // Act
        val result = bookmarkRepositoryImpl.performDeltaSync(since)

        // Assert
        assertTrue(result is BookmarkRepository.SyncResult.Error)
        assertTrue((result as BookmarkRepository.SyncResult.Error).errorMessage.contains("Delta sync disabled"))
    }

    @Test
    fun `createBookmark successful`() = runTest {
        // Arrange
        val title = "Test Bookmark"
        val url = "https://example.com"
        val labels = listOf("test", "bookmark")
        val bookmarkId = "new-bookmark-id"
        
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
        coVerify { bookmarkDao.insertBookmarksWithArticleContent(any()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun `refreshBookmarkFromApi successful`() = runTest {
        // Arrange
        val bookmarkId = "test-bookmark-id"
        val updatedBookmark = bookmarkDto.copy(title = "Updated Title")
        val response = Response.success(updatedBookmark)
        
        coEvery { readeckApi.getBookmarkById(bookmarkId) } returns response
        
        // Act
        bookmarkRepositoryImpl.refreshBookmarkFromApi(bookmarkId)
        
        // Assert
        coVerify { readeckApi.getBookmarkById(bookmarkId) }
        coVerify { bookmarkDao.insertBookmarksWithArticleContent(any()) }
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
        coVerify { pendingActionDao.insert(any<PendingActionEntity>()) }
        coVerify { workManager.enqueue(any<WorkRequest>()) }
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
