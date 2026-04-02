package com.mydeck.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mydeck.app.R
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.util.LogFileInfo
import com.mydeck.app.util.clearLogFiles
import com.mydeck.app.util.createLogFilesZip
import com.mydeck.app.util.getAllLogFiles
import com.mydeck.app.util.getLatestLogFile
import com.mydeck.app.util.logAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LogViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _availableLogFiles = MutableStateFlow<List<LogFileInfo>>(emptyList())
    val availableLogFiles: StateFlow<List<LogFileInfo>> = _availableLogFiles.asStateFlow()
    private val _selectedLogFile = MutableStateFlow<File?>(null)
    val selectedLogFile: StateFlow<File?> = _selectedLogFile.asStateFlow()
    val logRetentionDays: StateFlow<Int> = settingsDataStore.getLogRetentionDaysFlow()
    private val _showRetentionDialog = MutableStateFlow(false)
    val showRetentionDialog: StateFlow<Boolean> = _showRetentionDialog.asStateFlow()

    init {
        loadAvailableLogFiles()
        onRefresh()
    }

    fun onClickBack() {
        _navigationEvent.update { NavigationEvent.NavigateBack }
    }

    fun onRefresh() {
        Timber.d("refresh")
        viewModelScope.launch {
            loadAvailableLogFiles()
            val file = _selectedLogFile.value ?: getLatestLogFile()
            _uiState.value = file?.let {
                Timber.d("file=$it")
                UiState.Success(
                    logContent = it.readText()
                )
            }  ?: UiState.Error(R.string.log_view_no_log_file_found)
        }
    }

    fun onShareLogs() {
        logAppInfo()
        viewModelScope.launch {
            val zipFile = createLogFilesZip(context)
            if (zipFile != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    zipFile
                )
                _navigationEvent.update { NavigationEvent.ShowShareDialog(uri, isZip = true) }
            } else {
                _navigationEvent.update { NavigationEvent.ShareError }
            }
        }
    }

    fun onClearLogs() {
        viewModelScope.launch {
            clearLogFiles()
            onRefresh()
            _navigationEvent.update { NavigationEvent.LogsCleared }
        }
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.update { null }
    }

    fun onSelectLogFile(file: File) {
        _selectedLogFile.value = file
        onRefresh()
    }

    fun onClickLogRetention() {
        _showRetentionDialog.value = true
    }

    fun onDismissRetentionDialog() {
        _showRetentionDialog.value = false
    }

    fun onSelectRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsDataStore.saveLogRetentionDays(days)
            _showRetentionDialog.value = false
        }
    }

    private fun loadAvailableLogFiles() {
        val files = getAllLogFiles()
        _availableLogFiles.value = files
        val currentSelection = _selectedLogFile.value
        val selection = files.firstOrNull { it.file == currentSelection }?.file ?: files.firstOrNull()?.file
        _selectedLogFile.value = selection
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
        data class ShowShareDialog(val uri: Uri, val isZip: Boolean = false) : NavigationEvent()
        data object ShareError : NavigationEvent()
        data object LogsCleared : NavigationEvent()
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val logContent: String
        ) : UiState()
        data class Error(@StringRes val message: Int) : UiState()
    }
}
