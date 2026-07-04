package com.mydeck.app.domain.usecase

import android.content.Context
import android.os.Build
import com.mydeck.app.BuildConfig
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.isHttpBlockedByBuildPolicy
import com.mydeck.app.io.rest.model.OAuthAuthCodeTokenRequestDto
import com.mydeck.app.io.rest.model.OAuthClientRegistrationRequestDto
import com.mydeck.app.io.rest.model.OAuthErrorDto
import com.mydeck.app.util.AppVersion
import com.mydeck.app.util.PkceUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class OAuthAuthorizationCodeUseCase @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val json: Json,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
        private const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
        private const val SOFTWARE_ID = "com.mydeck.app"
        // Derived from the single build-config source so the manifest intent-filter,
        // MainActivity's matcher, and this registered redirect_uri can never drift.
        val REDIRECT_URI = "${BuildConfig.OAUTH_CALLBACK_SCHEME}://${BuildConfig.OAUTH_CALLBACK_HOST}"
        private val CLIENT_NAME get() = "MyDeck Android — ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    data class InitiateResult(
        val authorizeUrl: String,
        val codeVerifier: String,
        val state: String,
        val clientId: String
    )

    sealed class AuthCodeInitiateResult {
        data class Ready(val result: InitiateResult) : AuthCodeInitiateResult()
        data object HttpBlockedByBuildPolicy : AuthCodeInitiateResult()
        data class Error(val message: String, val exception: Exception? = null) : AuthCodeInitiateResult()
    }

    sealed class TokenExchangeResult {
        data class Success(val accessToken: String) : TokenExchangeResult()
        data class UserDenied(val message: String) : TokenExchangeResult()
        data object HttpBlockedByBuildPolicy : TokenExchangeResult()
        data class Error(val message: String, val exception: Exception? = null) : TokenExchangeResult()
    }

    /**
     * Step 1: Register an ephemeral OAuth client with authorization_code grant and redirect_uri,
     * generate PKCE + state, and build the /authorize URL to open in a Custom Tab.
     */
    suspend fun initiateAuthorization(serverUrl: String): AuthCodeInitiateResult {
        try {
            Timber.d("Registering OAuth client for authorization_code flow")
            val clientRegistrationRequest = OAuthClientRegistrationRequestDto(
                clientName = CLIENT_NAME,
                clientUri = CLIENT_URI,
                softwareId = SOFTWARE_ID,
                softwareVersion = AppVersion.versionName(context),
                grantTypes = listOf(GRANT_TYPE_AUTHORIZATION_CODE, GRANT_TYPE_DEVICE_CODE),
                redirectUris = listOf(REDIRECT_URI)
            )

            val clientResponse = readeckApi.registerOAuthClient(clientRegistrationRequest)

            if (!clientResponse.isSuccessful || clientResponse.body() == null) {
                val errorMessage = parseOAuthError(clientResponse.errorBody()?.string())
                Timber.e("Client registration failed: $errorMessage")
                return AuthCodeInitiateResult.Error("Failed to register with server: $errorMessage")
            }

            val clientId = clientResponse.body()!!.clientId
            Timber.d("OAuth client registered: $clientId")

            val verifier = PkceUtil.generateCodeVerifier()
            val challenge = PkceUtil.codeChallenge(verifier)
            val state = PkceUtil.generateState()

            val authorizeUrl = PkceUtil.buildAuthorizeUrl(
                serverUrlWithApi = serverUrl,
                clientId = clientId,
                redirectUri = REDIRECT_URI,
                scope = REQUIRED_SCOPES,
                challenge = challenge,
                state = state
            )

            Timber.d("Authorization URL built; opening Custom Tab")
            return AuthCodeInitiateResult.Ready(
                InitiateResult(
                    authorizeUrl = authorizeUrl,
                    codeVerifier = verifier,
                    state = state,
                    clientId = clientId
                )
            )

        } catch (e: IOException) {
            if (e.isHttpBlockedByBuildPolicy()) {
                Timber.w(e, "HTTP blocked during authorization initiation")
                return AuthCodeInitiateResult.HttpBlockedByBuildPolicy
            }
            Timber.e(e, "Network error during authorization initiation")
            return AuthCodeInitiateResult.Error("Network error: ${e.message}", e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error during authorization initiation")
            return AuthCodeInitiateResult.Error("Invalid response from server: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during authorization initiation")
            return AuthCodeInitiateResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    /**
     * Step 2: Exchange the authorization code for an access token via POST /api/oauth/token.
     */
    suspend fun exchangeCode(code: String, codeVerifier: String): TokenExchangeResult {
        try {
            Timber.d("Exchanging authorization code for access token")
            val tokenRequest = OAuthAuthCodeTokenRequestDto(
                grantType = GRANT_TYPE_AUTHORIZATION_CODE,
                code = code,
                codeVerifier = codeVerifier
            )

            val tokenResponse = readeckApi.requestTokenWithAuthCode(tokenRequest)

            if (tokenResponse.isSuccessful && tokenResponse.body() != null) {
                Timber.i("Authorization code exchange succeeded")
                return TokenExchangeResult.Success(tokenResponse.body()!!.accessToken)
            }

            val errorBody = tokenResponse.errorBody()?.string()
            if (errorBody.isNullOrBlank()) {
                Timber.e("Token exchange failed with no error body: ${tokenResponse.code()}")
                return TokenExchangeResult.Error("Server error: ${tokenResponse.code()}")
            }

            val oauthError = try {
                json.decodeFromString<OAuthErrorDto>(errorBody)
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to parse OAuth error response")
                return TokenExchangeResult.Error("Invalid error response from server")
            }

            Timber.w("Token exchange OAuth error: ${oauthError.error} — ${oauthError.errorDescription}")
            return when (oauthError.error) {
                "access_denied" ->
                    TokenExchangeResult.UserDenied(oauthError.errorDescription ?: "Authorization was denied")
                "invalid_grant" ->
                    TokenExchangeResult.Error(oauthError.errorDescription ?: "Invalid authorization code")
                else ->
                    TokenExchangeResult.Error(oauthError.errorDescription ?: "Unknown error: ${oauthError.error}")
            }

        } catch (e: IOException) {
            if (e.isHttpBlockedByBuildPolicy()) {
                Timber.w(e, "HTTP blocked during token exchange")
                return TokenExchangeResult.HttpBlockedByBuildPolicy
            }
            Timber.e(e, "Network error during token exchange")
            return TokenExchangeResult.Error("Network error: ${e.message}", e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error during token exchange")
            return TokenExchangeResult.Error("Invalid response from server: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during token exchange")
            return TokenExchangeResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    private fun parseOAuthError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Unknown error"
        return try {
            val oauthError = json.decodeFromString<OAuthErrorDto>(errorBody)
            oauthError.errorDescription ?: oauthError.error
        } catch (e: SerializationException) {
            "Server error"
        }
    }
}
