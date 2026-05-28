package com.mydeck.app.io.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.BookmarkWithArticleContent
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.ImageResourceEntity
import com.mydeck.app.io.db.model.RemoteBookmarkIdEntity
import com.mydeck.app.io.db.model.ResourceEntity
import com.mydeck.app.test.logging.replaceDebugTree
import com.mydeck.app.test.logging.restoreDebugTree
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import timber.log.Timber

@RunWith(Enclosed::class)
class BookmarkDaoTest {
    internal abstract class BaseTest {
        lateinit var bookmarkDao: BookmarkDao
        lateinit var cachedAnnotationDao: CachedAnnotationDao
        private lateinit var db: MyDeckDatabase
        val testDispatcher = StandardTestDispatcher()
        val remoteSyncRunId = "remote-sync-run"

        @Before
        fun setup() {
            replaceDebugTree()
            val context: Context = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(context, MyDeckDatabase::class.java)
                .allowMainThreadQueries().build()
            bookmarkDao = db.getBookmarkDao()
            cachedAnnotationDao = db.getCachedAnnotationDao()
            generateTestData()
        }

        @After
         fun tearDown() {
            db.close()
            restoreDebugTree()
        }

        private fun generateTestData() = runTest {
            val startDate = LocalDate(2025, 1, 1)
            val bookmarkEntities = (0 until 30).map { index ->
                val currentDate = startDate.plus(index.toLong(), kotlinx.datetime.DateTimeUnit.DAY)
                val type = when (index) {
                    in 0..9 -> BookmarkEntity.Type.ARTICLE
                    in 10..19 -> BookmarkEntity.Type.VIDEO
                    in 20..29 -> BookmarkEntity.Type.PHOTO
                    else -> {
                        BookmarkEntity.Type.ARTICLE
                    }
                }
                val state = when (index) {
                    9, 19, 29 -> BookmarkEntity.State.ERROR
                    8, 18, 28 -> BookmarkEntity.State.LOADING
                    else -> BookmarkEntity.State.LOADED
                }
                BookmarkEntity(
                    id = "test-$index",
                    href = "http://example.com/$index",
                    created = currentDate.atStartOfDayIn(TimeZone.UTC),
                    updated = currentDate.atStartOfDayIn(TimeZone.UTC),
                    state = state,
                    loaded = true,
                    url = "http://example.com/$index",
                    title = "Test Bookmark $index",
                    siteName = "Example",
                    site = "example.com",
                    authors = listOf("Author $index"),
                    lang = "en",
                    textDirection = "ltr",
                    documentTpe = type.value.lowercase(),
                    type = type,
                    hasArticle = true,
                    description = "Description for bookmark $index",
                    isDeleted = false,
                    isMarked = false,
                    isArchived = false,
                    labels = listOf("label1", "label2"),
                    readProgress = index * 3,
                    wordCount = 100 + index * 10,
                    readingTime = 5 + index,
                    published = null,
                    embed = null,
                    embedHostname = null,
                    article = ResourceEntity(""),
                    icon = ImageResourceEntity("", 50, 50),
                    image = ImageResourceEntity("", 200, 100),
                    log = ResourceEntity(""),
                    props = ResourceEntity(""),
                    thumbnail = ImageResourceEntity("", 100, 100),
                    hasServerErrors = index == 7 || index == 17 || index == 27,
                    errors = if (index == 7 || index == 17 || index == 27) {
                        listOf("extract failed")
                    } else {
                        emptyList()
                    }
                )
            }
            val bookmarkArticles = bookmarkEntities.map {
                BookmarkWithArticleContent(
                    bookmark = it,
                    articleContent = if (it.type == BookmarkEntity.Type.ARTICLE) {
                        ArticleContentEntity(bookmarkId = it.id, content = "content")
                    } else {
                        null
                    }
                )
            }
            bookmarkDao.insertBookmarksWithArticleContent(bookmarkArticles)
            val ids = bookmarkArticles.map { RemoteBookmarkIdEntity(remoteSyncRunId, it.bookmark.id) }
                .filterNot { it.id == "test-1" || it.id == "test-11" || it.id == "test-21" }
           bookmarkDao.insertRemoteBookmarkIds(ids + RemoteBookmarkIdEntity(remoteSyncRunId, "not-a-bookmark"))
        }
    }

    @RunWith(ParameterizedRobolectricTestRunner::class)
    internal class GetBookmarkListItemsByFiltersTest(private val parameter: ParameterType) :
        BaseTest() {

        companion object {
            @JvmStatic
            @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
            fun data(): List<ParameterType> = listOf(
                ParameterType(BookmarkEntity.Type.ARTICLE),
                ParameterType(BookmarkEntity.Type.PHOTO),
                ParameterType(BookmarkEntity.Type.VIDEO),
            )
        }

        data class ParameterType(val type: BookmarkEntity.Type)

        @Test
        fun testFilterArticles() = runTest(testDispatcher) {
            val flow = bookmarkDao.getBookmarkListItemsByFilters(parameter.type)
            val list = flow.first()
            assertEquals(10, list.size)
            list.forEach {
                assertEquals(parameter.type, it.type)
            }
        }

    }

    @RunWith(ParameterizedRobolectricTestRunner::class)
    internal class GetBookmarksByFiltersTest(private val parameter: ParameterType) : BaseTest() {

        companion object {
            @JvmStatic
            @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
            fun data(): List<ParameterType> = listOf(
                ParameterType(BookmarkEntity.Type.ARTICLE),
                ParameterType(BookmarkEntity.Type.PHOTO),
                ParameterType(BookmarkEntity.Type.VIDEO),
            )
        }

        data class ParameterType(val type: BookmarkEntity.Type)

        @Test
        fun testFilterArticles() = runTest(testDispatcher) {
            val flow = bookmarkDao.getBookmarksByFilters(parameter.type)
            val list = flow.first()
            assertEquals(10, list.size)
            list.forEach {
                assertEquals(parameter.type, it.type)
            }
        }

    }

    @RunWith(RobolectricTestRunner::class)
    internal class InsertBookmarksWithArticleContentTest : BaseTest() {
        @Test
        fun updatingExistingBookmarkPreservesCachedAnnotations() = runTest(testDispatcher) {
            cachedAnnotationDao.insertAnnotations(
                listOf(
                    CachedAnnotationEntity(
                        id = "annotation-1",
                        bookmarkId = "test-0",
                        text = "Cached highlight",
                        color = "yellow",
                        note = null,
                        created = "2026-01-01T00:00:00Z"
                    )
                )
            )
            val updatedBookmark = bookmarkDao.getBookmarkById("test-0").copy(
                title = "Updated title"
            )

            bookmarkDao.insertBookmarksWithArticleContent(
                listOf(
                    BookmarkWithArticleContent(
                        bookmark = updatedBookmark,
                        articleContent = ArticleContentEntity(
                            bookmarkId = "test-0",
                            content = "updated content"
                        )
                    )
                )
            )

            assertEquals(
                listOf("annotation-1"),
                cachedAnnotationDao.getAnnotationsForBookmark("test-0").map { it.id }
            )
            assertEquals("Updated title", bookmarkDao.getBookmarkById("test-0").title)
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class GetLastUpdatedBookmarkTest : BaseTest() {
        @Test
        fun testGetLastUpdatedBookmark() = runTest(testDispatcher) {
            val lastUpdated = bookmarkDao.getLastUpdatedBookmark()
            assertNotNull(lastUpdated)
            assertEquals("test-29", lastUpdated?.id)
        }

    }

    @RunWith(RobolectricTestRunner::class)
    internal class GetAllBookmarksIsSortedByCreationDateTest : BaseTest() {
        @Test
        fun testGetAllBookmarksIsSortedByCreationDate() = runTest(testDispatcher) {
            val flow = bookmarkDao.getAllBookmarks()
            val list = flow.first()
            assertEquals(30, list.size)
            var prevDate: Instant? = null
            list.forEach { bookmark ->
                prevDate?.let {
                    assertTrue("wrong sort order", it >= bookmark.created)
                }
                prevDate = bookmark.created
            }
        }

    }

    @RunWith(RobolectricTestRunner::class)
    internal class GetFilterByStateTest : BaseTest() {
        @Test
        fun testGetLoaded() = runTest(testDispatcher) {
            val flow = bookmarkDao.getBookmarksByFilters(state = BookmarkEntity.State.LOADED)
            val list = flow.first()
            assertEquals(24, list.size)
            list.forEach { bookmark ->
                assertEquals(BookmarkEntity.State.LOADED, bookmark.state)
            }
        }

        @Test
        fun testGetLoading() = runTest(testDispatcher) {
            val flow = bookmarkDao.getBookmarksByFilters(state = BookmarkEntity.State.LOADING)
            val list = flow.first()
            assertEquals(3, list.size)
            list.forEach { bookmark ->
                assertEquals(BookmarkEntity.State.LOADING, bookmark.state)
            }
        }

        @Test
        fun testGetError() = runTest(testDispatcher) {
            val flow = bookmarkDao.getBookmarksByFilters(state = BookmarkEntity.State.ERROR)
            val list = flow.first()
            assertEquals(3, list.size)
            list.forEach { bookmark ->
                assertEquals(BookmarkEntity.State.ERROR, bookmark.state)
            }
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class GetArticleTest : BaseTest() {
        @Test
        fun testGetLoaded() = runTest(testDispatcher) {
            val list = bookmarkDao.getAllBookmarksWithContent()
            Timber.d("list=$list")
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class RemoteBookmarkIdTest : BaseTest() {
        @Test
        fun testGetRemoteBookmarkIds() = runTest(testDispatcher) {
            val list = bookmarkDao.getAllRemoteBookmarkIds(remoteSyncRunId)
            assertEquals(28, list.size)
            list.forEach {
                if (it == "not-a-bookmark") {
                    try {
                        bookmarkDao.getBookmarkById(it)
                        fail("Expected IllegalStateException for missing bookmark")
                    } catch (_: IllegalStateException) {
                        Timber.d("not-a-bookmark")
                    }
                } else {
                    val bookmark = bookmarkDao.getBookmarkById(it)
                    assertEquals(it, bookmark.id)
                    Timber.d("id=$it")
                    Timber.d("bookmark=$bookmark")
                }
            }
        }

        @Test
        fun testRemoveBookmarks() = runTest(testDispatcher) {
            val removedIds = listOf<String>("test-1", "test-11", "test-21")
            removedIds.forEach {
                assertNotNull(bookmarkDao.getBookmarkById(it))
                Timber.d("id=$it is not null")
            }
            val count = bookmarkDao.removeDeletedBookmars(remoteSyncRunId)
            assertEquals(3, count)
            removedIds.forEach {
                try {
                    bookmarkDao.getBookmarkById(it)
                    fail("Expected IllegalStateException for missing bookmark")
                } catch (_: IllegalStateException) {
                    Timber.d("id=$it is null")
                }
            }
        }

        @Test
        fun `removeDeletedBookmars only uses current sync run`() = runTest(testDispatcher) {
            val currentRunId = "current-run"
            val oldRunId = "old-run"
            bookmarkDao.clearRemoteBookmarkIds(remoteSyncRunId)
            bookmarkDao.insertRemoteBookmarkIds(
                listOf(
                    RemoteBookmarkIdEntity(oldRunId, "test-1"),
                    RemoteBookmarkIdEntity(oldRunId, "test-11"),
                    RemoteBookmarkIdEntity(currentRunId, "test-0"),
                    RemoteBookmarkIdEntity(currentRunId, "test-2")
                )
            )

            val count = bookmarkDao.removeDeletedBookmars(currentRunId)

            assertEquals(28, count)
            assertNotNull(bookmarkDao.getBookmarkById("test-0"))
            assertNotNull(bookmarkDao.getBookmarkById("test-2"))
            try {
                bookmarkDao.getBookmarkById("test-1")
                fail("Expected stale run ID to be ignored during deletion detection")
            } catch (_: IllegalStateException) {
                Timber.d("stale run ID ignored")
            }
        }

        @Test
        fun `clearRemoteBookmarkIds clears only current sync run`() = runTest(testDispatcher) {
            val currentRunId = "current-run"
            val otherRunId = "other-run"
            bookmarkDao.insertRemoteBookmarkIds(
                listOf(
                    RemoteBookmarkIdEntity(currentRunId, "current-1"),
                    RemoteBookmarkIdEntity(currentRunId, "current-2"),
                    RemoteBookmarkIdEntity(otherRunId, "other-1")
                )
            )

            bookmarkDao.clearRemoteBookmarkIds(currentRunId)

            assertEquals(0, bookmarkDao.countDistinctRemoteBookmarkIds(currentRunId))
            assertEquals(1, bookmarkDao.countDistinctRemoteBookmarkIds(otherRunId))
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class BookmarkExistenceTest : BaseTest() {
        @Test
        fun `getExistingActiveBookmarkIds returns only local non-deleted bookmarks`() = runTest(testDispatcher) {
            bookmarkDao.softDeleteBookmark("test-1")

            val ids = bookmarkDao.getExistingActiveBookmarkIds(
                listOf("test-0", "test-1", "missing-bookmark", "test-2")
            )

            assertEquals(listOf("test-0", "test-2"), ids.sorted())
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class GetCountsTest : BaseTest() {
        @Test
        fun testObserveAllBookmarkCounts() = runTest(testDispatcher) {
            val counts = bookmarkDao.observeAllBookmarkCounts().first()
            assertEquals(0, counts?.archived)
            assertEquals(0, counts?.favorite)
            assertEquals(8, counts?.article)
            assertEquals(8, counts?.video)
            assertEquals(8, counts?.picture)
            assertEquals(24, counts?.total)
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class MetadataOnlyUpsertTest : BaseTest() {
        @Test
        fun `metadata-only upsert preserves article content and content state`() = runTest(testDispatcher) {
            val bookmarkId = "test-0"
            bookmarkDao.updateContentState(
                bookmarkId,
                BookmarkEntity.ContentState.DOWNLOADED.value,
                null
            )

            val original = bookmarkDao.getBookmarkById(bookmarkId)
            val updated = original.copy(
                title = "Updated Title",
                description = "Updated Description",
                contentState = BookmarkEntity.ContentState.NOT_ATTEMPTED,
                contentFailureReason = null
            )

            bookmarkDao.upsertBookmarksMetadataOnly(listOf(updated))

            val saved = bookmarkDao.getBookmarkById(bookmarkId)
            assertEquals("Updated Title", saved.title)
            assertEquals("Updated Description", saved.description)
            assertEquals(BookmarkEntity.ContentState.DOWNLOADED, saved.contentState)
            assertEquals("content", bookmarkDao.getArticleContent(bookmarkId))
        }

        @Test
        fun `metadata-only upsert preserves content failure reason`() = runTest(testDispatcher) {
            val bookmarkId = "test-0"
            bookmarkDao.updateContentState(
                bookmarkId,
                BookmarkEntity.ContentState.DIRTY.value,
                "network timeout"
            )

            val original = bookmarkDao.getBookmarkById(bookmarkId)
            val updated = original.copy(
                title = "Updated Title",
                description = "Updated Description",
                contentState = BookmarkEntity.ContentState.NOT_ATTEMPTED,
                contentFailureReason = null
            )

            bookmarkDao.upsertBookmarksMetadataOnly(listOf(updated))

            val saved = bookmarkDao.getBookmarkById(bookmarkId)
            assertEquals("Updated Title", saved.title)
            assertEquals("Updated Description", saved.description)
            assertEquals(BookmarkEntity.ContentState.DIRTY, saved.contentState)
            assertEquals("network timeout", saved.contentFailureReason)
        }
    }

    @RunWith(RobolectricTestRunner::class)
    internal class WithErrorsFilterTest : BaseTest() {
        @Test
        fun `withErrors yes matches server error state or persisted server errors`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(withErrors = true).first()

            assertEquals(6, list.size)
            assertTrue(list.any { it.id == "test-7" })
            assertTrue(list.any { it.id == "test-9" })
            assertTrue(list.any { it.id == "test-17" })
            assertTrue(list.any { it.id == "test-19" })
            assertTrue(list.any { it.id == "test-27" })
            assertTrue(list.any { it.id == "test-29" })
        }

        @Test
        fun `withErrors no excludes state errors and persisted server errors`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(withErrors = false).first()

            assertEquals(24, list.size)
            assertTrue(list.none { it.id == "test-7" })
            assertTrue(list.none { it.id == "test-9" })
            assertTrue(list.none { it.id == "test-17" })
            assertTrue(list.none { it.id == "test-19" })
            assertTrue(list.none { it.id == "test-27" })
            assertTrue(list.none { it.id == "test-29" })
        }
    }

    // Null-checkbox truth table (8 rows from the reading-time-filter spec)
    @RunWith(RobolectricTestRunner::class)
    internal class ReadingTimeFilterTest : BaseTest() {

        // Base data: 30 bookmarks, readingTime = 5+index (5..34), all non-null.
        // We supplement with 3 null-readingTime bookmarks so every truth-table branch is reachable.
        private val nullIds = listOf("rt-null-0", "rt-null-1", "rt-null-2")

        @Before
        fun insertNullReadingTimeBookmarks() = runTest {
            val base = BookmarkEntity(
                id = "rt-null-0",
                href = "http://example.com/rtnull",
                created = kotlinx.datetime.Instant.parse("2025-06-01T00:00:00Z"),
                updated = kotlinx.datetime.Instant.parse("2025-06-01T00:00:00Z"),
                state = BookmarkEntity.State.LOADED,
                loaded = true,
                url = "http://example.com/rtnull",
                title = "Null RT Bookmark",
                siteName = "Example",
                site = "example.com",
                authors = emptyList(),
                lang = "en",
                textDirection = "ltr",
                documentTpe = "article",
                type = BookmarkEntity.Type.ARTICLE,
                hasArticle = false,
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
                article = com.mydeck.app.io.db.model.ResourceEntity(""),
                icon = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
                image = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
                log = com.mydeck.app.io.db.model.ResourceEntity(""),
                props = com.mydeck.app.io.db.model.ResourceEntity(""),
                thumbnail = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
            )
            bookmarkDao.insertBookmarks(nullIds.mapIndexed { i, id -> base.copy(id = id, href = "${base.href}/$i", url = "${base.url}/$i") })
        }

        // Row 1 — no reading-time filter → all rows returned
        @Test
        fun `no reading time filter returns all bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems().first()
            assertEquals(33, list.size)
        }

        // Row 2 — min=10 max=20 null=false → range only, nulls excluded
        @Test
        fun `min and max set null false returns range only`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minReadingTime = 10, maxReadingTime = 20).first()
            // readingTime = 5+index, so 10<=5+index<=20 → 5<=index<=15 → 11 bookmarks
            assertEquals(11, list.size)
            assertTrue(list.all { (it.readingTime ?: -1) in 10..20 })
            assertTrue(list.none { it.id in nullIds })
        }

        // Row 3 — min=30 only, null=false → lower bound only
        @Test
        fun `min only set null false returns bookmarks with readingTime gte min`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minReadingTime = 30).first()
            // 5+index >= 30 → index >= 25 → 5 bookmarks (test-25..test-29)
            assertEquals(5, list.size)
            assertTrue(list.all { (it.readingTime ?: -1) >= 30 })
        }

        // Row 4 — max=7 only, null=false → upper bound only
        @Test
        fun `max only set null false returns bookmarks with readingTime lte max`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(maxReadingTime = 7).first()
            // 5+index <= 7 → index <= 2 → 3 bookmarks (test-0..test-2)
            assertEquals(3, list.size)
            assertTrue(list.all { (it.readingTime ?: Int.MAX_VALUE) <= 7 })
        }

        // Row 5 — null=true, no min/max → only null-readingTime bookmarks
        @Test
        fun `null only returns only null reading time bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(includeNullReadingTime = true).first()
            assertEquals(3, list.size)
            assertTrue(list.all { it.id in nullIds })
        }

        // Row 6 — min=10 max=20 null=true → range OR null
        @Test
        fun `min and max set null true returns range plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minReadingTime = 10, maxReadingTime = 20, includeNullReadingTime = true).first()
            assertEquals(14, list.size) // 11 in range + 3 null
            assertTrue(list.filter { it.id !in nullIds }.all { (it.readingTime ?: -1) in 10..20 })
            assertTrue(list.filter { it.id in nullIds }.all { it.readingTime == null })
        }

        // Row 7 — min=30 null=true → readingTime >= 30 OR null
        @Test
        fun `min only set null true returns gte min plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minReadingTime = 30, includeNullReadingTime = true).first()
            assertEquals(8, list.size) // 5 in range + 3 null
        }

        // Row 8 — max=7 null=true → readingTime <= 7 OR null
        @Test
        fun `max only set null true returns lte max plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(maxReadingTime = 7, includeNullReadingTime = true).first()
            assertEquals(6, list.size) // 3 in range + 3 null
        }
    }

    // Word-count null-checkbox truth table — mirrors the reading-time filter spec
    @RunWith(RobolectricTestRunner::class)
    internal class WordCountFilterTest : BaseTest() {

        // Base data: 30 bookmarks, wordCount = 100 + index*10 (100..390), all non-null.
        // Supplement with 3 null-wordCount bookmarks.
        private val nullIds = listOf("wc-null-0", "wc-null-1", "wc-null-2")

        @Before
        fun insertNullWordCountBookmarks() = runTest {
            val base = BookmarkEntity(
                id = "wc-null-0",
                href = "http://example.com/wcnull",
                created = kotlinx.datetime.Instant.parse("2025-07-01T00:00:00Z"),
                updated = kotlinx.datetime.Instant.parse("2025-07-01T00:00:00Z"),
                state = BookmarkEntity.State.LOADED,
                loaded = true,
                url = "http://example.com/wcnull",
                title = "Null WC Bookmark",
                siteName = "Example",
                site = "example.com",
                authors = emptyList(),
                lang = "en",
                textDirection = "ltr",
                documentTpe = "article",
                type = BookmarkEntity.Type.ARTICLE,
                hasArticle = false,
                description = "",
                isDeleted = false,
                isMarked = false,
                isArchived = false,
                labels = emptyList(),
                readProgress = 0,
                wordCount = null,
                readingTime = 5,
                published = null,
                embed = null,
                embedHostname = null,
                article = com.mydeck.app.io.db.model.ResourceEntity(""),
                icon = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
                image = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
                log = com.mydeck.app.io.db.model.ResourceEntity(""),
                props = com.mydeck.app.io.db.model.ResourceEntity(""),
                thumbnail = com.mydeck.app.io.db.model.ImageResourceEntity("", 0, 0),
            )
            bookmarkDao.insertBookmarks(nullIds.mapIndexed { i, id -> base.copy(id = id, href = "${base.href}/$i", url = "${base.url}/$i") })
        }

        // Row 1 — no word-count filter → all rows returned
        @Test
        fun `no word count filter returns all bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems().first()
            assertEquals(33, list.size)
        }

        // Row 2 — min=200 max=300 null=false → range only, nulls excluded
        @Test
        fun `min and max set null false returns range only`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minWordCount = 200, maxWordCount = 300).first()
            // wordCount = 100+index*10; 200<=100+index*10<=300 → 10<=index<=20 → 11 bookmarks
            assertEquals(11, list.size)
            assertTrue(list.all { (it.wordCount ?: -1) in 200..300 })
            assertTrue(list.none { it.id in nullIds })
        }

        // Row 3 — min=300 only, null=false → lower bound only
        @Test
        fun `min only set null false returns bookmarks with wordCount gte min`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minWordCount = 300).first()
            // 100+index*10 >= 300 → index >= 20 → 10 bookmarks (test-20..test-29)
            assertEquals(10, list.size)
            assertTrue(list.all { (it.wordCount ?: -1) >= 300 })
        }

        // Row 4 — max=150 only, null=false → upper bound only
        @Test
        fun `max only set null false returns bookmarks with wordCount lte max`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(maxWordCount = 150).first()
            // 100+index*10 <= 150 → index <= 5 → 6 bookmarks
            assertEquals(6, list.size)
            assertTrue(list.all { (it.wordCount ?: Int.MAX_VALUE) <= 150 })
        }

        // Row 5 — null=true, no min/max → only null-wordCount bookmarks
        @Test
        fun `null only returns only null word count bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(includeNullWordCount = true).first()
            assertEquals(3, list.size)
            assertTrue(list.all { it.id in nullIds })
        }

        // Row 6 — min=200 max=300 null=true → range OR null
        @Test
        fun `min and max set null true returns range plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minWordCount = 200, maxWordCount = 300, includeNullWordCount = true).first()
            assertEquals(14, list.size) // 11 in range + 3 null
        }

        // Row 7 — min=300 null=true → wordCount >= 300 OR null
        @Test
        fun `min only set null true returns gte min plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(minWordCount = 300, includeNullWordCount = true).first()
            assertEquals(13, list.size) // 10 in range + 3 null
        }

        // Row 8 — max=150 null=true → wordCount <= 150 OR null
        @Test
        fun `max only set null true returns lte max plus null bookmarks`() = runTest(testDispatcher) {
            val list = bookmarkDao.getFilteredBookmarkListItems(maxWordCount = 150, includeNullWordCount = true).first()
            assertEquals(9, list.size) // 6 in range + 3 null
        }
    }
}
