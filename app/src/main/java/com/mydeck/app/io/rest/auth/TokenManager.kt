package com.mydeck.app.io.rest.auth

import com.mydeck.app.io.prefs.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    // Read the token synchronously so a request fired immediately after saveToken (e.g. the
    // GET /profile in completeLogin) sees the new token. A previous async flow-cache had a
    // read-after-write gap that left the /profile call unauthenticated, so login fell back to
    // the placeholder username "user".
    fun getToken(): String? = settingsDataStore.getTokenSync()
}
