package com.mydeck.app.domain.sync

import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class OfflinePolicyEvaluatorTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var evaluator: OfflinePolicyEvaluator

    @Before
    fun setup() {
        settingsDataStore = mockk()
        connectivityMonitor = mockk()
        evaluator = OfflinePolicyEvaluator(settingsDataStore, connectivityMonitor)

        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT
        coEvery { settingsDataStore.getOfflinePolicyStorageLimit() } returns OfflinePolicyDefaults.STORAGE_LIMIT_BYTES
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns OfflinePolicyDefaults.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns OfflinePolicyDefaults.DATE_RANGE_WINDOW
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns OfflinePolicyDefaults.MAX_STORAGE_CAP_BYTES
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when wifiOnly is true but not on WiFi`() = runTest {
        val constraints = ContentSyncConstraints(wifiOnly = true, allowOnBatterySaver = true)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns false
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false

        val decision = evaluator.canFetchContent()

        assertFalse(decision.allowed)
        assertEquals("Wi-Fi required", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when battery saver is on and not allowed`() = runTest {
        val constraints = ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = false)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns true

        val decision = evaluator.canFetchContent()

        assertFalse(decision.allowed)
        assertEquals("Battery saver active", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when no network available`() = runTest {
        val constraints = ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = true)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns false
        every { connectivityMonitor.isBatterySaverOn() } returns false

        val decision = evaluator.canFetchContent()

        assertFalse(decision.allowed)
        assertEquals("No network", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=true) when all constraints satisfied`() = runTest {
        val constraints = ContentSyncConstraints(wifiOnly = true, allowOnBatterySaver = false)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false

        val decision = evaluator.canFetchContent()

        assertTrue(decision.allowed)
        assertEquals(null, decision.blockedReason)
    }

    @Test
    fun `shouldAutoFetchContent returns true when offline reading is enabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true

        assertTrue(evaluator.shouldAutoFetchContent())
    }

    @Test
    fun `shouldAutoFetchContent returns false when offline reading is disabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns false

        assertFalse(evaluator.shouldAutoFetchContent())
    }

    // --- needsOfflinePackage ---

    @Test
    fun `needsOfflinePackage returns true when content not downloaded`() {
        val bk = bookmark(id = "1", created = "2026-01-01T00:00:00Z",
            contentState = BookmarkEntity.ContentState.NOT_ATTEMPTED,
            hasOfflinePackage = false)

        assertTrue(evaluator.needsOfflinePackage(bk))
    }

    @Test
    fun `needsOfflinePackage returns true when downloaded but no offline package`() {
        val bk = bookmark(id = "1", created = "2026-01-01T00:00:00Z",
            contentState = BookmarkEntity.ContentState.DOWNLOADED,
            hasOfflinePackage = false)

        assertTrue(evaluator.needsOfflinePackage(bk))
    }

    @Test
    fun `needsOfflinePackage returns false when downloaded with offline package`() {
        val bk = bookmark(id = "1", created = "2026-01-01T00:00:00Z",
            contentState = BookmarkEntity.ContentState.DOWNLOADED,
            hasOfflinePackage = true)

        assertFalse(evaluator.needsOfflinePackage(bk))
    }

    // --- selectEligibleBookmarks: STORAGE_LIMIT policy ---

    @Test
    fun `selectEligibleBookmarks returns all bookmarks sorted newest first for storage limit policy`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-03-01T00:00:00Z"),
                bookmark(id = "3", created = "2026-02-01T00:00:00Z")
            )
        )

        assertEquals(listOf("2", "3", "1"), eligible.map { it.id })
    }

    @Test
    fun `selectEligibleBookmarks returns empty list for empty input`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT

        val eligible = evaluator.selectEligibleBookmarks(emptyList())

        assertTrue(eligible.isEmpty())
    }

    // --- selectEligibleBookmarks: NEWEST_N policy ---

    @Test
    fun `selectEligibleBookmarks returns newest n bookmarks for newest policy`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 2

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z"),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z")
            )
        )

        assertEquals(listOf("3", "2"), eligible.map { it.id })
    }

    @Test
    fun `selectEligibleBookmarks with newest N at exact count returns all`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 3

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z"),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z")
            )
        )

        assertEquals(3, eligible.size)
    }

    @Test
    fun `selectEligibleBookmarks with newest N at count plus one excludes oldest`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 2

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z"),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z")
            )
        )

        assertEquals(2, eligible.size)
        assertFalse(eligible.any { it.id == "1" })
    }

    @Test
    fun `selectEligibleBookmarks with newest N fewer than limit returns all`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 10

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z")
            )
        )

        assertEquals(2, eligible.size)
    }

    // --- selectEligibleBookmarks: DATE_RANGE policy ---

    @Test
    fun `selectEligibleBookmarks returns only bookmarks inside date range window`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
                bookmark(id = "2", created = "2026-03-10T00:00:00Z"),
                bookmark(id = "3", created = "2026-03-25T00:00:00Z")
            ),
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertEquals(listOf("3", "2"), eligible.map { it.id })
    }

    @Test
    fun `selectEligibleBookmarks date range includes bookmark exactly at cutoff`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val now = Instant.parse("2026-04-01T00:00:00Z")
        val exactlyCutoff = now - 30.days

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(bookmark(id = "1", created = exactlyCutoff.toString())),
            now = now
        )

        assertEquals(listOf("1"), eligible.map { it.id })
    }

    @Test
    fun `selectEligibleBookmarks date range excludes bookmark one second before cutoff`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val now = Instant.parse("2026-04-01T00:00:00Z")
        val justBeforeCutoff = now - 30.days - 1.seconds

        val eligible = evaluator.selectEligibleBookmarks(
            listOf(bookmark(id = "1", created = justBeforeCutoff.toString())),
            now = now
        )

        assertTrue(eligible.isEmpty())
    }

    // --- isEligible ---

    @Test
    fun `isEligible returns true for bookmark inside eligible set`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 1

        val bookmarks = listOf(
            bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
            bookmark(id = "2", created = "2026-03-01T00:00:00Z")
        )

        assertTrue(evaluator.isEligible(bookmarks[1], bookmarks))
    }

    @Test
    fun `isEligible returns false for bookmark outside eligible set`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 1

        val bookmarks = listOf(
            bookmark(id = "1", created = "2026-01-01T00:00:00Z"),
            bookmark(id = "2", created = "2026-03-01T00:00:00Z")
        )

        assertFalse(evaluator.isEligible(bookmarks[0], bookmarks))
    }

    // --- shouldPrune: STORAGE_LIMIT policy ---

    @Test
    fun `shouldPrune returns false for empty downloaded list`() = runTest {
        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = emptyList(),
            totalUsageBytes = Long.MAX_VALUE
        )

        assertFalse(shouldPrune)
    }

    @Test
    fun `shouldPrune storage limit returns false when usage at limit`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT
        coEvery { settingsDataStore.getOfflinePolicyStorageLimit() } returns 1000L

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 1000L
        )

        assertFalse(shouldPrune)
    }

    @Test
    fun `shouldPrune storage limit returns true when usage exceeds limit by one byte`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT
        coEvery { settingsDataStore.getOfflinePolicyStorageLimit() } returns 1000L

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 1001L
        )

        assertTrue(shouldPrune)
    }

    // --- shouldPrune: NEWEST_N policy ---

    @Test
    fun `shouldPrune returns true when newest policy exceeds count`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 1

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L
        )

        assertTrue(shouldPrune)
    }

    @Test
    fun `shouldPrune newest N returns false when count at exactly N`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 2

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L
        )

        assertFalse(shouldPrune)
    }

    @Test
    fun `shouldPrune newest N returns true when secondary cap exceeded even if count within N`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 10
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 501L
        )

        assertTrue(shouldPrune)
    }

    @Test
    fun `shouldPrune newest N returns false when secondary cap at exactly limit`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 10
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 500L
        )

        assertFalse(shouldPrune)
    }

    // --- shouldPrune: DATE_RANGE policy ---

    @Test
    fun `shouldPrune date range returns true when bookmark outside window`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertTrue(shouldPrune)
    }

    @Test
    fun `shouldPrune date range returns false when all bookmarks inside window`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-03-20T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertFalse(shouldPrune)
    }

    @Test
    fun `shouldPrune date range returns true when secondary cap exceeded even if all within window`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val shouldPrune = evaluator.shouldPrune(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 501L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertTrue(shouldPrune)
    }

    // --- selectForPruning: STORAGE_LIMIT policy ---

    @Test
    fun `selectForPruning storage limit returns all ids oldest first`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.STORAGE_LIMIT

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 2000L
        )

        assertEquals(listOf("1", "2", "3"), pruneIds)
    }

    // --- selectForPruning: NEWEST_N policy ---

    @Test
    fun `selectForPruning newest N returns only overflow bookmarks when no secondary cap exceeded`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 2

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L
        )

        assertEquals(listOf("1"), pruneIds)
    }

    @Test
    fun `selectForPruning newest N returns all oldest first when secondary cap exceeded`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 2
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "3", created = "2026-03-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 501L
        )

        assertEquals(listOf("1", "2", "3"), pruneIds)
    }

    @Test
    fun `selectForPruning newest N returns all when no overflow but secondary cap exceeded`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.NEWEST_N
        coEvery { settingsDataStore.getOfflinePolicyNewestN() } returns 5
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-02-01T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 501L
        )

        assertEquals(listOf("1", "2"), pruneIds)
    }

    // --- selectForPruning: DATE_RANGE policy ---

    @Test
    fun `selectForPruning returns aged out bookmarks for date range policy`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-03-10T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "3", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertEquals(listOf("1"), pruneIds)
    }

    @Test
    fun `selectForPruning date range returns all oldest first when secondary cap exceeded`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days
        coEvery { settingsDataStore.getOfflineMaxStorageCap() } returns 500L

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-01-01T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 501L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertEquals(listOf("1", "2"), pruneIds)
    }

    @Test
    fun `selectForPruning date range returns empty when all within window and under cap`() = runTest {
        coEvery { settingsDataStore.getOfflinePolicy() } returns OfflinePolicy.DATE_RANGE
        coEvery { settingsDataStore.getOfflinePolicyDateRangeWindow() } returns 30.days

        val pruneIds = evaluator.selectForPruning(
            downloadedBookmarks = listOf(
                bookmark(id = "1", created = "2026-03-20T00:00:00Z", hasOfflinePackage = true),
                bookmark(id = "2", created = "2026-03-25T00:00:00Z", hasOfflinePackage = true)
            ),
            totalUsageBytes = 100L,
            now = Instant.parse("2026-04-01T00:00:00Z")
        )

        assertTrue(pruneIds.isEmpty())
    }

    private fun bookmark(
        id: String,
        created: String,
        hasOfflinePackage: Boolean = false,
        contentState: BookmarkEntity.ContentState = BookmarkEntity.ContentState.NOT_ATTEMPTED
    ): BookmarkDao.OfflinePolicyBookmark {
        return BookmarkDao.OfflinePolicyBookmark(
            id = id,
            created = Instant.parse(created),
            contentState = contentState,
            hasOfflinePackage = hasOfflinePackage
        )
    }
}
