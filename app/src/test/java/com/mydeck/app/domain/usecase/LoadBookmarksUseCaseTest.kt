package com.mydeck.app.domain.usecase

import androidx.work.WorkManager
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.BookmarkDto
import com.mydeck.app.io.rest.model.ImageResource
import com.mydeck.app.io.rest.model.Resource
import com.mydeck.app.io.rest.model.Resources
import com.mydeck.app.io.rest.sync.BookmarkSyncPackage
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LoadBookmarksUseCaseTest {

    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var readeckApi: ReadeckApi
    private lateinit var multipartSyncClient: MultipartSyncClient
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var policyEvaluator: ContentSyncPolicyEvaluator
    private lateinit var workManager: WorkManager
    private lateinit var loadBookmarksUseCase: LoadBookmarksUseCase

    @Before
    fun setUp() {
        bookmarkRepository = mockk(relaxed = true)
        readeckApi = mockk()
        multipartSyncClient = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        policyEvaluator = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        coEvery { policyEvaluator.shouldAutoFetchContent() } returns false
        loadBookmarksUseCase = LoadBookmarksUseCase(
            bookmarkRepository,
            readeckApi,
            multipartSyncClient,
            settingsDataStore,
            policyEvaluator,
            workManager
        )
    }

    @Test
    fun `execute successful load`() = runBlocking {
        val pkg = BookmarkSyncPackage(bookmarkId = "2", json = bookmark2)
        coEvery { multipartSyncClient.fetchMetadata(listOf("2")) } returns MultipartSyncClient.Result.Success(listOf(pkg))
        coEvery { bookmarkRepository.insertBookmarks(any()) } returns Unit
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { settingsDataStore.saveLastBookmarkTimestamp(any()) } returns Unit
        coEvery { readeckApi.getBookmarks(any(), any(), any(), any(), any()) } returns Response.success(
            emptyList(),
            Headers.headersOf(
                ReadeckApi.Header.TOTAL_PAGES, "1",
                ReadeckApi.Header.CURRENT_PAGE, "1"
            )
        )

        // Execute the use case
        val result = loadBookmarksUseCase.execute(updatedIds = listOf("2"), pageSize = 10, initialOffset = 0)

        println("result=$result")
        // Verify the result
        assertTrue(result is LoadBookmarksUseCase.UseCaseResult.Success<*>)
        coVerify { multipartSyncClient.fetchMetadata(listOf("2")) }
        coVerify { bookmarkRepository.insertBookmarks(match { it.size == 1 && it.first().id == "2" }) }
    }

    @Test
    fun `execute api error`() = runBlocking {
        coEvery { multipartSyncClient.fetchMetadata(any()) } returns MultipartSyncClient.Result.Error("boom", 500)

        // Execute the use case
        val result = loadBookmarksUseCase.execute(updatedIds = listOf("2"), pageSize = 10, initialOffset = 0)

        // Verify the result
        assertTrue(result is LoadBookmarksUseCase.UseCaseResult.Error)
        // Add more specific assertions based on your logic
    }

    @Test
    fun `execute exception thrown`() = runBlocking {
        coEvery { multipartSyncClient.fetchMetadata(any()) } throws RuntimeException("Test Exception")

        // Execute the use case
        val result = loadBookmarksUseCase.execute(updatedIds = listOf("2"), pageSize = 10, initialOffset = 0)

        // Verify the result
        assertTrue(result is LoadBookmarksUseCase.UseCaseResult.Error)
        // Add more specific assertions based on your logic
    }

    @Test
    fun `execute with null updatedIds returns success without fetching`() = runBlocking {
        val result = loadBookmarksUseCase.execute(updatedIds = null)

        assertTrue(result is LoadBookmarksUseCase.UseCaseResult.Success<*>)
        coVerify(exactly = 0) { multipartSyncClient.fetchMetadata(any()) }
        coVerify(exactly = 0) { bookmarkRepository.insertBookmarks(any()) }
    }

    @Test
    fun `execute with empty updatedIds returns success without fetching`() = runBlocking {
        val result = loadBookmarksUseCase.execute(updatedIds = emptyList())

        assertTrue(result is LoadBookmarksUseCase.UseCaseResult.Success<*>)
        coVerify(exactly = 0) { multipartSyncClient.fetchMetadata(any()) }
        coVerify(exactly = 0) { bookmarkRepository.insertBookmarks(any()) }
    }

    @Test
    fun `execute saves last bookmark timestamp`() = runBlocking {
        val pkg2 = BookmarkSyncPackage(bookmarkId = "2", json = bookmark2)
        val pkg1 = BookmarkSyncPackage(bookmarkId = "1", json = bookmark1)
        coEvery { multipartSyncClient.fetchMetadata(listOf("2", "1")) } returns MultipartSyncClient.Result.Success(listOf(pkg2, pkg1))
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { settingsDataStore.saveLastBookmarkTimestamp(any()) } returns Unit
        coEvery { bookmarkRepository.insertBookmarks(any()) } returns Unit
        coEvery { readeckApi.getBookmarks(any(), any(), any(), any(), any()) } returns Response.success(
            emptyList(),
            Headers.headersOf(
                ReadeckApi.Header.TOTAL_PAGES, "1",
                ReadeckApi.Header.CURRENT_PAGE, "1"
            )
        )

        loadBookmarksUseCase.execute(updatedIds = listOf("2", "1"), pageSize = 10, initialOffset = 0)

        coVerify { settingsDataStore.saveLastBookmarkTimestamp(Instant.fromEpochSeconds(bookmark2.updated.epochSeconds)) }
        coVerify { bookmarkRepository.insertBookmarks(match { it.size == 2 }) }
    }

    val bookmark2 = BookmarkDto(
        id = "2",
        href = "https://example.com",
        created = Instant.parse("2026-03-06T08:15:30Z"),
        updated = Instant.parse("2026-03-12T19:46:28.351123456Z"),
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
    val bookmark1 = BookmarkDto(
        id = "1",
        href = "https://example.com",
        created = Instant.parse("2024-03-06T08:15:30Z"),
        updated = Instant.parse("2026-03-11T10:00:00Z"),
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
    val sampleBookmarks = listOf(bookmark2, bookmark1)
}
