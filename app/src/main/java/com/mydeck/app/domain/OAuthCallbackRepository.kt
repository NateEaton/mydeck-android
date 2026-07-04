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
 *
 * Uses `replay = 1` deliberately: after a full process death the redirect intent can be dispatched
 * from `MainActivity.onCreate` *before* the recreated ViewModel re-subscribes. With `replay = 0`
 * that emission would be dropped and the login would hang on the "waiting" screen. With `replay = 1`
 * the late subscriber still receives the callback.
 *
 * The consumer MUST call [consume] once it has handled (or rejected) an event, so a stale callback
 * is not replayed into a subsequent, unrelated login attempt.
 */
@Singleton
class OAuthCallbackRepository @Inject constructor() {

    sealed class OAuthCallbackEvent {
        data class Success(val code: String, val state: String) : OAuthCallbackEvent()
        data class Error(val error: String, val errorDescription: String?) : OAuthCallbackEvent()
    }

    private val _events = MutableSharedFlow<OAuthCallbackEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OAuthCallbackEvent> = _events.asSharedFlow()

    fun dispatch(event: OAuthCallbackEvent) {
        _events.tryEmit(event)
    }

    /** Clears the replayed event after it has been handled, so it is not re-delivered. */
    fun consume() {
        _events.resetReplayCache()
    }
}
