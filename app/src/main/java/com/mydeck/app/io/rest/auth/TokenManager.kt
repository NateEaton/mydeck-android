package com.mydeck.app.io.rest.auth

import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationScope applicationScope: CoroutineScope,
    settingsDataStore: SettingsDataStore
) {
    init {
        applicationScope.launch {
            settingsDataStore.tokenFlow.collectLatest {
                token = it
            }
        }
    }

    @Volatile
    private var token: String? = null

    fun getToken(): String? = token
}