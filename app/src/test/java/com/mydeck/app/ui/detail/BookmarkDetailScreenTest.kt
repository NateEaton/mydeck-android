package com.mydeck.app.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkDetailScreenTest {

    @Test
    fun `should reveal fullscreen top bar when scroll delta is upward`() {
        assertTrue(shouldRevealFullscreenTopBarOnScroll(1f))
    }

    @Test
    fun `should not reveal fullscreen top bar when scroll delta is neutral or downward`() {
        assertFalse(shouldRevealFullscreenTopBarOnScroll(0f))
        assertFalse(shouldRevealFullscreenTopBarOnScroll(-1f))
    }
}
