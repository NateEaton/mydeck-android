package com.mydeck.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.domain.sync.ContentSyncMode
import com.mydeck.app.domain.sync.DateRangePreset
import com.mydeck.app.ui.settings.composables.DateRangePresetDropdown
import com.mydeck.app.ui.theme.Typography
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SyncSettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: SyncSettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }?.also {
        viewModel.setPermissionState(it)
    }

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
        SyncSettingsDialog.BackgroundRationaleDialog -> {
            BackgroundSyncRationaleDialog(
                onConfirm = { viewModel.onRationaleDialogConfirm() },
                onDismiss = { viewModel.onDismissDialog() }
            )
        }
        SyncSettingsDialog.PermissionRequest -> {
            SideEffect {
                viewModel.onDismissDialog()
                notificationPermissionState?.launchPermissionRequest()
            }
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
        null -> { /* noop */ }
    }

    SyncSettingsView(
        snackbarHostState = snackbarHostState,
        settingsUiState = settingsUiState,
        onClickBack = { viewModel.onClickBack() },
        onClickBookmarkSyncFrequency = { viewModel.onClickBookmarkSyncFrequency() },
        onContentSyncModeSelected = { viewModel.onContentSyncModeSelected(it) },
        onDateRangePresetSelected = { viewModel.onDateRangePresetSelected(it) },
        onClickDateFrom = { viewModel.onShowDialog(SyncSettingsDialog.DateFromPicker) },
        onClickDateTo = { viewModel.onShowDialog(SyncSettingsDialog.DateToPicker) },
        onClickDateRangeDownload = { viewModel.onClickDateRangeDownload() },
        onWifiOnlyChanged = { viewModel.onWifiOnlyChanged(it) },
        onAllowBatterySaverChanged = { viewModel.onAllowBatterySaverChanged(it) }
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
    onContentSyncModeSelected: (ContentSyncMode) -> Unit,
    onDateRangePresetSelected: (DateRangePreset) -> Unit,
    onClickDateFrom: () -> Unit,
    onClickDateTo: () -> Unit,
    onClickDateRangeDownload: () -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit,
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
            // --- Section 1: Bookmark Sync ---
            Text(
                text = stringResource(R.string.sync_bookmark_section_title),
                style = Typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BookmarkSyncSection(
                        frequency = settingsUiState.bookmarkSyncFrequency,
                        nextRun = settingsUiState.nextAutoSyncRun,
                        onClickFrequency = onClickBookmarkSyncFrequency
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Section 2: Content Sync (with Constraints) ---
            Text(
                text = stringResource(R.string.sync_content_section_title),
                style = Typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Content Sync Mode
                    Text(
                        text = "Content Sync Mode",
                        style = Typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ContentSyncSection(
                        contentSyncMode = settingsUiState.contentSyncMode,
                        dateRangePreset = settingsUiState.dateRangePreset,
                        dateRangeFrom = settingsUiState.dateRangeFrom,
                        dateRangeTo = settingsUiState.dateRangeTo,
                        isDateRangeDownloading = settingsUiState.isDateRangeDownloading,
                        onContentSyncModeSelected = onContentSyncModeSelected,
                        onDateRangePresetSelected = onDateRangePresetSelected,
                        onClickDateFrom = onClickDateFrom,
                        onClickDateTo = onClickDateTo,
                        onClickDateRangeDownload = onClickDateRangeDownload
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Constraints heading
                    Text(
                        text = stringResource(R.string.sync_constraints_section_title),
                        style = Typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ConstraintsSection(
                        wifiOnly = settingsUiState.wifiOnly,
                        allowBatterySaver = settingsUiState.allowBatterySaver,
                        onWifiOnlyChanged = onWifiOnlyChanged,
                        onAllowBatterySaverChanged = onAllowBatterySaverChanged
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Section 3: Sync Status ---
            Text(
                text = stringResource(R.string.sync_status_section_title),
                style = Typography.titleMedium
            )

            SyncStatusSection(syncStatus = settingsUiState.syncStatus)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- Section 1: Bookmark Sync ---
@Composable
private fun BookmarkSyncSection(
    frequency: AutoSyncTimeframe,
    nextRun: String?,
    onClickFrequency: () -> Unit
) {
    Text(
        text = stringResource(R.string.sync_bookmark_description),
        style = Typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickFrequency)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.sync_bookmark_frequency_label))
            val nextRunMsg = nextRun?.let {
                stringResource(R.string.auto_sync_next_run, it)
            } ?: stringResource(R.string.auto_sync_next_run_null)
            Text(
                text = nextRunMsg,
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(frequency.toLabelResource()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// --- Section 2: Content Sync ---
@Composable
private fun ContentSyncSection(
    contentSyncMode: ContentSyncMode,
    dateRangePreset: DateRangePreset,
    dateRangeFrom: LocalDate?,
    dateRangeTo: LocalDate?,
    isDateRangeDownloading: Boolean,
    onContentSyncModeSelected: (ContentSyncMode) -> Unit,
    onDateRangePresetSelected: (DateRangePreset) -> Unit,
    onClickDateFrom: () -> Unit,
    onClickDateTo: () -> Unit,
    onClickDateRangeDownload: () -> Unit
) {
    // Automatic
    ContentSyncRadioOption(
        selected = contentSyncMode == ContentSyncMode.AUTOMATIC,
        title = stringResource(R.string.sync_content_automatic),
        description = stringResource(R.string.sync_content_automatic_desc),
        onClick = { onContentSyncModeSelected(ContentSyncMode.AUTOMATIC) }
    )

    // Manual (now contains Date Range as a sub-option)
    ContentSyncRadioOption(
        selected = contentSyncMode == ContentSyncMode.MANUAL || contentSyncMode == ContentSyncMode.DATE_RANGE,
        title = stringResource(R.string.sync_content_manual),
        description = stringResource(R.string.sync_content_manual_desc),
        onClick = {
            if (contentSyncMode != ContentSyncMode.MANUAL && contentSyncMode != ContentSyncMode.DATE_RANGE) {
                onContentSyncModeSelected(ContentSyncMode.MANUAL)
            }
        }
    )

    // Date range sub-option (shown when Manual is selected)
    if (contentSyncMode == ContentSyncMode.MANUAL || contentSyncMode == ContentSyncMode.DATE_RANGE) {
        Column(
            modifier = Modifier.padding(start = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sub-choice: On demand vs By date range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = contentSyncMode == ContentSyncMode.MANUAL,
                        onClick = { onContentSyncModeSelected(ContentSyncMode.MANUAL) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = contentSyncMode == ContentSyncMode.MANUAL,
                    onClick = null
                )
                Text(
                    text = "On demand",
                    style = Typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = contentSyncMode == ContentSyncMode.DATE_RANGE,
                        onClick = { onContentSyncModeSelected(ContentSyncMode.DATE_RANGE) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = contentSyncMode == ContentSyncMode.DATE_RANGE,
                    onClick = null
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.sync_content_date_range),
                        style = Typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.sync_content_date_range_desc),
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Date range controls (shown when Date Range is selected)
            if (contentSyncMode == ContentSyncMode.DATE_RANGE) {
                Column(
                    modifier = Modifier.padding(start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Preset dropdown
                    DateRangePresetDropdown(
                        selectedPreset = dateRangePreset,
                        onPresetSelected = onDateRangePresetSelected,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Custom date pickers (shown only when CUSTOM is selected)
                    if (dateRangePreset == DateRangePreset.CUSTOM) {
                        // From date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sync_content_date_from),
                                style = Typography.bodyMedium,
                                modifier = Modifier.width(48.dp)
                            )
                            OutlinedButton(
                                onClick = onClickDateFrom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(dateRangeFrom?.toString() ?: "Select date")
                            }
                        }

                        // To date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sync_content_date_to),
                                style = Typography.bodyMedium,
                                modifier = Modifier.width(48.dp)
                            )
                            OutlinedButton(
                                onClick = onClickDateTo,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(dateRangeTo?.toString() ?: "Select date")
                            }
                        }
                    }

                    // Download button
                    Button(
                        onClick = onClickDateRangeDownload,
                        enabled = dateRangeFrom != null && dateRangeTo != null && !isDateRangeDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isDateRangeDownloading) {
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
        }
    }
}

@Composable
private fun ContentSyncRadioOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(top = 2.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = Typography.bodyMedium)
            Text(
                text = description,
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Section 3: Constraints ---
@Composable
private fun ConstraintsSection(
    wifiOnly: Boolean,
    allowBatterySaver: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowBatterySaverChanged: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.sync_wifi_only),
                style = Typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChanged
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.sync_allow_battery_saver),
                style = Typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = allowBatterySaver,
                onCheckedChange = onAllowBatterySaverChanged
            )
        }
    }
}

// --- Section 4: Sync Status ---
@Composable
private fun SyncStatusSection(syncStatus: SyncStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Bookmark counts
            Text(
                text = stringResource(R.string.sync_status_bookmarks_heading),
                style = Typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.sync_status_total, syncStatus.totalBookmarks),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_unread, syncStatus.unread),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_archived, syncStatus.archived),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_favorites, syncStatus.favorites),
                style = Typography.bodySmall
            )

            syncStatus.lastBookmarkSyncTimestamp?.let { ts ->
                Text(
                    text = stringResource(R.string.sync_status_last_sync, ts),
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content counts
            Text(
                text = stringResource(R.string.sync_status_content_heading),
                style = Typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.sync_status_content_downloaded, syncStatus.contentDownloaded),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_available, syncStatus.contentAvailable),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_content_dirty, syncStatus.contentDirty),
                style = Typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_status_no_content, syncStatus.permanentNoContent),
                style = Typography.bodySmall
            )

            syncStatus.lastContentSyncTimestamp?.let { ts ->
                Text(
                    text = stringResource(R.string.sync_status_last_content_sync, ts),
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
        title = { Text("Download Content?") },
        text = { Text("Content download is blocked by active constraints (Wi-Fi only and/or battery saver). Would you like to temporarily override these settings to complete this download?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Override & Download")
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
