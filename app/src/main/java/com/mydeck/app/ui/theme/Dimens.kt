package com.mydeck.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalReaderMaxWidth = compositionLocalOf { Dp.Unspecified }

object Dimens {
    // Spacing
    val PaddingSmall = 4.dp
    val PaddingMedium = 8.dp
    val PaddingNormal = 16.dp
    val PaddingLarge = 24.dp

    // Grid spacing for tablet layouts
    val GridSpacing = 8.dp

    // Icon sizes
    val IconSizeSmall = 18.dp
    val IconSizeNormal = 24.dp

    // Corner radius
    val CornerRadiusCard = 12.dp
    val CornerRadiusSheet = 28.dp

    // Reader max widths
    val ReaderMaxWidthMedium = 720.dp
    val ReaderMaxWidthExpanded = 840.dp
}
