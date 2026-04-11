package com.mydeck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage

/**
 * A centered dialog for long-press context menus.
 *
 * Displays a header with a small image or icon, a title, and a subtitle,
 * followed by a divider and the provided action items.
 *
 * Provide either [headerImageUrl] (favicon/thumbnail loaded via Coil) or [headerIcon]
 * (a Material ImageVector rendered in the accent container colour). If both are provided,
 * [headerIcon] takes precedence. If neither is provided the slot is empty.
 *
 * @param headerImageUrl URL of the favicon or thumbnail to show in the header.
 * @param headerIcon A Material icon to show in the header instead of a favicon.
 * @param title Primary text in the header (bookmark title or link text).
 * @param subtitle Secondary text in the header (URL or description).
 * @param onDismiss Called when the dialog should be dismissed.
 * @param content The action rows to display below the divider.
 */
@Composable
fun LongPressContextMenuDialog(
    headerImageUrl: String? = null,
    headerIcon: ImageVector? = null,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        var isExpanded by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column {
                // Header: image/icon + title + subtitle
                Row(
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        headerIcon != null -> Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        else -> AsyncImage(
                            model = headerImageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = if (isExpanded) 10 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) 10 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider()
                content()
            }
        }
    }
}

/**
 * A single action row inside a [LongPressContextMenuDialog].
 */
@Composable
fun LongPressContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
