package com.mydeck.app.domain.sync

interface SyncScheduler {
    /**
     * Schedules a one-time sync of pending actions (e.g., updates to read status, archive, etc.).
     */
    fun scheduleActionSync()

    /**
     * Enqueues a batch content-sync job to download offline articles.
     *
     * @param userInitiated when true, replaces any existing (e.g. backed-off/lingering)
     *   enqueued work so a user-triggered sync actually runs instead of being silently
     *   dropped by [androidx.work.ExistingWorkPolicy.KEEP].
     */
    fun scheduleBatchArticleLoad(wifiOnly: Boolean, allowBatterySaver: Boolean, userInitiated: Boolean = false)

    /**
     * Enqueues a one-time forced bookmark metadata repair after global highlight
     * reconciliation finds annotations for locally missing bookmarks.
     */
    fun scheduleBookmarkOrphanRepairFullSync()

    /**
     * Enqueues a batch content-sync job that bypasses the user's connectivity
     * and battery-saver constraints (requires any network connection).
     * Used when the user explicitly overrides constraints via the pull-to-refresh dialog.
     */
    fun scheduleBatchArticleLoadOverridingConstraints()
}
