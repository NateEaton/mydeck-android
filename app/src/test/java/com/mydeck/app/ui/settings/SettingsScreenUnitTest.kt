package com.mydeck.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import com.mydeck.app.io.rest.NetworkModule
import com.mydeck.app.ui.theme.MyDeckTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@UninstallModules(NetworkModule::class)
class SettingsScreenUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun settingsScreen_displaysTitle() {
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.TOPBAR).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAccountSetting() {
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        val expectedTitle = "Account"
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SETTINGS_ITEM_TITLE)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAccountSettingWithUsername() {
        val expectedUsername = "test"
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = expectedUsername),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        with(
            composeTestRule.onNodeWithTag(
                "${SettingsScreenTestTags.SETTINGS_ITEM_SUBTITLE}.${SettingsScreenTestTags.SETTINGS_ITEM_ACCOUNT}",
                useUnmergedTree = true
            )
        ) {
            assertIsDisplayed()
            assertTextEquals(expectedUsername)
        }
    }

    @Test
    fun settingsScreen_clickAccountSetting_callsOnClickAccount() {
        var accountClicked = false
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = { accountClicked = true },
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(
            "${SettingsScreenTestTags.SETTINGS_ITEM}.${SettingsScreenTestTags.SETTINGS_ITEM_ACCOUNT}",
            useUnmergedTree = true
        ).performClick()
        assertTrue(accountClicked)
    }

    @Test
    fun settingsScreen_displaysBackButton() {
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACK_BUTTON).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_clickBackButton_callsOnClickBack() {
        var backClicked = false
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = { backClicked = true },
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACK_BUTTON).assertIsDisplayed()
            .performClick()
        assertTrue(backClicked)
    }

    @Test
    fun settingsScreen_clickLogs_callsOnClickLogs() {
        var logsClicked = false
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = { logsClicked = true },
                    onClickSync = {},
                    onClickUi = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(
            "${SettingsScreenTestTags.SETTINGS_ITEM}.${SettingsScreenTestTags.SETTINGS_ITEM_LOGS}",
            useUnmergedTree = true
        ).performClick()
        assertTrue(logsClicked)
    }

    @Test
    fun settingsScreen_clickUi_callsOnClickUi() {
        var uiClicked = false
        composeTestRule.setContent {
            MyDeckTheme {
                SettingScreenView(
                    settingsUiState = SettingsUiState(username = "test"),
                    onClickAccount = {},
                    onClickBack = {},
                    onClickOpenSourceLibraries = {},
                    onClickLogs = {},
                    onClickSync = {},
                    onClickUi = { uiClicked = true}
                )
            }
        }

        composeTestRule.onNodeWithTag(
            "${SettingsScreenTestTags.SETTINGS_ITEM}.${SettingsScreenTestTags.SETTINGS_ITEM_UI}",
            useUnmergedTree = true
        ).performClick()
        assertTrue(uiClicked)
    }
}
