package com.mydeck.app.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.login.DeviceAuthorizationScreen
import com.mydeck.app.ui.navigation.BookmarkListRoute
import com.mydeck.app.ui.navigation.WelcomeRoute
import com.mydeck.app.ui.settings.AccountSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    navHostController: NavHostController,
    viewModel: AccountSettingsViewModel = hiltViewModel()
) {
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                is AccountSettingsViewModel.NavigationEvent.NavigateToBookmarkList -> {
                    navHostController.navigate(BookmarkListRoute()) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                }
                is AccountSettingsViewModel.NavigationEvent.NavigateBack -> {
                    // No back from welcome
                }
            }
            viewModel.navigationEventConsumed()
        }
    }

    Scaffold { scaffoldPadding ->
        if (settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.WaitingForAuthorization &&
            settingsUiState.deviceAuthState != null) {
            DeviceAuthorizationScreen(
                userCode = settingsUiState.deviceAuthState!!.userCode,
                verificationUri = settingsUiState.deviceAuthState!!.verificationUri,
                verificationUriComplete = settingsUiState.deviceAuthState!!.verificationUriComplete,
                expiresAt = settingsUiState.deviceAuthState!!.expiresAt,
                onCancel = { viewModel.cancelAuthorization() },
                modifier = Modifier.padding(scaffoldPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(scaffoldPadding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.welcome_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = settingsUiState.url,
                    onValueChange = { viewModel.updateUrl(it) },
                    label = { Text(stringResource(R.string.account_settings_url_label)) },
                    placeholder = { Text(stringResource(R.string.account_settings_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = settingsUiState.urlError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (settingsUiState.loginEnabled) {
                                viewModel.login()
                            }
                        }
                    ),
                    supportingText = {
                        settingsUiState.urlError?.let {
                            Text(text = stringResource(it))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.login() },
                    enabled = settingsUiState.loginEnabled &&
                            settingsUiState.authStatus !is AccountSettingsViewModel.AuthStatus.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (settingsUiState.authStatus) {
                        is AccountSettingsViewModel.AuthStatus.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting...")
                        }
                        else -> {
                            Text(stringResource(R.string.welcome_connect_button))
                        }
                    }
                }

                if (settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (settingsUiState.authStatus as AccountSettingsViewModel.AuthStatus.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
