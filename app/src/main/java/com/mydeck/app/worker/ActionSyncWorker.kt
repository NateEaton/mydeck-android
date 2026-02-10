package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.PendingActionDao
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.LabelsPayload
import com.mydeck.app.io.db.model.PendingActionEntity
import com.mydeck.app.io.db.model.ProgressPayload
import com.mydeck.app.io.db.model.TogglePayload
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.EditBookmarkDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class ActionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val pendingActionDao: PendingActionDao,
    private val readeckApi: ReadeckApi,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val actions = pendingActionDao.getAllActionsSorted()
        Timber.d("Starting ActionSyncWorker. Pending actions: ${actions.size}")

        for (action in actions) {
            try {
                processAction(action)
                pendingActionDao.delete(action)
                Timber.d("Successfully processed action: ${action.actionType} for ${action.bookmarkId}")
            } catch (e: Exception) {
                if (isTransientError(e)) {
                    Timber.w(e, "Transient error processing action ${action.actionType}. Retrying...")
                    return Result.retry()
                } else {
                    Timber.e(e, "Permanent error processing action ${action.actionType} for ${action.bookmarkId}")
                    // Drop the action to prevent queue clog
                    pendingActionDao.delete(action)
                }
            }
        }

        return Result.success()
    }

    private suspend fun processAction(action: PendingActionEntity) {
        when (action.actionType) {
            ActionType.TOGGLE_FAVORITE -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(isMarked = payload.value))
                handleApiResponse(action.bookmarkId, response)
            }
            ActionType.TOGGLE_ARCHIVE -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(isArchived = payload.value))
                handleApiResponse(action.bookmarkId, response)
            }
            ActionType.TOGGLE_READ -> {
                val payload = json.decodeFromString<TogglePayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(readProgress = if (payload.value) 100 else 0))
                handleApiResponse(action.bookmarkId, response)
            }
            ActionType.UPDATE_PROGRESS -> {
                val payload = json.decodeFromString<ProgressPayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(readProgress = payload.progress))
                handleApiResponse(action.bookmarkId, response)
            }
            ActionType.UPDATE_LABELS -> {
                val payload = json.decodeFromString<LabelsPayload>(action.payload!!)
                val response = readeckApi.editBookmark(action.bookmarkId, EditBookmarkDto(labels = payload.labels))
                handleApiResponse(action.bookmarkId, response)
            }
            ActionType.DELETE -> {
                val response = readeckApi.deleteBookmark(action.bookmarkId)
                handleApiResponse(action.bookmarkId, response)
            }
        }
    }

    private suspend fun <T> handleApiResponse(bookmarkId: String, response: retrofit2.Response<T>) {
        if (response.isSuccessful) return

        val code = response.code()
        if (code == 404) {
            Timber.w("Bookmark $bookmarkId not found on server (404). Hard deleting locally.")
            bookmarkDao.hardDeleteBookmark(bookmarkId)
            pendingActionDao.deleteAllForBookmark(bookmarkId)
            return
        }

        throw Exception("API Error: $code - ${response.errorBody()?.string()}")
    }

    private fun isTransientError(e: Exception): Boolean {
        return e is IOException || e.message?.contains("500") == true || e.message?.contains("429") == true || e.message?.contains("408") == true
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "OfflineActionSync"

        fun enqueue(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ActionSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
