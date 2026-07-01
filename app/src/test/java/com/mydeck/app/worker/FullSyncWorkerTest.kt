package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkAnnotationSyncReason
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.CollectionRepository
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.SyncPriority
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.usecase.FreshnessMarkerUseCase
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertTrue
import org.junit.Test

class FullSyncWorkerTest {

    @Test
    fun `doWork enqueues bookmark annotation checks after delta metadata reload succeeds`() = runTest {
        val updatedIds = listOf("bk-1", "bk-2")
        val fixture = fixture()

        coEvery { fixture.bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = updatedIds.size,
            updatedIds = updatedIds,
            maxServerTime = Clock.System.now()
        )
        coEvery {
            fixture.loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        } returns LoadBookmarksUseCase.UseCaseResult.Success(Unit)
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit
        coEvery { fixture.freshnessMarkerUseCase.markDirtyForBookmarks(updatedIds) } returns Unit
        coEvery {
            fixture.highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        } returns Result.success(Unit)

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerifyOrder {
            fixture.loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
            fixture.highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        }
    }

    @Test
    fun `doWork refreshes collections after a successful sync`() = runTest {
        val fixture = fixture()

        coEvery { fixture.bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 0,
            updatedIds = emptyList(),
            maxServerTime = Clock.System.now()
        )
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 1) { fixture.collectionRepository.refreshCollections() }
    }

    @Test
    fun `doWork skips bookmark annotation checks when delta metadata reload fails`() = runTest {
        val updatedIds = listOf("bk-1")
        val fixture = fixture()

        coEvery { fixture.bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = updatedIds.size,
            updatedIds = updatedIds,
            maxServerTime = Clock.System.now()
        )
        coEvery {
            fixture.loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        } returns LoadBookmarksUseCase.UseCaseResult.Error(RuntimeException("metadata reload failed"))

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.failure()::class)
        coVerify(exactly = 0) {
            fixture.highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any())
        }
    }

    @Test
    fun `doWork with deleted-only delta enqueues no bookmark annotation checks`() = runTest {
        val fixture = fixture()

        coEvery { fixture.bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 2,
            countUpdated = 0,
            updatedIds = emptyList(),
            maxServerTime = Clock.System.now()
        )
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 0) { fixture.loadBookmarksUseCase.execute(updatedIds = any()) }
        coVerify(exactly = 0) {
            fixture.highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any())
        }
        coVerify(exactly = 1) {
            fixture.highlightsRepository.requestRefresh(HighlightsRefreshReason.PERIODIC_BACKSTOP)
        }
    }

    @Test
    fun `doWork with full bookmark sync enqueues no per-bookmark annotation checks`() = runTest {
        val fixture = fixture(forceFullSync = true)

        coEvery { fixture.bookmarkRepository.performFullSync() } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 3,
            updatedIds = listOf("bk-1", "bk-2", "bk-3"),
            maxServerTime = Clock.System.now()
        )
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 0) {
            fixture.highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any())
        }
        coVerify(exactly = 1) {
            fixture.highlightsRepository.requestRefresh(HighlightsRefreshReason.PERIODIC_BACKSTOP)
        }
    }

    @Test
    fun `manual full sync requests normal global backstop without forcing user retry`() = runTest {
        val fixture = fixture(isManualSync = true, forceFullSync = true)

        coEvery { fixture.bookmarkRepository.performFullSync() } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 3,
            updatedIds = listOf("bk-1", "bk-2", "bk-3"),
            maxServerTime = Clock.System.now()
        )
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded(userInitiated = true) } returns Unit

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 1) {
            fixture.highlightsRepository.requestRefresh(HighlightsRefreshReason.MANUAL_SYNC)
        }
        coVerify(exactly = 0) {
            fixture.highlightsRepository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
        }
        // Bug 4.6 (W5): manual/user-initiated sync must thread userInitiated=true so the
        // content-sync scheduler uses REPLACE instead of silently KEEP-dropping the trigger.
        coVerify(exactly = 1) {
            fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded(userInitiated = true)
        }
    }

    @Test
    fun `orphan repair full sync forces full sync and requests orphan repair highlights refresh`() = runTest {
        val fixture = fixture(isOrphanRepair = true)

        coEvery { fixture.bookmarkRepository.performFullSync() } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 3,
            maxServerTime = Clock.System.now()
        )
        coEvery { fixture.loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit

        val result = fixture.worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 1) { fixture.bookmarkRepository.performFullSync() }
        coVerify(exactly = 0) { fixture.bookmarkRepository.performDeltaSync(any()) }
        coVerify(exactly = 1) {
            fixture.highlightsRepository.requestRefresh(HighlightsRefreshReason.ORPHAN_REPAIR)
        }
    }

    private fun fixture(
        forceFullSync: Boolean = false,
        isManualSync: Boolean = false,
        isOrphanRepair: Boolean = false,
    ): Fixture {
        val inputData = Data.Builder()
            .putBoolean(FullSyncWorker.INPUT_FORCE_FULL_SYNC, forceFullSync)
            .putBoolean(FullSyncWorker.INPUT_IS_MANUAL_SYNC, isManualSync)
            .putBoolean(FullSyncWorker.INPUT_IS_ORPHAN_REPAIR, isOrphanRepair)
            .build()
        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val freshnessMarkerUseCase = mockk<FreshnessMarkerUseCase>()
        val highlightsRepository = mockk<HighlightsRepository>()
        val collectionRepository = mockk<CollectionRepository>()

        coEvery { bookmarkRepository.syncPendingActions() } returns BookmarkRepository.UpdateResult.Success
        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Clock.System.now()
        coEvery { settingsDataStore.getLastFullSyncTimestamp() } returns Clock.System.now()
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.saveLastFullSyncTimestamp(any()) } returns Unit
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)
        coEvery { collectionRepository.refreshCollections() } returns Result.success(Unit)

        val worker = FullSyncWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            freshnessMarkerUseCase = freshnessMarkerUseCase,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator(),
            collectionRepository = collectionRepository
        )
        return Fixture(
            worker = worker,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            freshnessMarkerUseCase = freshnessMarkerUseCase,
            highlightsRepository = highlightsRepository,
            collectionRepository = collectionRepository,
        )
    }

    private data class Fixture(
        val worker: FullSyncWorker,
        val bookmarkRepository: BookmarkRepository,
        val settingsDataStore: SettingsDataStore,
        val loadBookmarksUseCase: LoadBookmarksUseCase,
        val freshnessMarkerUseCase: FreshnessMarkerUseCase,
        val highlightsRepository: HighlightsRepository,
        val collectionRepository: CollectionRepository,
    )
}
