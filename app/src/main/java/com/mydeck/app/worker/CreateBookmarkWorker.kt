package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber
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
            Timber.d("CreateBookmarkWorker: Creating bookmark for URL=$url")
            val bookmarkRepository = bookmarkRepositoryProvider.get()
            val bookmarkId = bookmarkRepository.createBookmark(
                title = title,
                url = url,
                labels = labels
            )

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
        } catch (e: Exception) {
            Timber.e(e, "CreateBookmarkWorker: Failed to create bookmark")
            if (runAttemptCount < 3) {
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
        const val RESULT_BOOKMARK_ID = "bookmarkId"

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

            workManager.enqueue(request)
            Timber.d("CreateBookmarkWorker enqueued for URL=$url")
        }
    }
}
