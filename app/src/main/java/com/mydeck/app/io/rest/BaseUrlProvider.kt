package com.mydeck.app.io.rest

import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor(
    @ApplicationScope applicationScope: CoroutineScope,
    settingsDataStore: SettingsDataStore
) {
    @Volatile
    private var baseUrl: String? = null

    init {
        // Eager read: block once during initialization to prevent race condition
        baseUrl = runBlocking {
            settingsDataStore.urlFlow.first()
        }

        // Then start reactive updates for any future changes
        applicationScope.launch {
            settingsDataStore.urlFlow.collectLatest {
                baseUrl = it
            }
        }
    }

    /**
     * The configured server base URL, or null when no server is set yet (e.g. before
     * sign-in). Callers on the OkHttp chain must treat null as "not configured" and
     * fail with an IOException so the failure is delivered through normal call
     * callbacks rather than crashing the dispatcher thread.
     */
    fun getBaseUrl(): String? = baseUrl
}
