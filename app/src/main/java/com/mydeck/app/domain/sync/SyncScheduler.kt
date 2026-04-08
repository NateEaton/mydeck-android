package com.mydeck.app.domain.sync

interface SyncScheduler {
    /**
     * Schedules a one-time sync of pending actions (e.g., updates to read status, archive, etc.).
     */
    fun scheduleActionSync()

    /**
     * Schedules a one-time background download for an article's content.
     */
    fun scheduleArticleDownload(bookmarkId: String)

    /**
     * Enqueues a batch content-sync job to download offline articles.
     */
    fun scheduleBatchArticleLoad(wifiOnly: Boolean, allowBatterySaver: Boolean)

    /**
     * Enqueues a batch content-sync job that bypasses the user's connectivity
     * and battery-saver constraints (requires any network connection).
     * Used when the user explicitly overrides constraints via the pull-to-refresh dialog.
     */
    fun scheduleBatchArticleLoadOverridingConstraints()
}
