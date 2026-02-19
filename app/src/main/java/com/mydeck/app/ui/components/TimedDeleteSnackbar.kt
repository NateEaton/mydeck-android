package com.mydeck.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DELETE_SNACKBAR_DURATION_MS = 5000L

@Composable
fun TimedDeleteSnackbar(snackbarData: SnackbarData) {
    val progress = remember { Animatable(1f) }

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

    Box(modifier = Modifier.width(IntrinsicSize.Min)) {
        Snackbar(snackbarData = snackbarData)
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}
