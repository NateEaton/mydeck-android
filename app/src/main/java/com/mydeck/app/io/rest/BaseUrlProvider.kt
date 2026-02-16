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

    fun getBaseUrl(): String = baseUrl ?: throw IllegalStateException("BaseUrl not initialized")
}
