package com.mydeck.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.navigation.AccountSettingsRoute
import com.mydeck.app.ui.navigation.LogViewRoute
import com.mydeck.app.ui.navigation.OpenSourceLibrariesRoute
import com.mydeck.app.ui.navigation.SyncSettingsRoute
import com.mydeck.app.ui.navigation.UiSettingsRoute

@Composable
fun SettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val onClickAccount: () -> Unit = { viewModel.onClickAccount() }
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onClickOpenSourceLibraries: () -> Unit = { viewModel.onClickOpenSourceLibraries() }
    val onClickLogs: () -> Unit = { viewModel.onClickLogs() }
    val onClickSync: () -> Unit = { viewModel.onClickSync() }
    val onClickUi: () -> Unit = { viewModel.onClickView() }
    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is SettingsViewModel.NavigationEvent.NavigateToAccountSettings -> {
                    navHostController.navigate(AccountSettingsRoute)
                }
                is SettingsViewModel.NavigationEvent.NavigateToOpenSourceLibraries -> {
                    navHostController.navigate(OpenSourceLibrariesRoute)
                }
                is SettingsViewModel.NavigationEvent.NavigateToLogView -> {
                    navHostController.navigate(LogViewRoute)
                }
                is SettingsViewModel.NavigationEvent.NavigateToSyncView -> {
                    navHostController.navigate(SyncSettingsRoute)
                }
                is SettingsViewModel.NavigationEvent.NavigateToUiSettings -> {
                    navHostController.navigate(UiSettingsRoute)
                }
                is SettingsViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
            }
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }
    SettingScreenView(
        settingsUiState = settingsUiState,
        onClickAccount = onClickAccount,
        onClickBack = onClickBack,
        onClickOpenSourceLibraries = onClickOpenSourceLibraries,
        onClickLogs = onClickLogs,
        onClickSync = onClickSync,
        onClickUi = onClickUi
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenView(
    settingsUiState: SettingsUiState,
    onClickAccount: () -> Unit,
    onClickBack: () -> Unit,
    onClickOpenSourceLibraries: () -> Unit,
    onClickLogs: () -> Unit,
    onClickSync: () -> Unit,
    onClickUi: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag(SettingsScreenTestTags.TOPBAR),
                title = { Text(stringResource(R.string.settings_topbar_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClickBack,
                        modifier = Modifier.testTag(SettingsScreenTestTags.BACK_BUTTON)
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
            modifier = Modifier.padding(padding)
        ) {
            SettingItem(
                icon = Icons.Filled.AccountCircle,
                title = stringResource(R.string.settings_account_title),
                subtitle = settingsUiState.username
                    ?: stringResource(R.string.settings_account_subtitle_default),
                onClick = onClickAccount,
                testTag = SettingsScreenTestTags.SETTINGS_ITEM_ACCOUNT
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingItem(
                icon = Icons.Filled.Sync,
                title = stringResource(R.string.settings_sync),
                subtitle = stringResource(R.string.settings_sync_subtitle),
                onClick = onClickSync,
                testTag = SettingsScreenTestTags.SETTINGS_ITEM_SYNC
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingItem(
                icon = Icons.Filled.Visibility,
                title = stringResource(R.string.settings_ui),
                subtitle = stringResource(R.string.settings_ui_subtitle),
                onClick = onClickUi,
                testTag = SettingsScreenTestTags.SETTINGS_ITEM_UI
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingItem(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_logs),
                subtitle = stringResource(R.string.settings_logs_subtitle),
                onClick = onClickLogs,
                testTag = SettingsScreenTestTags.SETTINGS_ITEM_LOGS
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("${SettingsScreenTestTags.SETTINGS_ITEM}.$testTag"),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("${SettingsScreenTestTags.SETTINGS_ITEM_TITLE}.$testTag")
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("${SettingsScreenTestTags.SETTINGS_ITEM_SUBTITLE}.$testTag")
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = title)
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SettingScreenViewPreview() {
    SettingScreenView(
        settingsUiState = SettingsUiState(
            username = "test",
        ),
        onClickAccount = {},
        onClickBack = {},
        onClickOpenSourceLibraries = {},
        onClickLogs = {},
        onClickSync = {},
        onClickUi = {}
    )
}

@Preview(showBackground = true)
@Composable
fun SettingItemPreview() {
    SettingItem(
        icon = Icons.Filled.Lock,
        title = "test",
        subtitle = "test1",
        onClick = {},
        testTag = "account"
    )
}

object SettingsScreenTestTags {
    const val BACK_BUTTON = "SettingsScreenTestTags.BackButton"
    const val TOPBAR = "SettingsScreenTestTags.TopBar"
    const val SETTINGS_ITEM = "SettingsScreenTestTags.SettingsItem"
    const val SETTINGS_ITEM_TITLE = "SettingsScreenTestTags.SettingsItem.Title"
    const val SETTINGS_ITEM_SUBTITLE = "SettingsScreenTestTags.SettingsItem.Subtitle"
    const val SETTINGS_ITEM_ACCOUNT = "Account"
    const val SETTINGS_ITEM_OPEN_SOURCE = "OpenSource"
    const val SETTINGS_ITEM_LOGS = "Logs"
    const val SETTINGS_ITEM_SYNC = "Sync"
    const val SETTINGS_ITEM_UI = "Ui"
}
