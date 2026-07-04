package com.mydeck.app.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped event bus that bridges OAuth redirect intents (received in MainActivity)
 * to the login ViewModel, surviving process death and ViewModel recreation.
 */
@Singleton
class OAuthCallbackRepository @Inject constructor() {

    sealed class OAuthCallbackEvent {
        data class Success(val code: String, val state: String) : OAuthCallbackEvent()
        data class Error(val error: String, val errorDescription: String?) : OAuthCallbackEvent()
    }

    private val _events = MutableSharedFlow<OAuthCallbackEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OAuthCallbackEvent> = _events.asSharedFlow()

    fun dispatch(event: OAuthCallbackEvent) {
        _events.tryEmit(event)
    }
}
