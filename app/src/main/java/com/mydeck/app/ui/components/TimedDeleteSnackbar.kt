package com.mydeck.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DELETE_SNACKBAR_DURATION_MS = 5000L

@Composable
fun TimedDeleteSnackbar(snackbarData: SnackbarData) {
    val progress = remember { Animatable(1f) }
    val barColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(snackbarData) {
        launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = DELETE_SNACKBAR_DURATION_MS.toInt(), easing = LinearEasing)
            )
        }
        delay(DELETE_SNACKBAR_DURATION_MS)
        snackbarData.dismiss()
    }

    Snackbar(
        snackbarData = snackbarData,
        modifier = Modifier.drawWithContent {
            drawContent()
            val barHeight = 3.dp.toPx()
            drawRect(
                color = barColor,
                topLeft = Offset(0f, size.height - barHeight),
                size = Size(size.width * progress.value, barHeight)
            )
        }
    )
}
