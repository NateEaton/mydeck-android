package com.mydeck.app.ui.list

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkListScreenTest {

    @Test
    fun `formatPendingDeleteSnackbarTitleSnippet keeps short titles`() {
        assertEquals(
            "Short title",
            formatPendingDeleteSnackbarTitleSnippet("Short title")
        )
    }

    @Test
    fun `formatPendingDeleteSnackbarTitleSnippet collapses whitespace`() {
        assertEquals(
            "Title with spaces",
            formatPendingDeleteSnackbarTitleSnippet("  Title\nwith\tspaces  ")
        )
    }

    @Test
    fun `formatPendingDeleteSnackbarTitleSnippet truncates long titles`() {
        assertEquals(
            "A very long boo...",
            formatPendingDeleteSnackbarTitleSnippet("A very long bookmark title that keeps going")
        )
    }

    @Test
    fun `formatPendingDeleteSnackbarTitleSnippet returns blank for blank titles`() {
        assertEquals(
            "",
            formatPendingDeleteSnackbarTitleSnippet("   \n\t  ")
        )
    }
}
