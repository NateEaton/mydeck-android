package com.mydeck.app.domain.sync

import com.mydeck.app.domain.model.Bookmark

interface SyncScheduler {
    /**
     * Schedules a one-time sync of pending actions (e.g., updates to read status, archive, etc.).
     */
    fun scheduleActionSync()

    /**
     * Schedules a one-time background download for an article's content.
     */
    fun scheduleArticleDownload(bookmarkId: String)
}
