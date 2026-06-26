package com.mydeck.app.domain.model

/**
 * Where a bookmark's original web page opens (the "Original View" path only — the card's
 * View-web-page icon, the reader overflow's View-web-page action, and the no-content fallback).
 * All other web-opening paths (in-article links, gallery links, explicit Open-in-browser) are
 * unaffected by this preference.
 */
enum class OpenWebPagesIn {
    /** In the app's Original View (in-app web viewer) — current/default behavior. */
    IN_APP,
    /** In the device's external browser via [android.content.Intent.ACTION_VIEW]. */
    EXTERNAL_BROWSER
}
