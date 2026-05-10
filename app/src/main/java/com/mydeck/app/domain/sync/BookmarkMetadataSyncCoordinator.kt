package com.mydeck.app.domain.sync

import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

@Singleton
class BookmarkMetadataSyncCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withExclusiveMetadataSync(
        reason: String,
        block: suspend () -> T
    ): T = withGate(
        waitLog = "Bookmark metadata sync gate waiting: reason=%s",
        enterLog = "Bookmark metadata sync gate entered: reason=%s waitMs=%d",
        exitLog = "Bookmark metadata sync gate exited: reason=%s heldMs=%d",
        reason = reason,
        block = block
    )

    suspend fun <T> withStableBookmarks(
        reason: String,
        block: suspend () -> T
    ): T = withGate(
        waitLog = "Highlight bookmark stability gate waiting: reason=%s",
        enterLog = "Highlight bookmark stability gate entered: reason=%s waitMs=%d",
        exitLog = "Highlight bookmark stability gate exited: reason=%s heldMs=%d",
        reason = reason,
        block = block
    )

    private suspend fun <T> withGate(
        waitLog: String,
        enterLog: String,
        exitLog: String,
        reason: String,
        block: suspend () -> T
    ): T {
        val waitMark = TimeSource.Monotonic.markNow()
        if (mutex.isLocked) {
            Timber.i(waitLog, reason)
        }

        mutex.lock()
        val holdMark = TimeSource.Monotonic.markNow()
        Timber.i(
            enterLog,
            reason,
            waitMark.elapsedNow().inWholeMilliseconds
        )
        try {
            return block()
        } finally {
            Timber.i(
                exitLog,
                reason,
                holdMark.elapsedNow().inWholeMilliseconds
            )
            mutex.unlock()
        }
    }
}
