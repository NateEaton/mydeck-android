package com.mydeck.app.domain.model

import kotlinx.datetime.LocalDateTime

data class BookmarkListItem(
    val id: String,
    val href: String,
    val url: String,
    val title: String,
    val siteName: String,
    val isMarked: Boolean,
    val isArchived: Boolean,
    val isRead: Boolean,
    val readProgress: Int,
    val thumbnailSrc: String,
    val iconSrc: String,
    val imageSrc: String,
    val labels: List<String>,
    val type: Bookmark.Type,
    val readingTime: Int?,
    val created: LocalDateTime,
    val wordCount: Int?,
    val published: LocalDateTime?,
    val offlineState: OfflineState = OfflineState.NOT_DOWNLOADED,
    /**
     * Whether this bookmark can hold offline content (is pinnable): has article content or is a
     * picture, and isn't PERMANENT_NO_CONTENT. Lets multi-select decide the Pin/Unpin toggle from
     * only the *pinnable* items, so a no-content item in the selection doesn't block Unpin.
     */
    val offlineEligible: Boolean = false,
    /**
     * The server reported an extraction problem, or the bookmark is in the hard ERROR state — the same
     * "with errors" bucket the filter matches (`hasServerErrors = 1 OR state = ERROR`). Drives the card
     * error badge (highest priority in the download-icon slot).
     */
    val hasError: Boolean = false,
    /**
     * The server confirmed there is nothing to extract (`PERMANENT_NO_CONTENT`). Drives the card's
     * no-content badge ("opens the original page") when the bookmark isn't already flagged as an error.
     */
    val hasNoContent: Boolean = false
) {
    enum class OfflineState {
        NOT_DOWNLOADED,
        /** On-demand text cache (Room only, no committed package) or a managed image-less package. */
        DOWNLOADED_TEXT_ONLY,
        /** Managed full offline package (AUTOMATIC provenance: policy download or opened-on-read). */
        DOWNLOADED_FULL,
        /**
         * Pinned package the user explicitly kept (MANUAL provenance) — protected from prune,
         * rendered with a pin icon. Keyed off committed-package presence, so an image-less pinned
         * article counts as PINNED too (offline-pinning spec §9).
         */
        PINNED
    }
}
