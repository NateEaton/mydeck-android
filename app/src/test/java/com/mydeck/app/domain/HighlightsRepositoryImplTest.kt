package com.mydeck.app.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.domain.model.HighlightsSyncMetadata
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.sync.SyncScheduler
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.ImageResourceEntity
import com.mydeck.app.io.db.model.ResourceEntity
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import com.mydeck.app.io.rest.model.AnnotationSummaryDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HighlightsRepositoryImplTest {
    private lateinit var database: MyDeckDatabase
    private lateinit var readeckApi: ReadeckApi
    private lateinit var settingsDataStore: SettingsDataStore
    private var syncMetadata = HighlightsSyncMetadata()
    private val annotationSummaryResponses =
        mutableMapOf<Pair<Int, Int>, Response<List<AnnotationSummaryDto>>>()
    private val annotationSummaryDelaysMs = mutableMapOf<Pair<Int, Int>, Long>()
    private val annotationSummaryBlockers = mutableMapOf<Pair<Int, Int>, CompletableDeferred<Unit>>()
    private val annotationSummaryCalls = mutableListOf<Pair<Int, Int>>()
    private val annotationResponses = mutableMapOf<String, Response<List<AnnotationDto>>>()
    private val annotationDelaysMs = mutableMapOf<String, Long>()
    private val annotationCalls = mutableListOf<String>()
    private lateinit var bookmarkMetadataSyncCoordinator: BookmarkMetadataSyncCoordinator
    private lateinit var syncScheduler: FakeSyncScheduler
    private var initialSyncPerformed = true

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MyDeckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        readeckApi = mockk()
        settingsDataStore = mockk()
        syncMetadata = HighlightsSyncMetadata()
        annotationSummaryResponses.clear()
        annotationSummaryDelaysMs.clear()
        annotationSummaryBlockers.clear()
        annotationSummaryCalls.clear()
        annotationResponses.clear()
        annotationDelaysMs.clear()
        annotationCalls.clear()
        bookmarkMetadataSyncCoordinator = BookmarkMetadataSyncCoordinator()
        syncScheduler = FakeSyncScheduler()
        initialSyncPerformed = true
        coEvery {
            readeckApi.getAnnotationSummaries(limit = any(), offset = any())
        } coAnswers {
            val limit = firstArg<Int>()
            val offset = secondArg<Int>()
            annotationSummaryCalls += limit to offset
            annotationSummaryDelaysMs[limit to offset]?.let { delay(it) }
            annotationSummaryBlockers[limit to offset]?.await()
            annotationSummaryResponses[limit to offset]
                ?: error("No annotation summary response for limit=$limit offset=$offset")
        }
        coEvery { settingsDataStore.getHighlightsSyncMetadata() } coAnswers { syncMetadata }
        coEvery { settingsDataStore.saveHighlightsSyncMetadata(any()) } coAnswers {
            syncMetadata = firstArg()
        }
        coEvery { settingsDataStore.isInitialSyncPerformed() } coAnswers { initialSyncPerformed }
        coEvery { readeckApi.getAnnotations(any()) } coAnswers {
            val bookmarkId = firstArg<String>()
            annotationCalls += bookmarkId
            annotationDelaysMs[bookmarkId]?.let { delay(it) }
            annotationResponses[bookmarkId]
                ?: error("No annotation response for bookmarkId=$bookmarkId")
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `requestRefresh schedules crawl outside caller scope`() = runTest {
        val repository = repository(backgroundScope)
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        annotationSummaryResponses[50 to 0] =
            Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
    }

    @Test
    fun `screen-open skips global reconciliation when cache is complete and fresh`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.SCREEN_OPEN)
            delay(100)

            assertTrue(result.isSuccess)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    @Test
    fun `screen-open runs global reconciliation when cache is incomplete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.SCREEN_OPEN)
            waitUntil { syncMetadata.cacheComplete }

            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
            assertTrue(syncMetadata.cacheComplete)
            assertEquals(0, syncMetadata.skippedOrphanAnnotationCount)
            assertEquals(0, syncMetadata.missingBookmarkCount)
            assertEquals(null, syncMetadata.firstOrphanOffset)
            assertEquals(null, syncMetadata.lastOrphanWarningAt)
        }
    }

    @Test
    fun `global reconciliation skips orphan annotations and records diagnostics`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryResponses[50 to 0] = Response.success(
                listOf(
                    remoteAnnotation("valid-1", "bookmark-1", "Valid"),
                    remoteAnnotation("orphan-1", "missing-bookmark", "Orphan")
                )
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil {
                syncMetadata.cacheComplete &&
                    repository.observeSyncState().value == HighlightsSyncState.Idle &&
                    syncScheduler.orphanRepairRequests == 1
            }

            val validRows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1")
            val orphanRows = database.getCachedAnnotationDao().getAnnotationsForBookmark("missing-bookmark")
            assertTrue(result.isSuccess)
            assertEquals(listOf("valid-1"), validRows.map { it.id })
            assertEquals(emptyList<CachedAnnotationEntity>(), orphanRows)
            assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
            assertEquals(1, syncMetadata.skippedOrphanAnnotationCount)
            assertEquals(1, syncMetadata.missingBookmarkCount)
            assertEquals(0, syncMetadata.firstOrphanOffset)
            assertNotNull(syncMetadata.lastOrphanWarningAt)
            assertEquals(0, syncMetadata.globalFailureCount)
            assertEquals(null, syncMetadata.globalBackoffUntil)
            assertEquals(1, syncScheduler.orphanRepairRequests)
        }
    }

    @Test
    fun `global reconciliation continues paging after orphan annotations`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1"), bookmark("bookmark-2")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            val firstPage = List(49) { index ->
                remoteAnnotation("valid-page-1-$index", "bookmark-1", "Valid $index")
            } + remoteAnnotation("orphan-page-1", "missing-bookmark", "Orphan")
            annotationSummaryResponses[50 to 0] = Response.success(firstPage)
            annotationSummaryResponses[50 to 50] = Response.success(
                listOf(remoteAnnotation("valid-page-2", "bookmark-2", "Valid page 2"))
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil {
                syncMetadata.cacheComplete &&
                    repository.observeSyncState().value == HighlightsSyncState.Idle &&
                    syncScheduler.orphanRepairRequests == 1
            }

            val bookmarkOneRows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1")
            val bookmarkTwoRows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-2")
            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0, 50 to 50), annotationSummaryCalls)
            assertEquals(49, bookmarkOneRows.size)
            assertEquals(listOf("valid-page-2"), bookmarkTwoRows.map { it.id })
            assertEquals(1, syncMetadata.skippedOrphanAnnotationCount)
            assertEquals(1, syncMetadata.missingBookmarkCount)
            assertEquals(0, syncMetadata.firstOrphanOffset)
            assertTrue(syncMetadata.cacheComplete)
            assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
            assertEquals(1, syncScheduler.orphanRepairRequests)
        }
    }

    @Test
    fun `orphan repair is scheduled after bookmark stability gate is released`() = runTest {
        val repository = repository(backgroundScope)
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
        val releaseAnnotationPage = CompletableDeferred<Unit>()
        annotationSummaryBlockers[50 to 0] = releaseAnnotationPage
        annotationSummaryResponses[50 to 0] = Response.success(
            listOf(
                remoteAnnotation("valid-1", "bookmark-1", "Valid"),
                remoteAnnotation("orphan-1", "missing-bookmark", "Orphan")
            )
        )

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
        waitUntil { annotationSummaryCalls == listOf(50 to 0) }
        delay(100)

        assertTrue(result.isSuccess)
        assertEquals(0, syncScheduler.orphanRepairRequests)

        releaseAnnotationPage.complete(Unit)
        waitUntil { syncScheduler.orphanRepairRequests == 1 }
        assertTrue(syncMetadata.cacheComplete)
    }

    @Test
    fun `orphan repair is not scheduled for valid highlight reconciliation`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("valid-1", "bookmark-1", "Valid")))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { syncMetadata.cacheComplete }

            assertTrue(result.isSuccess)
            assertEquals(0, syncScheduler.orphanRepairRequests)
        }
    }

    @Test
    fun `orphan repair refresh does not schedule another repair when orphans remain`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(cacheComplete = true, lastGlobalSuccessAt = Clock.System.now())
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("orphan-1", "missing-bookmark", "Orphan")))

            val result = repository.requestRefresh(HighlightsRefreshReason.ORPHAN_REPAIR)
            waitUntil { syncMetadata.skippedOrphanAnnotationCount == 1 }

            assertTrue(result.isSuccess)
            assertEquals(0, syncScheduler.orphanRepairRequests)
            assertTrue(syncMetadata.cacheComplete)
            assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
        }
    }

    @Test
    fun `orphan repair refresh bypasses freshness when cache is complete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = true, lastGlobalSuccessAt = Clock.System.now())
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("valid-1", "bookmark-1", "Valid")))

            val result = repository.requestRefresh(HighlightsRefreshReason.ORPHAN_REPAIR)
            waitUntil { annotationSummaryCalls == listOf(50 to 0) }

            assertTrue(result.isSuccess)
            waitUntil { syncMetadata.cacheComplete }
            assertEquals(0, syncScheduler.orphanRepairRequests)
        }
    }

    @Test
    fun `screen-open skips global reconciliation while bookmark bootstrap is incomplete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            initialSyncPerformed = false
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.SCREEN_OPEN)
            delay(100)

            assertTrue(result.isSuccess)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
            assertEquals(false, syncMetadata.cacheComplete)
        }
    }

    @Test
    fun `app-open normal sync schedules global reconciliation when cache is incomplete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.APP_OPEN)
            waitUntil { syncMetadata.cacheComplete }

            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
            assertTrue(syncMetadata.cacheComplete)
        }
    }

    @Test
    fun `app-open normal sync skips global reconciliation when cache is fresh`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.APP_OPEN)
            delay(100)

            assertTrue(result.isSuccess)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    @Test
    fun `periodic backstop schedules global reconciliation when stale`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Instant.parse("2026-01-01T00:00:00Z"),
                cacheComplete = true,
            )
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.PERIODIC_BACKSTOP)
            waitUntil { syncMetadata.lastGlobalSuccessAt != Instant.parse("2026-01-01T00:00:00Z") }

            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
            assertTrue(syncMetadata.cacheComplete)
        }
    }

    @Test
    fun `periodic backstop skips global reconciliation when fresh`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.PERIODIC_BACKSTOP)
            delay(100)

            assertTrue(result.isSuccess)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    @Test
    fun `manual retry bypasses freshness throttle`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil {
                annotationSummaryCalls.isNotEmpty() &&
                    syncMetadata.cacheComplete &&
                    syncMetadata.globalFailureCount == 0 &&
                    syncMetadata.globalBackoffUntil == null
            }

            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
        }
    }

    @Test
    fun `manual retry bypasses freshness throttle and backoff`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                lastGlobalFailureAt = Clock.System.now(),
                globalFailureCount = 2,
                globalBackoffUntil = Instant.parse("9999-01-01T00:00:00Z"),
                cacheComplete = true,
            )
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil {
                annotationSummaryCalls.isNotEmpty() &&
                    syncMetadata.globalFailureCount == 0 &&
                    syncMetadata.globalBackoffUntil == null
            }

            assertTrue(result.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
            assertEquals(0, syncMetadata.globalFailureCount)
            assertEquals(null, syncMetadata.globalBackoffUntil)
        }
    }

    @Test
    fun `full bookmark sync reason does not force global crawl when cache is fresh`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )

            val result = repository.requestRefresh(HighlightsRefreshReason.MANUAL_SYNC)
            delay(100)

            assertTrue(result.isSuccess)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    @Test
    fun `mid-refresh failure keeps existing cache and upserts completed pages`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached-old", "bookmark-1", "Cached old"))
            )
            val firstPage = (0 until 50).map { index ->
                remoteAnnotation("remote-$index", "bookmark-1", "Remote $index")
            }
            annotationSummaryResponses[50 to 0] = Response.success(firstPage)
            annotationSummaryResponses[50 to 50] = Response.error(500, "error".toResponseBody(null))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { syncMetadata.lastGlobalFailureAt != null }

            val highlights = repository.observeHighlights().first()
            assertTrue(result.isSuccess)
            assertTrue(highlights.any { it.id == "cached-old" })
            assertTrue(highlights.any { it.id == "remote-0" })
            assertEquals(51, highlights.size)
            assertNotNull(syncMetadata.lastGlobalFailureAt)
            assertNotNull(syncMetadata.globalBackoffUntil)
            assertEquals(1, syncMetadata.globalFailureCount)
        }
    }

    @Test
    fun `cached highlights remain visible while global reconciliation runs`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached", "bookmark-1", "Cached"))
            )
            annotationSummaryDelaysMs[50 to 0] = 250
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { annotationSummaryCalls.isNotEmpty() }

            val highlights = repository.observeHighlights().first()
            assertTrue(result.isSuccess)
            assertEquals(listOf("cached"), highlights.map { it.id })
            assertTrue(repository.observeSyncState().value is HighlightsSyncState.Running)
        }
    }

    @Test
    fun `successful refresh deletes stale rows and marks cache complete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(
                    annotation("stale", "bookmark-1", "Stale"),
                    annotation("keep", "bookmark-1", "Old keep")
                )
            )
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("keep", "bookmark-1", "Updated keep")))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { syncMetadata.cacheComplete }

            val highlights = repository.observeHighlights().first()
            assertTrue(result.isSuccess)
            assertEquals(listOf("keep"), highlights.map { it.id })
            assertEquals("Updated keep", highlights.single().text)
            assertTrue(syncMetadata.cacheComplete)
            assertNotNull(syncMetadata.lastGlobalSuccessAt)
            assertEquals(0, syncMetadata.globalFailureCount)
            assertEquals(null, syncMetadata.globalBackoffUntil)
        }
    }

    @Test
    fun `successful empty remote result clears local highlights and marks cache complete`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached-old", "bookmark-1", "Cached old"))
            )
            annotationSummaryResponses[50 to 0] = Response.success(emptyList())

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { syncMetadata.cacheComplete }

            assertTrue(result.isSuccess)
            assertEquals(emptyList<String>(), repository.observeHighlights().first().map { it.id })
            assertTrue(syncMetadata.cacheComplete)
            assertNotNull(syncMetadata.lastGlobalSuccessAt)
        }
    }

    @Test
    fun `global failure records failure metadata and backoff`() = runBlocking {
        withRepository { repository ->
            annotationSummaryResponses[50 to 0] = Response.error(503, "error".toResponseBody(null))

            val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
            waitUntil { syncMetadata.lastGlobalFailureAt != null }

            assertTrue(result.isSuccess)
            assertNotNull(syncMetadata.lastGlobalAttemptAt)
            assertNotNull(syncMetadata.lastGlobalFailureAt)
            assertNotNull(syncMetadata.globalBackoffUntil)
            assertEquals(1, syncMetadata.globalFailureCount)
            assertEquals(false, syncMetadata.cacheComplete)
            assertEquals(0, syncScheduler.orphanRepairRequests)
        }
    }

    @Test
    fun `failed global reconciliation records backoff and preserves cached rows`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached", "bookmark-1", "Cached"))
            )
            annotationSummaryResponses[50 to 0] = Response.error(503, "error".toResponseBody(null))

            val result = repository.requestRefresh(HighlightsRefreshReason.PERIODIC_BACKSTOP)
            waitUntil { syncMetadata.lastGlobalFailureAt != null }

            val rows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1")
            assertTrue(result.isSuccess)
            assertEquals(listOf("cached"), rows.map { it.id })
            assertNotNull(syncMetadata.lastGlobalAttemptAt)
            assertNotNull(syncMetadata.lastGlobalFailureAt)
            assertNotNull(syncMetadata.globalBackoffUntil)
            assertEquals(1, syncMetadata.globalFailureCount)
            assertEquals(false, syncMetadata.cacheComplete)
        }
    }

    @Test
    fun `global reconciliation remains single flight`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
            annotationSummaryDelaysMs[50 to 0] = 250
            annotationSummaryResponses[50 to 0] =
                Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

            val first = repository.requestRefresh(HighlightsRefreshReason.APP_OPEN)
            val second = repository.requestRefresh(HighlightsRefreshReason.APP_OPEN)
            waitUntil { syncMetadata.cacheComplete }

            assertTrue(first.isSuccess)
            assertTrue(second.isSuccess)
            assertEquals(listOf(50 to 0), annotationSummaryCalls)
        }
    }

    @Test
    fun `global reconciliation waits for active bookmark metadata sync before paging annotations`() = runTest {
        val repository = repository(backgroundScope)
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
        annotationSummaryResponses[50 to 0] =
            Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))
        val metadataGateEntered = CompletableDeferred<Unit>()
        val releaseMetadataGate = CompletableDeferred<Unit>()
        val metadataJob = backgroundScope.async {
            bookmarkMetadataSyncCoordinator.withExclusiveMetadataSync("test.metadata") {
                metadataGateEntered.complete(Unit)
                releaseMetadataGate.await()
            }
        }
        metadataGateEntered.await()

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
        waitUntil { repository.observeSyncState().value is HighlightsSyncState.Running }
        delay(100)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
        assertTrue(repository.observeSyncState().value !is HighlightsSyncState.Failed)

        releaseMetadataGate.complete(Unit)
        metadataJob.await()
        waitUntil { syncMetadata.cacheComplete }

        assertEquals(listOf(50 to 0), annotationSummaryCalls)
        assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
    }

    @Test
    fun `bookmark metadata sync waits for active global reconciliation stability gate`() = runTest {
        val repository = repository(backgroundScope)
        database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
        syncMetadata = HighlightsSyncMetadata(cacheComplete = false)
        val releaseAnnotationPage = CompletableDeferred<Unit>()
        annotationSummaryBlockers[50 to 0] = releaseAnnotationPage
        annotationSummaryResponses[50 to 0] =
            Response.success(listOf(remoteAnnotation("remote-1", "bookmark-1", "Remote")))

        val result = repository.requestRefresh(HighlightsRefreshReason.USER_RETRY)
        waitUntil { annotationSummaryCalls == listOf(50 to 0) }

        val metadataEntered = CompletableDeferred<Unit>()
        val metadataJob = backgroundScope.async {
            bookmarkMetadataSyncCoordinator.withExclusiveMetadataSync("test.metadata") {
                metadataEntered.complete(Unit)
            }
        }
        delay(100)

        assertTrue(result.isSuccess)
        assertTrue(!metadataEntered.isCompleted)
        assertTrue(repository.observeSyncState().value !is HighlightsSyncState.Failed)

        releaseAnnotationPage.complete(Unit)
        waitUntil { syncMetadata.cacheComplete }
        metadataJob.await()

        assertTrue(metadataEntered.isCompleted)
        assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
    }

    @Test
    fun `repeated highlights screen opens do not reschedule global reconciliation when fresh`() = runBlocking {
        withRepository { repository ->
            syncMetadata = HighlightsSyncMetadata(
                lastGlobalSuccessAt = Clock.System.now(),
                cacheComplete = true,
            )

            repeat(3) {
                val result = repository.requestRefresh(HighlightsRefreshReason.SCREEN_OPEN)
                assertTrue(result.isSuccess)
            }
            delay(100)

            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    @Test
    fun `per-bookmark reconcile inserts a new remote annotation`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "New remote"))
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.READER_REFRESH,
            )

            val rows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1")
            assertTrue(result.isSuccess)
            assertEquals(listOf("remote-1"), rows.map { it.id })
            assertEquals("New remote", rows.single().text)
            assertEquals(0, result.getOrThrow().previousCount)
            assertEquals(1, result.getOrThrow().remoteCount)
            assertTrue(result.getOrThrow().changed)
        }
    }

    @Test
    fun `per-bookmark reconcile updates changed note color and text`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("remote-1", "bookmark-1", "Old", color = "yellow", note = "old note"))
            )
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "Updated", color = "red", note = "new note"))
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.READER_REFRESH,
            )

            val row = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1").single()
            assertTrue(result.isSuccess)
            assertEquals("Updated", row.text)
            assertEquals("red", row.color)
            assertEquals("new note", row.note)
            assertTrue(result.getOrThrow().changed)
        }
    }

    @Test
    fun `per-bookmark reconcile deletes stale rows only after successful empty response`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("stale", "bookmark-1", "Stale"))
            )
            annotationResponses["bookmark-1"] = Response.success(emptyList())

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.READER_REFRESH,
            )

            assertTrue(result.isSuccess)
            assertEquals(emptyList<CachedAnnotationEntity>(), database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1"))
            assertEquals(1, result.getOrThrow().previousCount)
            assertEquals(0, result.getOrThrow().remoteCount)
            assertTrue(result.getOrThrow().changed)
        }
    }

    @Test
    fun `per-bookmark reconcile failure preserves existing rows`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached", "bookmark-1", "Cached"))
            )
            annotationResponses["bookmark-1"] = Response.error(503, "error".toResponseBody(null))

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
            )

            val rows = database.getCachedAnnotationDao().getAnnotationsForBookmark("bookmark-1")
            val metadata = database.getCachedAnnotationDao().getBookmarkAnnotationSyncMetadata("bookmark-1")
            assertTrue(result.isFailure)
            assertEquals(listOf("cached"), rows.map { it.id })
            assertNotNull(metadata?.lastFailureAt)
            assertNotNull(metadata?.backoffUntil)
            assertEquals(1, metadata?.failureCount)
        }
    }

    @Test
    fun `cached highlights remain visible while bookmark scoped checks run`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached", "bookmark-1", "Cached"))
            )
            annotationDelaysMs["bookmark-1"] = 250
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "Remote"))
            )

            val result = repository.requestBookmarkAnnotationChecks(
                bookmarkIds = listOf("bookmark-1"),
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
            )
            waitUntil { annotationCalls.isNotEmpty() }

            val highlights = repository.observeHighlights().first()
            assertTrue(result.isSuccess)
            assertEquals(listOf("cached"), highlights.map { it.id })
            assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
        }
    }

    @Test
    fun `cached highlights remain visible after bookmark scoped check failure`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("cached", "bookmark-1", "Cached"))
            )
            annotationResponses["bookmark-1"] = Response.error(503, "error".toResponseBody(null))

            val result = repository.requestBookmarkAnnotationChecks(
                bookmarkIds = listOf("bookmark-1"),
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
            )
            waitUntil {
                database.getCachedAnnotationDao()
                    .getBookmarkAnnotationSyncMetadata("bookmark-1")
                    ?.lastFailureAt != null
            }

            val highlights = repository.observeHighlights().first()
            assertTrue(result.isSuccess)
            assertEquals(listOf("cached"), highlights.map { it.id })
            assertEquals(HighlightsSyncState.Idle, repository.observeSyncState().value)
        }
    }

    @Test
    fun `recent successful bookmark check is skipped when not forced`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().upsertBookmarkAnnotationSyncMetadata(
                com.mydeck.app.io.db.model.BookmarkAnnotationSyncMetadataEntity(
                    bookmarkId = "bookmark-1",
                    lastSuccessAt = Clock.System.now(),
                )
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
            )

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().skipped)
            assertEquals(emptyList<String>(), annotationCalls)
        }
    }

    @Test
    fun `forced bookmark check bypasses freshness throttle`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().upsertBookmarkAnnotationSyncMetadata(
                com.mydeck.app.io.db.model.BookmarkAnnotationSyncMetadataEntity(
                    bookmarkId = "bookmark-1",
                    lastSuccessAt = Clock.System.now(),
                )
            )
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "Forced"))
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.HIGHLIGHT_NAVIGATION_RETURN,
                force = true,
            )

            assertTrue(result.isSuccess)
            assertEquals(listOf("bookmark-1"), annotationCalls)
            assertEquals(false, result.getOrThrow().skipped)
        }
    }

    @Test
    fun `duplicate bookmark ids are deduped in request`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1"), bookmark("bookmark-2")))
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "One"))
            )
            annotationResponses["bookmark-2"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-2", "Two"))
            )

            val result = repository.requestBookmarkAnnotationChecks(
                bookmarkIds = listOf("bookmark-1", "bookmark-1", "bookmark-2", "bookmark-2"),
                reason = BookmarkAnnotationSyncReason.BOOKMARK_DELTA_HINT,
            )
            waitUntil { annotationCalls.size == 2 }

            assertTrue(result.isSuccess)
            assertEquals(listOf("bookmark-1", "bookmark-2"), annotationCalls)
        }
    }

    @Test
    fun `changed false is detected when remote and local annotation fingerprints match`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("remote-1", "bookmark-1", "Same", color = "yellow", note = "same note"))
            )
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "Same", color = "yellow", note = "same note"))
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.READER_REFRESH,
            )

            assertTrue(result.isSuccess)
            assertEquals(false, result.getOrThrow().changed)
        }
    }

    @Test
    fun `changed true is detected when remote differs from local`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(annotation("remote-1", "bookmark-1", "Old", color = "yellow", note = "old"))
            )
            annotationResponses["bookmark-1"] = Response.success(
                listOf(remoteBookmarkAnnotation("remote-1", "New", color = "yellow", note = "old"))
            )

            val result = repository.reconcileBookmarkAnnotationsNow(
                bookmarkId = "bookmark-1",
                reason = BookmarkAnnotationSyncReason.READER_REFRESH,
            )

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().changed)
        }
    }

    @Test
    fun `highlight count observes local Room without requesting refresh`() = runBlocking {
        withRepository { repository ->
            database.getBookmarkDao().insertBookmarks(listOf(bookmark("bookmark-1")))
            database.getCachedAnnotationDao().insertAnnotations(
                listOf(
                    annotation("cached-1", "bookmark-1", "Cached one"),
                    annotation("cached-2", "bookmark-1", "Cached two")
                )
            )

            val count = repository.observeHighlightCount().first()

            assertEquals(2, count)
            assertEquals(emptyList<Pair<Int, Int>>(), annotationSummaryCalls)
            assertEquals(emptyList<String>(), annotationCalls)
            assertEquals(null, syncMetadata.lastGlobalAttemptAt)
        }
    }

    private fun repository(applicationScope: CoroutineScope): HighlightsRepository {
        return HighlightsRepositoryImpl(
            readeckApi = readeckApi,
            cachedAnnotationDao = database.getCachedAnnotationDao(),
            bookmarkDao = database.getBookmarkDao(),
            database = database,
            settingsDataStore = settingsDataStore,
            bookmarkMetadataSyncCoordinator = bookmarkMetadataSyncCoordinator,
            syncScheduler = syncScheduler,
            applicationScope = applicationScope,
        )
    }

    private class FakeSyncScheduler : SyncScheduler {
        var orphanRepairRequests = 0

        override fun scheduleActionSync() = Unit
        override fun scheduleArticleDownload(bookmarkId: String) = Unit
        override fun scheduleBatchArticleLoad(wifiOnly: Boolean, allowBatterySaver: Boolean) = Unit
        override fun scheduleBookmarkOrphanRepairFullSync() {
            orphanRepairRequests += 1
        }
        override fun scheduleBatchArticleLoadOverridingConstraints() = Unit
    }

    private suspend fun withRepository(block: suspend (HighlightsRepository) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        try {
            block(repository(scope))
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
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

    private fun annotation(
        id: String,
        bookmarkId: String,
        text: String,
        color: String = "yellow",
        note: String? = null,
    ): CachedAnnotationEntity {
        return CachedAnnotationEntity(
            id = id,
            bookmarkId = bookmarkId,
            text = text,
            color = color,
            note = note,
            created = "2026-01-01T00:00:00Z",
        )
    }

    private fun remoteBookmarkAnnotation(
        id: String,
        text: String,
        color: String = "yellow",
        note: String = "",
        created: String = "2026-01-01T00:00:00Z",
    ): AnnotationDto {
        return AnnotationDto(
            id = id,
            start_selector = "p:nth-child(1)",
            start_offset = 0,
            end_selector = "p:nth-child(1)",
            end_offset = text.length,
            created = created,
            text = text,
            color = color,
            note = note,
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
