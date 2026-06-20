package com.mydeck.app.domain.content

import com.mydeck.app.io.db.model.BookmarkEntity.ContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the offline-completeness single source of truth (spec §4.1–§4.3).
 *
 * The whole rework hinges on these two predicates: every offline guard now keys off
 * [OfflineContentForm.needsContentFetch] instead of bare `contentState == DOWNLOADED`, so an
 * incomplete form is always upgradeable while a committed package is never re-fetched.
 */
class OfflineContentFormTest {

    // --- derive: physical storage form ---

    @Test
    fun `derive NONE when not attempted`() {
        assertEquals(
            OfflineContentForm.NONE,
            OfflineContentForm.derive(ContentState.NOT_ATTEMPTED, hasPackage = false, hasResources = false)
        )
    }

    @Test
    fun `derive PERMANENT_NONE when server confirmed no content`() {
        assertEquals(
            OfflineContentForm.PERMANENT_NONE,
            OfflineContentForm.derive(ContentState.PERMANENT_NO_CONTENT, hasPackage = false, hasResources = false)
        )
    }

    @Test
    fun `derive FULL_PACKAGE when downloaded with package and resources`() {
        assertEquals(
            OfflineContentForm.FULL_PACKAGE,
            OfflineContentForm.derive(ContentState.DOWNLOADED, hasPackage = true, hasResources = true)
        )
    }

    @Test
    fun `derive TEXT_CACHE when downloaded with package but no resources`() {
        // HTML-only / image-less committed package (diagnosis Form B).
        assertEquals(
            OfflineContentForm.TEXT_CACHE,
            OfflineContentForm.derive(ContentState.DOWNLOADED, hasPackage = true, hasResources = false)
        )
    }

    @Test
    fun `derive TEXT_CACHE when downloaded with no package`() {
        // Legacy on-demand text cache (diagnosis Form A).
        assertEquals(
            OfflineContentForm.TEXT_CACHE,
            OfflineContentForm.derive(ContentState.DOWNLOADED, hasPackage = false, hasResources = false)
        )
    }

    @Test
    fun `derive FULL_PACKAGE when dirty but resources are present`() {
        // A DIRTY full package is still physically a full package (text + images on disk).
        assertEquals(
            OfflineContentForm.FULL_PACKAGE,
            OfflineContentForm.derive(ContentState.DIRTY, hasPackage = true, hasResources = true)
        )
    }

    @Test
    fun `derive TEXT_CACHE when dirty with no resources`() {
        assertEquals(
            OfflineContentForm.TEXT_CACHE,
            OfflineContentForm.derive(ContentState.DIRTY, hasPackage = false, hasResources = false)
        )
    }

    // --- needsContentFetch: the completeness guard ---

    @Test
    fun `needsContentFetch true when not attempted`() {
        assertTrue(OfflineContentForm.needsContentFetch(ContentState.NOT_ATTEMPTED, hasPackage = false))
    }

    @Test
    fun `needsContentFetch true for legacy text cache so an incomplete form is selected for upgrade`() {
        // DOWNLOADED with no committed package — the core fix. The old guard excluded DOWNLOADED
        // and stranded these as text-only forever (diagnosis 4.1/4.2).
        assertTrue(OfflineContentForm.needsContentFetch(ContentState.DOWNLOADED, hasPackage = false))
    }

    @Test
    fun `needsContentFetch false for a committed package so a full package is not re-fetched`() {
        // A committed package that is not dirty — whether full OR legitimately image-less — is
        // complete and must NOT be re-downloaded on every run (churn).
        assertFalse(OfflineContentForm.needsContentFetch(ContentState.DOWNLOADED, hasPackage = true))
    }

    @Test
    fun `needsContentFetch true when dirty regardless of package (freshness or partial re-attempt)`() {
        assertTrue(OfflineContentForm.needsContentFetch(ContentState.DIRTY, hasPackage = true))
        assertTrue(OfflineContentForm.needsContentFetch(ContentState.DIRTY, hasPackage = false))
    }

    @Test
    fun `needsContentFetch false for permanent no content`() {
        assertFalse(OfflineContentForm.needsContentFetch(ContentState.PERMANENT_NO_CONTENT, hasPackage = false))
    }

    // --- isPartialPackage: §4.3 partial-package rule ---

    @Test
    fun `isPartialPackage true when html committed but resources dropped in transport`() {
        // HTML present, zero resources, parse warnings → transport-dropped images → re-attempt.
        assertTrue(
            OfflineContentForm.isPartialPackage(
                hasHtml = true, resourceCount = 0, parseWarningCount = 1,
                isPicture = false, isVideo = false
            )
        )
    }

    @Test
    fun `isPartialPackage false for a genuinely image-less article so it is not re-downloaded`() {
        // HTML present, zero resources, NO warnings → the article simply has no images → complete.
        assertFalse(
            OfflineContentForm.isPartialPackage(
                hasHtml = true, resourceCount = 0, parseWarningCount = 0,
                isPicture = false, isVideo = false
            )
        )
    }

    @Test
    fun `isPartialPackage false when resources are present`() {
        assertFalse(
            OfflineContentForm.isPartialPackage(
                hasHtml = true, resourceCount = 3, parseWarningCount = 0,
                isPicture = false, isVideo = false
            )
        )
    }

    @Test
    fun `isPartialPackage true for a picture with no image even without warnings`() {
        assertTrue(
            OfflineContentForm.isPartialPackage(
                hasHtml = true, resourceCount = 0, parseWarningCount = 0,
                isPicture = true, isVideo = false
            )
        )
    }

    @Test
    fun `isPartialPackage false for video bookmarks`() {
        assertFalse(
            OfflineContentForm.isPartialPackage(
                hasHtml = true, resourceCount = 0, parseWarningCount = 2,
                isPicture = false, isVideo = true
            )
        )
    }

    @Test
    fun `added article lifecycle ends FULL_PACKAGE and is not re-fetched`() {
        // Freshly added (NOT_ATTEMPTED) is eligible for fetch...
        assertTrue(OfflineContentForm.needsContentFetch(ContentState.NOT_ATTEMPTED, hasPackage = false))
        // ...and after the batch worker commits a full package it is FULL_PACKAGE and done — not
        // stranded as TEXT_CACHE, the regression this rework fixes (spec §6/§8).
        assertEquals(
            OfflineContentForm.FULL_PACKAGE,
            OfflineContentForm.derive(ContentState.DOWNLOADED, hasPackage = true, hasResources = true)
        )
        assertFalse(OfflineContentForm.needsContentFetch(ContentState.DOWNLOADED, hasPackage = true))
    }
}
