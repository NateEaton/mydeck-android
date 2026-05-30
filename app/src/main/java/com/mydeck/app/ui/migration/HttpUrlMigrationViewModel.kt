package com.mydeck.app.ui.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.R
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.util.isValidUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HttpUrlMigrationViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val userRepository: UserRepository,
) : ViewModel() {
    data class UiState(
        val savedUrl: String = "",
        val replacementUrl: String = "https://",
        val urlError: Int? = null,
        val actionError: Int? = null,
        val isBusy: Boolean = false,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            savedUrl = settingsDataStore.urlFlow.value.orEmpty(),
            replacementUrl = toDisplayHttpsUrl(settingsDataStore.urlFlow.value)
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateReplacementUrl(value: String) {
        _uiState.update {
            it.copy(
                replacementUrl = value,
                urlError = if (value.isValidUrl(allowHttp = false)) null else R.string.account_settings_url_error,
                actionError = null
            )
        }
    }

    fun saveReplacementUrl() {
        val value = _uiState.value.replacementUrl
        if (!value.isValidUrl(allowHttp = false)) {
            _uiState.update { it.copy(urlError = R.string.account_settings_url_error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, actionError = null) }
            try {
                val normalizedUrl = normalizeApiUrl(value)
                val logoutResult = userRepository.logout()
                if (logoutResult is UserRepository.LogoutResult.Error) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            actionError = R.string.http_migration_save_error
                        )
                    }
                    return@launch
                }
                settingsDataStore.saveUrl(normalizedUrl)
            } catch (_: Exception) {
                _uiState.update { it.copy(actionError = R.string.http_migration_save_error) }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun normalizeApiUrl(rawUrl: String): String {
        var url = rawUrl.trimEnd('/')
        if (!url.endsWith("/api")) {
            url = "$url/api"
        }
        return url
    }

    private fun toDisplayHttpsUrl(rawUrl: String?): String {
        val display = rawUrl
            ?.trimEnd('/')
            ?.removeSuffix("/api")
            .orEmpty()
        return if (display.startsWith("http://", ignoreCase = true)) {
            "https://" + display.substringAfter("://")
        } else {
            "https://"
        }
    }
}
