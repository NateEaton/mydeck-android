package com.mydeck.app.ui.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mydeck.app.domain.model.DrawerPreset

@Composable
fun AppNavigationRailContent(
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
    onClickAbout: () -> Unit,
) {
    val isLabelMode = activeLabel != null

    NavigationRail {
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.MY_LIST,
            onClick = onClickMyList,
            icon = { Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null) }
        )
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.ARCHIVE,
            onClick = onClickArchive,
            icon = { Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null) }
        )
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.FAVORITES,
            onClick = onClickFavorite,
            icon = { Icon(imageVector = Icons.Filled.Grade, contentDescription = null) }
        )
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.ARTICLES,
            onClick = onClickArticles,
            icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.Article, contentDescription = null) }
        )
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.VIDEOS,
            onClick = onClickVideos,
            icon = { Icon(imageVector = Icons.Outlined.VideoLibrary, contentDescription = null) }
        )
        NavigationRailItem(
            selected = !isLabelMode && drawerPreset == DrawerPreset.PICTURES,
            onClick = onClickPictures,
            icon = { Icon(imageVector = Icons.Outlined.Image, contentDescription = null) }
        )
        NavigationRailItem(
            selected = isLabelMode,
            onClick = onClickLabels,
            icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null) }
        )
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = false,
            onClick = onClickSettings,
            icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) }
        )
        NavigationRailItem(
            selected = false,
            onClick = onClickAbout,
            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) }
        )
    }
}
