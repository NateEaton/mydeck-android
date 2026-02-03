package com.mydeck.app.ui.list

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.luminance
import coil3.ColorImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.ui.components.ErrorPlaceholderImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(200.dp)
            .clickable { onClickCard(bookmark.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Full height thumbnail as background
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(bookmark.imageSrc)
                    .crossfade(true).build(),
                contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                contentScale = ContentScale.Crop,
                error = {
                    ErrorPlaceholderImage(
                        modifier = Modifier.fillMaxWidth().height(270.dp),
                        imageContentDescription = stringResource(R.string.common_bookmark_image_content_description)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Type icon overlay
            if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (bookmark.type is Bookmark.Type.Video) Icons.Filled.Movie else Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Gradient overlay on bottom third for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Show progress indicator based on read progress
            if (bookmark.readProgress > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(28.dp)
                        .background(
                            color = Color.Gray.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bookmark.readProgress == 100) {
                        // Show bold checkmark for completed
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.action_mark_read),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        // Show circular progress indicator that grows clockwise
                        CircularProgressIndicator(
                            progress = bookmark.readProgress,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Content overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Title
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )

                // Labels Row with clickable chips
                if (bookmark.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_label_24px),
                            contentDescription = "labels",
                            tint = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            bookmark.labels.forEach { label ->
                                SuggestionChip(
                                    onClick = { onClickLabel(label) },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Action Icons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
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
                                contentDescription = stringResource(R.string.action_favorite),
                                tint = Color.White
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
                                contentDescription = stringResource(R.string.action_archive),
                                tint = Color.White
                            )
                        }

                        // Open Original Button
                        IconButton(
                            onClick = { onClickOpenUrl(bookmark.id) },
                            modifier = Modifier
                                .width(48.dp)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_view_original),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
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
                            contentDescription = stringResource(R.string.action_delete),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkMagazineView(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {}
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

                // Type icon overlay for Grid
                if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (bookmark.type is Bookmark.Type.Video) Icons.Filled.Movie else Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Read progress indicator
                if (bookmark.readProgress > 0) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Gray.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bookmark.readProgress == 100) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.action_mark_read),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Canvas(
                                modifier = Modifier.size(20.dp)
                            ) {
                                val progressColor = Color.White
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
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // Title
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

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
                    bookmark.readingTime?.let {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "$it min",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Labels row
                if (bookmark.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_label_24px),
                            contentDescription = "labels",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val labels = bookmark.labels.joinToString(", ")
                        Text(
                            text = labels,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

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
                            onClick = { onClickOpenUrl(bookmark.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_view_original),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkListItemView(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickCard(bookmark.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Title row with favicon aligned to top
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Favicon
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(bookmark.iconSrc)
                    .crossfade(true).build(),
                contentDescription = "site icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(24.dp)
                    .height(24.dp),
            )

            Spacer(Modifier.width(12.dp))

            // Title
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Site row with progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Read progress indicator (theme-aware)
            if (bookmark.readProgress > 0 && bookmark.readProgress < 100) {
                val progressColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                    Color.DarkGray // Light theme - dark icon
                } else {
                    Color.LightGray // Dark theme - light icon
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = Color.Gray.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.size(14.dp)
                    ) {
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
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = bookmark.siteName,
                style = MaterialTheme.typography.labelSmall
            )
            bookmark.readingTime?.let {
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "$it min",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Labels row
        if (bookmark.labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_label_24px),
                    contentDescription = "labels",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                val labels = bookmark.labels.joinToString(", ")
                Text(
                    text = labels,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action buttons (same arrangement as Grid)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.Start) {
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
                    onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                        contentDescription = stringResource(R.string.action_archive),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onClickOpenUrl(bookmark.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.action_view_original),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    val progressColor = Color.White

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
        created = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        wordCount = 2000,
        published = null
    )
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
        BookmarkCard(
            bookmark = sampleBookmark,
            onClickCard = {},
            onClickDelete = {},
            onClickFavorite = { _, _ -> },
            onClickArchive = { _, _ -> },
            onClickShareBookmark = {_ -> }
        )
    }
}
