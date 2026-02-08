package com.mydeck.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun LogViewScreen(navController: NavHostController) {
    val viewModel: LogViewViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val uiState = viewModel.uiState.collectAsState()
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val shareTitleText = stringResource(R.string.log_view_share_title)
    val shareErrorText = stringResource(R.string.log_view_no_log_file_found)
    val logsClearedText = stringResource(R.string.log_view_logs_cleared)

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                LogViewViewModel.NavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }

                LogViewViewModel.NavigationEvent.ShowShareDialog -> {
                    if (uiState.value is LogViewViewModel.UiState.Success) {
                        val uri = (uiState.value as LogViewViewModel.UiState.Success).shareIntentUri
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooserIntent = Intent.createChooser(shareIntent, shareTitleText)
                        context.startActivity(chooserIntent)

                    } else {
                        scope.launch {
                            Toast.makeText(context, shareErrorText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                LogViewViewModel.NavigationEvent.LogsCleared -> {
                    scope.launch {
                        Toast.makeText(context, logsClearedText, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            viewModel.onNavigationEventConsumed()
        }
    }

    LogViewScreenView(
        uiState = uiState.value,
        onClickBack = { viewModel.onClickBack() },
        onShareLogs = { viewModel.onShareLogs() },
        onRefresh = { viewModel.onRefresh() },
        onClearLogs = { viewModel.onClearLogs() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewScreenView(
    uiState: LogViewViewModel.UiState,
    onClickBack: () -> Unit,
    onShareLogs: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.log_view_title)) },
                navigationIcon = {
                    IconButton(onClick = onClickBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShareLogs) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(id = R.string.log_view_send_logs)
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(id = R.string.log_view_refresh)
                        )
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(id = R.string.log_view_clear_logs)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        when (uiState) {
            is LogViewViewModel.UiState.Success -> {
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(text = uiState.logContent)
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        scrollState = scrollState
                    )
                }
            }

            is LogViewViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(uiState.message))
                }
            }

            is LogViewViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LogViewScreenPreview() {
    LogViewScreenView(
        uiState = LogViewViewModel.UiState.Success(
            logContent = "This is a test log\n".repeat(100),
            shareIntentUri = Uri.EMPTY
        ),
        onClickBack = {},
        onShareLogs = {},
        onRefresh = {},
        onClearLogs = {}
    )
}
