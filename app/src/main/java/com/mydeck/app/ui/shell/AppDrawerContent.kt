package com.mydeck.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.BookmarkCounts
import com.mydeck.app.domain.model.DrawerPreset

@Composable
fun AppDrawerContent(
    drawerPreset: DrawerPreset,
    activeLabel: String?,
    bookmarkCounts: BookmarkCounts,
    labelsWithCounts: Map<String, Int>,
    isOnline: Boolean,
    onClickMyList: () -> Unit,
    onClickArchive: () -> Unit,
    onClickFavorite: () -> Unit,
    onClickArticles: () -> Unit,
    onClickVideos: () -> Unit,
    onClickPictures: () -> Unit,
    onClickLabels: () -> Unit,
    onClickSettings: () -> Unit,
    onClickUserGuide: () -> Unit,
    onClickAbout: () -> Unit,
    usePermanentSheet: Boolean = false,
) {
    val isLabelMode = activeLabel != null

    if (usePermanentSheet) {
        PermanentDrawerSheet {
            DrawerColumnContent(
                isLabelMode = isLabelMode,
                bookmarkCounts = bookmarkCounts,
                labelsWithCounts = labelsWithCounts,
                isOnline = isOnline,
                drawerPreset = drawerPreset,
                activeLabel = activeLabel,
                onClickMyList = onClickMyList,
                onClickArchive = onClickArchive,
                onClickFavorite = onClickFavorite,
                onClickArticles = onClickArticles,
                onClickVideos = onClickVideos,
                onClickPictures = onClickPictures,
                onClickLabels = onClickLabels,
                onClickSettings = onClickSettings,
                onClickUserGuide = onClickUserGuide,
                onClickAbout = onClickAbout,
            )
        }
    } else {
        ModalDrawerSheet(
            modifier = Modifier.fillMaxWidth(0.75f)
        ) {
            DrawerColumnContent(
                isLabelMode = isLabelMode,
                bookmarkCounts = bookmarkCounts,
                labelsWithCounts = labelsWithCounts,
                isOnline = isOnline,
                drawerPreset = drawerPreset,
                activeLabel = activeLabel,
                onClickMyList = onClickMyList,
                onClickArchive = onClickArchive,
                onClickFavorite = onClickFavorite,
                onClickArticles = onClickArticles,
                onClickVideos = onClickVideos,
                onClickPictures = onClickPictures,
                onClickLabels = onClickLabels,
                onClickSettings = onClickSettings,
                onClickUserGuide = onClickUserGuide,
                onClickAbout = onClickAbout,
            )
        }
    }
}

@Composable
private fun DrawerColumnContent(
    isLabelMode: Boolean,
    bookmarkCounts: BookmarkCounts,
    labelsWithCounts: Map<String, Int>,
    isOnline: Boolean,
    drawerPreset: DrawerPreset,
    activeLabel: String?,
    onClickMyList: () -> Unit,
    onClickArchive: () -> Unit,
    onClickFavorite: () -> Unit,
    onClickArticles: () -> Unit,
    onClickVideos: () -> Unit,
    onClickPictures: () -> Unit,
    onClickLabels: () -> Unit,
    onClickSettings: () -> Unit,
    onClickUserGuide: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val prominentItemColors = NavigationDrawerItemDefaults.colors(
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
    )
    val selectedViewItemColors = NavigationDrawerItemDefaults.colors(
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineSmall
            )
            if (!isOnline) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = stringResource(R.string.offline_tooltip),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        val isMyListSelected = !isLabelMode && drawerPreset == DrawerPreset.MY_LIST
        NavigationDrawerItem(
            label = { Text(
                style = if (isMyListSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.my_list)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.CollectionsBookmark, contentDescription = null) },
            badge = {
                val myListCount = bookmarkCounts.total - bookmarkCounts.archived
                if (myListCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = myListCount.toString(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            selected = isMyListSelected,
            colors = if (isMyListSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickMyList
        )
        val isArchiveSelected = !isLabelMode && drawerPreset == DrawerPreset.ARCHIVE
        NavigationDrawerItem(
            label = { Text(
                style = if (isArchiveSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.archive)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null) },
            badge = {
                bookmarkCounts.archived.let { count ->
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            selected = isArchiveSelected,
            colors = if (isArchiveSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickArchive
        )
        val isFavoritesSelected = !isLabelMode && drawerPreset == DrawerPreset.FAVORITES
        NavigationDrawerItem(
            label = { Text(
                style = if (isFavoritesSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.favorites)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.FavoriteBorder, contentDescription = null) },
            badge = {
                bookmarkCounts.favorite.let { count ->
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            selected = isFavoritesSelected,
            colors = if (isFavoritesSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickFavorite
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        val isArticlesSelected = !isLabelMode && drawerPreset == DrawerPreset.ARTICLES
        NavigationDrawerItem(
            label = { Text(
                style = if (isArticlesSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.articles)
            ) },
            icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.Article, contentDescription = null) },
            badge = {
                bookmarkCounts.article.let { count ->
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            selected = isArticlesSelected,
            colors = if (isArticlesSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickArticles
        )
        val isVideosSelected = !isLabelMode && drawerPreset == DrawerPreset.VIDEOS
        NavigationDrawerItem(
            label = { Text(
                style = if (isVideosSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.videos)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.VideoLibrary, contentDescription = null) },
            badge = {
                bookmarkCounts.video.let { count ->
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            selected = isVideosSelected,
            colors = if (isVideosSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickVideos
        )
        val isPicturesSelected = !isLabelMode && drawerPreset == DrawerPreset.PICTURES
        NavigationDrawerItem(
            label = { Text(
                style = if (isPicturesSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                text = stringResource(id = R.string.pictures)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.Image, contentDescription = null) },
            badge = {
                bookmarkCounts.picture.let { count ->
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            selected = isPicturesSelected,
            colors = if (isPicturesSelected) selectedViewItemColors else NavigationDrawerItemDefaults.colors(),
            onClick = onClickPictures
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        NavigationDrawerItem(
            label = { Text(
                style = MaterialTheme.typography.titleMedium,
                text = stringResource(id = R.string.labels)
            ) },
            icon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
            badge = {
                if (labelsWithCounts.isNotEmpty()) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = labelsWithCounts.size.toString(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            selected = isLabelMode,
            colors = prominentItemColors,
            onClick = onClickLabels
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        NavigationDrawerItem(
            label = { Text(
                style = MaterialTheme.typography.titleMedium,
                text = stringResource(id = R.string.settings)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
            selected = false,
            colors = prominentItemColors,
            onClick = onClickSettings
        )
        NavigationDrawerItem(
            label = { Text(
                style = MaterialTheme.typography.titleMedium,
                text = stringResource(id = R.string.user_guide)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.HelpOutline, contentDescription = null) },
            selected = false,
            colors = prominentItemColors,
            onClick = onClickUserGuide
        )
        NavigationDrawerItem(
            label = { Text(
                style = MaterialTheme.typography.titleMedium,
                text = stringResource(id = R.string.about_title)
            ) },
            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
            selected = false,
            colors = prominentItemColors,
            onClick = onClickAbout
        )
    }
}
