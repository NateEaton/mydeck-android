package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.sync.OfflinePolicyEvaluator
import com.mydeck.app.domain.sync.SyncScheduler
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import javax.inject.Inject

class LoadBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val multipartSyncClient: MultipartSyncClient,
    private val settingsDataStore: SettingsDataStore,
    private val policyEvaluator: OfflinePolicyEvaluator,
    private val syncScheduler: SyncScheduler
) {

    sealed class UseCaseResult<out DataType : Any> {
        data class Success<out DataType : Any>(val  dataType: DataType) : UseCaseResult<DataType>()
        data class Error(val exception: Throwable) : UseCaseResult<Nothing>()
    }

    /**
     * Load bookmark metadata for the given updated IDs via multipart sync.
     * Requires non-empty updatedIds — callers that need to bootstrap from scratch
     * should use BookmarkRepository.performFullSync() instead.
     */
    suspend fun execute(
        updatedIds: List<String>? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        initialOffset: Int = 0,
        enqueueContentSyncAfterLoad: Boolean = true
    ): UseCaseResult<Unit> {
        return try {
            if (updatedIds != null && updatedIds.isNotEmpty()) {
                executeMultipart(updatedIds, enqueueContentSyncAfterLoad)
            } else {
                // No updated IDs — nothing to reload
                Timber.d("No updated IDs to reload via multipart")
                if (enqueueContentSyncAfterLoad) {
                    enqueueContentSyncIfNeeded()
                }
                UseCaseResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading bookmarks")
            UseCaseResult.Error(e)
        }
    }

    /**
     * Primary path: fetch metadata for specific bookmark IDs via POST /bookmarks/sync.
     */
    private suspend fun executeMultipart(
        updatedIds: List<String>,
        enqueueContentSyncAfterLoad: Boolean
    ): UseCaseResult<Unit> {
        Timber.d("executeMultipart: fetching metadata for ${updatedIds.size} bookmarks")

        val result = multipartSyncClient.fetchMetadata(updatedIds)

        when (result) {
            is MultipartSyncClient.Result.Success -> {
                val bookmarks = result.packages.mapNotNull { pkg ->
                    val dto = pkg.json
                    if (dto == null) {
                        Timber.w("No JSON part in multipart response for bookmark ${pkg.bookmarkId}")
                        return@mapNotNull null
                    }
                    try {
                        dto.toDomain()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to map bookmark ${pkg.bookmarkId}, skipping")
                        null
                    }
                }

                if (bookmarks.isNotEmpty()) {
                    bookmarkRepository.insertBookmarks(bookmarks)
                    Timber.i("Multipart metadata sync: inserted ${bookmarks.size} bookmarks")
                }

                // Update cursor based on the max updated timestamp from the synced bookmarks
                val maxUpdatedCursor = bookmarks
                    .maxByOrNull { it.updated }
                    ?.updated
                    ?.toInstant(TimeZone.currentSystemDefault())
                    ?.truncateToSyncCursor()

                val storedCursor = settingsDataStore.getLastBookmarkTimestamp()
                if (maxUpdatedCursor != null &&
                    (storedCursor == null || maxUpdatedCursor > storedCursor)
                ) {
                    settingsDataStore.saveLastBookmarkTimestamp(maxUpdatedCursor)
                    Timber.i("Saved last bookmark timestamp: [utc=$maxUpdatedCursor]")
                }

                bookmarkRepository.refreshServerErrorFlags()
                if (enqueueContentSyncAfterLoad) {
                    enqueueContentSyncIfNeeded()
                }

                return UseCaseResult.Success(Unit)
            }
            is MultipartSyncClient.Result.Error -> {
                Timber.w("Multipart metadata sync failed: ${result.message}")
                return UseCaseResult.Error(Exception(result.message))
            }
            is MultipartSyncClient.Result.NetworkError -> {
                Timber.w(result.exception, "Network error during multipart metadata sync")
                return UseCaseResult.Error(result.exception)
            }
        }
    }

    suspend fun enqueueContentSyncIfNeeded(userInitiated: Boolean = false) {
        if (policyEvaluator.shouldAutoFetchContent()) {
            enqueueBatchArticleLoader(userInitiated)
        }
    }

    private suspend fun enqueueBatchArticleLoader(userInitiated: Boolean = false) {
        val syncConstraints = settingsDataStore.getContentSyncConstraints()
        syncScheduler.scheduleBatchArticleLoad(
            wifiOnly = syncConstraints.wifiOnly,
            allowBatterySaver = syncConstraints.allowOnBatterySaver,
            userInitiated = userInitiated
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }

    private fun Instant.truncateToSyncCursor(): Instant = Instant.fromEpochSeconds(epochSeconds)
}
