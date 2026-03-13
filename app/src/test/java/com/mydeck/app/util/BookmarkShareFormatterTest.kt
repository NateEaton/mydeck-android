package com.mydeck.app.util

import com.mydeck.app.domain.model.BookmarkShareFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkShareFormatterTest {

    @Test
    fun `url only share format returns only the url`() {
        assertEquals(
            "https://example.com",
            formatBookmarkShareText(
                title = "Example",
                url = "https://example.com",
                format = BookmarkShareFormat.URL_ONLY
            )
        )
    }

    @Test
    fun `title and url share format uses a multiline block`() {
        assertEquals(
            "Example\nhttps://example.com",
            formatBookmarkShareText(
                title = "Example",
                url = "https://example.com",
                format = BookmarkShareFormat.TITLE_AND_URL_MULTILINE
            )
        )
    }

    @Test
    fun `title and url share format falls back to url when title is blank`() {
        assertEquals(
            "https://example.com",
            formatBookmarkShareText(
                title = "   ",
                url = "https://example.com",
                format = BookmarkShareFormat.TITLE_AND_URL_MULTILINE
            )
        )
    }
}
