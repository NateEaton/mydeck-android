package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.OfflineContentScope
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
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns emptyList()
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 0L
        coEvery { policyEvaluator.shouldStopDownloading(0L, 0) } returns false
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
        val policyBookmarksAfter = listOf(
            policyBookmark("1", "2026-03-01T00:00:00Z", hasOfflinePackage = true),
            policyBookmark("2", "2026-03-15T00:00:00Z", hasOfflinePackage = true)
        )

        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 50_000_000L
        coEvery { policyEvaluator.shouldStopDownloading(any(), any()) } returns false
        coEvery { policyEvaluator.downloadHeadroomBytes(any(), any()) } returns 46_000_000L
        coEvery { policyEvaluator.shouldPrune(any(), any(), any()) } returns false
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returnsMany listOf(
            policyBookmarks,      // initial prune (no packages → entry exits)
            policyBookmarks,      // loop iteration 1
            policyBookmarksAfter, // post-batch prune entry check
            policyBookmarksAfter  // loop iteration 2
        )
        coEvery { policyEvaluator.selectEligibleBookmarks(eq(policyBookmarks), any()) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(eq(policyBookmarksAfter), any()) } returns policyBookmarksAfter
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returnsMany listOf(
            true, true,   // iteration 1: both need packages
            false, false  // iteration 2: both already have packages
        )
        coEvery { loadContentPackageUseCase.executeBatch(any()) } answers {
            (firstArg() as List<*>).associate { it as String to LoadContentPackageUseCase.Result.Success }
        }

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { loadContentPackageUseCase.executeBatch(any()) }
        coVerify { settingsDataStore.saveLastContentSyncTimestamp(any()) }
    }

    @Test
    fun `doWork skips bookmarks that already have offline packages`() = runTest {
        val policyBookmarks = listOf(
            policyBookmark("1", "2026-03-01T00:00:00Z", hasOfflinePackage = true),
            policyBookmark("2", "2026-03-15T00:00:00Z", hasOfflinePackage = false)
        )
        val policyBookmarksAfter = listOf(
            policyBookmark("1", "2026-03-01T00:00:00Z", hasOfflinePackage = true),
            policyBookmark("2", "2026-03-15T00:00:00Z", hasOfflinePackage = true)
        )

        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 50_000_000L
        coEvery { policyEvaluator.shouldStopDownloading(any(), any()) } returns false
        coEvery { policyEvaluator.downloadHeadroomBytes(any(), any()) } returns 46_000_000L
        coEvery { policyEvaluator.shouldPrune(any(), any(), any()) } returns false
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returnsMany listOf(
            policyBookmarks,      // initial prune (bk "1" has package → entry check)
            policyBookmarks,      // loop iteration 1
            policyBookmarksAfter, // post-batch prune entry check
            policyBookmarksAfter  // loop iteration 2
        )
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returnsMany listOf(
            policyBookmarks, policyBookmarksAfter
        )
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarks[0]) } returns false
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarks[1]) } returns true
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarksAfter[0]) } returns false
        coEvery { policyEvaluator.needsOfflinePackage(policyBookmarksAfter[1]) } returns false
        coEvery { loadContentPackageUseCase.executeBatch(listOf("2")) } returns mapOf(
            "2" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { loadContentPackageUseCase.executeBatch(listOf("2")) }
    }

    // --- Constraint check between batches ---

    @Test
    fun `doWork stops when constraints become unsatisfied between batches`() = runTest {
        val policyBookmarks = (1..15).map {
            policyBookmark("$it", "2026-03-${it.toString().padStart(2, '0')}T00:00:00Z", hasOfflinePackage = false)
        }

        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 0L
        coEvery { policyEvaluator.shouldStopDownloading(any(), any()) } returns false
        coEvery { policyEvaluator.downloadHeadroomBytes(any(), any()) } returns Long.MAX_VALUE
        coEvery { policyEvaluator.shouldPrune(any(), any(), any()) } returns false
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returns true

        // Allow first batch, block second
        coEvery { policyEvaluator.canFetchContent() } returnsMany listOf(
            OfflinePolicyEvaluator.Decision(true),
            OfflinePolicyEvaluator.Decision(false, "Wi-Fi required")
        )

        coEvery { loadContentPackageUseCase.executeBatch(any()) } answers {
            (firstArg() as List<*>).associate { it as String to LoadContentPackageUseCase.Result.Success }
        }

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

    // --- Download threshold check ---

    @Test
    fun `doWork stops downloading when download threshold reached`() = runTest {
        // Initial prune: no packages → entry exits
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns emptyList()
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 96_000_000L
        coEvery { policyEvaluator.shouldStopDownloading(96_000_000L, 0) } returns true

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { loadContentPackageUseCase.executeBatch(any()) }
        coVerify { settingsDataStore.saveLastContentSyncTimestamp(any()) }
    }

    // --- Prune cycle ---

    @Test
    fun `doWork prunes content before and after downloading`() = runTest {
        val downloadedWithPackage = listOf(
            policyBookmark("old-1", "2026-01-01T00:00:00Z", hasOfflinePackage = true)
        )
        val newBookmark = policyBookmark("new-1", "2026-03-15T00:00:00Z", hasOfflinePackage = false)
        val newBookmarkAfter = policyBookmark("new-1", "2026-03-15T00:00:00Z", hasOfflinePackage = true)

        // Prune entry check sees old-1 has package → shouldPrune=true → prune loop
        // Loop iter 1: still sees old-1 (not deleted yet) → isPrunedEnough=false → delete
        // Loop iter 2: old-1 gone → empty downloaded list → exits
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returnsMany listOf(
            downloadedWithPackage + newBookmark, // initial prune entry: old-1 has package
            downloadedWithPackage + newBookmark, // prune loop iter 1: before deletion
            listOf(newBookmark),                  // prune loop iter 2: after deletion (no packages → exits)
            listOf(newBookmark),                  // download loop iteration 1
            listOf(newBookmarkAfter),             // post-batch prune entry check
            listOf(newBookmarkAfter)              // download loop iteration 2
        )
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returnsMany listOf(
            110_000_000L, // initial prune entry: shouldPrune check
            110_000_000L, // prune loop iter 1: isPrunedEnough check (before deletion)
            40_000_000L,  // download loop iteration 1: shouldStopDownloading
            42_000_000L,  // post-batch prune entry: shouldPrune check
            42_000_000L   // download loop iteration 2: shouldStopDownloading
        )
        // Initial prune: entry triggers
        coEvery { policyEvaluator.shouldPrune(any(), eq(110_000_000L), any()) } returns true
        // Prune loop iter 1: not yet pruned enough → will delete old-1
        coEvery { policyEvaluator.isPrunedEnough(any(), eq(110_000_000L), any()) } returns false
        coEvery { policyEvaluator.selectForPruning(any(), eq(110_000_000L), any()) } returns listOf("old-1")
        coEvery { contentPackageManager.deleteContentForBookmark("old-1") } returns Unit
        // Post-batch prune: not triggered
        coEvery { policyEvaluator.shouldPrune(any(), eq(42_000_000L), any()) } returns false

        coEvery { policyEvaluator.shouldStopDownloading(any(), any()) } returns false
        coEvery { policyEvaluator.downloadHeadroomBytes(any(), any()) } returns 56_000_000L

        coEvery { policyEvaluator.selectEligibleBookmarks(eq(listOf(newBookmark)), any()) } returns listOf(newBookmark)
        coEvery { policyEvaluator.selectEligibleBookmarks(eq(listOf(newBookmarkAfter)), any()) } returns listOf(newBookmarkAfter)
        coEvery { policyEvaluator.needsOfflinePackage(newBookmark) } returns true
        coEvery { policyEvaluator.needsOfflinePackage(newBookmarkAfter) } returns false
        coEvery { loadContentPackageUseCase.executeBatch(listOf("new-1")) } returns mapOf(
            "new-1" to LoadContentPackageUseCase.Result.Success
        )

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { contentPackageManager.deleteContentForBookmark("old-1") }
        coVerify { loadContentPackageUseCase.executeBatch(listOf("new-1")) }
    }

    // --- Stalled-progress regression test ---

    @Test
    fun `doWork stops when batch produces no storage change`() = runTest {
        // Simulate: a 0-byte article keeps re-appearing in the pending list.
        // After processing it, usage doesn't change → stalled-progress guard fires.
        val policyBookmarks = listOf(
            policyBookmark("zero-bytes", "2026-03-01T00:00:00Z", hasOfflinePackage = false)
        )

        // Usage stays constant at 90MB across all iterations
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 90_000_000L
        coEvery { policyEvaluator.shouldStopDownloading(90_000_000L, any()) } returns false
        coEvery { policyEvaluator.downloadHeadroomBytes(90_000_000L, any()) } returns 2_800_000L
        coEvery { policyEvaluator.shouldPrune(any(), any(), any()) } returns false
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returns true
        coEvery { loadContentPackageUseCase.executeBatch(any()) } answers {
            (firstArg() as List<*>).associate { it as String to LoadContentPackageUseCase.Result.Success }
        }

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // First batch executes, second iteration detects stall and breaks
        coVerify(exactly = 1) { loadContentPackageUseCase.executeBatch(any()) }
    }

    // --- Thrashing regression test ---

    @Test
    fun `doWork does not thrash when download threshold reached after first batch`() = runTest {
        // Simulate: 50 bookmarks eligible, threshold reached after first batch
        val policyBookmarks = (1..50).map {
            policyBookmark("$it", "2026-03-${(it % 28 + 1).toString().padStart(2, '0')}T00:00:00Z",
                hasOfflinePackage = false)
        }

        coEvery { contentPackageManager.calculateManagedOfflineSize() } returnsMany listOf(
            60_000_000L,   // loop iteration 1: below threshold
            92_000_000L    // loop iteration 2: at threshold, stop
        )
        coEvery { policyEvaluator.shouldStopDownloading(60_000_000L, 0) } returns false
        coEvery { policyEvaluator.shouldStopDownloading(92_000_000L, 0) } returns true
        coEvery { policyEvaluator.downloadHeadroomBytes(60_000_000L, 0) } returns 32_000_000L
        coEvery { policyEvaluator.shouldPrune(any(), any(), any()) } returns false

        coEvery { bookmarkDao.getOfflinePolicyBookmarks(false) } returns policyBookmarks
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns policyBookmarks
        coEvery { policyEvaluator.needsOfflinePackage(any()) } returns true

        coEvery { loadContentPackageUseCase.executeBatch(any()) } answers {
            (firstArg() as List<*>).associate { it as String to LoadContentPackageUseCase.Result.Success }
        }

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Only ONE batch — the loop must stop at download threshold
        coVerify(exactly = 1) { loadContentPackageUseCase.executeBatch(any()) }
    }

    // --- Error handling ---

    @Test
    fun `doWork retries on unexpected exception`() = runTest {
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
        coEvery { bookmarkDao.getOfflinePolicyBookmarks(true) } returns emptyList()
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 0L
        coEvery { policyEvaluator.shouldStopDownloading(0L, 0) } returns false
        coEvery { policyEvaluator.selectEligibleBookmarks(any(), any()) } returns emptyList()

        val result = createWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { bookmarkDao.getOfflinePolicyBookmarks(true) }
    }

    // --- Adaptive batch size ---

    @Test
    fun `adaptiveBatchSize fits as many as headroom allows`() {
        // 100MB headroom, 2MB articles → 50, capped at MAX_BATCH_SIZE=10
        assertEquals(10, BatchArticleLoadWorker.adaptiveBatchSize(100_000_000L, 2_000_000L, 50))
    }

    @Test
    fun `adaptiveBatchSize returns fewer when headroom is tight`() {
        // 5MB headroom, 2MB articles → 2
        assertEquals(2, BatchArticleLoadWorker.adaptiveBatchSize(5_000_000L, 2_000_000L, 50))
    }

    @Test
    fun `adaptiveBatchSize returns 1 when headroom barely fits one`() {
        // 2.5MB headroom, 2MB articles → 1
        assertEquals(1, BatchArticleLoadWorker.adaptiveBatchSize(2_500_000L, 2_000_000L, 50))
    }

    @Test
    fun `adaptiveBatchSize capped by pending count`() {
        assertEquals(3, BatchArticleLoadWorker.adaptiveBatchSize(100_000_000L, 2_000_000L, 3))
    }

    @Test
    fun `adaptiveBatchSize returns 1 for zero estimated size`() {
        assertEquals(1, BatchArticleLoadWorker.adaptiveBatchSize(100_000_000L, 0L, 50))
    }

    // --- Helpers ---

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
