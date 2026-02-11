package com.mydeck.app.domain

import com.mydeck.app.domain.model.AuthenticationDetails
import com.mydeck.app.domain.model.User
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.OAuthRevokeRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase
) : UserRepository {

    /**
     * Updated to no longer require password — with OAuth, password is stored as empty string.
     * Checks url + username + token (ignores password).
     */
    override fun observeAuthenticationDetails(): Flow<AuthenticationDetails?> =
        combine(
            settingsDataStore.urlFlow,
            settingsDataStore.usernameFlow,
            settingsDataStore.tokenFlow
        ) { url, username, token ->
            if (url != null && username != null && token != null) {
                AuthenticationDetails(url, username, "", token)
            } else {
                null
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Start OAuth Device Code flow.
     * Saves URL early (needed by UrlInterceptor), then registers client and requests device auth.
     * Returns DeviceAuthorizationRequired with state including clientId for polling.
     */
    override suspend fun initiateLogin(url: String): UserRepository.LoginResult {
        return withContext(Dispatchers.IO) {
            settingsDataStore.saveUrl(url)

            try {
                // Spec requirement: confirm server supports OAuth before starting device-code flow.
                val infoResponse = readeckApi.getInfo()
                val info = if (infoResponse.isSuccessful) infoResponse.body() else null
                if (info == null) {
                    settingsDataStore.clearCredentials()
                    return@withContext UserRepository.LoginResult.Error(
                        "Failed to read server capabilities. Please verify the server URL and try again.",
                        code = infoResponse.code()
                    )
                }

                if (!info.features.contains("oauth")) {
                    settingsDataStore.clearCredentials()
                    return@withContext UserRepository.LoginResult.Error(
                        "This server does not support OAuth authentication.",
                        code = infoResponse.code()
                    )
                }

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
     * Complete login after ViewModel receives token from polling.
     * Saves the token, then calls GET /profile to retrieve the username.
     */
    override suspend fun completeLogin(
        url: String,
        token: String
    ): UserRepository.LoginResult {
        return withContext(Dispatchers.IO) {
            try {
                // Save token first so AuthInterceptor can use it for the /profile call
                settingsDataStore.saveToken(token)

                // Fetch username from profile endpoint
                val profileResponse = readeckApi.userprofile()
                val username = if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    profileResponse.body()!!.user?.username ?: "user"
                } else {
                    Timber.w("Could not fetch profile, using default username")
                    "user"
                }

                // Save full credentials
                settingsDataStore.saveCredentials(
                    url = url,
                    username = username,
                    password = "", // No password with OAuth
                    token = token
                )
                Timber.i("Login completed successfully for user: $username")
                UserRepository.LoginResult.Success
            } catch (e: Exception) {
                Timber.e(e, "Failed to complete login")
                settingsDataStore.clearCredentials()
                UserRepository.LoginResult.Error(
                    "Failed to complete login: ${e.message}",
                    ex = e
                )
            }
        }
    }

    /**
     * Logout with token revocation.
     * Attempts server-side revocation, then clears local credentials regardless.
     */
    override suspend fun logout(): UserRepository.LogoutResult {
        return withContext(Dispatchers.IO) {
            try {
                val token = settingsDataStore.tokenFlow.first()

                if (token != null) {
                    try {
                        val response = readeckApi.revokeToken(
                            OAuthRevokeRequestDto(token = token)
                        )
                        if (response.isSuccessful) {
                            Timber.i("Token revoked successfully")
                        } else {
                            Timber.w("Token revocation failed: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to revoke token")
                        // Continue with local logout (fail open)
                    }
                }

                settingsDataStore.clearCredentials()
                Timber.i("Logout successful")
                UserRepository.LogoutResult.Success
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                try { settingsDataStore.clearCredentials() } catch (_: Exception) {}
                UserRepository.LogoutResult.Error("Failed to logout: ${e.message}")
            }
        }
    }

    override fun observeIsLoggedIn(): Flow<Boolean> = observeAuthenticationDetails().map {
        it != null
    }

    override fun observeUser(): Flow<User?> = observeAuthenticationDetails().map {
        if (it != null) User(it.username) else null
    }
}

