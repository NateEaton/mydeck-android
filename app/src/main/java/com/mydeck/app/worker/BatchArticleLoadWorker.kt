package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydeck.app.domain.sync.ContentSyncPolicyEvaluator
import com.mydeck.app.domain.usecase.LoadContentPackageUseCase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.prefs.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import timber.log.Timber

@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadContentPackageUseCase: LoadContentPackageUseCase,
    private val policyEvaluator: ContentSyncPolicyEvaluator,
    private val settingsDataStore: SettingsDataStore,
    private val contentPackageManager: com.mydeck.app.domain.content.ContentPackageManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BatchArticleLoadWorker starting")

        try {
            val includeArchived = settingsDataStore.getOfflineContentScope().includesArchived
            val pendingBookmarkIds = bookmarkDao.getBookmarkIdsEligibleForContentFetch(includeArchived)
            Timber.i("Found ${pendingBookmarkIds.size} bookmarks without content (includeArchived=$includeArchived)")

            if (pendingBookmarkIds.isEmpty()) {
                return Result.success()
            }

            val batches = pendingBookmarkIds.chunked(BATCH_SIZE)
            batches.forEachIndexed { index, batch ->
                // Check constraints before each batch
                if (!policyEvaluator.canFetchContent().allowed) {
                    Timber.i("Content fetch blocked by constraints, stopping batch")
                    return Result.success()
                }

                Timber.d("Processing batch ${index + 1}, size=${batch.size}")

                coroutineScope {
                    // Use multipart batching (up to 10 IDs per request)
                    val perIdResults = loadContentPackageUseCase.executeBatch(batch)
                    // Stage 4: no background legacy fallback. Transient failures stay DIRTY for retry.
                    perIdResults.entries.map { (id, res) ->
                        async {
                            if (res is LoadContentPackageUseCase.Result.TransientFailure) {
                                Timber.d("Multipart content fetch transient failure for $id: ${res.reason}")
                            } else if (res is LoadContentPackageUseCase.Result.PermanentFailure) {
                                Timber.d("Multipart content fetch permanent failure for $id: ${res.reason}")
                            }
                        }
                    }.awaitAll()
                }

                // Brief pause between batches to reduce resource contention
                if (index < batches.size - 1) {
                    delay(BATCH_DELAY_MS)
                }

                // Check and enforce storage limit after batch completes
                enforceImageStorageLimit()
            }

            // Record the content sync timestamp
            settingsDataStore.saveLastContentSyncTimestamp(Clock.System.now())

            Timber.i("BatchArticleLoadWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BatchArticleLoadWorker failed")
            return Result.retry()
        }
    }

    private suspend fun enforceImageStorageLimit() {
        // Enforce only if we are online enough to reload text
        if (!policyEvaluator.canFetchContent().allowed) return
        
        val limit = settingsDataStore.getOfflineImageStorageLimit()
        if (limit == com.mydeck.app.domain.sync.OfflineImageStorageLimit.UNLIMITED) return
        
        var currentSize = contentPackageManager.calculateTotalSize()
        if (currentSize <= limit.bytes) return
        
        Timber.i("Storage limit exceeded (current: $currentSize, limit: ${limit.bytes}), starting prune...")
        val includeArchived = settingsDataStore.getOfflineContentScope().includesArchived
        val evictionCandidates = bookmarkDao.getOldestBookmarkIdsWithResources(includeArchived)
        
        for (id in evictionCandidates) {
            if (currentSize <= limit.bytes) break
            
            Timber.d("Pruning images for old bookmark $id to free space")
            // Fetch text-only version to overwrite resources but preserve HTML references cleanly
            val result = loadContentPackageUseCase.executeTextOnlyOverwrite(id)
            if (result is LoadContentPackageUseCase.Result.TransientFailure || result is LoadContentPackageUseCase.Result.PermanentFailure) {
                Timber.w("Failed to text-only prune bookmark $id, skipping to next candidate")
            } else {
                // calculate updated size
                currentSize = contentPackageManager.calculateTotalSize()
            }
        }
        
        Timber.i("Finished pruning. New size: $currentSize")
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_article_load"
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 500L
    }
}
