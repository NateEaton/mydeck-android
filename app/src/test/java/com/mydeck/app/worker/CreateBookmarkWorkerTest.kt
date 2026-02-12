package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
        isArchived: Boolean = false
    ): Data {
        val data = mockk<Data>()
        every { data.getString(CreateBookmarkWorker.PARAM_URL) } returns url
        every { data.getString(CreateBookmarkWorker.PARAM_TITLE) } returns title
        every { data.getStringArray(CreateBookmarkWorker.PARAM_LABELS) } returns labels
        every { data.getBoolean(CreateBookmarkWorker.PARAM_IS_ARCHIVED, false) } returns isArchived
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
        coEvery { bookmarkRepository.updateBookmark(any(), any(), any(), any()) } returns BookmarkRepository.UpdateResult.Success

        val result = worker.doWork()

        assert(result is ListenableWorker.Result.Success)
        coVerify { bookmarkRepository.updateBookmark("bookmark-123", null, true, null) }
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
}
