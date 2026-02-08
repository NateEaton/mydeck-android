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

        // Default Mocking Behavior
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns true // Assume sync is done
        coEvery { settingsDataStore.isSyncOnAppOpenEnabled() } returns false // Disable sync on app open by default
        every { fullSyncUseCase.performFullSync() } returns Unit
        every { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any()) } returns flowOf(
            emptyList()
        ) // No bookmarks initially
        // Mock the default filter state (archived = false) for My List view
        every { bookmarkRepository.observeBookmarkListItems(null, null, false, null, any()) } returns flowOf(
            emptyList()
        )
        every { savedStateHandle.get<String>(any()) } returns null // no sharedUrl initially
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns workInfoFlow
        every { bookmarkRepository.observeAllBookmarkCounts() } returns flowOf(BookmarkCounts())
        every { bookmarkRepository.observeAllLabelsWithCounts() } returns flowOf(emptyMap())
        every { connectivityMonitor.observeConnectivity() } returns flowOf(true)
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false
        coEvery { settingsDataStore.getLayoutMode() } returns null
        coEvery { settingsDataStore.getSortOption() } returns null
    }

    @After
    fun tearDown() {
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
    fun `onClickMyList sets archived filter to false`() = runTest {
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
        assertEquals(
            BookmarkListViewModel.FilterState(archived = false),
            viewModel.filterState.first()
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
            BookmarkListViewModel.FilterState(archived = true),
            viewModel.filterState.first()
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
            BookmarkListViewModel.FilterState(favorite = true),
            viewModel.filterState.first()
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
    fun `onNavigationEventConsumed resets navigation event`() = runTest {
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
        viewModel.onClickSettings() // Set a navigation event
        viewModel.onNavigationEventConsumed()
        assertEquals(null, viewModel.navigationEvent.first())
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
        coEvery {
            bookmarkRepository.observeBookmarkListItems(
                type = null,
                unread = null,
                archived = false,
                favorite = null,
                state = Bookmark.State.LOADED
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
    fun `createBookmark calls repository and sets state to Success`() = runTest {
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
        coEvery { bookmarkRepository.createBookmark(title, url, emptyList()) } returns "bookmark123"

        viewModel.updateCreateBookmarkTitle(title)
        viewModel.updateCreateBookmarkUrl(url)
        viewModel.createBookmark()
        runCurrent()

        coVerify { bookmarkRepository.createBookmark(title, url, emptyList()) }
        println("state=${viewModel.createBookmarkUiState.value}")
        assertTrue(viewModel.createBookmarkUiState.value is BookmarkListViewModel.CreateBookmarkUiState.Success)
        assertEquals(
            BookmarkListViewModel.CreateBookmarkUiState.Success(isArchived = false),
            viewModel.createBookmarkUiState.value
        )
    }

    @Test
    fun `createBookmark sets state to Error if repository call fails`() = runTest {
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
        val errorMessage = "Failed to create bookmark"
        coEvery { bookmarkRepository.createBookmark(title, url, emptyList()) } throws Exception(errorMessage)

        viewModel.updateCreateBookmarkTitle(title)
        viewModel.updateCreateBookmarkUrl(url)
        viewModel.createBookmark()

        val uiStates = viewModel.createBookmarkUiState.take(2).toList()
        assertTrue(uiStates[1] is BookmarkListViewModel.CreateBookmarkUiState.Error)
        assertEquals(
            errorMessage,
            (uiStates[1] as BookmarkListViewModel.CreateBookmarkUiState.Error).message
        )
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
    fun `init sets CreateBookmarkUiState to Open with sharedText and urlError if present and invalid`() =
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
            assertEquals(R.string.account_settings_url_error, state.urlError)
            assertFalse(state.isCreateEnabled)
        }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with UpdateBookmarkState Success`() =
        runTest {
            coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
            val bookmarkId = "123"
            val isFavorite = true

            val bookmarkFlow = MutableStateFlow(bookmarks)
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
            bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
            coEvery {
                bookmarkRepository.observeBookmarkListItems(
                    type = null,
                    unread = null,
                    archived = false,
                    favorite = null,
                    state = Bookmark.State.LOADED
                )
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
    fun `onSearchQueryChange emits new query to searchQuery flow`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

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

        val query = "test query"
        viewModel.onSearchQueryChange(query)

        assertEquals(query, viewModel.searchQuery.value)
    }

    @Test
    fun `searchBookmarkListItems is called when query is non-empty`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
        coEvery { bookmarkRepository.searchBookmarkListItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

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

        // Collecting state to trigger the flatMapLatest flow
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        val query = "test"
        viewModel.onSearchQueryChange(query)
        advanceUntilIdle() // Wait for debounce (300ms)

        coVerify {
            bookmarkRepository.searchBookmarkListItems(
                searchQuery = query,
                type = null,
                unread = null,
                archived = false,
                favorite = null,
                label = null,
                state = Bookmark.State.LOADED,
                orderBy = "created DESC"
            )
        }
        
        job.cancel()
    }

    @Test
    fun `onClearSearch resets query to empty string`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

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

        viewModel.onSearchQueryChange("test")
        assertEquals("test", viewModel.searchQuery.value)

        viewModel.onClearSearch()
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `onClickLabel updates FilterState with selected label`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)

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

        val filterState = viewModel.filterState.value
        assertEquals(label, filterState.label)
    }

    @Test
    fun `onRenameLabel calls repository and updates filter if label was active`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
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
        assertEquals(oldLabel, viewModel.filterState.value.label)

        viewModel.onRenameLabel(oldLabel, newLabel)
        advanceUntilIdle()

        coVerify { bookmarkRepository.renameLabel(oldLabel, newLabel) }
        assertEquals(newLabel, viewModel.filterState.value.label)
    }

    @Test
    fun `onLayoutModeSelected persists to settings and updates flow`() = runTest {
        coEvery { settingsDataStore.isInitialSyncPerformed() } returns false
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
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
        coEvery { bookmarkRepository.observeBookmarkListItems(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(bookmarks)
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
