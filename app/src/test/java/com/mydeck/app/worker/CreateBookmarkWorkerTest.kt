package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBookmarkWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkRepositoryProvider: Provider<BookmarkRepository>

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkRepositoryProvider = mockk()
        every { bookmarkRepositoryProvider.get() } returns bookmarkRepository
    }

    private fun createInputData(
        url: String? = "https://example.com",
        title: String? = "Test Title",
        labels: Array<String>? = arrayOf("label1", "label2"),
        isArchived: Boolean = false,
        isFavorite: Boolean = false,
        attemptTimestampMs: Long = 0L
    ): Data {
        val data = mockk<Data>()
        every { data.getString(CreateBookmarkWorker.PARAM_URL) } returns url
        every { data.getString(CreateBookmarkWorker.PARAM_TITLE) } returns title
        every { data.getStringArray(CreateBookmarkWorker.PARAM_LABELS) } returns labels
        every { data.getBoolean(CreateBookmarkWorker.PARAM_IS_ARCHIVED, false) } returns isArchived
        every { data.getBoolean(CreateBookmarkWorker.PARAM_IS_FAVORITE, false) } returns isFavorite
        every { data.getLong(CreateBookmarkWorker.PARAM_ATTEMPT_TS, 0L) } returns attemptTimestampMs
        return data
    }

    private fun createWorker(inputData: Data): CreateBookmarkWorker {
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 0
        return CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
    }

    @Test
    fun `doWork succeeds with valid URL`() = runTest {
        val inputData = createInputData()
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(
            Data.Builder().putString(CreateBookmarkWorker.RESULT_BOOKMARK_ID, "bookmark-123").build()
        ), result)
        coVerify { bookmarkRepository.createBookmark("Test Title", "https://example.com", listOf("label1", "label2")) }
    }

    @Test
    fun `doWork fails with null URL`() = runTest {
        val inputData = createInputData(url = null)
        val worker = createWorker(inputData)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { bookmarkRepository.createBookmark(any(), any(), any()) }
    }

    @Test
    fun `doWork fails with blank URL`() = runTest {
        val inputData = createInputData(url = "")
        val worker = createWorker(inputData)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { bookmarkRepository.createBookmark(any(), any(), any()) }
    }

    @Test
    fun `doWork archives bookmark when isArchived is true`() = runTest {
        val inputData = createInputData(isArchived = true)
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"
        coEvery { bookmarkRepository.getBookmarkById(any()) } returns mockk(relaxed = true) {
            every { state } returns com.mydeck.app.domain.model.Bookmark.State.LOADED
        }
        coEvery { bookmarkRepository.updateBookmark(any(), any(), any(), any()) } returns BookmarkRepository.UpdateResult.Success

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify { bookmarkRepository.updateBookmark("bookmark-123", null, true, null) }
    }

    @Test
    fun `doWork marks bookmark as favorite when isFavorite is true`() = runTest {
        val inputData = createInputData(isFavorite = true)
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"
        coEvery { bookmarkRepository.getBookmarkById(any()) } returns mockk(relaxed = true) {
            every { state } returns com.mydeck.app.domain.model.Bookmark.State.LOADED
        }
        coEvery { bookmarkRepository.updateBookmark(any(), any(), any(), any()) } returns BookmarkRepository.UpdateResult.Success

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify { bookmarkRepository.updateBookmark("bookmark-123", true, null, null) }
    }

    @Test
    fun `doWork does not archive when isArchived is false`() = runTest {
        val inputData = createInputData(isArchived = false)
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { bookmarkRepository.updateBookmark(any(), any(), any(), any()) }
    }

    @Test
    fun `doWork retries on exception when attempts remaining`() = runTest {
        val inputData = createInputData()
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 0
        val worker = CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } throws RuntimeException("Network error")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork rethrows cancellation instead of retrying create`() = runTest {
        val inputData = createInputData()
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 0
        val worker = CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
        coEvery {
            bookmarkRepository.createBookmark(any(), any(), any())
        } throws CancellationException("work stopped")

        try {
            worker.doWork()
            fail("Expected cancellation to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("work stopped", e.message)
        }
    }

    @Test
    fun `doWork fails on exception when max attempts reached`() = runTest {
        val inputData = createInputData()
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 3
        val worker = CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } throws RuntimeException("Network error")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork handles empty labels`() = runTest {
        val inputData = createInputData(labels = null)
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify { bookmarkRepository.createBookmark("Test Title", "https://example.com", emptyList()) }
    }

    @Test
    fun `doWork handles null title as empty string`() = runTest {
        val inputData = createInputData(title = null)
        val worker = createWorker(inputData)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify { bookmarkRepository.createBookmark("", "https://example.com", listOf("label1", "label2")) }
    }

    // --- W3: idempotent background create ---

    @Test
    fun `enqueue coalesces duplicate URLs via unique work with KEEP`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val url = "https://example.com/article"

        CreateBookmarkWorker.enqueue(
            workManager = workManager,
            url = url,
            title = "Title",
            labels = emptyList()
        )

        // Unique work keyed by URL + KEEP = a double-tap/double-share can't spawn a second worker
        // for the same URL (test matrix W3: "concurrent duplicate enqueues -> one bookmark").
        verify {
            workManager.enqueueUniqueWork(
                CreateBookmarkWorker.uniqueWorkName(url),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `uniqueWorkName is stable per URL and distinct across URLs`() {
        val a = CreateBookmarkWorker.uniqueWorkName("https://example.com/a")
        val aAgain = CreateBookmarkWorker.uniqueWorkName("https://example.com/a")
        val b = CreateBookmarkWorker.uniqueWorkName("https://example.com/b")

        assertEquals(a, aAgain) // same URL coalesces
        assert(a != b)          // different URLs do not collide
        assertEquals("create_" + UUID.nameUUIDFromBytes("https://example.com/a".toByteArray()), a)
    }

    @Test
    fun `doWork on retry passes attempt timestamp so the repository reconciles`() = runTest {
        val attemptTs = 1_700_000_000_000L
        val inputData = createInputData(attemptTimestampMs = attemptTs)
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 1
        val worker = CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify {
            bookmarkRepository.createBookmark(
                "Test Title", "https://example.com", listOf("label1", "label2"), attemptTs
            )
        }
    }

    @Test
    fun `doWork on first attempt does not request reconcile`() = runTest {
        val inputData = createInputData(attemptTimestampMs = 1_700_000_000_000L)
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns 0
        val worker = CreateBookmarkWorker(context, workerParams, bookmarkRepositoryProvider)
        coEvery { bookmarkRepository.createBookmark(any(), any(), any(), any()) } returns "bookmark-123"

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        // First attempt -> null timestamp -> repository POSTs directly (happy path unchanged).
        coVerify {
            bookmarkRepository.createBookmark(
                "Test Title", "https://example.com", listOf("label1", "label2"), null
            )
        }
    }
}
