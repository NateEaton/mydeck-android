package com.mydeck.app.domain

import com.mydeck.app.domain.model.AuthenticationDetails
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeIsLoggedIn(): Flow<Boolean>
    fun observeUser(): Flow<User?>
    fun observeAuthenticationDetails(): Flow<AuthenticationDetails?>
    // Start OAuth Device Code flow — replaces login(url, username, password)
    suspend fun initiateLogin(url: String): LoginResult

    // Save credentials after token is obtained via polling
    suspend fun completeLogin(url: String, token: String): LoginResult

    // Implement logout with token revocation
    suspend fun logout(): LogoutResult

    sealed class LoginResult {
        data object Success : LoginResult()
        data class DeviceAuthorizationRequired(
            val state: OAuthDeviceAuthorizationState
        ) : LoginResult()
        data class Error(
            val errorMessage: String,
            val code: Int? = null,
            val ex: Exception? = null
        ) : LoginResult()
        data class NetworkError(val errorMessage: String) : LoginResult()
    }

    sealed class LogoutResult {
        data object Success : LogoutResult()
        data class Error(val errorMessage: String) : LogoutResult()
    }
}
