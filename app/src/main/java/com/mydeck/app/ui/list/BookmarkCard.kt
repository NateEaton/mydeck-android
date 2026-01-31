package com.mydeck.app.ui.list

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.datetime.Clock
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.ui.components.ErrorPlaceholderImage

@Composable
fun BookmarkCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClickCard(bookmark.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Image with checkmark overlay for read bookmarks
            Box {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(bookmark.imageSrc)
                        .crossfade(true).build(),
                    contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                    contentScale = ContentScale.FillWidth,
                    error = {
                        ErrorPlaceholderImage(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            imageContentDescription = stringResource(R.string.common_bookmark_image_content_description)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                // Show progress indicator based on read progress
                if (bookmark.readProgress > 0) {
                    if (bookmark.readProgress == 100) {
                        // Show checkmark with circle for completed
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.action_mark_read),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(24.dp)
                        )
                    } else {
                        // Show circular progress indicator that grows clockwise
                        CircularProgressIndicator(
                            progress = bookmark.readProgress,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(24.dp)
                        )
                    }
                }
            }

            // Title, Date, and Labels
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis

                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClickOpenUrl(bookmark.url) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(bookmark.iconSrc)
                            .crossfade(true).build(),
                        contentDescription = "site icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = bookmark.siteName, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.action_open_in_browser),
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp)
                    )

                }

                // Labels Row
                if (bookmark.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_label_24px),
                            contentDescription = "labels"
                        )
                        Spacer(Modifier.width(8.dp))
                        val labels = bookmark.labels.fold("") { acc, label ->
                            if (acc.isNotEmpty()) {
                                "$acc, $label"
                            } else {
                                label
                            }
                        }
                        Text(
                            text = labels,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Icons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.Start) {
                        // Favorite Button
                        IconButton(
                            onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                            modifier = Modifier
                                .width(48.dp)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (bookmark.isMarked) Icons.Filled.Grade else Icons.Outlined.Grade,
                                contentDescription = stringResource(R.string.action_favorite)
                            )
                        }

                        // Archive Button
                        IconButton(
                            onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) },
                            modifier = Modifier
                                .width(48.dp)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                contentDescription = stringResource(R.string.action_archive)
                            )
                        }
                    }

                    // Delete Button (right side)
                    IconButton(
                        onClick = { onClickDelete(bookmark.id) },
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun BookmarkMagazineView(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClickCard(bookmark.id) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail
            Box {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(bookmark.thumbnailSrc)
                        .crossfade(true).build(),
                    contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                    contentScale = ContentScale.Crop,
                    error = {
                        ErrorPlaceholderImage(
                            modifier = Modifier
                                .width(100.dp)
                                .height(80.dp),
                            imageContentDescription = stringResource(R.string.common_bookmark_image_content_description)
                        )
                    },
                    modifier = Modifier
                        .width(100.dp)
                        .height(80.dp)
                )
                // Read progress indicator
                if (bookmark.readProgress > 0 && bookmark.readProgress < 100) {
                    Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        val progressColor = Color.White.copy(alpha = 0.7f)
                        val strokeWidth = 2.dp.toPx()
                        val diameter = size.minDimension
                        val sweepAngle = (bookmark.readProgress / 100f) * 360f
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            size = Size(diameter - strokeWidth, diameter - strokeWidth),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // Site and read time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(bookmark.iconSrc)
                            .crossfade(true).build(),
                        contentDescription = "site icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = bookmark.siteName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    bookmark.readingTime?.let {
                        Text(
                            text = "$it min",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Title
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.Start) {
                        IconButton(
                            onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (bookmark.isMarked) Icons.Filled.Grade else Icons.Outlined.Grade,
                                contentDescription = stringResource(R.string.action_favorite),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                contentDescription = stringResource(R.string.action_archive),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onClickOpenUrl(bookmark.url) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_in_browser),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onClickDelete(bookmark.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun BookmarkListItemView(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenUrl: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickCard(bookmark.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favicon
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(bookmark.iconSrc)
                .crossfade(true).build(),
            contentDescription = "site icon",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(24.dp)
                .height(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        // Title and metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bookmark.siteName,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelSmall
                )
                bookmark.readingTime?.let {
                    Text(
                        text = "$it min",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Action buttons (compact)
        Row(
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (bookmark.isMarked) Icons.Filled.Grade else Icons.Outlined.Grade,
                    contentDescription = stringResource(R.string.action_favorite),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { onClickDelete(bookmark.id) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun CircularProgressIndicator(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val progressColor = Color.White.copy(alpha = 0.7f)

    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val diameter = size.minDimension
        val radius = (diameter - strokeWidth) / 2f
        val topLeft = Offset(
            x = (size.width - diameter) / 2f + strokeWidth / 2f,
            y = (size.height - diameter) / 2f + strokeWidth / 2f
        )
        val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)

        // Calculate sweep angle based on progress (0-100)
        // Start at -90 degrees (12 o'clock position) and sweep clockwise
        val sweepAngle = (progress / 100f) * 360f

        // Draw the circular arc
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkCardPreview() {
    val previewHandler = AsyncImagePreviewHandler {
        ColorImage(Color.Red.toArgb())
    }
    val sampleBookmark = BookmarkListItem(
        id = "1",
        url = "https://example.com",
        title = "Sample Bookmark",
        siteName = "Example",
        type = Bookmark.Type.Article,
        isMarked = false,
        isArchived = false,
        labels = listOf(
            "one",
            "two",
            "three",
            "fourhundretandtwentyone",
            "threethousendtwohundretandfive"
        ),
        isRead = true,
        readProgress = 100,
        iconSrc = "https://picsum.photos/seed/picsum/640/480",
        imageSrc = "https://picsum.photos/seed/picsum/640/480",
        thumbnailSrc = "https://picsum.photos/seed/picsum/640/480",
        readingTime = 8,
        created = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
        wordCount = 2000
    )
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
        BookmarkCard(
            bookmark = sampleBookmark,
            onClickCard = {},
            onClickDelete = {},
            onClickFavorite = { _, _ -> },
            onClickArchive = { _, _ -> },
            onClickOpenUrl = {},
            onClickShareBookmark = {_ -> }
        )
    }
}
