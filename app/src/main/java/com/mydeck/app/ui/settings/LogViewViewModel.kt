package com.mydeck.app.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LogViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()
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
        _navigationEvent.trySend(NavigationEvent.NavigateBack)
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
                _navigationEvent.trySend(NavigationEvent.ShowShareDialog(uri, isZip = true))
            } else {
                _navigationEvent.trySend(NavigationEvent.ShareError)
            }
        }
    }

    fun onSaveToDownloads() {
        logAppInfo()
        viewModelScope.launch {
            val zipFile = createLogFilesZip(context)
            if (zipFile == null) {
                _navigationEvent.trySend(NavigationEvent.SaveError)
                return@launch
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, zipFile.name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            zipFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        _navigationEvent.trySend(NavigationEvent.SavedToDownloads)
                    } else {
                        _navigationEvent.trySend(NavigationEvent.SaveError)
                    }
                } else {
                    // Pre-Q: copy directly to public Downloads directory
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destFile = java.io.File(downloadsDir, zipFile.name)
                    zipFile.copyTo(destFile, overwrite = true)
                    _navigationEvent.trySend(NavigationEvent.SavedToDownloads)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to save logs to Downloads")
                _navigationEvent.trySend(NavigationEvent.SaveError)
            }
        }
    }

    fun onClearLogs() {
        viewModelScope.launch {
            clearLogFiles()
            onRefresh()
            _navigationEvent.trySend(NavigationEvent.LogsCleared)
        }
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
        data object SavedToDownloads : NavigationEvent()
        data object SaveError : NavigationEvent()
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
