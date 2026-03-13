package com.mydeck.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.mydeck.app.R
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
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
    val onLightAppearanceSelected: (LightAppearance) -> Unit = { viewModel.onLightAppearanceSelected(it) }
    val onDarkAppearanceSelected: (DarkAppearance) -> Unit = { viewModel.onDarkAppearanceSelected(it) }
    val onKeepScreenOnWhileReadingToggled: (Boolean) -> Unit = { viewModel.onKeepScreenOnWhileReadingToggled(it) }
    val onFullscreenWhileReadingToggled: (Boolean) -> Unit = { viewModel.onFullscreenWhileReadingToggled(it) }
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
        onLightAppearanceSelected = onLightAppearanceSelected,
        onDarkAppearanceSelected = onDarkAppearanceSelected,
        onKeepScreenOnWhileReadingToggled = onKeepScreenOnWhileReadingToggled,
        onFullscreenWhileReadingToggled = onFullscreenWhileReadingToggled,
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
    onLightAppearanceSelected: (LightAppearance) -> Unit,
    onDarkAppearanceSelected: (DarkAppearance) -> Unit,
    onKeepScreenOnWhileReadingToggled: (Boolean) -> Unit,
    onFullscreenWhileReadingToggled: (Boolean) -> Unit,
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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
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

            AppearanceSelectionSection(
                title = stringResource(R.string.ui_settings_light_appearance_title)
            ) {
                AppearanceOptionCard(
                    label = stringResource(LightAppearance.PAPER.toLabelResource()),
                    selected = settingsUiState.lightAppearance == LightAppearance.PAPER,
                    previewBackground = Color(0xFFF9F9F9),
                    previewForeground = Color(0xFF3B3A36),
                    onClick = { onLightAppearanceSelected(LightAppearance.PAPER) }
                )
                AppearanceOptionCard(
                    label = stringResource(LightAppearance.SEPIA.toLabelResource()),
                    selected = settingsUiState.lightAppearance == LightAppearance.SEPIA,
                    previewBackground = Color(0xFFF4ECD8),
                    previewForeground = Color(0xFF4A3B2B),
                    onClick = { onLightAppearanceSelected(LightAppearance.SEPIA) }
                )
            }

            AppearanceSelectionSection(
                title = stringResource(R.string.ui_settings_dark_appearance_title)
            ) {
                AppearanceOptionCard(
                    label = stringResource(DarkAppearance.DARK.toLabelResource()),
                    selected = settingsUiState.darkAppearance == DarkAppearance.DARK,
                    previewBackground = Color(0xFF222222),
                    previewForeground = Color(0xFFD0CCC4),
                    onClick = { onDarkAppearanceSelected(DarkAppearance.DARK) }
                )
                AppearanceOptionCard(
                    label = stringResource(DarkAppearance.BLACK.toLabelResource()),
                    selected = settingsUiState.darkAppearance == DarkAppearance.BLACK,
                    previewBackground = Color(0xFF000000),
                    previewForeground = Color(0xFFF5F5F5),
                    onClick = { onDarkAppearanceSelected(DarkAppearance.BLACK) }
                )
            }

            ListItem(
                modifier = Modifier
                    .testTag(UiSettingsScreenTestTags.FULLSCREEN_READING_ROW)
                    .clickable {
                        onFullscreenWhileReadingToggled(!settingsUiState.fullscreenWhileReading)
                    },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_fullscreen_reading_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_fullscreen_reading_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settingsUiState.fullscreenWhileReading,
                        onCheckedChange = onFullscreenWhileReadingToggled
                    )
                }
            )

            // Keep screen on while reading toggle
            ListItem(
                modifier = Modifier
                    .testTag(UiSettingsScreenTestTags.KEEP_SCREEN_ON_ROW)
                    .clickable { onKeepScreenOnWhileReadingToggled(!settingsUiState.keepScreenOnWhileReading) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_keep_screen_on_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.ui_settings_keep_screen_on_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settingsUiState.keepScreenOnWhileReading,
                        onCheckedChange = onKeepScreenOnWhileReadingToggled
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
        lightAppearance = LightAppearance.PAPER,
        darkAppearance = DarkAppearance.DARK,
        themeOptions = listOf(),
        showDialog = false,
        themeLabel = Theme.SYSTEM.toLabelResource(),
        keepScreenOnWhileReading = true,
        fullscreenWhileReading = false,
    )
    UiSettingsView(
        modifier = Modifier,
        snackbarHostState = SnackbarHostState(),
        onClickBack = {},
        onThemeModeSelected = {},
        onLightAppearanceSelected = {},
        onDarkAppearanceSelected = {},
        onKeepScreenOnWhileReadingToggled = {},
        onFullscreenWhileReadingToggled = {},
        settingsUiState = settingsUiState
    )
}

@Composable
private fun AppearanceSelectionSection(
    title: String,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.AppearanceOptionCard(
    label: String,
    selected: Boolean,
    previewBackground: Color,
    previewForeground: Color,
    onClick: () -> Unit,
    minHeight: Dp = 84.dp,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        color = containerColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .padding(0.dp)
                    .background(previewBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(previewForeground.copy(alpha = 0.85f))
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(previewForeground.copy(alpha = 0.55f))
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

object UiSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
    const val KEEP_SCREEN_ON_ROW = "UiSettingsScreenTestTags.KeepScreenOnRow"
    const val FULLSCREEN_READING_ROW = "UiSettingsScreenTestTags.FullscreenReadingRow"
}
