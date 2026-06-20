package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkAnnotationSyncReason
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.SyncPriority
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class LoadBookmarksWorkerTest {

    @Test
    fun `resolveSyncSince prefers positive last sync timestamp`() {
        val lastSyncTimestamp = Instant.parse("2026-03-12T20:38:13Z")
        val lastBookmarkTimestamp = Instant.parse("2026-03-12T20:00:00Z")

        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = lastSyncTimestamp,
            lastBookmarkTimestamp = lastBookmarkTimestamp
        )

        assertEquals(lastSyncTimestamp, resolved)
    }

    @Test
    fun `resolveSyncSince falls back to bookmark timestamp when last sync is zero`() {
        val lastBookmarkTimestamp = Instant.parse("2026-03-12T20:38:13Z")

        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = Instant.fromEpochSeconds(0),
            lastBookmarkTimestamp = lastBookmarkTimestamp
        )

        assertEquals(lastBookmarkTimestamp, resolved)
    }

    @Test
    fun `resolveSyncSince returns null when both cursors are unset`() {
        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = null,
            lastBookmarkTimestamp = Instant.fromEpochSeconds(0)
        )

        assertNull(resolved)
    }

    @Test
    fun `doWork does not persist delta cursor when metadata reload fails`() = runTest {
        val pendingCursor = Instant.parse("2026-03-12T20:38:13Z")
        val inputData = Data.Builder()
            .putBoolean(LoadBookmarksWorker.PARAM_IS_INITIAL_LOAD, false)
            .putString(LoadBookmarksWorker.PARAM_TRIGGER, LoadBookmarksWorker.Trigger.PULL_TO_REFRESH.name)
            .build()

        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val highlightsRepository = mockk<HighlightsRepository>()

        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 1,
            updatedIds = listOf("bk-1"),
            maxServerTime = pendingCursor
        )
        coEvery {
            loadBookmarksUseCase.execute(updatedIds = any(), enqueueContentSyncAfterLoad = false)
        } returns LoadBookmarksUseCase.UseCaseResult.Error(RuntimeException("metadata load failed"))

        val worker = LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator()
        )

        val result = worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.failure()::class)
        coVerify(exactly = 0) { settingsDataStore.saveLastSyncTimestamp(any()) }
        coVerify(exactly = 0) {
            highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any())
        }
    }

    @Test
    fun `doWork enqueues bookmark annotation checks after delta metadata reload succeeds`() = runTest {
        val pendingCursor = Instant.parse("2026-03-12T20:38:13Z")
        val inputData = Data.Builder()
            .putBoolean(LoadBookmarksWorker.PARAM_IS_INITIAL_LOAD, false)
            .putString(LoadBookmarksWorker.PARAM_TRIGGER, LoadBookmarksWorker.Trigger.APP_OPEN.name)
            .build()

        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val highlightsRepository = mockk<HighlightsRepository>()
        val updatedIds = listOf("bk-1", "bk-2")

        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = updatedIds.size,
            updatedIds = updatedIds,
            maxServerTime = pendingCursor
        )
        coEvery {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        } returns LoadBookmarksUseCase.UseCaseResult.Success(Unit)
        coEvery { loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)
        coEvery {
            highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        } returns Result.success(Unit)

        val worker = LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator()
        )

        val result = worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerifyOrder {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
            highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        }
        coVerify(exactly = 1) {
            highlightsRepository.requestRefresh(HighlightsRefreshReason.APP_OPEN)
        }
    }

    @Test
    fun `doWork with empty delta updatedIds enqueues no bookmark annotation checks`() = runTest {
        val inputData = Data.Builder()
            .putBoolean(LoadBookmarksWorker.PARAM_IS_INITIAL_LOAD, false)
            .putString(LoadBookmarksWorker.PARAM_TRIGGER, LoadBookmarksWorker.Trigger.PULL_TO_REFRESH.name)
            .build()

        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val highlightsRepository = mockk<HighlightsRepository>()

        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 0,
            updatedIds = emptyList(),
            maxServerTime = Instant.parse("2026-03-12T20:38:13Z")
        )
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true
        coEvery { loadBookmarksUseCase.enqueueContentSyncIfNeeded(userInitiated = true) } returns Unit
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)

        val worker = LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator()
        )

        val result = worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 0) { loadBookmarksUseCase.execute(updatedIds = any()) }
        coVerify(exactly = 0) {
            highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any())
        }
        coVerify(exactly = 1) {
            highlightsRepository.requestRefresh(HighlightsRefreshReason.MANUAL_SYNC)
        }
        // Bug 4.6 (W5): PULL_TO_REFRESH is user-initiated → must REPLACE, not KEEP.
        coVerify(exactly = 1) {
            loadBookmarksUseCase.enqueueContentSyncIfNeeded(userInitiated = true)
        }
    }

    @Test
    fun `doWork passes duplicate updatedIds through bookmark annotation dedupe path`() = runTest {
        val pendingCursor = Instant.parse("2026-03-12T20:38:13Z")
        val inputData = Data.Builder()
            .putBoolean(LoadBookmarksWorker.PARAM_IS_INITIAL_LOAD, false)
            .putString(LoadBookmarksWorker.PARAM_TRIGGER, LoadBookmarksWorker.Trigger.PULL_TO_REFRESH.name)
            .build()

        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val highlightsRepository = mockk<HighlightsRepository>()
        val updatedIds = listOf("bk-1", "bk-1", "bk-2")

        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = updatedIds.size,
            updatedIds = updatedIds,
            maxServerTime = pendingCursor
        )
        coEvery {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        } returns LoadBookmarksUseCase.UseCaseResult.Success(Unit)
        coEvery { loadBookmarksUseCase.enqueueContentSyncIfNeeded(userInitiated = true) } returns Unit
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)
        coEvery {
            highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        } returns Result.success(Unit)

        val worker = LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator()
        )

        val result = worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.success()::class)
        coVerify(exactly = 1) {
            highlightsRepository.requestBookmarkAnnotationChecks(
                bookmarkIds = updatedIds,
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
                priority = SyncPriority.Normal
            )
        }
    }
}
