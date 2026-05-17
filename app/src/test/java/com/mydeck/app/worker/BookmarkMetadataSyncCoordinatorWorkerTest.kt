package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.usecase.FreshnessMarkerUseCase
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkMetadataSyncCoordinatorWorkerTest {
    @Test
    fun `initial load and manual full sync wait instead of running full sync concurrently`() = runTest {
        val coordinator = BookmarkMetadataSyncCoordinator()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val highlightsRepository = mockk<HighlightsRepository>()
        val freshnessMarkerUseCase = mockk<FreshnessMarkerUseCase>()
        val firstFullSyncEntered = CompletableDeferred<Unit>()
        val releaseFirstFullSync = CompletableDeferred<Unit>()
        var activeFullSyncs = 0
        var maxActiveFullSyncs = 0
        var fullSyncCalls = 0

        coEvery { bookmarkRepository.syncPendingActions() } returns BookmarkRepository.UpdateResult.Success
        coEvery { bookmarkRepository.performFullSync() } coAnswers {
            fullSyncCalls += 1
            activeFullSyncs += 1
            maxActiveFullSyncs = maxOf(maxActiveFullSyncs, activeFullSyncs)
            if (fullSyncCalls == 1) {
                firstFullSyncEntered.complete(Unit)
                releaseFirstFullSync.await()
            }
            activeFullSyncs -= 1
            BookmarkRepository.SyncResult.Success(countDeleted = 0, countUpdated = 1)
        }
        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Clock.System.now()
        coEvery { settingsDataStore.getLastFullSyncTimestamp() } returns Clock.System.now()
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.saveLastFullSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.setInitialSyncPerformed(any()) } returns Unit
        coEvery { loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)

        val initialWorker = loadWorker(
            coordinator = coordinator,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            highlightsRepository = highlightsRepository,
            trigger = LoadBookmarksWorker.Trigger.INITIAL,
            isInitialLoad = true
        )
        val manualWorker = fullWorker(
            coordinator = coordinator,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            freshnessMarkerUseCase = freshnessMarkerUseCase,
            highlightsRepository = highlightsRepository,
            forceFullSync = true,
            isManualSync = true
        )

        val initialResult = async { initialWorker.doWork() }
        firstFullSyncEntered.await()
        val manualResult = async { manualWorker.doWork() }
        runCurrent()

        coVerify(exactly = 1) { bookmarkRepository.performFullSync() }

        releaseFirstFullSync.complete(Unit)

        assertTrue(initialResult.await()::class == ListenableWorker.Result.success()::class)
        assertTrue(manualResult.await()::class == ListenableWorker.Result.success()::class)
        assertEquals(1, maxActiveFullSyncs)
        coVerify(exactly = 2) { bookmarkRepository.performFullSync() }
    }

    @Test
    fun `app open and periodic delta metadata reloads wait instead of running concurrently`() = runTest {
        val coordinator = BookmarkMetadataSyncCoordinator()
        val bookmarkRepository = mockk<BookmarkRepository>()
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val loadBookmarksUseCase = mockk<LoadBookmarksUseCase>()
        val highlightsRepository = mockk<HighlightsRepository>()
        val freshnessMarkerUseCase = mockk<FreshnessMarkerUseCase>()
        val updatedIds = listOf("bk-1")
        val firstReloadEntered = CompletableDeferred<Unit>()
        val releaseFirstReload = CompletableDeferred<Unit>()
        var activeReloads = 0
        var maxActiveReloads = 0
        var reloadCalls = 0

        coEvery { bookmarkRepository.syncPendingActions() } returns BookmarkRepository.UpdateResult.Success
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = updatedIds.size,
            updatedIds = updatedIds,
            maxServerTime = Instant.parse("2026-03-12T20:38:13Z")
        )
        coEvery {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        } coAnswers {
            reloadCalls += 1
            activeReloads += 1
            maxActiveReloads = maxOf(maxActiveReloads, activeReloads)
            if (reloadCalls == 1) {
                firstReloadEntered.complete(Unit)
                releaseFirstReload.await()
            }
            activeReloads -= 1
            LoadBookmarksUseCase.UseCaseResult.Success(Unit)
        }
        coEvery { loadBookmarksUseCase.enqueueContentSyncIfNeeded() } returns Unit
        coEvery { freshnessMarkerUseCase.markDirtyForBookmarks(any()) } returns Unit
        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { settingsDataStore.getLastFullSyncTimestamp() } returns Clock.System.now()
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true
        coEvery { highlightsRepository.requestBookmarkAnnotationChecks(any(), any(), any()) } returns Result.success(Unit)
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)

        val appOpenWorker = loadWorker(
            coordinator = coordinator,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            highlightsRepository = highlightsRepository,
            trigger = LoadBookmarksWorker.Trigger.APP_OPEN,
            isInitialLoad = false
        )
        val periodicWorker = fullWorker(
            coordinator = coordinator,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            freshnessMarkerUseCase = freshnessMarkerUseCase,
            highlightsRepository = highlightsRepository,
            forceFullSync = false,
            isManualSync = false
        )

        val appOpenResult = async { appOpenWorker.doWork() }
        firstReloadEntered.await()
        val periodicResult = async { periodicWorker.doWork() }
        runCurrent()

        coVerify(exactly = 1) {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        }

        releaseFirstReload.complete(Unit)

        assertTrue(appOpenResult.await()::class == ListenableWorker.Result.success()::class)
        assertTrue(periodicResult.await()::class == ListenableWorker.Result.success()::class)
        assertEquals(1, maxActiveReloads)
        coVerify(exactly = 2) {
            loadBookmarksUseCase.execute(updatedIds = updatedIds, enqueueContentSyncAfterLoad = false)
        }
    }

    private fun loadWorker(
        coordinator: BookmarkMetadataSyncCoordinator,
        bookmarkRepository: BookmarkRepository,
        settingsDataStore: SettingsDataStore,
        loadBookmarksUseCase: LoadBookmarksUseCase,
        highlightsRepository: HighlightsRepository,
        trigger: LoadBookmarksWorker.Trigger,
        isInitialLoad: Boolean
    ): LoadBookmarksWorker {
        val inputData = Data.Builder()
            .putBoolean(LoadBookmarksWorker.PARAM_IS_INITIAL_LOAD, isInitialLoad)
            .putString(LoadBookmarksWorker.PARAM_TRIGGER, trigger.name)
            .build()
        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        return LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = coordinator
        )
    }

    private fun fullWorker(
        coordinator: BookmarkMetadataSyncCoordinator,
        bookmarkRepository: BookmarkRepository,
        settingsDataStore: SettingsDataStore,
        loadBookmarksUseCase: LoadBookmarksUseCase,
        freshnessMarkerUseCase: FreshnessMarkerUseCase,
        highlightsRepository: HighlightsRepository,
        forceFullSync: Boolean,
        isManualSync: Boolean
    ): FullSyncWorker {
        val inputData = Data.Builder()
            .putBoolean(FullSyncWorker.INPUT_FORCE_FULL_SYNC, forceFullSync)
            .putBoolean(FullSyncWorker.INPUT_IS_MANUAL_SYNC, isManualSync)
            .build()
        val workerParams = mockk<WorkerParameters> {
            every { this@mockk.inputData } returns inputData
        }
        return FullSyncWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore,
            loadBookmarksUseCase = loadBookmarksUseCase,
            freshnessMarkerUseCase = freshnessMarkerUseCase,
            highlightsRepository = highlightsRepository,
            bookmarkMetadataSyncCoordinator = coordinator
        )
    }
}
