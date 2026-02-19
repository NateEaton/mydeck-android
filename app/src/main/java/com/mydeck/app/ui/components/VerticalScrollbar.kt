package com.mydeck.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    padding: Dp = 4.dp
) {
    val isScrollable = scrollState.maxValue > 0
    val progress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    } else {
        0f
    }

    ScrollbarTrack(
        modifier = modifier,
        isScrollInProgress = scrollState.isScrollInProgress,
        isScrollable = isScrollable,
        progress = progress,
        width = width,
        color = color,
        padding = padding
    )
}

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    padding: Dp = 4.dp
) {
    val isScrollable by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
        }
    }

    val progress by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= visibleItems.size || visibleItems.isEmpty()) {
                0f
            } else {
                val firstVisible = lazyListState.firstVisibleItemIndex
                val maxIndex = (totalItems - visibleItems.size).coerceAtLeast(1)
                (firstVisible.toFloat() / maxIndex).coerceIn(0f, 1f)
            }
        }
    }

    ScrollbarTrack(
        modifier = modifier,
        isScrollInProgress = lazyListState.isScrollInProgress,
        isScrollable = isScrollable,
        progress = progress,
        width = width,
        color = color,
        padding = padding
    )
}

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    padding: Dp = 4.dp
) {
    val isScrollable by remember {
        derivedStateOf {
            val layoutInfo = lazyGridState.layoutInfo
            layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
        }
    }

    val progress by remember {
        derivedStateOf {
            val layoutInfo = lazyGridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= visibleItems.size || visibleItems.isEmpty()) {
                0f
            } else {
                val firstVisible = lazyGridState.firstVisibleItemIndex
                val maxIndex = (totalItems - visibleItems.size).coerceAtLeast(1)
                (firstVisible.toFloat() / maxIndex).coerceIn(0f, 1f)
            }
        }
    }

    ScrollbarTrack(
        modifier = modifier,
        isScrollInProgress = lazyGridState.isScrollInProgress,
        isScrollable = isScrollable,
        progress = progress,
        width = width,
        color = color,
        padding = padding
    )
}

@Composable
private fun ScrollbarTrack(
    modifier: Modifier = Modifier,
    isScrollInProgress: Boolean,
    isScrollable: Boolean,
    progress: Float,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    padding: Dp = 4.dp
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollInProgress, progress) {
        if (isScrollInProgress) {
            isVisible = true
        } else {
            delay(1000)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    if (isScrollable) {
        val alignBias = (progress * 2) - 1f // Map [0, 1] to [-1, 1]

        Box(
            modifier = modifier
                .alpha(alpha)
                .fillMaxHeight()
                .padding(end = padding)
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(0f, alignBias))
                    .width(width)
                    .fillMaxHeight(0.1f)
                    .clip(RoundedCornerShape(width))
                    .background(color)
            )
        }
    }
}
