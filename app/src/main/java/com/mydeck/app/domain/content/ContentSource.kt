package com.mydeck.app.domain.content

/**
 * Provenance of a committed offline content package — *why* it is on the device
 * (spec offline-content-rework §6 W2). Orthogonal to [OfflineContentForm] (which is
 * *what* is on disk).
 *
 * - [AUTOMATIC] — downloaded by the offline policy (batch newest-N / date-range).
 *   Part of the rolling window; **prunable**.
 * - [MANUAL] — created by an explicit user action while offline reading is on:
 *   promote-on-open (opening a not-yet-downloaded article) or the multi-select
 *   "Available offline" action. **Protected** — never removed by the policy prune;
 *   only Clear All, disabling offline reading (full teardown), or the absolute
 *   storage cap (LRU across both pools) can evict it.
 *
 * Persisted as the `content_package.source` column (TEXT, default `AUTOMATIC`).
 */
enum class ContentSource {
    AUTOMATIC,
    MANUAL;

    companion object {
        /** Parse a stored value, tolerant of legacy/unknown rows (default [AUTOMATIC]). */
        fun fromStored(value: String?): ContentSource =
            entries.firstOrNull { it.name == value } ?: AUTOMATIC

        /**
         * The effective source to persist when committing a package: a background
         * (re)download must never **downgrade** an existing MANUAL package to AUTOMATIC,
         * but an explicit MANUAL action may **upgrade** an existing AUTOMATIC package.
         */
        fun resolveOnCommit(existing: ContentSource?, incoming: ContentSource): ContentSource =
            if (existing == MANUAL) MANUAL else incoming
    }
}
