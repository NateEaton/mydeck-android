package com.mydeck.app.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.ui.theme.MyDeckTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import com.mydeck.app.io.rest.NetworkModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34])
@UninstallModules(NetworkModule::class)
class UiSettingsScreenUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private fun buildState(keepScreenOn: Boolean = true) = UiSettingsUiState(
        themeMode = Theme.SYSTEM,
        useSepiaInLight = false,
        themeOptions = listOf(),
        showDialog = false,
        themeLabel = Theme.SYSTEM.toLabelResource(),
        keepScreenOnWhileReading = keepScreenOn,
    )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun uiSettingsView_displaysKeepScreenOnToggle() {
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(),
                    onThemeModeSelected = {},
                    onSepiaToggled = {},
                    onKeepScreenOnWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keep screen on while reading").assertIsDisplayed()
    }

    @Test
    fun uiSettingsView_keepScreenOnToggle_defaultsToOn() {
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(keepScreenOn = true),
                    onThemeModeSelected = {},
                    onSepiaToggled = {},
                    onKeepScreenOnWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keep screen on while reading").assertIsDisplayed()
    }

    @Test
    fun uiSettingsView_keepScreenOnToggle_clickCallsCallback() {
        var toggledValue: Boolean? = null
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(keepScreenOn = true),
                    onThemeModeSelected = {},
                    onSepiaToggled = {},
                    onKeepScreenOnWhileReadingToggled = { toggledValue = it },
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keep screen on while reading").performClick()
        assertFalse(toggledValue!!)
    }

    @Test
    fun uiSettingsView_keepScreenOnToggle_offState_clickCallsCallbackWithTrue() {
        var toggledValue: Boolean? = null
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(keepScreenOn = false),
                    onThemeModeSelected = {},
                    onSepiaToggled = {},
                    onKeepScreenOnWhileReadingToggled = { toggledValue = it },
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keep screen on while reading").performClick()
        assertTrue(toggledValue!!)
    }
}
