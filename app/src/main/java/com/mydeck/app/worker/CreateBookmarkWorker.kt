package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.io.rest.isHttpBlockedByBuildPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Provider

@HiltWorker
class CreateBookmarkWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    private val bookmarkRepositoryProvider: Provider<BookmarkRepository>
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val url = workerParams.inputData.getString(PARAM_URL)
        val title = workerParams.inputData.getString(PARAM_TITLE) ?: ""
        val labels = workerParams.inputData.getStringArray(PARAM_LABELS)?.toList() ?: emptyList()
        val isArchived = workerParams.inputData.getBoolean(PARAM_IS_ARCHIVED, false)
        val isFavorite = workerParams.inputData.getBoolean(PARAM_IS_FAVORITE, false)

        if (url.isNullOrBlank()) {
            Timber.w("CreateBookmarkWorker: No URL provided")
            return Result.failure()
        }

        return try {
            val bookmarkRepository = bookmarkRepositoryProvider.get()
            // On a retry (runAttemptCount > 0) a prior POST may have reached the server even though
            // the client saw a failure (lost response on flaky cellular — the W3 duplicate cause).
            // Hand the repository the original attempt timestamp so it can reconcile against the
            // server before re-POSTing, adopting the already-created bookmark instead of duplicating.
            // The input data is preserved unchanged across retries, so this baseline is stable.
            val attemptTimestampMs = workerParams.inputData.getLong(PARAM_ATTEMPT_TS, 0L)
            val reconcileSinceMs =
                if (runAttemptCount > 0 && attemptTimestampMs > 0L) attemptTimestampMs else null
            val bookmarkId = createMutex.withLock {
                Timber.d("CreateBookmarkWorker: Creating bookmark for URL=$url (attempt=$runAttemptCount)")
                bookmarkRepository.createBookmark(
                    title = title,
                    url = url,
                    labels = labels,
                    attemptTimestampMs = reconcileSinceMs
                )
            }

            if (isArchived || isFavorite) {
                // Wait for bookmark to reach LOADED state before updating,
                // otherwise pollForBookmarkReady() in createBookmark will
                // overwrite our local flags with the server's values.
                waitForBookmarkReady(bookmarkRepository, bookmarkId)
                bookmarkRepository.updateBookmark(
                    bookmarkId = bookmarkId,
                    isFavorite = if (isFavorite) true else null,
                    isArchived = if (isArchived) true else null,
                    isRead = null
                )
            }

            Timber.d("CreateBookmarkWorker: Bookmark created successfully: $bookmarkId")
            Result.success(
                Data.Builder()
                    .putString(RESULT_BOOKMARK_ID, bookmarkId)
                    .build()
            )
        } catch (e: CancellationException) {
            Timber.w(e, "CreateBookmarkWorker: Cancelled while creating bookmark")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CreateBookmarkWorker: Failed to create bookmark")
            if (e.isHttpBlockedByBuildPolicy()) {
                Result.failure()
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun waitForBookmarkReady(
        bookmarkRepository: BookmarkRepository,
        bookmarkId: String
    ) {
        val maxAttempts = 30
        val delayMs = 2000L
        for (i in 1..maxAttempts) {
            try {
                val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
                if (bookmark.state != Bookmark.State.LOADING) {
                    Timber.d("CreateBookmarkWorker: Bookmark ready after $i polls (state=${bookmark.state})")
                    return
                }
            } catch (e: Exception) {
                Timber.w(e, "CreateBookmarkWorker: Poll attempt $i failed")
            }
            delay(delayMs)
        }
        Timber.w("CreateBookmarkWorker: Timed out waiting for bookmark $bookmarkId to be ready")
    }

    companion object {
        const val PARAM_URL = "url"
        const val PARAM_TITLE = "title"
        const val PARAM_LABELS = "labels"
        const val PARAM_IS_ARCHIVED = "isArchived"
        const val PARAM_IS_FAVORITE = "isFavorite"
        const val PARAM_ATTEMPT_TS = "attemptTimestampMs"
        const val RESULT_BOOKMARK_ID = "bookmarkId"
        // Held during the entire 60s poll loop for intentional process-global serialization
        private val createMutex = Mutex()

        /** Stable per-URL unique work name so duplicate adds of the same URL coalesce. */
        fun uniqueWorkName(url: String): String =
            "create_" + UUID.nameUUIDFromBytes(url.toByteArray())

        fun enqueue(
            workManager: WorkManager,
            url: String,
            title: String,
            labels: List<String>,
            isArchived: Boolean = false,
            isFavorite: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putString(PARAM_URL, url)
                .putString(PARAM_TITLE, title)
                .putStringArray(PARAM_LABELS, labels.toTypedArray())
                .putBoolean(PARAM_IS_ARCHIVED, isArchived)
                .putBoolean(PARAM_IS_FAVORITE, isFavorite)
                .putLong(PARAM_ATTEMPT_TS, System.currentTimeMillis())
                .build()

            val request = OneTimeWorkRequestBuilder<CreateBookmarkWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            // Coalesce duplicate adds of the same URL (double-tap / double-share) into a single
            // worker; KEEP lets the in-flight worker win, and its retry path reconciles the
            // lost-response case. Different URLs get distinct unique names, so they don't collide.
            workManager.enqueueUniqueWork(
                uniqueWorkName(url),
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.d("CreateBookmarkWorker enqueued (unique) for URL=$url")
        }
    }
}
