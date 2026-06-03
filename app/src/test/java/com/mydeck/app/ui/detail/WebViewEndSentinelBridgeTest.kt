package com.mydeck.app.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewEndSentinelBridgeTest {

    @Test
    fun `observer script reports sentinel visibility without mutation hooks`() {
        val script = WebViewEndSentinelBridge.injectEndSentinelObserver()

        assertTrue(script.contains("IntersectionObserver"))
        assertTrue(script.contains("mydeck-end-sentinel"))
        assertTrue(script.contains(WebViewEndSentinelBridge.BRIDGE_NAME))
        assertTrue(script.contains("onEndVisible"))
        assertFalse(script.contains("toggleFavorite"))
        assertFalse(script.contains("toggleArchive"))
        assertFalse(script.contains("MyDeckActions"))
    }
}
