package com.mydeck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.OAuthCallbackRepository
import com.mydeck.app.domain.model.resolveEffectiveAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.ui.navigation.AccountSettingsRoute
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import com.mydeck.app.ui.shell.AppShell
import com.mydeck.app.ui.theme.MyDeckTheme
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.ui.migration.HttpUrlMigrationScreen
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var oauthCallbackRepository: OAuthCallbackRepository

    private lateinit var intentState: MutableState<Intent?>

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !mainViewModel.isReady.value }
        enableEdgeToEdge()
        setContent {
            val viewModel = mainViewModel
            val theme = viewModel.theme.collectAsState()
            val lightAppearance = viewModel.lightAppearance.collectAsState()
            val darkAppearance = viewModel.darkAppearance.collectAsState()
            val httpUrlMigrationState = viewModel.httpUrlMigrationState.collectAsState()
            val navController = rememberNavController()
            intentState = remember { mutableStateOf(intent) }

            LaunchedEffect(intentState.value) {
                intentState.value?.let { newIntent ->
                    if (newIntent.hasExtra("navigateToAccountSettings")) {
                        Timber.d("Navigating to AccountSettingsScreen")
                        newIntent.removeExtra("navigateToAccountSettings") // Prevent re-navigation
                        navController.navigate(AccountSettingsRoute)
                    }
                    if (newIntent.hasExtra("navigateToBookmarkDetail")) {
                        val bookmarkId = newIntent.getStringExtra("navigateToBookmarkDetail")
                        if (bookmarkId != null) {
                            Timber.d("Navigating to BookmarkDetail: $bookmarkId")
                            newIntent.removeExtra("navigateToBookmarkDetail")
                            navController.navigate(BookmarkDetailRoute(bookmarkId))
                        }
                    }
                    dispatchOAuthCallbackIfPresent(newIntent)
                    // Consume the intent after processing
                    intentState.value = null
                }
            }

            val effectiveAppearance = resolveEffectiveAppearance(
                themeMode = theme.value,
                isSystemDark = isSystemInDarkTheme(),
                lightAppearance = lightAppearance.value,
                darkAppearance = darkAppearance.value
            )

            MyDeckTheme(appearance = effectiveAppearance) {
                if (httpUrlMigrationState.value is MainViewModel.HttpUrlMigrationState.Required) {
                    HttpUrlMigrationScreen()
                } else {
                    AppShell(navController, settingsDataStore)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // onNewIntent can arrive during resume — after onCreate returns but before setContent's
        // composition has initialized `intentState` (e.g. the cold-start OAuth redirect after a
        // process death). Update the activity intent so the initial composition picks it up, and
        // only touch the Compose state once it actually exists.
        setIntent(intent)
        if (::intentState.isInitialized) {
            intentState.value = intent
        }
    }

    private fun dispatchOAuthCallbackIfPresent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != BuildConfig.OAUTH_CALLBACK_SCHEME ||
            data.host != BuildConfig.OAUTH_CALLBACK_HOST
        ) return

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val error = data.getQueryParameter("error")

        when {
            error != null -> {
                val errorDescription = data.getQueryParameter("error_description")
                Timber.w("OAuth callback error: $error — $errorDescription")
                oauthCallbackRepository.dispatch(
                    OAuthCallbackRepository.OAuthCallbackEvent.Error(error, errorDescription)
                )
            }
            !code.isNullOrBlank() && !state.isNullOrBlank() -> {
                Timber.d("OAuth callback received: code present, state=$state")
                oauthCallbackRepository.dispatch(
                    OAuthCallbackRepository.OAuthCallbackEvent.Success(code, state)
                )
            }
            else -> {
                Timber.w("OAuth callback received but missing both error and valid code+state — ignoring")
            }
        }
    }
}
