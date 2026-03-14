package com.mydeck.app.worker

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadBookmarksWorkerTest {

    @Test
    fun `resolveSyncSince prefers positive last sync timestamp`() {
        val lastSyncTimestamp = Instant.parse("2026-03-12T20:38:13Z")
        val lastBookmarkTimestamp = Instant.parse("2026-03-12T20:00:00Z")

        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = lastSyncTimestamp,
            lastBookmarkTimestamp = lastBookmarkTimestamp
        )

        assertEquals(lastSyncTimestamp, resolved)
    }

    @Test
    fun `resolveSyncSince falls back to bookmark timestamp when last sync is zero`() {
        val lastBookmarkTimestamp = Instant.parse("2026-03-12T20:38:13Z")

        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = Instant.fromEpochSeconds(0),
            lastBookmarkTimestamp = lastBookmarkTimestamp
        )

        assertEquals(lastBookmarkTimestamp, resolved)
    }

    @Test
    fun `resolveSyncSince returns null when both cursors are unset`() {
        val resolved = LoadBookmarksWorker.resolveSyncSince(
            lastSyncTimestamp = null,
            lastBookmarkTimestamp = Instant.fromEpochSeconds(0)
        )

        assertNull(resolved)
    }
}
