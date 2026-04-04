package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyDefaults
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.usecase.LoadContentPackageUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BatchArticleLoadWorkerTest {

    private lateinit var context: Context
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var loadContentPackageUseCase: LoadContentPackageUseCase
    private lateinit var policyEvaluator: OfflinePolicyEvaluator
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var contentPackageManager: ContentPackageManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bookmarkDao = mockk()
        loadContentPackageUseCase = mockk()
        policyEvaluator = mockk()
        settingsDataStore = mockk()
        contentPackageManager = mockk()

        // Common defaults
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { settingsDataStore.saveLastContentSyncTimestamp(any()) } returns Unit
        coEvery { policyEvaluator.canFetchContent() } returns OfflinePolicyEvaluator.Decision(true)
    }

    private fun createWorker(inputData: Data = Data.EMPTY): BatchArticleLoadWorker {
        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        return BatchArticleLoadWorker(
            appContext = context,
            workerParams = workerParams,
            bookmarkDao = bookmarkDao,
            loadContentPackageUseCase = loadContentPackageUseCase,
            policyEvaluator = policyEvaluator,
            settingsDataStore = settingsDataStore,
            contentPackageManager = contentPackageManager
        )
    }

    // --- Offline reading disabled ---

    @Test
    fun `doWork returns success immediately when offline reading is disabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns false

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { bookmarkDao.getOfflinePolicyBookmarks(any()) }
    }

    // --- Batch path: no eligible bookmarks ---

    @Test
    fun `doWork succeeds with no downloads when no bookmarks are eligible`() = runTest {
        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns emptyList()
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns emptyList()

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { loadContentPackageUseCase.executeBatch(any()) }
        coVerify { settingsDataStore.saveLastContentSyncTimestamp(any()) }
    }

    // --- Batch path: eligible bookmarks downloaded and pruned ---

    @Test
    fun `doWork downloads eligible bookmarks and prunes after batch`() = runTest {
        val policyBookmarks = listOf(
            policyBookmark("1", "2026-03-01T00:00:00Z", hasOfflinePackage = false),
            policyBookmark("2", "2026-03-15T00:00:00Z", hasOfflinePackage = false)
        )

        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returns true
        coEvery { loadContentPackageUseCase.executeBatch(listOf("1", "2")) } returns mapOf(
            "1" to LoadContentPackageUseCase.Result.Success,
            "2" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { loadContentPackageUseCase.executeBatch(listOf("1", "2")) }
        coVerify { settingsDataStore.saveLastContentSyncTimestamp(any()) }
    }

    @Test
    fun `doWork skips bookmarks that already have offline packages`() = runTest {
        val policyBookmarks = listOf(
            policyBookmark("1", "2026-03-01T00:00:00Z", hasOfflinePackage = true),
            policyBookmark("2", "2026-03-15T00:00:00Z", hasOfflinePackage = false)
        )

        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarks[0]) } returns false
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarks[1]) } returns true
        coEvery { loadContentPackageUseCase.executeBatch(listOf("2")) } returns mapOf(
            "2" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { loadContentPackageUseCase.executeBatch(listOf("2")) }
    }

    // --- Constraint check between batches ---

    @Test
    fun `doWork stops when constraints become unsatisfied between batches`() = runTest {
        // Create 15 bookmarks so there are 2 batches (10 + 5)
        val policyBookmarks = (1..15).map {
            policyBookmark("$it", "2026-03-${it.toString().padStart(2, '0')}T00:00:00Z", hasOfflinePackage = false)
        }

        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returns true

        // Allow first batch, block second
        coEvery { policyEvaluator.canFetchContent() } returnsMany listOf(
            OfflinePolicyEvaluator.Decision(true),
            OfflinePolicyEvaluator.Decision(false, "Wi-Fi required")
        )

        val firstBatchIds = (1..10).map { "$it" }
        coEvery { loadContentPackageUseCase.executeBatch(firstBatchIds) } returns
            firstBatchIds.associateWith { LoadContentPackageUseCase.Result.Success }

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { loadContentPackageUseCase.executeBatch(any()) }
    }

    // --- Priority bookmark path ---

    @Test
    fun `doWork processes priority bookmark directly`() = runTest {
        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { bookmarkDao.getIsArchived("priority-1") } returns false
        coEvery { loadContentPackageUseCase.executeBatch(listOf("priority-1")) } returns mapOf(
            "priority-1" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { loadContentPackageUseCase.executeBatch(listOf("priority-1")) }
    }

    @Test
    fun `doWork skips priority bookmark when offline reading disabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns false

        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { loadContentPackageUseCase.executeBatch(any()) }
    }

    @Test
    fun `doWork skips archived priority bookmark when scope excludes archived`() = runTest {
        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { bookmarkDao.getIsArchived("priority-1") } returns true

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { loadContentPackageUseCase.executeBatch(any()) }
    }

    @Test
    fun `doWork downloads archived priority bookmark when scope includes archived`() = runTest {
        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST_AND_ARCHIVED
        coEvery { loadContentPackageUseCase.executeBatch(listOf("priority-1")) } returns mapOf(
            "priority-1" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { loadContentPackageUseCase.executeBatch(listOf("priority-1")) }
    }

    @Test
    fun `doWork skips priority bookmark when constraints block fetch`() = runTest {
        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { bookmarkDao.getIsArchived("priority-1") } returns false
        coEvery { policyEvaluator.canFetchContent() } returns OfflinePolicyEvaluator.Decision(false, "No network")

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { loadContentPackageUseCase.executeBatch(any()) }
    }

    // --- Prune cycle ---

    @Test
    fun `doWork prunes content before and after downloading`() = runTest {
        val downloadedWithPackage = listOf(
            policyBookmark("old-1", "2026-01-01T00:00:00Z", hasOfflinePackage = true)
        )
        val newBookmark = policyBookmark("new-1", "2026-03-15T00:00:00Z", hasOfflinePackage = false)

        // First prune call (before download): needs pruning, prunes old-1, then stops
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns
            downloadedWithPackage + newBookmark
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returnsMany listOf(
            2000L, // first prune check: over limit
            500L,  // after pruning old-1: under limit
            500L,  // prune check after download batch
            500L   // final prune check
        )
        coEvery { policyEvaluator.shouldPrune(any(), eq(2000L), any()) } returns true
        coEvery { policyEvaluator.shouldPrune(any(), eq(500L), any()) } returns false
        coEvery { policyEvaluator.selectForPruning(any(), eq(2000L), any()) } returns listOf("old-1")
        coEvery { contentPackageManager.deleteContentForBookmark("old-1") } returns Unit

        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns listOf(newBookmark)
        coEvery { policyEvaluator.needsOfflinePackage(newBookmark) } returns true
        coEvery { loadContentPackageUseCase.executeBatch(listOf("new-1")) } returns mapOf(
            "new-1" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { contentPackageManager.deleteContentForBookmark("old-1") }
        coVerify { loadContentPackageUseCase.executeBatch(listOf("new-1")) }
    }

    // --- Error handling ---

    @Test
    fun `doWork retries on unexpected exception`() = runTest {
        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } throws RuntimeException("DB error")

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork priority path retries on unexpected exception`() = runTest {
        val inputData = Data.Builder()
            .putString(BatchArticleLoadWorker.KEY_PRIORITY_BOOKMARK_ID, "priority-1")
            .build()

        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { bookmarkDao.getIsArchived("priority-1") } returns false
        coEvery { loadContentPackageUseCase.executeBatch(any()) } throws RuntimeException("Network error")

        val result = createWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // --- Scope: includeArchived ---

    @Test
    fun `doWork passes includeArchived true when scope includes archived`() = runTest {
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST_AND_ARCHIVED
        stubPruneLoop(emptyList(), 0L)
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(true) } returns emptyList()
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns emptyList()

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { bookmarkDao.getOfflinePolicyBookmarks(true) }
    }

    // --- Helpers ---

    private fun stubPruneLoop(
        downloadedWithPackages: List<BookmarkDao.OfflinePolicyBookmark>,
        totalUsageBytes: Long
    ) {
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns totalUsageBytes
        coEvery { policyEvaluator.shouldPrune(any(), eq(totalUsageBytes), any()) } returns false
    }

    private fun policyBookmark(
        id: String,
        created: String,
        hasOfflinePackage: Boolean = false,
        contentState: BookmarkEntity.ContentState = BookmarkEntity.ContentState.NOT_ATTEMPTED
    ): BookmarkDao.OfflinePolicyBookmark {
        return BookmarkDao.OfflinePolicyBookmark(
            id = id,
            created = Instant.parse(created),
            contentState = contentState,
            hasOfflinePackage = hasOfflinePackage
        )
    }
}
