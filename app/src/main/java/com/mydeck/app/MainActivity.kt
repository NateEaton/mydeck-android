package com.mydeck.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.usecase.FullSyncUseCase
import com.mydeck.app.ui.detail.BookmarkDetailScreen
import com.mydeck.app.ui.list.BookmarkListScreen
import com.mydeck.app.ui.navigation.AccountSettingsRoute
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import com.mydeck.app.ui.navigation.BookmarkListRoute
import com.mydeck.app.ui.navigation.LogViewRoute
import com.mydeck.app.ui.navigation.OpenSourceLibrariesRoute
import com.mydeck.app.ui.navigation.SettingsRoute
import com.mydeck.app.ui.navigation.SyncSettingsRoute
import com.mydeck.app.ui.navigation.UiSettingsRoute
import com.mydeck.app.ui.navigation.AboutRoute
import com.mydeck.app.ui.about.AboutScreen
import com.mydeck.app.ui.settings.AccountSettingsScreen
import com.mydeck.app.ui.settings.LogViewScreen
import com.mydeck.app.ui.settings.OpenSourceLibrariesScreen
import com.mydeck.app.ui.settings.SettingsScreen
import com.mydeck.app.ui.settings.SyncSettingsScreen
import com.mydeck.app.ui.settings.UiSettingsScreen
import com.mydeck.app.ui.theme.MyDeckTheme
import com.mydeck.app.io.prefs.SettingsDataStore
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var fullSyncUseCase: FullSyncUseCase

    private lateinit var intentState: MutableState<Intent?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (settingsDataStore.isSyncOnAppOpenEnabled()) {
                    Timber.d("App Open: Triggering Full Sync")
                    fullSyncUseCase.performFullSync()
                }
            }
        }

        setContent {
            val viewModel = hiltViewModel<MainViewModel>()
            val theme = viewModel.theme.collectAsState()
            val navController = rememberNavController()
            intentState = remember { mutableStateOf(intent) }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val noValidUrlMessage = stringResource(id = R.string.not_valid_url)

            LaunchedEffect(intentState.value) {
                intentState.value?.let { newIntent ->
                    if (newIntent.action == Intent.ACTION_SEND && newIntent.type == "text/plain") {
                        val sharedText = newIntent.getStringExtra(Intent.EXTRA_TEXT)
                        if (sharedText.isNullOrBlank()) {
                            scope.launch {
                                Toast.makeText(context, noValidUrlMessage, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            navController.navigate(BookmarkListRoute(sharedText = sharedText))
                        }
                    }
                    if (newIntent.hasExtra("navigateToAccountSettings")) {
                        Timber.d("Navigating to AccountSettingsScreen")
                        newIntent.removeExtra("navigateToAccountSettings") // Prevent re-navigation
                        navController.navigate(AccountSettingsRoute)
                    }
                    // Consume the intent after processing
                    intentState.value = null
                }
            }

            val themeValue = when (theme.value) {
                Theme.SYSTEM -> if (isSystemInDarkTheme()) Theme.DARK else Theme.LIGHT
                else -> theme.value
            }

            MyDeckTheme(theme = themeValue) {
                MyDeckNavHost(navController, settingsDataStore)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentState.value = intent
    }
}

@SuppressLint("WrongStartDestinationType")
@Composable
fun MyDeckNavHost(navController: NavHostController, settingsDataStore: SettingsDataStore? = null) {
    // Determine start destination based on auth state
    val token = settingsDataStore?.tokenFlow?.collectAsState()?.value
    val startDestination: Any = if (token.isNullOrBlank()) {
        AccountSettingsRoute
    } else {
        BookmarkListRoute()
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<BookmarkListRoute> { BookmarkListScreen(navController) }
        composable<SettingsRoute> { SettingsScreen(navController) }
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
