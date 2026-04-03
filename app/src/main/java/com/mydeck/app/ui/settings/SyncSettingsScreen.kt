package com.mydeck.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.RadioButton
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
        onClearContentOnArchiveChanged = { viewModel.onClearContentOnArchiveChanged(it) },
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
    onClearContentOnArchiveChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
    onClickClearOfflineContent: () -> Unit,
) {
    val hasOfflineContent = settingsUiState.syncStatus.fullOfflineAvailable > 0

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
                onOfflinePolicySelected = onOfflinePolicySelected,
                onClickOfflinePolicyStorageLimit = onClickOfflinePolicyStorageLimit,
                onClickOfflinePolicyNewestN = onClickOfflinePolicyNewestN,
                onClickOfflinePolicyDateRangeWindow = onClickOfflinePolicyDateRangeWindow,
                onClickOfflineMaxStorageCap = onClickOfflineMaxStorageCap,
                onIncludeArchivedChanged = onIncludeArchivedChanged,
                onClearContentOnArchiveChanged = onClearContentOnArchiveChanged,
                onWifiOnlyChanged = onWifiOnlyChanged,
                onAllowBatterySaverChanged = onAllowBatterySaverChanged
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SyncStatusSection(
                syncStatus = settingsUiState.syncStatus,
                nextRun = settingsUiState.nextAutoSyncRun,
                showOfflineDetails = settingsUiState.offlineReadingEnabled
            )

            if (settingsUiState.offlineReadingEnabled || hasOfflineContent) {
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
    onOfflinePolicySelected: (OfflinePolicy) -> Unit,
    onClickOfflinePolicyStorageLimit: () -> Unit,
    onClickOfflinePolicyNewestN: () -> Unit,
    onClickOfflinePolicyDateRangeWindow: () -> Unit,
    onClickOfflineMaxStorageCap: () -> Unit,
    onIncludeArchivedChanged: (Boolean) -> Unit,
    onClearContentOnArchiveChanged: (Boolean) -> Unit,
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

                Text(
                    text = stringResource(R.string.sync_offline_keep_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                OfflinePolicyRow(
                    selected = uiState.offlinePolicy == OfflinePolicy.STORAGE_LIMIT,
                    title = stringResource(R.string.sync_offline_policy_storage_limit),
                    value = stringResource(uiState.offlinePolicyStorageLimit.toLabelResource()),
                    onClick = {
                        onOfflinePolicySelected(OfflinePolicy.STORAGE_LIMIT)
                        onClickOfflinePolicyStorageLimit()
                    }
                )

                OfflinePolicyRow(
                    selected = uiState.offlinePolicy == OfflinePolicy.NEWEST_N,
                    title = stringResource(R.string.sync_offline_policy_newest_n),
                    value = uiState.offlinePolicyNewestN.toString(),
                    onClick = {
                        onOfflinePolicySelected(OfflinePolicy.NEWEST_N)
                        onClickOfflinePolicyNewestN()
                    }
                )

                OfflinePolicyRow(
                    selected = uiState.offlinePolicy == OfflinePolicy.DATE_RANGE,
                    title = stringResource(R.string.sync_offline_policy_date_range),
                    value = stringResource(uiState.offlinePolicyDateRangeWindow.toLabelResource()),
                    onClick = {
                        onOfflinePolicySelected(OfflinePolicy.DATE_RANGE)
                        onClickOfflinePolicyDateRangeWindow()
                    }
                )

                AnimatedVisibility(
                    visible = uiState.offlinePolicy != OfflinePolicy.STORAGE_LIMIT,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onClickOfflineMaxStorageCap),
                        headlineContent = {
                            Text(text = stringResource(R.string.sync_offline_max_storage_cap))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.sync_offline_max_storage_cap_desc))
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

                TogglePreferenceRow(
                    title = stringResource(R.string.sync_include_archived_bookmarks),
                    checked = uiState.includeArchivedBookmarks,
                    onCheckedChange = onIncludeArchivedChanged
                )
                TogglePreferenceRow(
                    title = stringResource(R.string.sync_clear_on_archive),
                    checked = uiState.clearContentOnArchive,
                    onCheckedChange = onClearContentOnArchiveChanged
                )
                TogglePreferenceRow(
                    title = stringResource(R.string.sync_wifi_only),
                    checked = uiState.wifiOnly,
                    onCheckedChange = onWifiOnlyChanged
                )
                TogglePreferenceRow(
                    title = stringResource(R.string.sync_allow_battery_saver),
                    checked = uiState.allowBatterySaver,
                    onCheckedChange = onAllowBatterySaverChanged
                )

                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.sync_offline_download_date_range))
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
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    )
}

@Composable
private fun TogglePreferenceRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun SyncStatusSection(
    syncStatus: SyncStatus,
    nextRun: String?,
    showOfflineDetails: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.sync_status_section_title))

        StatusRow(
            title = stringResource(R.string.sync_status_last_bookmark_sync),
            value = syncStatus.lastBookmarkSyncTimestamp
                ?: stringResource(R.string.sync_status_never_short)
        )
        StatusRow(
            title = stringResource(R.string.sync_status_next_bookmark_sync),
            value = nextRun ?: stringResource(R.string.auto_sync_next_run_null)
        )

        AnimatedVisibility(
            visible = showOfflineDetails,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusRow(
                    title = stringResource(R.string.sync_status_total),
                    value = syncStatus.totalBookmarks.toString()
                )
                StatusRow(
                    title = stringResource(R.string.sync_status_my_list),
                    value = syncStatus.myListBookmarks.toString()
                )
                StatusRow(
                    title = stringResource(R.string.sync_status_archived),
                    value = syncStatus.archivedBookmarks.toString()
                )
                StatusRow(
                    title = stringResource(R.string.sync_status_full_offline_available),
                    value = syncStatus.fullOfflineAvailable.toString()
                )
                StatusRow(
                    title = stringResource(R.string.sync_status_offline_storage_used),
                    value = syncStatus.offlineStorageSize ?: "0 B"
                )
                StatusRow(
                    title = stringResource(R.string.sync_status_last_offline_maintenance),
                    value = syncStatus.lastOfflineMaintenanceTimestamp
                        ?: stringResource(R.string.sync_status_never_short)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    value: String
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) }
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
                    label = stringResource(R.string.sync_offline_image_limit_5_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_5,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_5) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_10_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_10,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_10) }
                )
                OfflineSelectionOption(
                    label = stringResource(R.string.sync_offline_image_limit_20_mb),
                    selected = selectedLimit == OfflineImageStorageLimit.MB_20,
                    onClick = { onLimitSelected(OfflineImageStorageLimit.MB_20) }
                )
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
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(text = label) },
            leadingContent = {
                RadioButton(
                    selected = selected,
                    onClick = null
                )
            }
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
