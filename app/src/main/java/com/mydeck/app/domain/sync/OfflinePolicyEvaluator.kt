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
        return bookmark.contentState != com.mydeck.app.io.db.model.BookmarkEntity.ContentState.DOWNLOADED
    }

    /**
     * Returns true when usage has crossed the **hard ceiling** and a prune cycle
     * should begin.
     *
     * For [OfflinePolicy.STORAGE_LIMIT] the hard ceiling is the user-configured
     * `target` itself (one-sided hysteresis — storage never intentionally exceeds
     * the limit).  For other policies the existing count / date / secondary-cap
     * rules apply unchanged.
     */
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
                val hardCeiling = settingsDataStore.getOfflinePolicyStorageLimit()
                totalUsageBytes > hardCeiling
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

    /**
     * Returns true when an active prune cycle has removed enough content to
     * bring usage at or below the **soft ceiling** (`target − avg`).
     *
     * Only meaningful after [shouldPrune] returned true; the prune loop should
     * keep removing content one bookmark at a time until this returns true.
     */
    suspend fun isPrunedEnough(
        downloadedBookmarks: List<BookmarkDao.OfflinePolicyBookmark>,
        totalUsageBytes: Long,
        now: Instant = Clock.System.now()
    ): Boolean {
        if (downloadedBookmarks.isEmpty()) return true

        return when (settingsDataStore.getOfflinePolicy()) {
            OfflinePolicy.STORAGE_LIMIT -> {
                val avg = avgArticleBytes(totalUsageBytes, downloadedBookmarks.size)
                val softCeiling = settingsDataStore.getOfflinePolicyStorageLimit() - avg
                totalUsageBytes <= softCeiling
            }
            // For non-storage policies the prune loop simply continues until
            // the original condition is no longer met.
            else -> !shouldPrune(downloadedBookmarks, totalUsageBytes, now)
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

    /**
     * Returns true when the download loop should stop fetching new content.
     *
     * For [OfflinePolicy.STORAGE_LIMIT] this is the **download threshold**
     * (`target − 4 × avg`) of the one-sided hysteresis band.  Below this
     * threshold the system keeps downloading; at or above it the loop stops.
     * The dead-band between this threshold and the hard ceiling (`target`)
     * ensures that small overshoots never trigger a prune → re-download cycle.
     *
     * For [OfflinePolicy.NEWEST_N] / [OfflinePolicy.DATE_RANGE] the secondary
     * storage cap acts as a hard stop.
     */
    suspend fun shouldStopDownloading(
        totalUsageBytes: Long,
        downloadedCount: Int
    ): Boolean {
        return when (settingsDataStore.getOfflinePolicy()) {
            OfflinePolicy.STORAGE_LIMIT -> {
                val avg = avgArticleBytes(totalUsageBytes, downloadedCount)
                val downloadThreshold =
                    settingsDataStore.getOfflinePolicyStorageLimit() - 4 * avg
                totalUsageBytes >= downloadThreshold
            }
            OfflinePolicy.NEWEST_N, OfflinePolicy.DATE_RANGE -> {
                totalUsageBytes >= settingsDataStore.getOfflineMaxStorageCap()
            }
        }
    }

    /**
     * Bytes remaining before the download threshold is reached.
     * Returns 0 when already at or past the threshold.
     */
    suspend fun downloadHeadroomBytes(
        totalUsageBytes: Long,
        downloadedCount: Int
    ): Long {
        val threshold = when (settingsDataStore.getOfflinePolicy()) {
            OfflinePolicy.STORAGE_LIMIT -> {
                val avg = avgArticleBytes(totalUsageBytes, downloadedCount)
                settingsDataStore.getOfflinePolicyStorageLimit() - 4 * avg
            }
            OfflinePolicy.NEWEST_N, OfflinePolicy.DATE_RANGE -> {
                settingsDataStore.getOfflineMaxStorageCap()
            }
        }
        return (threshold - totalUsageBytes).coerceAtLeast(0L)
    }

    private suspend fun isSecondaryCapExceeded(totalUsageBytes: Long): Boolean {
        return totalUsageBytes > settingsDataStore.getOfflineMaxStorageCap()
    }

    companion object {
        /** Fallback average when no articles have been downloaded yet. */
        const val DEFAULT_AVG_ARTICLE_BYTES = 2L * 1024 * 1024  // 2 MB

        /**
         * Raw average bytes per downloaded article.  Falls back to
         * [DEFAULT_AVG_ARTICLE_BYTES] when there is no data yet.
         */
        fun avgArticleBytes(totalUsageBytes: Long, downloadedCount: Int): Long {
            if (downloadedCount <= 0 || totalUsageBytes <= 0) {
                return DEFAULT_AVG_ARTICLE_BYTES
            }
            return totalUsageBytes / downloadedCount
        }
    }
}
