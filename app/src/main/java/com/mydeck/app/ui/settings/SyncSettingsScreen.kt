package com.mydeck.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.OfflineContentScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: SyncSettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is SyncSettingsViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
            }
            viewModel.onNavigationEventConsumed()
        }
    }

    when (settingsUiState.showDialog) {
        SyncSettingsDialog.BookmarkSyncFrequencyDialog -> {
            AutoSyncTimeframeDialog(
                autoSyncTimeframeOptions = settingsUiState.bookmarkSyncFrequencyOptions,
                onDismissRequest = { viewModel.onDismissDialog() },
                onElementSelected = { viewModel.onBookmarkSyncFrequencySelected(it) }
            )
        }

        SyncSettingsDialog.OfflineContentScopeDialog -> {
            OfflineContentScopeDialog(
                selectedScope = settingsUiState.offlineContentScope,
                onDismissRequest = { viewModel.onDismissDialog() },
                onScopeSelected = { scope ->
                    viewModel.onOfflineContentScopeSelected(scope)
                    viewModel.onDismissDialog()
                }
            )
        }

        SyncSettingsDialog.OfflineImageStorageLimitDialog -> {
            OfflineImageStorageLimitDialog(
                selectedLimit = settingsUiState.offlineImageStorageLimit,
                onDismissRequest = { viewModel.onDismissDialog() },
                onLimitSelected = { limit ->
                    viewModel.onOfflineImageStorageLimitSelected(limit)
                    viewModel.onDismissDialog()
                }
            )
        }

        SyncSettingsDialog.ClearOfflineContentDialog -> {
            ClearOfflineContentConfirmDialog(
                onConfirm = { viewModel.onConfirmClearOfflineContent() },
                onCancel = { viewModel.onDismissDialog() }
            )
        }

        null -> Unit
    }

    SyncSettingsView(
        snackbarHostState = snackbarHostState,
        settingsUiState = settingsUiState,
        onClickBack = { viewModel.onClickBack() },
        onClickBookmarkSyncFrequency = { viewModel.onClickBookmarkSyncFrequency() },
        onClickSyncBookmarksNow = { viewModel.onClickSyncBookmarksNow() },
        onOfflineReadingChanged = { viewModel.onOfflineReadingChanged(it) },
        onClickOfflineContentScope = { viewModel.onClickOfflineContentScope() },
        onClickOfflineImageStorageLimit = { viewModel.onClickOfflineImageStorageLimit() },
        onWifiOnlyChanged = { viewModel.onWifiOnlyChanged(it) },
        onAllowBatterySaverChanged = { viewModel.onAllowBatterySaverChanged(it) },
        onClickClearOfflineContent = { viewModel.onClickClearOfflineContent() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsView(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    settingsUiState: SyncSettingsUiState,
    onClickBack: () -> Unit,
    onClickBookmarkSyncFrequency: () -> Unit,
    onClickSyncBookmarksNow: () -> Unit,
    onOfflineReadingChanged: (Boolean) -> Unit,
    onClickOfflineContentScope: () -> Unit,
    onClickOfflineImageStorageLimit: () -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
    onClickClearOfflineContent: () -> Unit,
) {
    val hasOfflineContent = settingsUiState.syncStatus.contentDownloaded > 0
    val showClearOfflineContent = settingsUiState.offlineReadingEnabled || hasOfflineContent

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_settings_topbar_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClickBack,
                        modifier = Modifier.testTag(SyncSettingsScreenTestTags.BACK_BUTTON)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BookmarkSyncSection(
                frequency = settingsUiState.bookmarkSyncFrequency,
                nextRun = settingsUiState.nextAutoSyncRun,
                isSyncRunning = settingsUiState.isBookmarkSyncRunning,
                onClickFrequency = onClickBookmarkSyncFrequency,
                onClickSyncBookmarksNow = onClickSyncBookmarksNow
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            OfflineReadingSection(
                uiState = settingsUiState,
                onOfflineReadingChanged = onOfflineReadingChanged,
                onClickOfflineContentScope = onClickOfflineContentScope,
                onClickOfflineImageStorageLimit = onClickOfflineImageStorageLimit,
                onWifiOnlyChanged = onWifiOnlyChanged,
                onAllowBatterySaverChanged = onAllowBatterySaverChanged
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SyncStatusSection(
                syncStatus = settingsUiState.syncStatus,
                showOfflineDetails = settingsUiState.offlineReadingEnabled
            )

            if (showClearOfflineContent) {
                OutlinedButton(
                    onClick = onClickClearOfflineContent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sync_storage_clear))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookmarkSyncSection(
    frequency: AutoSyncTimeframe,
    nextRun: String?,
    isSyncRunning: Boolean,
    onClickFrequency: () -> Unit,
    onClickSyncBookmarksNow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.sync_bookmark_section_title),
            description = stringResource(R.string.sync_bookmark_description)
        )

        ListItem(
            modifier = Modifier.clickable(onClick = onClickFrequency),
            headlineContent = {
                Text(text = stringResource(R.string.sync_bookmark_frequency_label))
            },
            supportingContent = {
                val nextRunMsg = nextRun?.let {
                    stringResource(R.string.auto_sync_next_run, it)
                } ?: stringResource(R.string.auto_sync_next_run_null)
                Text(text = nextRunMsg)
            },
            trailingContent = {
                Text(
                    text = stringResource(frequency.toLabelResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )

        Button(
            onClick = onClickSyncBookmarksNow,
            enabled = !isSyncRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSyncRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.sync_settings_sync_running))
            } else {
                Text(stringResource(R.string.sync_settings_sync_bookmarks_now))
            }
        }
    }
}

@Composable
private fun OfflineReadingSection(
    uiState: SyncSettingsUiState,
    onOfflineReadingChanged: (Boolean) -> Unit,
    onClickOfflineContentScope: () -> Unit,
    onClickOfflineImageStorageLimit: () -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.sync_offline_section_title),
            description = stringResource(R.string.sync_offline_section_desc)
        )

        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.sync_offline_enable))
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.sync_offline_enable_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = uiState.offlineReadingEnabled,
                    onCheckedChange = onOfflineReadingChanged
                )
            }
        )

        AnimatedVisibility(
            visible = uiState.offlineReadingEnabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                uiState.contentSyncStatusRes?.let { statusRes ->
                    Text(
                        text = stringResource(statusRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusRes == R.string.sync_content_status_up_to_date) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                ListItem(
                    modifier = Modifier.clickable(onClick = onClickOfflineContentScope),
                    headlineContent = {
                        Text(text = stringResource(R.string.sync_offline_scope))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.sync_offline_scope_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(uiState.offlineContentScope.toLabelResource()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                ListItem(
                    modifier = Modifier.clickable(onClick = onClickOfflineImageStorageLimit),
                    headlineContent = {
                        Text(text = stringResource(R.string.sync_offline_image_limit))
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(uiState.offlineImageStorageLimit.toLabelResource()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.sync_wifi_only))
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = onWifiOnlyChanged
                        )
                    }
                )

                        ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.sync_allow_battery_saver))
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.allowBatterySaver,
                            onCheckedChange = onAllowBatterySaverChanged
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncStatusSection(
    syncStatus: SyncStatus,
    showOfflineDetails: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title = stringResource(R.string.sync_status_section_title))

        syncStatus.lastBookmarkSyncTimestamp?.let { ts ->
            Text(
                text = stringResource(R.string.sync_status_last_sync, ts),
                style = MaterialTheme.typography.bodySmall
            )
        } ?: Text(
            text = stringResource(R.string.sync_status_never),
            style = MaterialTheme.typography.bodySmall
        )

        val hasStorageToShow = (syncStatus.textStorageSize?.let { it != "0 B" } == true)
            || (syncStatus.imageStorageSize?.let { it != "0 B" } == true)
        if (showOfflineDetails || hasStorageToShow) {
            Text(
                text = stringResource(
                    R.string.sync_storage_usage_text,
                    syncStatus.textStorageSize ?: "0 B"
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.sync_storage_usage_images,
                    syncStatus.imageStorageSize ?: "0 B"
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (showOfflineDetails) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.sync_status_total, syncStatus.totalBookmarks),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_unread, syncStatus.unread),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_archived, syncStatus.archived),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_favorites, syncStatus.favorites),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_downloaded, syncStatus.contentDownloaded),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_available, syncStatus.contentAvailable),
                style = MaterialTheme.typography.bodySmall
            )

            syncStatus.lastContentSyncTimestamp?.let { ts ->
                Text(
                    text = stringResource(R.string.sync_status_last_content_sync, ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OfflineContentScopeDialog(
    selectedScope: OfflineContentScope,
    onDismissRequest: () -> Unit,
    onScopeSelected: (OfflineContentScope) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.sync_offline_scope_dialog_title)) },
        text = {
            Column {
                OfflineScopeOption(
                    label = stringResource(R.string.sync_offline_scope_my_list),
                    selected = selectedScope == OfflineContentScope.MY_LIST,
                    onClick = { onScopeSelected(OfflineContentScope.MY_LIST) }
                )
                OfflineScopeOption(
                    label = stringResource(R.string.sync_offline_scope_my_list_and_archived),
                    selected = selectedScope == OfflineContentScope.MY_LIST_AND_ARCHIVED,
                    onClick = { onScopeSelected(OfflineContentScope.MY_LIST_AND_ARCHIVED) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun OfflineImageStorageLimitDialog(
    selectedLimit: com.mydeck.app.domain.sync.OfflineImageStorageLimit,
    onDismissRequest: () -> Unit,
    onLimitSelected: (com.mydeck.app.domain.sync.OfflineImageStorageLimit) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.sync_offline_image_limit_dialog_title)) },
        text = {
            Column {
                com.mydeck.app.domain.sync.OfflineImageStorageLimit.entries.forEach { limit ->
                    OfflineScopeOption(
                        label = stringResource(limit.toLabelResource()),
                        selected = limit == selectedLimit,
                        onClick = { onLimitSelected(limit) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun OfflineScopeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label)
            if (selected) {
                Text(text = stringResource(R.string.sync_scope_selected))
            }
        }
    }
}

@Composable
private fun ClearOfflineContentConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_storage_clear_confirm_title)) },
        text = { Text(stringResource(R.string.sync_storage_clear_confirm_body)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.sync_storage_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun OfflineContentScope.toLabelResource(): Int {
    return when (this) {
        OfflineContentScope.MY_LIST -> R.string.sync_offline_scope_my_list
        OfflineContentScope.MY_LIST_AND_ARCHIVED -> R.string.sync_offline_scope_my_list_and_archived
    }
}

object SyncSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
}
