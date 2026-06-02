package com.mydeck.app.ui.detail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderActionFooterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders footer actions`() {
        renderAndAssert()
    }

    @Test
    fun `stacks actions in phone layout`() {
        renderFooter(isWideLayout = false)

        val favoriteBounds = composeTestRule
            .onNodeWithTag(ReaderActionFooterTestTags.FAVORITE)
            .getUnclippedBoundsInRoot()
        val archiveBounds = composeTestRule
            .onNodeWithTag(ReaderActionFooterTestTags.ARCHIVE)
            .getUnclippedBoundsInRoot()

        assertTrue(favoriteBounds.bottom <= archiveBounds.top)
    }

    @Test
    fun `lays out actions side by side in wide layout`() {
        renderFooter(isWideLayout = true)

        val favoriteBounds = composeTestRule
            .onNodeWithTag(ReaderActionFooterTestTags.FAVORITE)
            .getUnclippedBoundsInRoot()
        val archiveBounds = composeTestRule
            .onNodeWithTag(ReaderActionFooterTestTags.ARCHIVE)
            .getUnclippedBoundsInRoot()

        assertTrue(favoriteBounds.right <= archiveBounds.left)
    }

    private fun renderAndAssert() {
        val clickCounts = renderFooter()

        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.FOOTER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archive").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.FAVORITE).performClick()
        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.ARCHIVE).performClick()

        assertEquals(1, clickCounts.favorite)
        assertEquals(1, clickCounts.archive)
    }

    private fun renderFooter(isWideLayout: Boolean = false): ClickCounts {
        val clickCounts = ClickCounts()

        composeTestRule.setContent {
            MaterialTheme {
                ReaderActionFooter(
                    isFavorite = false,
                    isArchived = false,
                    isWideLayout = isWideLayout,
                    onToggleFavorite = { clickCounts.favorite++ },
                    onToggleArchive = { clickCounts.archive++ }
                )
            }
        }

        return clickCounts
    }

    private data class ClickCounts(
        var favorite: Int = 0,
        var archive: Int = 0
    )
}
