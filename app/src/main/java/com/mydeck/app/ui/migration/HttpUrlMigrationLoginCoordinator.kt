package com.mydeck.app.ui.migration

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpUrlMigrationLoginCoordinator @Inject constructor() {
    private val pendingLoginUrl = AtomicReference<String?>()

    fun requestLogin(normalizedUrl: String) {
        pendingLoginUrl.set(normalizedUrl)
    }

    fun cancelLogin(normalizedUrl: String) {
        pendingLoginUrl.compareAndSet(normalizedUrl, null)
    }

    fun consumePendingLoginUrl(): String? = pendingLoginUrl.getAndSet(null)
}
