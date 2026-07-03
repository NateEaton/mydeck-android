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

    private var searchDocs: List<GuideSearchDoc> = emptyList()

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

    fun onSearchQueryChange(query: String) {
        uiState = uiState.copy(searchQuery = query, searchResults = search(query))
    }

    fun clearSearch() {
        uiState = uiState.copy(searchQuery = "", searchResults = emptyList())
    }

    private fun search(query: String): List<GuideSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (searchDocs.isEmpty()) {
            searchDocs = markdownLoader.loadSearchDocs()
        }
        val needle = trimmed.lowercase()
        val headingMatches = mutableListOf<GuideSearchResult>()
        val bodyMatches = mutableListOf<GuideSearchResult>()
        for (doc in searchDocs) {
            val heading = doc.headings.firstOrNull { it.lowercase().contains(needle) }
            if (heading != null) {
                headingMatches += GuideSearchResult(doc.section, heading, heading)
            } else {
                val idx = doc.body.lowercase().indexOf(needle)
                if (idx >= 0) {
                    bodyMatches += GuideSearchResult(doc.section, null, snippet(doc.body, idx, needle.length))
                }
            }
        }
        return headingMatches + bodyMatches
    }

    /** A short window of body text around the match, for display in the result row. */
    private fun snippet(body: String, matchIndex: Int, matchLength: Int): String {
        val start = (matchIndex - 40).coerceAtLeast(0)
        val end = (matchIndex + matchLength + 40).coerceAtMost(body.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < body.length) "…" else ""
        return prefix + body.substring(start, end).trim() + suffix
    }
}

data class UserGuideIndexUiState(
    val isLoading: Boolean = false,
    val sections: List<GuideSection> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<GuideSearchResult> = emptyList()
)

data class GuideSearchResult(
    val section: GuideSection,
    val matchedHeading: String?,
    val snippet: String
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
