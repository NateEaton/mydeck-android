package com.mydeck.app.ui.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.theme.ReaderThemePalette

object ReaderActionFooterTestTags {
    const val FOOTER = "reader_action_footer"
    const val FAVORITE = "reader_action_footer_favorite"
    const val ARCHIVE = "reader_action_footer_archive"
}

internal val ReaderActionFooterButtonHeight = 48.dp
internal val ReaderActionFooterButtonSpacing = 8.dp
internal val ReaderActionFooterInlineHeight = ReaderActionFooterButtonHeight
internal val ReaderActionFooterStackedHeight =
    ReaderActionFooterButtonHeight + ReaderActionFooterButtonSpacing + ReaderActionFooterButtonHeight

@Composable
fun ReaderActionFooter(
    isFavorite: Boolean,
    isArchived: Boolean,
    isWideLayout: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    palette: ReaderThemePalette,
    modifier: Modifier = Modifier
) {
    val surfaceColor = remember(palette.bodyBackgroundColor) {
        palette.bodyBackgroundColor.toComposeColor()
    }
    val contentColor = remember(palette.accentColor) {
        palette.accentColor.toComposeColor()
    }
    val borderColor = remember(palette.buttonBorderColor) {
        palette.buttonBorderColor.toComposeColor()
    }
    val favoriteDescription = stringResource(
        if (isFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites
    )
    val archiveDescription = stringResource(
        if (isArchived) R.string.action_remove_from_archive else R.string.action_add_to_archive
    )

    if (isWideLayout) {
        Row(
            modifier = modifier.testTag(ReaderActionFooterTestTags.FOOTER),
            horizontalArrangement = Arrangement.spacedBy(
                ReaderActionFooterButtonSpacing,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderActionPill(
                modifier = Modifier.testTag(ReaderActionFooterTestTags.FAVORITE),
                label = favoriteDescription,
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onToggleFavorite,
                surfaceColor = surfaceColor,
                contentColor = contentColor,
                borderColor = borderColor
            )
            ReaderActionPill(
                modifier = Modifier.testTag(ReaderActionFooterTestTags.ARCHIVE),
                label = archiveDescription,
                icon = {
                    Icon(
                        imageVector = if (isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onToggleArchive,
                surfaceColor = surfaceColor,
                contentColor = contentColor,
                borderColor = borderColor
            )
        }
    } else {
        Column(
            modifier = modifier
                .testTag(ReaderActionFooterTestTags.FOOTER)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ReaderActionFooterButtonSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReaderActionPill(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .testTag(ReaderActionFooterTestTags.FAVORITE),
                label = favoriteDescription,
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onToggleFavorite,
                surfaceColor = surfaceColor,
                contentColor = contentColor,
                borderColor = borderColor
            )
            ReaderActionPill(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .testTag(ReaderActionFooterTestTags.ARCHIVE),
                label = archiveDescription,
                icon = {
                    Icon(
                        imageVector = if (isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onToggleArchive,
                surfaceColor = surfaceColor,
                contentColor = contentColor,
                borderColor = borderColor
            )
        }
    }
}

@Composable
private fun ReaderActionPill(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    surfaceColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 176.dp, minHeight = ReaderActionFooterButtonHeight),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(ReaderActionFooterButtonHeight)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun String.toComposeColor(): Color =
    Color(android.graphics.Color.parseColor(this))
