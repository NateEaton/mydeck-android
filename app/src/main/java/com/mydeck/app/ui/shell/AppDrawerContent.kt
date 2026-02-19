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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onClickAbout: () -> Unit,
) {
    val isLabelMode = activeLabel != null

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.padding(16.dp),
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
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = stringResource(id = R.string.my_list)
                ) },
                icon = { Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null)},
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.MY_LIST,
                onClick = onClickMyList
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.ARCHIVE,
                onClick = onClickArchive
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.FAVORITES,
                onClick = onClickFavorite
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.ARTICLES,
                onClick = onClickArticles
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.VIDEOS,
                onClick = onClickVideos
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                selected = !isLabelMode && drawerPreset == DrawerPreset.PICTURES,
                onClick = onClickPictures
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
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
                onClick = onClickLabels
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = stringResource(id = R.string.settings)
                ) },
                icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onClickSettings
            )
            NavigationDrawerItem(
                label = { Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = stringResource(id = R.string.about_title)
                ) },
                icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                selected = false,
                onClick = onClickAbout
            )
        }
    }
}
