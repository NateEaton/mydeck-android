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
