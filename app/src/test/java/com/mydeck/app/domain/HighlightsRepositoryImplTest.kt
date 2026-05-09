package com.mydeck.app.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.ImageResourceEntity
import com.mydeck.app.io.db.model.ResourceEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationSummaryDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class HighlightsRepositoryImplTest {
    private lateinit var database: MyDeckDatabase
    private lateinit var readeckApi: ReadeckApi
    private lateinit var repository: HighlightsRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MyDeckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        readeckApi = mockk()
        repository = HighlightsRepositoryImpl(
            readeckApi = readeckApi,
            cachedAnnotationDao = database.getCachedAnnotationDao(),
            database = database,
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `mid-refresh failure keeps existing cache and upserts completed pages`() = runTest {
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        database.getCachedAnnotationDao().insertAnnotations(
            listOf(annotation("cached-old", "bookmark-1", "Cached old"))
        )
        val firstPage = (0 until 50).map { index ->
            remoteAnnotation("remote-$index", "bookmark-1", "Remote $index")
        }
        coEvery {
            readeckApi.getAnnotationSummaries(limit = 50, offset = 0)
        } returns Response.success(firstPage)
        coEvery {
            readeckApi.getAnnotationSummaries(limit = 50, offset = 50)
        } returns Response.error(500, "error".toResponseBody(null))

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)

        val highlights = repository.observeHighlights().first()
        assertTrue(result.isFailure)
        assertTrue(highlights.any { it.id == "cached-old" })
        assertTrue(highlights.any { it.id == "remote-0" })
        assertEquals(51, highlights.size)
    }

    @Test
    fun `successful refresh deletes stale rows after reconciliation`() = runTest {
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        database.getCachedAnnotationDao().insertAnnotations(
            listOf(
                annotation("stale", "bookmark-1", "Stale"),
                annotation("keep", "bookmark-1", "Old keep")
            )
        )
        coEvery {
            readeckApi.getAnnotationSummaries(limit = 50, offset = 0)
        } returns Response.success(listOf(remoteAnnotation("keep", "bookmark-1", "Updated keep")))

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)

        val highlights = repository.observeHighlights().first()
        assertTrue(result.isSuccess)
        assertEquals(listOf("keep"), highlights.map { it.id })
        assertEquals("Updated keep", highlights.single().text)
    }

    @Test
    fun `successful empty remote result clears local highlights`() = runTest {
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        database.getCachedAnnotationDao().insertAnnotations(
            listOf(annotation("cached-old", "bookmark-1", "Cached old"))
        )
        coEvery {
            readeckApi.getAnnotationSummaries(limit = 50, offset = 0)
        } returns Response.success(emptyList())

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), repository.observeHighlights().first().map { it.id })
    }

    private fun bookmark(id: String): BookmarkEntity {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return BookmarkEntity(
            id = id,
            href = "https://example.com/$id",
            created = now,
            updated = now,
            state = BookmarkEntity.State.LOADED,
            loaded = true,
            url = "https://example.com/$id",
            title = "Bookmark $id",
            siteName = "Example",
            site = "example.com",
            authors = emptyList(),
            lang = "en",
            textDirection = "ltr",
            documentTpe = "article",
            type = BookmarkEntity.Type.ARTICLE,
            hasArticle = true,
            description = "",
            isDeleted = false,
            isMarked = false,
            isArchived = false,
            labels = emptyList(),
            readProgress = 0,
            wordCount = null,
            readingTime = null,
            published = null,
            embed = null,
            embedHostname = null,
            article = ResourceEntity(""),
            icon = ImageResourceEntity("", 0, 0),
            image = ImageResourceEntity("", 0, 0),
            log = ResourceEntity(""),
            props = ResourceEntity(""),
            thumbnail = ImageResourceEntity("", 0, 0),
        )
    }

    private fun annotation(id: String, bookmarkId: String, text: String): CachedAnnotationEntity {
        return CachedAnnotationEntity(
            id = id,
            bookmarkId = bookmarkId,
            text = text,
            color = "yellow",
            note = null,
            created = "2026-01-01T00:00:00Z",
        )
    }

    private fun remoteAnnotation(id: String, bookmarkId: String, text: String): AnnotationSummaryDto {
        return AnnotationSummaryDto(
            id = id,
            href = "https://example.com/annotations/$id",
            text = text,
            color = "yellow",
            note = "",
            created = "2026-01-02T00:00:00Z",
            bookmark_id = bookmarkId,
            bookmark_href = "https://example.com/bookmarks/$bookmarkId",
            bookmark_url = "https://example.com/$bookmarkId",
            bookmark_title = "Bookmark $bookmarkId",
            bookmark_site_name = "Example",
        )
    }
}
