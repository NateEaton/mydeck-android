package com.mydeck.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mydeck.app.R
import com.mydeck.app.domain.model.GalleryImage
import com.mydeck.app.domain.model.ImageGalleryData
import kotlinx.coroutines.launch

@Composable
fun ImageGalleryOverlay(
    galleryData: ImageGalleryData,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onPageChanged: (Int) -> Unit = {},
) {
    val pagerState = rememberPagerState(
        initialPage = galleryData.currentIndex,
        pageCount = { galleryData.images.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }

    // Notify ViewModel of page changes so the current index survives rotation.
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    // Back press dismisses the gallery (replaces Dialog's dismissOnBackPress).
    BackHandler(onBack = onDismiss)

    // The gallery is a plain in-tree composable, not a Dialog window. Because the
    // app uses enableEdgeToEdge(), fillMaxSize() here covers the entire physical
    // screen automatically in all orientations — no window-management needed.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val image = galleryData.images[page]
            ZoomableImage(
                imageUrl = image.src,
                contentDescription = image.alt.takeIf { it.isNotBlank() },
                onClick = { chromeVisible = !chromeVisible },
                onZoomChanged = { zoom ->
                    if (zoom > 1f) chromeVisible = false else chromeVisible = true
                }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            GalleryTopBar(
                currentImage = galleryData.images[pagerState.currentPage],
                imageIndex = pagerState.currentPage + 1,
                totalImages = galleryData.images.size,
                onClose = onDismiss,
                onOpenLink = { url ->
                    onDismiss()
                    onOpenLink(url)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (galleryData.images.size > 1) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ThumbnailStrip(
                    images = galleryData.images,
                    currentIndex = pagerState.currentPage,
                    onThumbnailClick = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    onClick: () -> Unit,
    onZoomChanged: (Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tap / double-tap: toggle chrome and zoom level.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = {
                        val newScale = if (scale > 1f) 1f else 2f
                        scale = newScale
                        if (newScale == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                        onZoomChanged(newScale)
                    }
                )
            }
            // Pinch-to-zoom and pan while zoomed.
            //
            // Critically, single-finger gestures at 1x zoom are NOT consumed here,
            // so HorizontalPager receives them as page-swipe events.
            // Multi-touch (pinch) is always consumed.
            // Single-finger pan is only consumed when scale > 1f.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break

                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            // Multi-touch pinch — always consume.
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            onZoomChanged(newScale)
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            // Single-finger pan while zoomed — consume so pager doesn't swipe.
                            // Only consume if the pointer actually moved (preserve tap events).
                            val panChange = event.calculatePan()
                            if (panChange.x != 0f || panChange.y != 0f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                                event.changes.forEach { it.consume() }
                            }
                        }
                        // else: single-finger at 1x — don't consume → HorizontalPager swipes.
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            // Inside = scale down to fit the viewport if the image is larger, but
            // never upscale an image that is already smaller than the viewport.
            // This prevents blurry stretching of low-res article thumbnails.
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
        )
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<GalleryImage>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        lazyListState.animateScrollToItem(currentIndex)
    }

    LazyRow(
        state = lazyListState,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            // Keep thumbnail content above the navigation bar (portrait bottom,
            // landscape side). The background extends into the nav bar area so
            // the strip's semi-transparent black blends with the gallery.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images.size) { index ->
            val isSelected = index == currentIndex
            AsyncImage(
                model = images[index].src,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(index) {
                        detectTapGestures(onTap = { onThumbnailClick(index) })
                    }
            )
        }
    }
}

@Composable
private fun GalleryTopBar(
    currentImage: GalleryImage,
    imageIndex: Int,
    totalImages: Int,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.gallery_close),
                tint = Color.White,
            )
        }

        Text(
            text = stringResource(R.string.gallery_image_counter, imageIndex, totalImages),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        if (currentImage.linkHref != null) {
            IconButton(onClick = { onOpenLink(currentImage.linkHref) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.gallery_open_link),
                    tint = Color.White,
                )
            }
        } else {
            Box(modifier = Modifier.size(48.dp))
        }
    }
}
