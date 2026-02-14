package com.mydeck.app.io.rest

import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor(
    @ApplicationScope applicationScope: CoroutineScope,
    settingsDataStore: SettingsDataStore
) {
    init {
        applicationScope.launch {
            settingsDataStore.urlFlow.collectLatest {
                baseUrl = it
            }
        }
    }

    @Volatile
    private var baseUrl: String? = null

    fun getBaseUrl(): String? = baseUrl
}
