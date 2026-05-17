package com.mydeck.app.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.mydeck.app.domain.model.SwipeAction
import com.mydeck.app.domain.model.SwipeConfig
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import com.mydeck.app.io.rest.NetworkModule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34])
@UninstallModules(NetworkModule::class)
class SwipeableCardContainerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private val wrapperTag = "swipe-wrapper"
    private val contentTag = "swipe-content"

    private fun setSwipeable(
        config: SwipeConfig = SwipeConfig.Default,
        leftCounter: AtomicInteger = AtomicInteger(0),
        rightCounter: AtomicInteger = AtomicInteger(0),
        contentBuilder: @Composable () -> Unit = {
            Box(
                Modifier
                    .testTag(contentTag)
                    .fillMaxSize()
                    .background(Color.LightGray),
            ) { Text("card") }
        },
    ) {
        composeTestRule.setContent {
            Box(
                Modifier
                    .testTag(wrapperTag)
                    .width(300.dp)
                    .height(120.dp),
            ) {
                SwipeableCardContainer(
                    config = config,
                    onCommitLeft = { leftCounter.incrementAndGet() },
                    onCommitRight = { rightCounter.incrementAndGet() },
                    a11yLeftLabel = "left",
                    a11yRightLabel = "right",
                ) { contentBuilder() }
            }
        }
    }

    @Test
    fun tap_propagates_to_content_without_committing() {
        val tapped = AtomicInteger(0)
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(
            leftCounter = left,
            rightCounter = right,
            contentBuilder = {
                Box(
                    Modifier
                        .testTag(contentTag)
                        .fillMaxSize()
                        .clickable { tapped.incrementAndGet() }
                        .background(Color.LightGray),
                ) { Text("card") }
            },
        )

        composeTestRule.onNodeWithTag(contentTag).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, tapped.get())
        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun long_press_propagates_to_content_without_committing() {
        val longPressed = AtomicInteger(0)
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(
            leftCounter = left,
            rightCounter = right,
            contentBuilder = {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .testTag(contentTag)
                        .fillMaxSize()
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {},
                            onLongClick = { longPressed.incrementAndGet() },
                        )
                        .background(Color.LightGray),
                ) { Text("card") }
            },
        )

        composeTestRule.onNodeWithTag(contentTag).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, longPressed.get())
        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun vertical_drag_does_not_commit() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(leftCounter = left, rightCounter = right)

        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun horizontal_swipe_left_past_threshold_fires_left_commit_once_per_gesture() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(leftCounter = left, rightCounter = right)

        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals("first gesture fires once", 1, left.get())
        assertEquals(0, right.get())

        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals("second gesture fires once more, not multiplied", 2, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun horizontal_swipe_right_past_threshold_fires_right_commit_once_per_gesture() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(leftCounter = left, rightCounter = right)

        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        assertEquals(0, left.get())
        assertEquals(1, right.get())
    }

    @Test
    fun horizontal_drag_below_threshold_does_not_commit() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(leftCounter = left, rightCounter = right)

        // Move from the right side of the card leftward by ~35% of the width — well
        // below the 50% commit threshold; verifies the snap-back path.
        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput {
            swipe(
                start = Offset(width * 0.85f, height / 2f),
                end = Offset(width * 0.50f, height / 2f),
                durationMillis = 200,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun disabled_config_passes_through_without_committing() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        val tapped = AtomicInteger(0)
        setSwipeable(
            config = SwipeConfig.Default.copy(enabled = false),
            leftCounter = left,
            rightCounter = right,
            contentBuilder = {
                Box(
                    Modifier
                        .testTag(contentTag)
                        .fillMaxSize()
                        .clickable { tapped.incrementAndGet() }
                        .background(Color.LightGray),
                ) { Text("card") }
            },
        )

        // Tap must still flow to the underlying clickable.
        composeTestRule.onNodeWithTag(contentTag).performClick()
        composeTestRule.waitForIdle()
        val tappedAfterClick = tapped.get()
        assertEquals("inner clickable still receives taps", 1, tappedAfterClick)

        // Swiping does not fire either commit; the gesture passes through to
        // whatever the content does with it (possibly nothing, possibly more
        // taps — that's the content's business, not ours).
        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun both_directions_none_passes_through_without_committing() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(
            config = SwipeConfig(
                enabled = true,
                leftAction = SwipeAction.NONE,
                rightAction = SwipeAction.NONE,
            ),
            leftCounter = left,
            rightCounter = right,
        )

        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertEquals(0, left.get())
        assertEquals(0, right.get())
    }

    @Test
    fun one_direction_none_is_inert_in_that_direction_only() {
        val left = AtomicInteger(0)
        val right = AtomicInteger(0)
        setSwipeable(
            config = SwipeConfig(
                enabled = true,
                leftAction = SwipeAction.NONE,
                rightAction = SwipeAction.ARCHIVE,
            ),
            leftCounter = left,
            rightCounter = right,
        )

        // Right (mapped) fires.
        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        assertEquals(1, right.get())
        assertEquals(0, left.get())

        // Left (NONE) does not.
        composeTestRule.onNodeWithTag(wrapperTag).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(0, left.get())
        assertEquals(1, right.get())
    }
}
