package com.mydeck.app.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mydeck.app.domain.model.LayoutMode
import com.mydeck.app.domain.model.SortOption
import com.mydeck.app.ui.theme.MyDeckTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkListTopBarOverflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun launchNormalMode(
        onOpenFilterSheet: () -> Unit = {},
        onEnterMultiSelectMode: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyDeckTheme {
                BookmarkListBarActions(
                    isLabelMode = false,
                    layoutMode = LayoutMode.MOSAIC,
                    sortOption = SortOption.ADDED_NEWEST,
                    onLayoutModeSelected = {},
                    onSortOptionSelected = {},
                    onOpenFilterSheet = onOpenFilterSheet,
                    onEnterMultiSelectMode = onEnterMultiSelectMode,
                    onRequestRenameLabel = {},
                    onRequestDeleteLabel = {},
                )
            }
        }
    }

    private fun launchLabelMode(
        onEnterMultiSelectMode: () -> Unit = {},
        onRequestRenameLabel: () -> Unit = {},
        onRequestDeleteLabel: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyDeckTheme {
                BookmarkListBarActions(
                    isLabelMode = true,
                    layoutMode = LayoutMode.MOSAIC,
                    sortOption = SortOption.ADDED_NEWEST,
                    onLayoutModeSelected = {},
                    onSortOptionSelected = {},
                    onOpenFilterSheet = {},
                    onEnterMultiSelectMode = onEnterMultiSelectMode,
                    onRequestRenameLabel = onRequestRenameLabel,
                    onRequestDeleteLabel = onRequestDeleteLabel,
                )
            }
        }
    }

    private fun openOverflow() {
        composeTestRule.onNodeWithContentDescription("More options").performClick()
    }

    // -------------------------------------------------------------------------
    // §8 — Normal mode
    // -------------------------------------------------------------------------

    @Test
    fun normalMode_barShowsLayoutSortAndOverflow() {
        launchNormalMode()

        composeTestRule.onNodeWithContentDescription("Layout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sort").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun normalMode_overflowContains_FilterThenSelectBookmarks_inOrder() {
        launchNormalMode()
        openOverflow()

        // Both items appear
        composeTestRule.onNodeWithText("Filter bookmarks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select bookmarks").assertIsDisplayed()

        // Filter comes before Select bookmarks in the semantic tree (positional ordering)
        val filterNode = composeTestRule.onNodeWithText("Filter bookmarks")
            .fetchSemanticsNode()
        val selectNode = composeTestRule.onNodeWithText("Select bookmarks")
            .fetchSemanticsNode()
        assertTrue(
            "Filter bookmarks must appear above Select bookmarks",
            filterNode.positionInRoot.y < selectNode.positionInRoot.y
        )
    }

    @Test
    fun normalMode_overflowDoesNotContain_RenameLabel_OrDeleteLabel() {
        launchNormalMode()
        openOverflow()

        composeTestRule.onNodeWithText("Rename label").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete label").assertDoesNotExist()
    }

    @Test
    fun normalMode_tapFilter_callsOnOpenFilterSheet() {
        var called = false
        launchNormalMode(onOpenFilterSheet = { called = true })
        openOverflow()

        composeTestRule.onNodeWithText("Filter bookmarks").performClick()

        assertTrue("onOpenFilterSheet should have been called", called)
    }

    @Test
    fun normalMode_tapSelectBookmarks_callsOnEnterMultiSelectMode() {
        var called = false
        launchNormalMode(onEnterMultiSelectMode = { called = true })
        openOverflow()

        composeTestRule.onNodeWithText("Select bookmarks").performClick()

        assertTrue("onEnterMultiSelectMode should have been called", called)
    }

    // -------------------------------------------------------------------------
    // §8 — Label mode
    // -------------------------------------------------------------------------

    @Test
    fun labelMode_barShowsLayoutSortAndOverflow() {
        launchLabelMode()

        composeTestRule.onNodeWithContentDescription("Layout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sort").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun labelMode_overflowContains_RenameThenDeleteThenSelectBookmarks() {
        launchLabelMode()
        openOverflow()

        composeTestRule.onNodeWithText("Rename label").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete label").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select bookmarks").assertIsDisplayed()

        val renameNode = composeTestRule.onNodeWithText("Rename label").fetchSemanticsNode()
        val deleteNode = composeTestRule.onNodeWithText("Delete label").fetchSemanticsNode()
        val selectNode = composeTestRule.onNodeWithText("Select bookmarks").fetchSemanticsNode()

        assertTrue(
            "Rename label must appear above Delete label",
            renameNode.positionInRoot.y < deleteNode.positionInRoot.y
        )
        assertTrue(
            "Delete label must appear above Select bookmarks",
            deleteNode.positionInRoot.y < selectNode.positionInRoot.y
        )
    }

    @Test
    fun labelMode_overflowDoesNotContain_FilterBookmarks() {
        launchLabelMode()
        openOverflow()

        composeTestRule.onNodeWithText("Filter bookmarks").assertDoesNotExist()
    }

    @Test
    fun labelMode_tapRenameLabel_callsOnRequestRenameLabel() {
        var called = false
        launchLabelMode(onRequestRenameLabel = { called = true })
        openOverflow()

        composeTestRule.onNodeWithText("Rename label").performClick()

        assertTrue("onRequestRenameLabel should have been called", called)
    }

    @Test
    fun labelMode_tapDeleteLabel_callsOnRequestDeleteLabel() {
        var called = false
        launchLabelMode(onRequestDeleteLabel = { called = true })
        openOverflow()

        composeTestRule.onNodeWithText("Delete label").performClick()

        assertTrue("onRequestDeleteLabel should have been called", called)
    }

    @Test
    fun labelMode_tapSelectBookmarks_callsOnEnterMultiSelectMode() {
        var called = false
        launchLabelMode(onEnterMultiSelectMode = { called = true })
        openOverflow()

        composeTestRule.onNodeWithText("Select bookmarks").performClick()

        assertTrue("onEnterMultiSelectMode should have been called", called)
    }
}
