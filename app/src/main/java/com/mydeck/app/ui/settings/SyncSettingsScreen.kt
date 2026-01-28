package com.mydeck.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.mydeck.app.R
import com.mydeck.app.domain.model.AutoSyncTimeframe
import com.mydeck.app.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SyncSettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: SyncSettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onClickDoFullSyncNow: () -> Unit = { viewModel.onClickDoFullSyncNow() }
    val onClickAutoSync: () -> Unit = { viewModel.onClickAutoSync() }
    val onClickAutoSyncSwitch: (value: Boolean) -> Unit =
        { value -> viewModel.onClickAutoSyncSwitch(value) }
    val onClickSyncOnAppOpenSwitch: (value: Boolean) -> Unit =
        { value -> viewModel.onClickSyncOnAppOpenSwitch(value) }
    val onClickSyncNotificationsSwitch: (value: Boolean) -> Unit =
        { value -> viewModel.onClickSyncNotificationsSwitch(value) }
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
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }

    when (settingsUiState.showDialog) {
        Dialog.AutoSyncTimeframeDialog -> {
            AutoSyncTimeframeDialog(
                autoSyncTimeframeOptions = settingsUiState.autoSyncTimeframeOptions,
                onDismissRequest = { viewModel.onDismissDialog() },
                onElementSelected = { viewModel.onAutoSyncTimeframeSelected(it) }
            )
        }
        Dialog.RationaleDialog -> {
            NotificationRationaleDialog(
                onRationaleDialogDismiss = { viewModel.onDismissDialog() },
                onRationaleDialogConfirm = { viewModel.onRationaleDialogConfirm() }
            )
        }
        Dialog.PermissionRequest -> {
            SideEffect {
                viewModel.onDismissDialog()
                notificationPermissionState?.launchPermissionRequest()
            }
        }
        else -> {
            // noop
        }
    }

    SyncSettingsView(
        modifier = Modifier,
        snackbarHostState = snackbarHostState,
        onClickBack = onClickBack,
        onClickDoFullSyncNow = onClickDoFullSyncNow,
        onClickAutoSync = onClickAutoSync,
        onClickAutoSyncSwitch = onClickAutoSyncSwitch,
        onClickSyncOnAppOpenSwitch = onClickSyncOnAppOpenSwitch,
        onClickSyncNotificationsSwitch = onClickSyncNotificationsSwitch,
        settingsUiState = settingsUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsView(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    settingsUiState: SyncSettingsUiState,
    onClickDoFullSyncNow: () -> Unit,
    onClickAutoSync: () -> Unit,
    onClickAutoSyncSwitch: (Boolean) -> Unit,
    onClickSyncOnAppOpenSwitch: (Boolean) -> Unit,
    onClickSyncNotificationsSwitch: (Boolean) -> Unit,
    onClickBack: () -> Unit,
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
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_sync_full_sync_heading),
                style = Typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_sync_support_text),
                style = Typography.bodySmall
            )

            // Background Sync Master Switch
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_sync_auto_full_sync_title))
                }
                Switch(
                    checked = settingsUiState.autoSyncEnabled,
                    onCheckedChange = { onClickAutoSyncSwitch(it) }
                )
            }

            // Grouped Options (Indented)
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Schedule Option
                val contentAlpha = if (settingsUiState.autoSyncEnabled) 1f else 0.5f
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = settingsUiState.autoSyncEnabled, onClick = onClickAutoSync)
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_sync_timeframe_label),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                        )
                         val nextRunMsg = settingsUiState.nextAutoSyncRun?.let {
                            stringResource(
                                R.string.auto_sync_next_run,
                                settingsUiState.nextAutoSyncRun
                            )
                        } ?: stringResource(R.string.auto_sync_next_run_null)
                        Text(
                            text = nextRunMsg,
                            style = Typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                        )
                    }
                    Text(
                        text = stringResource(settingsUiState.autoSyncTimeframeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                    )
                }

                // Notifications Option
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_sync_notifications_title),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_notifications_subtitle),
                            style = Typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                        )
                    }
                    Switch(
                        checked = settingsUiState.syncNotificationsEnabled,
                        onCheckedChange = { onClickSyncNotificationsSwitch(it) },
                        enabled = settingsUiState.autoSyncEnabled
                    )
                }
            }

            // Sync on App Open (Independent)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_sync_on_app_open_title))
                    Text(
                        text = stringResource(R.string.settings_sync_on_app_open_subtitle),
                        style = Typography.bodySmall
                    )
                }
                Row {
                    Switch(
                        checked = settingsUiState.syncOnAppOpenEnabled,
                        onCheckedChange = { onClickSyncOnAppOpenSwitch(it) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sync_status_heading),
                style = Typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.sync_status_bookmarks,
                    settingsUiState.bookmarksWithContent,
                    settingsUiState.totalBookmarks
                ),
                style = Typography.bodySmall
            )
            Text(
                text = settingsUiState.lastSyncTimestamp?.let {
                    stringResource(R.string.sync_status_last_sync, it)
                } ?: stringResource(R.string.sync_status_never),
                style = Typography.bodySmall
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onClickDoFullSyncNow,
                    enabled = settingsUiState.autoSyncButtonEnabled
                ) {
                    Text(stringResource(R.string.settings_sync_auto_full_button))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SyncSettingsScreenViewPreview() {
    val settingsUiState = SyncSettingsUiState(
        autoSyncEnabled = true,
        autoSyncTimeframe = AutoSyncTimeframe.HOURS_12,
        autoSyncTimeframeOptions = listOf(),
        showDialog = null,
        autoSyncTimeframeLabel = AutoSyncTimeframe.HOURS_12.toLabelResource(),
        nextAutoSyncRun = null,
        autoSyncButtonEnabled = false
    )
    SyncSettingsView(
        modifier = Modifier,
        snackbarHostState = SnackbarHostState(),
        onClickBack = {},
        onClickAutoSync = {},
        onClickAutoSyncSwitch = {},
        onClickSyncOnAppOpenSwitch = {},
        onClickSyncNotificationsSwitch = {},
        onClickDoFullSyncNow = {},
        settingsUiState = settingsUiState
    )
}

object SyncSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
}
