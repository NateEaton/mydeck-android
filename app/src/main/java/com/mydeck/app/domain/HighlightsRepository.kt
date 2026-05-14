package com.mydeck.app.domain

import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.domain.model.HighlightSummary
import com.mydeck.app.domain.model.HighlightsSyncMetadata
import com.mydeck.app.domain.sync.BookmarkMetadataSyncCoordinator
import com.mydeck.app.domain.sync.SyncScheduler
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.model.BookmarkAnnotationSyncMetadataEntity
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.CachedAnnotationHighlightEntity
import com.mydeck.app.io.db.model.RemoteAnnotationIdEntity
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import com.mydeck.app.io.rest.model.AnnotationSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    fun observeSyncMetadata(): StateFlow<HighlightsSyncMetadata>
    suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit>
    suspend fun requestBookmarkAnnotationChecks(
        bookmarkIds: Collection<String>,
        reason: BookmarkAnnotationSyncReason,
        priority: SyncPriority = SyncPriority.Normal,
    ): Result<Unit>

    suspend fun reconcileBookmarkAnnotationsNow(
        bookmarkId: String,
        reason: BookmarkAnnotationSyncReason,
        force: Boolean = false,
    ): Result<BookmarkAnnotationReconcileResult>
}

enum class HighlightsRefreshReason {
    SCREEN_OPEN,
    USER_RETRY,
    APP_OPEN,
    MANUAL_SYNC,
    PERIODIC_BACKSTOP,
    ORPHAN_REPAIR,
}

enum class BookmarkAnnotationSyncReason {
    BOOKMARK_DELTA_HINT,
    HIGHLIGHT_NAVIGATION_RETURN,
    READER_REFRESH,
    CONTENT_PACKAGE_ENRICHMENT,
    MANUAL_BOOKMARK_REFRESH,
}

enum class SyncPriority {
    Normal,
    UserVisible,
}

data class BookmarkAnnotationReconcileResult(
    val bookmarkId: String,
    val previousCount: Int,
    val remoteCount: Int?,
    val changed: Boolean,
    val skipped: Boolean,
    val reason: String,
)

sealed interface HighlightsSyncState {
    data object Idle : HighlightsSyncState
    data class Running(val loadedCount: Int? = null) : HighlightsSyncState
    data class Failed(val message: String, val cause: Throwable? = null) : HighlightsSyncState
}

@Singleton
class HighlightsRepositoryImpl @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val cachedAnnotationDao: CachedAnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val database: MyDeckDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val bookmarkMetadataSyncCoordinator: BookmarkMetadataSyncCoordinator,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : HighlightsRepository {
    private val refreshMutex = Mutex()
    private val bookmarkCheckMutex = Mutex()
    private val runningBookmarkChecks = mutableSetOf<String>()
    private val syncState = MutableStateFlow<HighlightsSyncState>(HighlightsSyncState.Idle)
    private val syncMetadata = MutableStateFlow(HighlightsSyncMetadata())

    init {
        applicationScope.launch {
            syncMetadata.value = settingsDataStore.getHighlightsSyncMetadata()
        }
    }

    private suspend fun persistSyncMetadata(metadata: HighlightsSyncMetadata) {
        settingsDataStore.saveHighlightsSyncMetadata(metadata)
        syncMetadata.value = metadata
    }

    override fun observeHighlights(): Flow<List<HighlightSummary>> {
        return cachedAnnotationDao.observeAllHighlights()
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHighlightCount(): Flow<Int> = cachedAnnotationDao.observeHighlightCount()
        .onEach { count ->
            Timber.d(
                "Highlights count emitted from local Room: count=%d source=cached_annotation",
                count
            )
        }

    override fun observeSyncState(): StateFlow<HighlightsSyncState> = syncState

    override fun observeSyncMetadata(): StateFlow<HighlightsSyncMetadata> = syncMetadata

    override suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit> {
        val metadata = settingsDataStore.getHighlightsSyncMetadata()
        Timber.d(
            "Highlights refresh requested: reason=%s currentState=%s cacheComplete=%s lastSuccess=%s lastFailure=%s backoffUntil=%s",
            reason,
            syncState.value.toLogString(),
            metadata.cacheComplete,
            metadata.lastGlobalSuccessAt,
            metadata.lastGlobalFailureAt,
            metadata.globalBackoffUntil
        )
        applicationScope.launch {
            reconcileAllAnnotations(reason)
        }
        Timber.d("Global highlights reconciliation scheduled: reason=%s", reason)
        return Result.success(Unit)
    }

    override suspend fun requestBookmarkAnnotationChecks(
        bookmarkIds: Collection<String>,
        reason: BookmarkAnnotationSyncReason,
        priority: SyncPriority,
    ): Result<Unit> {
        val distinctIds = bookmarkIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val enqueuedIds = bookmarkCheckMutex.withLock {
            distinctIds.filter { runningBookmarkChecks.add(it) }
        }
        val duplicateOrRunning = distinctIds.size - enqueuedIds.size
        Timber.d(
            "Bookmark annotation checks enqueued: reason=%s requested=%d enqueued=%d duplicateOrRunning=%d priority=%s",
            reason,
            bookmarkIds.size,
            enqueuedIds.size,
            duplicateOrRunning,
            priority
        )
        if (enqueuedIds.isEmpty()) {
            return Result.success(Unit)
        }
        val force = priority == SyncPriority.UserVisible
        applicationScope.launch {
            var skippedRecent = 0
            var skippedBackoff = 0
            var succeeded = 0
            var failed = 0
            var changed = 0
            enqueuedIds.forEach { bookmarkId ->
                try {
                    val result = reconcileBookmarkAnnotationsNow(
                        bookmarkId = bookmarkId,
                        reason = reason,
                        force = force,
                    )
                    result
                        .onSuccess { checkResult ->
                            if (checkResult.skipped) {
                                if (checkResult.reason.contains("backoff", ignoreCase = true)) {
                                    skippedBackoff += 1
                                } else {
                                    skippedRecent += 1
                                }
                            } else {
                                succeeded += 1
                                if (checkResult.changed) {
                                    changed += 1
                                }
                            }
                        }
                        .onFailure { cause ->
                            Timber.w(cause, "Per-bookmark annotation check failed: bookmarkId=%s", bookmarkId)
                            failed += 1
                        }
                } finally {
                    bookmarkCheckMutex.withLock {
                        runningBookmarkChecks.remove(bookmarkId)
                    }
                }
            }
            Timber.d(
                "Bookmark annotation checks completed: reason=%s requested=%d enqueued=%d duplicateOrRunning=%d skippedRecent=%d skippedBackoff=%d succeeded=%d failed=%d changed=%d priority=%s",
                reason,
                bookmarkIds.size,
                enqueuedIds.size,
                duplicateOrRunning,
                skippedRecent,
                skippedBackoff,
                succeeded,
                failed,
                changed,
                priority
            )
        }
        return Result.success(Unit)
    }

    override suspend fun reconcileBookmarkAnnotationsNow(
        bookmarkId: String,
        reason: BookmarkAnnotationSyncReason,
        force: Boolean,
    ): Result<BookmarkAnnotationReconcileResult> {
        val startedAtMs = System.currentTimeMillis()
        val metadata = cachedAnnotationDao.getBookmarkAnnotationSyncMetadata(bookmarkId)
        val previous = cachedAnnotationDao.getAnnotationsForBookmark(bookmarkId)
        val decision = bookmarkAnnotationCheckDecision(force, metadata)
        if (!decision.due) {
            Timber.d(
                "Bookmark annotation check skipped: bookmarkId=%s reason=%s force=%s previous=%d detail=%s",
                bookmarkId,
                reason,
                force,
                previous.size,
                decision.detail
            )
            return Result.success(
                BookmarkAnnotationReconcileResult(
                    bookmarkId = bookmarkId,
                    previousCount = previous.size,
                    remoteCount = null,
                    changed = false,
                    skipped = true,
                    reason = decision.detail,
                )
            )
        }

        val attemptAt = Clock.System.now()
        cachedAnnotationDao.upsertBookmarkAnnotationSyncMetadata(
            (metadata ?: BookmarkAnnotationSyncMetadataEntity(bookmarkId = bookmarkId))
                .copy(lastAttemptAt = attemptAt)
        )
        Timber.d(
            "Bookmark annotation check started: bookmarkId=%s reason=%s force=%s previous=%d",
            bookmarkId,
            reason,
            force,
            previous.size
        )

        return try {
            val response = readeckApi.getAnnotations(bookmarkId)
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code()}")
            }
            val remote = response.body().orEmpty()
            val entities = remote.map { it.toCachedEntity(bookmarkId) }
            val changed = previous.annotationFingerprint() != entities.annotationFingerprint()
            database.performTransaction {
                cachedAnnotationDao.replaceAnnotationsForBookmark(bookmarkId, entities)
                cachedAnnotationDao.upsertBookmarkAnnotationSyncMetadata(
                    cachedAnnotationDao.getBookmarkAnnotationSyncMetadata(bookmarkId)
                        .orEmpty(bookmarkId)
                        .copy(
                            lastSuccessAt = Clock.System.now(),
                            failureCount = 0,
                            backoffUntil = null,
                        )
                )
            }
            Timber.d(
                "Bookmark annotation check succeeded: bookmarkId=%s reason=%s previous=%d remote=%d changed=%s durationMs=%d",
                bookmarkId,
                reason,
                previous.size,
                remote.size,
                changed,
                System.currentTimeMillis() - startedAtMs
            )
            Result.success(
                BookmarkAnnotationReconcileResult(
                    bookmarkId = bookmarkId,
                    previousCount = previous.size,
                    remoteCount = remote.size,
                    changed = changed,
                    skipped = false,
                    reason = "success",
                )
            )
        } catch (e: CancellationException) {
            recordBookmarkAnnotationFailure(bookmarkId, reason, e, startedAtMs)
            throw e
        } catch (e: Exception) {
            recordBookmarkAnnotationFailure(bookmarkId, reason, e, startedAtMs)
            Result.failure(e)
        }
    }

    private suspend fun reconcileAllAnnotations(reason: HighlightsRefreshReason): Result<Unit> {
        val initialMetadata = settingsDataStore.getHighlightsSyncMetadata()
        val initialSyncPerformed = settingsDataStore.isInitialSyncPerformed()
        val decision = globalRefreshDecision(reason, initialMetadata, initialSyncPerformed)
        Timber.d(
            "Global highlights reconciliation decision: reason=%s due=%s cacheComplete=%s initialSyncPerformed=%s lastSuccess=%s lastFailure=%s failureCount=%d backoffUntil=%s detail=%s",
            reason,
            decision.due,
            initialMetadata.cacheComplete,
            initialSyncPerformed,
            initialMetadata.lastGlobalSuccessAt,
            initialMetadata.lastGlobalFailureAt,
            initialMetadata.globalFailureCount,
            initialMetadata.globalBackoffUntil,
            decision.detail
        )
        if (!decision.due) {
            Timber.d(
                "Global highlights reconciliation skipped: reason=%s detail=%s cacheComplete=%s initialSyncPerformed=%s lastSuccess=%s lastFailure=%s backoffUntil=%s",
                reason,
                decision.detail,
                initialMetadata.cacheComplete,
                initialSyncPerformed,
                initialMetadata.lastGlobalSuccessAt,
                initialMetadata.lastGlobalFailureAt,
                initialMetadata.globalBackoffUntil
            )
            return Result.success(Unit)
        }
        if (!refreshMutex.tryLock()) {
            Timber.d(
                "Global highlights reconciliation skipped because one is already running: reason=%s currentState=%s",
                reason,
                syncState.value.toLogString()
            )
            return Result.success(Unit)
        }

        var offset = 0
        var loadedCount = 0
        var skippedOrphanAnnotations = 0
        var firstOrphanOffset: Int? = null
        var lastOrphanWarningAt: Instant? = null
        val missingBookmarkIds = mutableSetOf<String>()
        val startedAtMs = System.currentTimeMillis()
        val attemptAt = Clock.System.now()
        persistSyncMetadata(
            initialMetadata.copy(lastGlobalAttemptAt = attemptAt)
        )
        return try {
            syncState.value = HighlightsSyncState.Running(loadedCount = 0)
            Timber.d(
                "Global highlights reconciliation started: reason=%s pageSize=%d cacheComplete=%s lastSuccess=%s lastFailure=%s backoffUntil=%s",
                reason,
                HIGHLIGHTS_PAGE_SIZE,
                initialMetadata.cacheComplete,
                initialMetadata.lastGlobalSuccessAt,
                initialMetadata.lastGlobalFailureAt,
                initialMetadata.globalBackoffUntil
            )
            bookmarkMetadataSyncCoordinator.withStableBookmarks(
                reason = "HighlightsRepository.global.$reason"
            ) {
            cachedAnnotationDao.clearRemoteAnnotationIds()
            Timber.d("Global highlights reconciliation table cleared")

            while (true) {
                Timber.d(
                    "Global highlights reconciliation requesting page: reason=%s offset=%d limit=%d loaded=%d",
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
                        "Global highlights reconciliation HTTP failure: reason=%s offset=%d code=%d loaded=%d",
                        reason,
                        offset,
                        response.code(),
                        loadedCount
                    )
                    throw IllegalStateException("HTTP ${response.code()}")
                }
                val page = response.body().orEmpty()
                val pageBookmarkIds = page.map { it.bookmark_id }.distinct()
                val existingBookmarkIds = if (pageBookmarkIds.isEmpty()) {
                    emptySet()
                } else {
                    bookmarkDao.getExistingActiveBookmarkIds(pageBookmarkIds).toSet()
                }
                val missingPageBookmarkIds = pageBookmarkIds
                    .filterNot { it in existingBookmarkIds }
                    .toSet()
                val (acceptedAnnotations, orphanAnnotations) = page.partition {
                    it.bookmark_id in existingBookmarkIds
                }
                if (orphanAnnotations.isNotEmpty()) {
                    val warningAt = Clock.System.now()
                    skippedOrphanAnnotations += orphanAnnotations.size
                    missingBookmarkIds += missingPageBookmarkIds
                    firstOrphanOffset = firstOrphanOffset ?: offset
                    lastOrphanWarningAt = warningAt
                    Timber.w(
                        "Global highlights reconciliation skipped orphan annotations: reason=%s offset=%d missingBookmarkCount=%d pageAnnotations=%d skipped=%d bookmarkIdSamples=%s annotationIdSamples=%s",
                        reason,
                        offset,
                        missingPageBookmarkIds.size,
                        page.size,
                        orphanAnnotations.size,
                        missingPageBookmarkIds.take(5),
                        orphanAnnotations.map { it.id }.take(5)
                    )
                }
                Timber.d(
                    "Global highlights reconciliation page received: reason=%s offset=%d size=%d accepted=%d orphan=%d loadedBefore=%d",
                    reason,
                    offset,
                    page.size,
                    acceptedAnnotations.size,
                    orphanAnnotations.size,
                    loadedCount
                )
                database.performTransaction {
                    if (acceptedAnnotations.isNotEmpty()) {
                        cachedAnnotationDao.insertAnnotations(acceptedAnnotations.map { it.toCachedEntity() })
                        cachedAnnotationDao.insertRemoteAnnotationIds(
                            acceptedAnnotations.map { RemoteAnnotationIdEntity(it.id) }
                        )
                    }
                }

                loadedCount += acceptedAnnotations.size
                syncState.value = HighlightsSyncState.Running(loadedCount = loadedCount)
                Timber.d(
                    "Global highlights reconciliation page stored: reason=%s offset=%d pageSize=%d accepted=%d skippedOrphans=%d loadedTotal=%d",
                    reason,
                    offset,
                    page.size,
                    acceptedAnnotations.size,
                    orphanAnnotations.size,
                    loadedCount
                )
                if (page.size < HIGHLIGHTS_PAGE_SIZE) {
                    Timber.d(
                        "Global highlights reconciliation reached final page: reason=%s offset=%d finalPageSize=%d loadedTotal=%d",
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
            val successAt = Clock.System.now()
            persistSyncMetadata(
                settingsDataStore.getHighlightsSyncMetadata().copy(
                    lastGlobalSuccessAt = successAt,
                    globalFailureCount = 0,
                    globalBackoffUntil = null,
                    cacheComplete = true,
                    skippedOrphanAnnotationCount = skippedOrphanAnnotations,
                    missingBookmarkCount = missingBookmarkIds.size,
                    firstOrphanOffset = firstOrphanOffset,
                    lastOrphanWarningAt = lastOrphanWarningAt,
                )
            )
            syncState.value = HighlightsSyncState.Idle
            Timber.d(
                "Global highlights reconciliation succeeded: reason=%s loadedTotal=%d staleDeleted=%d skippedOrphans=%d missingBookmarkCount=%d cacheComplete=%s lastSuccess=%s durationMs=%d",
                reason,
                loadedCount,
                staleDeleted,
                skippedOrphanAnnotations,
                missingBookmarkIds.size,
                true,
                successAt,
                System.currentTimeMillis() - startedAtMs
            )
            }
            if (skippedOrphanAnnotations > 0 && reason != HighlightsRefreshReason.ORPHAN_REPAIR) {
                Timber.w(
                    "Scheduling bookmark orphan repair full sync: reason=%s skippedOrphanAnnotations=%d missingBookmarkCount=%d firstOrphanOffset=%s lastOrphanWarningAt=%s",
                    reason,
                    skippedOrphanAnnotations,
                    missingBookmarkIds.size,
                    firstOrphanOffset,
                    lastOrphanWarningAt
                )
                syncScheduler.scheduleBookmarkOrphanRepairFullSync()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            recordGlobalFailure(reason, e, offset, loadedCount, startedAtMs)
            throw e
        } catch (e: Exception) {
            recordGlobalFailure(reason, e, offset, loadedCount, startedAtMs)
            Result.failure(e)
        } finally {
            refreshMutex.unlock()
        }
    }

    private suspend fun recordGlobalFailure(
        reason: HighlightsRefreshReason,
        error: Throwable,
        offset: Int,
        loadedCount: Int,
        startedAtMs: Long,
    ) {
        val failedAt = Clock.System.now()
        val current = settingsDataStore.getHighlightsSyncMetadata()
        val failureCount = current.globalFailureCount + 1
        val backoffUntil = failedAt.plusMilliseconds(globalFailureBackoffMs(failureCount))
        persistSyncMetadata(
            current.copy(
                lastGlobalFailureAt = failedAt,
                globalFailureCount = failureCount,
                globalBackoffUntil = backoffUntil,
            )
        )
        syncState.value = HighlightsSyncState.Failed(
            message = "Could not refresh highlights",
            cause = error
        )
        Timber.w(
            error,
            "Global highlights reconciliation failed: reason=%s offset=%d loaded=%d cacheComplete=%s lastSuccess=%s failureCount=%d backoffUntil=%s durationMs=%d",
            reason,
            offset,
            loadedCount,
            current.cacheComplete,
            current.lastGlobalSuccessAt,
            failureCount,
            backoffUntil,
            System.currentTimeMillis() - startedAtMs
        )
    }

    private fun globalRefreshDecision(
        reason: HighlightsRefreshReason,
        metadata: HighlightsSyncMetadata,
        initialSyncPerformed: Boolean,
    ): GlobalRefreshDecision {
        val now = Clock.System.now()
        if (!initialSyncPerformed) {
            return GlobalRefreshDecision(
                due = false,
                detail = "bookmark bootstrap incomplete"
            )
        }
        if (reason == HighlightsRefreshReason.USER_RETRY) {
            return GlobalRefreshDecision(due = true, detail = "manual retry bypasses throttle and backoff")
        }
        if (reason == HighlightsRefreshReason.ORPHAN_REPAIR) {
            return GlobalRefreshDecision(due = true, detail = "orphan repair bypasses throttle and backoff")
        }
        // SCREEN_OPEN, APP_OPEN, MANUAL_SYNC, and any other reasons intentionally share the same
        // freshness-and-backoff path below. Only USER_RETRY and ORPHAN_REPAIR get early-return bypasses.
        metadata.globalBackoffUntil?.let { backoffUntil ->
            if (backoffUntil > now) {
                return GlobalRefreshDecision(due = false, detail = "failure backoff active until $backoffUntil")
            }
        }
        if (!metadata.cacheComplete) {
            return GlobalRefreshDecision(due = true, detail = "cache incomplete")
        }
        val lastSuccess = metadata.lastGlobalSuccessAt
            ?: return GlobalRefreshDecision(due = true, detail = "cache complete but no success timestamp")
        val elapsedMs = now.toEpochMilliseconds() - lastSuccess.toEpochMilliseconds()
        return if (elapsedMs >= GLOBAL_REFRESH_FRESHNESS_MS) {
            GlobalRefreshDecision(due = true, detail = "global backstop due after ${elapsedMs}ms")
        } else {
            GlobalRefreshDecision(due = false, detail = "fresh for ${GLOBAL_REFRESH_FRESHNESS_MS - elapsedMs}ms")
        }
    }

    private fun bookmarkAnnotationCheckDecision(
        force: Boolean,
        metadata: BookmarkAnnotationSyncMetadataEntity?,
    ): BookmarkAnnotationCheckDecision {
        val now = Clock.System.now()
        if (force) {
            return BookmarkAnnotationCheckDecision(due = true, detail = "forced")
        }
        metadata?.backoffUntil?.let { backoffUntil ->
            if (backoffUntil > now) {
                return BookmarkAnnotationCheckDecision(
                    due = false,
                    detail = "failure backoff active until $backoffUntil"
                )
            }
        }
        val lastSuccess = metadata?.lastSuccessAt
            ?: return BookmarkAnnotationCheckDecision(due = true, detail = "no previous success")
        val elapsedMs = now.toEpochMilliseconds() - lastSuccess.toEpochMilliseconds()
        return if (elapsedMs >= BOOKMARK_ANNOTATION_FRESHNESS_MS) {
            BookmarkAnnotationCheckDecision(
                due = true,
                detail = "bookmark freshness expired after ${elapsedMs}ms"
            )
        } else {
            BookmarkAnnotationCheckDecision(
                due = false,
                detail = "recent success fresh for ${BOOKMARK_ANNOTATION_FRESHNESS_MS - elapsedMs}ms"
            )
        }
    }

    private suspend fun recordBookmarkAnnotationFailure(
        bookmarkId: String,
        reason: BookmarkAnnotationSyncReason,
        error: Throwable,
        startedAtMs: Long,
    ) {
        val failedAt = Clock.System.now()
        val current = cachedAnnotationDao.getBookmarkAnnotationSyncMetadata(bookmarkId)
            .orEmpty(bookmarkId)
        val failureCount = current.failureCount + 1
        val backoffUntil = failedAt.plusMilliseconds(bookmarkAnnotationFailureBackoffMs(failureCount))
        cachedAnnotationDao.upsertBookmarkAnnotationSyncMetadata(
            current.copy(
                lastFailureAt = failedAt,
                failureCount = failureCount,
                backoffUntil = backoffUntil,
            )
        )
        Timber.w(
            error,
            "Bookmark annotation check failed: bookmarkId=%s reason=%s failureCount=%d backoffUntil=%s durationMs=%d",
            bookmarkId,
            reason,
            failureCount,
            backoffUntil,
            System.currentTimeMillis() - startedAtMs
        )
    }
}

private data class GlobalRefreshDecision(
    val due: Boolean,
    val detail: String,
)

private data class BookmarkAnnotationCheckDecision(
    val due: Boolean,
    val detail: String,
)

private fun HighlightsSyncState.toLogString(): String {
    return when (this) {
        HighlightsSyncState.Idle -> "Idle"
        is HighlightsSyncState.Running -> "Running(loadedCount=$loadedCount)"
        is HighlightsSyncState.Failed -> "Failed(message=$message)"
    }
}

private const val HIGHLIGHTS_PAGE_SIZE = 50
private const val GLOBAL_REFRESH_FRESHNESS_MS = 12 * 60 * 60 * 1000L
private const val GLOBAL_FAILURE_BACKOFF_BASE_MS = 5 * 60 * 1000L
private const val GLOBAL_FAILURE_BACKOFF_MAX_MS = 60 * 60 * 1000L
private const val BOOKMARK_ANNOTATION_FRESHNESS_MS = 10 * 60 * 1000L
private const val BOOKMARK_ANNOTATION_FAILURE_BACKOFF_BASE_MS = 5 * 60 * 1000L
private const val BOOKMARK_ANNOTATION_FAILURE_BACKOFF_MAX_MS = 60 * 60 * 1000L

private fun globalFailureBackoffMs(failureCount: Int): Long {
    val multiplier = 1L shl (failureCount - 1).coerceIn(0, 4)
    return (GLOBAL_FAILURE_BACKOFF_BASE_MS * multiplier)
        .coerceAtMost(GLOBAL_FAILURE_BACKOFF_MAX_MS)
}

private fun bookmarkAnnotationFailureBackoffMs(failureCount: Int): Long {
    val multiplier = 1L shl (failureCount - 1).coerceIn(0, 4)
    return (BOOKMARK_ANNOTATION_FAILURE_BACKOFF_BASE_MS * multiplier)
        .coerceAtMost(BOOKMARK_ANNOTATION_FAILURE_BACKOFF_MAX_MS)
}

private fun Instant.plusMilliseconds(milliseconds: Long): Instant {
    return Instant.fromEpochMilliseconds(toEpochMilliseconds() + milliseconds)
}

private fun BookmarkAnnotationSyncMetadataEntity?.orEmpty(
    bookmarkId: String,
): BookmarkAnnotationSyncMetadataEntity {
    return this ?: BookmarkAnnotationSyncMetadataEntity(bookmarkId = bookmarkId)
}

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

private fun AnnotationDto.toCachedEntity(bookmarkId: String): CachedAnnotationEntity {
    return CachedAnnotationEntity(
        id = id,
        bookmarkId = bookmarkId,
        text = text,
        color = color.takeIf { it.isNotBlank() } ?: "yellow",
        note = note.takeIf { it.isNotBlank() },
        created = created.takeIf { it.isNotBlank() } ?: Clock.System.now().toString()
    )
}

private data class AnnotationFingerprint(
    val id: String,
    val text: String,
    val color: String,
    val note: String?,
    val created: String,
)

private fun List<CachedAnnotationEntity>.annotationFingerprint(): List<AnnotationFingerprint> {
    return map {
        AnnotationFingerprint(
            id = it.id,
            text = it.text,
            color = it.color,
            note = it.note,
            created = it.created,
        )
    }.sortedBy { it.id }
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
