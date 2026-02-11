package com.mydeck.app.domain.usecase

import android.os.Build
import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.*
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
        private const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
        private const val SOFTWARE_ID = "com.mydeck.app"
        private const val SLOW_DOWN_ADDITIONAL_INTERVAL = 5 // seconds
        private val CLIENT_NAME = "MyDeck Android — ${Build.MANUFACTURER} ${Build.MODEL}"
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
        data class NetworkError(val message: String, val exception: Exception? = null) : TokenPollResult()
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

            val registeredClientId = clientResponse.body()!!.clientId
            Timber.d("OAuth client registered: $registeredClientId")

            // Step 1.2: Request device authorization
            Timber.d("Requesting device authorization")
            val deviceAuthRequest = OAuthDeviceAuthorizationRequestDto(
                clientId = registeredClientId,
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
                clientId = registeredClientId, // Needed for token polling
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
                    TokenPollResult.StillPending
                }

                "slow_down" -> {
                    val newInterval = currentInterval + SLOW_DOWN_ADDITIONAL_INTERVAL
                    Timber.w("Server requested slow_down, new interval: ${newInterval}s")
                    TokenPollResult.SlowDown(newInterval)
                }

                "access_denied" -> {
                    val message = oauthError.errorDescription ?: "Authorization was denied"
                    Timber.w("User denied authorization: $message")
                    TokenPollResult.UserDenied(message)
                }

                "expired_token" -> {
                    val message = oauthError.errorDescription ?: "Authorization code has expired"
                    Timber.w("Device code expired: $message")
                    TokenPollResult.Expired(message)
                }

                "invalid_grant", "invalid_client" -> {
                    val message = oauthError.errorDescription ?: "Invalid authorization code"
                    Timber.e("Invalid grant or client: $message")
                    TokenPollResult.Error(message)
                }

                else -> {
                    val message = oauthError.errorDescription ?: "Unknown error: ${oauthError.error}"
                    Timber.e("Unexpected OAuth error: ${oauthError.error}")
                    TokenPollResult.Error(message)
                }
            }

        } catch (e: IOException) {
            Timber.w(e, "Network error while polling for token (retryable)")
            return TokenPollResult.NetworkError("Network error: ${e.message}", e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error while polling for token")
            return TokenPollResult.Error("Invalid response from server: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while polling for token")
            return TokenPollResult.Error("Unexpected error: ${e.message}", e)
        }
    }

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
