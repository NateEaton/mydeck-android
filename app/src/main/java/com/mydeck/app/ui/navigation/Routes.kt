package com.mydeck.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkListRoute(val sharedText: String? = null)

@Serializable
data class BookmarkDetailRoute(
    val bookmarkId: String,
    val showOriginal: Boolean = false
)

@Serializable
object AccountSettingsRoute

@Serializable
object SettingsRoute

@Serializable
object OpenSourceLibrariesRoute

@Serializable
object LogViewRoute

@Serializable
object SyncSettingsRoute

@Serializable
object UiSettingsRoute

@Serializable
object AboutRoute
