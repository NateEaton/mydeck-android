package com.mydeck.app.ui.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
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

    private lateinit var workInfoFlow: Flow<List<WorkInfo>>

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

        workInfoFlow = flowOf(emptyList())
        mockkObject(LoadBookmarksWorker.Companion)
        every { LoadBookmarksWorker.enqueue(any(), any()) } just Runs
        mockkObject(CreateBookmarkWorker.Companion)
        every { CreateBookmarkWorker.enqueue(any(), any(), any(), any(), any()) } just Runs

        every { bookmarkRepository.searchBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        // Default Mocking Behavior
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true // Assume sync is done
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns false // Disable sync on app open by default
        every { fullSyncUseCase.performFullSync() } returns Unit
        // Use any() for all arguments to be safe, then specialize
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        every { savedStateHandle.get<String>(any()) } returns null // no sharedUrl initially
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns workInfoFlow
        every { bookmarkRepository.observeAllBookmarkCounts() } returns flowOf(BookmarkCounts())
        every { bookmarkRepository.observeAllLabelsWithCounts() } returns flowOf(emptyMap())
        every { bookmarkRepository.observePendingActionCount() } returns flowOf(0)
        every { connectivityMonitor.observeConnectivity() } returns flowOf(true)
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false
        coEvery { settingsDataStore.getLayoutMode() } returns null
        coEvery { settingsDataStore.getSortOption() } returns null
    }

    @After
    fun tearDown() {
        unmockkObject(CreateBookmarkWorker.Companion)
        unmockkObject(LoadBookmarksWorker.Companion)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState is Empty`() {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            connectivityMonitor
        )
        // Since we emit empty list by default from mock, and filter/query are empty/default,
        // it should be Empty state.
        assertEquals(BookmarkListViewModel.UiState.Empty(R.string.list_view_empty_not_loaded_yet), viewModel.uiState.value)
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
            connectivityMonitor
        )
        // Just verify that it doesn't throw an exception for now
        viewModel.onPullToRefresh()
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
            connectivityMonitor
        )

        viewModel.onClickMyList()

        val uiStates = viewModel.uiState.take(2).toList()
        val empty = uiStates[0]
        val success = uiStates[1]
        // Assert initial state
        assert(empty is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns bookmarkFlow

            viewModel = BookmarkListViewModel(
                updateBookmarkUseCase,
                fullSyncUseCase,
                workManager,
                bookmarkRepository,
                context,
                settingsDataStore,
                savedStateHandle,
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
                bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
                connectivityMonitor
            )

            val uiStates = viewModel.uiState.take(2).toList()
            val emptyState = uiStates[0]
            val successState = uiStates[1]
            // Assert initial state
            assert(emptyState is BookmarkListViewModel.UiState.Empty)
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
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            connectivityMonitor
        )

        val label = "important"
        viewModel.onClickLabel(label)

        assertEquals(label, viewModel.activeLabel.value)
    }

    @Test
    fun `onRenameLabel calls repository and updates activeLabel if label was active`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { bookmarkRepository.renameLabel(any(), any()) } returns BookmarkRepository.UpdateResult.Success

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
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
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { settingsDataStore.saveLayoutMode(any()) } returns Unit

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
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
        every { bookmarkRepository.observeFilteredBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { settingsDataStore.saveSortOption(any()) } returns Unit

        viewModel = BookmarkListViewModel(
            updateBookmarkUseCase,
            fullSyncUseCase,
            workManager,
            bookmarkRepository,
            context,
            settingsDataStore,
            savedStateHandle,
            connectivityMonitor
        )

        val option = com.mydeck.app.domain.model.SortOption.TITLE_A_TO_Z
        viewModel.onSortOptionSelected(option)
        advanceUntilIdle()

        assertEquals(option, viewModel.sortOption.value)
        coVerify { settingsDataStore.saveSortOption(option.name) }
    }
    private val bookmarks = listOf(
        BookmarkListItem(
            id = "1",
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
