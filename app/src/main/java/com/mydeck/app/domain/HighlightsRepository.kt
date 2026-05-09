package com.mydeck.app.domain

import com.mydeck.app.domain.model.HighlightSummary
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.CachedAnnotationHighlightEntity
import com.mydeck.app.io.db.model.RemoteAnnotationIdEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationSummaryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface HighlightsRepository {
    fun observeHighlights(): Flow<List<HighlightSummary>>
    fun observeHighlightCount(): Flow<Int>
    fun observeSyncState(): StateFlow<HighlightsSyncState>
    suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit>
}

enum class HighlightsRefreshReason {
    SCREEN_OPEN,
    USER_RETRY,
    APP_BACKGROUND
}

sealed interface HighlightsSyncState {
    data object Idle : HighlightsSyncState
    data class Running(val loadedCount: Int? = null) : HighlightsSyncState
    data class Failed(val message: String, val cause: Throwable? = null) : HighlightsSyncState
}

@Singleton
class HighlightsRepositoryImpl @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val cachedAnnotationDao: CachedAnnotationDao,
    private val database: MyDeckDatabase,
) : HighlightsRepository {
    private val refreshMutex = Mutex()
    private val syncState = MutableStateFlow<HighlightsSyncState>(HighlightsSyncState.Idle)
    private var lastSuccessfulRefresh: Instant? = null

    override fun observeHighlights(): Flow<List<HighlightSummary>> {
        return cachedAnnotationDao.observeAllHighlights()
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHighlightCount(): Flow<Int> = cachedAnnotationDao.observeHighlightCount()

    override fun observeSyncState(): StateFlow<HighlightsSyncState> = syncState

    override suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit> {
        Timber.d(
            "Highlights refresh requested: reason=%s currentState=%s lastSuccess=%s",
            reason,
            syncState.value.toLogString(),
            lastSuccessfulRefresh
        )
        if (reason == HighlightsRefreshReason.SCREEN_OPEN && !screenOpenRefreshDue()) {
            Timber.d(
                "Highlights refresh skipped by screen-open throttle: reason=%s lastSuccess=%s",
                reason,
                lastSuccessfulRefresh
            )
            return Result.success(Unit)
        }
        if (!refreshMutex.tryLock()) {
            Timber.d(
                "Highlights refresh skipped because one is already running: reason=%s currentState=%s",
                reason,
                syncState.value.toLogString()
            )
            return Result.success(Unit)
        }
        var offset = 0
        var loadedCount = 0
        val startedAtMs = System.currentTimeMillis()
        return try {
            syncState.value = HighlightsSyncState.Running(loadedCount = 0)
            Timber.d("Highlights refresh started: reason=%s pageSize=%d", reason, HIGHLIGHTS_PAGE_SIZE)
            cachedAnnotationDao.clearRemoteAnnotationIds()
            Timber.d("Highlights refresh reconciliation table cleared")

            while (true) {
                Timber.d(
                    "Highlights refresh requesting page: reason=%s offset=%d limit=%d loaded=%d",
                    reason,
                    offset,
                    HIGHLIGHTS_PAGE_SIZE,
                    loadedCount
                )
                val response = readeckApi.getAnnotationSummaries(
                    limit = HIGHLIGHTS_PAGE_SIZE,
                    offset = offset
                )
                if (!response.isSuccessful) {
                    Timber.w(
                        "Highlights refresh HTTP failure: reason=%s offset=%d code=%d loaded=%d",
                        reason,
                        offset,
                        response.code(),
                        loadedCount
                    )
                    throw IllegalStateException("HTTP ${response.code()}")
                }
                val page = response.body().orEmpty()
                Timber.d(
                    "Highlights refresh page received: reason=%s offset=%d size=%d loadedBefore=%d",
                    reason,
                    offset,
                    page.size,
                    loadedCount
                )
                database.performTransaction {
                    if (page.isNotEmpty()) {
                        cachedAnnotationDao.insertAnnotations(page.map { it.toCachedEntity() })
                        cachedAnnotationDao.insertRemoteAnnotationIds(
                            page.map { RemoteAnnotationIdEntity(it.id) }
                        )
                    }
                }

                loadedCount += page.size
                syncState.value = HighlightsSyncState.Running(loadedCount = loadedCount)
                Timber.d(
                    "Highlights refresh page stored: reason=%s offset=%d pageSize=%d loadedTotal=%d",
                    reason,
                    offset,
                    page.size,
                    loadedCount
                )
                if (page.size < HIGHLIGHTS_PAGE_SIZE) {
                    Timber.d(
                        "Highlights refresh reached final page: reason=%s offset=%d finalPageSize=%d loadedTotal=%d",
                        reason,
                        offset,
                        page.size,
                        loadedCount
                    )
                    break
                }
                offset += HIGHLIGHTS_PAGE_SIZE
            }

            var staleDeleted = 0
            database.performTransaction {
                staleDeleted = cachedAnnotationDao.removeAnnotationsMissingFromRemote()
                cachedAnnotationDao.clearRemoteAnnotationIds()
            }
            lastSuccessfulRefresh = Clock.System.now()
            syncState.value = HighlightsSyncState.Idle
            Timber.d(
                "Highlights refresh succeeded: reason=%s loadedTotal=%d staleDeleted=%d durationMs=%d",
                reason,
                loadedCount,
                staleDeleted,
                System.currentTimeMillis() - startedAtMs
            )
            Result.success(Unit)
        } catch (e: Exception) {
            syncState.value = HighlightsSyncState.Failed(
                message = "Could not refresh highlights",
                cause = e
            )
            Timber.w(
                e,
                "Highlights refresh failed: reason=%s offset=%d loaded=%d durationMs=%d",
                reason,
                offset,
                loadedCount,
                System.currentTimeMillis() - startedAtMs
            )
            Result.failure(e)
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun screenOpenRefreshDue(): Boolean {
        val lastRefresh = lastSuccessfulRefresh ?: return true
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - lastRefresh.toEpochMilliseconds()
        return elapsedMs >= SCREEN_OPEN_REFRESH_THROTTLE_MS
    }
}

private fun HighlightsSyncState.toLogString(): String {
    return when (this) {
        HighlightsSyncState.Idle -> "Idle"
        is HighlightsSyncState.Running -> "Running(loadedCount=$loadedCount)"
        is HighlightsSyncState.Failed -> "Failed(message=$message)"
    }
}

private const val HIGHLIGHTS_PAGE_SIZE = 50
private const val SCREEN_OPEN_REFRESH_THROTTLE_MS = 5 * 60 * 1000L

private fun AnnotationSummaryDto.toCachedEntity(): CachedAnnotationEntity {
    return CachedAnnotationEntity(
        id = id,
        bookmarkId = bookmark_id,
        text = text,
        color = color.takeIf { it.isNotBlank() } ?: "yellow",
        note = note.takeIf { it.isNotBlank() },
        created = created.takeIf { it.isNotBlank() } ?: Clock.System.now().toString()
    )
}

private fun CachedAnnotationHighlightEntity.toDomain(): HighlightSummary {
    return HighlightSummary(
        id = id,
        text = text,
        color = color,
        note = note.orEmpty(),
        created = created.toInstantOrEpoch(),
        bookmarkId = bookmarkId,
        bookmarkTitle = bookmarkTitle,
        bookmarkSiteName = bookmarkSiteName,
    )
}

private fun String.toInstantOrEpoch(): Instant {
    return runCatching { Instant.parse(this) }
        .getOrElse { Instant.fromEpochMilliseconds(0) }
}
