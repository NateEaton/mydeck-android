package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LoadArticleUseCaseTest {

    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var readeckApi: ReadeckApi
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loadArticleUseCase: LoadArticleUseCase
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        bookmarkRepository = mockk()
        readeckApi = mockk()
        bookmarkDao = mockk()
        connectivityMonitor = mockk()
        settingsDataStore = mockk()

        loadArticleUseCase = LoadArticleUseCase(
            bookmarkRepository = bookmarkRepository,
            readeckApi = readeckApi,
            bookmarkDao = bookmarkDao,
            connectivityMonitor = connectivityMonitor,
            settingsDataStore = settingsDataStore,
            json = json
        )
    }

    @Test
    fun `refreshCachedArticleIfAnnotationsChanged refreshes article using cached article table content`() {
        val bookmark = sampleBookmark.copy(
            articleContent = null,
            contentState = Bookmark.ContentState.DOWNLOADED
        )
        val cachedArticleContent = """
            <section>
              <p><rd-annotation data-annotation-id-value="old">Old</rd-annotation></p>
            </section>
        """.trimIndent()
        val refreshedArticleContent = "<section><p>Fresh article</p></section>"
        val annotations = listOf(
            AnnotationDto(
                id = "annotation-1",
                start_selector = "/p[1]",
                start_offset = 0,
                end_selector = "/p[1]",
                end_offset = 4,
                created = "2026-03-09T13:00:00Z",
                text = "Fresh"
            )
        )

        coEvery { bookmarkRepository.getBookmarkById("123") } returns bookmark
        coEvery { bookmarkDao.getArticleContent("123") } returns cachedArticleContent
        every { connectivityMonitor.isNetworkAvailable() } returns true
        coEvery { settingsDataStore.getCachedAnnotationSnapshot("123") } returns "stale-snapshot"
        coEvery { settingsDataStore.saveCachedAnnotationSnapshot("123", any()) } just Runs
        coEvery { readeckApi.getAnnotations("123") } returns Response.success(annotations)
        coEvery { readeckApi.getArticle("123") } returns Response.success(refreshedArticleContent)
        coEvery { bookmarkRepository.insertBookmarks(any()) } just Runs

        kotlinx.coroutines.runBlocking {
            loadArticleUseCase.refreshCachedArticleIfAnnotationsChanged("123")
        }

        coVerify(exactly = 1) { readeckApi.getArticle("123") }
        coVerify {
            bookmarkRepository.insertBookmarks(
                match { bookmarks ->
                    bookmarks.singleOrNull()?.articleContent == refreshedArticleContent
                }
            )
        }
        coVerify(exactly = 1) { settingsDataStore.saveCachedAnnotationSnapshot("123", any()) }
    }

    private val sampleBookmark = Bookmark(
        id = "123",
        href = "https://example.com/bookmark/123",
        created = LocalDateTime(2026, 3, 9, 8, 0, 0),
        updated = LocalDateTime(2026, 3, 9, 8, 0, 0),
        state = Bookmark.State.LOADED,
        loaded = true,
        url = "https://example.com/article",
        title = "Sample Article",
        siteName = "Example",
        site = "example.com",
        authors = listOf("Author"),
        lang = "en",
        textDirection = "ltr",
        documentTpe = "article",
        type = Bookmark.Type.Article,
        hasArticle = true,
        description = "Description",
        isDeleted = false,
        isMarked = false,
        isArchived = false,
        labels = emptyList(),
        readProgress = 0,
        wordCount = 100,
        readingTime = 1,
        published = null,
        embed = null,
        embedHostname = null,
        article = Bookmark.Resource("https://example.com/article"),
        articleContent = null,
        icon = Bookmark.ImageResource("", 0, 0),
        image = Bookmark.ImageResource("", 0, 0),
        log = Bookmark.Resource(""),
        props = Bookmark.Resource(""),
        thumbnail = Bookmark.ImageResource("", 0, 0),
        contentState = Bookmark.ContentState.DOWNLOADED,
        contentFailureReason = null
    )
}
