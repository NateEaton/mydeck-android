package com.mydeck.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
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
import com.mydeck.app.ui.components.MyDeckBrandHeader
import com.mydeck.app.ui.login.DeviceAuthorizationScreen
import com.mydeck.app.util.openUrlInCustomTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navHostController: NavHostController,
    viewModel: AccountSettingsViewModel = hiltViewModel()
) {
    val settingsUiState = viewModel.uiState.collectAsState().value
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val onUrlChanged: (String) -> Unit = { url -> viewModel.updateUrl(url) }
    val onLoginClicked: () -> Unit = { viewModel.login() }
    val onSignOut: () -> Unit = { viewModel.signOut() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_account_title)) },
                navigationIcon = {
                    IconButton(onClick = { navHostController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { scaffoldPadding ->

    when {
        settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.WaitingForAuthorization &&
            settingsUiState.deviceAuthState != null -> {
            DeviceAuthorizationScreen(
                userCode = settingsUiState.deviceAuthState!!.userCode,
                verificationUri = settingsUiState.deviceAuthState!!.verificationUri,
                verificationUriComplete = settingsUiState.deviceAuthState!!.verificationUriComplete,
                expiresAt = settingsUiState.deviceAuthState!!.expiresAt,
                onCancel = { viewModel.cancelAuthorization() },
                modifier = Modifier.padding(scaffoldPadding)
            )
        }
        settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.BrowserLaunched ||
            settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.Exchanging -> {
            BrowserLoginWaitingScreen(
                isExchanging = settingsUiState.authStatus is AccountSettingsViewModel.AuthStatus.Exchanging,
                onCancel = { viewModel.cancelAuthorization() },
                onUseCodeInstead = { viewModel.switchToDeviceCodeFlow() },
                modifier = Modifier.padding(scaffoldPadding)
            )
        }
        else -> {
            LoginFormContent(
                settingsUiState = settingsUiState,
                onUrlChanged = onUrlChanged,
                onLoginClicked = onLoginClicked,
                onSignInWithCodeClicked = { viewModel.switchToDeviceCodeFlow() },
                onSignOut = onSignOut,
                keyboardController = keyboardController,
                modifier = Modifier.padding(scaffoldPadding)
            )
        }
    }

    } // end Scaffold

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { event ->
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
        }
    }

    LaunchedEffect(Unit) {
        viewModel.browserLaunchEvent.collect { url ->
            openUrlInCustomTab(context, url)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginFormContent(
    settingsUiState: AccountSettingsViewModel.AccountSettingsUiState,
    onUrlChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    onSignInWithCodeClicked: () -> Unit,
    onSignOut: () -> Unit,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 48.dp)
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
                    when {
                        settingsUiState.urlError != null ->
                            Text(text = stringResource(settingsUiState.urlError))
                        settingsUiState.urlWarning != null ->
                            HttpUrlWarningText()
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

        item {
            TextButton(
                onClick = onSignInWithCodeClicked,
                enabled = settingsUiState.loginEnabled && settingsUiState.authStatus !is AccountSettingsViewModel.AuthStatus.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.account_settings_sign_in_with_code))
            }
        }

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
}

@Composable
internal fun BrowserLoginWaitingScreen(
    isExchanging: Boolean,
    onCancel: () -> Unit,
    onUseCodeInstead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        MyDeckBrandHeader()

        Spacer(modifier = Modifier.weight(1f))

        CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isExchanging) {
                stringResource(R.string.oauth_auth_code_exchanging)
            } else {
                stringResource(R.string.oauth_auth_code_browser_launched)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (!isExchanging) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.oauth_auth_code_browser_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!isExchanging) {
            TextButton(onClick = onUseCodeInstead) {
                Text(stringResource(R.string.account_settings_sign_in_with_code))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(android.R.string.cancel))
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
                Text(stringResource(R.string.account_settings_signing_in))
            }
            else -> {
                Text(stringResource(R.string.account_settings_login))
            }
        }
    }
}
