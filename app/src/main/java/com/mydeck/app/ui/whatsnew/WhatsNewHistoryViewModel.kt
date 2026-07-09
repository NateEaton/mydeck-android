package com.mydeck.app.ui.whatsnew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsNewHistoryViewModel @Inject constructor(
    private val loader: WhatsNewAssetLoader,
) : ViewModel() {

    var uiState by mutableStateOf(WhatsNewHistoryUiState())
        private set

    init {
        uiState = uiState.copy(versions = loader.listAvailableVersions())
    }

    fun onVersionClick(version: String) {
        viewModelScope.launch {
            val content = loader.loadNotesForVersion(version)
            if (content != null) {
                uiState = uiState.copy(selectedVersion = version, selectedContent = content)
            }
        }
    }

    fun onSheetDismissed() {
        uiState = uiState.copy(selectedVersion = null, selectedContent = null)
    }
}

data class WhatsNewHistoryUiState(
    val versions: List<String> = emptyList(),
    val selectedVersion: String? = null,
    val selectedContent: String? = null,
)
