package com.mydeck.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.login.DeviceAuthorizationDialog

@Composable
fun AccountSettingsScreen(
    navHostController: NavHostController,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
    padding: PaddingValues = PaddingValues(0.dp)
) {
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val onUrlChanged: (String) -> Unit = { url -> viewModel.updateUrl(url) }
    val onLoginClicked: () -> Unit = { viewModel.login() }
    val onSignOut: () -> Unit = { viewModel.signOut() }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = settingsUiState.url,
                placeholder = { Text(stringResource(R.string.account_settings_url_placeholder)) },
                onValueChange = { onUrlChanged(it) },
                label = { Text(stringResource(R.string.account_settings_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = settingsUiState.urlError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (settingsUiState.loginEnabled) {
                            onLoginClicked()
                        }
                    }
                ),
                supportingText = {
                    settingsUiState.urlError?.let {
                        Text(text = stringResource(it))
                    }
                }
            )
        }

        item {
            LoginButton(
                onClick = onLoginClicked,
                enabled = settingsUiState.loginEnabled && settingsUiState.authStatus !is AccountSettingsViewModel.AuthStatus.Loading,
                authStatus = settingsUiState.authStatus,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error message display
        if (settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.Error) {
            item {
                Text(
                    text = (settingsUiState.authStatus as AccountSettingsViewModel.AuthStatus.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // Sign-out section (only show when logged in)
        if (settingsUiState.isLoggedIn) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }
            item {
                Text(
                    text = stringResource(R.string.account_settings_sign_out_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedButton(
                    onClick = { onSignOut() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text(stringResource(R.string.account_settings_sign_out))
                }
            }
        }
    }

    // OAuth Device Authorization Dialog (outside LazyColumn to fix scoping)
    if (settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.WaitingForAuthorization &&
        settingsUiState.deviceAuthState != null) {
        DeviceAuthorizationDialog(
            userCode = settingsUiState.deviceAuthState!!.userCode,
            verificationUri = settingsUiState.deviceAuthState!!.verificationUri,
            verificationUriComplete = settingsUiState.deviceAuthState!!.verificationUriComplete,
            expiresAt = settingsUiState.deviceAuthState!!.expiresAt,
            onCancel = { viewModel.cancelAuthorization() }
        )
    }

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is AccountSettingsViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
                is AccountSettingsViewModel.NavigationEvent.NavigateToBookmarkList -> {
                    navHostController.navigate(com.mydeck.app.ui.navigation.BookmarkListRoute()) {
                        popUpTo(com.mydeck.app.ui.navigation.AccountSettingsRoute) { inclusive = true }
                    }
                }
            }
            viewModel.navigationEventConsumed() // Consume event
        }
    }
}

@Composable
fun LoginButton(
    onClick: () -> Unit,
    enabled: Boolean,
    authStatus: AccountSettingsViewModel.AuthStatus,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        when (authStatus) {
            is AccountSettingsViewModel.AuthStatus.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Signing in...")
            }
            else -> {
                Text(stringResource(R.string.account_settings_login))
            }
        }
    }
}
