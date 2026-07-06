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
        private const val LOGO_URI = "data:image/png;base64,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"
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
                redirectUris = listOf(REDIRECT_URI),
                logoUri = LOGO_URI
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
