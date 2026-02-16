package com.mydeck.app.ui.shell

import android.annotation.SuppressLint
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.ui.about.AboutScreen
import com.mydeck.app.ui.detail.BookmarkDetailScreen
import com.mydeck.app.ui.list.BookmarkListScreen
import com.mydeck.app.ui.list.BookmarkListViewModel
import com.mydeck.app.ui.navigation.AboutRoute
import com.mydeck.app.ui.navigation.AccountSettingsRoute
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import com.mydeck.app.ui.navigation.BookmarkListRoute
import com.mydeck.app.ui.navigation.LogViewRoute
import com.mydeck.app.ui.navigation.OpenSourceLibrariesRoute
import com.mydeck.app.ui.navigation.SettingsRoute
import com.mydeck.app.ui.navigation.SyncSettingsRoute
import com.mydeck.app.ui.navigation.UiSettingsRoute
import com.mydeck.app.ui.navigation.WelcomeRoute
import com.mydeck.app.ui.settings.AccountSettingsScreen
import com.mydeck.app.ui.settings.LogViewScreen
import com.mydeck.app.ui.settings.OpenSourceLibrariesScreen
import com.mydeck.app.ui.settings.SettingsScreen
import com.mydeck.app.ui.settings.SyncSettingsScreen
import com.mydeck.app.ui.settings.UiSettingsScreen
import com.mydeck.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("WrongStartDestinationType")
@Composable
fun AppShell(
    navController: NavHostController,
    settingsDataStore: SettingsDataStore? = null,
) {
    val bookmarkListViewModel: BookmarkListViewModel = hiltViewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Collect drawer-relevant state from ViewModel
    val filterState = bookmarkListViewModel.filterState.collectAsState()
    val bookmarkCounts = bookmarkListViewModel.bookmarkCounts.collectAsState()
    val labelsWithCounts = bookmarkListViewModel.labelsWithCounts.collectAsState()
    val isOnline = bookmarkListViewModel.isOnline.collectAsState()

    // Collect navigation events from the ViewModel and handle them here,
    // since we now own the drawer state and need to close it after navigation
    LaunchedEffect(Unit) {
        bookmarkListViewModel.navigationEvent.collectLatest { event ->
            when (event) {
                is BookmarkListViewModel.NavigationEvent.NavigateToSettings -> {
                    navController.navigate(SettingsRoute)
                    scope.launch { drawerState.close() }
                }

                is BookmarkListViewModel.NavigationEvent.NavigateToAbout -> {
                    navController.navigate(AboutRoute)
                    scope.launch { drawerState.close() }
                }

                is BookmarkListViewModel.NavigationEvent.NavigateToBookmarkDetail -> {
                    navController.navigate(
                        BookmarkDetailRoute(event.bookmarkId, event.showOriginal)
                    )
                }
            }
        }
    }

    // Only allow swipe-to-open drawer gesture on BookmarkListScreen (matches original behavior)
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val isOnBookmarkList = currentBackStackEntry?.destination?.route
        ?.startsWith(BookmarkListRoute::class.qualifiedName ?: "") == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isOnBookmarkList,
        drawerContent = {
            AppDrawerContent(
                filterState = filterState.value,
                bookmarkCounts = bookmarkCounts.value,
                labelsWithCounts = labelsWithCounts.value,
                isOnline = isOnline.value,
                onClickMyList = {
                    bookmarkListViewModel.onClickMyList()
                    scope.launch { drawerState.close() }
                },
                onClickArchive = {
                    bookmarkListViewModel.onClickArchive()
                    scope.launch { drawerState.close() }
                },
                onClickFavorite = {
                    bookmarkListViewModel.onClickFavorite()
                    scope.launch { drawerState.close() }
                },
                onClickLabels = {
                    bookmarkListViewModel.onClickLabelsView()
                    scope.launch { drawerState.close() }
                },
                onClickSettings = {
                    bookmarkListViewModel.onClickSettings()
                    scope.launch { drawerState.close() }
                },
                onClickAbout = {
                    bookmarkListViewModel.onClickAbout()
                    scope.launch { drawerState.close() }
                },
            )
        }
    ) {
        // Persistent themed Surface prevents white flash between screen transitions
        Surface(color = MaterialTheme.colorScheme.background) {
            // Determine start destination based on auth state
            val token = settingsDataStore?.tokenFlow?.collectAsState()?.value
            val startDestination: Any = if (token.isNullOrBlank()) {
                WelcomeRoute
            } else {
                BookmarkListRoute()
            }

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable<BookmarkListRoute> {
                    BookmarkListScreen(navController, bookmarkListViewModel, drawerState)
                }
                composable<SettingsRoute> { SettingsScreen(navController) }
                composable<WelcomeRoute> { WelcomeScreen(navController) }
                composable<AccountSettingsRoute> { AccountSettingsScreen(navController) }
                composable<BookmarkDetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<BookmarkDetailRoute>()
                    BookmarkDetailScreen(
                        navController,
                        route.bookmarkId,
                        showOriginal = route.showOriginal
                    )
                }
                composable<OpenSourceLibrariesRoute> {
                    OpenSourceLibrariesScreen(navHostController = navController)
                }
                composable<LogViewRoute> {
                    LogViewScreen(navController = navController)
                }
                composable<SyncSettingsRoute> {
                    SyncSettingsScreen(navHostController = navController)
                }
                composable<UiSettingsRoute> {
                    UiSettingsScreen(navHostController = navController)
                }
                composable<AboutRoute> {
                    AboutScreen(navHostController = navController)
                }
            }
        }
    }
}
