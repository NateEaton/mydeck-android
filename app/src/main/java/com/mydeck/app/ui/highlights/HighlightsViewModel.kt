package com.mydeck.app.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.model.BookmarkHighlightGroup
import com.mydeck.app.domain.model.HighlightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HighlightsUiState>(HighlightsUiState.Loading)
    val uiState: StateFlow<HighlightsUiState> = _uiState.asStateFlow()

    init {
        loadHighlights()
    }

    fun loadHighlights() {
        viewModelScope.launch {
            _uiState.value = HighlightsUiState.Loading
            highlightsRepository.getAllHighlights()
                .onSuccess { highlights ->
                    if (highlights.isEmpty()) {
                        _uiState.value = HighlightsUiState.Empty
                    } else {
                        _uiState.value = HighlightsUiState.Success(group(highlights))
                    }
                }
                .onFailure { error ->
                    _uiState.value = HighlightsUiState.Error(
                        error.message ?: "Failed to load highlights"
                    )
                }
        }
    }

    /**
     * Groups flat highlights by bookmarkId. Groups are sorted by most-recent
     * highlight descending. Within each group, highlights are newest-first.
     */
    private fun group(highlights: List<HighlightSummary>): List<BookmarkHighlightGroup> {
        return highlights
            .sortedByDescending { it.created }
            .groupBy { it.bookmarkId }
            .map { (bookmarkId, items) ->
                BookmarkHighlightGroup(
                    bookmarkId = bookmarkId,
                    bookmarkTitle = items.first().bookmarkTitle,
                    bookmarkSiteName = items.first().bookmarkSiteName,
                    highlights = items,  // already sorted descending from outer sort
                )
            }
    }
}

sealed interface HighlightsUiState {
    data object Loading : HighlightsUiState
    data object Empty : HighlightsUiState
    data class Success(val groups: List<BookmarkHighlightGroup>) : HighlightsUiState
    data class Error(val message: String) : HighlightsUiState
}
