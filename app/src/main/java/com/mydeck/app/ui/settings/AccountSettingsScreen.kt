package com.mydeck.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.domain.usecase.AuthenticationResult

@Composable
fun AccountSettingsScreen(
    navHostController: NavHostController
) {
    val viewModel: AccountSettingsViewModel = hiltViewModel()
    val settingsUiState = viewModel.uiState.collectAsState().value
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val onUrlChanged: (String) -> Unit = { url -> viewModel.onUrlChanged(url) }
    val onUsernameChanged: (String) -> Unit = { username -> viewModel.onUsernameChanged(username) }
    val onPasswordChanged: (String) -> Unit = { password -> viewModel.onPasswordChanged(password) }
    val onLoginClicked: () -> Unit = { viewModel.login() }
    val onAllowUnencryptedConnectionChanged: (Boolean) -> Unit = { allow -> viewModel.onAllowUnencryptedConnectionChanged(allow) }
    val onClickBack: () -> Unit = { viewModel.onClickBack() }
    val onSignOut: () -> Unit = { viewModel.signOut() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isStartDestination = navHostController.previousBackStackEntry == null

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
            viewModel.onNavigationEventConsumed() // Consume the event
        }
    }

    LaunchedEffect(key1 = settingsUiState.authenticationResult) {
        settingsUiState.authenticationResult?.let { result ->
            when (result) {
                is AuthenticationResult.Success -> {
                    snackbarHostState.showSnackbar(
                        message = "Success",
                        duration = SnackbarDuration.Short
                    )
                }

                is AuthenticationResult.AuthenticationFailed -> {
                    snackbarHostState.showSnackbar(
                        message = result.message,
                        duration = SnackbarDuration.Short
                    )
                }

                is AuthenticationResult.NetworkError -> {
                    snackbarHostState.showSnackbar(
                        message = result.message,
                        duration = SnackbarDuration.Short
                    )
                }

                is AuthenticationResult.GenericError -> {
                    snackbarHostState.showSnackbar(
                        message = result.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    AccountSettingsView(
        modifier = Modifier,
        snackbarHostState = snackbarHostState,
        settingsUiState = settingsUiState,
        onUrlChanged = onUrlChanged,
        onUsernameChanged = onUsernameChanged,
        onPasswordChanged = onPasswordChanged,
        onLoginClicked = onLoginClicked,
        onClickBack = onClickBack,
        onAllowUnencryptedConnectionChanged = onAllowUnencryptedConnectionChanged,
        onSignOut = onSignOut,
        isStartDestination = isStartDestination
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsView(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    settingsUiState: AccountSettingsUiState,
    onUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    onClickBack: () -> Unit,
    onAllowUnencryptedConnectionChanged: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    isStartDestination: Boolean = false
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val urlFocusRequester = remember { FocusRequester() }
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Request focus on URL field when screen opens
    LaunchedEffect(Unit) {
        urlFocusRequester.requestFocus()
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.account_settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.account_settings_sign_out_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text(stringResource(R.string.account_settings_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accountsettings_topbar_title)) },
                navigationIcon = if (!isStartDestination) {
                    {
                        IconButton(
                            onClick = onClickBack,
                            modifier = Modifier.testTag(AccountSettingsScreenTestTags.BACK_BUTTON)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                } else {
                    null
                }
            )
        }
    ) { padding ->
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
                    value = settingsUiState.url ?: "",
                    placeholder = { Text(stringResource(R.string.account_settings_url_placeholder)) },
                    onValueChange = { onUrlChanged(it) },
                    label = { Text(stringResource(R.string.account_settings_url_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(urlFocusRequester),
                    singleLine = true,
                    isError = settingsUiState.urlError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    supportingText = {
                        settingsUiState.urlError?.let {
                            Text(text = stringResource(it))
                        }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = settingsUiState.username ?: "",
                    placeholder = { Text(stringResource(R.string.account_settings_username_placeholder)) },
                    onValueChange = { onUsernameChanged(it) },
                    label = { Text(stringResource(R.string.account_settings_username_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = settingsUiState.usernameError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    supportingText = {
                        settingsUiState.usernameError?.let {
                            Text(text = stringResource(it))
                        }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = settingsUiState.password ?: "",
                    placeholder = { Text(stringResource(R.string.account_settings_password_placeholder)) },
                    onValueChange = { onPasswordChanged(it) },
                    label = { Text(stringResource(R.string.account_settings_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = settingsUiState.passwordError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (settingsUiState.loginEnabled) {
                                onLoginClicked()
                            }
                        }
                    ),
                    supportingText = {
                        settingsUiState.passwordError?.let {
                            Text(text = stringResource(it))
                        }
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .selectable(
                            selected = settingsUiState.allowUnencryptedConnection,
                            onClick = { onAllowUnencryptedConnectionChanged(settingsUiState.allowUnencryptedConnection.not()) },
                            role = Role.Checkbox
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = settingsUiState.allowUnencryptedConnection,
                        onCheckedChange = null
                    )
                    Text(text = stringResource(R.string.account_settings_allow_unencrypted))
                }
            }
            item {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onLoginClicked.invoke()
                    },
                    enabled = settingsUiState.loginEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.account_settings_login))
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
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.account_settings_sign_out))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AccountSettingsScreenViewPreview() {
    val settingsUiState = AccountSettingsUiState(
        url = "https://example.com",
        username = "user",
        password = "pass",
        loginEnabled = true,
        urlError = R.string.account_settings_url_error,
        usernameError = null,
        passwordError = null,
        authenticationResult = null,
        allowUnencryptedConnection = false,
        isLoggedIn = true
    )
    AccountSettingsView(
        modifier = Modifier,
        snackbarHostState = SnackbarHostState(),
        settingsUiState = settingsUiState,
        onUrlChanged = {},
        onUsernameChanged = {},
        onPasswordChanged = {},
        onLoginClicked = {},
        onClickBack = {},
        onAllowUnencryptedConnectionChanged = {},
        onSignOut = {},
        isStartDestination = false
    )
}

object AccountSettingsScreenTestTags {
    const val BACK_BUTTON = "AccountSettingsScreenTestTags.BackButton"
    const val TOPBAR = "AccountSettingsScreenTestTags.TopBar"
    const val SETTINGS_ITEM = "AccountSettingsScreenTestTags.SettingsItem"
    const val SETTINGS_ITEM_TITLE = "AccountSettingsScreenTestTags.SettingsItem.Title"
    const val SETTINGS_ITEM_SUBTITLE = "AccountSettingsScreenTestTags.SettingsItem.Subtitle"
    const val SETTINGS_ITEM_ACCOUNT = "Account"
}
