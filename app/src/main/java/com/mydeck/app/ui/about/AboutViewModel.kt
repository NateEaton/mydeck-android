package com.mydeck.app.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.domain.model.CachedServerInfo
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val connectivityMonitor: ConnectivityMonitor,
    private val readeckApi: ReadeckApi,
) : ViewModel() {

    data class UiState(
        val serverInfo: CachedServerInfo? = null,
        val serverInfoLoading: Boolean = false,
        val serverInfoError: Boolean = false
    )

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val serverUrl: StateFlow<String?> = settingsDataStore.urlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadAndRefreshServerInfo()
    }

    private fun loadAndRefreshServerInfo() {
        viewModelScope.launch {
            // Load cached server info immediately
            val cachedInfo = settingsDataStore.getServerInfo()
            _uiState.value = _uiState.value.copy(
                serverInfo = cachedInfo,
                serverInfoLoading = cachedInfo == null
            )

            // If network is available, fetch fresh info in background
            if (connectivityMonitor.isNetworkAvailable()) {
                try {
                    val response = readeckApi.getInfo()
                    if (response.isSuccessful) {
                        val infoDto = response.body()
                        if (infoDto != null) {
                            val freshInfo = CachedServerInfo(
                                canonical = infoDto.version.canonical,
                                release = infoDto.version.release,
                                build = infoDto.version.build,
                                features = infoDto.features
                            )
                            settingsDataStore.saveServerInfo(freshInfo)
                            _uiState.value = _uiState.value.copy(
                                serverInfo = freshInfo,
                                serverInfoLoading = false,
                                serverInfoError = false
                            )
                        }
                    } else {
                        // Fetch failed - if we have cache, keep it; otherwise mark error
                        if (cachedInfo == null) {
                            _uiState.value = _uiState.value.copy(
                                serverInfoLoading = false,
                                serverInfoError = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                serverInfoLoading = false
                            )
                        }
                        Timber.w("Failed to fetch server info: ${response.code()}")
                    }
                } catch (e: Exception) {
                    // Network error - if we have cache, keep it; otherwise mark error
                    if (cachedInfo == null) {
                        _uiState.value = _uiState.value.copy(
                            serverInfoLoading = false,
                            serverInfoError = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            serverInfoLoading = false
                        )
                    }
                    Timber.e(e, "Error fetching server info")
                }
            } else {
                // No network - if we have cache, show it; otherwise keep loading=true
                if (cachedInfo != null) {
                    _uiState.value = _uiState.value.copy(
                        serverInfoLoading = false
                    )
                }
            }
        }
    }

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
