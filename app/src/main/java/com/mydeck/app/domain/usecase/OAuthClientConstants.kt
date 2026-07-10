package com.mydeck.app.domain.usecase

import android.os.Build
import com.mydeck.app.io.rest.model.OAuthErrorDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Client identity shared by every OAuth grant flow (device code, authorization code).
 * Both flows register independent ephemeral clients but must present the same identity.
 */
internal object OAuthClientConstants {
    const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
    const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
    const val SOFTWARE_ID = "com.mydeck.app"
    val CLIENT_NAME get() = "MyDeck Android — ${Build.MANUFACTURER} ${Build.MODEL}"

    fun parseOAuthError(json: Json, errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Unknown error"
        return try {
            val oauthError = json.decodeFromString<OAuthErrorDto>(errorBody)
            oauthError.errorDescription ?: oauthError.error
        } catch (e: SerializationException) {
            "Server error"
        }
    }
}
