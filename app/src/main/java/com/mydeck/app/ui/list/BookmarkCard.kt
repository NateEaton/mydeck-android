package com.mydeck.app.ui.list

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.outlinedCardBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import coil3.ColorImage
import coil3.asImage
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
import com.mydeck.app.ui.components.LongPressContextMenuDialog
import com.mydeck.app.ui.components.LongPressContextMenuItem
import com.mydeck.app.ui.drawable.ReadeckPlaceholderDrawable

@Composable
private fun BookmarkShimmerBox(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    Box(
        modifier = modifier.background(
            Brush.horizontalGradient(
                colors = shimmerColors,
                startX = translateAnim - 400f,
                endX = translateAnim
            )
        )
    )
}

private val ThumbnailStatusBadgeSize = 24.dp
private val ThumbnailStatusIconSize = 14.dp
private val ThumbnailStatusProgressSize = 16.dp
private val CompactStatusRailWidth = 32.dp
private val CompactStatusSlotSize = 24.dp
private val CompactStatusIconSize = 20.dp
private val CompactCardMinHeight = 84.dp
private val BookmarkDownloadIconSize = 14.dp
private val MosaicOfflineBadgeBottomInset = 96.dp

@Composable
private fun ThumbnailReadingStatusIndicator(
    readProgress: Int,
    modifier: Modifier = Modifier
) {
    if (readProgress <= 0) return

    Box(
        modifier = modifier
            .size(ThumbnailStatusBadgeSize)
            .background(
                color = Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (readProgress == 100) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.action_mark_read),
                tint = Color.White,
                modifier = Modifier.size(ThumbnailStatusIconSize)
            )
        } else {
            Canvas(modifier = Modifier.size(ThumbnailStatusProgressSize)) {
                val strokeWidth = 2.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val sweepAngle = (readProgress.coerceIn(0, 100) / 100f) * 360f
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun OfflineStateIndicator(
    offlineState: BookmarkListItem.OfflineState,
    modifier: Modifier = Modifier,
    badgeSize: Dp = ThumbnailStatusBadgeSize,
    iconSize: Dp = ThumbnailStatusIconSize
) {
    if (offlineState == BookmarkListItem.OfflineState.NOT_DOWNLOADED) return
    Box(
        modifier = modifier
            .size(badgeSize)
            .background(
                color = Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (offlineState) {
                BookmarkListItem.OfflineState.DOWNLOADED_FULL -> Icons.Filled.DownloadForOffline
                else -> Icons.Outlined.DownloadForOffline
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun CompactReadingStatusIndicator(
    readProgress: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = CompactStatusIconSize
) {
    if (readProgress <= 0) return
    val primary = MaterialTheme.colorScheme.primary
    if (readProgress == 100) {
        Box(
            modifier = modifier
                .size(iconSize + 4.dp)
                .background(
                    color = primary.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(iconSize - 4.dp)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(iconSize + 4.dp)
                .background(
                    color = primary.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize - 4.dp)) {
                val strokeWidth = 2.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val sweepAngle = (readProgress.coerceIn(0, 100) / 100f) * 360f
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun CompactOfflineStateIndicator(
    offlineState: BookmarkListItem.OfflineState,
    modifier: Modifier = Modifier,
    iconSize: Dp = CompactStatusIconSize
) {
    if (offlineState == BookmarkListItem.OfflineState.NOT_DOWNLOADED) return
    Icon(
        imageVector = when (offlineState) {
            BookmarkListItem.OfflineState.DOWNLOADED_FULL -> Icons.Filled.DownloadForOffline
            else -> Icons.Outlined.DownloadForOffline
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(iconSize)
    )
}

@Composable
private fun CompactStatusRail(
    bookmark: BookmarkListItem,
    faviconSize: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(CompactStatusRailWidth)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(faviconSize),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bookmark.iconSrc)
                    .crossfade(true)
                    .build(),
                contentDescription = "site icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(faviconSize),
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

val LocalIsWideLayout = compositionLocalOf { false }

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarkMosaicCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
    isWideLayout: Boolean = LocalIsWideLayout.current
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isWideLayout) 1.dp else 8.dp)
                .height(200.dp)
                .clickable { onClickCard(bookmark.id) },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = BookmarkCardBorderAlpha)
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Full height thumbnail as background — Zone B (image long-press)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bookmark.imageSrc)
                        .crossfade(true)
                        .error(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .fallback(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .build(),
                    contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                    contentScale = ContentScale.Crop,
                    loading = { BookmarkShimmerBox(modifier = Modifier.fillMaxSize()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .combinedClickable(
                            onClick = { onClickCard(bookmark.id) },
                            onLongClick = { showImageContextMenu = true },
                            onLongClickLabel = stringResource(R.string.long_press_for_options)
                        )
                )

                // Type icon overlay
                if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
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

                ThumbnailReadingStatusIndicator(
                    readProgress = bookmark.readProgress,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )

                // Darker, taller gradient overlay for stronger contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.15f to Color.Black.copy(alpha = 0.05f),
                                    0.35f to Color.Black.copy(alpha = 0.25f),
                                    0.55f to Color.Black.copy(alpha = 0.55f),
                                    0.75f to Color.Black.copy(alpha = 0.80f),
                                    1.0f to Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Overlay content: title + action icons — Zone A (body long-press)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onClickCard(bookmark.id) },
                            onLongClick = { showBodyContextMenu = true },
                            onLongClickLabel = stringResource(R.string.long_press_for_options)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Top row: Title + Download status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = bookmark.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        BookmarkDownloadStatusIndicator(
                            offlineState = bookmark.offlineState,
                            modifier = Modifier.padding(top = 2.dp),
                            isMosaic = true
                        )
                    }

                    // Bottom row: Action icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.Start) {
                            IconButton(
                                onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.action_favorite),
                                    tint = Color.White,
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
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onClickOpenUrl(bookmark.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = stringResource(R.string.action_view_original),
                                    tint = Color.White,
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
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkGridCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
    isWideLayout: Boolean = LocalIsWideLayout.current,
    isInGrid: Boolean = false,
    useMobilePortraitLayout: Boolean = false,
) {
    if (useMobilePortraitLayout) {
        BookmarkGridCardMobilePortrait(
            bookmark = bookmark,
            onClickCard = onClickCard,
            onClickDelete = onClickDelete,
            onClickFavorite = onClickFavorite,
            onClickArchive = onClickArchive,
            onClickLabel = onClickLabel,
            onClickOpenUrl = onClickOpenUrl,
            onClickCopyLink = onClickCopyLink,
            onClickCopyLinkText = onClickCopyLinkText,
            onClickDownloadLink = onClickDownloadLink,
            onClickShareLink = onClickShareLink,
            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
            onClickCopyImage = onClickCopyImage,
            onClickDownloadImage = onClickDownloadImage,
            onClickShareImage = onClickShareImage,
            onRemoveDownloadedContent = onRemoveDownloadedContent,
        )
    } else if (isWideLayout) {
        BookmarkGridCardWide(
            bookmark = bookmark,
            onClickCard = onClickCard,
            onClickDelete = onClickDelete,
            onClickFavorite = onClickFavorite,
            onClickArchive = onClickArchive,
            onClickLabel = onClickLabel,
            onClickOpenUrl = onClickOpenUrl,
            onClickCopyLink = onClickCopyLink,
            onClickCopyLinkText = onClickCopyLinkText,
            onClickDownloadLink = onClickDownloadLink,
            onClickShareLink = onClickShareLink,
            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
            onClickCopyImage = onClickCopyImage,
            onClickDownloadImage = onClickDownloadImage,
            onClickShareImage = onClickShareImage,
            onRemoveDownloadedContent = onRemoveDownloadedContent,
            isInGrid = isInGrid,
        )
    } else {
        BookmarkGridCardNarrow(
            bookmark = bookmark,
            onClickCard = onClickCard,
            onClickDelete = onClickDelete,
            onClickFavorite = onClickFavorite,
            onClickArchive = onClickArchive,
            onClickLabel = onClickLabel,
            onClickOpenUrl = onClickOpenUrl,
            onClickCopyLink = onClickCopyLink,
            onClickCopyLinkText = onClickCopyLinkText,
            onClickDownloadLink = onClickDownloadLink,
            onClickShareLink = onClickShareLink,
            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
            onClickCopyImage = onClickCopyImage,
            onClickDownloadImage = onClickDownloadImage,
            onClickShareImage = onClickShareImage,
            onRemoveDownloadedContent = onRemoveDownloadedContent,
        )
    }
}

private val MobilePortraitGridCardHeight = 168.dp
private const val MobilePortraitThumbnailWeight = 0.25f
private val MobilePortraitLabelRowHeight = 32.dp
private val BookmarkCardBorderAlpha = 0.4f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGridCardMobilePortrait(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }
    var titleLineCount by remember(bookmark.id) { mutableIntStateOf(2) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(MobilePortraitGridCardHeight)
                .combinedClickable(
                    onClick = { onClickCard(bookmark.id) },
                    onLongClick = { showBodyContextMenu = true },
                    onLongClickLabel = stringResource(R.string.long_press_for_options)
                ),
            border = outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = BookmarkCardBorderAlpha)
                )
            )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(MobilePortraitThumbnailWeight)
                        .fillMaxHeight()
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(bookmark.thumbnailSrc)
                            .crossfade(true)
                            .error(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                            .fallback(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                            .build(),
                        contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                        contentScale = ContentScale.Crop,
                        loading = { BookmarkShimmerBox(modifier = Modifier.fillMaxSize()) },
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { onClickCard(bookmark.id) },
                                onLongClick = { showImageContextMenu = true },
                                onLongClickLabel = stringResource(R.string.long_press_for_options)
                            )
                    )

                    if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = if (bookmark.type is Bookmark.Type.Video) Icons.Filled.Movie else Icons.Filled.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    ThumbnailReadingStatusIndicator(
                        readProgress = bookmark.readProgress,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f - MobilePortraitThumbnailWeight)
                        .fillMaxHeight()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = bookmark.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            val measuredLines = result.lineCount.coerceIn(1, 2)
                            if (measuredLines != titleLineCount) titleLineCount = measuredLines
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(bookmark.iconSrc)
                                .crossfade(true)
                                .build(),
                            contentDescription = "site icon",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = bookmark.siteName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        bookmark.readingTime?.let {
                            Text(
                                text = " · $it min",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        BookmarkDownloadStatusIndicator(
                            offlineState = bookmark.offlineState,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (titleLineCount == 1) 8.dp else 6.dp))

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MobilePortraitLabelRowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (bookmark.labels.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(bookmark.labels) { label ->
                                    SuggestionChip(
                                        onClick = { onClickLabel(label) },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.Start) {
                            IconButton(
                                onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.action_favorite),
                                )
                            }
                            IconButton(
                                onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (bookmark.isArchived) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                    contentDescription = stringResource(R.string.action_archive),
                                )
                            }
                            IconButton(
                                onClick = { onClickOpenUrl(bookmark.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = stringResource(R.string.action_view_original),
                                )
                            }
                        }
                        IconButton(
                            onClick = { onClickDelete(bookmark.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                }
            }
        }

        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGridCardNarrow(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .combinedClickable(
                    onClick = { onClickCard(bookmark.id) },
                    onLongClick = { showBodyContextMenu = true },
                    onLongClickLabel = stringResource(R.string.long_press_for_options)
                ),
            border = outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = BookmarkCardBorderAlpha)
                )
            )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail
            Box {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bookmark.thumbnailSrc)
                        .crossfade(true)
                        .error(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .fallback(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .build(),
                    contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                    contentScale = ContentScale.Crop,
                    loading = { BookmarkShimmerBox(modifier = Modifier.fillMaxSize()) },
                    modifier = Modifier
                        .width(100.dp)
                        .height(80.dp)
                        .combinedClickable(
                            onClick = { onClickCard(bookmark.id) },
                            onLongClick = { showImageContextMenu = true },
                            onLongClickLabel = stringResource(R.string.long_press_for_options)
                        )
                )

                // Type icon overlay for Grid
                if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = if (bookmark.type is Bookmark.Type.Video) Icons.Filled.Movie else Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                ThumbnailReadingStatusIndicator(
                    readProgress = bookmark.readProgress,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
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
                        overflow = TextOverflow.Ellipsis
                    )
                    bookmark.readingTime?.let {
                        Text(
                            text = " · $it min",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    BookmarkDownloadStatusIndicator(
                        offlineState = bookmark.offlineState,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Labels row with tappable chips
                if (bookmark.labels.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        bookmark.labels.forEach { label ->
                            SuggestionChip(
                                onClick = { onClickLabel(label) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
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
                                imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
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
                                imageVector = Icons.Filled.Language,
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
        }
        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGridCardWide(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
    isInGrid: Boolean = false,
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }

    Box {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isInGrid) Modifier.height(300.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = { onClickCard(bookmark.id) },
                onLongClick = { showBodyContextMenu = true },
                onLongClickLabel = stringResource(R.string.long_press_for_options)
            ),
        border = outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = BookmarkCardBorderAlpha)
            )
        )
    ) {
        Column(modifier = if (isInGrid) Modifier.fillMaxHeight() else Modifier) {
            // Full-width image above content (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isInGrid) Modifier.height(140.dp) else Modifier.aspectRatio(16f / 9f))
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bookmark.imageSrc)
                        .crossfade(true)
                        .error(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .fallback(ReadeckPlaceholderDrawable(bookmark.url).asImage())
                        .build(),
                    contentDescription = stringResource(R.string.common_bookmark_image_content_description),
                    contentScale = ContentScale.Crop,
                    loading = { BookmarkShimmerBox(modifier = Modifier.fillMaxSize()) },
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { onClickCard(bookmark.id) },
                            onLongClick = { showImageContextMenu = true },
                            onLongClickLabel = stringResource(R.string.long_press_for_options)
                        )
                )

                // Type icon overlay
                if (bookmark.type is Bookmark.Type.Video || bookmark.type is Bookmark.Type.Picture) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (bookmark.type is Bookmark.Type.Video) Icons.Filled.Movie else Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                ThumbnailReadingStatusIndicator(
                    readProgress = bookmark.readProgress,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }

            // Content below image
            val contentModifier = Modifier.padding(12.dp)
            Column(
                modifier = if (isInGrid) contentModifier.weight(1f) else contentModifier
            ) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
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
                        overflow = TextOverflow.Ellipsis
                    )
                    bookmark.readingTime?.let {
                        Text(
                            text = " · $it min",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    BookmarkDownloadStatusIndicator(
                        offlineState = bookmark.offlineState,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Labels row with tappable chips (single line, scrollable)
                if (bookmark.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bookmark.labels.forEach { label ->
                            SuggestionChip(
                                onClick = { onClickLabel(label) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                if (isInGrid) {
                    Spacer(modifier = Modifier.weight(1f))
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
                                imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
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
                                imageVector = Icons.Filled.Language,
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
        }
        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkDownloadStatusIndicator(
    offlineState: BookmarkListItem.OfflineState,
    modifier: Modifier = Modifier,
    isMosaic: Boolean = false
) {
    if (offlineState == BookmarkListItem.OfflineState.NOT_DOWNLOADED) return
    val isFull = offlineState == BookmarkListItem.OfflineState.DOWNLOADED_FULL
    Icon(
        imageVector = if (isFull) Icons.Filled.DownloadForOffline else Icons.Outlined.DownloadForOffline,
        contentDescription = stringResource(if (isFull) R.string.notif_content_ready_title else R.string.action_download_title),
        modifier = modifier.size(BookmarkDownloadIconSize),
        tint = if (isMosaic) {
            Color.White.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        }
    )
}

@Composable
fun BookmarkCompactCard(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
    isWideLayout: Boolean = LocalIsWideLayout.current
) {
    if (isWideLayout) {
        BookmarkCompactCardWide(
            bookmark = bookmark,
            onClickCard = onClickCard,
            onClickDelete = onClickDelete,
            onClickFavorite = onClickFavorite,
            onClickArchive = onClickArchive,
            onClickLabel = onClickLabel,
            onClickOpenUrl = onClickOpenUrl,
            onClickCopyLink = onClickCopyLink,
            onClickCopyLinkText = onClickCopyLinkText,
            onClickDownloadLink = onClickDownloadLink,
            onClickShareLink = onClickShareLink,
            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
            onClickCopyImage = onClickCopyImage,
            onClickDownloadImage = onClickDownloadImage,
            onClickShareImage = onClickShareImage,
            onRemoveDownloadedContent = onRemoveDownloadedContent,
        )
    } else {
        BookmarkCompactCardNarrow(
            bookmark = bookmark,
            onClickCard = onClickCard,
            onClickDelete = onClickDelete,
            onClickFavorite = onClickFavorite,
            onClickArchive = onClickArchive,
            onClickLabel = onClickLabel,
            onClickOpenUrl = onClickOpenUrl,
            onClickCopyLink = onClickCopyLink,
            onClickCopyLinkText = onClickCopyLinkText,
            onClickDownloadLink = onClickDownloadLink,
            onClickShareLink = onClickShareLink,
            onClickOpenInBrowserFromMenu = onClickOpenInBrowserFromMenu,
            onClickCopyImage = onClickCopyImage,
            onClickDownloadImage = onClickDownloadImage,
            onClickShareImage = onClickShareImage,
            onRemoveDownloadedContent = onRemoveDownloadedContent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCompactCardNarrow(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }

    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClickCard(bookmark.id) },
                onLongClick = { showBodyContextMenu = true },
                onLongClickLabel = stringResource(R.string.long_press_for_options)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = CompactCardMinHeight)
    ) {
        CompactStatusRail(
            bookmark = bookmark,
            faviconSize = 28.dp
        )

        Spacer(Modifier.width(12.dp))

        // Right column: title, site info, labels, actions
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Site row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactReadingStatusIndicator(
                    readProgress = bookmark.readProgress,
                    modifier = Modifier.padding(end = 4.dp),
                    iconSize = 17.dp
                )
                Text(
                    text = bookmark.siteName,
                    style = MaterialTheme.typography.labelMedium
                )
                bookmark.readingTime?.let {
                    Text(
                        text = " · $it min",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                BookmarkDownloadStatusIndicator(
                    offlineState = bookmark.offlineState,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Labels row — all labels as tappable chips, single-line horizontal scroll
            if (bookmark.labels.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmark.labels) { label ->
                        SuggestionChip(
                            onClick = { onClickLabel(label) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            // Action buttons (same arrangement as Grid)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.Start) {
                IconButton(
                    onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        modifier = Modifier.size(18.dp)
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
                        imageVector = Icons.Filled.Language,
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
        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCompactCardWide(
    bookmark: BookmarkListItem,
    onClickCard: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickCopyLink: (String) -> Unit = {},
    onClickCopyLinkText: (String) -> Unit = {},
    onClickDownloadLink: (String, String) -> Unit = { _, _ -> },
    onClickShareLink: (String, String) -> Unit = { _, _ -> },
    onClickOpenInBrowserFromMenu: (String) -> Unit = {},
    onClickCopyImage: (String) -> Unit = {},
    onClickDownloadImage: (String) -> Unit = {},
    onClickShareImage: (String) -> Unit = {},
    onRemoveDownloadedContent: (String) -> Unit = {},
) {
    var showBodyContextMenu by remember { mutableStateOf(false) }
    var showImageContextMenu by remember { mutableStateOf(false) }

    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClickCard(bookmark.id) },
                onLongClick = { showBodyContextMenu = true },
                onLongClickLabel = stringResource(R.string.long_press_for_options)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = CompactCardMinHeight)
    ) {
        CompactStatusRail(
            bookmark = bookmark,
            faviconSize = 24.dp
        )

        Spacer(Modifier.width(8.dp))

        // Right column: title row with actions, site info row
        Column(modifier = Modifier.weight(1f)) {
            // Title + action icons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(4.dp))

                // Action icons in title row (right-aligned)
                IconButton(
                    onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (bookmark.isMarked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
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
                        imageVector = Icons.Filled.Language,
                        contentDescription = stringResource(R.string.action_view_original),
                        modifier = Modifier.size(20.dp)
                    )
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

            // Site name + labels on same row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactReadingStatusIndicator(
                    readProgress = bookmark.readProgress,
                    modifier = Modifier.padding(end = 4.dp),
                    iconSize = 17.dp
                )
                Text(
                    text = bookmark.siteName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                bookmark.readingTime?.let {
                    Text(
                        text = " · $it min",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                BookmarkDownloadStatusIndicator(
                    offlineState = bookmark.offlineState,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Labels inline after site name, single-line horizontal scroll
                if (bookmark.labels.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(bookmark.labels) { label ->
                            SuggestionChip(
                                onClick = { onClickLabel(label) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
        HorizontalDivider()
        if (showBodyContextMenu) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.iconSrc,
                title = bookmark.title,
                subtitle = bookmark.url,
                onDismiss = { showBodyContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link),
                    onClick = { showBodyContextMenu = false; onClickCopyLink(bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_link_text),
                    onClick = { showBodyContextMenu = false; onClickCopyLinkText(bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_link),
                    onClick = { showBodyContextMenu = false; onClickDownloadLink(bookmark.url, bookmark.title) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_link),
                    onClick = { showBodyContextMenu = false; onClickShareLink(bookmark.title, bookmark.url) },
                )
                LongPressContextMenuItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_in_browser),
                    onClick = { showBodyContextMenu = false; onClickOpenInBrowserFromMenu(bookmark.url) },
                )

            }
        }
        if (showImageContextMenu && bookmark.imageSrc.isNotBlank()) {
            LongPressContextMenuDialog(
                headerImageUrl = bookmark.imageSrc,
                title = bookmark.title,
                subtitle = bookmark.imageSrc,
                onDismiss = { showImageContextMenu = false },
            ) {
                LongPressContextMenuItem(
                    icon = Icons.Outlined.ContentCopy,
                    text = stringResource(R.string.action_copy_image),
                    onClick = { showImageContextMenu = false; onClickCopyImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.action_download_image),
                    onClick = { showImageContextMenu = false; onClickDownloadImage(bookmark.imageSrc) },
                )
                LongPressContextMenuItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share_image),
                    onClick = { showImageContextMenu = false; onClickShareImage(bookmark.imageSrc) },
                )
            }
        }
    }
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
        href = "https://example.com",
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
        BookmarkMosaicCard(
            bookmark = sampleBookmark,
            onClickCard = {},
            onClickDelete = {},
            onClickFavorite = { _, _ -> },
            onClickArchive = { _, _ -> },
        )
    }
}
