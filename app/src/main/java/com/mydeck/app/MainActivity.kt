package com.mydeck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.ui.navigation.AccountSettingsRoute
import com.mydeck.app.ui.navigation.BookmarkDetailRoute
import com.mydeck.app.ui.shell.AppShell
import com.mydeck.app.ui.theme.MyDeckTheme
import com.mydeck.app.io.prefs.SettingsDataStore
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private lateinit var intentState: MutableState<Intent?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = hiltViewModel<MainViewModel>()
            val theme = viewModel.theme.collectAsState()
            val sepiaEnabled = viewModel.sepiaEnabled.collectAsState()
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
                    // Consume the intent after processing
                    intentState.value = null
                }
            }

            val themeValue = when (theme.value) {
                Theme.SYSTEM -> if (isSystemInDarkTheme()) Theme.DARK else Theme.LIGHT
                else -> theme.value
            }

            MyDeckTheme(theme = themeValue, sepiaEnabled = sepiaEnabled.value) {
                AppShell(navController, settingsDataStore)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentState.value = intent
    }
}
