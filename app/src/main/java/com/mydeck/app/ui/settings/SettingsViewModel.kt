package com.mydeck.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    userRepository: UserRepository,
    private val settingsDataStore: com.mydeck.app.io.prefs.SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()
    val uiState: StateFlow<SettingsUiState> = combine(
        userRepository.observeAuthenticationDetails(),
        settingsDataStore.offlineReadingEnabledFlow,
        settingsDataStore.themeFlow
    ) { authDetails, offlineReadingEnabled, theme ->
        val syncSubtitle = if (offlineReadingEnabled) {
            context.getString(R.string.settings_sync_subtitle_offline_enabled)
        } else {
            context.getString(R.string.settings_sync_subtitle_offline_disabled)
        }
        val uiSubtitle = when (theme) {
            Theme.LIGHT.name -> context.getString(R.string.settings_ui_subtitle_light)
            Theme.DARK.name -> context.getString(R.string.settings_ui_subtitle_dark)
            Theme.SYSTEM.name -> context.getString(R.string.settings_ui_subtitle_system)
            else -> context.getString(R.string.settings_ui_subtitle_system)
        }
        SettingsUiState(
            username = authDetails?.username,
            syncSubtitle = syncSubtitle,
            uiSubtitle = uiSubtitle,
            logsSubtitle = context.getString(R.string.settings_logs_subtitle)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState(username = null)
    )

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null } // Reset the event
    }

    fun onClickAccount() {
        _navigationEvent.update { NavigationEvent.NavigateToAccountSettings }
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    fun onClickOpenSourceLibraries() {
        _navigationEvent.update { NavigationEvent.NavigateToOpenSourceLibraries }
    }

    fun onClickLogs() {
        _navigationEvent.update { NavigationEvent.NavigateToLogView }
    }

    fun onClickSync() {
        _navigationEvent.update { NavigationEvent.NavigateToSyncView }
    }

    fun onClickView() {
        _navigationEvent.update { NavigationEvent.NavigateToUiSettings }
    }

    sealed class NavigationEvent {
        data object NavigateToAccountSettings : NavigationEvent()
        data object NavigateToOpenSourceLibraries : NavigationEvent()
        data object NavigateBack : NavigationEvent()
        data object NavigateToLogView : NavigationEvent()
        data object NavigateToSyncView : NavigationEvent()
        data object NavigateToUiSettings : NavigationEvent()
    }

}

data class SettingsUiState(
    val username: String?,
    val syncSubtitle: String = "Synchronization Settings",
    val uiSubtitle: String = "Appearance",
    val logsSubtitle: String = "View & Send"
)
