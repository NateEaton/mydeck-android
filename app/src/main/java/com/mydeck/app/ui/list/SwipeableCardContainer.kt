package com.mydeck.app.ui.list

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import android.os.Build
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mydeck.app.domain.model.EffectiveAppearance
import com.mydeck.app.domain.model.SwipeAction
import com.mydeck.app.domain.model.SwipeConfig
import com.mydeck.app.ui.theme.LocalEffectiveAppearance
import com.mydeck.app.ui.theme.PaperColorScheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SwipeableCardContainer(
    config: SwipeConfig,
    onCommitLeft: () -> Unit,
    onCommitRight: () -> Unit,
    a11yLeftLabel: String,
    a11yRightLabel: String,
    leftAction: SwipeAction = config.leftAction,
    rightAction: SwipeAction = config.rightAction,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Edge gating uses enabled veto (see pointerInput below); multi-column callers pass enabled=false.
    // No early-return for the disabled case: if config flips disabled mid-drag (e.g. delete pending),
    // we keep the state machinery alive so the snap-back animates instead of jumping to 0.
    val effectivelyEnabled = config.enabled &&
        !(leftAction == SwipeAction.NONE && rightAction == SwipeAction.NONE)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val commitPx = widthPx * 0.5f

        var offsetX by remember { mutableStateOf(0f) }
        // Holds the active snap-back job so a new drag or commit can cancel it.
        val animHolder = remember { object { var job: Job? = null } }
        // Tracks whether we've fired the threshold-crossed haptic for the current excursion.
        // Resets when offset returns below threshold so re-crossing re-fires haptic.
        var hapticTriggered by remember { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        // Long-lived LaunchedEffect must read latest callbacks/actions, not the
        // values captured at first launch — otherwise a list-slot recomposition
        // with a new bookmark fires the prior bookmark's handler.
        val currentLeftAction by rememberUpdatedState(leftAction)
        val currentRightAction by rememberUpdatedState(rightAction)
        val currentOnCommitLeft by rememberUpdatedState(onCommitLeft)
        val currentOnCommitRight by rememberUpdatedState(onCommitRight)

        // If the container flips effectively-disabled while offset is non-zero (e.g. delete
        // commit stages pending-delete on the same recomposition that disables swipe), let
        // any in-flight onDragStopped animation finish; only start one here if nothing is
        // already animating, so the card glides home instead of jumping.
        LaunchedEffect(effectivelyEnabled) {
            if (!effectivelyEnabled && offsetX != 0f && animHolder.job?.isActive != true) {
                animHolder.job = scope.launch {
                    animate(offsetX, 0f) { value, _ -> offsetX = value }
                }
            }
        }

        // Threshold-crossing haptic only. Commit dispatch lives in onDragStopped
        // so the user can drag past, back off, and release without firing.
        LaunchedEffect(commitPx) {
            snapshotFlow { offsetX }.collect { off ->
                val past = abs(off) >= commitPx
                if (past && !hapticTriggered) {
                    val goingLeft = off < 0f
                    val action = if (goingLeft) currentLeftAction else currentRightAction
                    if (effectivelyEnabled && action != SwipeAction.NONE) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    hapticTriggered = true
                } else if (!past && hapticTriggered) {
                    hapticTriggered = false
                }
            }
        }

        val layoutDir = LocalLayoutDirection.current
        val startInsetPx = with(density) {
            WindowInsets.systemGestures.asPaddingValues(this)
                .calculateStartPadding(layoutDir).toPx()
        }

        // Edge gating: veto draggable via `enabled` when down lands inside the
        // start system-gesture inset. Cleaner than consuming events — parent
        // scroll still receives vertical-drag input from the same pointer.
        var edgeVeto by remember { mutableStateOf(false) }

        // Intent fence: once a touch has demonstrated meaningful vertical-dominant
        // motion, lock out swipe for the rest of that pointer's life. Prevents
        // accidental swipe firing on a "scroll then sideways" single-touch gesture.
        // Detected before the draggable's own slop check by using a smaller threshold,
        // so we can veto via `enabled` before the draggable claims the pointer.
        var verticalIntentVeto by remember { mutableStateOf(false) }

        val customActionsList = buildList {
            if (rightAction != SwipeAction.NONE) {
                add(CustomAccessibilityAction(a11yRightLabel) { onCommitRight(); true })
            }
            if (leftAction != SwipeAction.NONE) {
                add(CustomAccessibilityAction(a11yLeftLabel) { onCommitLeft(); true })
            }
        }

        val colorScheme = MaterialTheme.colorScheme
        val appearance = LocalEffectiveAppearance.current
        // Archive uses the system's *light* dynamic primary across all themes so
        // the shade stays consistent regardless of light/dark mode. Sepia bypasses
        // dynamic color elsewhere and uses its own palette for Archive.
        val context = LocalContext.current
        val lightDynamicScheme = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                PaperColorScheme
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize()) {
                val absOffset = abs(offsetX)
                val activeAction = when {
                    offsetX > 0f && rightAction != SwipeAction.NONE -> rightAction
                    offsetX < 0f && leftAction != SwipeAction.NONE -> leftAction
                    else -> SwipeAction.NONE
                }

                if (activeAction != SwipeAction.NONE && absOffset > 0f) {
                    val progress = (absOffset / commitPx).coerceIn(0f, 1f)
                    val scale = lerp(0.8f, 1.0f, progress)
                    val surfaceWidthDp = with(density) { absOffset.toDp() }
                    val surfaceAlignment =
                        if (offsetX > 0f) Alignment.CenterStart else Alignment.CenterEnd

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(surfaceWidthDp)
                            .fillMaxHeight()
                            .align(surfaceAlignment)
                            .background(
                                color = backgroundColorFor(activeAction, colorScheme, appearance, lightDynamicScheme),
                                shape = MaterialTheme.shapes.medium,
                            ),
                    ) {
                        // Icon clips as surface narrows at low offsets; alpha ramp masks clipping below notice threshold.
                        Icon(
                            imageVector = iconFor(activeAction),
                            contentDescription = null,
                            tint = iconTintFor(activeAction, colorScheme, appearance, lightDynamicScheme),
                            modifier = Modifier.graphicsLayer {
                                this.alpha = progress
                                this.scaleX = scale
                                this.scaleY = scale
                            },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(startInsetPx, effectivelyEnabled) {
                        if (!effectivelyEnabled) return@pointerInput
                        // Intent fence: decide which axis the gesture is "about" at the
                        // moment the first axis crosses full touch slop, then lock that
                        // decision for the rest of the pointer's life. Locking once (rather
                        // than re-checking each event) means a swipe with vertical wobble
                        // cannot trip the veto mid-drag — which would otherwise cause
                        // draggable's enabled=false to call onDragStopped abnormally and
                        // sometimes commit an action without the usual snackbar handshake.
                        val intentThresholdPx = viewConfiguration.touchSlop.toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            edgeVeto = down.position.x < startInsetPx
                            verticalIntentVeto = false
                            var intentLocked = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                if (!intentLocked) {
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    if (dx > intentThresholdPx || dy > intentThresholdPx) {
                                        intentLocked = true
                                        // 45° or steeper → veto swipe for this pointer.
                                        // The equality boundary is critical: a perfectly
                                        // diagonal gesture would otherwise leave both
                                        // detectors waiting for axis dominance and produce
                                        // no motion at all. Biasing to scroll here also
                                        // matches user intent: as much vertical as
                                        // horizontal is too much vertical for a swipe.
                                        if (dy >= dx) {
                                            verticalIntentVeto = true
                                        }
                                    }
                                }
                            }
                            edgeVeto = false
                            verticalIntentVeto = false
                        }
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            animHolder.job?.cancel()
                            offsetX += delta
                        },
                        orientation = Orientation.Horizontal,
                        enabled = effectivelyEnabled && !edgeVeto && !verticalIntentVeto,
                        onDragStopped = { velocity ->
                            val off = offsetX
                            val goingLeft = off < 0f
                            val action = if (goingLeft) currentLeftAction else currentRightAction
                            val shouldCommit =
                                action != SwipeAction.NONE && abs(off) >= commitPx
                            animHolder.job?.cancel()
                            animHolder.job = scope.launch {
                                if (shouldCommit) {
                                    if (goingLeft) currentOnCommitLeft() else currentOnCommitRight()
                                }
                                animate(
                                    initialValue = offsetX,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                ) { value, _ -> offsetX = value }
                            }
                        },
                    )
                    .semantics {
                        if (effectivelyEnabled && customActionsList.isNotEmpty()) {
                            customActions = customActionsList
                        }
                    },
            ) {
                content()
            }
        }
    }
}

private fun backgroundColorFor(
    action: SwipeAction,
    cs: ColorScheme,
    appearance: EffectiveAppearance,
    lightDynamic: ColorScheme,
): Color = when (action) {
    SwipeAction.ARCHIVE -> when (appearance) {
        // Paper already runs on the light dynamic palette, but we resolve through
        // lightDynamic here too so the source of truth is identical to Dark/Black.
        EffectiveAppearance.PAPER -> lightDynamic.primary
        // Sepia bypasses dynamic color; keep the curated sepia surface.
        EffectiveAppearance.SEPIA -> cs.secondaryContainer
        // Dark/Black: use the *light* dynamic primary so the shade matches Paper
        // rather than the auto-lightened dark-scheme primary.
        EffectiveAppearance.DARK, EffectiveAppearance.BLACK -> lightDynamic.primary
    }
    // Paper/Sepia: soften the plain error toward errorContainer (20%) so it doesn't
    // read as alarmingly dark on a light surface. Dark/Black use errorContainer.
    SwipeAction.DELETE -> if (!appearance.isDark) {
        colorLerp(cs.error, cs.errorContainer, 0.2f)
    } else {
        cs.errorContainer
    }
    SwipeAction.FAVORITE -> when (appearance) {
        EffectiveAppearance.PAPER -> cs.tertiary
        EffectiveAppearance.SEPIA,
        EffectiveAppearance.DARK,
        EffectiveAppearance.BLACK -> cs.tertiaryContainer
    }
    SwipeAction.NONE -> Color.Transparent
}

private fun iconTintFor(
    action: SwipeAction,
    cs: ColorScheme,
    appearance: EffectiveAppearance,
    lightDynamic: ColorScheme,
): Color = when (action) {
    SwipeAction.ARCHIVE -> when (appearance) {
        EffectiveAppearance.PAPER -> lightDynamic.onPrimary
        EffectiveAppearance.SEPIA -> cs.onSecondaryContainer
        EffectiveAppearance.DARK, EffectiveAppearance.BLACK -> lightDynamic.onPrimary
    }
    SwipeAction.DELETE -> if (!appearance.isDark) cs.onError else cs.onErrorContainer
    SwipeAction.FAVORITE -> when (appearance) {
        EffectiveAppearance.PAPER -> cs.onTertiary
        EffectiveAppearance.SEPIA,
        EffectiveAppearance.DARK,
        EffectiveAppearance.BLACK -> cs.onTertiaryContainer
    }
    SwipeAction.NONE -> Color.Unspecified
}

private fun iconFor(action: SwipeAction): ImageVector =
    when (action) {
        SwipeAction.ARCHIVE -> Icons.Filled.Archive
        SwipeAction.DELETE -> Icons.Filled.Delete
        SwipeAction.FAVORITE -> Icons.Filled.Favorite
        SwipeAction.NONE -> Icons.Filled.Archive
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SwipeableCardContainerPreview() {
    SwipeableCardContainer(
        config = SwipeConfig.Default,
        onCommitLeft = {},
        onCommitRight = {},
        a11yLeftLabel = "Delete",
        a11yRightLabel = "Archive",
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Sample bookmark card",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
