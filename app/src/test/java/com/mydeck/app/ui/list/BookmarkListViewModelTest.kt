package com.mydeck.app.ui.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkBatchUpdate
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.sync.SyncScheduler
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.domain.model.SwipeConfig
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.CreateBookmarkWorker
import com.mydeck.app.worker.LoadBookmarksWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var context: Context
    private lateinit var viewModel: BookmarkListViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var updateBookmarkUseCase: UpdateBookmarkUseCase
    private lateinit var fullSyncUseCase: FullSyncUseCase
    private lateinit var workManager : WorkManager
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var contentSyncPolicyEvaluator: OfflinePolicyEvaluator
    private lateinit var contentPackageManager: ContentPackageManager
    private lateinit var highlightsRepository: HighlightsRepository
    private lateinit var syncScheduler: SyncScheduler

    private lateinit var workInfoFlow: MutableStateFlow<List<WorkInfo>>
    private lateinit var initialSyncPerformedFlow: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk()
        settingsDataStore = mockk()
        context = mockk()
        savedStateHandle = mockk()
        updateBookmarkUseCase = mockk()
        fullSyncUseCase = mockk()
        workManager = mockk()
        connectivityMonitor = mockk()
        contentSyncPolicyEvaluator = mockk()
        contentPackageManager = mockk()
        highlightsRepository = mockk()
        syncScheduler = mockk(relaxed = true)
        coEvery { contentSyncPolicyEvaluator.shouldAutoFetchContent() } returns false
        coEvery { highlightsRepository.requestRefresh(any()) } returns Result.success(Unit)

        workInfoFlow = MutableStateFlow(emptyList())
        initialSyncPerformedFlow = MutableStateFlow(false)
        mockkObject(LoadBookmarksWorker.Companion)
        every { LoadBookmarksWorker.enqueue(any(), any<Boolean>()) } returns UUID.randomUUID()
        every { LoadBookmarksWorker.enqueue(any(), any<LoadBookmarksWorker.Trigger>()) } returns UUID.randomUUID()
        mockkObject(CreateBookmarkWorker.Companion)
        every { CreateBookmarkWorker.enqueue(any(), any(), any(), any(), any()) } just Runs

        every { bookmarkRepository.searchBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        // Default Mocking Behavior
        every { settingsDataStore.initialSyncPerformedFlow } returns initialSyncPerformedFlow
        coEvery { settingsDataStore.isInitialSyncPerformed() } answers { initialSyncPerformedFlow.value }
        coEvery { settingsDataStore.setInitialSyncPerformed(any()) } answers {
            initialSyncPerformedFlow.value = firstArg()
            Unit
        }
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns false // Disable sync on app open by default
        every { fullSyncUseCase.performFullSync() } returns Unit
        // Use any() for all arguments to be safe, then specialize
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        every { savedStateHandle.get<String>(any()) } returns null // no sharedUrl initially
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns workInfoFlow
        every { bookmarkRepository.observeAllBookmarkCounts() } returns flowOf(BookmarkCounts())
        every { bookmarkRepository.observeAllLabelsWithCounts() } returns flowOf(emptyMap())
        every { bookmarkRepository.observePendingActionCount() } returns flowOf(0)
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(BookmarkRepository.BookmarkSyncProgress.Idle)
        every { connectivityMonitor.observeConnectivity() } returns flowOf(true)
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false
        coEvery { settingsDataStore.getLayoutMode() } returns null
        coEvery { settingsDataStore.getSortOption() } returns null
        every { settingsDataStore.swipeConfigFlow } returns kotlinx.coroutines.flow.MutableStateFlow(SwipeConfig.Default)
        coEvery { settingsDataStore.getBookmarkShareFormat() } returns BookmarkShareFormat.URL_ONLY
        every { settingsDataStore.urlFlow } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        every { settingsDataStore.tokenFlow } returns kotlinx.coroutines.flow.MutableStateFlow(null)
    }

    private fun createViewModel() = BookmarkListViewModel(
        updateBookmarkUseCase,
        fullSyncUseCase,
        workManager,
        bookmarkRepository,
        context,
        settingsDataStore,
        savedStateHandle,
        contentSyncPolicyEvaluator,
        contentPackageManager,
        syncScheduler,
        connectivityMonitor
    )

    @After
    fun tearDown() {
        unmockkObject(CreateBookmarkWorker.Companion)
        unmockkObject(LoadBookmarksWorker.Companion)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState is Loading`() {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        // Since we emit empty list by default from mock, and filter/query are empty/default,
        // it should be Loading state.
        assertEquals(BookmarkListViewModel.UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `app open does not refresh highlights for drawer badge`() = runTest {
        every { settingsDataStore.urlFlow } returns kotlinx.coroutines.flow.MutableStateFlow("https://example.com/api")
        every { settingsDataStore.tokenFlow } returns kotlinx.coroutines.flow.MutableStateFlow("token")
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns false

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { highlightsRepository.requestRefresh(any()) }
    }

    @Test
    fun `initial sync failure shows loading error instead of empty list`() = runTest {
        val failedWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
        }
        workInfoFlow = MutableStateFlow(listOf(failedWorkInfo))
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns workInfoFlow
        initialSyncPerformedFlow.value = false

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        advanceUntilIdle()

        assertEquals(
            BookmarkListViewModel.UiState.Empty(R.string.list_view_empty_error_loading_bookmarks),
            viewModel.uiState.value
        )
    }

    @Test
    fun `loadBookmarks enqueues LoadBookmarksWorker`() = runTest {
        // TODO: Find a way to properly test WorkManager enqueuing
        //  This requires more setup with Robolectric and testing WorkManager
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        // Just verify that it doesn't throw an exception for now
        viewModel.onPullToRefresh()
    }

    @Test
    fun `sync on app open enqueues worker when enabled and url is configured`() = runTest {
        every { settingsDataStore.urlFlow } returns MutableStateFlow("https://example.com")
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns true

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        advanceUntilIdle()

        verify(exactly = 1) { LoadBookmarksWorker.enqueue(context, LoadBookmarksWorker.Trigger.APP_OPEN) }
    }

    @Test
    fun `sync on app open does not enqueue worker when disabled`() = runTest {
        every { settingsDataStore.urlFlow } returns MutableStateFlow("https://example.com")
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns false

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        advanceUntilIdle()

        verify(exactly = 0) { LoadBookmarksWorker.enqueue(any(), any<Boolean>()) }
        verify(exactly = 0) { LoadBookmarksWorker.enqueue(any(), any<LoadBookmarksWorker.Trigger>()) }
    }

    @Test
    fun `pull to refresh shows user refreshing while work is active`() = runTest {
        initialSyncPerformedFlow.value = true

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val refreshJob = launch { viewModel.isUserRefreshing.collect() }
        viewModel.onPullToRefresh()
        workInfoFlow.value = listOf(
            mockk {
                every { state } returns WorkInfo.State.RUNNING
            }
        )

        advanceUntilIdle()

        assertTrue(viewModel.isUserRefreshing.value)
        verify(exactly = 1) { LoadBookmarksWorker.enqueue(context, LoadBookmarksWorker.Trigger.PULL_TO_REFRESH) }
        refreshJob.cancel()
    }

    @Test
    fun `onClickMyList sets unread filter to true`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.onClickMyList()
        advanceUntilIdle()
        assertEquals(
            com.mydeck.app.domain.model.FilterFormState(isArchived = false),
            viewModel.filterFormState.value
        )
    }

    @Test
    fun `onClickArchive sets archived filter`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.onClickArchive()
        assertEquals(
            com.mydeck.app.domain.model.FilterFormState(isArchived = true),
            viewModel.filterFormState.first()
        )
    }

    @Test
    fun `onClickFavorite sets favorite filter`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.onClickFavorite()
        assertEquals(
            com.mydeck.app.domain.model.FilterFormState(isFavorite = true),
            viewModel.filterFormState.first()
        )
    }

    @Test
    fun `onClickSettings sets NavigateToSettings navigation event`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.onClickSettings()
        assertEquals(
            BookmarkListViewModel.NavigationEvent.NavigateToSettings,
            viewModel.navigationEvent.first()
        )
    }

    @Test
    fun `onClickBookmark sets NavigateToBookmarkDetail navigation event`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        val bookmarkId = "someBookmarkId"
        viewModel.onClickBookmark(bookmarkId)
        assertEquals(
            BookmarkListViewModel.NavigationEvent.NavigateToBookmarkDetail(bookmarkId),
            viewModel.navigationEvent.first()
        )
    }



    @Test
    fun `observeBookmarks collects bookmarks with correct filters`() = runTest {
        // Arrange
        val expectedBookmarks = listOf(
            BookmarkListItem(
                id = "1",
                href = "https://example.com/api/bookmarks/1",
                url = "https://example.com",
                title = "Test Bookmark",
                siteName = "Example Site",
                type = Bookmark.Type.Article,
                isMarked = false,
                isArchived = false,
                labels = emptyList(),
                isRead = false,
                readProgress = 0,
                thumbnailSrc = "",
                iconSrc = "",
                imageSrc = "",
                readingTime = null,
                created = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                wordCount = null,
                published = null
            )
        )
        val bookmarkFlow = MutableStateFlow(expectedBookmarks)
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                searchQuery = any(),
                title = any(),
                author = any(),
                site = any(),
                types = any(),
                progressFilters = any(),
                isArchived = any(),
                isFavorite = any(),
                label = any(),
                fromDate = any(),
                toDate = any(),
                isLoaded = any(),
                withLabels = any(),
                withErrors = any(),
                minReadingTime = any(),
                maxReadingTime = any(),
                includeNullReadingTime = any(),
                minWordCount = any(),
                maxWordCount = any(),
                includeNullWordCount = any(),
                orderBy = any()
            )
        } returns bookmarkFlow

        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        viewModel.onClickMyList()

        val uiStates = viewModel.uiState.take(2).toList()
        val empty = uiStates[0]
        val success = uiStates[1]
        // Assert initial state
        assert(empty is BookmarkListViewModel.UiState.Loading)
        // Assert success state
        assertEquals(
            BookmarkListViewModel.UiState.Success(expectedBookmarks, null),
            success
        )
    }

    @Test
    fun `openCreateBookmarkDialog sets CreateBookmarkUiState to Open`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.openCreateBookmarkDialog()
        assertTrue(viewModel.createBookmarkUiState.first() is BookmarkListViewModel.CreateBookmarkUiState.Open)
    }

    @Test
    fun `closeCreateBookmarkDialog sets CreateBookmarkUiState to Closed`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.openCreateBookmarkDialog()
        viewModel.closeCreateBookmarkDialog()
        assertTrue(viewModel.createBookmarkUiState.first() is BookmarkListViewModel.CreateBookmarkUiState.Closed)
    }

    @Test
    fun `updateCreateBookmarkTitle updates title and enables create button if URL is valid`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )
            viewModel.openCreateBookmarkDialog()

            val validUrl = "https://example.com"
            viewModel.updateCreateBookmarkUrl(validUrl)
            viewModel.updateCreateBookmarkTitle("Test Title")

            val state =
                viewModel.createBookmarkUiState.first() as BookmarkListViewModel.CreateBookmarkUiState.Open
            assertEquals("Test Title", state.title)
            assertEquals(validUrl, state.url)
            assertTrue(state.isCreateEnabled)
        }

    @Test
    fun `updateCreateBookmarkUrl updates url and enables create button if title is present`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )
            viewModel.openCreateBookmarkDialog()

            viewModel.updateCreateBookmarkTitle("Test Title")
            val validUrl = "https://example.com"
            viewModel.updateCreateBookmarkUrl(validUrl)

            val state =
                viewModel.createBookmarkUiState.first() as BookmarkListViewModel.CreateBookmarkUiState.Open
            assertEquals("Test Title", state.title)
            assertEquals(validUrl, state.url)
            assertTrue(state.isCreateEnabled)
        }

    @Test
    fun `updateCreateBookmarkUrl updates urlError if URL is invalid`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.openCreateBookmarkDialog()

        val invalidUrl = "invalid-url"
        viewModel.updateCreateBookmarkUrl(invalidUrl)

        val state =
            viewModel.createBookmarkUiState.first() as BookmarkListViewModel.CreateBookmarkUiState.Open
        assertEquals(R.string.account_settings_url_error, state.urlError)
        assertFalse(state.isCreateEnabled)
    }

    @Test
    fun `createBookmark enqueues worker and sets state to Success`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.openCreateBookmarkDialog()

        val title = "Test Title"
        val url = "https://example.com"

        viewModel.updateCreateBookmarkTitle(title)
        viewModel.updateCreateBookmarkUrl(url)
        viewModel.createBookmark()

        io.mockk.verify { CreateBookmarkWorker.enqueue(workManager, url, title, emptyList(), false) }
        assertTrue(viewModel.createBookmarkUiState.value is BookmarkListViewModel.CreateBookmarkUiState.Success)
    }

    @Test
    fun `createBookmark with archive enqueues worker with isArchived true`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )
        viewModel.openCreateBookmarkDialog()

        val title = "Test Title"
        val url = "https://example.com"

        viewModel.updateCreateBookmarkTitle(title)
        viewModel.updateCreateBookmarkUrl(url)
        viewModel.handleCreateBookmarkAction(SaveAction.ARCHIVE)

        io.mockk.verify { CreateBookmarkWorker.enqueue(workManager, url, title, emptyList(), true) }
        assertTrue(viewModel.createBookmarkUiState.value is BookmarkListViewModel.CreateBookmarkUiState.Success)
    }

    @Test
    fun `init sets CreateBookmarkUiState to Open with sharedText if present and valid`() = runTest {
        val sharedUrl = "https://example.com"
        every { savedStateHandle.get<String>("sharedText") } returns sharedUrl

        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val state =
            viewModel.createBookmarkUiState.first() as BookmarkListViewModel.CreateBookmarkUiState.Open
        assertEquals(sharedUrl, state.url)
        assertEquals(null, state.urlError)
        assertTrue(state.isCreateEnabled)
    }

    @Test
    fun `init sets CreateBookmarkUiState to Open empty result if sharedText is invalid`() =
        runTest {
            val sharedText = "invalid-url"
            every { savedStateHandle.get<String>("sharedText") } returns sharedText

            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val state =
                viewModel.createBookmarkUiState.first() as BookmarkListViewModel.CreateBookmarkUiState.Open
            assertEquals("", state.url)
            assertEquals(null, state.urlError)
            assertFalse(state.isCreateEnabled)
        }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with UpdateBookmarkState Success`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isFavorite = true

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsFavorite(
                    bookmarkId,
                    isFavorite
                )
            } returns UpdateBookmarkUseCase.Result.Success

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite)
            advanceUntilIdle()

            val updateState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Success
                ),
                updateState
            )

            coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
        }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with UpdateBookmarkState Error on GenericError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isFavorite = true
            val errorMessage = "Generic Error"

            coEvery {
                updateBookmarkUseCase.updateIsFavorite(
                    bookmarkId,
                    isFavorite
                )
            } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
        }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with UpdateBookmarkState Error on NetworkError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isFavorite = true
            val errorMessage = "Network Error"

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsFavorite(
                    bookmarkId,
                    isFavorite
                )
            } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleFavoriteBookmark(bookmarkId, isFavorite)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
        }

    @Test
    fun `onToggleArchiveBookmark updates UiState with UpdateBookmarkState Success`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isArchived = true

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsArchived(
                    bookmarkId,
                    isArchived
                )
            } returns UpdateBookmarkUseCase.Result.Success

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleArchiveBookmark(bookmarkId, isArchived)
            advanceUntilIdle()

            val updateState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Success
                ),
                updateState
            )

            coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
        }

    @Test
    fun `onToggleArchivedBookmark updates UiState with UpdateBookmarkState Error on GenericError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isArchived = true
            val errorMessage = "Generic Error"

            coEvery {
                updateBookmarkUseCase.updateIsArchived(
                    bookmarkId,
                    isArchived
                )
            } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleArchiveBookmark(bookmarkId, isArchived)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
        }

    @Test
    fun `onToggleArchivedBookmark updates UiState with UpdateBookmarkState Error on NetworkError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isArchived = true
            val errorMessage = "Network Error"

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsArchived(
                    bookmarkId,
                    isArchived
                )
            } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleArchiveBookmark(bookmarkId, isArchived)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
        }


    @Test
    fun `onToggleMarkReadBookmark updates UiState with UpdateBookmarkState Success`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isRead = true

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsRead(
                    bookmarkId,
                    isRead
                )
            } returns UpdateBookmarkUseCase.Result.Success

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleMarkReadBookmark(bookmarkId, isRead)
            advanceUntilIdle()

            val updateState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Success
                ),
                updateState
            )

            coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
        }

    @Test
    fun `onToggleMarkReadBookmark updates UiState with UpdateBookmarkState Error on GenericError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isRead = true
            val errorMessage = "Generic Error"

            coEvery {
                updateBookmarkUseCase.updateIsRead(
                    bookmarkId,
                    isRead
                )
            } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleMarkReadBookmark(bookmarkId, isRead)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
        }

    @Test
    fun `onToggleMarkReadBookmark updates UiState with UpdateBookmarkState Error on NetworkError`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isRead = true
            val errorMessage = "Network Error"

            val bookmarkFlow = MutableStateFlow(bookmarks)
            every {
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            coEvery {
                updateBookmarkUseCase.updateIsRead(
                    bookmarkId,
                    isRead
                )
            } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                contentSyncPolicyEvaluator,
                contentPackageManager,
            syncScheduler,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Loading)
            // Assert success state
            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    null
                ),
                successState
            )

            viewModel.onToggleMarkReadBookmark(bookmarkId, isRead)
            advanceUntilIdle()

            val errorState = viewModel.uiState.value

            assertEquals(
                BookmarkListViewModel.UiState.Success(
                    bookmarks,
                    BookmarkListViewModel.UpdateBookmarkState.Error(errorMessage)
                ),
                errorState
            )

            coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
        }

    @Test
    fun `onClickLabel updates activeLabel with selected label`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val label = "important"
        viewModel.onClickLabel(label)

        assertEquals(label, viewModel.activeLabel.value)
    }

    @Test
    fun `onRenameLabel calls repository and updates activeLabel if label was active`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { bookmarkRepository.renameLabel(any(), any()) } returns BookmarkRepository.UpdateResult.Success

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val oldLabel = "old"
        val newLabel = "new"
        
        // specific setup to set initial label
        viewModel.onClickLabel(oldLabel)
        assertEquals(oldLabel, viewModel.activeLabel.value)

        viewModel.onRenameLabel(oldLabel, newLabel)
        advanceUntilIdle()

        coVerify { bookmarkRepository.renameLabel(oldLabel, newLabel) }
        assertEquals(newLabel, viewModel.activeLabel.value)
    }

    @Test
    fun `onLayoutModeSelected persists to settings and updates flow`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { settingsDataStore.saveLayoutMode(any()) } returns Unit

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val mode = com.mydeck.app.domain.model.LayoutMode.COMPACT
        viewModel.onLayoutModeSelected(mode)
        advanceUntilIdle()

        assertEquals(mode, viewModel.layoutMode.value)
        coVerify { settingsDataStore.saveLayoutMode(mode.name) }
    }

    @Test
    fun `onSortOptionSelected persists to settings and updates flow`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { settingsDataStore.saveSortOption(any()) } returns Unit

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            contentSyncPolicyEvaluator,
            contentPackageManager,
            syncScheduler,
            connectivityMonitor
        )

        val option = com.mydeck.app.domain.model.SortOption.TITLE_A_TO_Z
        viewModel.onSortOptionSelected(option)
        advanceUntilIdle()

        assertEquals(option, viewModel.sortOption.value)
        coVerify { settingsDataStore.saveSortOption(option.name) }
    }

    @Test
    fun `enter multi-select starts active with zero selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()

        assertTrue(viewModel.multiSelectState.value.active)
        assertEquals(0, viewModel.multiSelectState.value.selectedCount)
        assertFalse(viewModel.multiSelectState.value.hasSelection)
    }

    @Test
    fun `toggle bookmark selection adds and removes selected id`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("bookmark-1")
        assertEquals(setOf("bookmark-1"), viewModel.multiSelectState.value.selectedIds)

        viewModel.onToggleBookmarkSelected("bookmark-1")
        assertTrue(viewModel.multiSelectState.value.active)
        assertEquals(emptySet<String>(), viewModel.multiSelectState.value.selectedIds)
    }

    @Test
    fun `exit multi-select clears selected ids`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("bookmark-1")
        viewModel.onExitMultiSelectMode()

        assertFalse(viewModel.multiSelectState.value.active)
        assertEquals(emptySet<String>(), viewModel.multiSelectState.value.selectedIds)
    }

    @Test
    fun `context change clears multi-select state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("bookmark-1")
        viewModel.onApplyFilter(com.mydeck.app.domain.model.FilterFormState(search = "updated"))

        assertEquals(MultiSelectState(), viewModel.multiSelectState.value)
    }

    @Test
    fun `visible list refresh drops selected ids that are no longer visible`() = runTest {
        val bookmarkFlow = MutableStateFlow(
            listOf(bookmarkListItem("bookmark-1"), bookmarkListItem("bookmark-2"))
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns bookmarkFlow
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("bookmark-1")
        viewModel.onToggleBookmarkSelected("bookmark-2")

        bookmarkFlow.value = listOf(bookmarkListItem("bookmark-2"))
        advanceUntilIdle()

        assertTrue(viewModel.multiSelectState.value.active)
        assertEquals(setOf("bookmark-2"), viewModel.multiSelectState.value.selectedIds)
    }

    @Test
    fun `visible list refresh exits selection mode when all selected ids disappear`() = runTest {
        val bookmarkFlow = MutableStateFlow(
            listOf(bookmarkListItem("bookmark-1"), bookmarkListItem("bookmark-2"))
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns bookmarkFlow
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("bookmark-1")
        viewModel.onToggleBookmarkSelected("bookmark-2")

        bookmarkFlow.value = listOf(bookmarkListItem("bookmark-3"))
        advanceUntilIdle()

        assertEquals(MultiSelectState(), viewModel.multiSelectState.value)
    }

    @Test
    fun `onFavoriteSelectedBookmarks sets favorited on items that need it and emits snackbar with selected count`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("not-favorite", isMarked = false),
            bookmarkListItem("favorite", isMarked = true),
            bookmarkListItem("unselected", isMarked = false)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("not-favorite")
        viewModel.onToggleBookmarkSelected("favorite")
        viewModel.onFavoriteSelectedBookmarks()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(BookmarkBatchUpdate(bookmarkId = "not-favorite", isFavorite = true))
            )
        }
        assertTrue(viewModel.multiSelectState.value.active)
        assertEquals(setOf("not-favorite", "favorite"), viewModel.multiSelectState.value.selectedIds)
        assertEquals(listOf(BatchActionSnackbarEvent.FavoritesAdded(2, listOf("not-favorite"))), events)
        job.cancel()
    }

    @Test
    fun `onFavoriteSelectedBookmarks emits snackbar even when no DB updates are needed`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("fav-a", isMarked = true),
            bookmarkListItem("fav-b", isMarked = true)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("fav-a")
        viewModel.onToggleBookmarkSelected("fav-b")
        viewModel.onFavoriteSelectedBookmarks()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateBookmarkUseCase.updateBookmarks(any()) }
        assertEquals(listOf(BatchActionSnackbarEvent.FavoritesAdded(2, emptyList())), events)
        job.cancel()
    }

    @Test
    fun `onUnfavoriteSelectedBookmarks sets unfavorited on items that need it and emits snackbar`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("fav-a", isMarked = true),
            bookmarkListItem("fav-b", isMarked = true),
            bookmarkListItem("not-favorite", isMarked = false)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("fav-a")
        viewModel.onToggleBookmarkSelected("fav-b")
        viewModel.onToggleBookmarkSelected("not-favorite")
        viewModel.onUnfavoriteSelectedBookmarks()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(
                    BookmarkBatchUpdate(bookmarkId = "fav-a", isFavorite = false),
                    BookmarkBatchUpdate(bookmarkId = "fav-b", isFavorite = false)
                )
            )
        }
        assertEquals(listOf(BatchActionSnackbarEvent.FavoritesRemoved(3, listOf("fav-a", "fav-b"))), events)
        job.cancel()
    }

    @Test
    fun `onArchiveSelectedBookmarks sets archived on items that need it and emits snackbar`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("not-archived", isArchived = false),
            bookmarkListItem("archived", isArchived = true),
            bookmarkListItem("unselected", isArchived = false)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("not-archived")
        viewModel.onToggleBookmarkSelected("archived")
        viewModel.onArchiveSelectedBookmarks()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(BookmarkBatchUpdate(bookmarkId = "not-archived", isArchived = true))
            )
        }
        assertTrue(viewModel.multiSelectState.value.active)
        assertEquals(setOf("not-archived", "archived"), viewModel.multiSelectState.value.selectedIds)
        assertEquals(listOf(BatchActionSnackbarEvent.Archived(2, listOf("not-archived"))), events)
        job.cancel()
    }

    @Test
    fun `onUnarchiveSelectedBookmarks sets unarchived on items that need it and emits snackbar`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("a-a", isArchived = true),
            bookmarkListItem("a-b", isArchived = true)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("a-a")
        viewModel.onToggleBookmarkSelected("a-b")
        viewModel.onUnarchiveSelectedBookmarks()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(
                    BookmarkBatchUpdate(bookmarkId = "a-a", isArchived = false),
                    BookmarkBatchUpdate(bookmarkId = "a-b", isArchived = false)
                )
            )
        }
        assertEquals(listOf(BatchActionSnackbarEvent.Unarchived(2, listOf("a-a", "a-b"))), events)
        job.cancel()
    }

    @Test
    fun `multiSelectTargets reflects mixed selection as no uniform state`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("fav", isMarked = true, isArchived = false),
            bookmarkListItem("arc", isMarked = false, isArchived = true)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("fav")
        viewModel.onToggleBookmarkSelected("arc")
        advanceUntilIdle()

        val targets = viewModel.multiSelectTargets.value
        assertFalse(targets.selectedAllFavorited)
        assertFalse(targets.selectedAllUnfavorited)
        assertFalse(targets.selectedAllArchived)
        assertFalse(targets.selectedAllUnarchived)
    }

    @Test
    fun `multiSelectTargets reflects all-favorited selection`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("a", isMarked = true),
            bookmarkListItem("b", isMarked = true),
            bookmarkListItem("c", isMarked = false)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("a")
        viewModel.onToggleBookmarkSelected("b")
        advanceUntilIdle()

        val targets = viewModel.multiSelectTargets.value
        assertTrue(targets.selectedAllFavorited)
        assertFalse(targets.selectedAllUnfavorited)
        // archive axis is independent: both items are not archived
        assertFalse(targets.selectedAllArchived)
        assertTrue(targets.selectedAllUnarchived)
    }

    @Test
    fun `multiSelectTargets reflects all-archived selection`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("a", isArchived = true),
            bookmarkListItem("b", isArchived = true)
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("a")
        viewModel.onToggleBookmarkSelected("b")
        advanceUntilIdle()

        val targets = viewModel.multiSelectTargets.value
        assertTrue(targets.selectedAllArchived)
        assertFalse(targets.selectedAllUnarchived)
        // favorite axis is independent: neither is favorited
        assertFalse(targets.selectedAllFavorited)
        assertTrue(targets.selectedAllUnfavorited)
    }

    @Test
    fun `multiSelectTargets returns false on all uniformity flags when selection is empty`() = runTest {
        val visibleBookmarks = listOf(bookmarkListItem("a", isMarked = true, isArchived = true))
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        advanceUntilIdle()

        val targets = viewModel.multiSelectTargets.value
        assertFalse(targets.selectedAllFavorited)
        assertFalse(targets.selectedAllUnfavorited)
        assertFalse(targets.selectedAllArchived)
        assertFalse(targets.selectedAllUnarchived)
    }

    @Test
    fun `select all toggles between selecting all visible and clearing`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("a"),
            bookmarkListItem("b"),
            bookmarkListItem("c")
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleSelectAllBookmarks()
        advanceUntilIdle()

        assertEquals(setOf("a", "b", "c"), viewModel.multiSelectState.value.selectedIds)
        assertTrue(viewModel.multiSelectTargets.value.allVisibleSelected)

        viewModel.onToggleSelectAllBookmarks()
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.multiSelectState.value.selectedIds)
        assertFalse(viewModel.multiSelectTargets.value.allVisibleSelected)
    }

    @Test
    fun `select all from partial selection completes to all visible`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("a"),
            bookmarkListItem("b"),
            bookmarkListItem("c")
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("a")
        viewModel.onToggleSelectAllBookmarks()

        assertEquals(setOf("a", "b", "c"), viewModel.multiSelectState.value.selectedIds)
    }

    @Test
    fun `select all is a no-op when multi-select is not active`() = runTest {
        val visibleBookmarks = listOf(bookmarkListItem("a"))
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleSelectAllBookmarks()

        assertFalse(viewModel.multiSelectState.value.active)
        assertEquals(emptySet<String>(), viewModel.multiSelectState.value.selectedIds)
    }

    @Test
    fun `onDeleteSelectedBookmarks stages all selected ids, exits selection, and emits Deleted without deleting`() = runTest {
        val visibleBookmarks = listOf(
            bookmarkListItem("del-1"),
            bookmarkListItem("del-2"),
            bookmarkListItem("keep")
        )
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<BatchActionSnackbarEvent>()
        val job = launch { viewModel.batchActionSnackbarEvent.toList(events) }

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("del-1")
        viewModel.onToggleBookmarkSelected("del-2")
        viewModel.onDeleteSelectedBookmarks()
        advanceUntilIdle()

        assertEquals(setOf("del-1", "del-2"), viewModel.pendingBatchDeletionBookmarkIds.value)
        assertEquals(MultiSelectState(), viewModel.multiSelectState.value)
        assertEquals(listOf<BatchActionSnackbarEvent>(BatchActionSnackbarEvent.Deleted(2)), events)
        coVerify(exactly = 0) { updateBookmarkUseCase.deleteBookmarks(any()) }
        job.cancel()
    }

    @Test
    fun `onConfirmBatchDeletion deletes all staged ids once and clears pending`() = runTest {
        val visibleBookmarks = listOf(bookmarkListItem("del-1"), bookmarkListItem("del-2"))
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        coEvery { updateBookmarkUseCase.deleteBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("del-1")
        viewModel.onToggleBookmarkSelected("del-2")
        viewModel.onDeleteSelectedBookmarks()
        advanceUntilIdle()

        viewModel.onConfirmBatchDeletion()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.deleteBookmarks(match { it.toSet() == setOf("del-1", "del-2") })
        }
        assertEquals(emptySet<String>(), viewModel.pendingBatchDeletionBookmarkIds.value)
    }

    @Test
    fun `onCancelBatchDeletion clears staged ids without enqueuing any delete`() = runTest {
        val visibleBookmarks = listOf(bookmarkListItem("del-1"), bookmarkListItem("del-2"))
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("del-1")
        viewModel.onToggleBookmarkSelected("del-2")
        viewModel.onDeleteSelectedBookmarks()
        advanceUntilIdle()

        viewModel.onCancelBatchDeletion()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateBookmarkUseCase.deleteBookmarks(any()) }
        assertEquals(emptySet<String>(), viewModel.pendingBatchDeletionBookmarkIds.value)
    }

    @Test
    fun `onUndoBatchAction on Deleted event cancels staged batch delete`() = runTest {
        val visibleBookmarks = listOf(bookmarkListItem("del-1"), bookmarkListItem("del-2"))
        every {
            bookmarkRepository.observeFilteredBookmarkListItems(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns flowOf(visibleBookmarks)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMultiSelectMode()
        viewModel.onToggleBookmarkSelected("del-1")
        viewModel.onToggleBookmarkSelected("del-2")
        viewModel.onDeleteSelectedBookmarks()
        advanceUntilIdle()

        viewModel.onUndoBatchAction(BatchActionSnackbarEvent.Deleted(2))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateBookmarkUseCase.deleteBookmarks(any()) }
        assertEquals(emptySet<String>(), viewModel.pendingBatchDeletionBookmarkIds.value)
    }

    @Test
    fun `onUndoBatchAction reverts only changed favorite items to their prior state`() = runTest {
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUndoBatchAction(BatchActionSnackbarEvent.FavoritesAdded(3, listOf("changed-a", "changed-b")))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(
                    BookmarkBatchUpdate(bookmarkId = "changed-a", isFavorite = false),
                    BookmarkBatchUpdate(bookmarkId = "changed-b", isFavorite = false)
                )
            )
        }
    }

    @Test
    fun `onUndoBatchAction reverts only changed archive items to their prior state`() = runTest {
        coEvery { updateBookmarkUseCase.updateBookmarks(any()) } returns UpdateBookmarkUseCase.Result.Success
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUndoBatchAction(BatchActionSnackbarEvent.Unarchived(1, listOf("changed-x")))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBookmarkUseCase.updateBookmarks(
                listOf(BookmarkBatchUpdate(bookmarkId = "changed-x", isArchived = true))
            )
        }
    }

    @Test
    fun `onUndoBatchAction with no changed favorite items does not touch the repository`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUndoBatchAction(BatchActionSnackbarEvent.FavoritesAdded(2, emptyList()))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateBookmarkUseCase.updateBookmarks(any()) }
    }

    private fun bookmarkListItem(
        id: String,
        isMarked: Boolean = false,
        isArchived: Boolean = false
    ) = BookmarkListItem(
        id = id,
        href = "https://example.com/api/bookmarks/$id",
        url = "https://example.com/$id",
        title = "Bookmark $id",
        siteName = "Example Site",
        type = Bookmark.Type.Article,
        isMarked = isMarked,
        isArchived = isArchived,
        labels = emptyList(),
        isRead = false,
        readProgress = 0,
        thumbnailSrc = "",
        iconSrc = "",
        imageSrc = "",
        readingTime = null,
        created = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
        wordCount = null,
        published = null
    )

    private val bookmarks = listOf(
        BookmarkListItem(
            id = "1",
            href = "https://example.com/api/bookmarks/1",
            url = "https://example.com",
            title = "Test Bookmark",
            siteName = "Example Site",
            type = Bookmark.Type.Article,
            isMarked = false,
            isArchived = false,
            labels = emptyList(),
            isRead = false,
            readProgress = 0,
            thumbnailSrc = "",
            iconSrc = "",
            imageSrc = "",
            readingTime = null,
            created = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
            wordCount = null,
            published = null
        )
    )
}
