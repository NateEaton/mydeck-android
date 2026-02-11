package com.mydeck.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.worker.LoadBookmarksWorker
import com.mydeck.app.util.isValidUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val userRepository: UserRepository,
    private val oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase,
    private val bookmarkRepository: BookmarkRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class AccountSettingsUiState(
        val url: String = "https://",
        val urlError: Int? = null,
        val loginEnabled: Boolean = true,
        val isLoggedIn: Boolean = false,
        val authStatus: AuthStatus = AuthStatus.Idle,
        val deviceAuthState: OAuthDeviceAuthorizationState? = null
    )

    sealed class AuthStatus {
        data object Idle : AuthStatus()
        data object Loading : AuthStatus()
        data object WaitingForAuthorization : AuthStatus()
        data object Success : AuthStatus()
        data class Error(val message: String) : AuthStatus()
    }

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    private var pollingJob: Job? = null

    sealed class NavigationEvent {
        data object NavigateToBookmarkList : NavigationEvent()
        data object NavigateBack : NavigationEvent()
    }

    init {
        viewModelScope.launch {
            val token = settingsDataStore.tokenFlow.value
            val url = settingsDataStore.urlFlow.value
            _uiState.update {
                it.copy(
                    isLoggedIn = !token.isNullOrBlank(),
                    url = url ?: "https://"
                )
            }
        }
    }

    fun updateUrl(url: String) {
        validateUrl(url)
    }

    fun login() {
        val url = normalizeApiUrl(_uiState.value.url)
        Timber.d("login() called with normalized URL: $url")

        viewModelScope.launch {
            _uiState.update { it.copy(authStatus = AuthStatus.Loading) }

            val result = userRepository.initiateLogin(url)
            Timber.d("initiateLogin result: $result")
            when (result) {
                is UserRepository.LoginResult.DeviceAuthorizationRequired -> {
                    val state = result.state
                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.WaitingForAuthorization,
                            deviceAuthState = state
                        )
                    }
                    startPolling(url, state)
                }

                is UserRepository.LoginResult.Success -> {
                    // Shouldn't happen from initiateLogin, but handle it
                    onLoginSuccess()
                }

                is UserRepository.LoginResult.Error -> {
                    Timber.e("Login error (code=${result.code}): ${result.errorMessage}")
                    _uiState.update { it.copy(authStatus = AuthStatus.Error(result.errorMessage)) }
                }

                is UserRepository.LoginResult.NetworkError -> {
                    Timber.e("Login network error: ${result.errorMessage}")
                    _uiState.update { it.copy(authStatus = AuthStatus.Error(result.errorMessage)) }
                }
            }
        }
    }

    private fun startPolling(url: String, state: OAuthDeviceAuthorizationState) {
        pollingJob?.cancel()

        var currentInterval = state.pollingInterval

        pollingJob = viewModelScope.launch {
            Timber.d("Starting OAuth token polling (clientId=${state.clientId})")

            while (true) {
                if (System.currentTimeMillis() >= state.expiresAt) {
                    Timber.w("Device authorization expired")
                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.Error("Authorization code expired. Please try again."),
                            deviceAuthState = null
                        )
                    }
                    break
                }

                delay(currentInterval.seconds)

                when (val result = oauthDeviceAuthUseCase.pollForToken(
                    clientId = state.clientId,
                    deviceCode = state.deviceCode,
                    currentInterval = currentInterval
                )) {
                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Success -> {
                        Timber.i("Token received, completing login")

                        // Complete login: save token, fetch profile
                        when (val loginResult = userRepository.completeLogin(url, result.accessToken)) {
                            is UserRepository.LoginResult.Success -> onLoginSuccess()
                            is UserRepository.LoginResult.Error -> {
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(loginResult.errorMessage)) }
                            }
                            else -> {
                                _uiState.update { it.copy(authStatus = AuthStatus.Error("Unexpected error")) }
                            }
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending -> {
                        Timber.d("Authorization still pending")
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown -> {
                        currentInterval = result.newInterval
                        Timber.w("Server requested slow_down, new interval: ${currentInterval}s")
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * Post-login tasks: clear stale data, reset sync timestamp, and trigger initial bookmark load.
     */
    private suspend fun onLoginSuccess() {
        bookmarkRepository.deleteAllBookmarks()
        settingsDataStore.saveLastBookmarkTimestamp(Instant.fromEpochMilliseconds(0))
        LoadBookmarksWorker.enqueue(context, isInitialLoad = true)
        settingsDataStore.setInitialSyncPerformed(true)

        _uiState.update {
            it.copy(
                authStatus = AuthStatus.Success,
                isLoggedIn = true,
                deviceAuthState = null
            )
        }
        _navigationEvent.value = NavigationEvent.NavigateToBookmarkList
    }

    fun cancelAuthorization() {
        pollingJob?.cancel()
        _uiState.update {
            it.copy(authStatus = AuthStatus.Idle, deviceAuthState = null)
        }
    }

    fun signOut() {
        Timber.d("signOut() called")
        viewModelScope.launch {
            val logoutResult = userRepository.logout()
            Timber.d("logout result: $logoutResult")
            _uiState.update {
                AccountSettingsUiState(url = it.url) // Reset to logged-out state
            }
        }
    }


    private fun validateUrl(value: String) {
        val isUrlValid = isValidUrlForCurrentSettings(value)
        val urlError = if (!isUrlValid && value.isNotEmpty()) {
            com.mydeck.app.R.string.account_settings_url_error // Use resource ID
        } else {
            null
        }
        _uiState.update {
            it.copy(
                url = value,
                urlError = urlError,
                loginEnabled = isUrlValid // OAuth only needs valid URL
            )
        }
    }

    fun navigationEventConsumed() {
        _navigationEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    /**
     * Normalize user-entered URL to always end with exactly /api (no trailing slash, no double /api).
     */
    private fun normalizeApiUrl(rawUrl: String): String {
        var url = rawUrl.trimEnd('/')
        if (!url.endsWith("/api")) {
            url = "$url/api"
        }
        return url
    }

    private fun isValidUrlForCurrentSettings(url: String?): Boolean {
        return url.isValidUrl()
    }
}
