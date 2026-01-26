package com.mydeck.app.ui.about

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    fun onClickBack() {
        _navigationEvent.value = NavigationEvent.NavigateBack
    }

    fun onClickOpenSourceLibraries() {
        _navigationEvent.value = NavigationEvent.NavigateToOpenSourceLibraries
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.value = null
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
        data object NavigateToOpenSourceLibraries : NavigationEvent()
    }
}
