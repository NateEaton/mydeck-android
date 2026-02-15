package com.mydeck.app.ui.list.components



import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar

import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.domain.model.LayoutMode

@Composable
fun EmptyScreen(
    modifier: Modifier = Modifier,
    messageResource: Int
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = stringResource(id = messageResource),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}


@Composable
fun LabelsListView(
    modifier: Modifier = Modifier,
    labels: Map<String, Int>,
    scrollToTopTrigger: Int = 0,
    onLabelSelected: (String) -> Unit
) {
    if (labels.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.list_view_empty_nothing_to_see),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        val lazyListState = rememberLazyListState()
        LaunchedEffect(scrollToTopTrigger) {
            if (scrollToTopTrigger > 0) {
                lazyListState.animateScrollToItem(0)
            }
        }
        Box(modifier = modifier.fillMaxWidth()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.labels_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(
                    items = labels.entries.sortedBy { it.key }.toList(),
                    key = { it.key }
                ) { (label, count) ->
                    NavigationDrawerItem(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.medium
                            ),
                        label = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label)
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(count.toString())
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            onLabelSelected(label)
                        }
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                lazyListState = lazyListState
            )
        }
    }
}

@Composable
fun BookmarkListView(
    modifier: Modifier = Modifier,
    filterKey: Any = Unit,
    scrollToTopTrigger: Int = 0,
    layoutMode: LayoutMode = LayoutMode.GRID,
    bookmarks: List<BookmarkListItem>,
    onClickBookmark: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickShareBookmark: (String) -> Unit,
    onClickLabel: (String) -> Unit = {},
    onClickOpenUrl: (String) -> Unit = {},
    onClickOpenInBrowser: (String) -> Unit = {}
) {
    val lazyListState = key(filterKey) { rememberLazyListState() }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            lazyListState.animateScrollToItem(0)
        }
    }
    Box(modifier = modifier) {
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            items(bookmarks, key = { it.id }) { bookmark ->
                when (layoutMode) {
                    LayoutMode.GRID -> BookmarkGridCard(
                        bookmark = bookmark,
                        onClickCard = onClickBookmark,
                        onClickDelete = onClickDelete,
                        onClickArchive = onClickArchive,
                        onClickFavorite = onClickFavorite,
                        onClickShareBookmark = onClickShareBookmark,
                        onClickLabel = onClickLabel,
                        onClickOpenUrl = onClickOpenUrl,
                        onClickOpenInBrowser = onClickOpenInBrowser
                    )
                    LayoutMode.COMPACT -> BookmarkCompactCard(
                        bookmark = bookmark,
                        onClickCard = onClickBookmark,
                        onClickDelete = onClickDelete,
                        onClickArchive = onClickArchive,
                        onClickFavorite = onClickFavorite,
                        onClickShareBookmark = onClickShareBookmark,
                        onClickLabel = onClickLabel,
                        onClickOpenUrl = onClickOpenUrl,
                        onClickOpenInBrowser = onClickOpenInBrowser
                    )
                    LayoutMode.MOSAIC -> BookmarkMosaicCard(
                        bookmark = bookmark,
                        onClickCard = onClickBookmark,
                        onClickDelete = onClickDelete,
                        onClickArchive = onClickArchive,
                        onClickFavorite = onClickFavorite,
                        onClickShareBookmark = onClickShareBookmark,
                        onClickLabel = onClickLabel,
                        onClickOpenUrl = onClickOpenUrl,
                        onClickOpenInBrowser = onClickOpenInBrowser
                    )
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            lazyListState = lazyListState
        )
    }
}
