package com.mydeck.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mydeck.app.domain.model.ProgressPayload
import com.mydeck.app.domain.model.TogglePayload
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.PendingActionDao
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.PendingActionEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.EditBookmarkDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ActionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingActionDao: PendingActionDao,
    private val bookmarkDao: BookmarkDao,
    private val readeckApi: ReadeckApi,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val actions = pendingActionDao.getAllActionsSorted()
        for (action in actions) {
            when (val outcome = processAction(action)) {
                is ActionOutcome.Success -> pendingActionDao.deleteAction(action.id)
                is ActionOutcome.Retry -> return Result.retry()
                is ActionOutcome.Drop -> pendingActionDao.deleteAction(action.id)
            }
        }
        return Result.success()
    }

    private suspend fun processAction(action: PendingActionEntity): ActionOutcome {
        return when (action.actionType) {
            ActionType.UPDATE_PROGRESS -> handleProgress(action)
            ActionType.TOGGLE_FAVORITE -> handleToggle(action) { value ->
                EditBookmarkDto(isMarked = value)
            }
            ActionType.TOGGLE_ARCHIVE -> handleToggle(action) { value ->
                EditBookmarkDto(isArchived = value)
            }
            ActionType.TOGGLE_READ -> handleToggle(action) { value ->
                EditBookmarkDto(readProgress = if (value) 100 else 0)
            }
            ActionType.DELETE -> handleDelete(action)
        }
    }

    private suspend fun handleProgress(action: PendingActionEntity): ActionOutcome {
        val payload = action.payload ?: return ActionOutcome.Drop("Missing payload for progress update.")
        val progressPayload = decodePayload<ProgressPayload>(payload) ?: return ActionOutcome.Drop(
            "Invalid progress payload."
        )
        return handleEdit(action, EditBookmarkDto(readProgress = progressPayload.progress))
    }

    private suspend fun handleToggle(
        action: PendingActionEntity,
        mapper: (Boolean) -> EditBookmarkDto
    ): ActionOutcome {
        val payload = action.payload ?: return ActionOutcome.Drop("Missing payload for toggle.")
        val togglePayload = decodePayload<TogglePayload>(payload) ?: return ActionOutcome.Drop(
            "Invalid toggle payload."
        )
        return handleEdit(action, mapper(togglePayload.value))
    }

    private suspend fun handleEdit(action: PendingActionEntity, body: EditBookmarkDto): ActionOutcome {
        return try {
            val response = readeckApi.editBookmark(id = action.bookmarkId, body = body)
            when {
                response.isSuccessful -> ActionOutcome.Success
                response.code() == 404 -> {
                    bookmarkDao.deleteBookmark(action.bookmarkId)
                    ActionOutcome.Success
                }
                response.code().isTransientError() -> ActionOutcome.Retry("Transient error ${response.code()}")
                else -> ActionOutcome.Drop("Permanent error ${response.code()}")
            }
        } catch (e: IOException) {
            Timber.w(e, "Transient failure syncing action ${action.id}")
            ActionOutcome.Retry(e.message)
        } catch (e: Exception) {
            Timber.e(e, "Permanent failure syncing action ${action.id}")
            ActionOutcome.Drop(e.message)
        }
    }

    private suspend fun handleDelete(action: PendingActionEntity): ActionOutcome {
        return try {
            val response = readeckApi.deleteBookmark(id = action.bookmarkId)
            when {
                response.isSuccessful || response.code() == 404 -> {
                    bookmarkDao.deleteBookmark(action.bookmarkId)
                    ActionOutcome.Success
                }
                response.code().isTransientError() -> ActionOutcome.Retry("Transient error ${response.code()}")
                else -> ActionOutcome.Drop("Permanent error ${response.code()}")
            }
        } catch (e: IOException) {
            Timber.w(e, "Transient failure deleting bookmark ${action.bookmarkId}")
            ActionOutcome.Retry(e.message)
        } catch (e: Exception) {
            Timber.e(e, "Permanent failure deleting bookmark ${action.bookmarkId}")
            ActionOutcome.Drop(e.message)
        }
    }

    private inline fun <reified T> decodePayload(payload: String): T? {
        return try {
            json.decodeFromString<T>(payload)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse payload: $payload")
            null
        }
    }

    private fun Int.isTransientError(): Boolean {
        return this in 500..599 || this == 408 || this == 429
    }

    private sealed class ActionOutcome {
        data object Success : ActionOutcome()
        data class Retry(val reason: String? = null) : ActionOutcome()
        data class Drop(val reason: String? = null) : ActionOutcome()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "OfflineActionSync"

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ActionSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND, request)
        }
    }
}
