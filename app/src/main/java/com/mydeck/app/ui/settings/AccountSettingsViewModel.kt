package com.mydeck.app.ui.settings

import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mydeck.app.R
import com.mydeck.app.domain.usecase.AuthenticateUseCase
import com.mydeck.app.domain.usecase.AuthenticationResult
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.util.isValidUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val authenticateUseCase: AuthenticateUseCase
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()
    private val _uiState =
        MutableStateFlow(AccountSettingsUiState("", "", "", false, null, null, null, null, false, false))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsDataStore.urlFlow.value
            val username = settingsDataStore.usernameFlow.value
            val password = settingsDataStore.passwordFlow.value
            val token = settingsDataStore.tokenFlow.value
            val isLoggedIn = !token.isNullOrBlank()

            // Auto-populate URL with protocol if empty
            val populatedUrl = if (url.isNullOrBlank()) "https://" else url

            _uiState.value = AccountSettingsUiState(
                url = populatedUrl,
                username = username,
                password = password,
                loginEnabled = isValidUrl(populatedUrl) && !username.isNullOrBlank() && !password.isNullOrBlank(),
                urlError = null,
                usernameError = null,
                passwordError = null,
                authenticationResult = null,
                allowUnencryptedConnection = false,
                isLoggedIn = isLoggedIn
            )
        }
    }

    fun login() {
        viewModelScope.launch {
            _uiState.value.url!!.also { url ->
                if (!url.endsWith("/api")) {
                    _uiState.update { it.copy(url = "$url/api") }
                }
            }
            val result = authenticateUseCase.execute(
                _uiState.value.url!!,
                _uiState.value.username!!,
                _uiState.value.password!!
            )
            _uiState.update {
                it.copy(authenticationResult = result, isLoggedIn = result is AuthenticationResult.Success)
            }
            Timber.d("result=$result")
            // Navigate to BookmarkList on successful authentication
            if (result is AuthenticationResult.Success) {
                _navigationEvent.update { NavigationEvent.NavigateToBookmarkList }
            }
        }
    }

    fun onAllowUnencryptedConnectionChanged(allow: Boolean) {
        _uiState.update {
            it.copy(allowUnencryptedConnection = allow)
        }
        uiState.value.url?.apply {
            // Update protocol prefix based on allow setting
            val updatedUrl = when {
                allow && this.startsWith("https://") -> this // https still works with unencrypted allowed
                !allow && this.startsWith("http://") -> "https://${this.substringAfter("http://")}" // Replace http with https
                this == "https://" || this == "http://" -> if (allow) "http://" else "https://" // Just the prefix
                else -> this
            }
            validateUrl(updatedUrl)
        }
    }

    fun onUrlChanged(value: String) {
        validateUrl(value)
    }

    private fun validateUrl(value: String) {
        val isValidUrl = isValidUrl(value)
        val urlError = if (!isValidUrl && value.isNotEmpty()) {
            R.string.account_settings_url_error // Use resource ID
        } else {
            null
        }
        _uiState.update {
            it.copy(
                url = value,
                urlError = urlError,
                loginEnabled = isValidUrl && !it.username.isNullOrBlank() && !it.password.isNullOrBlank(),
                authenticationResult = null // Clear any previous result
            )
        }
    }

    fun onUsernameChanged(value: String) {
        val usernameError = if (value.isBlank()) {
            R.string.account_settings_username_error // Use resource ID
        } else {
            null
        }
        _uiState.update {
            it.copy(
                username = value,
                usernameError = usernameError,
                loginEnabled = isValidUrl(uiState.value.url) && !value.isBlank() && !it.password.isNullOrBlank(),
                authenticationResult = null // Clear any previous result
            )
        }
    }

    fun onPasswordChanged(value: String) {
        val passwordError = if (value.isBlank()) {
            R.string.account_settings_password_error // Use resource ID
        } else {
            null
        }
        _uiState.update {
            it.copy(
                password = value,
                passwordError = passwordError,
                loginEnabled = isValidUrl(uiState.value.url) && !it.username.isNullOrBlank() && !value.isBlank(),
                authenticationResult = null // Clear any previous result
            )
        }
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null } // Reset the event
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                settingsDataStore.clearCredentials()
                // Reset the UI state
                _uiState.value = AccountSettingsUiState(
                    url = "https://",
                    username = "",
                    password = "",
                    loginEnabled = false,
                    urlError = null,
                    usernameError = null,
                    passwordError = null,
                    authenticationResult = null,
                    allowUnencryptedConnection = false,
                    isLoggedIn = false
                )
                Timber.d("Signed out successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error signing out")
            }
        }
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
        data object NavigateToBookmarkList : NavigationEvent()
    }

    private fun isValidUrl(url: String?): Boolean {
        val allowUnencrypted = _uiState.value.allowUnencryptedConnection
        return if (allowUnencrypted) {
            url.isValidUrl() // Any URL is valid if unencrypted is allowed
        } else {
            url?.startsWith("https://") == true && url.isValidUrl() // Must be HTTPS if unencrypted is not allowed
        }
    }

}

data class AccountSettingsUiState(
    val url: String?,
    val username: String?,
    val password: String?,
    val loginEnabled: Boolean,
    val urlError: Int?,
    val usernameError: Int?,
    val passwordError: Int?,
    val authenticationResult: AuthenticationResult?,
    val allowUnencryptedConnection: Boolean = false,
    val isLoggedIn: Boolean = false
)
