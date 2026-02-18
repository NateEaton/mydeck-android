package com.mydeck.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.mydeck.app.R
import com.mydeck.app.domain.model.Theme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun UiSettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: UiSettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onThemeModeSelected: (Theme) -> Unit = { viewModel.onThemeModeSelected(it) }
    val onSepiaToggled: (Boolean) -> Unit = { viewModel.onSepiaToggled(it) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is UiSettingsViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
            }
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }

    UiSettingsView(
        modifier = Modifier,
        snackbarHostState = snackbarHostState,
        onClickBack = onClickBack,
        onThemeModeSelected = onThemeModeSelected,
        onSepiaToggled = onSepiaToggled,
        settingsUiState = settingsUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiSettingsView(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    settingsUiState: UiSettingsUiState,
    onThemeModeSelected: (Theme) -> Unit,
    onSepiaToggled: (Boolean) -> Unit,
    onClickBack: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_settings_topbar_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClickBack,
                        modifier = Modifier.testTag(UiSettingsScreenTestTags.BACK_BUTTON)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme mode selection (Light/Dark/System)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_settings_theme_title),
                    style = MaterialTheme.typography.titleMedium
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val themeModes = listOf(Theme.LIGHT, Theme.DARK, Theme.SYSTEM)
                    themeModes.forEachIndexed { index, theme ->
                        SegmentedButton(
                            selected = settingsUiState.themeMode == theme,
                            onClick = { onThemeModeSelected(theme) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = themeModes.size
                            )
                        ) {
                            Text(stringResource(theme.toLabelResource()))
                        }
                    }
                }
            }

            // Sepia theme toggle (applies when effective theme is Light)
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_sepia_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_sepia_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settingsUiState.useSepiaInLight,
                        onCheckedChange = onSepiaToggled
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UiSettingsScreenViewPreview() {
    val settingsUiState = UiSettingsUiState(
        themeMode = Theme.SYSTEM,
        useSepiaInLight = false,
        themeOptions = listOf(),
        showDialog = false,
        themeLabel = Theme.SYSTEM.toLabelResource(),
    )
    UiSettingsView(
        modifier = Modifier,
        snackbarHostState = SnackbarHostState(),
        onClickBack = {},
        onThemeModeSelected = {},
        onSepiaToggled = {},
        settingsUiState = settingsUiState
    )
}

object UiSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
}
