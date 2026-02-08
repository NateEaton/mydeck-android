package com.mydeck.app.util

import com.mydeck.app.domain.model.SharedText
import junit.framework.TestCase.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun testExtractUrlAndTitle() {
        val testSet = listOf<Pair<String, SharedText?>>(
            "test " to null,
            "https://example.com" to SharedText(url = "https://example.com"),
            "before title https://example.com" to SharedText(url = "https://example.com", "before title"),
            "https://example.com after title" to SharedText(url = "https://example.com", "after title"),
            "Check this out: https://example.com" to SharedText(url = "https://example.com", title = "Check this out:"),
            "https://example.com?foo=bar&baz=qux" to SharedText(url = "https://example.com?foo=bar&baz=qux", title = null),
            "https://example.com#section" to SharedText(url = "https://example.com#section", title = null),
            "First https://one.com then https://two.com" to SharedText(url = "https://one.com", title = "First\nthen https://two.com"),
            "Привет https://example.com мир" to SharedText(url = "https://example.com", title = "Привет\nмир"),
            "Line 1\nhttps://example.com\nLine  3" to SharedText(url = "https://example.com", title = "Line 1\n\nLine  3")
        )
        testSet.forEachIndexed { index, testSet ->
            assertEquals("Error in testSet $index", testSet.second, testSet.first.extractUrlAndTitle())
        }
    }

}