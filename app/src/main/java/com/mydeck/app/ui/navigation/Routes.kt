package com.mydeck.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkListRoute(val sharedText: String? = null)

@Serializable
data class BookmarkDetailRoute(
    val bookmarkId: String,
    val showOriginal: Boolean = false,
    val annotationId: String? = null
)

@Serializable
object HighlightsRoute

@Serializable
object CollectionsRoute

@Serializable
object WelcomeRoute

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

@Serializable
object UserGuideRoute

@Serializable
data class UserGuideSectionRoute(
    val fileName: String,
    val title: String,
    // Optional text to scroll to when the page opens (e.g. a matched heading or
    // the search query when arriving from guide search). Null for normal navigation.
    val anchor: String? = null,
    // Optional search term to highlight throughout the page when arriving from
    // guide search. Null for normal navigation.
    val query: String? = null
)
