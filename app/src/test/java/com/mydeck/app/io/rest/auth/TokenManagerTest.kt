package com.mydeck.app.io.rest.auth

import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenManagerTest {

    private val settingsDataStore = mockk<SettingsDataStore>()
    private val tokenManager = TokenManager(settingsDataStore)

    @Test
    fun `getToken reads synchronously from the data store`() {
        every { settingsDataStore.getTokenSync() } returns "abc123"

        assertEquals("abc123", tokenManager.getToken())
        // Read-after-write consistency depends on the synchronous getter, not the async tokenFlow —
        // guards against reintroducing the cache gap that left post-login /profile unauthenticated.
        verify { settingsDataStore.getTokenSync() }
    }

    @Test
    fun `getToken reflects the latest value on each call`() {
        every { settingsDataStore.getTokenSync() } returnsMany listOf(null, "fresh-token")

        assertNull(tokenManager.getToken())
        assertEquals("fresh-token", tokenManager.getToken())
    }
}
