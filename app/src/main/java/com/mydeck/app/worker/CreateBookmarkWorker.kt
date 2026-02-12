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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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

            if (isArchived) {
                bookmarkRepository.updateBookmark(
                    bookmarkId = bookmarkId,
                    isFavorite = null,
                    isArchived = true,
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

    companion object {
        const val PARAM_URL = "url"
        const val PARAM_TITLE = "title"
        const val PARAM_LABELS = "labels"
        const val PARAM_IS_ARCHIVED = "isArchived"
        const val RESULT_BOOKMARK_ID = "bookmarkId"

        fun enqueue(
            workManager: WorkManager,
            url: String,
            title: String,
            labels: List<String>,
            isArchived: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putString(PARAM_URL, url)
                .putString(PARAM_TITLE, title)
                .putStringArray(PARAM_LABELS, labels.toTypedArray())
                .putBoolean(PARAM_IS_ARCHIVED, isArchived)
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
