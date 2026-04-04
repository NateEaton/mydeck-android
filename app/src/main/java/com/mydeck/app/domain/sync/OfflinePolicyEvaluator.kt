package com.mydeck.app.domain.sync

import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import javax.inject.Inject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class OfflinePolicyEvaluator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectivityMonitor: ConnectivityMonitor
) {
    data class Decision(
        val allowed: Boolean,
        val blockedReason: String? = null
    )

    suspend fun canFetchContent(): Decision {
        if (!connectivityMonitor.isNetworkAvailable()) {
            return Decision(false, "No network")
        }

        val constraints = settingsDataStore.getContentSyncConstraints()

        if (constraints.wifiOnly && !connectivityMonitor.isOnWifi()) {
            return Decision(false, "Wi-Fi required")
        }

        if (!constraints.allowOnBatterySaver && connectivityMonitor.isBatterySaverOn()) {
            return Decision(false, "Battery saver active")
        }

        return Decision(true)
    }

    suspend fun shouldAutoFetchContent(): Boolean {
        return settingsDataStore.isOfflineReadingEnabled()
    }

    suspend fun isEligible(
        bookmark: BookmarkDao.OfflinePolicyBookmark,
        rankedBookmarks: List<BookmarkDao.OfflinePolicyBookmark>,
        now: Instant = Clock.System.now()
    ): Boolean {
        return selectEligibleBookmarks(rankedBookmarks, now).any { it.id == bookmark.id }
    }

    suspend fun selectEligibleBookmarks(
        bookmarks: List<BookmarkDao.OfflinePolicyBookmark>,
        now: Instant = Clock.System.now()
    ): List<BookmarkDao.OfflinePolicyBookmark> {
        val policy = settingsDataStore.getOfflinePolicy()

        return when (policy) {
            OfflinePolicy.STORAGE_LIMIT -> bookmarks.sortedByDescending { it.created }
            OfflinePolicy.NEWEST_N -> {
                val newestN = settingsDataStore.getOfflinePolicyNewestN()
                bookmarks.sortedByDescending { it.created }.take(newestN)
            }

            OfflinePolicy.DATE_RANGE -> {
                val cutoff = now - settingsDataStore.getOfflinePolicyDateRangeWindow()
                bookmarks
                    .filter { it.created >= cutoff }
                    .sortedByDescending { it.created }
            }
        }
    }

    fun needsOfflinePackage(bookmark: BookmarkDao.OfflinePolicyBookmark): Boolean {
        return bookmark.contentState != com.mydeck.app.io.db.model.BookmarkEntity.ContentState.DOWNLOADED ||
            !bookmark.hasOfflinePackage
    }

    suspend fun shouldPrune(
        downloadedBookmarks: List<BookmarkDao.OfflinePolicyBookmark>,
        totalUsageBytes: Long,
        now: Instant = Clock.System.now()
    ): Boolean {
        if (downloadedBookmarks.isEmpty()) {
            return false
        }

        return when (settingsDataStore.getOfflinePolicy()) {
            OfflinePolicy.STORAGE_LIMIT -> {
                totalUsageBytes > settingsDataStore.getOfflinePolicyStorageLimit()
            }

            OfflinePolicy.NEWEST_N -> {
                isSecondaryCapExceeded(totalUsageBytes) ||
                    downloadedBookmarks.size > settingsDataStore.getOfflinePolicyNewestN()
            }

            OfflinePolicy.DATE_RANGE -> {
                val cutoff = now - settingsDataStore.getOfflinePolicyDateRangeWindow()
                isSecondaryCapExceeded(totalUsageBytes) ||
                    downloadedBookmarks.any { it.created < cutoff }
            }
        }
    }

    suspend fun selectForPruning(
        downloadedBookmarks: List<BookmarkDao.OfflinePolicyBookmark>,
        totalUsageBytes: Long,
        now: Instant = Clock.System.now()
    ): List<String> {
        val oldestFirst = downloadedBookmarks.sortedBy { it.created }

        return when (settingsDataStore.getOfflinePolicy()) {
            OfflinePolicy.STORAGE_LIMIT -> oldestFirst.map { it.id }
            OfflinePolicy.NEWEST_N -> {
                val overflowCount =
                    (downloadedBookmarks.size - settingsDataStore.getOfflinePolicyNewestN()).coerceAtLeast(0)
                if (isSecondaryCapExceeded(totalUsageBytes) || overflowCount == 0) {
                    oldestFirst.map { it.id }
                } else {
                    oldestFirst.take(overflowCount).map { it.id }
                }
            }

            OfflinePolicy.DATE_RANGE -> {
                if (isSecondaryCapExceeded(totalUsageBytes)) {
                    oldestFirst.map { it.id }
                } else {
                    val cutoff = now - settingsDataStore.getOfflinePolicyDateRangeWindow()
                    oldestFirst.filter { it.created < cutoff }.map { it.id }
                }
            }
        }
    }

    private suspend fun isSecondaryCapExceeded(totalUsageBytes: Long): Boolean {
        return totalUsageBytes > settingsDataStore.getOfflineMaxStorageCap()
    }
}
