package com.mydeck.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.navigation.NavHostController
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar
import com.mydeck.app.util.LogFileInfo
import kotlinx.coroutines.launch

@Composable
fun LogViewScreen(navController: NavHostController) {
    val viewModel: LogViewViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val uiState = viewModel.uiState.collectAsState()
    val navigationEvent = viewModel.navigationEvent.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availableLogFiles by viewModel.availableLogFiles.collectAsState()
    val selectedLogFile by viewModel.selectedLogFile.collectAsState()
    val showRetentionDialog by viewModel.showRetentionDialog.collectAsState()
    val currentRetentionDays by viewModel.logRetentionDays.collectAsState()

    val shareTitleText = stringResource(R.string.log_view_share_title)
    val shareErrorText = stringResource(R.string.log_view_share_error)
    val logsClearedText = stringResource(R.string.log_view_logs_cleared)

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                LogViewViewModel.NavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }

                is LogViewViewModel.NavigationEvent.ShowShareDialog -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (event.isZip) "application/zip" else "text/plain"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooserIntent = Intent.createChooser(shareIntent, shareTitleText)
                    context.startActivity(chooserIntent)
                }

                LogViewViewModel.NavigationEvent.ShareError -> {
                    scope.launch {
                        Toast.makeText(context, shareErrorText, Toast.LENGTH_SHORT).show()
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
        availableLogFiles = availableLogFiles,
        selectedLogFile = selectedLogFile,
        onClickBack = { viewModel.onClickBack() },
        onClickLogRetention = { viewModel.onClickLogRetention() },
        onShareLogs = { viewModel.onShareLogs() },
        onRefresh = { viewModel.onRefresh() },
        onClearLogs = { viewModel.onClearLogs() },
        onSelectLogFile = { viewModel.onSelectLogFile(it) }
    )

    if (showRetentionDialog) {
        LogRetentionDialog(
            currentRetentionDays = currentRetentionDays,
            onDismiss = { viewModel.onDismissRetentionDialog() },
            onSelect = { days -> viewModel.onSelectRetentionDays(days) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewScreenView(
    uiState: LogViewViewModel.UiState,
    availableLogFiles: List<LogFileInfo>,
    selectedLogFile: java.io.File?,
    onClickBack: () -> Unit,
    onClickLogRetention: () -> Unit,
    onShareLogs: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
    onSelectLogFile: (java.io.File) -> Unit
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
                    IconButton(onClick = onClickLogRetention) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(id = R.string.log_retention_title)
                        )
                    }
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

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LogFileSelector(
                availableFiles = availableLogFiles,
                selectedFile = selectedLogFile,
                onSelectFile = onSelectLogFile
            )

            when (uiState) {
                is LogViewViewModel.UiState.Success -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(uiState.message))
                    }
                }

                is LogViewViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRetentionDialog(
    currentRetentionDays: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var selectedDays by remember(currentRetentionDays) { mutableStateOf(currentRetentionDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_retention_title)) },
        text = {
            Column {
                LogRetentionOption(1, R.string.log_retention_1_day, selectedDays == 1) { selectedDays = it }
                LogRetentionOption(7, R.string.log_retention_1_week, selectedDays == 7) { selectedDays = it }
                LogRetentionOption(30, R.string.log_retention_1_month, selectedDays == 30) { selectedDays = it }
                LogRetentionOption(90, R.string.log_retention_3_months, selectedDays == 90) { selectedDays = it }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.log_retention_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedDays) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LogRetentionOption(
    days: Int,
    @StringRes labelRes: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(days) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { onSelect(days) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
}

@Composable
private fun LogFileSelector(
    availableFiles: List<LogFileInfo>,
    selectedFile: java.io.File?,
    onSelectFile: (java.io.File) -> Unit
) {
    if (availableFiles.size <= 1) return

    var expanded by remember { mutableStateOf(false) }
    val selectedFileInfo = availableFiles.find { it.file == selectedFile } ?: availableFiles.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFileInfo?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectedFileInfo?.let {
                        Text(
                            text = "${it.label} • ${it.sizeKb} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            availableFiles.forEach { fileInfo ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = fileInfo.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${fileInfo.label} • ${fileInfo.sizeKb} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelectFile(fileInfo.file)
                        expanded = false
                    },
                    leadingIcon = if (fileInfo.file == selectedFile) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LogViewScreenPreview() {
    LogViewScreenView(
        uiState = LogViewViewModel.UiState.Success(
            logContent = "This is a test log\n".repeat(100)
        ),
        availableLogFiles = emptyList(),
        selectedLogFile = null,
        onClickBack = {},
        onClickLogRetention = {},
        onShareLogs = {},
        onRefresh = {},
        onClearLogs = {},
        onSelectLogFile = {}
    )
}
