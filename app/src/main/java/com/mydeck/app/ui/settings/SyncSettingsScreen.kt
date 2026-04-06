package com.mydeck.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.OfflineImageStorageLimit
import com.mydeck.app.domain.sync.OfflinePolicy

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

        SyncSettingsDialog.OfflineStorageLimitDialog -> {
            StorageLimitDialog(
                titleRes = R.string.sync_offline_storage_limit_dialog_title,
                selectedLimit = settingsUiState.offlinePolicyStorageLimit,
                onDismissRequest = { viewModel.onDismissDialog() },
                onLimitSelected = { limit ->
                    viewModel.onOfflinePolicyStorageLimitSelected(limit)
                    viewModel.onDismissDialog()
                }
            )
        }

        SyncSettingsDialog.OfflineNewestNDialog -> {
            IntSelectionDialog(
                titleRes = R.string.sync_offline_policy_newest_n,
                options = offlineNewestNOptions(),
                selectedValue = settingsUiState.offlinePolicyNewestN,
                onDismissRequest = { viewModel.onDismissDialog() },
                onOptionSelected = { newestN ->
                    viewModel.onOfflinePolicyNewestNSelected(newestN)
                    viewModel.onDismissDialog()
                }
            )
        }

        SyncSettingsDialog.OfflineDateRangeWindowDialog -> {
            DurationSelectionDialog(
                titleRes = R.string.sync_offline_policy_date_range,
                options = offlineDateRangeWindowOptions(),
                selectedValue = settingsUiState.offlinePolicyDateRangeWindow,
                onDismissRequest = { viewModel.onDismissDialog() },
                onOptionSelected = { window ->
                    viewModel.onOfflinePolicyDateRangeWindowSelected(window)
                    viewModel.onDismissDialog()
                }
            )
        }

        SyncSettingsDialog.OfflineMaxStorageCapDialog -> {
            StorageLimitDialog(
                titleRes = R.string.sync_offline_max_storage_cap,
                selectedLimit = settingsUiState.offlineMaxStorageCap,
                onDismissRequest = { viewModel.onDismissDialog() },
                onLimitSelected = { limit ->
                    viewModel.onOfflineMaxStorageCapSelected(limit)
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
        onOfflinePolicySelected = { viewModel.onOfflinePolicySelected(it) },
        onClickOfflinePolicyStorageLimit = { viewModel.onClickOfflinePolicyStorageLimit() },
        onClickOfflinePolicyNewestN = { viewModel.onClickOfflinePolicyNewestN() },
        onClickOfflinePolicyDateRangeWindow = { viewModel.onClickOfflinePolicyDateRangeWindow() },
        onClickOfflineMaxStorageCap = { viewModel.onClickOfflineMaxStorageCap() },
        onIncludeArchivedChanged = { viewModel.onIncludeArchivedChanged(it) },
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
    onOfflinePolicySelected: (OfflinePolicy) -> Unit,
    onClickOfflinePolicyStorageLimit: () -> Unit,
    onClickOfflinePolicyNewestN: () -> Unit,
    onClickOfflinePolicyDateRangeWindow: () -> Unit,
    onClickOfflineMaxStorageCap: () -> Unit,
    onIncludeArchivedChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
    onClickClearOfflineContent: () -> Unit,
) {
    val controlsEnabled = !settingsUiState.isPurgingOfflineContent

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
                lastBookmarkSyncTimestamp = settingsUiState.syncStatus.lastBookmarkSyncTimestamp,
                syncStatus = settingsUiState.syncStatus,
                onClickFrequency = onClickBookmarkSyncFrequency,
                onClickSyncBookmarksNow = onClickSyncBookmarksNow
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            OfflineReadingSection(
                uiState = settingsUiState,
                onOfflineReadingChanged = onOfflineReadingChanged,
                controlsEnabled = controlsEnabled,
                onOfflinePolicySelected = onOfflinePolicySelected,
                onClickOfflinePolicyStorageLimit = onClickOfflinePolicyStorageLimit,
                onClickOfflinePolicyNewestN = onClickOfflinePolicyNewestN,
                onClickOfflinePolicyDateRangeWindow = onClickOfflinePolicyDateRangeWindow,
                onClickOfflineMaxStorageCap = onClickOfflineMaxStorageCap,
                onIncludeArchivedChanged = onIncludeArchivedChanged,
                onWifiOnlyChanged = onWifiOnlyChanged,
                onAllowBatterySaverChanged = onAllowBatterySaverChanged,
                onClickClearOfflineContent = onClickClearOfflineContent
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ContentSyncStatusIndicator(
    contentSyncStatusRes: Int?,
    isPurgingOfflineContent: Boolean,
    modifier: Modifier = Modifier
) {
    val (statusText, statusIcon, isProgress) = when {
        isPurgingOfflineContent -> {
            Triple(
                stringResource(R.string.sync_content_status_clearing),
                null,
                true
            )
        }
        contentSyncStatusRes == R.string.sync_content_status_downloading_text -> {
            Triple(
                stringResource(R.string.sync_content_status_downloading_text),
                null,
                true
            )
        }
        contentSyncStatusRes == R.string.sync_content_status_up_to_date -> {
            Triple(
                stringResource(R.string.sync_content_status_up_to_date),
                Icons.Filled.Check,
                false
            )
        }
        contentSyncStatusRes == R.string.sync_content_waiting_wifi -> {
            Triple(
                stringResource(R.string.sync_content_waiting_wifi),
                Icons.Filled.WifiOff,
                false
            )
        }
        contentSyncStatusRes == R.string.sync_content_waiting_battery -> {
            Triple(
                stringResource(R.string.sync_content_waiting_battery),
                Icons.Filled.BatteryAlert,
                false
            )
        }
        else -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (isProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                statusIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (contentSyncStatusRes == R.string.sync_content_status_up_to_date) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    contentSyncStatusRes == R.string.sync_content_status_up_to_date ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                    contentSyncStatusRes == R.string.sync_content_waiting_wifi ||
                    contentSyncStatusRes == R.string.sync_content_waiting_battery ->
                        MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
private fun CompactStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                style = MaterialTheme.typography.bodyMedium,
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
    lastBookmarkSyncTimestamp: String?,
    syncStatus: SyncStatus,
    onClickFrequency: () -> Unit,
    onClickSyncBookmarksNow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.sync_bookmark_section_title),
            description = stringResource(R.string.sync_bookmark_description)
        )

        // Sync frequency and timestamps grouped tightly — no extra gap between the
        // ListItem's internal padding and the supplementary sync time rows
        Column {
            ListItem(
                modifier = Modifier.clickable(onClick = onClickFrequency),
                headlineContent = {
                    Text(text = stringResource(R.string.sync_bookmark_frequency_label))
                },
                trailingContent = {
                    Text(
                        text = stringResource(frequency.toLabelResource()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.sync_status_last_sync,
                        lastBookmarkSyncTimestamp ?: stringResource(R.string.sync_status_never_short)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.sync_status_next_sync,
                        nextRun ?: stringResource(R.string.auto_sync_next_run_null)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bookmarks — same heading+stats pattern as Storage
        Column {
            Text(
                text = stringResource(R.string.sync_status_bookmarks_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompactStatRow(
                    label = stringResource(R.string.sync_status_my_list),
                    value = syncStatus.myListBookmarks.toString()
                )
                CompactStatRow(
                    label = stringResource(R.string.sync_status_archived),
                    value = syncStatus.archivedBookmarks.toString()
                )
                CompactStatRow(
                    label = stringResource(R.string.sync_status_favorites_label),
                    value = syncStatus.favorites.toString()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
    controlsEnabled: Boolean,
    onOfflinePolicySelected: (OfflinePolicy) -> Unit,
    onClickOfflinePolicyStorageLimit: () -> Unit,
    onClickOfflinePolicyNewestN: () -> Unit,
    onClickOfflinePolicyDateRangeWindow: () -> Unit,
    onClickOfflineMaxStorageCap: () -> Unit,
    onIncludeArchivedChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
    onClickClearOfflineContent: () -> Unit
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
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingContent = {
                Switch(
                    checked = uiState.offlineReadingEnabled,
                    onCheckedChange = onOfflineReadingChanged,
                    enabled = controlsEnabled
                )
            }
        )

        AnimatedVisibility(
            visible = uiState.offlineReadingEnabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Status indicator
                ContentSyncStatusIndicator(
                    contentSyncStatusRes = uiState.contentSyncStatusRes,
                    isPurgingOfflineContent = uiState.isPurgingOfflineContent,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.sync_offline_what_to_keep),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Policy options with reduced spacing
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OfflinePolicyRow(
                        selected = uiState.offlinePolicy == OfflinePolicy.STORAGE_LIMIT,
                        title = stringResource(R.string.sync_offline_policy_storage_limit),
                        value = stringResource(uiState.offlinePolicyStorageLimit.toLabelResource()),
                        enabled = controlsEnabled,
                        onClick = {
                            onOfflinePolicySelected(OfflinePolicy.STORAGE_LIMIT)
                            onClickOfflinePolicyStorageLimit()
                        }
                    )

                    OfflinePolicyRow(
                        selected = uiState.offlinePolicy == OfflinePolicy.NEWEST_N,
                        title = stringResource(R.string.sync_offline_policy_newest_n),
                        value = uiState.offlinePolicyNewestN.toString(),
                        enabled = controlsEnabled,
                        onClick = {
                            onOfflinePolicySelected(OfflinePolicy.NEWEST_N)
                            onClickOfflinePolicyNewestN()
                        }
                    )

                    OfflinePolicyRow(
                        selected = uiState.offlinePolicy == OfflinePolicy.DATE_RANGE,
                        title = stringResource(R.string.sync_offline_policy_date_range),
                        value = stringResource(uiState.offlinePolicyDateRangeWindow.toLabelResource()),
                        enabled = controlsEnabled,
                        onClick = {
                            onOfflinePolicySelected(OfflinePolicy.DATE_RANGE)
                            onClickOfflinePolicyDateRangeWindow()
                        }
                    )

                    // Maximum storage cap - indented with policies
                    AnimatedVisibility(
                        visible = uiState.offlinePolicy != OfflinePolicy.STORAGE_LIMIT,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ListItem(
                            modifier = Modifier.clickable(
                                enabled = controlsEnabled,
                                onClick = onClickOfflineMaxStorageCap
                            ),
                            headlineContent = {
                                Text(text = stringResource(R.string.sync_offline_max_storage_cap))
                            },
                            trailingContent = {
                                Text(
                                    text = stringResource(uiState.offlineMaxStorageCap.toLabelResource()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                // Include archived bookmarks as part of "What to keep offline"
                Spacer(modifier = Modifier.height(4.dp))
                TogglePreferenceRow(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    title = stringResource(R.string.sync_include_archived_bookmarks),
                    checked = uiState.includeArchivedBookmarks,
                    enabled = controlsEnabled,
                    onCheckedChange = onIncludeArchivedChanged
                )

                Text(
                    text = stringResource(R.string.sync_offline_whether_to_download),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                TogglePreferenceRow(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    title = stringResource(R.string.sync_wifi_only),
                    checked = uiState.wifiOnly,
                    enabled = controlsEnabled,
                    onCheckedChange = onWifiOnlyChanged
                )
                TogglePreferenceRow(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    title = stringResource(R.string.sync_allow_battery_saver),
                    checked = uiState.allowBatterySaver,
                    enabled = controlsEnabled,
                    onCheckedChange = onAllowBatterySaverChanged
                )

                Text(
                    text = stringResource(R.string.sync_storage_heading),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactStatRow(
                        label = stringResource(R.string.sync_status_available_offline),
                        value = uiState.syncStatus.fullOfflineAvailable.toString()
                    )
                    CompactStatRow(
                        label = stringResource(R.string.sync_status_offline_storage_used),
                        value = uiState.syncStatus.offlineStorageSize ?: "0 B"
                    )
                    CompactStatRow(
                        label = stringResource(R.string.sync_status_last_content_sync_label),
                        value = uiState.syncStatus.lastOfflineMaintenanceTimestamp
                            ?: stringResource(R.string.sync_status_never_short)
                    )
                }

                OutlinedButton(
                    onClick = onClickClearOfflineContent,
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (uiState.isPurgingOfflineContent) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.sync_offline_purging))
                    } else {
                        Text(stringResource(R.string.sync_storage_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePolicyRow(
    selected: Boolean,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun TogglePreferenceRow(
    modifier: Modifier = Modifier,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun StorageLimitDialog(
    titleRes: Int,
    selectedLimit: OfflineImageStorageLimit,
    onDismissRequest: () -> Unit,
    onLimitSelected: (OfflineImageStorageLimit) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_100_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_100,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_100) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_250_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_250,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_250) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_500_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_500,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_500) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_1_gb),
                    selected = selectedLimit == OfflineImageStorageLimit.GB_1,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.GB_1) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_unlimited),
                    selected = selectedLimit == OfflineImageStorageLimit.UNLIMITED,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.UNLIMITED) }
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
private fun IntSelectionDialog(
    titleRes: Int,
    options: List<IntSelectionOption>,
    selectedValue: Int,
    onDismissRequest: () -> Unit,
    onOptionSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                options.forEach { option ->
                    OfflineSelectionOption(
                        label = stringResource(option.label),
                        selected = option.value == selectedValue,
                        onClick = { onOptionSelected(option.value) }
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
private fun DurationSelectionDialog(
    titleRes: Int,
    options: List<DurationSelectionOption>,
    selectedValue: kotlin.time.Duration,
    onDismissRequest: () -> Unit,
    onOptionSelected: (kotlin.time.Duration) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                options.forEach { option ->
                    OfflineSelectionOption(
                        label = stringResource(option.label),
                        selected = option.value == selectedValue,
                        onClick = { onOptionSelected(option.value) }
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
private fun OfflineSelectionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp)
        )
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

private fun kotlin.time.Duration.toLabelResource(): Int {
    return when (this.inWholeDays) {
        7L -> R.string.sync_offline_date_range_1_week
        30L -> R.string.sync_offline_date_range_1_month
        90L -> R.string.sync_offline_date_range_3_months
        180L -> R.string.sync_offline_date_range_6_months
        365L -> R.string.sync_offline_date_range_1_year
        else -> R.string.sync_offline_date_range_3_months
    }
}

object SyncSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
}
