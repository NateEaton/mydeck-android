package com.mydeck.app.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
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

    private fun buildState(
        shareFormat: BookmarkShareFormat = BookmarkShareFormat.URL_ONLY,
        keepScreenOn: Boolean = true,
        fullscreenWhileReading: Boolean = false
    ) = UiSettingsUiState(
        themeMode = Theme.SYSTEM,
        lightAppearance = LightAppearance.PAPER,
        darkAppearance = DarkAppearance.DARK,
        themeOptions = listOf(),
        showDialog = false,
        themeLabel = Theme.SYSTEM.toLabelResource(),
        bookmarkShareFormat = shareFormat,
        keepScreenOnWhileReading = keepScreenOn,
        fullscreenWhileReading = fullscreenWhileReading,
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
                    onLightAppearanceSelected = {},
                    onDarkAppearanceSelected = {},
                    onBookmarkShareFormatSelected = {},
                    onKeepScreenOnWhileReadingToggled = {},
                    onFullscreenWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("When app is light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paper").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sepia").assertIsDisplayed()
        composeTestRule.onNodeWithText("When app is dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("Black").assertIsDisplayed()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.SHARE_FORMAT_SECTION).performScrollTo()
        composeTestRule.onNodeWithText("Share links as").assertIsDisplayed()
        composeTestRule.onNodeWithText("Title + URL").assertIsDisplayed()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.FULLSCREEN_READING_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.FULLSCREEN_READING_ROW).assertIsDisplayed()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).assertIsDisplayed()
    }

    @Test
    fun uiSettingsView_keepScreenOnToggle_defaultsToOn() {
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(keepScreenOn = true),
                    onThemeModeSelected = {},
                    onLightAppearanceSelected = {},
                    onDarkAppearanceSelected = {},
                    onBookmarkShareFormatSelected = {},
                    onKeepScreenOnWhileReadingToggled = {},
                    onFullscreenWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).assertIsDisplayed()
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
                    onLightAppearanceSelected = {},
                    onDarkAppearanceSelected = {},
                    onBookmarkShareFormatSelected = {},
                    onKeepScreenOnWhileReadingToggled = { toggledValue = it },
                    onFullscreenWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performClick()
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
                    onLightAppearanceSelected = {},
                    onDarkAppearanceSelected = {},
                    onBookmarkShareFormatSelected = {},
                    onKeepScreenOnWhileReadingToggled = { toggledValue = it },
                    onFullscreenWhileReadingToggled = {},
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW).performClick()
        assertTrue(toggledValue!!)
    }

    @Test
    fun uiSettingsView_fullscreenToggle_clickCallsCallback() {
        var toggledValue: Boolean? = null
        composeTestRule.setContent {
            MyDeckTheme {
                UiSettingsView(
                    snackbarHostState = SnackbarHostState(),
                    settingsUiState = buildState(fullscreenWhileReading = false),
                    onThemeModeSelected = {},
                    onLightAppearanceSelected = {},
                    onDarkAppearanceSelected = {},
                    onBookmarkShareFormatSelected = {},
                    onKeepScreenOnWhileReadingToggled = {},
                    onFullscreenWhileReadingToggled = { toggledValue = it },
                    onClickBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.FULLSCREEN_READING_ROW).performScrollTo()
        composeTestRule.onNodeWithTag(UiSettingsScreenTestTags.FULLSCREEN_READING_ROW).performClick()
        assertTrue(toggledValue!!)
    }

}
