package com.mydeck.app.domain.model

data class OAuthDeviceAuthorizationState(
    val clientId: String, // Needed for token polling after device authorization
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresAt: Long, // Unix timestamp (milliseconds)
    val pollingInterval: Int // seconds
)
