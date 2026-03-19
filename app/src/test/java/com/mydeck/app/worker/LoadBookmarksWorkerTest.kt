package com.mydeck.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
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

        coEvery { settingsDataStore.getLastSyncTimestamp() } returns Instant.parse("2026-03-10T08:00:00Z")
        coEvery { settingsDataStore.getLastBookmarkTimestamp() } returns null
        coEvery { bookmarkRepository.performDeltaSync(any()) } returns BookmarkRepository.SyncResult.Success(
            countDeleted = 0,
            countUpdated = 1,
            maxServerTime = pendingCursor
        )
        coEvery { loadBookmarksUseCase.execute() } returns LoadBookmarksUseCase.UseCaseResult.Error(
            RuntimeException("metadata load failed")
        )

        val worker = LoadBookmarksWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = workerParams,
            loadBookmarksUseCase = loadBookmarksUseCase,
            bookmarkRepository = bookmarkRepository,
            settingsDataStore = settingsDataStore
        )

        val result = worker.doWork()

        assertTrue(result::class == ListenableWorker.Result.failure()::class)
        coVerify(exactly = 0) { settingsDataStore.saveLastSyncTimestamp(any()) }
    }
}
