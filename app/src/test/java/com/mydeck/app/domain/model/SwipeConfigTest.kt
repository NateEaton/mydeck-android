package com.mydeck.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeConfigTest {

    @Test
    fun `Default enables swipe with delete left and archive right`() {
        val default = SwipeConfig.Default

        assertTrue(default.enabled)
        assertEquals(SwipeAction.DELETE, default.leftAction)
        assertEquals(SwipeAction.ARCHIVE, default.rightAction)
    }

    @Test
    fun `copy with enabled false produces disabled pass-through config`() {
        val disabled = SwipeConfig.Default.copy(enabled = false)

        assertFalse(disabled.enabled)
        assertEquals(SwipeConfig.Default.leftAction, disabled.leftAction)
        assertEquals(SwipeConfig.Default.rightAction, disabled.rightAction)
    }
}
