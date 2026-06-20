package com.mydeck.app.ui.settings

import android.content.Context
import androidx.work.WorkManager
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflineImageStorageLimit
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyDefaults
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.LoadBookmarksUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.BatchArticleLoadWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var fullSyncUseCase: FullSyncUseCase
    private lateinit var loadBookmarksUseCase: LoadBookmarksUseCase
    private lateinit var contentPackageManager: ContentPackageManager
    private lateinit var contentSyncPolicyEvaluator: OfflinePolicyEvaluator
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private lateinit var viewModel: SyncSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        bookmarkDao = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        fullSyncUseCase = mockk(relaxed = true)
        loadBookmarksUseCase = mockk(relaxed = true)
        contentPackageManager = mockk(relaxed = true)
        contentSyncPolicyEvaluator = mockk(relaxed = true)
        connectivityMonitor = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { bookmarkDao.observeDetailedSyncStatus() } returns
            MutableStateFlow(BookmarkDao.DetailedSyncStatusCounts(0, 0, 0, 0, 0, 0, 0))
        every { fullSyncUseCase.workInfoFlow } returns flowOf(emptyList())
        every { fullSyncUseCase.syncIsRunning } returns flowOf(false)
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        every { settingsDataStore.lastSyncTimestampFlow } returns MutableStateFlow(null)
        every { settingsDataStore.lastContentSyncTimestampFlow } returns MutableStateFlow(null)
        every { connectivityMonitor.observeConnectivity() } returns flowOf(true)
        every { connectivityMonitor.isNetworkAvailable() } returns true

        coEvery { settingsDataStore.getAutoSyncTimeframe() } returns AutoSyncTimeframe.HOURS_01
        coEvery { settingsDataStore.isAutoSyncEnabled() } returns true
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT
        coEvery { settingsDataStore.getOfflinePolicyStorageLimit() } returns
            OfflineImageStorageLimit.MB_100.bytes
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns OfflinePolicyDefaults.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns
            OfflinePolicyDefaults.DATE_RANGE_WINDOW
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns
            OfflineImageStorageLimit.UNLIMITED.bytes
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST
        coEvery { settingsDataStore.getContentSyncConstraints() } returns
            ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = true)
        coEvery { contentPackageManager.calculateManagedOfflineSize() } returns 0L
        coEvery { bookmarkDao.getOldestDownloadedBookmarkEpoch(any()) } returns null
        coEvery { contentPackageManager.deleteAllContent() } returns Unit

        viewModel = SyncSettingsViewModel(
            bookmarkDao = bookmarkDao,
            settingsDataStore = settingsDataStore,
            fullSyncUseCase = fullSyncUseCase,
            loadBookmarksUseCase = loadBookmarksUseCase,
            contentPackageManager = contentPackageManager,
            contentSyncPolicyEvaluator = contentSyncPolicyEvaluator,
            connectivityMonitor = connectivityMonitor,
            workManager = workManager,
            context = context,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onConfirmClearOfflineContent performs a full content clear, not the managed-only purge`() = runTest {
        viewModel.onConfirmClearOfflineContent()
        advanceUntilIdle()

        coVerify(exactly = 1) { contentPackageManager.deleteAllContent() }
        coVerify(exactly = 0) { bookmarkDao.getBookmarkIdsWithOfflinePackages() }
        coVerify(exactly = 0) { contentPackageManager.deleteContentForBookmark(any()) }
        verify { workManager.cancelUniqueWork(BatchArticleLoadWorker.UNIQUE_WORK_NAME) }
        verify { workManager.cancelAllWorkByTag(BatchArticleLoadWorker.WORK_TAG_OFFLINE_CONTENT) }
    }
}
