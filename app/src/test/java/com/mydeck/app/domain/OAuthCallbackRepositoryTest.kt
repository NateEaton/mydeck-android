package com.mydeck.app.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthCallbackRepositoryTest {

    @Test
    fun `event dispatched before subscription is still delivered via replay`() = runTest {
        val repo = OAuthCallbackRepository()

        // Dispatch first — no collector yet (simulates MainActivity onCreate after process death).
        repo.dispatch(OAuthCallbackRepository.OAuthCallbackEvent.Success("code-1", "state-1"))

        // A late subscriber must still receive it.
        val event = repo.events.first()
        assertTrue(event is OAuthCallbackRepository.OAuthCallbackEvent.Success)
        assertEquals("code-1", (event as OAuthCallbackRepository.OAuthCallbackEvent.Success).code)
    }

    @Test
    fun `consume clears the replay cache so a stale event is not re-delivered`() = runTest {
        val repo = OAuthCallbackRepository()

        repo.dispatch(OAuthCallbackRepository.OAuthCallbackEvent.Success("code-1", "state-1"))
        // Handle and consume.
        repo.events.first()
        repo.consume()

        // A fresh event dispatched after consume is the only one seen.
        repo.dispatch(OAuthCallbackRepository.OAuthCallbackEvent.Error("access_denied", null))
        val next = repo.events.first()
        assertTrue(next is OAuthCallbackRepository.OAuthCallbackEvent.Error)
    }
}
