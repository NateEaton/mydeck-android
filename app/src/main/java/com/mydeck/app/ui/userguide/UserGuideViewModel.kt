package com.mydeck.app.ui.userguide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mydeck.app.ui.navigation.UserGuideSectionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserGuideIndexViewModel @Inject constructor(
    private val markdownLoader: MarkdownAssetLoader
) : ViewModel() {

    var uiState by mutableStateOf(UserGuideIndexUiState())
        private set

    init {
        loadSections()
    }

    private fun loadSections() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val sections = markdownLoader.loadSections()
                uiState = uiState.copy(sections = sections, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to load sections: ${e.message}"
                )
            }
        }
    }
}

data class UserGuideIndexUiState(
    val isLoading: Boolean = false,
    val sections: List<GuideSection> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class UserGuideSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val markdownLoader: MarkdownAssetLoader
) : ViewModel() {

    private val route: UserGuideSectionRoute = savedStateHandle.toRoute()

    var uiState by mutableStateOf(UserGuideSectionUiState(title = route.title))
        private set

    init {
        loadContent(route.fileName)
    }

    private fun loadContent(fileName: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val content = markdownLoader.loadMarkdown(fileName)
                uiState = uiState.copy(content = content, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to load content: ${e.message}"
                )
            }
        }
    }
}

data class UserGuideSectionUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val content: String = "",
    val error: String? = null
)
