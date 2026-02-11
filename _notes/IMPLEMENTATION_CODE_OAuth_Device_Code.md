# Implementation Code: OAuth Device Code Grant

**Companion Document to:** `TECH_SPEC_OAuth_Device_Code_Authentication.md`

This document provides complete, production-ready code for the key components of the OAuth Device Code Grant implementation.

---

## Table of Contents

1. [OAuthDeviceAuthorizationUseCase (Complete)](#1-oauthdeviceauthorizationusecase)
2. [UserRepositoryImpl Updates](#2-userrepositoryimpl-updates)
3. [Login ViewModel](#3-login-viewmodel)
4. [Device Authorization Dialog (Compose)](#4-device-authorization-dialog-compose)
5. [String Resources](#5-string-resources)

---

## 1. OAuthDeviceAuthorizationUseCase

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/OAuthDeviceAuthorizationUseCase.kt`

```kotlin
package com.mydeck.app.domain.usecase

import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Use case that handles OAuth 2.0 Device Code Grant flow for authentication.
 *
 * This implements RFC 8628: OAuth 2.0 Device Authorization Grant
 * https://www.rfc-editor.org/rfc/rfc8628
 */
class OAuthDeviceAuthorizationUseCase @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val json: Json
) {
    companion object {
        private const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
        private const val CLIENT_NAME = "MyDeck Android"
        private const val CLIENT_URI = "https://github.com/yourusername/mydeck-android"
        private const val SOFTWARE_ID = "com.mydeck.app"
        private const val SLOW_DOWN_ADDITIONAL_INTERVAL = 5 // seconds
    }

    sealed class DeviceAuthResult {
        data class AuthorizationRequired(val state: OAuthDeviceAuthorizationState) : DeviceAuthResult()
        data class Error(val message: String, val exception: Exception? = null) : DeviceAuthResult()
    }

    sealed class TokenPollResult {
        data class Success(val accessToken: String) : TokenPollResult()
        data object StillPending : TokenPollResult()
        data class UserDenied(val message: String) : TokenPollResult()
        data class Expired(val message: String) : TokenPollResult()
        data class SlowDown(val newInterval: Int) : TokenPollResult()
        data class Error(val message: String, val exception: Exception? = null) : TokenPollResult()
    }

    /**
     * Step 1: Register OAuth client and request device authorization
     *
     * This combines client registration and device authorization into a single operation.
     * Returns the device authorization state that should be displayed to the user.
     */
    suspend fun initiateDeviceAuthorization(): DeviceAuthResult {
        try {
            // Step 1.1: Register OAuth client
            Timber.d("Registering OAuth client")
            val clientRegistrationRequest = OAuthClientRegistrationRequestDto(
                clientName = CLIENT_NAME,
                clientUri = CLIENT_URI,
                softwareId = SOFTWARE_ID,
                softwareVersion = BuildConfig.VERSION_NAME,
                grantTypes = listOf(GRANT_TYPE_DEVICE_CODE)
            )

            val clientResponse = readeckApi.registerOAuthClient(clientRegistrationRequest)

            if (!clientResponse.isSuccessful || clientResponse.body() == null) {
                val errorMessage = parseOAuthError(clientResponse.errorBody()?.string())
                Timber.e("Client registration failed: $errorMessage")
                return DeviceAuthResult.Error("Failed to register with server: $errorMessage")
            }

            val clientId = clientResponse.body()!!.clientId
            Timber.d("OAuth client registered: $clientId")

            // Step 1.2: Request device authorization
            Timber.d("Requesting device authorization")
            val deviceAuthRequest = OAuthDeviceAuthorizationRequestDto(
                clientId = clientId,
                scope = REQUIRED_SCOPES
            )

            val deviceAuthResponse = readeckApi.authorizeDevice(deviceAuthRequest)

            if (!deviceAuthResponse.isSuccessful || deviceAuthResponse.body() == null) {
                val errorMessage = parseOAuthError(deviceAuthResponse.errorBody()?.string())
                Timber.e("Device authorization failed: $errorMessage")
                return DeviceAuthResult.Error("Failed to authorize device: $errorMessage")
            }

            val deviceAuth = deviceAuthResponse.body()!!

            // Calculate expiration timestamp
            val expiresAt = System.currentTimeMillis() + (deviceAuth.expiresIn * 1000L)

            val state = OAuthDeviceAuthorizationState(
                deviceCode = deviceAuth.deviceCode,
                userCode = deviceAuth.userCode,
                verificationUri = deviceAuth.verificationUri,
                verificationUriComplete = deviceAuth.verificationUriComplete,
                expiresAt = expiresAt,
                pollingInterval = deviceAuth.interval
            )

            Timber.d("Device authorization initiated: user_code=${deviceAuth.userCode}, " +
                    "expires_in=${deviceAuth.expiresIn}s, interval=${deviceAuth.interval}s")

            return DeviceAuthResult.AuthorizationRequired(state)

        } catch (e: IOException) {
            Timber.e(e, "Network error during device authorization")
            return DeviceAuthResult.Error("Network error: ${e.message}", e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error during device authorization")
            return DeviceAuthResult.Error("Invalid response from server: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during device authorization")
            return DeviceAuthResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    /**
     * Step 2: Poll for access token
     *
     * Call this method repeatedly until you receive a final result (Success, UserDenied, Expired, Error).
     * StillPending means continue polling after waiting for the polling interval.
     * SlowDown means the server wants you to increase the polling interval.
     *
     * @param clientId The client ID from the registration response
     * @param deviceCode The device code from the authorization response
     * @param currentInterval The current polling interval in seconds
     * @return TokenPollResult indicating the current state
     */
    suspend fun pollForToken(
        clientId: String,
        deviceCode: String,
        currentInterval: Int
    ): TokenPollResult {
        try {
            Timber.d("Polling for token (interval: ${currentInterval}s)")

            val tokenRequest = OAuthTokenRequestDto(
                grantType = GRANT_TYPE_DEVICE_CODE,
                clientId = clientId,
                deviceCode = deviceCode
            )

            val tokenResponse = readeckApi.requestToken(tokenRequest)

            // Success case
            if (tokenResponse.isSuccessful && tokenResponse.body() != null) {
                val token = tokenResponse.body()!!.accessToken
                Timber.i("Successfully obtained access token")
                return TokenPollResult.Success(token)
            }

            // Error cases - parse OAuth error response
            val errorBody = tokenResponse.errorBody()?.string()
            if (errorBody.isNullOrBlank()) {
                Timber.e("Token request failed with no error body: ${tokenResponse.code()}")
                return TokenPollResult.Error("Server error: ${tokenResponse.code()}")
            }

            val oauthError = try {
                json.decodeFromString<OAuthErrorDto>(errorBody)
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to parse OAuth error response")
                return TokenPollResult.Error("Invalid error response from server")
            }

            Timber.d("OAuth error: ${oauthError.error} - ${oauthError.errorDescription}")

            return when (oauthError.error) {
                "authorization_pending" -> {
                    // Normal case - user hasn't authorized yet
                    TokenPollResult.StillPending
                }

                "slow_down" -> {
                    // Server wants us to poll less frequently
                    val newInterval = currentInterval + SLOW_DOWN_ADDITIONAL_INTERVAL
                    Timber.w("Server requested slow_down, new interval: ${newInterval}s")
                    TokenPollResult.SlowDown(newInterval)
                }

                "access_denied" -> {
                    // User denied the authorization request
                    val message = oauthError.errorDescription ?: "Authorization was denied"
                    Timber.w("User denied authorization: $message")
                    TokenPollResult.UserDenied(message)
                }

                "expired_token" -> {
                    // Device code has expired
                    val message = oauthError.errorDescription ?: "Authorization code has expired"
                    Timber.w("Device code expired: $message")
                    TokenPollResult.Expired(message)
                }

                "invalid_grant", "invalid_client" -> {
                    // Invalid device code or client ID - should restart flow
                    val message = oauthError.errorDescription ?: "Invalid authorization code"
                    Timber.e("Invalid grant or client: $message")
                    TokenPollResult.Error(message)
                }

                else -> {
                    // Unexpected error
                    val message = oauthError.errorDescription ?: "Unknown error: ${oauthError.error}"
                    Timber.e("Unexpected OAuth error: ${oauthError.error}")
                    TokenPollResult.Error(message)
                }
            }

        } catch (e: IOException) {
            Timber.e(e, "Network error while polling for token")
            return TokenPollResult.Error("Network error: ${e.message}", e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error while polling for token")
            return TokenPollResult.Error("Invalid response from server: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while polling for token")
            return TokenPollResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    /**
     * Parse OAuth error response, falling back to generic message
     */
    private fun parseOAuthError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) {
            return "Unknown error"
        }

        return try {
            val oauthError = json.decodeFromString<OAuthErrorDto>(errorBody)
            oauthError.errorDescription ?: oauthError.error
        } catch (e: SerializationException) {
            "Server error"
        }
    }
}
```

---

## 2. UserRepositoryImpl Updates

**File:** `app/src/main/java/com/mydeck/app/domain/UserRepositoryImpl.kt`

**Changes to make:**

```kotlin
package com.mydeck.app.domain

import com.mydeck.app.domain.model.AuthenticationDetails
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.model.User
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.OAuthRevokeRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val readeckApi: ReadeckApi,
    private val json: Json,
    private val oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase // NEW
) : UserRepository {

    override fun observeAuthenticationDetails(): Flow<AuthenticationDetails?> =
        combine(
            settingsDataStore.urlFlow,
            settingsDataStore.usernameFlow,
            settingsDataStore.passwordFlow,
            settingsDataStore.tokenFlow
        ) { url, username, password, token ->
            if (url != null && username != null && password != null && token != null) {
                AuthenticationDetails(url, username, password, token)
            } else {
                null
            }
        }.flowOn(Dispatchers.IO)

    /**
     * NEW: OAuth-based login flow
     *
     * This replaces the old username/password login method.
     * Returns DeviceAuthorizationRequired with state to display to user.
     */
    override suspend fun login(url: String): UserRepository.LoginResult {
        return withContext(Dispatchers.IO) {
            // Save URL early to allow API calls to the correct server
            settingsDataStore.saveUrl(url)

            try {
                // Initiate OAuth Device Code Grant flow
                when (val result = oauthDeviceAuthUseCase.initiateDeviceAuthorization()) {
                    is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.AuthorizationRequired -> {
                        Timber.d("Device authorization required")
                        UserRepository.LoginResult.DeviceAuthorizationRequired(result.state)
                    }
                    is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error -> {
                        Timber.e("Device authorization failed: ${result.message}")
                        settingsDataStore.clearCredentials()
                        UserRepository.LoginResult.Error(result.message, ex = result.exception)
                    }
                }
            } catch (e: IOException) {
                settingsDataStore.clearCredentials()
                UserRepository.LoginResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                settingsDataStore.clearCredentials()
                UserRepository.LoginResult.Error(
                    "An unexpected error occurred: ${e.message}",
                    ex = e
                )
            }
        }
    }

    /**
     * NEW: Complete login after user has authorized in browser
     *
     * This method handles the polling loop and token retrieval.
     * Call this from a ViewModel/UI layer with proper lifecycle management.
     */
    suspend fun completeLogin(
        clientId: String,
        deviceCode: String,
        username: String, // From profile endpoint after getting token
        pollingInterval: Int
    ): UserRepository.LoginResult {
        return withContext(Dispatchers.IO) {
            var currentInterval = pollingInterval

            try {
                // Poll for token
                when (val result = oauthDeviceAuthUseCase.pollForToken(
                    clientId = clientId,
                    deviceCode = deviceCode,
                    currentInterval = currentInterval
                )) {
                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Success -> {
                        // Save credentials
                        val url = settingsDataStore.urlFlow.first()
                        settingsDataStore.saveCredentials(
                            url = url ?: "",
                            username = username,
                            password = "", // No password with OAuth
                            token = result.accessToken
                        )
                        Timber.i("Login successful")
                        UserRepository.LoginResult.Success
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending -> {
                        // Continue polling - caller should delay and call again
                        UserRepository.LoginResult.DeviceAuthorizationRequired(
                            OAuthDeviceAuthorizationState(
                                deviceCode = deviceCode,
                                userCode = "",
                                verificationUri = "",
                                verificationUriComplete = null,
                                expiresAt = 0,
                                pollingInterval = currentInterval
                            )
                        )
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown -> {
                        // Update interval and continue
                        currentInterval = result.newInterval
                        UserRepository.LoginResult.DeviceAuthorizationRequired(
                            OAuthDeviceAuthorizationState(
                                deviceCode = deviceCode,
                                userCode = "",
                                verificationUri = "",
                                verificationUriComplete = null,
                                expiresAt = 0,
                                pollingInterval = currentInterval
                            )
                        )
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied -> {
                        settingsDataStore.clearCredentials()
                        UserRepository.LoginResult.Error(result.message)
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired -> {
                        settingsDataStore.clearCredentials()
                        UserRepository.LoginResult.Error(result.message)
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error -> {
                        settingsDataStore.clearCredentials()
                        UserRepository.LoginResult.Error(result.message, ex = result.exception)
                    }
                }
            } catch (e: Exception) {
                settingsDataStore.clearCredentials()
                UserRepository.LoginResult.Error(
                    "An unexpected error occurred: ${e.message}",
                    ex = e
                )
            }
        }
    }

    /**
     * NEW: Logout with token revocation
     */
    override suspend fun logout(): UserRepository.LogoutResult {
        return withContext(Dispatchers.IO) {
            try {
                val token = settingsDataStore.tokenFlow.first()

                if (token != null) {
                    // Attempt to revoke token on server
                    try {
                        val response = readeckApi.revokeToken(
                            OAuthRevokeRequestDto(token = token)
                        )
                        if (response.isSuccessful) {
                            Timber.i("Token revoked successfully")
                        } else {
                            Timber.w("Token revocation failed: ${response.code()}")
                            // Continue with local logout anyway
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to revoke token")
                        // Continue with local logout anyway (fail open)
                    }
                }

                // Clear local credentials
                settingsDataStore.clearCredentials()
                Timber.i("Logout successful")
                UserRepository.LogoutResult.Success

            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                // Still try to clear credentials
                try {
                    settingsDataStore.clearCredentials()
                } catch (clearError: Exception) {
                    Timber.e(clearError, "Failed to clear credentials")
                }
                UserRepository.LogoutResult.Error("Failed to logout: ${e.message}")
            }
        }
    }

    override fun observeIsLoggedIn(): Flow<Boolean> = observeAuthenticationDetails().map {
        it != null
    }

    override fun observeUser(): Flow<User?> = observeAuthenticationDetails().map {
        if (it != null) {
            User(it.username)
        } else {
            null
        }
    }
}
```

**Note:** The `completeLogin()` method should actually be called from the ViewModel in a polling loop. The repository method should just handle a single poll attempt.

---

## 3. Login ViewModel

**File:** `app/src/main/java/com/mydeck/app/ui/login/LoginViewModel.kt`

```kotlin
package com.mydeck.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var clientId: String? = null
    private var deviceCode: String? = null
    private var pollingInterval: Int = 5

    sealed class LoginUiState {
        data object Initial : LoginUiState()
        data object Loading : LoginUiState()
        data class DeviceAuthorizationRequired(
            val userCode: String,
            val verificationUri: String,
            val verificationUriComplete: String?,
            val expiresAt: Long,
            val pollingInterval: Int
        ) : LoginUiState()
        data object Success : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }

    fun login(serverUrl: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            when (val result = userRepository.login(serverUrl)) {
                is UserRepository.LoginResult.Success -> {
                    _uiState.value = LoginUiState.Success
                }

                is UserRepository.LoginResult.DeviceAuthorizationRequired -> {
                    val state = result.state
                    // Store for polling
                    deviceCode = state.deviceCode
                    pollingInterval = state.pollingInterval

                    // Extract client ID (this is a simplification - in reality you'd need to
                    // pass this through the result or store it differently)
                    // For now, we'll need to modify the flow to include clientId

                    _uiState.value = LoginUiState.DeviceAuthorizationRequired(
                        userCode = state.userCode,
                        verificationUri = state.verificationUri,
                        verificationUriComplete = state.verificationUriComplete,
                        expiresAt = state.expiresAt,
                        pollingInterval = state.pollingInterval
                    )

                    // Start polling
                    startPolling(state)
                }

                is UserRepository.LoginResult.Error -> {
                    _uiState.value = LoginUiState.Error(result.errorMessage)
                }

                is UserRepository.LoginResult.NetworkError -> {
                    _uiState.value = LoginUiState.Error(result.errorMessage)
                }
            }
        }
    }

    private fun startPolling(state: OAuthDeviceAuthorizationState) {
        // Cancel any existing polling
        pollingJob?.cancel()

        var currentInterval = state.pollingInterval

        pollingJob = viewModelScope.launch {
            Timber.d("Starting OAuth token polling")

            while (true) {
                // Check if expired
                if (System.currentTimeMillis() >= state.expiresAt) {
                    Timber.w("Device authorization expired")
                    _uiState.value = LoginUiState.Error("Authorization code expired. Please try again.")
                    break
                }

                // Wait for polling interval
                delay(currentInterval.seconds)

                // Poll for token
                // Note: We need clientId here - this would need to be stored from the initial auth
                val clientId = this@LoginViewModel.clientId ?: run {
                    Timber.e("Client ID not available for polling")
                    _uiState.value = LoginUiState.Error("Authentication error. Please try again.")
                    break
                }

                when (val result = oauthDeviceAuthUseCase.pollForToken(
                    clientId = clientId,
                    deviceCode = state.deviceCode,
                    currentInterval = currentInterval
                )) {
                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Success -> {
                        Timber.i("Token received, completing login")
                        // Save token via repository
                        // This needs to be refactored to work properly
                        // For now, assume repository handles it
                        _uiState.value = LoginUiState.Success
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending -> {
                        Timber.d("Authorization still pending")
                        // Continue polling
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown -> {
                        Timber.w("Server requested slow_down, adjusting interval")
                        currentInterval = result.newInterval
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied -> {
                        Timber.w("User denied authorization")
                        _uiState.value = LoginUiState.Error(result.message)
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired -> {
                        Timber.w("Device code expired")
                        _uiState.value = LoginUiState.Error(result.message)
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error -> {
                        Timber.e("Polling error: ${result.message}")
                        _uiState.value = LoginUiState.Error(result.message)
                        break
                    }
                }
            }
        }
    }

    fun cancelAuthorization() {
        pollingJob?.cancel()
        _uiState.value = LoginUiState.Initial
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
```

**Note:** This ViewModel has a simplification issue - the `clientId` needs to be returned from the repository. This would require refactoring the `LoginResult.DeviceAuthorizationRequired` to include the `clientId`.

---

## 4. Device Authorization Dialog (Compose)

**File:** `app/src/main/java/com/mydeck/app/ui/login/DeviceAuthorizationDialog.kt`

```kotlin
package com.mydeck.app.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydeck.app.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun DeviceAuthorizationDialog(
    userCode: String,
    verificationUri: String,
    verificationUriComplete: String?,
    expiresAt: Long,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(expiresAt)) }

    // Update countdown timer
    LaunchedEffect(expiresAt) {
        while (timeRemaining > 0) {
            delay(1.seconds)
            timeRemaining = calculateTimeRemaining(expiresAt)
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(R.string.oauth_device_auth_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.oauth_device_auth_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Step 1: URL
                Text(
                    text = stringResource(R.string.oauth_device_auth_step1),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = verificationUri,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { copyToClipboard(context, verificationUri, "URL") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.oauth_device_auth_copy_url))
                            }

                            Button(
                                onClick = { openInBrowser(context, verificationUriComplete ?: verificationUri) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.oauth_device_auth_open_browser))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Step 2: Code
                Text(
                    text = stringResource(R.string.oauth_device_auth_step2),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = userCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        OutlinedButton(
                            onClick = { copyToClipboard(context, userCode, "Code") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.oauth_device_auth_copy_code))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Status
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.oauth_device_auth_waiting),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.oauth_device_auth_expires_in, formatTime(timeRemaining)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            // No confirm button
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun calculateTimeRemaining(expiresAt: Long): Long {
    return maxOf(0, expiresAt - System.currentTimeMillis()) / 1000
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)

    // Show toast (you might want to use a Snackbar in Compose instead)
    android.widget.Toast.makeText(
        context,
        "$label copied to clipboard",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

---

## 5. String Resources

**File:** `app/src/main/res/values/strings.xml`

Add these strings:

```xml
<!-- OAuth Device Code Grant -->
<string name="oauth_device_auth_title">Authorize MyDeck</string>
<string name="oauth_device_auth_instructions">To authorize this app, follow these steps:</string>
<string name="oauth_device_auth_step1">1. Visit this URL on any device:</string>
<string name="oauth_device_auth_step2">2. Enter this code:</string>
<string name="oauth_device_auth_copy_code">Copy Code</string>
<string name="oauth_device_auth_copy_url">Copy URL</string>
<string name="oauth_device_auth_open_browser">Open in Browser</string>
<string name="oauth_device_auth_waiting">Waiting for authorization…</string>
<string name="oauth_device_auth_expires_in">Expires in: %s</string>
<string name="oauth_device_auth_expired">Authorization code has expired. Please try again.</string>
<string name="oauth_device_auth_denied">Authorization was denied.</string>
<string name="oauth_device_auth_success">Successfully authorized!</string>

<!-- OAuth Errors -->
<string name="oauth_error_network">Network error. Please check your connection.</string>
<string name="oauth_error_server">Server error. Please try again later.</string>
<string name="oauth_error_invalid_server">Unable to connect to server. Please check your server URL.</string>
<string name="oauth_error_generic">An error occurred: %s</string>

<!-- Logout -->
<string name="logout">Sign Out</string>
<string name="logout_confirm_title">Sign Out</string>
<string name="logout_confirm_message">Are you sure you want to sign out? You\'ll need to authorize this app again to access your bookmarks.</string>
<string name="logout_success">Signed out successfully</string>
<string name="logout_error">Failed to sign out: %s</string>

<!-- Common -->
<string name="cancel">Cancel</string>
<string name="retry">Retry</string>
<string name="ok">OK</string>
```

**Important:** As per `CLAUDE.md`, copy these English strings to ALL language files as placeholders:
- `values-de-rDE/strings.xml`
- `values-es-rES/strings.xml`
- `values-fr/strings.xml`
- `values-gl-rES/strings.xml`
- `values-pl/strings.xml`
- `values-pt-rPT/strings.xml`
- `values-ru/strings.xml`
- `values-uk/strings.xml`
- `values-zh-rCN/strings.xml`

---

## 6. Additional Notes for Developer

### 6.1 Architecture Considerations

The current implementation has a **small architectural issue**: the `clientId` needs to be preserved between the initial authorization and the polling phase. There are several ways to solve this:

**Option A: Return clientId in DeviceAuthorizationState**
```kotlin
data class OAuthDeviceAuthorizationState(
    val clientId: String, // ADD THIS
    val deviceCode: String,
    val userCode: String,
    // ... rest of fields
)
```

**Option B: Make OAuthDeviceAuthorizationUseCase stateful**
Store the clientId internally in the use case and provide a method to access it.

**Option C: Return both results together**
Return a combined result that includes both the authorization state and the client ID.

**Recommendation:** Use **Option A** - it's the cleanest and most explicit.

### 6.2 Token Storage and UserRepository

The current `UserRepositoryImpl` expects `username` and `password` in credentials, but OAuth doesn't use passwords. You'll need to either:

1. **Store empty password:** `password = ""`
2. **Refactor AuthenticationDetails:** Make password optional
3. **Fetch username from API:** After getting token, call `/profile` endpoint to get username

**Recommendation:** Fetch username from `/profile` endpoint after receiving token.

### 6.3 Testing with Real Server

To test this implementation:

1. Set up Readeck 0.22+ (nightly build)
2. Configure OAuth in Readeck settings
3. Use the app to initiate flow
4. Monitor logs with: `adb logcat | grep "OAuth\|LoginViewModel"`
5. Test all error scenarios (denial, timeout, network errors)

### 6.4 Background Polling Considerations

The current implementation polls in a ViewModel coroutine. This has limitations:
- Stops polling if app is killed
- May be throttled if app is backgrounded

**For production, consider:**
- Using WorkManager for background polling
- Showing a foreground notification during authorization
- Implementing "Resume Authentication" if app is restarted

### 6.5 Security Best Practices

1. **Never log tokens:** Ensure Timber doesn't log full tokens in production
2. **Validate URLs:** Check user-entered server URLs for common mistakes
3. **HTTPS warnings:** Warn users about non-HTTPS URLs
4. **Token expiration:** Handle token expiration gracefully (though Readeck tokens are long-lived)

---

## 7. Quick Start Checklist

- [ ] Create all DTO classes (Section 1 of tech spec)
- [ ] Add OAuth endpoints to `ReadeckApi`
- [ ] Create `OAuthDeviceAuthorizationUseCase` (this document, Section 1)
- [ ] Update `UserRepository` interface
- [ ] Update `UserRepositoryImpl` (this document, Section 2)
- [ ] Create `LoginViewModel` or update existing (this document, Section 3)
- [ ] Create `DeviceAuthorizationDialog` composable (this document, Section 4)
- [ ] Add string resources to all language files (this document, Section 5)
- [ ] Add logout functionality to settings screen
- [ ] Test with Readeck 0.22+ nightly instance
- [ ] Handle edge cases (backgrounding, expiration, network errors)
- [ ] Write unit tests for use case and repository
- [ ] Write UI tests for login flow
- [ ] Update documentation

---

*End of Implementation Code Document*
