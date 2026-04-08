package com.mydeck.app.ui.about

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.BuildConfig
import com.mydeck.app.R
import com.mydeck.app.domain.model.CachedServerInfo
import com.mydeck.app.ui.components.MyDeckBrandHeader
import com.mydeck.app.ui.navigation.OpenSourceLibrariesRoute
import com.mydeck.app.util.openUrlInCustomTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AboutScreen(navHostController: NavHostController, showBackButton: Boolean = true) {
    val viewModel: AboutViewModel = hiltViewModel()
    val uiState = viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                AboutViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
                AboutViewModel.NavigationEvent.NavigateToOpenSourceLibraries -> {
                    navHostController.navigate(OpenSourceLibrariesRoute) { launchSingleTop = true }
                }
            }
        }
    }

    val serverUrl = viewModel.serverUrl.collectAsState()
    val context = LocalContext.current
    AboutScreenContent(
        uiState = uiState.value,
        serverUrl = serverUrl.value,
        onBackClick = { viewModel.onClickBack() },
        onOpenSourceLibrariesClick = { viewModel.onClickOpenSourceLibraries() },
        onUrlClick = { url -> openUrlInCustomTab(context, url) },
        showBackButton = showBackButton,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    uiState: AboutViewModel.UiState,
    serverUrl: String? = null,
    onBackClick: () -> Unit,
    onOpenSourceLibrariesClick: () -> Unit,
    onUrlClick: (String) -> Unit,
    showBackButton: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header
            MyDeckBrandHeader(iconSize = 96.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Description
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Credits Section
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_credits_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_credits_app_author),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_credits_readeck),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            // FORK_INFO_START - Remove this section if merging back to original repo
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_credits_fork),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            // FORK_INFO_END

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_credits_thanks),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // System Info Section
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_system_info_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // App Info Card
            val appInfoSummary = stringResource(
                R.string.about_system_info_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
            val appBuildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(BuildConfig.BUILD_TIME.toLong()))
            val appAndroid = stringResource(
                R.string.about_system_info_android,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT
            )
            val appDevice = stringResource(
                R.string.about_system_info_device,
                Build.MANUFACTURER,
                Build.MODEL
            )
            val appDetailLines = listOf(
                appInfoSummary,
                stringResource(R.string.about_system_info_build_time, appBuildTime),
                appAndroid,
                appDevice
            )

            CollapsibleInfoCard(
                subheading = stringResource(R.string.about_system_info_app_subtitle),
                summary = appInfoSummary,
                detailLines = appDetailLines,
                clipboardHeader = stringResource(R.string.app_name),
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Server Info Card
            val serverSummary = when {
                uiState.serverInfoLoading && uiState.serverInfo == null -> stringResource(R.string.about_system_info_server_loading)
                uiState.serverInfoError && uiState.serverInfo == null -> stringResource(R.string.about_system_info_server_error)
                uiState.serverInfo == null -> stringResource(R.string.about_system_info_server_unavailable)
                else -> uiState.serverInfo!!.canonical
            }

            val serverDetailLines = if (uiState.serverInfo != null) {
                val build = if (uiState.serverInfo!!.build.isEmpty()) {
                    stringResource(R.string.about_system_info_server_build_stable)
                } else {
                    uiState.serverInfo!!.build
                }
                buildList {
                    if (serverUrl != null) {
                        add(stringResource(R.string.about_system_info_server_url, serverUrl))
                    }
                    add(stringResource(R.string.about_system_info_server_version, uiState.serverInfo!!.canonical))
                    add(stringResource(R.string.about_system_info_server_release, uiState.serverInfo!!.release))
                    add(stringResource(R.string.about_system_info_server_build, build))
                    add(stringResource(R.string.about_system_info_server_features, uiState.serverInfo!!.features.joinToString(", ")))
                }
            } else {
                emptyList()
            }

            CollapsibleInfoCard(
                subheading = stringResource(R.string.about_system_info_server_subtitle),
                summary = serverSummary,
                detailLines = serverDetailLines,
                clipboardHeader = "Readeck",
                isLoading = uiState.serverInfoLoading && uiState.serverInfo == null,
                isError = uiState.serverInfoError && uiState.serverInfo == null,
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Project Links Section
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_project_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // FORK_INFO_START - Remove this section if merging back to original repo
            // Fork Repository
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUrlClick("https://github.com/NateEaton/mydeck-android") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = stringResource(R.string.about_project_this_repo),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // FORK_INFO_END

            // Original Repository
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUrlClick("https://github.com/jensomato/ReadeckApp") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = stringResource(R.string.about_project_original_repo),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Readeck Server
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUrlClick("https://codeberg.org/readeck/readeck") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Text(
                    text = stringResource(R.string.about_project_readeck_repo),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // License Section
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_license_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_license_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.about_license_readeck),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Open Source Libraries Link
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSourceLibrariesClick() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = stringResource(R.string.settings_open_source_libraries),
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.settings_open_source_libraries),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_open_source_libraries_subtitle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CollapsibleInfoCard(
    subheading: String,
    summary: String,
    detailLines: List<String>,
    clipboardHeader: String? = null,
    isLoading: Boolean = false,
    isError: Boolean = false,
    snackbarHostState: SnackbarHostState,
) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { setExpanded(!expanded) }
            .padding(8.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subheading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.about_system_info_server_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // Expanded Details
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                // Detail lines
                detailLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                // Copy button
                if (detailLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val text = if (clipboardHeader != null) "$clipboardHeader\n${detailLines.joinToString("\n")}" else detailLines.joinToString("\n")
                                clipboardManager.setText(AnnotatedString(text))
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier
                                .height(16.dp)
                                .padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.about_system_info_copy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
