package com.mydeck.app.ui.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mydeck.app.R
import com.mydeck.app.util.openUrlInCustomTab

private const val TAILSCALE_SERVE_URL = "https://tailscale.com/docs/features/tailscale-serve"
private const val READECK_PROXY_DOCS_URL = "https://readeck.org/en/docs/configuration"
private const val MYDECK_RELEASES_URL = "https://github.com/NateEaton/mydeck-android/releases/latest"

@Composable
fun HttpUrlMigrationScreen(
    viewModel: HttpUrlMigrationViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    HttpUrlMigrationScreenContent(
        uiState = uiState,
        onReplacementUrlChanged = viewModel::updateReplacementUrl,
        onSaveReplacementUrl = viewModel::saveReplacementUrl,
        onInstallHttpEnabled = { openUrlInCustomTab(context, MYDECK_RELEASES_URL) },
        onOpenTailscaleServe = { openUrlInCustomTab(context, TAILSCALE_SERVE_URL) },
        onOpenReadeckProxyDocs = { openUrlInCustomTab(context, READECK_PROXY_DOCS_URL) },
    )
}

@Composable
fun HttpUrlMigrationScreenContent(
    uiState: HttpUrlMigrationViewModel.UiState,
    onReplacementUrlChanged: (String) -> Unit,
    onSaveReplacementUrl: () -> Unit,
    onInstallHttpEnabled: () -> Unit,
    onOpenTailscaleServe: () -> Unit,
    onOpenReadeckProxyDocs: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.http_migration_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.http_migration_body, uiState.savedUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                MigrationSection(
                    title = stringResource(R.string.http_migration_https_title),
                    body = stringResource(R.string.http_migration_https_body)
                ) {
                    OutlinedTextField(
                        value = uiState.replacementUrl,
                        onValueChange = onReplacementUrlChanged,
                        label = { Text(stringResource(R.string.account_settings_url_label)) },
                        placeholder = { Text(stringResource(R.string.account_settings_url_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isBusy,
                        isError = uiState.urlError != null,
                        supportingText = {
                            uiState.urlError?.let { Text(stringResource(it)) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onSaveReplacementUrl()
                            }
                        )
                    )
                    Button(
                        onClick = onSaveReplacementUrl,
                        enabled = !uiState.isBusy && uiState.urlError == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.http_migration_https_action))
                    }
                    LinkButtonRow(
                        onOpenTailscaleServe = onOpenTailscaleServe,
                        onOpenReadeckProxyDocs = onOpenReadeckProxyDocs
                    )
                }
            }

            item {
                MigrationSection(
                    title = stringResource(R.string.http_migration_http_apk_title),
                    body = stringResource(R.string.http_migration_http_apk_body)
                ) {
                    OutlinedButton(
                        onClick = onInstallHttpEnabled,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.http_migration_http_apk_action))
                    }
                }
            }

            if (uiState.actionError != null) {
                item {
                    Text(
                        text = stringResource(uiState.actionError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationSection(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun LinkButtonRow(
    onOpenTailscaleServe: () -> Unit,
    onOpenReadeckProxyDocs: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onOpenTailscaleServe, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Row {
                Text(stringResource(R.string.http_migration_tailscale_link))
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                )
            }
        }
        TextButton(onClick = onOpenReadeckProxyDocs, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Row {
                Text(stringResource(R.string.http_migration_readeck_proxy_link))
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                )
            }
        }
    }
}
