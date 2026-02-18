package com.mydeck.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val settingsDataStore: SettingsDataStore
): ViewModel() {
    val theme = settingsDataStore.themeFlow.map {
        it?.let {
            try { Theme.valueOf(it) } catch (_: IllegalArgumentException) { Theme.LIGHT }
        } ?: Theme.SYSTEM
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Theme.SYSTEM
    )

    val sepiaEnabled = settingsDataStore.sepiaEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
}