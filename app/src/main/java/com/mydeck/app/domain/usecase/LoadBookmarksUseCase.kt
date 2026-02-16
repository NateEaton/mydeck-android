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
import com.mydeck.app.worker.BatchArticleLoadWorker
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import timber.log.Timber
import javax.inject.Inject

class LoadBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readeckApi: ReadeckApi,
    private val settingsDataStore: SettingsDataStore,
    private val policyEvaluator: ContentSyncPolicyEvaluator,
    private val workManager: WorkManager
) {

    sealed class UseCaseResult<out DataType : Any> {
        data class Success<out DataType : Any>(val  dataType: DataType) : UseCaseResult<DataType>()
        data class Error(val exception: Throwable) : UseCaseResult<Nothing>()
    }

    suspend fun execute(pageSize: Int = DEFAULT_PAGE_SIZE, initialOffset: Int = 0): UseCaseResult<Unit> {
        Timber.d("execute(pageSize=$pageSize, initialOffset=$initialOffset")

        var offset = initialOffset
        try {
            val lastLoadedTimestamp = settingsDataStore.getLastBookmarkTimestamp()
            Timber.i("Loaded last bookmark timestamp: [utc=$lastLoadedTimestamp]")

            var hasMorePages = true
            while (hasMorePages) {
                val response = readeckApi.getBookmarks(pageSize, offset, lastLoadedTimestamp, ReadeckApi.SortOrder(ReadeckApi.Sort.Created))
                if (response.isSuccessful && response.body() != null) {
                    val bookmarkDtos = response.body() as List<BookmarkDto>
                    
                    // Process each bookmark individually to prevent one malformed bookmark from aborting the entire sync
                    val bookmarks = bookmarkDtos.mapNotNull { dto ->
                        try {
                            dto.toDomain()
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to deserialize bookmark ${dto.id}, skipping")
                            null
                        }
                    }
                    
                    if (bookmarks.isEmpty() && bookmarkDtos.isNotEmpty()) {
                        Timber.e("All bookmarks in page failed deserialization")
                        return UseCaseResult.Error(Exception("All bookmarks in page failed deserialization"))
                    }

                    val totalCountHeader = response.headers()[ReadeckApi.Header.TOTAL_COUNT]
                    val totalPagesHeader = response.headers()[ReadeckApi.Header.TOTAL_PAGES]
                    val currentPageHeader = response.headers()[ReadeckApi.Header.CURRENT_PAGE]

                    if (totalCountHeader == null || totalPagesHeader == null || currentPageHeader == null) {
                        return UseCaseResult.Error(Exception("Missing headers in API response"))
                    }

                    val totalCount = totalCountHeader.toInt()
                    val totalPages = totalPagesHeader.toInt()
                    val currentPage = currentPageHeader.toInt()

                    Timber.d("currentPage=$currentPage")
                    Timber.d("totalPages=$totalPages")
                    Timber.d("totalCount=$totalCount")
                    if (bookmarks.size < bookmarkDtos.size) {
                        Timber.w("Skipped ${bookmarkDtos.size - bookmarks.size} malformed bookmarks in page $currentPage")
                    }

                    bookmarkRepository.insertBookmarks(bookmarks)

                    // Find the latest created timestamp in the current page
                    val latestBookmark = bookmarks.maxByOrNull { it.created }
                    latestBookmark?.let {
                        val timestamp = it.created.toInstant(TimeZone.currentSystemDefault())
                        settingsDataStore.saveLastBookmarkTimestamp(timestamp)
                        Timber.i("Saved last bookmark timestamp: [local=${it.created}, utc=$timestamp]")
                    }

                    if (currentPage < totalPages) {
                        offset += pageSize
                    } else {
                        hasMorePages = false
                    }
                } else {
                    Timber.w("Error loading bookmarks: [code=${response.code()}, body=${response.body()}]")
                    return UseCaseResult.Error(Exception("Unsuccessful response: ${response.code()}"))
                }
            }

            // After metadata sync completes, conditionally enqueue content sync
            if (policyEvaluator.shouldAutoFetchContent()) {
                enqueueBatchArticleLoader()
            }

            return UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error loading bookmarks")
            return UseCaseResult.Error(e)
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
}
