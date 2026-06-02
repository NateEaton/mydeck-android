package com.mydeck.app.ui.detail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mydeck.app.ui.theme.ReaderThemePalette
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
    fun `renders footer actions for paper palette`() {
        renderAndAssert(paperPalette)
    }

    @Test
    fun `renders footer actions for sepia palette`() {
        renderAndAssert(sepiaPalette)
    }

    @Test
    fun `renders footer actions for dark palette`() {
        renderAndAssert(darkPalette)
    }

    @Test
    fun `renders footer actions for black palette`() {
        renderAndAssert(blackPalette)
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

    private fun renderAndAssert(palette: ReaderThemePalette) {
        val clickCounts = renderFooter(palette = palette)

        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.FOOTER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archive").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.FAVORITE).performClick()
        composeTestRule.onNodeWithTag(ReaderActionFooterTestTags.ARCHIVE).performClick()

        assertEquals(1, clickCounts.favorite)
        assertEquals(1, clickCounts.archive)
    }

    private fun renderFooter(
        palette: ReaderThemePalette = paperPalette,
        isWideLayout: Boolean = false
    ): ClickCounts {
        val clickCounts = ClickCounts()

        composeTestRule.setContent {
            MaterialTheme {
                ReaderActionFooter(
                    isFavorite = false,
                    isArchived = false,
                    isWideLayout = isWideLayout,
                    onToggleFavorite = { clickCounts.favorite++ },
                    onToggleArchive = { clickCounts.archive++ },
                    palette = palette
                )
            }
        }

        return clickCounts
    }

    private data class ClickCounts(
        var favorite: Int = 0,
        var archive: Int = 0
    )

    private companion object {
        val paperPalette = testPalette(body = "#4A4A4A", background = "#F9F9F9", accent = "#D7EEF2", onAccent = "#123B43")
        val sepiaPalette = testPalette(body = "#4A3B2B", background = "#F4ECD8", accent = "#A5734A", onAccent = "#F9F9F9")
        val darkPalette = testPalette(body = "#C9C9C9", background = "#222222", accent = "#37474F", onAccent = "#FFFFFF")
        val blackPalette = testPalette(body = "#F5F5F5", background = "#000000", accent = "#263238", onAccent = "#FFFFFF")

        fun testPalette(
            body: String,
            background: String,
            accent: String,
            onAccent: String
        ) = ReaderThemePalette(
            accentColor = accent,
            accentContainerColor = accent,
            accentUnderlineColor = "rgba(0, 0, 0, 0.5)",
            onAccentColor = onAccent,
            onAccentContainerColor = onAccent,
            bodyColor = body,
            bodyBackgroundColor = background,
            blockquoteBackgroundColor = background,
            codeBackgroundColor = background,
            tableBorderColor = background,
            inputColor = body,
            inputBackgroundColor = background,
            inputBorderColor = background,
            linkColor = accent,
            linkVisitedColor = accent,
            linkHoverColor = accent,
            buttonBorderColor = accent,
            annotationActiveOutlineColor = "rgba(0, 0, 0, 0.7)",
            annotationActiveShadowColor = "rgba(0, 0, 0, 0.2)",
            annotationYellowColor = "rgba(255, 235, 59, 0.4)",
            annotationRedColor = "rgba(239, 83, 80, 0.35)",
            annotationBlueColor = "rgba(66, 165, 245, 0.35)",
            annotationGreenColor = "rgba(102, 187, 106, 0.35)",
            annotationNoneUnderlineColor = "rgba(150, 150, 150, 0.6)"
        )
    }
}
