package com.mydeck.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val settingsDataStore: SettingsDataStore
): ViewModel() {
    val isReady: StateFlow<Boolean> = combine(
        settingsDataStore.themeFlow,
        settingsDataStore.lightAppearanceFlow,
        settingsDataStore.darkAppearanceFlow,
    ) { _, _, _ -> true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val theme = settingsDataStore.themeFlow.map {
        it?.let {
            try { Theme.valueOf(it) } catch (_: IllegalArgumentException) { Theme.LIGHT }
        } ?: Theme.SYSTEM
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Theme.SYSTEM
    )

    val lightAppearance = settingsDataStore.lightAppearanceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LightAppearance.PAPER
    )

    val darkAppearance = settingsDataStore.darkAppearanceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DarkAppearance.DARK
    )
}
