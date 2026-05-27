package com.mydeck.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.ui.components.FilterBottomSheet
import com.mydeck.app.ui.theme.MyDeckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterBottomSheetValidationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launchSheet() {
        composeTestRule.setContent {
            MyDeckTheme {
                FilterBottomSheet(
                    currentFilter = FilterFormState(),
                    onApply = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun searchButtonEnabledWhenNoLengthInput() {
        launchSheet()
        composeTestRule.onNodeWithText("Search").assertIsEnabled()
    }

    @Test
    fun errorShownAndOkDisabledWhenDialogMinExceedsMax() {
        launchSheet()

        composeTestRule.onNodeWithText("Length").performClick()
        composeTestRule.onAllNodesWithText("Min")[0].performTextInput("20")
        composeTestRule.onAllNodesWithText("Max")[0].performTextInput("5")

        composeTestRule.onNodeWithText("Min must be less than or equal to max").assertExists()
        composeTestRule.onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun errorClearedWhenDialogMinEqualToMax() {
        launchSheet()

        composeTestRule.onNodeWithText("Length").performClick()
        composeTestRule.onAllNodesWithText("Min")[0].performTextInput("10")
        composeTestRule.onAllNodesWithText("Max")[0].performTextInput("10")

        composeTestRule.onNodeWithText("Min must be less than or equal to max").assertDoesNotExist()
        composeTestRule.onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun errorClearedWhenDialogMaxFieldCleared() {
        launchSheet()

        composeTestRule.onNodeWithText("Length").performClick()
        composeTestRule.onAllNodesWithText("Min")[0].performTextInput("20")
        composeTestRule.onAllNodesWithText("Max")[0].performTextInput("5")
        // Now clear max — one field empty means no error
        composeTestRule.onAllNodesWithText("Max")[0].performTextClearance()

        composeTestRule.onNodeWithText("Min must be less than or equal to max").assertDoesNotExist()
        composeTestRule.onNodeWithText("OK").assertIsEnabled()
    }
}
