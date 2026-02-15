package com.mydeck.app.ui.list.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun BookmarkGridCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClickCard(bookmark.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (bookmark.imageSrc != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(bookmark.imageSrc)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Read progress indicator
                if (bookmark.readProgress > 0 && bookmark.readProgress < 100) {
                     CircularProgressIndicator(
                        progress = { bookmark.readProgress / 100f },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                } else if (bookmark.readProgress == 100) {
                     Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.action_is_read),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(2.dp)
                            .size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.siteName ?: bookmark.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateStr = bookmark.created.date.toString()
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    BookmarkCardActions(
                        bookmark = bookmark,
                        onClickDelete = onClickDelete,
                        onClickArchive = onClickArchive,
                        onClickFavorite = onClickFavorite,
                        onClickShareBookmark = onClickShareBookmark,
                        onClickOpenUrl = onClickOpenUrl,
                        onClickOpenInBrowser = onClickOpenInBrowser
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkCompactCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClickCard(bookmark.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bookmark.imageSrc != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bookmark.imageSrc)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = bookmark.siteName ?: bookmark.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            BookmarkCardActions(
                bookmark = bookmark,
                onClickDelete = onClickDelete,
                onClickArchive = onClickArchive,
                onClickFavorite = onClickFavorite,
                onClickShareBookmark = onClickShareBookmark,
                onClickOpenUrl = onClickOpenUrl,
                onClickOpenInBrowser = onClickOpenInBrowser
            )
        }
    }
}

@Composable
fun BookmarkMosaicCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit
) {
    // Mosaic can be similar to Grid but with different aspect ratios or sizing logic handled by the specific StaggeredGrid layout (if implemented).
    // For now, reusing Grid layout structure but allowing it to be used in a StaggeredGrid.
    // Simplifying to match GridCard for this extraction, assuming LayoutMode.MOSAIC handles the container.
    BookmarkGridCard(
         bookmark = bookmark,
         onClickCard = onClickCard,
         onClickDelete = onClickDelete,
         onClickArchive = onClickArchive,
         onClickFavorite = onClickFavorite,
         onClickShareBookmark = onClickShareBookmark,
         onClickLabel = onClickLabel,
         onClickOpenUrl = onClickOpenUrl,
         onClickOpenInBrowser = onClickOpenInBrowser
    )
}

@Composable
private fun BookmarkCardActions(
    bookmark: BookmarkListItem,
    onClickDelete: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickOpenInBrowser: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (bookmark.isArchived) stringResource(R.string.action_archive) else stringResource(R.string.action_archive)) },
                leadingIcon = {
                     Icon(
                         if (bookmark.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                         contentDescription = null
                     )
                },
                onClick = {
                    expanded = false
                    onClickArchive(bookmark.id, !bookmark.isArchived)
                }
            )
            DropdownMenuItem(
                text = { Text(if (bookmark.isMarked) stringResource(R.string.action_favorite) else stringResource(R.string.action_favorite)) },
                leadingIcon = {
                    Icon(
                        if (bookmark.isMarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onClickFavorite(bookmark.id, !bookmark.isMarked)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                 leadingIcon = {
                    Icon(Icons.Default.Share, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onClickShareBookmark(bookmark.url)
                }
            )
             DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_in_browser)) },
                 leadingIcon = {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onClickOpenInBrowser(bookmark.url)
                }
            )
             DropdownMenuItem(
                text = { Text(stringResource(R.string.action_view_original)) },
                 leadingIcon = {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onClickOpenUrl(bookmark.url)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onClickDelete(bookmark.id)
                }
            )
        }
    }
}
