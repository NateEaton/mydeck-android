package com.mydeck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ErrorPlaceholderImage(modifier: Modifier, imageContentDescription: String) {
    // Use a subtle dark gray that blends with the UI
    val placeholderBackgroundColor = Color(0xFF2C2C2C)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(placeholderBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = imageContentDescription,
            modifier = Modifier.size(48.dp),
            tint = Color(0xFF505050) // Slightly lighter gray for the icon
        )
    }
}