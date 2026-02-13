package com.mydeck.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.AssetLoader
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var assetLoader: AssetLoader
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: BookmarkDetailViewModel
    private lateinit var updateBookmarkUseCase: UpdateBookmarkUseCase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loadArticleUseCase: LoadArticleUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk()
        assetLoader = mockk()
        savedStateHandle = mockk()
        updateBookmarkUseCase = mockk()
        settingsDataStore = mockk()
        loadArticleUseCase = mockk(relaxed = true)
        every { bookmarkRepository.observeBookmark(any()) } returns MutableStateFlow(sampleBookmark)
        coEvery { bookmarkRepository.getBookmarkById(any()) } returns sampleBookmark
        coEvery { bookmarkRepository.refreshBookmarkFromApi(any()) } returns Unit
        every { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate
        every { savedStateHandle.get<String>("bookmarkId") } returns "123"
        every { settingsDataStore.themeFlow } returns MutableStateFlow(Theme.LIGHT.name)
        every { settingsDataStore.zoomFactorFlow } returns MutableStateFlow(100)
        every { settingsDataStore.typographySettingsFlow } returns MutableStateFlow(com.mydeck.app.domain.model.TypographySettings())
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits success when bookmark and html template are loaded successfully`() = runTest {
        // Arrange
        val bookmark = Bookmark(
            id = "123",
            href = "https://example.com",
            created = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            updated = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            state = Bookmark.State.LOADED,
            loaded = true,
            url = "https://example.com",
            title = "Test Bookmark",
            siteName = "Example Site",
            site = "example.com",
            authors = listOf("Author 1", "Author 2"),
            lang = "en",
            textDirection = "ltr",
            documentTpe = "article",
            type = Bookmark.Type.Article,
            hasArticle = true,
            description = "Test Description",
            isDeleted = false,
            isMarked = false,
            isArchived = false,
            labels = emptyList(),
            readProgress = 0,
            wordCount = 0,
            readingTime = 0,
            published = null,
            embed = null,
            embedHostname = null,
            article = Bookmark.Resource(""),
            articleContent = "Test Article Content",
            icon = Bookmark.ImageResource("", 0, 0),
            image = Bookmark.ImageResource("", 0, 0),
            log = Bookmark.Resource(""),
            props = Bookmark.Resource(""),
            thumbnail = Bookmark.ImageResource("", 0, 0)
        )
        val htmlTemplate = "<html><body>%s</body></html>"
        val expectedHtmlContent = htmlTemplate.replace("%s", bookmark.articleContent!!)
        val expectedCreatedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(
            Date(bookmark.created.toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds())
        )

        coEvery { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        val uiStates = viewModel.uiState.take(2).toList()
        val loading = uiStates[0]
        val success = uiStates[1]

        // Assert initial state
        assert(loading is BookmarkDetailViewModel.UiState.Loading)
        // Assert success state
        assert(success is BookmarkDetailViewModel.UiState.Success)
        success as BookmarkDetailViewModel.UiState.Success
        assertEquals("Test Bookmark", success.bookmark.title)
        assertEquals(expectedCreatedDate, success.bookmark.createdDate)
        assertEquals("123", success.bookmark.bookmarkId)
        assertEquals("Example Site", success.bookmark.siteName)
    }

    @Test
    fun `uiState emits error when html template loading fails`() = runTest {
        // Arrange
        val bookmark = Bookmark(
            id = "123",
            href = "https://example.com",
            created = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            updated = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            state = Bookmark.State.LOADED,
            loaded = true,
            url = "https://example.com",
            title = "Test Bookmark",
            siteName = "Example Site",
            site = "example.com",
            authors = listOf("Author 1", "Author 2"),
            lang = "en",
            textDirection = "ltr",
            documentTpe = "article",
            type = Bookmark.Type.Article,
            hasArticle = true,
            description = "Test Description",
            isDeleted = false,
            isMarked = false,
            isArchived = false,
            labels = emptyList(),
            readProgress = 0,
            wordCount = 0,
            readingTime = 0,
            published = null,
            embed = null,
            embedHostname = null,
            article = Bookmark.Resource(""),
            articleContent = "Test Article Content",
            icon = Bookmark.ImageResource("", 0, 0),
            image = Bookmark.ImageResource("", 0, 0),
            log = Bookmark.Resource(""),
            props = Bookmark.Resource(""),
            thumbnail = Bookmark.ImageResource("", 0, 0)
        )
        coEvery { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns null

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        val uiStates = viewModel.uiState.take(2).toList()
        val loading = uiStates[0]
        val error = uiStates[1]
        // Assert initial state
        assert(loading is BookmarkDetailViewModel.UiState.Loading)
        // Assert error state
        assert(error is BookmarkDetailViewModel.UiState.Error)
    }

    @Test
    fun `uiState emits loading initially`() = runTest {
        // Arrange
        val bookmarkFlow = MutableStateFlow(Bookmark(
            id = "123",
            href = "https://example.com",
            created = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            updated = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
            state = Bookmark.State.LOADED,
            loaded = true,
            url = "https://example.com",
            title = "Test Bookmark",
            siteName = "Example Site",
            site = "example.com",
            authors = listOf("Author 1", "Author 2"),
            lang = "en",
            textDirection = "ltr",
            documentTpe = "article",
            type = Bookmark.Type.Article,
            hasArticle = true,
            description = "Test Description",
            isDeleted = false,
            isMarked = false,
            isArchived = false,
            labels = emptyList(),
            readProgress = 0,
            wordCount = 0,
            readingTime = 0,
            published = null,
            embed = null,
            embedHostname = null,
            article = Bookmark.Resource(""),
            articleContent = "Test Article Content",
            icon = Bookmark.ImageResource("", 0, 0),
            image = Bookmark.ImageResource("", 0, 0),
            log = Bookmark.Resource(""),
            props = Bookmark.Resource(""),
            thumbnail = Bookmark.ImageResource("", 0, 0)
        ))
        coEvery { bookmarkRepository.observeBookmark("123") } returns bookmarkFlow
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns "template"

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)

        // Assert
        assertEquals(BookmarkDetailViewModel.UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `onNavigationEventConsumed should reset navigation event`() = runTest {
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onClickBack()
        advanceUntilIdle()
        viewModel.onNavigationEventConsumed()
        assertNull(viewModel.navigationEvent.first())
    }

    @Test
    fun `onClickBack should set NavigateBack navigation event`() = runTest {
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onClickBack()
        advanceUntilIdle()
        assertEquals(BookmarkDetailViewModel.NavigationEvent.NavigateBack, viewModel.navigationEvent.first())
    }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with Success`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true
        coEvery { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) } returns UpdateBookmarkUseCase.Result.Success
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleFavorite(bookmarkId, isFavorite)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Success, (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
    }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with GenericError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true
        val errorMessage = "Generic Error"
        coEvery { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleFavorite(bookmarkId, isFavorite)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
    }

    @Test
    fun `onToggleFavoriteBookmark updates UiState with NetworkError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isFavorite = true
        val errorMessage = "Network Error"
        coEvery { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleFavorite(bookmarkId, isFavorite)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsFavorite(bookmarkId, isFavorite) }
    }

    @Test
    fun `onToggleArchivedBookmark updates UiState with Success`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true
        coEvery { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) } returns UpdateBookmarkUseCase.Result.Success
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleArchive(bookmarkId, isArchived)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Success, (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
    }

    @Test
    fun `onToggleArchivedBookmark updates UiState with GenericError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true
        val errorMessage = "Generic Error"
        coEvery { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleArchive(bookmarkId, isArchived)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
    }

    @Test
    fun `onToggleArchivedBookmark updates UiState with NetworkError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true
        val errorMessage = "Network Error"
        coEvery { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleArchive(bookmarkId, isArchived)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
    }
    @Test
    fun `onToggleMarkReadBookmark updates UiState with Success`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isRead = true
        coEvery { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) } returns UpdateBookmarkUseCase.Result.Success
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleMarkRead(bookmarkId, isRead)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Success, (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
    }

    @Test
    fun `onToggleMarkReadBookmark updates UiState with GenericError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isRead = true
        val errorMessage = "Generic Error"
        coEvery { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) } returns UpdateBookmarkUseCase.Result.GenericError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleMarkRead(bookmarkId, isRead)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
    }

    @Test
    fun `onToggleMarkReadBookmark updates UiState with NetworkError`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isRead = true
        val errorMessage = "Network Error"
        coEvery { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) } returns UpdateBookmarkUseCase.Result.NetworkError(errorMessage)
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        viewModel.onToggleMarkRead(bookmarkId, isRead)
        advanceUntilIdle()

        // Assert
        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Error(errorMessage), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
        coVerify { updateBookmarkUseCase.updateIsRead(bookmarkId, isRead) }
    }

    @Test
    fun `init calls loadArticleUseCase when contentState is NOT_ATTEMPTED and hasArticle is true`() = runTest {
        // Arrange
        val bookmarkWithNotAttempted = sampleBookmark.copy(
            contentState = Bookmark.ContentState.NOT_ATTEMPTED,
            hasArticle = true,
            articleContent = null
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithNotAttempted)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmarkWithNotAttempted
        coEvery { bookmarkRepository.refreshBookmarkFromApi(any()) } returns Unit
        coEvery { loadArticleUseCase.execute("123") } returns LoadArticleUseCase.Result.Success

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        advanceUntilIdle()

        // Assert
        coVerify { loadArticleUseCase.execute("123") }
    }

    @Test
    fun `init does NOT call loadArticleUseCase when contentState is DOWNLOADED`() = runTest {
        // Arrange
        val bookmarkWithDownloaded = sampleBookmark.copy(
            contentState = Bookmark.ContentState.DOWNLOADED,
            hasArticle = true,
            articleContent = "Content already downloaded"
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithDownloaded)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmarkWithDownloaded
        coEvery { bookmarkRepository.refreshBookmarkFromApi(any()) } returns Unit

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { loadArticleUseCase.execute(any()) }
    }

    @Test
    fun `init sets contentLoadState to Failed when contentState is PERMANENT_NO_CONTENT`() = runTest {
        // Arrange
        val bookmarkWithNoContent = sampleBookmark.copy(
            contentState = Bookmark.ContentState.PERMANENT_NO_CONTENT,
            contentFailureReason = "Source blocked content extraction",
            hasArticle = true,
            articleContent = null
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithNoContent)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmarkWithNoContent
        coEvery { bookmarkRepository.refreshBookmarkFromApi(any()) } returns Unit

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        advanceUntilIdle()

        // Assert
        val state = viewModel.contentLoadState.value
        assert(state is BookmarkDetailViewModel.ContentLoadState.Failed)
        val failedState = state as BookmarkDetailViewModel.ContentLoadState.Failed
        assertEquals("Source blocked content extraction", failedState.reason)
        assertEquals(false, failedState.canRetry)
        coVerify(exactly = 0) { loadArticleUseCase.execute(any()) }
    }

    @Test
    fun `permanent failure from loadArticleUseCase sets canRetry to false`() = runTest {
        // Arrange
        val bookmarkWithNotAttempted = sampleBookmark.copy(
            contentState = Bookmark.ContentState.NOT_ATTEMPTED,
            hasArticle = true,
            articleContent = null
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithNotAttempted)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmarkWithNotAttempted
        coEvery { bookmarkRepository.refreshBookmarkFromApi(any()) } returns Unit
        coEvery { loadArticleUseCase.execute("123") } returns LoadArticleUseCase.Result.PermanentFailure("Article extraction not supported for this site")

        // Act
        viewModel = BookmarkDetailViewModel(updateBookmarkUseCase, bookmarkRepository, assetLoader, settingsDataStore, loadArticleUseCase, savedStateHandle)
        advanceUntilIdle()

        // Assert
        val state = viewModel.contentLoadState.value
        assert(state is BookmarkDetailViewModel.ContentLoadState.Failed)
        val failedState = state as BookmarkDetailViewModel.ContentLoadState.Failed
        assertEquals("Article extraction not supported for this site", failedState.reason)
        assertEquals(false, failedState.canRetry)
    }

    val sampleBookmark = Bookmark(

        id = "123",
        href = "https://example.com",
        created = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
        updated = kotlinx.datetime.LocalDateTime(2024, 1, 20, 12, 0, 0),
        state = Bookmark.State.LOADED,
        loaded = true,
        url = "https://example.com",
        title = "Test Bookmark",
        siteName = "Example Site",
        site = "example.com",
        authors = listOf("Author 1", "Author 2"),
        lang = "en",
        textDirection = "ltr",
        documentTpe = "article",
        type = Bookmark.Type.Article,
        hasArticle = true,
        description = "Test Description",
        isDeleted = false,
        isMarked = false,
        isArchived = false,
        labels = emptyList(),
        readProgress = 0,
        wordCount = 0,
        readingTime = 0,
        published = null,
        embed = null,
        embedHostname = null,
        article = Bookmark.Resource(""),
        articleContent = "Test Article Content",
        icon = Bookmark.ImageResource("", 0, 0),
        image = Bookmark.ImageResource("", 0, 0),
        log = Bookmark.Resource(""),
        props = Bookmark.Resource(""),
        thumbnail = Bookmark.ImageResource("", 0, 0),
        contentState = Bookmark.ContentState.DOWNLOADED,
        contentFailureReason = null
    )
    val htmlTemplate = "<html><body>%s</body></html>"
}
