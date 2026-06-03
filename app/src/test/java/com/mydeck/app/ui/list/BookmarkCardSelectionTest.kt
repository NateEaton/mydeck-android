package com.mydeck.app.ui.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkCardSelectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selection mode shows favorite and archive state indicators alongside select indicator`() {
        val toggles = AtomicInteger(0)
        val navigations = AtomicInteger(0)
        val favoriteToggles = AtomicInteger(0)
        val archiveToggles = AtomicInteger(0)

        composeTestRule.setContent {
            MaterialTheme {
                BookmarkGridCard(
                    bookmark = bookmarkListItem(),
                    onClickCard = { navigations.incrementAndGet() },
                    onClickDelete = {},
                    onClickFavorite = { _, _ -> favoriteToggles.incrementAndGet() },
                    onClickArchive = { _, _ -> archiveToggles.incrementAndGet() },
                    isSelectionMode = true,
                    isSelected = false,
                    onToggleSelection = { toggles.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Archive").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Not favorited").assertExists()
        composeTestRule.onNodeWithContentDescription("Not archived").assertExists()
        composeTestRule.onNodeWithContentDescription("Select bookmark").assertExists()

        composeTestRule.onNodeWithText("Test Bookmark").performClick()

        assertEquals(0, favoriteToggles.get())
        assertEquals(0, archiveToggles.get())
        assertEquals(1, toggles.get())
        assertEquals(0, navigations.get())
    }

    @Test
    fun `selection mode reflects favorited and archived state`() {
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkGridCard(
                    bookmark = bookmarkListItem(isMarked = true, isArchived = true),
                    onClickCard = {},
                    onClickDelete = {},
                    onClickFavorite = { _, _ -> },
                    onClickArchive = { _, _ -> },
                    isSelectionMode = true,
                    isSelected = false,
                    onToggleSelection = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorited").assertExists()
        composeTestRule.onNodeWithContentDescription("Archived").assertExists()
    }

    @Test
    fun `selected card exposes deselect indicator`() {
        composeTestRule.setContent {
            MaterialTheme {
                BookmarkGridCard(
                    bookmark = bookmarkListItem(),
                    onClickCard = {},
                    onClickDelete = {},
                    onClickFavorite = { _, _ -> },
                    onClickArchive = { _, _ -> },
                    isSelectionMode = true,
                    isSelected = true,
                    onToggleSelection = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Deselect bookmark").assertExists()
    }

    @Test
    fun `long press does not open context menu in selection mode`() {
        val toggles = AtomicInteger(0)
        val navigations = AtomicInteger(0)

        composeTestRule.setContent {
            MaterialTheme {
                BookmarkGridCard(
                    bookmark = bookmarkListItem(),
                    onClickCard = { navigations.incrementAndGet() },
                    onClickDelete = {},
                    onClickFavorite = { _, _ -> },
                    onClickArchive = { _, _ -> },
                    isSelectionMode = true,
                    isSelected = false,
                    onToggleSelection = { toggles.incrementAndGet() }
                )
            }
        }

        composeTestRule.onNodeWithText("Test Bookmark").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Copy link").assertDoesNotExist()
        assertEquals(0, navigations.get())
    }

    private fun bookmarkListItem(
        isMarked: Boolean = false,
        isArchived: Boolean = false
    ) = BookmarkListItem(
        id = "bookmark-1",
        href = "https://example.com/api/bookmarks/bookmark-1",
        url = "https://example.com/bookmark-1",
        title = "Test Bookmark",
        siteName = "Example",
        isMarked = isMarked,
        isArchived = isArchived,
        isRead = false,
        readProgress = 0,
        thumbnailSrc = "",
        iconSrc = "",
        imageSrc = "",
        labels = listOf("work"),
        type = Bookmark.Type.Article,
        readingTime = null,
        created = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        wordCount = null,
        published = null
    )
}
