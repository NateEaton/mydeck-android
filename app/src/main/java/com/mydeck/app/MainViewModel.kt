package com.mydeck.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckNetworkPolicy
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
    sealed interface HttpUrlMigrationState {
        data object Checking : HttpUrlMigrationState
        data object NotRequired : HttpUrlMigrationState
        data class Required(val savedUrl: String) : HttpUrlMigrationState
    }

    val httpUrlMigrationState: StateFlow<HttpUrlMigrationState> = combine(
        settingsDataStore.tokenFlow,
        settingsDataStore.urlFlow
    ) { token, url ->
        if (!token.isNullOrBlank() &&
            ReadeckNetworkPolicy.isSavedHttpUrlBlocked(url, BuildConfig.ALLOW_INSECURE_HTTP)
        ) {
            HttpUrlMigrationState.Required(url.orEmpty())
        } else {
            HttpUrlMigrationState.NotRequired
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HttpUrlMigrationState.Checking
    )

    val isReady: StateFlow<Boolean> = combine(
        settingsDataStore.themeFlow,
        settingsDataStore.lightAppearanceFlow,
        settingsDataStore.darkAppearanceFlow,
        httpUrlMigrationState,
    ) { _, _, _, migrationState -> migrationState !is HttpUrlMigrationState.Checking }
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
