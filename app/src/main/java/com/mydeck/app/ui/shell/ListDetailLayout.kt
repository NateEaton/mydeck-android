package com.mydeck.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.list.BookmarkListScreen
import com.mydeck.app.ui.list.BookmarkListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExpandedListDetailLayout(
    bookmarkListViewModel: BookmarkListViewModel,
    selectedBookmarkId: MutableState<String?>,
    selectedShowOriginal: MutableState<Boolean>,
    navController: NavHostController,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    // Sync external selection with scaffold navigator
    LaunchedEffect(selectedBookmarkId.value) {
        val id = selectedBookmarkId.value
        if (id != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
        } else if (navigator.canNavigateBack()) {
            navigator.navigateBack()
        }
    }

    BackHandler(navigator.canNavigateBack()) {
        selectedBookmarkId.value = null
        selectedShowOriginal.value = false
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                BookmarkListScreen(
                    navHostController = navController,
                    viewModel = bookmarkListViewModel,
                    drawerState = DrawerState(DrawerValue.Closed),
                    showNavigationIcon = false,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val contentKey = navigator.currentDestination?.contentKey
                if (contentKey != null) {
                    BookmarkDetailPaneHost(
                        bookmarkId = contentKey,
                        showOriginal = selectedShowOriginal.value,
                        onNavigateBack = {
                            selectedBookmarkId.value = null
                            selectedShowOriginal.value = false
                            scope.launch { navigator.navigateBack() }
                        }
                    )
                } else {
                    // Empty placeholder when no bookmark is selected
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.select_bookmark),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}
