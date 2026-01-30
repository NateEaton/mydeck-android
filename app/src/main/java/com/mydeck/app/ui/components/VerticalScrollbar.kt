package com.mydeck.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var isVisible by remember { mutableStateOf(false) }
    
    // Auto-hide logic
    LaunchedEffect(scrollState.isScrollInProgress, scrollState.value) {
        if (scrollState.isScrollInProgress) {
            isVisible = true
        } else {
            // Wait for 1 second after scroll stops before hiding
            delay(1000)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    if (scrollState.maxValue > 0) {
        // Calculate scrollbar size and position
        // Viewport size calculation is approximate as we don't have direct access to viewport height here easily 
        // without BoxWithConstraints or LayoutCoordinates. 
        // However, a common scrollbar pattern is to represent the proportion of "visible" area.
        // Since we are overlaying this on the content, we can use a simpler approach:
        // Just show a knob moving proportionally?
        // Or a bar that shrinks as content grows?
        // Native android scrollbars changes size.
        // But for "Reading Progress Bar", often a fixed "thumb" or just simple proportional thumb is enough.
        
        // Let's implement a proportional thumb. 
        // Position relative to container height.
        // We assume 'modifier' has fillMaxHeight() on the container Box.
        
        Box(
            modifier = modifier
                .alpha(alpha)
                .fillMaxHeight()
                .padding(end = padding)
        ) {
            // We need to know the height of this track (the viewport height).
            // But in a simple Box overlay, we can use BiasAlignment or just percentage offset.
             
            // Fraction of content scrolled:
            val scrollValue = scrollState.value.toFloat()
            val maxScroll = scrollState.maxValue.toFloat()
            // Total content height = viewport + maxScroll
            // Ideally we need viewport height. 
            // scrollState doesn't expose viewport height directly. 
            // BUT: maxScroll = contentHeight - viewportHeight.
            // So contentHeight = maxScroll + viewportHeight.
            // Thumb height / Viewport Height = Viewport Height / Content Height
            // This requires Viewport Height.
            
            // SIMPLIFICATION:
            // Since accessing viewport height needs `BoxWithConstraints`, let's just 
            // place the thumb purely based on scroll percentage [0, 1] mapped to alignment [-1, 1].
            // This won't change thumb size but will indicate position correctly.
            // OR we use a fixed small thumb.
            
            // Let's use a fixed-width bar that aligns vertically based on progress.
            // Using `BiasAlignment` is easiest for positioning.
            // -1f is top, 1f is bottom.
            
            val progress = if (maxScroll > 0) scrollValue / maxScroll else 0f
            val alignBias = (progress * 2) - 1f // Map [0, 1] to [-1, 1]
            
            Box(
                modifier = Modifier
                    .align(BiasAlignment(0f, alignBias))
                    .width(width)
                    // Static height for the thumb for now, or we could try to make it proportional if requested later.
                    // A fixed small thumb (e.g. 48dp) is often cleaner for very long content than a 1px proportional one.
                    .fillMaxHeight(0.1f) // 10% of screen height as thumb? Or fixed dp?
                    // Let's stick to fixed dp for consistent visibility.
                    // But 10% is nicer for "how much is left".
                    // Let's use a dynamic height clamped.
                    .clip(RoundedCornerShape(width))
                    .background(color)
            )
        }
    }
}
