package com.mydeck.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Annotation
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.ReaderAppearanceSelection
import com.mydeck.app.domain.model.SelectionData
import com.mydeck.app.domain.model.Template
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.ContentSyncConstraints
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.usecase.LoadArticleUseCase
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.domain.usecase.LoadContentPackageUseCase
import com.mydeck.app.domain.usecase.UpdateBookmarkUseCase
import com.mydeck.app.io.AssetLoader
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import com.mydeck.app.io.rest.model.UpdateAnnotationDto
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.text.DateFormat
import java.util.Date
import android.content.Context
import androidx.work.WorkManager

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
    private lateinit var loadContentPackageUseCase: LoadContentPackageUseCase
    private lateinit var contentPackageManager: ContentPackageManager
    private lateinit var readeckApi: ReadeckApi
    private lateinit var cachedAnnotationDao: CachedAnnotationDao
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var multipartSyncClient: com.mydeck.app.io.rest.sync.MultipartSyncClient
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private lateinit var themeFlow: MutableStateFlow<String?>
    private lateinit var lightAppearanceFlow: MutableStateFlow<LightAppearance>
    private lateinit var darkAppearanceFlow: MutableStateFlow<DarkAppearance>
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        bookmarkRepository = mockk()
        assetLoader = mockk()
        savedStateHandle = mockk()
        updateBookmarkUseCase = mockk()
        settingsDataStore = mockk()
        loadArticleUseCase = mockk(relaxed = true)
        loadContentPackageUseCase = mockk(relaxed = true)
        contentPackageManager = mockk(relaxed = true)
        every { contentPackageManager.observePackageSource(any()) } returns MutableStateFlow(null)
        every { contentPackageManager.getContentDir(any()) } returns null
        coEvery { contentPackageManager.hasResources(any()) } returns true
        coEvery { loadContentPackageUseCase.execute(any(), any()) } returns LoadContentPackageUseCase.Result.TransientFailure("Not available")
        readeckApi = mockk(relaxed = true)
        cachedAnnotationDao = mockk(relaxed = true)
        connectivityMonitor = mockk(relaxed = true)
        multipartSyncClient = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        coEvery { cachedAnnotationDao.getAnnotationsForBookmark(any()) } returns emptyList()
        every { connectivityMonitor.isNetworkAvailable() } returns true
        context = mockk(relaxed = true)
        every { context.packageName } returns "com.mydeck.app"
        every { bookmarkRepository.observeBookmark(any()) } returns MutableStateFlow(sampleBookmark)
        coEvery { bookmarkRepository.getBookmarkById(any()) } returns sampleBookmark
        every { assetLoader.loadAsset(match { it.startsWith("html_template_") }) } returns htmlTemplate
        every { savedStateHandle.get<String>("bookmarkId") } returns "123"
        every { savedStateHandle.get<String>("annotationId") } returns null
        themeFlow = MutableStateFlow(Theme.LIGHT.name)
        lightAppearanceFlow = MutableStateFlow(LightAppearance.PAPER)
        darkAppearanceFlow = MutableStateFlow(DarkAppearance.DARK)
        every { settingsDataStore.themeFlow } returns themeFlow
        every { settingsDataStore.lightAppearanceFlow } returns lightAppearanceFlow
        every { settingsDataStore.darkAppearanceFlow } returns darkAppearanceFlow
        every { settingsDataStore.typographySettingsFlow } returns MutableStateFlow(com.mydeck.app.domain.model.TypographySettings())
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(true)
        every { settingsDataStore.fullscreenWhileReadingFlow } returns MutableStateFlow(false)
        every { settingsDataStore.offlineReadingEnabledFlow } returns MutableStateFlow(false)
        coEvery { settingsDataStore.getBookmarkShareFormat() } returns BookmarkShareFormat.URL_ONLY
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns false
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST_AND_ARCHIVED
        coEvery {
            settingsDataStore.getContentSyncConstraints()
        } returns ContentSyncConstraints(
            wifiOnly = false,
            allowOnBatterySaver = true
        )
        coEvery { settingsDataStore.getTheme() } coAnswers {
            Theme.valueOf(themeFlow.value ?: Theme.SYSTEM.name)
        }
        coEvery { settingsDataStore.getLightAppearance() } coAnswers {
            lightAppearanceFlow.value
        }
        coEvery { settingsDataStore.getDarkAppearance() } coAnswers {
            darkAppearanceFlow.value
        }
        coEvery { settingsDataStore.saveTheme(any()) } coAnswers {
            themeFlow.value = firstArg<Theme>().name
        }
        coEvery { settingsDataStore.saveLightAppearance(any()) } coAnswers {
            lightAppearanceFlow.value = firstArg()
        }
        coEvery { settingsDataStore.saveDarkAppearance(any()) } coAnswers {
            darkAppearanceFlow.value = firstArg()
        }
        every { bookmarkRepository.observeAllLabelsWithCounts() } returns MutableStateFlow(emptyMap())
        coEvery { settingsDataStore.saveCachedAnnotationSnapshot(any(), any()) } just Runs
        coEvery { settingsDataStore.getCachedAnnotationSnapshot(any()) } returns null
        coEvery { settingsDataStore.clearCachedAnnotationSnapshot(any()) } just Runs
        coEvery { readeckApi.getAnnotations(any()) } returns Response.success(emptyList())
        viewModel = createViewModel()
    }

    private fun createViewModel(): BookmarkDetailViewModel {
        return BookmarkDetailViewModel(
            updateBookmarkUseCase = updateBookmarkUseCase,
            bookmarkRepository = bookmarkRepository,
            assetLoader = assetLoader,
            settingsDataStore = settingsDataStore,
            loadArticleUseCase = loadArticleUseCase,
            loadContentPackageUseCase = loadContentPackageUseCase,
            contentPackageManager = contentPackageManager,
            cachedAnnotationDao = cachedAnnotationDao,
            connectivityMonitor = connectivityMonitor,
            multipartSyncClient = multipartSyncClient,
            readeckApi = readeckApi,
            workManager = workManager,
            context = context,
            json = json,
            savedStateHandle = savedStateHandle
        )
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
        val expectedCreatedDate = DateFormat.getDateInstance(DateFormat.LONG).format(
            Date(bookmark.created.toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds())
        )

        coEvery { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = createViewModel()
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()

        // Assert
        assertEquals(BookmarkDetailViewModel.UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `onClickBack should set NavigateBack navigation event`() = runTest {
        viewModel = createViewModel()
        viewModel.onClickBack()
        advanceUntilIdle()
        assertEquals(BookmarkDetailViewModel.NavigationEvent.NavigateBack, viewModel.navigationEvent.first())
    }

    @Test
    fun `onReaderThemeSelectionChanged saves dark appearance and theme when switching from light`() = runTest {
        viewModel = createViewModel()

        viewModel.onReaderThemeSelectionChanged(
            ReaderAppearanceSelection(
                themeMode = Theme.DARK,
                lightAppearance = LightAppearance.PAPER,
                darkAppearance = DarkAppearance.BLACK
            )
        )
        advanceUntilIdle()

        assertEquals(Theme.DARK.name, themeFlow.value)
        assertEquals(LightAppearance.PAPER, lightAppearanceFlow.value)
        assertEquals(DarkAppearance.BLACK, darkAppearanceFlow.value)
        coVerify { settingsDataStore.saveDarkAppearance(DarkAppearance.BLACK) }
        coVerify { settingsDataStore.saveTheme(Theme.DARK) }
    }

    @Test
    fun `onReaderThemeSelectionChanged reset to system keeps stored appearances`() = runTest {
        themeFlow.value = Theme.DARK.name
        lightAppearanceFlow.value = LightAppearance.SEPIA
        darkAppearanceFlow.value = DarkAppearance.BLACK
        viewModel = createViewModel()

        viewModel.onReaderThemeSelectionChanged(
            ReaderAppearanceSelection(
                themeMode = Theme.SYSTEM,
                lightAppearance = LightAppearance.SEPIA,
                darkAppearance = DarkAppearance.BLACK
            )
        )
        advanceUntilIdle()

        assertEquals(Theme.SYSTEM.name, themeFlow.value)
        assertEquals(LightAppearance.SEPIA, lightAppearanceFlow.value)
        assertEquals(DarkAppearance.BLACK, darkAppearanceFlow.value)
        coVerify(exactly = 0) { settingsDataStore.saveLightAppearance(any()) }
        coVerify(exactly = 0) { settingsDataStore.saveDarkAppearance(any()) }
        coVerify { settingsDataStore.saveTheme(Theme.SYSTEM) }
    }

    @Test
    fun `onArticleSearchQueryChange resets prior match state for a new search`() = runTest {
        viewModel = createViewModel()

        viewModel.onArticleSearchUpdateResults(totalMatches = 36, preferredMatch = 27)
        assertEquals(27, viewModel.articleSearchState.value.currentMatch)

        viewModel.onArticleSearchQueryChange("beef")

        assertEquals("beef", viewModel.articleSearchState.value.query)
        assertEquals(0, viewModel.articleSearchState.value.totalMatches)
        assertEquals(0, viewModel.articleSearchState.value.currentMatch)
    }

    @Test
    fun `onArticleSearchUpdateResults uses preferred viewport anchored match when none selected`() = runTest {
        viewModel = createViewModel()

        viewModel.onArticleSearchUpdateResults(totalMatches = 36, preferredMatch = 27)

        assertEquals(36, viewModel.articleSearchState.value.totalMatches)
        assertEquals(27, viewModel.articleSearchState.value.currentMatch)

        viewModel.onArticleSearchUpdateResults(totalMatches = 36, preferredMatch = 30)

        assertEquals(27, viewModel.articleSearchState.value.currentMatch)
    }

    @Test
    fun `reader end visibility is debounced before showing footer`() = runTest {
        viewModel = createViewModel()
        viewModel.uiState.take(2).toList()

        viewModel.updateReaderFooterGates(
            readerMode = true,
            hasDuplicateWideControls = false,
            selectionToolbarActive = false,
            annotationEditorActive = false,
            videoFullscreenActive = false
        )
        viewModel.onReaderEndVisibleChanged(true)
        advanceTimeBy(149)

        var state = viewModel.uiState.value as BookmarkDetailViewModel.UiState.Success
        assertFalse(state.endVisible)
        assertFalse(state.showFooter)

        advanceTimeBy(1)
        advanceUntilIdle()

        state = viewModel.uiState.value as BookmarkDetailViewModel.UiState.Success
        assertTrue(state.endVisible)
        assertTrue(state.showFooter)
    }

    @Test
    fun `reader footer show state honors suppression gates`() = runTest {
        viewModel = createViewModel()
        viewModel.uiState.take(2).toList()
        viewModel.onReaderEndVisibleChanged(true)
        advanceTimeBy(150)
        advanceUntilIdle()

        viewModel.updateReaderFooterGates(
            readerMode = false,
            hasDuplicateWideControls = false,
            selectionToolbarActive = false,
            annotationEditorActive = false,
            videoFullscreenActive = false
        )
        val originalModeState = viewModel.uiState.first {
            it is BookmarkDetailViewModel.UiState.Success &&
                !it.endVisible &&
                !it.footerEnabled &&
                !it.showFooter
        } as BookmarkDetailViewModel.UiState.Success
        assertFalse(originalModeState.endVisible)
        assertFalse(originalModeState.footerEnabled)
        assertFalse(originalModeState.showFooter)

        viewModel.onReaderEndVisibleChanged(true)
        advanceTimeBy(150)
        advanceUntilIdle()

        val gateCases = listOf(
            "wide duplicate controls" to ReaderFooterGateArgs(hasDuplicateWideControls = true),
            "selection toolbar" to ReaderFooterGateArgs(selectionToolbarActive = true),
            "annotation editor" to ReaderFooterGateArgs(annotationEditorActive = true),
            "video fullscreen" to ReaderFooterGateArgs(videoFullscreenActive = true)
        )

        gateCases.forEach { (name, gate) ->
            viewModel.updateReaderFooterGates(
                readerMode = true,
                hasDuplicateWideControls = false,
                selectionToolbarActive = false,
                annotationEditorActive = false,
                videoFullscreenActive = false
            )
            viewModel.uiState.first {
                it is BookmarkDetailViewModel.UiState.Success &&
                    it.endVisible &&
                    it.footerEnabled &&
                    it.showFooter
            }

            viewModel.updateReaderFooterGates(
                readerMode = gate.readerMode,
                hasDuplicateWideControls = gate.hasDuplicateWideControls,
                selectionToolbarActive = gate.selectionToolbarActive,
                annotationEditorActive = gate.annotationEditorActive,
                videoFullscreenActive = gate.videoFullscreenActive
            )

            val state = viewModel.uiState.first {
                it is BookmarkDetailViewModel.UiState.Success &&
                    it.endVisible &&
                    !it.footerEnabled
            } as BookmarkDetailViewModel.UiState.Success
            assertTrue("endVisible remains true for $name", state.endVisible)
            assertFalse("footer disabled for $name", state.footerEnabled)
            assertFalse("footer hidden for $name", state.showFooter)
        }
    }

    @Test
    fun `sentinel changes do not alter read progress persistence`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(bookmarkRepository, answers = false, recordedCalls = true)

        viewModel.onScrollProgressChanged(25)
        viewModel.onReaderEndVisibleChanged(true)
        advanceTimeBy(150)
        viewModel.onReaderEndVisibleChanged(false)
        advanceTimeBy(150)
        viewModel.saveProgressOnPause()
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.updateReadProgress("123", 25) }
    }

    @Test
    fun `article completion still runs through scroll progress and remains locked`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(bookmarkRepository, answers = false, recordedCalls = true)

        viewModel.onReaderEndVisibleChanged(true)
        advanceTimeBy(150)
        viewModel.onScrollProgressChanged(100)
        viewModel.onReaderEndVisibleChanged(false)
        advanceTimeBy(150)
        viewModel.onScrollProgressChanged(50)
        viewModel.saveProgressOnPause()
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.updateReadProgress("123", 100) }
    }

    @Test
    fun `picture bookmarks still auto mark read on open`() = runTest {
        val pictureBookmark = sampleBookmark.copy(
            type = Bookmark.Type.Picture,
            documentTpe = "photo",
            hasArticle = false,
            articleContent = null,
            readProgress = 0
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(pictureBookmark)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns pictureBookmark

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.updateReadProgress("123", 100) }
    }

    @Test
    fun `video bookmarks still auto mark read on open`() = runTest {
        val videoBookmark = sampleBookmark.copy(
            type = Bookmark.Type.Video,
            documentTpe = "video",
            hasArticle = false,
            articleContent = null,
            embed = "<iframe src=\"https://example.com/embed\"></iframe>",
            readProgress = 0
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(videoBookmark)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns videoBookmark

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.updateReadProgress("123", 100) }
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
        viewModel = createViewModel()
        viewModel.onToggleFavorite(bookmarkId, isFavorite)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Success(), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
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
    fun `onToggleArchivedBookmark queues archive state and saves on exit`() = runTest {
        // Arrange
        val bookmarkId = "123"
        val isArchived = true
        coEvery { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) } returns UpdateBookmarkUseCase.Result.Success
        every { bookmarkRepository.observeBookmark(bookmarkId) } returns MutableStateFlow(sampleBookmark)
        coEvery { assetLoader.loadAsset("html_template_light.html") } returns htmlTemplate

        // Act
        viewModel = createViewModel()
        viewModel.onToggleArchive(bookmarkId, isArchived)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val successState = uiStates.last() as BookmarkDetailViewModel.UiState.Success
        assertEquals(true, successState.bookmark.isArchived)
        
        // Ensure not yet called
        coVerify(exactly = 0) { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
        
        // Now trigger an exit
        viewModel.onClickBack()
        advanceUntilIdle()

        // Verify saved
        coVerify(exactly = 1) { updateBookmarkUseCase.updateIsArchived(bookmarkId, isArchived) }
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
        viewModel = createViewModel()
        viewModel.onToggleMarkRead(bookmarkId, isRead)
        advanceUntilIdle()

        val uiStates = viewModel.uiState.take(2).toList()
        val loadingState = uiStates[0]
        val successState = uiStates[1]
        assert(loadingState is BookmarkDetailViewModel.UiState.Loading)
        assert(successState is BookmarkDetailViewModel.UiState.Success)
        assertEquals(BookmarkDetailViewModel.UpdateBookmarkState.Success(), (successState as BookmarkDetailViewModel.UiState.Success).updateBookmarkState)
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
        viewModel = createViewModel()
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
        viewModel = createViewModel()
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
    fun `init uses text-only article fetch first for article bookmarks`() = runTest {
        // Arrange
        val bookmarkWithNotAttempted = sampleBookmark.copy(
            contentState = Bookmark.ContentState.NOT_ATTEMPTED,
            hasArticle = true,
            articleContent = null
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithNotAttempted)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmarkWithNotAttempted
        coEvery { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) } returns LoadArticleUseCase.Result.Success

        // Act
        viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        coVerify { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) }
        coVerify(exactly = 0) { loadContentPackageUseCase.execute(any(), any()) }
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
        coEvery { loadArticleUseCase.refreshCachedArticleIfAnnotationsChanged("123") } just Runs
        advanceUntilIdle()
        clearMocks(loadArticleUseCase, answers = false, recordedCalls = true)

        // Act
        viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { loadArticleUseCase.execute(any(), any()) }
        coVerify(exactly = 1) { loadArticleUseCase.refreshCachedArticleIfAnnotationsChanged("123") }
    }

    @Test
    fun `fetchAnnotations uses API data for membership but keeps rendered metadata and does not update freshness cache`() = runTest {
        advanceUntilIdle()
        clearMocks(settingsDataStore, readeckApi, answers = false, recordedCalls = true)
        val renderedAnnotations = listOf(
            Annotation(
                id = "existing",
                bookmarkId = "123",
                text = "Rendered text",
                color = "red",
                note = "Rendered note",
                created = ""
            )
        )
        coEvery {
            readeckApi.getAnnotations("123")
        } returns Response.success(
            listOf(
                AnnotationDto(
                    id = "existing",
                    start_selector = "/p[1]",
                    start_offset = 0,
                    end_selector = "/p[1]",
                    end_offset = 5,
                    created = "2024-01-20T12:00:00Z",
                    text = "API text",
                    color = "red"
                ),
                AnnotationDto(
                    id = "remote-only",
                    start_selector = "/p[2]",
                    start_offset = 0,
                    end_selector = "/p[2]",
                    end_offset = 4,
                    created = "2024-01-20T12:01:00Z",
                    text = "Remote"
                )
            )
        )

        viewModel.fetchAnnotations("123", renderedAnnotations)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Annotation(
                    id = "existing",
                    bookmarkId = "123",
                    text = "API text",
                    color = "red",
                    note = "Rendered note",
                    created = "2024-01-20T12:00:00Z"
                ),
                Annotation(
                    id = "remote-only",
                    bookmarkId = "123",
                    text = "Remote",
                    color = "yellow",
                    note = null,
                    created = "2024-01-20T12:01:00Z"
                )
            ),
            viewModel.annotationsState.value.annotations
        )
        coVerify(exactly = 0) { settingsDataStore.saveCachedAnnotationSnapshot(any(), any()) }
    }

    @Test
    fun `updating annotation attributes upserts snapshot without pruning existing cached annotations`() = runTest {
        advanceUntilIdle()
        clearMocks(cachedAnnotationDao, readeckApi, answers = false, recordedCalls = true)
        coEvery {
            readeckApi.updateAnnotation(
                bookmarkId = "123",
                annotationId = "selected",
                body = UpdateAnnotationDto(color = "green", note = "Updated")
            )
        } returns Response.success(Unit)
        coEvery { cachedAnnotationDao.getAnnotationsForBookmark("123") } returns listOf(
            CachedAnnotationEntity(
                id = "selected",
                bookmarkId = "123",
                text = "Selected cached text",
                color = "yellow",
                note = null,
                created = "2024-01-20T12:00:00Z"
            ),
            CachedAnnotationEntity(
                id = "other",
                bookmarkId = "123",
                text = "Other cached text",
                color = "yellow",
                note = null,
                created = "2024-01-20T12:01:00Z"
            )
        )
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(
            listOf(
                AnnotationDto(
                    id = "selected",
                    start_selector = "/p[1]",
                    start_offset = 0,
                    end_selector = "/p[1]",
                    end_offset = 5,
                    created = "2024-01-20T12:00:00Z",
                    text = "Selected API text",
                    color = "green",
                    note = "Updated"
                )
            )
        )

        viewModel.updateAnnotations(listOf("selected"), color = "green", note = "Updated")
        advanceUntilIdle()

        coVerify(exactly = 1) { cachedAnnotationDao.insertAnnotations(any()) }
        coVerify(exactly = 0) { cachedAnnotationDao.replaceAnnotationsForBookmark(any(), any()) }
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

        // Act
        viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = viewModel.contentLoadState.value
        assert(state is BookmarkDetailViewModel.ContentLoadState.Failed)
        val failedState = state as BookmarkDetailViewModel.ContentLoadState.Failed
        assertEquals("Source blocked content extraction", failedState.reason)
        assertEquals(false, failedState.canRetry)
        coVerify(exactly = 0) { loadArticleUseCase.execute(any(), any()) }
    }

    @Test
    fun `init sets contentLoadState to Failed when video contentState is PERMANENT_NO_CONTENT`() = runTest {
        val videoBookmarkWithNoContent = sampleBookmark.copy(
            type = Bookmark.Type.Video,
            documentTpe = "video",
            hasArticle = false,
            articleContent = null,
            embed = null,
            readProgress = 100,
            contentState = Bookmark.ContentState.PERMANENT_NO_CONTENT,
            contentFailureReason = "No video reader payload available"
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(videoBookmarkWithNoContent)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns videoBookmarkWithNoContent
        coEvery { bookmarkRepository.refreshBookmarkMetadata("123") } just io.mockk.Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.contentLoadState.value
        assert(state is BookmarkDetailViewModel.ContentLoadState.Failed)
        val failedState = state as BookmarkDetailViewModel.ContentLoadState.Failed
        assertEquals("No video reader payload available", failedState.reason)
        assertEquals(false, failedState.canRetry)
        coVerify(exactly = 0) { loadArticleUseCase.execute(any(), any()) }
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
        coEvery { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) } returns
            LoadArticleUseCase.Result.PermanentFailure("Article extraction not supported for this site")
        coEvery { loadContentPackageUseCase.execute("123", any()) } returns
            LoadContentPackageUseCase.Result.PermanentFailure("not available")

        // Act
        viewModel = createViewModel()
        advanceUntilIdle()

        // Assert
        val state = viewModel.contentLoadState.value
        assert(state is BookmarkDetailViewModel.ContentLoadState.Failed)
        val failedState = state as BookmarkDetailViewModel.ContentLoadState.Failed
        assertEquals("Article extraction not supported for this site", failedState.reason)
        assertEquals(false, failedState.canRetry)
    }

    @Test
    fun `showCreateAnnotationSheet stores selection data`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18
        )

        viewModel.showCreateAnnotationSheet(selectionData)

        val state = viewModel.annotationEditState.value
        assertEquals(null, state?.annotationId)
        assertEquals("yellow", state?.color)
        assertEquals(selectionData, state?.selectionData)
        assertEquals("Selected text", state?.text)
    }

    @Test
    fun `showCreateAnnotationSheet treats overlapping highlights as edit state`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18,
            selectedAnnotationIds = listOf("annotation-1", "annotation-2")
        )
        val existingAnnotations = listOf(
            Annotation(
                id = "annotation-1",
                bookmarkId = "123",
                text = "Existing highlight 1",
                color = "green",
                note = "First note",
                created = "2026-03-09T10:00:00Z"
            ),
            Annotation(
                id = "annotation-2",
                bookmarkId = "123",
                text = "Existing highlight 2",
                color = "green",
                note = "Second note",
                created = "2026-03-09T10:05:00Z"
            )
        )

        viewModel.showCreateAnnotationSheet(selectionData, existingAnnotations)

        val state = viewModel.annotationEditState.value
        assertEquals(listOf("annotation-1", "annotation-2"), state?.annotationIds)
        assertEquals("green", state?.color)
        assertNull(state?.selectionData)
        assertEquals("Selected text", state?.text)
        assertEquals("", state?.noteText)
        assertEquals(true, state?.hasMixedNotes)
    }

    @Test
    fun `showEditAnnotationSheet seeds current annotation`() = runTest {
        val annotation = Annotation(
            id = "annotation-1",
            bookmarkId = "123",
            text = "Existing highlight",
            color = "green",
            note = "Saved note",
            created = "2026-03-09T10:00:00Z"
        )

        viewModel.showEditAnnotationSheet(annotation)

        val state = viewModel.annotationEditState.value
        assertEquals("annotation-1", state?.annotationId)
        assertEquals("green", state?.color)
        assertNull(state?.selectionData)
        assertEquals("Existing highlight", state?.text)
        assertEquals("Saved note", state?.noteText)
        assertEquals(false, state?.hasMixedNotes)
    }

    @Test
    fun `saveAnnotationEdit creates annotation patches note and refreshes article content`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18
        )
        val createdAnnotation = AnnotationDto(
            id = "annotation-1",
            start_selector = selectionData.startSelector,
            start_offset = selectionData.startOffset,
            end_selector = selectionData.endSelector,
            end_offset = selectionData.endOffset,
            created = "2026-03-09T10:00:00Z",
            text = selectionData.text
        )
        val updateBody = slot<UpdateAnnotationDto>()

        coEvery {
            readeckApi.createAnnotation("123", any())
        } returns Response.success(createdAnnotation)
        coEvery {
            readeckApi.updateAnnotation("123", "annotation-1", capture(updateBody))
        } returns Response.success(Unit)
        coEvery { contentPackageManager.hasResources("123") } returns true
        coEvery { loadContentPackageUseCase.refreshHtmlForAnnotations("123") } returns "<p>refreshed</p>"
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())

        viewModel.showCreateAnnotationSheet(selectionData)
        viewModel.onAnnotationEditColorSelected("red")
        viewModel.onAnnotationEditNoteChanged("New note")
        viewModel.saveAnnotationEdit()
        advanceUntilIdle()

        assertNull(viewModel.annotationEditState.value)
        coVerify { readeckApi.createAnnotation("123", any()) }
        coVerify { readeckApi.updateAnnotation("123", "annotation-1", any()) }
        assertEquals("red", updateBody.captured.color)
        assertEquals("New note", updateBody.captured.note)
        coVerify { loadContentPackageUseCase.refreshHtmlForAnnotations("123") }
    }

    @Test
    fun `saveAnnotationEdit updates overlapping annotations and refreshes article content`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18,
            selectedAnnotationIds = listOf("annotation-1", "annotation-2")
        )
        val existingAnnotations = listOf(
            Annotation(
                id = "annotation-1",
                bookmarkId = "123",
                text = "Existing highlight 1",
                color = "yellow",
                note = null,
                created = "2026-03-09T10:00:00Z"
            ),
            Annotation(
                id = "annotation-2",
                bookmarkId = "123",
                text = "Existing highlight 2",
                color = "yellow",
                note = null,
                created = "2026-03-09T10:05:00Z"
            )
        )

        val updateBodies = mutableListOf<UpdateAnnotationDto>()
        coEvery { readeckApi.updateAnnotation("123", "annotation-1", capture(updateBodies)) } returns Response.success(Unit)
        coEvery { readeckApi.updateAnnotation("123", "annotation-2", capture(updateBodies)) } returns Response.success(Unit)
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())
        coEvery {
            contentPackageManager.getCachedReaderHtml("123")
        } returns """<rd-annotation id="annotation-1" data-annotation-id-value="annotation-1" data-annotation-color="yellow">one</rd-annotation><rd-annotation id="annotation-2" data-annotation-id-value="annotation-2" data-annotation-color="yellow" title="old" data-annotation-note="true">two</rd-annotation>"""

        viewModel.showCreateAnnotationSheet(selectionData, existingAnnotations)
        viewModel.onAnnotationEditColorSelected("red")
        viewModel.onAnnotationEditNoteChanged("Shared note")
        viewModel.saveAnnotationEdit()
        advanceUntilIdle()

        assertNull(viewModel.annotationEditState.value)
        coVerify { readeckApi.updateAnnotation("123", "annotation-1", any()) }
        coVerify { readeckApi.updateAnnotation("123", "annotation-2", any()) }
        assertEquals(listOf("Shared note", "Shared note"), updateBodies.map { it.note })
        coVerify {
            contentPackageManager.updateCachedReaderHtml(
                "123",
                match { html ->
                    html.contains("""data-annotation-color="red"""") &&
                        html.contains("""title="Shared note"""") &&
                        html.contains("""data-annotation-note="true"""")
                }
            )
        }
        coVerify(exactly = 0) { readeckApi.createAnnotation(any(), any()) }
        // Color changes use JS-only path, no full content refresh
        coVerify(exactly = 0) { loadContentPackageUseCase.executeForceRefresh("123") }
        coVerify { readeckApi.getAnnotations("123") }
    }

    @Test
    fun `saveAnnotationEdit clears existing annotation note with blank text`() = runTest {
        val annotation = Annotation(
            id = "annotation-1",
            bookmarkId = "123",
            text = "Existing highlight",
            color = "yellow",
            note = "Old note",
            created = "2026-03-09T10:00:00Z"
        )
        val updateBody = slot<UpdateAnnotationDto>()

        coEvery {
            readeckApi.updateAnnotation("123", "annotation-1", capture(updateBody))
        } returns Response.success(Unit)
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())
        coEvery {
            contentPackageManager.getCachedReaderHtml("123")
        } returns """<rd-annotation id="annotation-1" data-annotation-id-value="annotation-1" data-annotation-color="yellow" title="Old note" data-annotation-note="true">one</rd-annotation>"""

        viewModel.showEditAnnotationSheet(annotation)
        viewModel.onAnnotationEditNoteChanged("")
        viewModel.saveAnnotationEdit()
        advanceUntilIdle()

        assertEquals("", updateBody.captured.note)
        coVerify {
            contentPackageManager.updateCachedReaderHtml(
                "123",
                match { html ->
                    !html.contains("title=") &&
                        !html.contains("data-annotation-note") &&
                        html.contains("""data-annotation-color="yellow"""")
                }
            )
        }
    }

    @Test
    fun `saveAnnotationEdit writes note marker only on final cached segment`() = runTest {
        val annotation = Annotation(
            id = "annotation-1",
            bookmarkId = "123",
            text = "Split highlight",
            color = "yellow",
            note = null,
            created = "2026-03-09T10:00:00Z"
        )

        coEvery { readeckApi.updateAnnotation("123", "annotation-1", any()) } returns Response.success(Unit)
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())
        coEvery {
            contentPackageManager.getCachedReaderHtml("123")
        } returns """<rd-annotation id="annotation-1" data-annotation-id-value="annotation-1" data-annotation-color="yellow">one</rd-annotation><rd-annotation data-annotation-id-value="annotation-1" data-annotation-color="yellow">two</rd-annotation>"""

        viewModel.showEditAnnotationSheet(annotation)
        viewModel.onAnnotationEditNoteChanged("Segment note")
        viewModel.saveAnnotationEdit()
        advanceUntilIdle()

        coVerify {
            contentPackageManager.updateCachedReaderHtml(
                "123",
                match { html ->
                    Regex("""data-annotation-note="true"""").findAll(html).count() == 1 &&
                        Regex("""title="Segment note"""").findAll(html).count() == 1 &&
                        html.contains(""">one</rd-annotation><rd-annotation data-annotation-id-value="annotation-1" data-annotation-color="yellow" title="Segment note" data-annotation-note="true">two</rd-annotation>""")
                }
            )
        }
    }

    @Test
    fun `deleteCurrentAnnotation deletes overlapping annotations and refreshes article content`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18,
            selectedAnnotationIds = listOf("annotation-1", "annotation-2")
        )

        coEvery { readeckApi.deleteAnnotation("123", "annotation-1") } returns Response.success(Unit)
        coEvery { readeckApi.deleteAnnotation("123", "annotation-2") } returns Response.success(Unit)
        coEvery { contentPackageManager.hasResources("123") } returns true
        coEvery { loadContentPackageUseCase.refreshHtmlForAnnotations("123") } returns "<p>refreshed</p>"
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())

        viewModel.showCreateAnnotationSheet(selectionData)
        viewModel.deleteCurrentAnnotation()
        advanceUntilIdle()

        assertNull(viewModel.annotationEditState.value)
        coVerify { readeckApi.deleteAnnotation("123", "annotation-1") }
        coVerify { readeckApi.deleteAnnotation("123", "annotation-2") }
        coVerify { loadContentPackageUseCase.refreshHtmlForAnnotations("123") }
    }

    @Test
    fun `saveAnnotationEdit refreshes cached article html for text-only content`() = runTest {
        val selectionData = SelectionData(
            text = "Selected text",
            startSelector = "/section[1]/p[1]",
            startOffset = 5,
            endSelector = "/section[1]/p[1]",
            endOffset = 18
        )
        val createdAnnotation = AnnotationDto(
            id = "annotation-1",
            start_selector = selectionData.startSelector,
            start_offset = selectionData.startOffset,
            end_selector = selectionData.endSelector,
            end_offset = selectionData.endOffset,
            color = "red",
            created = "2026-03-09T10:00:00Z",
            text = selectionData.text
        )

        coEvery {
            readeckApi.createAnnotation("123", any())
        } returns Response.success(createdAnnotation)
        coEvery { contentPackageManager.hasResources("123") } returns false
        coEvery { loadArticleUseCase.refreshHtmlForAnnotations("123") } returns "<p>refreshed</p>"
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showCreateAnnotationSheet(selectionData)
        viewModel.onAnnotationEditColorSelected("red")
        viewModel.saveAnnotationEdit()
        advanceUntilIdle()

        coVerify { loadArticleUseCase.refreshHtmlForAnnotations("123") }
        coVerify(exactly = 0) { loadContentPackageUseCase.refreshHtmlForAnnotations("123") }
        coVerify(exactly = 0) { loadContentPackageUseCase.executeForceRefresh("123") }
    }

    @Test
    fun `init does not enqueue priority package for archived bookmark outside offline scope`() = runTest {
        val archivedBookmark = sampleBookmark.copy(
            contentState = Bookmark.ContentState.NOT_ATTEMPTED,
            hasArticle = true,
            articleContent = null,
            isArchived = true
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(archivedBookmark)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns archivedBookmark
        coEvery { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) } returns LoadArticleUseCase.Result.Success
        coEvery { contentPackageManager.hasResources("123") } returns false
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST

        viewModel = createViewModel()
        advanceUntilIdle()

        io.mockk.verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<androidx.work.ExistingWorkPolicy>(),
                any<androidx.work.OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `init enqueues priority package after text-only article fetch when offline reading is enabled`() = runTest {
        val bookmark = sampleBookmark.copy(
            contentState = Bookmark.ContentState.NOT_ATTEMPTED,
            hasArticle = true,
            articleContent = null
        )
        every { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmark)
        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmark
        coEvery { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) } returns
            LoadArticleUseCase.Result.Success
        coEvery { contentPackageManager.hasResources("123") } returns false
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true
        coEvery { settingsDataStore.getOfflineContentScope() } returns OfflineContentScope.MY_LIST_AND_ARCHIVED
        coEvery { settingsDataStore.getContentSyncConstraints() } returns
            ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = true)

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { loadArticleUseCase.execute("123", markDirtyAfterSuccess = false) }
        coVerify(exactly = 0) { loadContentPackageUseCase.execute(any(), any()) }
        io.mockk.verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<androidx.work.ExistingWorkPolicy>(),
                any<androidx.work.OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `forceRefreshContent refreshes downloaded article content`() = runTest {
        coEvery { loadContentPackageUseCase.executeForceRefresh("123") } returns LoadContentPackageUseCase.Result.Success
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(emptyList())

        viewModel.forceRefreshContent()
        advanceUntilIdle()

        coVerify { loadContentPackageUseCase.executeForceRefresh("123") }
        coVerify { readeckApi.getAnnotations("123") }
    }

    @Test
    fun `detail bookmark hides header description when omitDescription is true and article content exists`() {
        val bookmark = BookmarkDetailViewModel.Bookmark(
            url = "https://example.com",
            title = "Title",
            authors = emptyList(),
            createdDate = "March 17, 2026",
            publishedDate = null,
            publishedDateInput = null,
            bookmarkId = "123",
            siteName = "Example",
            imgSrc = "",
            iconSrc = "",
            thumbnailSrc = "",
            isFavorite = false,
            isArchived = false,
            isRead = false,
            type = BookmarkDetailViewModel.Bookmark.Type.ARTICLE,
            articleContent = "<p>Article</p>",
            embed = null,
            lang = "en",
            textDirection = "ltr",
            wordCount = null,
            readingTime = null,
            description = "Description",
            omitDescription = true,
            labels = emptyList(),
            readProgress = 0,
            hasContent = true
        )

        assertFalse(bookmark.shouldShowHeaderDescription())
    }

    @Test
    fun `detail bookmark keeps header description for photo fallback when omitDescription is true`() {
        val bookmark = BookmarkDetailViewModel.Bookmark(
            url = "https://example.com",
            title = "Title",
            authors = emptyList(),
            createdDate = "March 17, 2026",
            publishedDate = null,
            publishedDateInput = null,
            bookmarkId = "123",
            siteName = "Example",
            imgSrc = "",
            iconSrc = "",
            thumbnailSrc = "",
            isFavorite = false,
            isArchived = false,
            isRead = false,
            type = BookmarkDetailViewModel.Bookmark.Type.PHOTO,
            articleContent = null,
            embed = null,
            lang = "en",
            textDirection = "ltr",
            wordCount = null,
            readingTime = null,
            description = "Description",
            omitDescription = true,
            labels = emptyList(),
            readProgress = 0,
            hasContent = true
        )

        assertTrue(bookmark.shouldShowHeaderDescription())
    }

    @Test
    fun `article reader content replaces html footer with end sentinel`() {
        val content = detailBookmark(
            type = BookmarkDetailViewModel.Bookmark.Type.ARTICLE,
            articleContent = "<p>Article</p>"
        ).getContent(Template.SimpleTemplate("<html><body>%s</body></html>"), isDark = false)

        assertNotNull(content)
        assertTrue(content!!.contains("<div id=\"rd-article-content\"><p>Article</p></div><div id=\"mydeck-end-sentinel\""))
        assertFalse(content.contains("mydeck-footer"))
        assertFalse(content.contains("MyDeckActions"))
    }

    @Test
    fun `photo reader content places end sentinel after image and caption`() {
        val content = detailBookmark(
            type = BookmarkDetailViewModel.Bookmark.Type.PHOTO,
            articleContent = "<p>Caption</p>",
            imgSrc = "image.jpg"
        ).getContent(Template.SimpleTemplate("<html><body>%s</body></html>"), isDark = false)

        assertNotNull(content)
        assertTrue(content!!.contains("<img src=\"image.jpg\"/><p>Caption</p><div id=\"mydeck-end-sentinel\""))
        assertFalse(content.contains("mydeck-footer"))
        assertFalse(content.contains("MyDeckActions"))
    }

    @Test
    fun `video reader content places end sentinel after transcript content`() {
        val content = detailBookmark(
            type = BookmarkDetailViewModel.Bookmark.Type.VIDEO,
            articleContent = "<p>Transcript</p>",
            embed = "<iframe src=\"https://example.com/embed\"></iframe>"
        ).getContent(Template.SimpleTemplate("<html><body>%s</body></html>"), isDark = false)

        assertNotNull(content)
        val articleIndex = content!!.indexOf("<div id=\"rd-article-content\"><p>Transcript</p></div>")
        val sentinelIndex = content.indexOf("<div id=\"mydeck-end-sentinel\"")
        assertTrue(articleIndex >= 0)
        assertTrue(sentinelIndex > articleIndex)
        assertFalse(content.contains("mydeck-footer"))
        assertFalse(content.contains("MyDeckActions"))
    }

    // ── Extraction error / log ────────────────────────────────────────────────

    @Test
    fun `uiState exposes extraction errors and logSrc from domain bookmark`() = runTest {
        val bookmarkWithErrors = sampleBookmark.copy(
            errors = listOf("could not extract content"),
            log = Bookmark.Resource("/api/bookmarks/123/log"),
            state = Bookmark.State.ERROR
        )
        coEvery { bookmarkRepository.observeBookmark("123") } returns MutableStateFlow(bookmarkWithErrors)
        viewModel = createViewModel()

        val uiStates = viewModel.uiState.take(2).toList()
        val state = uiStates[1] as? BookmarkDetailViewModel.UiState.Success

        assertNotNull(state)
        assertEquals(listOf("could not extract content"), state!!.bookmark.errors)
        assertTrue(state.bookmark.hasExtractionError)
        assertEquals("/api/bookmarks/123/log", state.bookmark.logSrc)
    }

    @Test
    fun `onViewExtractionLog transitions through loading to success`() = runTest {
        coEvery { bookmarkRepository.fetchExtractionLog("123") } returns BookmarkRepository.ExtractionLogResult.Success("INFO: done")
        viewModel = createViewModel()

        viewModel.uiState.take(2).toList()
        viewModel.onViewExtractionLog()
        testDispatcher.scheduler.advanceUntilIdle()

        val logState = viewModel.extractionLogState.value
        assertTrue(logState.visible)
        assertEquals("INFO: done", logState.text)
        assertNull(logState.errorMessage)
    }

    @Test
    fun `failed log fetch shows error state without disrupting details screen`() = runTest {
        coEvery { bookmarkRepository.fetchExtractionLog("123") } returns BookmarkRepository.ExtractionLogResult.NetworkError
        viewModel = createViewModel()

        viewModel.uiState.take(2).toList()
        viewModel.onViewExtractionLog()
        testDispatcher.scheduler.advanceUntilIdle()

        val logState = viewModel.extractionLogState.value
        assertTrue(logState.visible)
        assertNotNull(logState.errorMessage)
        assertNull(logState.text)
        assertTrue(viewModel.uiState.value is BookmarkDetailViewModel.UiState.Success)
    }

    private data class ReaderFooterGateArgs(
        val readerMode: Boolean = true,
        val hasDuplicateWideControls: Boolean = false,
        val selectionToolbarActive: Boolean = false,
        val annotationEditorActive: Boolean = false,
        val videoFullscreenActive: Boolean = false
    )

    private fun detailBookmark(
        type: BookmarkDetailViewModel.Bookmark.Type,
        articleContent: String?,
        embed: String? = null,
        imgSrc: String = "",
        description: String = ""
    ) = BookmarkDetailViewModel.Bookmark(
        url = "https://example.com",
        title = "Title",
        authors = emptyList(),
        createdDate = "March 17, 2026",
        publishedDate = null,
        publishedDateInput = null,
        bookmarkId = "123",
        siteName = "Example",
        imgSrc = imgSrc,
        iconSrc = "",
        thumbnailSrc = "",
        isFavorite = false,
        isArchived = false,
        isRead = false,
        type = type,
        articleContent = articleContent,
        embed = embed,
        lang = "en",
        textDirection = "ltr",
        wordCount = null,
        readingTime = null,
        description = description,
        omitDescription = false,
        labels = emptyList(),
        readProgress = 0,
        hasContent = true
    )

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
