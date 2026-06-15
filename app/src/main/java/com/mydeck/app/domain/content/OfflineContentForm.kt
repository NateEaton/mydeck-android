package com.mydeck.app.domain.content

import com.mydeck.app.io.db.model.BookmarkEntity

/**
 * Single source of truth for a bookmark's offline **completeness** — the physical storage
 * form of its on-device content. Replaces the scattered `contentState == DOWNLOADED` checks
 * that overloaded one flag across three different storage forms (legacy text cache /
 * HTML-only package / full package). See `docs/specs/offline-content-rework-spec.md`
 * §4.1–§4.3 and `debug/2026-06-14/offline-sync-diagnosis.md` for the full rationale.
 *
 * Derived from three inputs:
 *  - `contentState` — the persisted [BookmarkEntity.ContentState].
 *  - `hasPackage`   — a `content_package` row has been committed for this bookmark
 *                     (its HTML is on disk; `ContentPackageManager.getContentDir(id) != null`).
 *  - `hasResources` — that package committed ≥1 image resource (`content_package.hasResources = 1`).
 *
 * | Form           | meaning                      | (contentState, hasPackage, hasResources)               |
 * |----------------|------------------------------|--------------------------------------------------------|
 * | NONE           | nothing on device            | NOT_ATTEMPTED                                           |
 * | TEXT_CACHE     | text only, no images on disk | DOWNLOADED/DIRTY, (no package) or (package, no images)  |
 * | FULL_PACKAGE   | text + images on disk        | DOWNLOADED/DIRTY, package + resources                   |
 * | PERMANENT_NONE | server confirmed no content  | PERMANENT_NO_CONTENT                                    |
 */
enum class OfflineContentForm {
    NONE,
    TEXT_CACHE,
    FULL_PACKAGE,
    PERMANENT_NONE;

    companion object {
        /**
         * The bookmark's current storage [OfflineContentForm]. Used for display/icon
         * semantics (FULL vs TEXT distinction keys off [hasResources]).
         */
        fun derive(
            contentState: BookmarkEntity.ContentState,
            hasPackage: Boolean,
            hasResources: Boolean
        ): OfflineContentForm = when (contentState) {
            BookmarkEntity.ContentState.PERMANENT_NO_CONTENT -> PERMANENT_NONE
            BookmarkEntity.ContentState.NOT_ATTEMPTED -> NONE
            BookmarkEntity.ContentState.DOWNLOADED,
            BookmarkEntity.ContentState.DIRTY ->
                if (hasPackage && hasResources) FULL_PACKAGE else TEXT_CACHE
        }

        /**
         * Whether this bookmark still needs a content (re)fetch to reach a *fresh, complete*
         * offline package. This is the completeness guard that ALL offline guards key off
         * (spec §4.2), replacing `contentState == DOWNLOADED`.
         *
         * Returns `true` when:
         *  - it has no committed package yet — [BookmarkEntity.ContentState.NOT_ATTEMPTED], or a
         *    legacy on-demand text cache that was never packaged (a freshly-added article, or a
         *    `getArticle` text cache); these must be upgraded to a real package, **or**
         *  - it is marked [BookmarkEntity.ContentState.DIRTY] — either server content is newer
         *    than the local package (freshness refresh) **or** a prior multipart fetch dropped
         *    its resource parts in transport (the §4.3 partial-package case is funneled through
         *    DIRTY so it is re-attempted).
         *
         * Returns `false` for a committed package that is **not** DIRTY (a full package, **or** a
         * legitimately image-less article whose committed text-only package is its complete
         * content) and for [BookmarkEntity.ContentState.PERMANENT_NO_CONTENT].
         *
         * NOTE — eligibility intentionally keys off package **presence + DIRTY**, not off
         * [hasResources]. Keying it off `hasResources` (the literal "not FULL_PACKAGE" reading)
         * would re-download genuinely image-less articles on **every** batch run forever — a
         * churn bug. `hasResources` only distinguishes the FULL vs TEXT *form* (see [derive]);
         * the transport-partial case is routed through DIRTY by the §4.3 commit rule, so it is
         * still re-attempted.
         *
         * The equivalent SQL used by the batch eligibility queries is:
         * ```
         * b.contentState != 3
         *   AND (b.contentState = 2
         *        OR NOT EXISTS (SELECT 1 FROM content_package cp WHERE cp.bookmarkId = b.id))
         * ```
         */
        fun needsContentFetch(
            contentState: BookmarkEntity.ContentState,
            hasPackage: Boolean
        ): Boolean = when (contentState) {
            BookmarkEntity.ContentState.PERMANENT_NO_CONTENT -> false
            BookmarkEntity.ContentState.DIRTY -> true
            BookmarkEntity.ContentState.NOT_ATTEMPTED -> true
            BookmarkEntity.ContentState.DOWNLOADED -> !hasPackage
        }

        /**
         * Whether a just-committed multipart package is a **partial** result that must be
         * re-attempted (spec §4.3) rather than treated as a complete package: its HTML was
         * committed but the image resource parts were dropped in transport (parse warnings present
         * and zero resources), or it is a picture with no image at all. The caller marks such a
         * package DIRTY so the completeness guards re-attempt it next run, while the HTML text
         * stays immediately readable in the meantime.
         *
         * A genuinely image-less article (HTML committed, zero resources, **no** parse warnings) is
         * NOT partial — it is its own complete content and must not be re-downloaded each run.
         * Video bookmarks never carry image packages, so they are never partial here.
         */
        fun isPartialPackage(
            hasHtml: Boolean,
            resourceCount: Int,
            parseWarningCount: Int,
            isPicture: Boolean,
            isVideo: Boolean
        ): Boolean {
            if (!hasHtml || isVideo) return false
            val droppedResources = resourceCount == 0 && parseWarningCount > 0
            val pictureMissingImage = isPicture && resourceCount == 0
            return droppedResources || pictureMissingImage
        }
    }
}
