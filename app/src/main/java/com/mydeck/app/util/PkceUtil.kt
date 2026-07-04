package com.mydeck.app.util

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PkceUtil {

    private const val VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val VERIFIER_LENGTH = 64

    fun generateCodeVerifier(): String {
        val random = SecureRandom()
        return (1..VERIFIER_LENGTH)
            .map { VERIFIER_CHARS[random.nextInt(VERIFIER_CHARS.length)] }
            .joinToString("")
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildAuthorizeUrl(
        serverUrlWithApi: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        challenge: String,
        state: String
    ): String {
        val baseUrl = serverUrlWithApi.removeSuffix("/api")
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to scope,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$baseUrl/authorize?$query"
    }
}
