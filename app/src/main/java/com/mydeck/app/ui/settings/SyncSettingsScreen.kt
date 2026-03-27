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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangePreset
import com.mydeck.app.ui.settings.composables.DateRangePresetDropdown
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

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

    // Handle dialogs
    when (settingsUiState.showDialog) {
        SyncSettingsDialog.BookmarkSyncFrequencyDialog -> {
            AutoSyncTimeframeDialog(
                autoSyncTimeframeOptions = settingsUiState.bookmarkSyncFrequencyOptions,
                onDismissRequest = { viewModel.onDismissDialog() },
                onElementSelected = { viewModel.onBookmarkSyncFrequencySelected(it) }
            )
        }
        SyncSettingsDialog.BackgroundRationaleDialog,
        SyncSettingsDialog.PermissionRequest -> {
            // Permission dialogs are currently disabled
        }
        SyncSettingsDialog.DateFromPicker -> {
            DatePickerDialogWrapper(
                initialDate = settingsUiState.dateRangeFrom,
                onDateSelected = { viewModel.onDateRangeFromSelected(it) },
                onDismiss = { viewModel.onDismissDialog() }
            )
        }
        SyncSettingsDialog.DateToPicker -> {
            DatePickerDialogWrapper(
                initialDate = settingsUiState.dateRangeTo,
                onDateSelected = { viewModel.onDateRangeToSelected(it) },
                onDismiss = { viewModel.onDismissDialog() }
            )
        }
        SyncSettingsDialog.ConstraintOverrideDialog -> {
            ConstraintOverrideConfirmDialog(
                onConfirm = { viewModel.onConstraintOverrideConfirmed() },
                onCancel = { viewModel.onConstraintOverrideCancelled() }
            )
        }
        SyncSettingsDialog.ClearOfflineContentDialog -> {
            ClearOfflineContentConfirmDialog(
                onConfirm = { viewModel.onConfirmClearOfflineContent() },
                onCancel = { viewModel.onDismissDialog() }
            )
        }
        null -> { /* noop */ }
    }

    SyncSettingsView(
        snackbarHostState = snackbarHostState,
        settingsUiState = settingsUiState,
        onClickBack = { viewModel.onClickBack() },
        onClickBookmarkSyncFrequency = { viewModel.onClickBookmarkSyncFrequency() },
        onClickSyncBookmarksNow = { viewModel.onClickSyncBookmarksNow() },
        onContentSyncModeSelected = { viewModel.onContentSyncModeSelected(it) },
        onDateRangePresetSelected = { viewModel.onDateRangePresetSelected(it) },
        onClickDateFrom = { viewModel.onShowDialog(SyncSettingsDialog.DateFromPicker) },
        onClickDateTo = { viewModel.onShowDialog(SyncSettingsDialog.DateToPicker) },
        onClickDateRangeDownload = { viewModel.onClickDateRangeDownload() },
        onDownloadImagesChanged = { viewModel.onDownloadImagesChanged(it) },
        onIncludeArchivedContentChanged = { viewModel.onIncludeArchivedContentChanged(it) },
        onWifiOnlyChanged = { viewModel.onWifiOnlyChanged(it) },
        onAllowBatterySaverChanged = { viewModel.onAllowBatterySaverChanged(it) },
        onClearContentOnArchiveChanged = { viewModel.onClearContentOnArchiveChanged(it) },
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
    onContentSyncModeSelected: (ContentSyncMode) -> Unit,
    onDateRangePresetSelected: (DateRangePreset) -> Unit,
    onClickDateFrom: () -> Unit,
    onClickDateTo: () -> Unit,
    onClickDateRangeDownload: () -> Unit,
    onDownloadImagesChanged: (Boolean) -> Unit,
    onIncludeArchivedContentChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
    onClearContentOnArchiveChanged: (Boolean) -> Unit,
    onClickClearOfflineContent: () -> Unit = {},
) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ━━━ Section 1: Content Downloads ━━━
            Text(
                text = stringResource(R.string.sync_content_section_title),
                style = MaterialTheme.typography.titleMedium
            )

            // Segmented button: On demand | Automatic
            val isOnDemand = settingsUiState.contentSyncMode == ContentSyncMode.MANUAL ||
                    settingsUiState.contentSyncMode == ContentSyncMode.DATE_RANGE
            val modes = listOf(
                stringResource(R.string.sync_content_on_demand) to true,
                stringResource(R.string.sync_content_automatic) to false
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                modes.forEachIndexed { index, (label, isOnDemandOption) ->
                    SegmentedButton(
                        selected = isOnDemand == isOnDemandOption,
                        onClick = {
                            onContentSyncModeSelected(
                                if (isOnDemandOption) ContentSyncMode.MANUAL else ContentSyncMode.AUTOMATIC
                            )
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size
                        )
                    ) {
                        Text(label)
                    }
                }
            }

            // Contextual hint
            Text(
                text = if (isOnDemand) {
                    stringResource(R.string.sync_content_on_demand_desc)
                } else {
                    stringResource(R.string.sync_content_automatic_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Content sync status line (for Automatic mode constraint feedback)
            if (!isOnDemand) {
                settingsUiState.contentSyncStatusRes?.let { statusRes ->
                    if (statusRes != R.string.sync_content_status_manual) {
                        Text(
                            text = stringResource(statusRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusRes == R.string.sync_content_status_up_to_date)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Download for offline reading section (only when On demand)
            AnimatedVisibility(
                visible = isOnDemand,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sync_content_download_for_offline),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Date range preset dropdown
                    DateRangePresetDropdown(
                        selectedPreset = settingsUiState.dateRangePreset,
                        onPresetSelected = onDateRangePresetSelected,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Custom date pickers (shown only when CUSTOM is selected)
                    if (settingsUiState.dateRangePreset == DateRangePreset.CUSTOM) {
                        // From date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sync_content_date_from),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(48.dp)
                            )
                            OutlinedButton(
                                onClick = onClickDateFrom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(settingsUiState.dateRangeFrom?.toString() ?: "Select date")
                            }
                        }

                        // To date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sync_content_date_to),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(48.dp)
                            )
                            OutlinedButton(
                                onClick = onClickDateTo,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(settingsUiState.dateRangeTo?.toString() ?: "Select date")
                            }
                        }
                    }

                    // Start Download button
                    Button(
                        onClick = onClickDateRangeDownload,
                        enabled = settingsUiState.dateRangeFrom != null &&
                                settingsUiState.dateRangeTo != null &&
                                !settingsUiState.isDateRangeDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (settingsUiState.isDateRangeDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sync_content_downloading))
                        } else {
                            Text(stringResource(R.string.sync_content_download_button))
                        }
                    }
                }
            }

            // Download images toggle
            ListItem(
                headlineContent = {
                    Text(text = stringResource(R.string.sync_download_images))
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.sync_download_images_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settingsUiState.downloadImages,
                        onCheckedChange = onDownloadImagesChanged
                    )
                }
            )

            // Include archived content toggle
            ListItem(
                headlineContent = {
                    Text(text = stringResource(R.string.sync_include_archived))
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.sync_include_archived_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settingsUiState.includeArchivedContent,
                        onCheckedChange = onIncludeArchivedContentChanged
                    )
                }
            )

            // ▸ Download constraints (collapsible)
            CollapsibleSection(
                title = stringResource(R.string.sync_constraints_section_title),
                initiallyExpanded = false
            ) {
                ConstraintsSection(
                    wifiOnly = settingsUiState.wifiOnly,
                    allowBatterySaver = settingsUiState.allowBatterySaver,
                    onWifiOnlyChanged = onWifiOnlyChanged,
                    onAllowBatterySaverChanged = onAllowBatterySaverChanged
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ━━━ Section 2: Storage (collapsible) ━━━
            CollapsibleSection(
                title = stringResource(R.string.sync_storage_heading),
                initiallyExpanded = false
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sync_storage_usage, settingsUiState.syncStatus.offlineStorageSize ?: "…"),
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Auto-clear on archive toggle
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.sync_clear_on_archive))
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.sync_clear_on_archive_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settingsUiState.clearContentOnArchive,
                                onCheckedChange = onClearContentOnArchiveChanged
                            )
                        }
                    )

                    OutlinedButton(
                        onClick = onClickClearOfflineContent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_storage_clear))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ━━━ Section 3: Bookmark Sync (collapsible) ━━━
            CollapsibleSection(
                title = stringResource(R.string.sync_bookmark_section_title),
                subtitle = stringResource(R.string.sync_bookmark_description),
                initiallyExpanded = false
            ) {
                BookmarkSyncSection(
                    frequency = settingsUiState.bookmarkSyncFrequency,
                    nextRun = settingsUiState.nextAutoSyncRun,
                    isSyncRunning = settingsUiState.isBookmarkSyncRunning,
                    onClickFrequency = onClickBookmarkSyncFrequency,
                    onClickSyncBookmarksNow = onClickSyncBookmarksNow
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ━━━ Section 4: Status (collapsible) ━━━
            CollapsibleSection(
                title = stringResource(R.string.sync_status_section_title),
                initiallyExpanded = false
            ) {
                SyncStatusSection(syncStatus = settingsUiState.syncStatus)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// --- Collapsible Section ---
@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            content()
        }
    }
}

// --- Bookmark Sync Section ---
@Composable
private fun BookmarkSyncSection(
    frequency: AutoSyncTimeframe,
    nextRun: String?,
    isSyncRunning: Boolean,
    onClickFrequency: () -> Unit,
    onClickSyncBookmarksNow: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sync_settings_sync_running))
            } else {
                Text(stringResource(R.string.sync_settings_sync_bookmarks_now))
            }
        }
    }
}

// --- Constraints Section ---
@Composable
private fun ConstraintsSection(
    wifiOnly: Boolean,
    allowBatterySaver: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit
) {
    Column {
        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.sync_wifi_only))
            },
            trailingContent = {
                Switch(
                    checked = wifiOnly,
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
                    checked = allowBatterySaver,
                    onCheckedChange = onAllowBatterySaverChanged
                )
            }
        )
    }
}

// --- Sync Status Section ---
@Composable
private fun SyncStatusSection(syncStatus: SyncStatus) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
            // Bookmark counts
            Text(
                text = stringResource(R.string.sync_status_bookmarks_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
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

            syncStatus.lastBookmarkSyncTimestamp?.let { ts ->
                Text(
                    text = stringResource(R.string.sync_status_last_sync, ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content counts
            Text(
                text = stringResource(R.string.sync_status_content_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.sync_status_content_downloaded, syncStatus.contentDownloaded),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_available, syncStatus.contentAvailable),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_dirty, syncStatus.contentDirty),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_no_content, syncStatus.permanentNoContent),
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


// --- Clear Offline Content Confirm Dialog ---
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

// --- Date Picker Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogWrapper(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val instant = Instant.fromEpochMilliseconds(millis)
                    val localDate = instant.toLocalDateTime(TimeZone.UTC).date
                    onDateSelected(localDate)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

// --- Background Sync Rationale Dialog ---
@Composable
private fun BackgroundSyncRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.background_sync_rationale_title)) },
        text = { Text(stringResource(R.string.background_sync_rationale_body)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.background_sync_rationale_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- Constraint Override Dialog ---
@Composable
private fun ConstraintOverrideConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_constraint_override_title)) },
        text = { Text(stringResource(R.string.sync_constraint_override_body, "")) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.sync_constraint_override_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

object SyncSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
}
