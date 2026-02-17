package com.mydeck.app.ui.userguide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserGuideViewModel @Inject constructor(
    private val markdownLoader: MarkdownAssetLoader
) : ViewModel() {
    
    var uiState by mutableStateOf(UserGuideUiState())
        private set
    
    init {
        loadSections()
    }
    
    private fun loadSections() {
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null
            )
            
            try {
                val sections = markdownLoader.loadSections()
                uiState = uiState.copy(
                    sections = sections,
                    isLoading = false,
                    selectedSection = sections.firstOrNull()
                )
                
                // Load content for first section
                sections.firstOrNull()?.let { section ->
                    loadSectionContent(section)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to load sections: ${e.message}"
                )
            }
        }
    }
    
    fun selectSection(section: GuideSection) {
        if (uiState.selectedSection?.fileName != section.fileName) {
            uiState = uiState.copy(selectedSection = section)
            loadSectionContent(section)
        }
    }
    
    private fun loadSectionContent(section: GuideSection) {
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoadingContent = true,
                contentError = null
            )
            
            try {
                val content = markdownLoader.loadMarkdown(section.fileName)
                uiState = uiState.copy(
                    currentContent = content,
                    isLoadingContent = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingContent = false,
                    contentError = "Failed to load content: ${e.message}"
                )
            }
        }
    }
    
    fun refreshContent() {
        uiState.selectedSection?.let { section ->
            loadSectionContent(section)
        }
    }
}

data class UserGuideUiState(
    val isLoading: Boolean = false,
    val isLoadingContent: Boolean = false,
    val sections: List<GuideSection> = emptyList(),
    val selectedSection: GuideSection? = null,
    val currentContent: String = "",
    val error: String? = null,
    val contentError: String? = null
)
