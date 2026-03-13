package com.mydeck.app.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalPermissionsApi::class)
@HiltViewModel
class UiSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()
    private val theme = MutableStateFlow(Theme.SYSTEM)
    private val lightAppearance = MutableStateFlow(LightAppearance.PAPER)
    private val darkAppearance = MutableStateFlow(DarkAppearance.DARK)
    private val showDialog = MutableStateFlow(false)
    private val keepScreenOnWhileReading = MutableStateFlow(true)
    private val fullscreenWhileReading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            theme.value = settingsDataStore.getTheme()
            lightAppearance.value = settingsDataStore.getLightAppearance()
            darkAppearance.value = settingsDataStore.getDarkAppearance()
            keepScreenOnWhileReading.value = settingsDataStore.isKeepScreenOnWhileReading()
            fullscreenWhileReading.value = settingsDataStore.isFullscreenWhileReading()
        }
    }
    val uiState = combine(
        theme,
        lightAppearance,
        darkAppearance,
        keepScreenOnWhileReading,
        fullscreenWhileReading
    ) { themeMode, lightAppearance, darkAppearance, keepScreenOn, fullscreen ->
        UiSettingsUiState(
            themeMode = themeMode,
            lightAppearance = lightAppearance,
            darkAppearance = darkAppearance,
            themeOptions = getThemeOptionList(themeMode),
            showDialog = showDialog.value,
            themeLabel = themeMode.toLabelResource(),
            keepScreenOnWhileReading = keepScreenOn,
            fullscreenWhileReading = fullscreen,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue =
                UiSettingsUiState(
                    themeMode = Theme.SYSTEM,
                    lightAppearance = LightAppearance.PAPER,
                    darkAppearance = DarkAppearance.DARK,
                    themeOptions = getThemeOptionList(Theme.SYSTEM),
                    showDialog = false,
                    themeLabel = Theme.SYSTEM.toLabelResource(),
                    keepScreenOnWhileReading = true,
                    fullscreenWhileReading = false,
                )
        )

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null } // Reset the event
    }

    fun onClickTheme() {
        showDialog.value = true
    }

    fun onDismissDialog() {
        showDialog.value = false
    }

    fun onThemeSelected(selected: Theme) {
        Timber.d("onThemeSelected [selected=$selected]")
        updateTheme(selected)
    }

    fun onThemeModeSelected(mode: Theme) {
        Timber.d("onThemeModeSelected [mode=$mode]")
        updateTheme(mode)
    }

    fun onLightAppearanceSelected(appearance: LightAppearance) {
        Timber.d("onLightAppearanceSelected [appearance=$appearance]")
        viewModelScope.launch {
            settingsDataStore.saveLightAppearance(appearance)
            lightAppearance.value = settingsDataStore.getLightAppearance()
        }
    }

    fun onDarkAppearanceSelected(appearance: DarkAppearance) {
        Timber.d("onDarkAppearanceSelected [appearance=$appearance]")
        viewModelScope.launch {
            settingsDataStore.saveDarkAppearance(appearance)
            darkAppearance.value = settingsDataStore.getDarkAppearance()
        }
    }

    fun onKeepScreenOnWhileReadingToggled(enabled: Boolean) {
        Timber.d("onKeepScreenOnWhileReadingToggled [enabled=$enabled]")
        viewModelScope.launch {
            settingsDataStore.saveKeepScreenOnWhileReading(enabled)
            keepScreenOnWhileReading.value = settingsDataStore.isKeepScreenOnWhileReading()
        }
    }

    fun onFullscreenWhileReadingToggled(enabled: Boolean) {
        Timber.d("onFullscreenWhileReadingToggled [enabled=$enabled]")
        viewModelScope.launch {
            settingsDataStore.saveFullscreenWhileReading(enabled)
            fullscreenWhileReading.value = settingsDataStore.isFullscreenWhileReading()
        }
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
    }

    private fun getThemeOptionList(selected: Theme): List<ThemeOption> {
        return Theme.entries.map {
            ThemeOption(
                theme = it,
                label = it.toLabelResource(),
                selected = it == selected
            )
        }
    }

    private fun updateTheme(value: Theme) {
        viewModelScope.launch {
            settingsDataStore.saveTheme(value)
            theme.value = settingsDataStore.getTheme()
        }
    }
}

@Immutable
data class UiSettingsUiState(
    val themeMode: Theme,  // The base mode (LIGHT, DARK, or SYSTEM)
    val lightAppearance: LightAppearance,
    val darkAppearance: DarkAppearance,
    val themeOptions: List<ThemeOption>,
    val showDialog: Boolean,
    @StringRes
    val themeLabel: Int,
    val keepScreenOnWhileReading: Boolean,
    val fullscreenWhileReading: Boolean,
)

data class ThemeOption(
    val theme: Theme,
    @StringRes
    val label: Int,
    val selected: Boolean
)

@StringRes
fun Theme.toLabelResource(): Int {
    return when (this) {
        Theme.LIGHT -> R.string.theme_light
        Theme.DARK -> R.string.theme_dark
        Theme.SYSTEM -> R.string.theme_system
    }
}

@StringRes
fun LightAppearance.toLabelResource(): Int {
    return when (this) {
        LightAppearance.PAPER -> R.string.appearance_paper
        LightAppearance.SEPIA -> R.string.appearance_sepia
    }
}

@StringRes
fun DarkAppearance.toLabelResource(): Int {
    return when (this) {
        DarkAppearance.DARK -> R.string.appearance_dark
        DarkAppearance.BLACK -> R.string.appearance_black
    }
}
