package com.mydeck.app.ui.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.theme.ReaderThemePalette

object ReaderActionFooterTestTags {
    const val FOOTER = "reader_action_footer"
    const val FAVORITE = "reader_action_footer_favorite"
    const val ARCHIVE = "reader_action_footer_archive"
}

internal val ReaderActionFooterHeight = 56.dp

@Composable
fun ReaderActionFooter(
    isFavorite: Boolean,
    isArchived: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    palette: ReaderThemePalette,
    modifier: Modifier = Modifier
) {
    val surfaceColor = remember(palette.accentContainerColor) {
        palette.accentContainerColor.toComposeColor()
    }
    val contentColor = remember(palette.onAccentContainerColor) {
        palette.onAccentContainerColor.toComposeColor()
    }
    val favoriteDescription = stringResource(
        if (isFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites
    )
    val archiveDescription = stringResource(
        if (isArchived) R.string.action_remove_from_archive else R.string.action_add_to_archive
    )

    Surface(
        modifier = modifier.testTag(ReaderActionFooterTestTags.FOOTER),
        shape = RoundedCornerShape(28.dp),
        color = surfaceColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .height(ReaderActionFooterHeight)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                modifier = Modifier.testTag(ReaderActionFooterTestTags.FAVORITE),
                onClick = onToggleFavorite
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = favoriteDescription
                )
            }
            IconButton(
                modifier = Modifier.testTag(ReaderActionFooterTestTags.ARCHIVE),
                onClick = onToggleArchive
            ) {
                Icon(
                    imageVector = if (isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                    contentDescription = archiveDescription
                )
            }
        }
    }
}

private fun String.toComposeColor(): Color =
    Color(android.graphics.Color.parseColor(this))
