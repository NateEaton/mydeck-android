package com.mydeck.app.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.domain.model.Collection
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.ui.list.BookmarkListViewModel
import com.mydeck.app.ui.navigation.BookmarkListRoute
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shared leading icon for a collection (drawer/rail entry, active-collection app bar, cards). The
 * Navigation Settings custom-view item reuses this same icon, so keep them consistent.
 */
val CollectionIcon: ImageVector = Icons.Outlined.Bookmarks

/**
 * Browse/manage collections. Modeled on the Highlights screen: a list of cards (name + created
 * date), a FAB to create one via the editor sheet, and an M3 empty state. Reuses the shell's shared
 * [BookmarkListViewModel] so selection state stays in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    navController: NavHostController,
    viewModel: BookmarkListViewModel,
    showBackButton: Boolean = true,
) {
    val collections by viewModel.visibleCollections.collectAsState()
    val labels by viewModel.labelsWithCounts.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshCollections() }

    // Collection failures (e.g. a create from the FAB that doesn't reach the server) surface here
    // while the Collections screen is foreground; the ViewModel is shared with the bookmark list.
    LaunchedEffect(Unit) {
        viewModel.collectionMessageEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_screen_title)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.collection_create_fab)) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (collections.isEmpty()) {
                CollectionsEmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(collections, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            onClick = {
                                viewModel.onSelectCollection(collection.id)
                                navController.navigate(BookmarkListRoute()) {
                                    popUpTo(BookmarkListRoute()) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        CollectionEditorSheet(
            heading = stringResource(R.string.collection_create_fab),
            initialName = "",
            initialFilter = FilterFormState(),
            labels = labels,
            onSave = { name, filter ->
                showEditor = false
                viewModel.onCreateCollection(name, filter)
            },
            onDismiss = { showEditor = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionCard(
    collection: Collection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatter.format(collection.created.toJavaInstant()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollectionsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = CollectionIcon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.collections_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.collections_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
