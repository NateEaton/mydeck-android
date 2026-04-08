package com.mydeck.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class OpenSourceLibrariesViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()

    fun onClickBack() {
        _navigationEvent.trySend(NavigationEvent.NavigateBack)
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }
}
