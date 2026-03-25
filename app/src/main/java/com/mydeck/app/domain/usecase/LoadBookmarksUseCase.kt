package com.mydeck.app.domain.usecase

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.mapper.toDomain
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.BookmarkDto
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import com.mydeck.app.worker.BatchArticleLoadWorker
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import javax.inject.Inject

class LoadBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val multipartSyncClient: MultipartSyncClient,
    private val settingsDataStore: SettingsDataStore,
    private val policyEvaluator: ContentSyncPolicyEvaluator,
    private val workManager: WorkManager
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
        initialOffset: Int = 0
    ): UseCaseResult<Unit> {
        return try {
            if (updatedIds != null && updatedIds.isNotEmpty()) {
                executeMultipart(updatedIds)
            } else {
                // No updated IDs — nothing to reload
                Timber.d("No updated IDs to reload via multipart")
                enqueueContentSyncIfNeeded()
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
    private suspend fun executeMultipart(updatedIds: List<String>): UseCaseResult<Unit> {
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

                refreshServerErrorFlags(DEFAULT_PAGE_SIZE)
                enqueueContentSyncIfNeeded()

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

    suspend fun enqueueContentSyncIfNeeded() {
        if (policyEvaluator.shouldAutoFetchContent()) {
            enqueueBatchArticleLoader()
        }
    }

    private fun enqueueBatchArticleLoader() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                BatchArticleLoadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.d("Batch article loader enqueued (policy: AUTOMATIC)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue batch article loader")
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }

    private fun Instant.truncateToSyncCursor(): Instant = Instant.fromEpochSeconds(epochSeconds)

    private suspend fun refreshServerErrorFlags(pageSize: Int) {
        try {
            var offset = 0
            var hasMorePages = true
            val serverErrorIds = mutableSetOf<String>()

            while (hasMorePages) {
                val response = readeckApi.getBookmarks(
                    limit = pageSize,
                    offset = offset,
                    updatedSince = null,
                    sortOrder = ReadeckApi.SortOrder(ReadeckApi.Sort.Created),
                    hasErrors = true
                )

                if (!response.isSuccessful || response.body() == null) {
                    Timber.w("Skipping server error flag refresh: [code=${response.code()}]")
                    return
                }

                val bookmarkDtos = response.body() ?: emptyList()
                serverErrorIds += bookmarkDtos.map { it.id }

                val totalPagesHeader = response.headers()[ReadeckApi.Header.TOTAL_PAGES]
                val currentPageHeader = response.headers()[ReadeckApi.Header.CURRENT_PAGE]
                if (totalPagesHeader == null || currentPageHeader == null) {
                    Timber.w("Skipping server error flag refresh due to missing pagination headers")
                    return
                }

                val totalPages = totalPagesHeader.toInt()
                val currentPage = currentPageHeader.toInt()
                if (currentPage < totalPages) {
                    offset += pageSize
                } else {
                    hasMorePages = false
                }
            }

            bookmarkRepository.replaceServerErrorFlags(serverErrorIds)
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh server error flags")
        }
    }
}
